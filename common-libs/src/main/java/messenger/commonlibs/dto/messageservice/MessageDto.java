package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MessageDto(
        String id,

        @NotNull
        @Positive
        Long chatId,

        @NotNull
        @Positive
        Long userId,

        @NotBlank
        String content,

        @NotNull
        Boolean readStatus,

        @NotNull
        LocalDateTime sendAt
) {
}
