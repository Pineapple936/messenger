package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;

public record DeleteMessageDto(
        @NotBlank
        String messageId
) {
}
