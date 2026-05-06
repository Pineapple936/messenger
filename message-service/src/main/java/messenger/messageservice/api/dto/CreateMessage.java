package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CreateMessage(
        @NotNull
        @Positive
        Long chatId,

        @NotBlank
        String content,

        @NotNull
        LocalDateTime sendAt,

        @Pattern(regexp = "^$|\\S.*")
        String repliedMessageId
) {
}
