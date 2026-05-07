package messenger.messageservice.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    Slice<Message> findByChatIdOrderBySendAtDescIdDesc(Long chatId, Pageable page);

    void deleteByChatId(Long chatId);

    Boolean existsByUserIdAndId(Long userId, String id);

    List<Message> findByChatId(Long chatId);
}
