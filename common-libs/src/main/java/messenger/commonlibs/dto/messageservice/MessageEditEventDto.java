package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MessageEditEventDto(
        @NotBlank
        String id,

        @Positive
        Long chatId,

        @NotBlank
        String content,

        @NotNull
        Boolean editStatus
) {
}
