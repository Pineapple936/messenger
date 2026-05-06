package messenger.reactionservice.domain;

import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionDetailsRepository extends JpaRepository<ReactionDetails, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReactionDetails r WHERE r.userId = :userId AND r.messageId = :messageId")
    List<ReactionDetails> findByUserIdAndMessageIdForUpdate(@Param("userId") Long userId, @Param("messageId") String messageId);

    Optional<ReactionDetails> findByUserIdAndMessageIdAndReactionType(Long userId, String messageId, messenger.commonlibs.dto.reactionservice.ReactionType reactionType);

    void deleteByMessageId(String messageId);

    void deleteByChatId(Long chatId);

    List<ReactionDetails> findByUserIdAndMessageId(Long userId, @NotBlank String messageId);

    List<ReactionDetails> findByUserIdAndMessageIdIn(Long userId, List<String> messageIds);
}
