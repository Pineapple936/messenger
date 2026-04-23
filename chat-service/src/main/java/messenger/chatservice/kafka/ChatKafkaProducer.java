package messenger.chatservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatKafkaProducer {
    @Value("${kafka.topic.delete:chat-delete}")
    private String chatDeleteTopic;
    private final KafkaTemplate<String, DeleteChatDto> kafkaTemplate;

    public void sendMessageToKafka(DeleteChatDto deleteChatDto) {
        kafkaTemplate.send(chatDeleteTopic, String.valueOf(deleteChatDto.chatId()), deleteChatDto);
    }
}
