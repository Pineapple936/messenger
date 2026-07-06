package messenger.reactionservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import messenger.commonlibs.dto.reactionservice.ReactionType;

public record CreateReactionDto(
        @NotBlank
        String messageId,

        @NotNull
        ReactionType reactionType
) {
}
