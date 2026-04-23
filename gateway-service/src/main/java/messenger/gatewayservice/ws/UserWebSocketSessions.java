package messenger.gatewayservice.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class UserWebSocketSessions {
    private static final int MAX_PRESENCE_SUBSCRIPTIONS = 512;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<Sinks.Many<String>>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Sinks.Many<String>, Long> sinkOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Sinks.Many<String>, Set<Long>> presenceSubscriptionsBySink = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Sinks.Many<String>>> presenceWatchersByUser = new ConcurrentHashMap<>();

    public Sinks.Many<String> register(Long userId) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean becameOnline = new AtomicBoolean(false);

        sessions.compute(userId, (key, existing) -> {
            Set<Sinks.Many<String>> userSinks = existing;
            if (userSinks == null) {
                userSinks = ConcurrentHashMap.newKeySet();
                becameOnline.set(true);
            }

            userSinks.add(sink);
            return userSinks;
        });

        sinkOwners.put(sink, userId);
        presenceSubscriptionsBySink.put(sink, ConcurrentHashMap.newKeySet());

        if (becameOnline.get()) {
            broadcastPresence(userId, true);
        }

        return sink;
    }

    public void unregister(Long userId, Sinks.Many<String> sink) {
        removePresenceSubscriptions(sink);
        sinkOwners.remove(sink);

        AtomicBoolean becameOffline = new AtomicBoolean(false);
        sessions.compute(userId, (key, existing) -> {
            if (existing == null) {
                return null;
            }

            existing.remove(sink);
            if (existing.isEmpty()) {
                becameOffline.set(true);
                return null;
            }

            return existing;
        });

        if (becameOffline.get()) {
            broadcastPresence(userId, false);
        }
    }

    public void pushMessageToUser(Long userId, MessageDto messageDto) {
        Set<Sinks.Many<String>> userSinks = sessions.get(userId);
        if (userSinks == null || userSinks.isEmpty()) {
            return;
        }

        String payload = toJsonSafe(new OutgoingMessageEvent(
                "message",
                messageDto.id(),
                messageDto.chatId(),
                messageDto.userId(),
                messageDto.content()
        ));

        for (Sinks.Many<String> sink : userSinks) {
            emitToSink(sink, payload);
        }
    }

    public void pushMessageReadToUser(Long userId, MessageReadEventDto messageReadEventDto) {
        Set<Sinks.Many<String>> userSinks = sessions.get(userId);
        if (userSinks == null || userSinks.isEmpty()) {
            return;
        }

        String payload = toJsonSafe(new OutgoingMessageReadEvent(
                "message_read",
                messageReadEventDto.id(),
                messageReadEventDto.chatId(),
                messageReadEventDto.readerId(),
                messageReadEventDto.readStatus()
        ));

        for (Sinks.Many<String> sink : userSinks) {
            emitToSink(sink, payload);
        }
    }

    public void pushMessageDeleteToUser(Long userId, MessageDeleteEventDto messageDeleteEventDto) {
        Set<Sinks.Many<String>> userSinks = sessions.get(userId);
        if (userSinks == null || userSinks.isEmpty()) {
            return;
        }

        String payload = toJsonSafe(new OutgoingMessageDeleteEvent(
                "message_delete",
                messageDeleteEventDto.id(),
                messageDeleteEventDto.chatId(),
                messageDeleteEventDto.deletedByUserId()
        ));

        for (Sinks.Many<String> sink : userSinks) {
            emitToSink(sink, payload);
        }
    }

    public void updatePresenceSubscriptions(Sinks.Many<String> sink, Iterable<Long> userIds) {
        if (!sinkOwners.containsKey(sink)) {
            return;
        }

        Set<Long> normalizedTargets = normalizeTargets(userIds);
        Set<Long> previousTargets = presenceSubscriptionsBySink.computeIfAbsent(sink, key -> ConcurrentHashMap.newKeySet());
        Set<Long> previousSnapshot = Set.copyOf(previousTargets);

        for (Long targetUserId : previousSnapshot) {
            if (normalizedTargets.contains(targetUserId)) {
                continue;
            }

            Set<Sinks.Many<String>> watchers = presenceWatchersByUser.get(targetUserId);
            if (watchers == null) {
                continue;
            }

            watchers.remove(sink);
            if (watchers.isEmpty()) {
                presenceWatchersByUser.remove(targetUserId, watchers);
            }
        }

        previousTargets.clear();
        previousTargets.addAll(normalizedTargets);

        for (Long targetUserId : normalizedTargets) {
            presenceWatchersByUser.computeIfAbsent(targetUserId, key -> ConcurrentHashMap.newKeySet()).add(sink);
            sendPresenceSnapshot(sink, targetUserId);
        }
    }

    private Set<Long> normalizeTargets(Iterable<Long> userIds) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        if (userIds == null) {
            return normalized;
        }

        for (Long candidate : userIds) {
            if (candidate == null || candidate <= 0) {
                continue;
            }

            normalized.add(candidate);
            if (normalized.size() >= MAX_PRESENCE_SUBSCRIPTIONS) {
                break;
            }
        }

        return normalized;
    }

    private void removePresenceSubscriptions(Sinks.Many<String> sink) {
        Set<Long> subscribedUsers = presenceSubscriptionsBySink.remove(sink);
        if (subscribedUsers == null || subscribedUsers.isEmpty()) {
            return;
        }

        for (Long targetUserId : subscribedUsers) {
            Set<Sinks.Many<String>> watchers = presenceWatchersByUser.get(targetUserId);
            if (watchers == null) {
                continue;
            }

            watchers.remove(sink);
            if (watchers.isEmpty()) {
                presenceWatchersByUser.remove(targetUserId, watchers);
            }
        }
    }

    private void sendPresenceSnapshot(Sinks.Many<String> sink, Long targetUserId) {
        emitToSink(sink, toJsonSafe(new OutgoingPresenceEvent(
                "presence",
                targetUserId,
                isOnline(targetUserId)
        )));
    }

    private void broadcastPresence(Long userId, boolean online) {
        Set<Sinks.Many<String>> watchers = presenceWatchersByUser.get(userId);
        if (watchers == null || watchers.isEmpty()) {
            return;
        }

        String payload = toJsonSafe(new OutgoingPresenceEvent("presence", userId, online));
        for (Sinks.Many<String> sink : watchers) {
            emitToSink(sink, payload);
        }
    }

    private boolean isOnline(Long userId) {
        Set<Sinks.Many<String>> userSinks = sessions.get(userId);
        return userSinks != null && !userSinks.isEmpty();
    }

    private void emitToSink(Sinks.Many<String> sink, String payload) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Sinks.EmitResult result = sink.tryEmitNext(payload);
            if (result == Sinks.EmitResult.OK) {
                return;
            }

            if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                Thread.yield();
                continue;
            }

            if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                return;
            }

            if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                // Receiver can be not fully attached yet right after websocket upgrade.
                // Keep session alive, next event/subscription update will retry delivery.
                return;
            }

            if (result == Sinks.EmitResult.FAIL_CANCELLED || result == Sinks.EmitResult.FAIL_TERMINATED) {
                cleanupFailedSink(sink);
            }
            return;
        }
    }

    private void cleanupFailedSink(Sinks.Many<String> sink) {
        Long userId = sinkOwners.get(sink);
        if (userId != null) {
            unregister(userId, sink);
            return;
        }

        removePresenceSubscriptions(sink);
    }

    private String toJsonSafe(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            return "{\"type\":\"error\",\"message\":\"Serialization error\"}";
        }
    }

    private record OutgoingMessageEvent(String type, String id, Long chatId, Long userId, String content) {
    }

    private record OutgoingMessageReadEvent(
            String type,
            String id,
            Long chatId,
            Long readerId,
            Boolean readStatus
    ) {
    }

    private record OutgoingMessageDeleteEvent(
            String type,
            String id,
            Long chatId,
            Long deletedByUserId
    ) {
    }

    private record OutgoingPresenceEvent(String type, Long userId, boolean online) {
    }
}
