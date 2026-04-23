package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatUsersDto(
        @NotNull
        @Positive
        Long userId1,

        @NotNull
        @Positive
        Long userId2
) {
}
