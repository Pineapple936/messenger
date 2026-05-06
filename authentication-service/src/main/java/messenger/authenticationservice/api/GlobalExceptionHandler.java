package messenger.authenticationservice.api;

import messenger.commonlibs.api.BaseGlobalExceptionHandler;
import messenger.commonlibs.dto.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
}
