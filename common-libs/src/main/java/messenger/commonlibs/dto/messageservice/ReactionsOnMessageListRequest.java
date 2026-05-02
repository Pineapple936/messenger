package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReactionsOnMessageListRequest(
        @Positive
        @NotNull
        Long userId,

        @NotNull
        List<@NotBlank String> messageIds
) {
}
