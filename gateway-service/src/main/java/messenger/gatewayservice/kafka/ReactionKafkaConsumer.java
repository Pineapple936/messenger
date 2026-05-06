package messenger.gatewayservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.reactionservice.GatewayReactionEventDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReactionKafkaConsumer {
    private final GatewayReactionEventHandler gatewayReactionEventHandler;

    @KafkaListener(
            topics = "${gateway.kafka.topic.reactionEvents:gateway-reaction-events}",
            containerFactory = "reactionEventContainerFactory"
    )
    public void consumeReactionEvent(GatewayReactionEventDto reactionEvent) {
        gatewayReactionEventHandler.handle(reactionEvent);
    }
}
