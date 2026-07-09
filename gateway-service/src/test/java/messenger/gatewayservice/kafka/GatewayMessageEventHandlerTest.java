package messenger.gatewayservice.kafka;

import messenger.commonlibs.dto.messageservice.*;
import messenger.gatewayservice.ws.ChatParticipantsClient;
import messenger.gatewayservice.ws.UserWebSocketSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayMessageEventHandlerTest {
    @Mock
    private ChatParticipantsClient chatParticipantsClient;

    @Mock
    private UserWebSocketSessions userWebSocketSessions;

    private GatewayMessageEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GatewayMessageEventHandler(chatParticipantsClient, userWebSocketSessions);
    }

    @Test
    void messageCreatedIsDeliveredToParticipantsExceptSender() {
        MessageDto message = MessageDto.builder()
                .id("message-1")
                .chatId(10L)
                .userId(1L)
                .content("hello")
                .readStatus(false)
                .editStatus(false)
                .sendAt(Instant.parse("2026-07-05T10:00:00Z"))
                .build();
        when(chatParticipantsClient.getChatUsers(10L)).thenReturn(List.of(1L, 2L, 3L));

        handler.handle(GatewayMessageEventDto.messageCreated(message));

        verify(userWebSocketSessions, never()).pushMessageToUser(1L, message);
        verify(userWebSocketSessions).pushMessageToUser(2L, message);
        verify(userWebSocketSessions).pushMessageToUser(3L, message);
    }

    @Test
    void messageDeletedIsNotDeliveredToDeletingUser() {
        MessageDeleteEventDto event = new MessageDeleteEventDto("message-1", 10L, 2L);
        when(chatParticipantsClient.getChatUsers(10L)).thenReturn(List.of(1L, 2L, 3L));

        handler.handle(GatewayMessageEventDto.messageDeleted(event));

        verify(userWebSocketSessions).pushMessageDeleteToUser(1L, event);
        verify(userWebSocketSessions, never()).pushMessageDeleteToUser(2L, event);
        verify(userWebSocketSessions).pushMessageDeleteToUser(3L, event);
    }

    @Test
    void messagePinnedIsDeliveredToAllParticipants() {
        PinMessageInfoDto event = new PinMessageInfoDto(10L, "message-1", "text", java.time.Instant.parse("2026-07-05T10:00:00Z"), 2L);
        when(chatParticipantsClient.getChatUsers(10L)).thenReturn(List.of(1L, 2L, 3L));

        handler.handle(GatewayMessageEventDto.messagePinned(event));

        verify(userWebSocketSessions).pushMessagePinToUser(1L, event);
        verify(userWebSocketSessions).pushMessagePinToUser(2L, event);
        verify(userWebSocketSessions).pushMessagePinToUser(3L, event);
    }

    @Test
    void messageUnpinnedIsDeliveredToAllParticipants() {
        PinMessageDeleteResponse event = new PinMessageDeleteResponse(10L, "message-1", 2L);
        when(chatParticipantsClient.getChatUsers(10L)).thenReturn(List.of(1L, 2L, 3L));

        handler.handle(GatewayMessageEventDto.messageUnpinned(event));

        verify(userWebSocketSessions).pushMessageUnpinToUser(1L, event);
        verify(userWebSocketSessions).pushMessageUnpinToUser(2L, event);
        verify(userWebSocketSessions).pushMessageUnpinToUser(3L, event);
    }
}
