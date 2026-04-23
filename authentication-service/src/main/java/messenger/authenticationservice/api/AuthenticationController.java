package messenger.authenticationservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import messenger.authenticationservice.domain.UserCredential;
import messenger.authenticationservice.domain.UserCredentialService;
import messenger.authenticationservice.api.dto.JWTAuthentificationDto;
import messenger.authenticationservice.api.dto.LoginDto;
import messenger.authenticationservice.api.dto.RefreshTokenRequest;
import messenger.authenticationservice.api.dto.UserRegistrationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import static messenger.commonlibs.Constants.USER_ID_HEADER;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationController.class);
    private final UserCredentialService userCredentialService;

    @PostMapping("/register")
    public ResponseEntity<JWTAuthentificationDto> register(@Valid @RequestBody UserRegistrationDto dto) {
        log.info("Registration request received for email={}", dto.email());
        UserCredential userCredential = userCredentialService.save(dto);
        log.info("User registered with id={}", userCredential.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(userCredentialService.createTokenById(userCredential.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<JWTAuthentificationDto> login(@Valid @RequestBody LoginDto dto) {
        log.info("Login request received for email={}", dto.email());
        JWTAuthentificationDto jwtAuthenticationDto = userCredentialService.signIn(dto);
        log.info("Login successful for email={}", dto.email());
        return ResponseEntity.ok(jwtAuthenticationDto);
    }

    @PostMapping("/refresh")
    public ResponseEntity<JWTAuthentificationDto> refresh(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
    ) {
        if(!authorizationHeader.startsWith("Bearer ")) {
            log.warn("Refresh token request rejected: Authorization header is not Bearer");
            throw new BadCredentialsException("Invalid Authorization header");
        }
        log.info("Refresh token request received");
        authorizationHeader = authorizationHeader.substring(7);
        JWTAuthentificationDto jwtAuthenticationDto = userCredentialService.refreshToken(authorizationHeader, refreshTokenRequest.refreshToken());
        log.info("Refresh token request completed successfully");
        return ResponseEntity.ok(jwtAuthenticationDto);
    }

    @GetMapping("/validate")
    public ResponseEntity<Void> validate(@RequestHeader(value = HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        if(!authorizationHeader.startsWith("Bearer ")) {
            log.warn("Validation request rejected: Authorization header is not Bearer");
            throw new BadCredentialsException("Invalid Authorization header");
        }

        String accessToken = authorizationHeader.substring(7);
        Long userId = userCredentialService.validateAccessTokenAndGetUserId(accessToken);
        log.debug("Token validation successful for userId={}", userId);
        return ResponseEntity.ok()
                .header(USER_ID_HEADER, String.valueOf(userId))
                .build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestHeader(USER_ID_HEADER) Long id) {
        userCredentialService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
