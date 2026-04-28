package messenger.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;

public record UserDetailsResponse(
        @NonNull
        @Positive
        Long userId,

        @NotBlank
        String tag,

        @NotBlank
        String name,

        String description
) {
}
