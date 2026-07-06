package messenger.chatservice.kafka;

import messenger.commonlibs.dto.chatservice.DeleteChatDto;
import messenger.commonlibs.dto.chatservice.RemoveChatParticipantDto;
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

    private Map<String, Object> baseProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }

    @Bean
    public ProducerFactory<String, DeleteChatDto> producerFactory() {
        JacksonJsonSerializer<DeleteChatDto> serializer = new JacksonJsonSerializer<>();
        serializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(baseProducerProps(), new StringSerializer(), serializer);
    }

    @Bean
    public KafkaTemplate<String, DeleteChatDto> kafkaTemplate(ProducerFactory<String, DeleteChatDto> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, RemoveChatParticipantDto> participantRemoveProducerFactory() {
        JacksonJsonSerializer<RemoveChatParticipantDto> serializer = new JacksonJsonSerializer<>();
        serializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(baseProducerProps(), new StringSerializer(), serializer);
    }

    @Bean
    public KafkaTemplate<String, RemoveChatParticipantDto> participantRemoveKafkaTemplate(
            ProducerFactory<String, RemoveChatParticipantDto> participantRemoveProducerFactory) {
        return new KafkaTemplate<>(participantRemoveProducerFactory);
    }
}
