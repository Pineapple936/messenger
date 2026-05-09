package messenger.gatewayservice.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@Component
public class MessageBridgeWebSocketHandler implements WebSocketHandler {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String PRESENCE_SUBSCRIBE_EVENT = "presence_subscribe";
    private static final String TYPING_EVENT = "typing";
    private static final String UNAUTHORIZED_RESPONSE = "{\"status\":\"error\",\"code\":401,\"message\":\"Unauthorized\"}";

    private final UserWebSocketSessions userWebSocketSessions;
    private final ChatParticipantsClient chatParticipantsClient;
    private final ObjectMapper objectMapper;

    public MessageBridgeWebSocketHandler(UserWebSocketSessions userWebSocketSessions,
                                         ChatParticipantsClient chatParticipantsClient,
                                         ObjectMapper objectMapper) {
        this.userWebSocketSessions = userWebSocketSessions;
        this.chatParticipantsClient = chatParticipantsClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String userIdHeader = session.getHandshakeInfo().getHeaders().getFirst(USER_ID_HEADER);
        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return sendUnauthorizedAndClose(session);
        }

        Sinks.Many<String> outboundSink = userWebSocketSessions.register(userId);
        Flux<String> outboundMessages = outboundSink.asFlux();

        return session.send(outboundMessages.map(session::textMessage))
                .and(session.receive()
                        .map(message -> message.getPayloadAsText())
                        .flatMap(payload ->
                            // Run on boundedElastic so blocking HTTP calls (getChatUsers) are allowed
                            Mono.fromRunnable(() -> handleIncomingPayload(userId, outboundSink, payload))
                                .subscribeOn(Schedulers.boundedElastic())
                                .onErrorResume(ex -> Mono.empty())
                        )
                        .then())
                .doFinally(signalType -> userWebSocketSessions.unregister(userId, outboundSink));
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) return null;
        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Mono<Void> sendUnauthorizedAndClose(WebSocketSession session) {
        return session.send(Mono.just(session.textMessage(UNAUTHORIZED_RESPONSE)))
                .then(session.close(CloseStatus.POLICY_VIOLATION));
    }

    private void handleIncomingPayload(Long userId, Sinks.Many<String> outboundSink, String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String type = json.path("type").asText(null);
            if (type == null) return;

            if (PRESENCE_SUBSCRIBE_EVENT.equals(type)) {
                List<Long> userIds = new ArrayList<>();
                JsonNode arr = json.path("userIds");
                if (arr.isArray()) {
                    arr.forEach(node -> { if (node.isNumber()) userIds.add(node.asLong()); });
                }
                userWebSocketSessions.updatePresenceSubscriptions(outboundSink, userIds);

            } else if (TYPING_EVENT.equals(type)) {
                JsonNode chatIdNode = json.path("chatId");
                if (chatIdNode.isNumber()) {
                    Long chatId = chatIdNode.asLong();
                    List<Long> participants = chatParticipantsClient.getChatUsers(chatId);
                    for (Long participantId : participants) {
                        if (!participantId.equals(userId)) {
                            userWebSocketSessions.pushTypingToUser(participantId, chatId, userId);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // ignore malformed or failed events — must not break WS stream
        }
    }
}
