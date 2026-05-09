package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

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
        Boolean editStatus,

        @NotNull
        Instant sendAt,

        List<String> photoLinks,

        @Pattern(regexp = "^$|\\S.*")
        String repliedMessageId
) {
}
