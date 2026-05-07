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
    Message(MessageDto dto, Message xRepliedMessage) {
        chatId = dto.chatId();
        userId = dto.userId();
        content = dto.content();
        readStatus = dto.readStatus();
        editStatus = dto.editStatus();
        sendAt = dto.sendAt();
        repliedMessage = xRepliedMessage;

        if(dto.photoLinks() != null) {
            photoLinks = dto.photoLinks();
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

    @Field(name = "replied_message")
    private Message repliedMessage;
}
