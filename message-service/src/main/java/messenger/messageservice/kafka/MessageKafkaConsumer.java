package messenger.messageservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.messageservice.domain.MessageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageKafkaConsumer {
    private final MessageService messageService;

    @KafkaListener(
            topics = "${kafka.topic.delete.chat:chat-delete}",
            containerFactory = "chatDeleteContainerFactory"
    )
    public void consumeMessage(ConsumerRecord<String, DeleteChatDto> record) {
        messageService.deleteByChatId(record.value().chatId());
    }
}
