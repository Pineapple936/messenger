package messenger.reactionservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.messageservice.DeleteMessageDto;
import messenger.reactionservice.domain.ReactionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReactionKafkaConsumer {
    private final ReactionService reactionService;

    @KafkaListener(
            topics = "${kafka.topic.delete.message:message-delete}",
            containerFactory = "messageDeleteContainerFactory"
    )
    public void consumeDeleteMessage(ConsumerRecord<String, DeleteMessageDto> record) {
        reactionService.deleteMessageByIdWithoutCheckOwnerUser(record.value().messageId());
    }

    @KafkaListener(
            topics = "${kafka.topic.delete.chat:chat-delete}",
            containerFactory = "chatDeleteContainerFactory"
    )
    public void consumeDeleteChat(ConsumerRecord<String, DeleteChatDto> record) {
        reactionService.deleteByChatId(record.value().chatId());
    }
}
