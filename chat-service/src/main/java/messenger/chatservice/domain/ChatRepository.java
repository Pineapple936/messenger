package messenger.chatservice.domain;

import messenger.chatservice.api.dto.ChatResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Chat c
            set c.lastMessageAt      = :lastMessageAt,
                c.lastMessagePreview = :preview,
                c.lastMessageUserId  = :userId,
                c.lastMessageHasMedia = :hasMedia
            where c.id = :chatId
              and (c.lastMessageAt is null or c.lastMessageAt < :lastMessageAt)
            """)
    int touchLastMessage(@Param("chatId")       Long chatId,
                         @Param("lastMessageAt") Instant lastMessageAt,
                         @Param("preview")       String preview,
                         @Param("userId")        Long userId,
                         @Param("hasMedia")      Boolean hasMedia);

    @Query("""
    SELECT new messenger.chatservice.api.dto.ChatResponse(
        c.id,
        COALESCE(cp.customChatName, c.name),
        c.lastMessageAt,
        c.lastMessagePreview,
        c.lastMessageUserId,
        c.lastMessageHasMedia,
        c.avatarUrl
    )
    FROM Chat c
    JOIN c.participants cp
    WHERE cp.userId = :userId
    AND cp.leftAt IS NULL
    ORDER BY c.lastMessageAt DESC""")
    Slice<ChatResponse> findChatPreviews(@Param("userId") Long userId, Pageable pageable);
}
