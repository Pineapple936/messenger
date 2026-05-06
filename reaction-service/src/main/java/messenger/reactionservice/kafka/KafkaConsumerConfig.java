package messenger.reactionservice.kafka;

import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.messageservice.DeleteMessageDto;
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

    @Value("${kafka.group.delete.message:warehouse-group-delete-message}")
    private String deleteMessageGroupId;

    @Value("${kafka.group.delete.chat:warehouse-group-delete-chat-reaction}")
    private String deleteChatGroupId;

    @Bean
    public ConsumerFactory<String, DeleteMessageDto> messageDeleteConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, deleteMessageGroupId);
        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new JacksonJsonDeserializer<>(DeleteMessageDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeleteMessageDto> messageDeleteContainerFactory(
            ConsumerFactory<String, DeleteMessageDto> messageDeleteConsumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, DeleteMessageDto>();
        factory.setConcurrency(1);
        factory.setConsumerFactory(messageDeleteConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, DeleteChatDto> chatDeleteConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, deleteChatGroupId);
        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new JacksonJsonDeserializer<>(DeleteChatDto.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto> chatDeleteContainerFactory(
            ConsumerFactory<String, DeleteChatDto> chatDeleteConsumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto>();
        factory.setConcurrency(1);
        factory.setConsumerFactory(chatDeleteConsumerFactory);
        return factory;
    }
}
