package messenger.authenticationservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JWTAuthentificationDto(
        @NotBlank
        @Size(max = 1024)
        String token,

        @NotBlank
        @Size(max = 1024)
        String refreshToken
) {
}
