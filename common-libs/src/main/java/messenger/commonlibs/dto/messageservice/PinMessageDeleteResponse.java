package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;

public record PinMessageDeleteResponse(
        @Positive
        @NonNull
        Long chatId,

        @NotBlank
        String messageId,

        @NonNull
        @Positive
        Long unpinnedByUserId
) {
}
