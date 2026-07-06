package messenger.authenticationservice.domain;

import messenger.authenticationservice.api.dto.JWTAuthentificationDto;
import messenger.authenticationservice.api.dto.LoginDto;
import messenger.authenticationservice.domain.security.JwtCore;
import messenger.authenticationservice.external.UserHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialServiceTest {
    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtCore jwtCore;

    @Mock
    private UserHttpClient userHttpClient;

    private UserCredentialService service;

    @BeforeEach
    void setUp() {
        service = new UserCredentialService(userCredentialRepository, passwordEncoder, jwtCore, userHttpClient);
    }

    @Test
    void signInReturnsStoredTokenPairWhenItIsStillValid() {
        UserCredential user = user(7L, "mail@example.com", "encoded");
        user.setAuthToken("access");
        user.setRefreshToken("refresh");

        when(userCredentialRepository.findByEmail("mail@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtCore.validateJwtToken("access")).thenReturn(true);
        when(jwtCore.validateJwtToken("refresh")).thenReturn(true);

        JWTAuthentificationDto result = service.signIn(new LoginDto("mail@example.com", "password123"));

        assertThat(result.token()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(jwtCore, never()).createAuthToken(7L);
        verify(userCredentialRepository, never()).save(user);
    }

    @Test
    void validateAccessTokenRejectsTokenThatDoesNotMatchStoredActiveToken() {
        UserCredential user = user(7L, "mail@example.com", "encoded");
        user.setAuthToken("current-token");

        when(jwtCore.validateJwtToken("old-token")).thenReturn(true);
        when(jwtCore.getUserIdFromToken("old-token")).thenReturn("7");
        when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.validateAccessTokenAndGetUserId("old-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static UserCredential user(Long id, String email, String password) {
        UserCredential user = new UserCredential();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setAuthToken("");
        user.setRefreshToken("");
        return user;
    }
}
