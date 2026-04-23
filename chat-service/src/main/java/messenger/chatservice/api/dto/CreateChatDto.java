package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateChatDto(
        @NotNull
        @Positive
        Long toUserId
) {
}
