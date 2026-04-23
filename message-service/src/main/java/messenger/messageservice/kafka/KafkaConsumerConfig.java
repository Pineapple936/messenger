package messenger.messageservice.kafka;

import messenger.commonlibs.dto.chatservice.DeleteChatDto;
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

    @Value("${kafka.group.delete.chat:warehouse-group-delete-chat}")
    private String deleteChatGroupId;

    @Bean
    public ConsumerFactory<String, DeleteChatDto> chatDeleteConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, deleteChatGroupId);

        JacksonJsonDeserializer<DeleteChatDto> jsonDeserializer = new JacksonJsonDeserializer<>(DeleteChatDto.class);

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto> chatDeleteContainerFactory (
            ConsumerFactory<String, DeleteChatDto> consumerFactory
    ) {
        var containerFactory = new ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(consumerFactory);
        return containerFactory;
    }
}
