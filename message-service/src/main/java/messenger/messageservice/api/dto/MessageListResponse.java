package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MessageListResponse(
        @NotNull
        List<MessageResponse> messages,

        String anchorMessageId,

        boolean hasBefore,

        boolean hasAfter
) {
}
