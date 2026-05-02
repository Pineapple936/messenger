package messenger.reactionservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction> findByMessageId(String messageId);

    Optional<Reaction> findByMessageIdAndReactionType(String messageId, messenger.commonlibs.dto.reactionservice.ReactionType reactionType);

    List<Reaction> findByMessageIdIn(List<String> messageIds);

    void deleteByMessageId(String messageId);

    void deleteByChatId(Long chatId);
}
