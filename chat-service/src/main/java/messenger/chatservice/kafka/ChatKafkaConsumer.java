package messenger.chatservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.chatservice.domain.ChatService;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.userservice.DeleteUserDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatKafkaConsumer {
    private final ChatService chatService;

    @KafkaListener(
            topics = "${kafka.topic.message:chat-messages}",
            containerFactory = "messageContainerFactory"
    )
    public void consumeMessage(ConsumerRecord<String, MessageDto> record) {
        MessageDto msg = record.value();
        boolean hasMedia = msg.photoLinks() != null && !msg.photoLinks().isEmpty();
        chatService.touchChatLastMessage(msg.chatId(), msg.sendAt(), msg.content(), msg.userId(), hasMedia);
    }

    @KafkaListener(
            topics = "${kafka.topic.user.delete:user-delete}",
            containerFactory = "deleteUserContainerFactory"
    )
    public void consumeUserDelete(ConsumerRecord<String, DeleteUserDto> record) {
        chatService.deleteByUserId(record.value().userId());
    }
}
