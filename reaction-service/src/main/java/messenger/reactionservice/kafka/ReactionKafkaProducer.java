package messenger.reactionservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.reactionservice.GatewayReactionEventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReactionKafkaProducer {
    private final KafkaTemplate<String, GatewayReactionEventDto> reactionEventKafkaTemplate;

    @Value("${kafka.topic.gateway.reactionEvents:gateway-reaction-events}")
    private String reactionEventsTopic;

    public void sendReactionEvent(GatewayReactionEventDto dto) {
        reactionEventKafkaTemplate.send(reactionEventsTopic, dto.chatId().toString(), dto);
    }
}
