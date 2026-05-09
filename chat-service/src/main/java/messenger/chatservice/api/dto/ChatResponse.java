package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record ChatResponse(
        @Positive
        @NotNull
        Long chatId,

        @NotBlank
        String chatName,

        @NotNull
        Instant lastMessageAt,

        String lastMessagePreview,

        Long lastMessageUserId,

        Boolean lastMessageHasMedia,

        String avatarUrl
) {
}
