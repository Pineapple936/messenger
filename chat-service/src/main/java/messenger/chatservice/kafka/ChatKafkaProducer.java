package messenger.chatservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.chatservice.RemoveChatParticipantDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatKafkaProducer {
    @Value("${kafka.topic.delete:chat-delete}")
    private String chatDeleteTopic;

    @Value("${kafka.topic.participant.remove:chat-participant-remove}")
    private String participantRemoveTopic;

    private final KafkaTemplate<String, DeleteChatDto> kafkaTemplate;
    private final KafkaTemplate<String, RemoveChatParticipantDto> participantRemoveKafkaTemplate;

    public void sendMessageToKafka(DeleteChatDto deleteChatDto) {
        kafkaTemplate.send(chatDeleteTopic, String.valueOf(deleteChatDto.chatId()), deleteChatDto);
    }

    public void sendParticipantRemoveEvent(RemoveChatParticipantDto dto) {
        participantRemoveKafkaTemplate.send(participantRemoveTopic, String.valueOf(dto.chatId()), dto);
    }
}
