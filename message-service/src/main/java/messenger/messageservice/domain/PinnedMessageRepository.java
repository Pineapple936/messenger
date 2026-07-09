package messenger.messageservice.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PinnedMessageRepository extends MongoRepository<PinnedMessage, String> {
    Optional<PinnedMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    List<PinnedMessage> findAllByChatIdOrderByMessageSendAtAsc(Long chatId);
}
