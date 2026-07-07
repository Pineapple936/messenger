package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;

import java.time.Instant;

public record PinMessageDto(
        @Positive
        @NonNull
        Long chatId,

        @NotBlank
        String messageId,

        @NonNull
        Instant messageSendAt,

        @Positive
        @NonNull
        Long pinnedByUserId
) {
}
