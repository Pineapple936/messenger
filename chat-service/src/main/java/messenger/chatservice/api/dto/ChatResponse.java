package messenger.chatservice.api.dto;

import java.time.LocalDateTime;

public record ChatResponse(
        Long id,
        Long userId1,
        Long userId2,
        LocalDateTime lastMessageAt
) {
}
