package messenger.messageservice.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends MongoRepository<Message, String> {
    Slice<Message> findByChatIdOrderBySendAtDescIdDesc(Long chatId, Pageable limit);

    Optional<Message> findFirstByChatIdAndUserIdNotAndReadStatusFalseOrderBySendAtAscIdAsc(Long chatId, Long userId);

    @Query("{ 'chat_id': ?0, $or: [ { 'send_at': { $lt: ?1 } }, { 'send_at': ?1, '_id': { $lt: ?2 } } ] }")
    Slice<Message> findBefore(Long chatId, Instant sendAt, String id, Pageable limit);

    @Query("{ 'chat_id': ?0, $or: [ { 'send_at': { $gt: ?1 } }, { 'send_at': ?1, '_id': { $gt: ?2 } } ] }")
    Slice<Message> findAfter(Long chatId, Instant sendAt, String id, Pageable limit);

    void deleteByChatId(Long chatId);

    Boolean existsByUserIdAndId(Long userId, String id);

    List<Message> findByChatId(Long chatId);

    List<Message> findByPhotoLinksIn(List<String> photoLinks);
}
