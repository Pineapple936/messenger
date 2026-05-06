package messenger.messageservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageKafkaProducer {
    private final KafkaTemplate<String, MessageDto> chatMessageKafkaTemplate;
    private final KafkaTemplate<String, GatewayMessageEventDto> gatewayMessageEventKafkaTemplate;
    private final KafkaTemplate<String, DeleteMessageDto> deleteMessageKafkaTemplate;

    @Value("${kafka.topic.chat:chat-messages}")
    private String chatTopic;

    @Value("${kafka.topic.gateway.messageEvents:gateway-message-events}")
    private String gatewayMessageEventsTopic;

    @Value("${kafka.topic.delete.message:message-delete}")
    private String messageDeleteTopic;

    public void sendMessageToKafka(MessageDto messageDto) {
        String key = String.valueOf(messageDto.chatId());
        chatMessageKafkaTemplate.send(chatTopic, key, messageDto);
        gatewayMessageEventKafkaTemplate.send(gatewayMessageEventsTopic, key, GatewayMessageEventDto.messageCreated(messageDto));
    }

    public void sendReadEvent(MessageReadEventDto messageReadEventDto) {
        gatewayMessageEventKafkaTemplate.send(
                gatewayMessageEventsTopic,
                String.valueOf(messageReadEventDto.chatId()),
                GatewayMessageEventDto.messageRead(messageReadEventDto)
        );
    }

    public void sendEditEvent(MessageEditEventDto messageEditEventDto) {
        gatewayMessageEventKafkaTemplate.send(
                gatewayMessageEventsTopic,
                String.valueOf(messageEditEventDto.chatId()),
                GatewayMessageEventDto.messageEdit(messageEditEventDto)
        );
    }

    public void sendDeleteEvent(MessageDeleteEventDto messageDeleteEventDto) {
        gatewayMessageEventKafkaTemplate.send(
                gatewayMessageEventsTopic,
                String.valueOf(messageDeleteEventDto.chatId()),
                GatewayMessageEventDto.messageDeleted(messageDeleteEventDto)
        );
        sendDeleteMessage(new DeleteMessageDto(messageDeleteEventDto.id()));
    }

    public void sendDeleteMessage(DeleteMessageDto deleteMessageDto) {
        deleteMessageKafkaTemplate.send(
                messageDeleteTopic,
                deleteMessageDto.messageId(),
                deleteMessageDto
        );
    }
}
