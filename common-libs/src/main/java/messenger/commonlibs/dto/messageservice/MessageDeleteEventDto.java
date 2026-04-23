package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotNull;

public record MessageDeleteEventDto(
        @NotNull
        String id,

        @NotNull
        Long chatId,

        @NotNull
        Long deletedByUserId
) {
}
