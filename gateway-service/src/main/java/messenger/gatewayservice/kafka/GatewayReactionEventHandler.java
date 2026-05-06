package messenger.gatewayservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.reactionservice.GatewayReactionEventDto;
import messenger.gatewayservice.ws.ChatParticipantsClient;
import messenger.gatewayservice.ws.UserWebSocketSessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GatewayReactionEventHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayReactionEventHandler.class);

    private final ChatParticipantsClient chatParticipantsClient;
    private final UserWebSocketSessions userWebSocketSessions;

    public void handle(GatewayReactionEventDto event) {
        List<Long> users;
        try {
            users = chatParticipantsClient.getChatUsers(event.chatId());
        } catch (Exception e) {
            log.warn("Failed to fetch chat users for chatId={}, skipping reaction delivery: {}", event.chatId(), e.getMessage());
            return;
        }
        for (Long userId : users) {
            userWebSocketSessions.pushReactionToUser(userId, event);
        }
    }
}
