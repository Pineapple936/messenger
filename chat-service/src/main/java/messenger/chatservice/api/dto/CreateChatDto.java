package messenger.chatservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import messenger.chatservice.domain.ChatType;

import java.util.List;

public record CreateChatDto(
        @NotBlank
        @Size(max = 63)
        String name,

        @NotNull
        ChatType chatType,

        @NotNull
        List<@NotNull Long> participantIds
) {
}
