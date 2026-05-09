package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.chatservice.domain.ChatRole;

public record ChatParticipantDto(
        @Positive
        @NotNull
        Long userId,

        @NotNull
        ChatRole role,

        String customChatName
) {
}
