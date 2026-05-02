package messenger.reactionservice.kafka;

import messenger.commonlibs.dto.reactionservice.GatewayReactionEventDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, GatewayReactionEventDto> reactionEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(
                producerProperties(),
                new StringSerializer(),
                jsonSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, GatewayReactionEventDto> reactionEventKafkaTemplate(
            ProducerFactory<String, GatewayReactionEventDto> reactionEventProducerFactory
    ) {
        return new KafkaTemplate<>(reactionEventProducerFactory);
    }

    private Map<String, Object> producerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

    private <T> JacksonJsonSerializer<T> jsonSerializer() {
        JacksonJsonSerializer<T> serializer = new JacksonJsonSerializer<>();
        serializer.setAddTypeInfo(false);
        return serializer;
    }
}
