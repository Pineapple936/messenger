package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;

public record ReactionsOnMessageListResponse (
    @NotNull
    Map<@NotBlank String, @NotNull Set<@NotNull ReactionOnMessage>> reactions
) {
}
