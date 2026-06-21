package messenger.commonlibs.dto.messageservice;

import java.time.Instant;
import java.util.List;

public record ForwardedMessageDto(
        Long userId,
        String content,
        List<String> photoLinks,
        Instant sendAt
) {
}
