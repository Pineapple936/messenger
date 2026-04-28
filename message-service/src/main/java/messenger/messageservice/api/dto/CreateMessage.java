package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateMessage(
        @NotNull
        @Positive
        Long chatId,

        @NotBlank
        String content,

        @Pattern(regexp = "^$|\\S.*")
        String repliedMessageId
) {
}
