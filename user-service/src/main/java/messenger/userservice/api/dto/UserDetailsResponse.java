package messenger.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UserDetailsResponse(
        @NotNull
        @Positive
        Long userId,

        @NotBlank
        String tag,

        @NotBlank
        String name,

        String description,

        String avatarUrl
) {
}
