package messenger.messageservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageKafkaProducer {
    private final KafkaTemplate<String, MessageDto> messageKafkaTemplate;
    private final KafkaTemplate<String, MessageReadEventDto> messageReadEventKafkaTemplate;
    private final KafkaTemplate<String, MessageDeleteEventDto> messageDeleteEventKafkaTemplate;

    @Value("${kafka.topic.chat:chat-messages}")
    private String chatTopic;

    @Value("${kafka.topic.messageRead:message-read-event}")
    private String readMessageTopic;

    @Value("${kafka.topic.messageDelete:message-delete-event}")
    private String messageDeleteTopic;

    public void sendMessageToKafka(MessageDto messageDto) {
        messageKafkaTemplate.send(chatTopic, String.valueOf(messageDto.chatId()), messageDto);
    }

    public void sendReadEvent(MessageReadEventDto messageReadEventDto) {
        messageReadEventKafkaTemplate.send(readMessageTopic, String.valueOf(messageReadEventDto.chatId()), messageReadEventDto);
    }

    public void sendDeleteEvent(MessageDeleteEventDto messageDeleteEventDto) {
        messageDeleteEventKafkaTemplate.send(messageDeleteTopic, String.valueOf(messageDeleteEventDto.chatId()), messageDeleteEventDto);
    }
}
