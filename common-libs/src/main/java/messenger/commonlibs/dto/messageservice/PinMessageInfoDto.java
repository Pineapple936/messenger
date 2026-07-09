package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;

import java.time.Instant;

public record PinMessageInfoDto(
        @Positive
        @NonNull
        Long chatId,

        @NotBlank
        String messageId,

        @NotBlank
        String content,

        @NonNull
        Instant messageSendAt,

        @Positive
        @NonNull
        Long pinnedByUserId
) {
}
