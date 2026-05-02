package messenger.reactionservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;
import messenger.reactionservice.api.dto.CreateReactionDto;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reaction",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_message_id_and_reaction_type",
               columnNames = {"message_id", "reaction_type"}
       )
)
@NoArgsConstructor
public class Reaction {
    Reaction(@Valid CreateReactionDto dto) {
        chatId = dto.chatId();
        messageId = dto.messageId();
        reactionType = dto.reactionType();
        count = 1L;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false)
    private messenger.commonlibs.dto.reactionservice.ReactionType reactionType;

    @Column(name = "count", nullable = false)
    private Long count;

    @CreationTimestamp
    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
