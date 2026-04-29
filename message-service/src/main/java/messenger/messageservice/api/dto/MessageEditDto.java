package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MessageEditDto(
        @NotBlank
        String id,

        @NotBlank
        String content
) {
}
