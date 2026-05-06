package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record RepliedMessageInfo(
        @NotBlank
        String id,

        @Positive
        @NotNull
        Long userId,

        @NotBlank
        String content,

        @NotNull
        LocalDateTime sendAt
) {
}
