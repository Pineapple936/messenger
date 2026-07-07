package messenger.commonlibs.dto;

import messenger.commonlibs.dto.messageservice.GatewayMessageEventDto;
import messenger.commonlibs.dto.messageservice.MessageDeleteEventDto;
import messenger.commonlibs.dto.messageservice.MessageDto;
import messenger.commonlibs.dto.messageservice.PinMessageDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayMessageEventDtoTest {
    @Test
    void messageCreatedFactorySetsTypeChatIdAndPayload() {
        MessageDto message = MessageDto.builder()
                .id("message-1")
                .chatId(10L)
                .userId(1L)
                .content("hello")
                .readStatus(false)
                .editStatus(false)
                .sendAt(Instant.parse("2026-07-05T10:00:00Z"))
                .build();

        GatewayMessageEventDto event = GatewayMessageEventDto.messageCreated(message);

        assertThat(event.type()).isEqualTo(GatewayMessageEventDto.EventType.MESSAGE_CREATED);
        assertThat(event.chatId()).isEqualTo(10L);
        assertThat(event.message()).isSameAs(message);
        assertThat(event.messageDeleteEvent()).isNull();
        assertThat(event.pinMessageEvent()).isNull();
    }

    @Test
    void messageDeletedFactorySetsDeletePayloadOnly() {
        MessageDeleteEventDto deleteEvent = new MessageDeleteEventDto("message-1", 10L, 2L);

        GatewayMessageEventDto event = GatewayMessageEventDto.messageDeleted(deleteEvent);

        assertThat(event.type()).isEqualTo(GatewayMessageEventDto.EventType.MESSAGE_DELETED);
        assertThat(event.chatId()).isEqualTo(10L);
        assertThat(event.message()).isNull();
        assertThat(event.messageDeleteEvent()).isSameAs(deleteEvent);
    }

    @Test
    void messagePinnedFactorySetsPinPayloadOnly() {
        PinMessageDto pinEvent = new PinMessageDto(10L, "message-1", Instant.parse("2026-07-05T10:00:00Z"), 2L);

        GatewayMessageEventDto event = GatewayMessageEventDto.messagePinned(pinEvent);

        assertThat(event.type()).isEqualTo(GatewayMessageEventDto.EventType.MESSAGE_PINNED);
        assertThat(event.chatId()).isEqualTo(10L);
        assertThat(event.message()).isNull();
        assertThat(event.pinMessageEvent()).isSameAs(pinEvent);
    }
}
