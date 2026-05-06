package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.commonlibs.dto.messageservice.ReactionOnMessage;
import messenger.messageservice.domain.Message;

import java.time.LocalDateTime;
import java.util.Set;

public record MessageResponse(
        @NotBlank
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
        LocalDateTime sendAt,

        @NotNull
        Set<ReactionOnMessage> reactions,

        RepliedMessageInfo repliedMessage
) {
        public MessageResponse(Message message, Set<ReactionOnMessage> reactions) {
            this(
                message.getId(),
                message.getChatId(),
                message.getUserId(),
                message.getContent(),
                message.getReadStatus(),
                message.getEditStatus(),
                message.getSendAt(),
                reactions,
                message.getRepliedMessage() == null ? null : new RepliedMessageInfo(
                    message.getRepliedMessage().getId(),
                    message.getRepliedMessage().getUserId(),
                    message.getRepliedMessage().getContent(),
                    message.getRepliedMessage().getSendAt()
                )
            );
        }
}
