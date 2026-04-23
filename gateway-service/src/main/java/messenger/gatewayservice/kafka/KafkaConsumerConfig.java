package messenger.gatewayservice.kafka;

import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
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

    @Value("${gateway.kafka.group.message-read:gateway-message-read-delivery}")
    private String messageReadGroupId;

    @Value("${gateway.kafka.group.message-delete:gateway-message-delete-delivery}")
    private String messageDeleteGroupId;

    @Bean
    public ConsumerFactory<String, MessageDto> messageConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(messageGroupId),
                new StringDeserializer(),
                jsonDeserializer(MessageDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageDto> messageContainerFactory(
            ConsumerFactory<String, MessageDto> messageConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MessageDto> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(messageConsumerFactory);
        return containerFactory;
    }

    @Bean
    public ConsumerFactory<String, MessageReadEventDto> messageReadConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(messageReadGroupId),
                new StringDeserializer(),
                jsonDeserializer(MessageReadEventDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageReadEventDto> messageReadContainerFactory(
            ConsumerFactory<String, MessageReadEventDto> messageReadConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MessageReadEventDto> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(messageReadConsumerFactory);
        return containerFactory;
    }

    @Bean
    public ConsumerFactory<String, MessageDeleteEventDto> messageDeleteConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProperties(messageDeleteGroupId),
                new StringDeserializer(),
                jsonDeserializer(MessageDeleteEventDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageDeleteEventDto> messageDeleteContainerFactory(
            ConsumerFactory<String, MessageDeleteEventDto> messageDeleteConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, MessageDeleteEventDto> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(messageDeleteConsumerFactory);
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
