package messenger.gatewayservice.kafka;

import lombok.RequiredArgsConstructor;
import messenger.commonlibs.dto.messageservice.GatewayMessageEventDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageKafkaConsumer {
    private final GatewayMessageEventHandler gatewayMessageEventHandler;

    @KafkaListener(
            topics = "${gateway.kafka.topic.messageEvents:gateway-message-events}",
            containerFactory = "messageEventContainerFactory"
    )
    public void consumeMessageEvent(GatewayMessageEventDto messageEvent) {
        gatewayMessageEventHandler.handle(messageEvent);
    }
}
