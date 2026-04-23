package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record MessageResponse(
        @NotBlank
        String id,

        @NotNull
        @Positive
        Long chatId,

        @NotNull
        @Positive
        Long userId,

        @NotBlank
        String content,

        @NotNull
        Boolean readStatus,

        @NotNull
        LocalDateTime sendAt
) {
}
