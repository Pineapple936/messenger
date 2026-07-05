package messenger.reactionservice.domain;

import messenger.commonlibs.dto.reactionservice.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction> findByMessageId(String messageId);

    Optional<Reaction> findByMessageIdAndReactionType(String messageId, ReactionType reactionType);

    @Query(value = """
            insert into reaction (chat_id, message_id, reaction_type, count, created_at)
            values (:chatId, :messageId, :reactionType, 1, now())
            on conflict (message_id, reaction_type)
            do update set count = reaction.count + 1
            returning id
            """, nativeQuery = true)
    Long upsertAndIncrementReturningId(@Param("chatId") Long chatId,
                                       @Param("messageId") String messageId,
                                       @Param("reactionType") String reactionType);

    List<Reaction> findByMessageIdIn(List<String> messageIds);

    void deleteByMessageId(String messageId);

    void deleteByChatId(Long chatId);
}
