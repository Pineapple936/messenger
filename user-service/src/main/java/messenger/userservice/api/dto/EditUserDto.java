package messenger.userservice.api.dto;

import jakarta.validation.constraints.Size;

public record EditUserDto(
        @Size(min = 4, max = 50)
        String name,

        @Size(max = 50)
        String description
) {
}
