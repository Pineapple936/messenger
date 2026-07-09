package messenger.messageservice.domain;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Document(collection = "pinned_message")
@NoArgsConstructor
@CompoundIndex(
        name = "unique_chat_message_pin",
        def = "{'chat_id': 1, 'message_id': 1}",
        unique = true
)
public class PinnedMessage {
    PinnedMessage(Message message, long pinnedByUserId) {
        this.chatId = message.getChatId();
        this.messageId = message.getId();
        this.messageSendAt = message.getSendAt();
        this.pinnedByUserId = pinnedByUserId;
    }

    @Id
    private String id;

    @Field(name = "chat_id")
    @Positive
    @NonNull
    private Long chatId;

    @Field(name = "message_id")
    @NonNull
    private String messageId;

    @Field(name = "pinned_by_user_id")
    @Positive
    @NonNull
    private Long pinnedByUserId;

    @Field(name = "message_send_at")
    @NonNull
    private Instant messageSendAt;

    @CreatedDate
    @Field(name = "pinned_at")
    private Instant pinnedAt;
}
