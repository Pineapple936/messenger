package messenger.messageservice.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, String> {
    Slice<Message> findByChatIdOrderBySendAtDescIdDesc(Long chatId, Pageable page);

    void deleteByChatId(Long chatId);
}
