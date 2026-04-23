package messenger.gatewayservice.ws;

import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class MessageWebSocketBridgeFilter implements WebFilter {
    private static final String MESSAGE_PATH = "/message";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final MessageBridgeWebSocketHandler webSocketHandler;
    private final WebSocketService webSocketService;

    public MessageWebSocketBridgeFilter(MessageBridgeWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketService = new HandshakeWebSocketService();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!isMessageWebSocketUpgrade(exchange)) {
            return chain.filter(exchange);
        }

        String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            return writeResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing user id");
        }

        return webSocketService.handleRequest(exchange, webSocketHandler);
    }

    private boolean isMessageWebSocketUpgrade(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String upgrade = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE);

        return MESSAGE_PATH.equals(path)
                && exchange.getRequest().getMethod() == HttpMethod.GET
                && "websocket".equalsIgnoreCase(upgrade);
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        exchange.getResponse().getHeaders().setContentLength(payload.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(payload)));
    }
}
