package messenger.chatservice.kafka;

import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.userservice.DeleteUserDto;
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

    @Value("${kafka.group.message:warehouse-group-message}")
    private String messageGroupId;

    @Value("${kafka.group.user.delete:warehouse-group-user-delete}")
    private String deleteUserGroupId;

    @Bean
    public ConsumerFactory<String, MessageDto> messageConsumerFactory() {
        return buildConsumerFactory(MessageDto.class, messageGroupId);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageDto> messageContainerFactory(
            ConsumerFactory<String, MessageDto> messageConsumerFactory
    ) {
        return buildContainerFactory(messageConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, DeleteUserDto> deleteUserConsumerFactory() {
        return buildConsumerFactory(DeleteUserDto.class, deleteUserGroupId);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeleteUserDto> deleteUserContainerFactory(
            ConsumerFactory<String, DeleteUserDto> deleteUserConsumerFactory
    ) {
        return buildContainerFactory(deleteUserConsumerFactory);
    }

    private <T> ConsumerFactory<String, T> buildConsumerFactory(Class<T> payloadClass, String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        JacksonJsonDeserializer<T> jsonDeserializer = new JacksonJsonDeserializer<>(payloadClass);

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> buildContainerFactory(
            ConsumerFactory<String, T> consumerFactory
    ) {
        var containerFactory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(consumerFactory);
        return containerFactory;
    }
}
