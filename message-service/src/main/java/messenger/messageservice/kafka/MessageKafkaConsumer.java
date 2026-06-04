package messenger.messageservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.chatservice.RemoveChatParticipantDto;
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
    public void consumeDeleteChat(ConsumerRecord<String, DeleteChatDto> record) {
        messageService.deleteByChatId(record.value().chatId());
    }

    @KafkaListener(
            topics = "${kafka.topic.participant.remove:chat-participant-remove}",
            containerFactory = "participantRemoveContainerFactory"
    )
    public void consumeParticipantRemove(ConsumerRecord<String, RemoveChatParticipantDto> record) {
        RemoveChatParticipantDto dto = record.value();
        messageService.evictChatMemberCache(dto.chatId(), dto.userId());
    }
}
