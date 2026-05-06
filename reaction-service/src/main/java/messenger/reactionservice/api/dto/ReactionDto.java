package messenger.reactionservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.commonlibs.dto.reactionservice.ReactionType;

public record ReactionDto(
        @NotNull
        ReactionType reactionType,

        @NotBlank
        String messageId,

        @NotNull
        @Positive
        Long count
) {
}
