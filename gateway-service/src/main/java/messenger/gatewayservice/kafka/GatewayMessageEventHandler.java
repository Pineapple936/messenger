package messenger.gatewayservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.GatewayMessageEventDto;
import messenger.gatewayservice.ws.ChatParticipantsClient;
import messenger.gatewayservice.ws.UserWebSocketSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatewayMessageEventHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayMessageEventHandler.class);

    private final ChatParticipantsClient chatParticipantsClient;
    private final UserWebSocketSessions userWebSocketSessions;

    public void handle(GatewayMessageEventDto messageEvent) {
        var users = chatParticipantsClient.getChatUsers(messageEvent.chatId());
        log.info("Handling {} for chatId={}, participants={}", messageEvent.type(), messageEvent.chatId(), users);
        switch (messageEvent.type()) {
            case MESSAGE_CREATED -> {
                if (messageEvent.message() == null) {
                    log.warn("Received MESSAGE_CREATED event without payload for chatId={}", messageEvent.chatId());
                    return;
                }
                for (Long userId : users) {
                    if (!userId.equals(messageEvent.message().userId())) {
                        log.info("Pushing message to userId={}, online={}", userId, userWebSocketSessions.isOnline(userId));
                        userWebSocketSessions.pushMessageToUser(userId, messageEvent.message());
                    }
                }
            }
            case MESSAGE_READ -> {
                if (messageEvent.messageReadEvent() == null) {
                    log.warn("Received MESSAGE_READ event without payload for chatId={}", messageEvent.chatId());
                    return;
                }
                for (Long userId : users) {
                    if (!userId.equals(messageEvent.messageReadEvent().readerId())) {
                        userWebSocketSessions.pushMessageReadToUser(userId, messageEvent.messageReadEvent());
                    }
                }
            }
            case MESSAGE_EDIT -> {
                if (messageEvent.messageEditEvent() == null) {
                    log.warn("Received MESSAGE_EDIT event without payload for chatId={}", messageEvent.chatId());
                    return;
                }
                for (Long userId : users) {
                    userWebSocketSessions.pushMessageEditToUser(userId, messageEvent.messageEditEvent());
                }
            }
            case MESSAGE_DELETED -> {
                if (messageEvent.messageDeleteEvent() == null) {
                    log.warn("Received MESSAGE_DELETED event without payload for chatId={}", messageEvent.chatId());
                    return;
                }
                for (Long userId : users) {
                    if (!userId.equals(messageEvent.messageDeleteEvent().deletedByUserId())) {
                        userWebSocketSessions.pushMessageDeleteToUser(userId, messageEvent.messageDeleteEvent());
                    }
                }
            }
        }
    }
}
