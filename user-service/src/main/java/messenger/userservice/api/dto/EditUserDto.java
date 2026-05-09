package messenger.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditUserDto(
        @NotBlank
        @Size(min = 4, max = 50)
        String name,

        @NotBlank
        @Size(max = 15)
        String tag,

        @Size(max = 50)
        String description
) {
}
