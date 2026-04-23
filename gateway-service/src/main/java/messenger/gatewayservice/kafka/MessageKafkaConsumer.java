package messenger.gatewayservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
import messenger.gatewayservice.ws.ChatParticipantsClient;
import messenger.gatewayservice.ws.UserWebSocketSessions;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageKafkaConsumer {
    private final ChatParticipantsClient chatParticipantsClient;
    private final UserWebSocketSessions userWebSocketSessions;

    @KafkaListener(
            topics = "${gateway.kafka.topic.chat:chat-messages}",
            containerFactory = "messageContainerFactory"
    )
    public void consumeMessage(MessageDto messageDto) {
        var users = chatParticipantsClient.getChatUsers(messageDto.chatId());
        for (Long userId : users) {
            if (!userId.equals(messageDto.userId())) {
                userWebSocketSessions.pushMessageToUser(userId, messageDto);
            }
        }
    }

    @KafkaListener(
            topics = "${gateway.kafka.topic.messageRead:message-read-event}",
            containerFactory = "messageReadContainerFactory"
    )
    public void consumeMessageRead(MessageReadEventDto messageReadEventDto) {
        var users = chatParticipantsClient.getChatUsers(messageReadEventDto.chatId());
        for (Long userId : users) {
            if (!userId.equals(messageReadEventDto.readerId())) {
                userWebSocketSessions.pushMessageReadToUser(userId, messageReadEventDto);
            }
        }
    }

    @KafkaListener(
            topics = "${gateway.kafka.topic.messageDelete:message-delete-event}",
            containerFactory = "messageDeleteContainerFactory"
    )
    public void consumeMessageDelete(MessageDeleteEventDto messageDeleteEventDto) {
        var users = chatParticipantsClient.getChatUsers(messageDeleteEventDto.chatId());
        for (Long userId : users) {
            if (!userId.equals(messageDeleteEventDto.deletedByUserId())) {
                userWebSocketSessions.pushMessageDeleteToUser(userId, messageDeleteEventDto);
            }
        }
    }
}
