package messenger.messageservice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import messenger.commonlibs.dto.messageservice.MessageDto;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "message")
@NoArgsConstructor
public class Message {
    Message(MessageDto dto, Message repliedMessage, Message forwardedMessage) {
        chatId = dto.chatId();
        userId = dto.userId();
        content = forwardedMessage == null ? dto.content() : null;
        readStatus = dto.readStatus();
        editStatus = dto.editStatus();
        sendAt = dto.sendAt();
        repliedMessageId = repliedMessage == null ? null : repliedMessage.getId();
        forwardedMessageId = forwardedMessage == null ? null : forwardedMessage.getId();

        if(forwardedMessage == null && dto.photoLinks() != null) {
            photoLinks = List.copyOf(dto.photoLinks());
        }
    }

    @Id
    private String id;

    @Field(name = "chat_id")
    @NonNull
    @Positive
    private Long chatId;

    @Field(name = "user_id")
    @NonNull
    @Positive
    private Long userId;

    @Field(name = "content")
    private String content;

    @Field(name = "photo_links")
    @Size(max = 10)
    private List<@NotBlank String> photoLinks;

    @Field(name = "read_status")
    @NonNull
    private Boolean readStatus;

    @Field(name = "edit_status")
    @NonNull
    private Boolean editStatus = false;

    @Field(name = "send_at")
    @NonNull
    private Instant sendAt;

    @Field(name = "replied_message_id")
    private String repliedMessageId;

    @Field(name = "forwarded_message_id")
    private String forwardedMessageId;
}
