package messenger.gatewayservice.security;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthTokenValidationFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenValidationFilter.class);
    private static final String AUTH_PATH_PREFIX = "/auth/";
    private static final String VALIDATE_PATH = "/auth/validate";
    private static final String AUTH_UNAVAILABLE_MESSAGE = "Authentication service unavailable";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final WebClient authWebClient;

    public AuthTokenValidationFilter(@Qualifier("authWebClient") WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith(AUTH_PATH_PREFIX) || exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain
                    .filter(exchange)
                    .onErrorResume(ex -> {
                        if (path.startsWith(AUTH_PATH_PREFIX)) {
                            log.warn("Authentication service is unavailable while processing {} {}", exchange.getRequest().getMethod(), path, ex);
                            return writeResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE_MESSAGE);
                        }

                        return Mono.error(ex);
                    });
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return writeResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        return validateToken(exchange, authorizationHeader)
                .flatMap(userId -> {
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate().header(USER_ID_HEADER, userId).build())
                            .build();
                    return chain.filter(mutatedExchange);
                });
    }

    private Mono<String> validateToken(ServerWebExchange exchange, String authorizationHeader) {
        return authWebClient
                .get()
                .uri(VALIDATE_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is2xxSuccessful()) {
                        String userId = clientResponse.headers().asHttpHeaders().getFirst(USER_ID_HEADER);
                        if (userId == null || userId.isBlank()) {
                            log.warn("Authentication validation endpoint returned success without {}", USER_ID_HEADER);
                            return writeResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE_MESSAGE)
                                    .then(Mono.empty());
                        }
                        return Mono.just(userId);
                    }

                    if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED || clientResponse.statusCode() == HttpStatus.FORBIDDEN) {
                        return writeResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid token")
                                .then(Mono.empty());
                    }

                    log.warn("Authentication validation endpoint returned status {}", clientResponse.statusCode());
                    return writeResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE_MESSAGE)
                            .then(Mono.empty());
                })
                .onErrorResume(ex -> {
                    log.warn("Authentication validation request failed", ex);
                    return writeResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, AUTH_UNAVAILABLE_MESSAGE)
                            .then(Mono.empty());
                });
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        exchange.getResponse().getHeaders().setContentLength(payload.length);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(payload))
        );
    }
}
