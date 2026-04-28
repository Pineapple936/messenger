package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.chatservice.domain.ChatRole;

public record UpdateRoleUserDto (
    @Positive
    @NotNull
    Long userId,

    @Positive
    @NotNull
    Long chatId,

    @NotNull
    ChatRole role
) {

}