package messenger.authenticationservice.domain;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import messenger.authenticationservice.api.dto.JWTAuthentificationDto;
import messenger.authenticationservice.api.dto.LoginDto;
import messenger.authenticationservice.api.dto.UserRegistrationDto;
import messenger.authenticationservice.domain.security.JwtCore;
import messenger.authenticationservice.external.UserHttpClient;
import messenger.commonlibs.dto.userservice.CreateUserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserCredentialService {
    private static final Logger log = LoggerFactory.getLogger(UserCredentialService.class);

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtCore jwtCore;
    private final UserHttpClient userHttpClient;

    @Transactional
    public UserCredential save(UserRegistrationDto dto) {
        log.info("Creating new user for email={}", dto.email());

        UserCredential userCredential = new UserCredential(dto.email(), passwordEncoder.encode(dto.password()));
        userCredential.setAuthToken("");
        userCredential.setRefreshToken("");
        userCredentialRepository.save(userCredential);
        userHttpClient.createUser(CreateUserDto.builder()
                .id(userCredential.getId())
                .name(dto.name())
                .description(dto.description())
                .build()
        );
        log.info("User persisted with id={}", userCredential.getId());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userCredential.getEmail(), userCredential.getPassword(), new HashSet<>()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return userCredential;
    }

    @Transactional
    public JWTAuthentificationDto signIn(LoginDto dto) throws AuthenticationException {
        log.info("Sign in started for email={}", dto.email());
        UserCredential user = findByCredentials(dto);

        JWTAuthentificationDto authToken = new JWTAuthentificationDto(
                user.getAuthToken(),
                user.getRefreshToken()
        );

        if (isTokenPairValid(authToken)) {
            log.debug("Returning active token pair for userId={}", user.getId());
            return authToken;
        }

        JWTAuthentificationDto newTokenPair = jwtCore.createAuthToken(user.getId());
        saveTokenPair(user, newTokenPair);
        log.info("Issued new token pair for userId={}", user.getId());
        return newTokenPair;
    }

    @Transactional
    public JWTAuthentificationDto refreshToken(String accessToken, String refreshToken) throws AuthenticationException {
        if (!jwtCore.validateJwtToken(refreshToken)) {
            log.warn("Refresh token validation failed");
            throw new BadCredentialsException("Invalid token");
        }

        final Long refreshUserId = Long.parseLong(jwtCore.getUserIdFromToken(refreshToken));
        log.debug("Refresh token belongs to userId={}", refreshUserId);

        UserCredential user = userCredentialRepository.findById(refreshUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!refreshToken.equals(user.getRefreshToken())) {
            log.warn("Refresh token does not match stored token for userId={}", user.getId());
            throw new BadCredentialsException("Invalid token");
        }

        if (jwtCore.validateJwtToken(accessToken)) {
            Long accessUserId = Long.parseLong(jwtCore.getUserIdFromToken(accessToken));
            if (!refreshUserId.equals(accessUserId)) {
                log.warn("Token pair mismatch: accessUserId={} refreshUserId={}", accessUserId, refreshUserId);
                throw new BadCredentialsException("Invalid token");
            }
        }
        JWTAuthentificationDto refreshedTokenPair = jwtCore.createAuthToken(user.getId(), refreshToken);
        saveTokenPair(user, refreshedTokenPair);
        log.info("Issued refreshed access token for userId={}", user.getId());
        return refreshedTokenPair;
    }

    @Transactional(readOnly = true)
    public Long validateAccessTokenAndGetUserId(String accessToken) {
        if (!jwtCore.validateJwtToken(accessToken)) {
            log.warn("Access token validation failed: JWT is invalid");
            throw new BadCredentialsException("Invalid token");
        }

        final Long userId;
        try {
            userId = Long.parseLong(jwtCore.getUserIdFromToken(accessToken));
        } catch (Exception e) {
            log.warn("Access token validation failed: user id cannot be parsed");
            throw new BadCredentialsException("Invalid token");
        }

        UserCredential user = userCredentialRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));

        if (!accessToken.equals(user.getAuthToken())) {
            log.warn("Access token does not match active token for userId={}", userId);
            throw new BadCredentialsException("Invalid token");
        }

        return userId;
    }

    @Transactional(readOnly = true)
    public void validateAccessToken(String accessToken) {
        validateAccessTokenAndGetUserId(accessToken);
    }

    private boolean isTokenPairValid(JWTAuthentificationDto tokenPair) {
        return tokenPair != null
                && jwtCore.validateJwtToken(tokenPair.token())
                && jwtCore.validateJwtToken(tokenPair.refreshToken());
    }

    @Transactional
    public JWTAuthentificationDto createTokenById(Long id) {
        UserCredential user = userCredentialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        JWTAuthentificationDto newTokenPair = jwtCore.createAuthToken(id);
        saveTokenPair(user, newTokenPair);
        log.info("Created token pair for userId={}", id);
        return newTokenPair;
    }

    @Transactional
    public void deleteById(Long id) {
        userCredentialRepository.deleteById(id);
    }

    private void saveTokenPair(UserCredential user, JWTAuthentificationDto tokenPair) {
        user.setAuthToken(tokenPair.token());
        user.setRefreshToken(tokenPair.refreshToken());
        userCredentialRepository.save(user);
    }

    private UserCredential findByCredentials(LoginDto dto) throws AuthenticationException {
        Optional<UserCredential> optionalUser = userCredentialRepository.findByEmail(dto.email());
        if (optionalUser.isPresent()){
            UserCredential user = optionalUser.get();
            if (passwordEncoder.matches(dto.password(), user.getPassword())){
                log.debug("Credentials verified for userId={}", user.getId());
                return user;
            }
        }
        log.warn("Authentication failed for email={}", dto.email());
        throw new BadCredentialsException("Invalid email or password");
    }
}
