package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotNull;

public record MessageReadEventDto(
        @NotNull
        String id,

        @NotNull
        Long chatId,

        @NotNull
        Long readerId,

        @NotNull
        Boolean readStatus
) {
}
