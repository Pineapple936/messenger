package messenger.reactionservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import messenger.reactionservice.api.dto.CreateReactionDto;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reaction_details",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_reaction_details_msg_user_type",
               columnNames = {"message_id", "user_id", "reaction_type"}
       )
)
@NoArgsConstructor
public class ReactionDetails {
    ReactionDetails(Long xUserId, CreateReactionDto dto) {
        chatId = dto.chatId();
        messageId = dto.messageId();
        userId = xUserId;
        reactionType = dto.reactionType();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false)
    private messenger.commonlibs.dto.reactionservice.ReactionType reactionType;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
