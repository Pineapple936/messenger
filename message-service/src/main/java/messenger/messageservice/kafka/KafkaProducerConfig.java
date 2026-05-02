package messenger.messageservice.kafka;

import messenger.commonlibs.dto.messageservice.DeleteMessageDto;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.GatewayMessageEventDto;
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
    public ProducerFactory<String, DeleteMessageDto> messageDeleteProducerFactory() {
        return new DefaultKafkaProducerFactory<>(
                baseProducerProperties(),
                new StringSerializer(),
                jsonSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, DeleteMessageDto> messageDeleteKafkaTemplate(
            ProducerFactory<String, DeleteMessageDto> messageProducerFactory
    ) {
        return new KafkaTemplate<>(messageProducerFactory);
    }

    @Bean
    public ProducerFactory<String, MessageDto> messageProducerFactory() {
        return new DefaultKafkaProducerFactory<>(
                baseProducerProperties(),
                new StringSerializer(),
                jsonSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, MessageDto> messageKafkaTemplate(
            ProducerFactory<String, MessageDto> messageProducerFactory
    ) {
        return new KafkaTemplate<>(messageProducerFactory);
    }

    @Bean
    public ProducerFactory<String, GatewayMessageEventDto> gatewayMessageEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(
                baseProducerProperties(),
                new StringSerializer(),
                jsonSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, GatewayMessageEventDto> gatewayMessageEventKafkaTemplate(
            ProducerFactory<String, GatewayMessageEventDto> gatewayMessageEventProducerFactory
    ) {
        return new KafkaTemplate<>(gatewayMessageEventProducerFactory);
    }

    private Map<String, Object> baseProducerProperties() {
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
