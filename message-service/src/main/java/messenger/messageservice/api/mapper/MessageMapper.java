package messenger.messageservice.api.mapper;

import messenger.commonlibs.dto.messageservice.ForwardedMessageDto;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.messageservice.domain.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {
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
}
