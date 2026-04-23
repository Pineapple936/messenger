package messenger.gatewayservice.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;

@Component
public class MessageBridgeWebSocketHandler implements WebSocketHandler {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String PRESENCE_SUBSCRIBE_EVENT = "presence_subscribe";
    private static final String UNAUTHORIZED_RESPONSE = "{\"status\":\"error\",\"code\":401,\"message\":\"Unauthorized\"}";

    private final UserWebSocketSessions userWebSocketSessions;
    private final ObjectMapper objectMapper;

    public MessageBridgeWebSocketHandler(UserWebSocketSessions userWebSocketSessions, ObjectMapper objectMapper) {
        this.userWebSocketSessions = userWebSocketSessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String userIdHeader = session.getHandshakeInfo().getHeaders().getFirst(USER_ID_HEADER);
        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return sendUnauthorizedAndClose(session);
        }

        Sinks.Many<String> outboundSink = userWebSocketSessions.register(userId);
        Flux<String> outboundMessages = outboundSink.asFlux();

        return session.send(outboundMessages.map(session::textMessage))
                .and(session.receive()
                        .map(message -> message.getPayloadAsText())
                        .doOnNext(payload -> handleIncomingPayload(outboundSink, payload))
                        .then())
                .doFinally(signalType -> userWebSocketSessions.unregister(userId, outboundSink));
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Mono<Void> sendUnauthorizedAndClose(WebSocketSession session) {
        return session.send(Mono.just(session.textMessage(UNAUTHORIZED_RESPONSE)))
                .then(session.close(CloseStatus.POLICY_VIOLATION));
    }

    private void handleIncomingPayload(Sinks.Many<String> outboundSink, String payload) {
        IncomingClientEvent event = parseEvent(payload);
        if (event == null || !PRESENCE_SUBSCRIBE_EVENT.equals(event.type())) {
            return;
        }

        List<Long> userIds = event.userIds() == null ? List.of() : event.userIds();
        userWebSocketSessions.updatePresenceSubscriptions(outboundSink, userIds);
    }

    private IncomingClientEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, IncomingClientEvent.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record IncomingClientEvent(String type, List<Long> userIds) {
    }
}
