package messenger.gatewayservice.kafka;

import messenger.commonlibs.dto.messageservice.GatewayMessageEventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:gateway-message-delivery}")
    private String messageGroupId;

    @Bean
    public ConsumerFactory<String, GatewayMessageEventDto> messageEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(messageGroupId),
                new StringDeserializer(),
                jsonDeserializer(GatewayMessageEventDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GatewayMessageEventDto> messageEventContainerFactory(
            ConsumerFactory<String, GatewayMessageEventDto> messageEventConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, GatewayMessageEventDto> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(messageEventConsumerFactory);
        return containerFactory;
    }

    private Map<String, Object> baseConsumerProperties(String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return properties;
    }

    private <T> JacksonJsonDeserializer<T> jsonDeserializer(Class<T> clazz) {
        JacksonJsonDeserializer<T> deserializer = new JacksonJsonDeserializer<>(clazz);
        deserializer.setUseTypeHeaders(false);
        return deserializer;
    }
}
