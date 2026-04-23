package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MessageReadListDto(
        @NotNull
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank String> ids
) {
}
