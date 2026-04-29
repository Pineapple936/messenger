package messenger.commonlibs.dto.messageservice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GatewayMessageEventDto(
        @NotNull
        EventType type,

        @NotNull
        @Positive
        Long chatId,

        MessageDto message,
        MessageReadEventDto messageReadEvent,
        MessageDeleteEventDto messageDeleteEvent
) {
    public enum EventType {
        MESSAGE_CREATED,
        MESSAGE_READ,
        MESSAGE_DELETED
    }

    public static GatewayMessageEventDto messageCreated(MessageDto message) {
        return new GatewayMessageEventDto(EventType.MESSAGE_CREATED, message.chatId(), message, null, null);
    }

    public static GatewayMessageEventDto messageRead(MessageReadEventDto messageReadEvent) {
        return new GatewayMessageEventDto(EventType.MESSAGE_READ, messageReadEvent.chatId(), null, messageReadEvent, null);
    }

    public static GatewayMessageEventDto messageDeleted(MessageDeleteEventDto messageDeleteEvent) {
        return new GatewayMessageEventDto(EventType.MESSAGE_DELETED, messageDeleteEvent.chatId(), null, null, messageDeleteEvent);
    }
}
