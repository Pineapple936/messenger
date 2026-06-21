package messenger.messageservice.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class ForwardedMessage {
    ForwardedMessage(Message source) {
        if (source.getForwardedMessage() != null) {
            ForwardedMessage forwarded = source.getForwardedMessage();
            originalMessageId = forwarded.getOriginalMessageId();
            userId = forwarded.getUserId();
            content = forwarded.getContent();
            photoLinks = forwarded.getPhotoLinks() == null ? null : List.copyOf(forwarded.getPhotoLinks());
            sendAt = forwarded.getSendAt();
        } else {
            originalMessageId = source.getId();
            userId = source.getUserId();
            content = source.getContent();
            photoLinks = source.getPhotoLinks() == null ? null : List.copyOf(source.getPhotoLinks());
            sendAt = source.getSendAt();
        }
    }

    @Field(name = "original_message_id")
    private String originalMessageId;

    @Field(name = "user_id")
    private Long userId;

    @Field(name = "content")
    private String content;

    @Field(name = "photo_links")
    private List<String> photoLinks;

    @Field(name = "send_at")
    private Instant sendAt;
}
