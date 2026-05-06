package messenger.reactionservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.commonlibs.dto.reactionservice.ReactionType;

public record CreateReactionDto(
        @NotNull
        @Positive
        Long chatId,

        @NotBlank
        String messageId,

        @NotNull
        ReactionType reactionType
) {
}
