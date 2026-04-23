package messenger.chatservice.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    @Query("""
            select count(c) > 0
            from Chat c
            where (c.userId1 = :userA and c.userId2 = :userB)
               or (c.userId1 = :userB and c.userId2 = :userA)
            """)
    boolean existsByUsers(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
            select count(c) > 0
            from Chat c
            where c.id = :chatId
              and (c.userId1 = :userId or c.userId2 = :userId)
            """)
    boolean hasUser(@Param("chatId") Long chatId, @Param("userId") Long userId);

    @Query("""
            select c
            from Chat c
            where c.userId1 = :userId
               or c.userId2 = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc
            """)
    Slice<Chat> findByUserIdOrderByLastMessageAtDesc(@Param("userId") Long userId, Pageable page);

    @Query("""
            select c
            from Chat c
            where c.userId1 = :userId
               or c.userId2 = :userId
            """)
    List<Chat> findAllByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Chat c
            set c.lastMessageAt = :lastMessageAt
            where c.id = :chatId
              and (c.lastMessageAt is null or c.lastMessageAt < :lastMessageAt)
            """)
    int touchLastMessageAt(@Param("chatId") Long chatId, @Param("lastMessageAt") LocalDateTime lastMessageAt);
}
