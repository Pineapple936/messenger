package messenger.chatservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat")
@NoArgsConstructor
public class Chat {
    Chat(Long createUserId, Long userId2) {
        userId1 = createUserId;
        this.userId2 = userId2;
        lastMessageAt = LocalDateTime.now();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId1", nullable = false)
    private Long userId1;

    @Column(name = "userId2", nullable = false)
    private Long userId2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}
