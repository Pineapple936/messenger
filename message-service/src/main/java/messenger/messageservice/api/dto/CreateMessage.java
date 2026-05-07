package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateMessage(
        @NotNull
        @Positive
        Long chatId,

        String content,

        List<String> photoLinks,

        @Pattern(regexp = "^$|\\S.*")
        String repliedMessageId
) {
}
