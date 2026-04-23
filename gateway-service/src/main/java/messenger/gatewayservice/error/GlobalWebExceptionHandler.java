package messenger.gatewayservice.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import messenger.commonlibs.dto.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalWebExceptionHandler implements WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode statusCode;
        String message;

        if (ex instanceof ResponseStatusException statusException) {
            statusCode = statusException.getStatusCode();
            message = statusException.getReason();
            if (message == null || message.isBlank()) {
                if (statusCode instanceof HttpStatus status) {
                    message = status.getReasonPhrase();
                } else {
                    message = "Request failed";
                }
            }
        } else if (ex instanceof WebClientResponseException webClientResponseException) {
            statusCode = webClientResponseException.getStatusCode();
            message = webClientResponseException.getResponseBodyAsString();
            if (message == null || message.isBlank()) {
                if (statusCode instanceof HttpStatus status) {
                    message = status.getReasonPhrase();
                } else {
                    message = "Request failed";
                }
            }
        } else if (ex instanceof ConstraintViolationException constraintViolationException) {
            statusCode = HttpStatus.BAD_REQUEST;
            message = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof ServerWebInputException) {
            statusCode = HttpStatus.BAD_REQUEST;
            message = "Invalid request parameters";
        } else {
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal server error";
        }

        ErrorDto errorDto = new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now());

        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(errorDto);
        } catch (JsonProcessingException jsonException) {
            payload = "{\"name\":\"SerializationException\",\"message\":\"Failed to serialize error response\",\"localDateTime\":null}"
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(statusCode);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(payload.length);

        String route = exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath().value();
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled gateway exception on {}", route, ex);
        } else {
            log.warn("Gateway request error on {}: {}", route, message);
        }

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(payload))
        );
    }
}
