package messenger.authenticationservice.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import messenger.commonlibs.dto.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + ((error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()) ? "Invalid request" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        log.warn("Request body validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorDto> handleBindException(BindException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + ((error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()) ? "Invalid request" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        log.warn("Request binding failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorDto> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        String message = "Missing required header: " + ex.getHeaderName();
        log.warn(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDto> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String message = "Missing required parameter: " + ex.getParameterName();
        log.warn(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected type";
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "' (expected " + requiredType + ")";
        log.warn(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String message = "Malformed JSON request body";
        log.warn("{}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorDto> handleResponseStatusException(ResponseStatusException ex) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            if (ex.getStatusCode() instanceof HttpStatus status) {
                message = status.getReasonPhrase();
            } else {
                message = "Request failed";
            }
        }
        log.warn("Request failed with status {}: {}", ex.getStatusCode(), message);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorDto> handleBadCredentialsException(BadCredentialsException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Invalid request" : ex.getMessage();
        log.warn("Authentication failed: {}", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDeniedException(AccessDeniedException ex) {
        String message = "Access is denied";
        log.warn(message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDto> handleEntityNotFoundException(EntityNotFoundException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Invalid request" : ex.getMessage();
        log.warn("Entity not found: {}", message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Request conflicts with existing data";
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorDto> handleRestClientResponseException(RestClientResponseException ex) {
        String message = ex.getResponseBodyAsString();
        if (message == null || message.isBlank()) {
            HttpStatusCode statusCode = ex.getStatusCode();
            if (statusCode instanceof HttpStatus status) {
                message = status.getReasonPhrase();
            } else {
                message = "Request failed";
            }
        }
        log.warn("Downstream service returned {}: {}", ex.getStatusCode(), message);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Invalid request" : ex.getMessage();
        log.warn("Illegal argument: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleException(Exception ex) {
        String message = "Internal server error";
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto(ex.getClass().getSimpleName(), message, LocalDateTime.now()));
    }
}
