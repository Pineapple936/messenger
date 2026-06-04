package messenger.messageservice.kafka;

import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.chatservice.RemoveChatParticipantDto;
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

    @Value("${kafka.group.participant.remove:warehouse-group-participant-remove}")
    private String participantRemoveGroupId;

    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return props;
    }

    @Bean
    public ConsumerFactory<String, DeleteChatDto> chatDeleteConsumerFactory() {
        JacksonJsonDeserializer<DeleteChatDto> jsonDeserializer = new JacksonJsonDeserializer<>(DeleteChatDto.class);
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(deleteChatGroupId), new StringDeserializer(), jsonDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto> chatDeleteContainerFactory(
            ConsumerFactory<String, DeleteChatDto> consumerFactory) {
        var containerFactory = new ConcurrentKafkaListenerContainerFactory<String, DeleteChatDto>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(consumerFactory);
        return containerFactory;
    }

    @Bean
    public ConsumerFactory<String, RemoveChatParticipantDto> participantRemoveConsumerFactory() {
        JacksonJsonDeserializer<RemoveChatParticipantDto> jsonDeserializer = new JacksonJsonDeserializer<>(RemoveChatParticipantDto.class);
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(participantRemoveGroupId), new StringDeserializer(), jsonDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RemoveChatParticipantDto> participantRemoveContainerFactory(
            ConsumerFactory<String, RemoveChatParticipantDto> participantRemoveConsumerFactory) {
        var containerFactory = new ConcurrentKafkaListenerContainerFactory<String, RemoveChatParticipantDto>();
        containerFactory.setConcurrency(1);
        containerFactory.setConsumerFactory(participantRemoveConsumerFactory);
        return containerFactory;
    }
}
