package messenger.userservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.userservice.DeleteUserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserKafkaProducer {
    @Value("${kafka.topic.user.delete:user-delete}")
    private String userDeleteTopic;
    private final KafkaTemplate<String, DeleteUserDto> kafkaTemplate;

    public void sendMessageToKafka(DeleteUserDto deleteUserDto) {
        kafkaTemplate.send(userDeleteTopic, deleteUserDto);
    }
}
