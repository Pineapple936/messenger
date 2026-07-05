package messenger.messageservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import messenger.commonlibs.dto.messageservice.ReactionOnMessage;
import messenger.messageservice.domain.Message;

import java.time.Instant;
import java.util.List;
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

        String content,

        @NotNull
        Boolean readStatus,

        @NotNull
        Boolean editStatus,

        @NotNull
        Instant sendAt,

        List<String> photoLinks,

        @NotNull
        Set<ReactionOnMessage> reactions,

        RepliedMessageInfo repliedMessage,

        ForwardedMessageInfo forwardedMessage
) {
        public MessageResponse(Message message, Set<ReactionOnMessage> reactions) {
            this(message, reactions, null, null);
        }

        public MessageResponse(
                Message message,
                Set<ReactionOnMessage> reactions,
                Message repliedMessage,
                Message forwardedMessage
        ) {
            this(
                message.getId(),
                message.getChatId(),
                message.getUserId(),
                forwardedMessage == null ? message.getContent() : forwardedMessage.getContent(),
                message.getReadStatus(),
                message.getEditStatus(),
                message.getSendAt(),
                forwardedMessage == null ? message.getPhotoLinks() : forwardedMessage.getPhotoLinks(),
                reactions,
                repliedMessage == null ? null : new RepliedMessageInfo(
                    repliedMessage.getId(),
                    repliedMessage.getUserId(),
                    repliedMessage.getContent(),
                    repliedMessage.getSendAt()
                ),
                forwardedMessage == null ? null : new ForwardedMessageInfo(forwardedMessage)
            );
        }
}
