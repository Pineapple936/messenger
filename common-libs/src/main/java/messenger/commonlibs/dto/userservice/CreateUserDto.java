package messenger.commonlibs.dto.userservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.NonNull;

@Builder
public record CreateUserDto(
        @NonNull
        @PositiveOrZero
        Long id,

        @NotBlank
        @Size(min = 4, max = 50)
        String name,

        @NotBlank
        @Size(min = 3, max = 15)
        String tag,

        @Size(max = 50)
        String description,

        String avatarUrl
) {
}
