package messenger.reactionservice.domain;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionDetailsRepository extends JpaRepository<ReactionDetails, Long> {
    int countByMessageIdAndUserId(@NotBlank String s, Long userId);

    Optional<ReactionDetails> findByUserIdAndMessageIdAndReactionType(Long userId, String messageId, messenger.commonlibs.dto.reactionservice.ReactionType reactionType);

    void deleteByMessageId(String messageId);

    void deleteByChatId(Long chatId);

    List<ReactionDetails> findByUserIdAndMessageId(Long userId, @NotBlank String messageId);

    List<ReactionDetails> findByUserIdAndMessageIdIn(Long userId, List<String> messageIds);
}
