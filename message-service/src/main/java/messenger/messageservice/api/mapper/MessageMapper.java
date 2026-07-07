package messenger.messageservice.api.mapper;

import messenger.commonlibs.dto.messageservice.ForwardedMessageDto;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.MessageAccessInfoDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageEditEventDto;
import messenger.commonlibs.dto.messageservice.MessageReadEventDto;
import messenger.commonlibs.dto.messageservice.PinMessageDto;
import messenger.commonlibs.dto.messageservice.ReactionOnMessage;
import messenger.messageservice.api.dto.CreateMessage;
import messenger.messageservice.api.dto.MessageResponse;
import messenger.messageservice.domain.Message;
import messenger.messageservice.domain.PinnedMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MessageMapper {
    public MessageDto toDto(Long userId, CreateMessage request, Instant sendAt) {
        return MessageDto.builder()
                .chatId(request.chatId())
                .userId(userId)
                .content(request.content())
                .photoLinks(request.photoLinks())
                .readStatus(false)
                .editStatus(false)
                .sendAt(sendAt)
                .repliedMessageId(request.repliedMessageId())
                .forwardedFromMessageId(request.forwardedFromMessageId())
                .build();
    }

    public MessageResponse toResponse(Message message, Set<ReactionOnMessage> reactions) {
        return toResponse(message, reactions, null, null);
    }

    public MessageResponse toResponse(
            Message message,
            Set<ReactionOnMessage> reactions,
            Message repliedMessage,
            Message forwardedMessage
    ) {
        return new MessageResponse(message, reactions, repliedMessage,
                forwardedMessage);
    }

    public MessageResponse toResponse(
            Message message,
            Map<String, Set<ReactionOnMessage>> reactions,
            Map<String, Message> referencedMessages
    ) {
        return toResponse(
                message,
                reactions.getOrDefault(message.getId(), Set.of()),
                message.getRepliedMessageId() == null ? null : referencedMessages.get(message.getRepliedMessageId()),
                message.getForwardedMessageId() == null ? null : referencedMessages.get(message.getForwardedMessageId())
        );
    }

    public List<MessageResponse> toResponses(
            List<Message> messages,
            Map<String, Set<ReactionOnMessage>> reactions,
            Map<String, Message> referencedMessages
    ) {
        return messages.stream()
                .map(message -> toResponse(message, reactions, referencedMessages))
                .toList();
    }

    public MessageDto toDto(Message message) {
        return toDto(message, null);
    }

    public MessageDto toDto(Message message, Message forwardedMessage) {
        Message displayMessage = forwardedMessage == null ? message : forwardedMessage;
        return MessageDto.builder()
                .id(message.getId())
                .chatId(message.getChatId())
                .userId(message.getUserId())
                .content(displayMessage.getContent())
                .readStatus(message.getReadStatus())
                .editStatus(message.getEditStatus())
                .sendAt(message.getSendAt())
                .photoLinks(displayMessage.getPhotoLinks())
                .repliedMessageId(message.getRepliedMessageId())
                .forwardedFromMessageId(message.getForwardedMessageId())
                .forwardedMessage(forwardedMessage == null ? null : new ForwardedMessageDto(
                        forwardedMessage.getUserId(),
                        forwardedMessage.getContent(),
                        forwardedMessage.getPhotoLinks(),
                        forwardedMessage.getSendAt()
                ))
                .build();
    }

    public PinMessageDto toPinDto(PinnedMessage pinnedMessage) {
        return new PinMessageDto(
                pinnedMessage.getChatId(),
                pinnedMessage.getMessageId(),
                pinnedMessage.getMessageSendAt(),
                pinnedMessage.getPinnedByUserId()
        );
    }

    public PinMessageDto toPinEvent(Message message, Long userId) {
        return new PinMessageDto(
                message.getChatId(),
                message.getId(),
                message.getSendAt(),
                userId
        );
    }

    public PinMessageDto toPinEvent(PinnedMessage pinnedMessage, Long userId) {
        return new PinMessageDto(
                pinnedMessage.getChatId(),
                pinnedMessage.getMessageId(),
                pinnedMessage.getMessageSendAt(),
                userId
        );
    }

    public MessageAccessInfoDto toAccessInfo(Message message) {
        return new MessageAccessInfoDto(message.getId(), message.getChatId());
    }

    public MessageReadEventDto toReadEvent(Message message, Long readerId) {
        return new MessageReadEventDto(
                message.getId(),
                message.getChatId(),
                readerId,
                message.getReadStatus()
        );
    }

    public MessageEditEventDto toEditEvent(Message message) {
        return new MessageEditEventDto(
                message.getId(),
                message.getChatId(),
                message.getContent(),
                message.getEditStatus()
        );
    }

    public MessageDeleteEventDto toDeleteEvent(Message message) {
        return new MessageDeleteEventDto(
                message.getId(),
                message.getChatId(),
                message.getUserId()
        );
    }
}
