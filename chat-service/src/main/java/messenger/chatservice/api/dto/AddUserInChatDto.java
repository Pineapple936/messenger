package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.chatservice.domain.ChatRole;

public record AddUserInChatDto(
        @NotNull
        @Positive
        Long userId,

        @NotNull
        ChatRole role,

        @NotNull
        @Positive
        Long chatId
) {
}
