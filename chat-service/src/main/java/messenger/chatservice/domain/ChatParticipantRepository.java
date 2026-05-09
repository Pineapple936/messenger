package messenger.chatservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    List<ChatParticipant> findByChatId(Long chatId);

    boolean existsByChatIdAndUserIdAndLeftAtIsNull(Long chatId, Long userId);

    Optional<ChatParticipant> findByChatIdAndUserIdAndLeftAtIsNull(Long chatId, Long userId);

    List<ChatParticipant> findByUserIdAndLeftAtIsNull(Long userId);
}
