package messenger.messageservice.api.dto;

import messenger.messageservice.domain.ForwardedMessage;

import java.time.Instant;
import java.util.List;

public record ForwardedMessageInfo(
        Long userId,
        String content,
        List<String> photoLinks,
        Instant sendAt
) {
    public ForwardedMessageInfo(ForwardedMessage message) {
        this(
                message.getUserId(),
                message.getContent(),
                message.getPhotoLinks(),
                message.getSendAt()
        );
    }
}
