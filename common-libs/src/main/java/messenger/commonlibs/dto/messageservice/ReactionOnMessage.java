package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.commonlibs.dto.reactionservice.ReactionType;

public record ReactionOnMessage(
        @NotNull
        ReactionType reactionType,

        @Positive
        @NotNull
        Long count,

        @NotNull
        Boolean reactedByCurrentUser
) {
}
