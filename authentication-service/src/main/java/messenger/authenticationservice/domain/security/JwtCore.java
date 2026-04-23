package messenger.authenticationservice.domain.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import messenger.authenticationservice.api.dto.JWTAuthentificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class JwtCore {
    private static final Logger log = LoggerFactory.getLogger(JwtCore.class);

    private final String secret;
    private final int authLifetime, refreshLifetime;

    public JwtCore(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token-expiration-minutes}") int authLifetime,
                   @Value("${jwt.refresh-token-expiration-days}") int refreshLifetime) {
        this.secret = secret;
        this.authLifetime = authLifetime;
        this.refreshLifetime = refreshLifetime;
        log.info("JwtCore initialized: accessLifetimeMinutes={}, refreshLifetimeDays={}", authLifetime, refreshLifetime);
    }

    public JWTAuthentificationDto createAuthToken(Long id) {
        log.debug("Creating auth and refresh tokens for userId={}", id);
        return new JWTAuthentificationDto(generateAuthToken(id), generateRefreshToken(id));
    }

    public JWTAuthentificationDto createAuthToken(Long id, String refreshToken) {
        log.debug("Creating auth token with existing refresh token for userId={}", id);
        return new JWTAuthentificationDto(generateAuthToken(id), refreshToken);
    }

    public String getUserIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateJwtToken(String token) {
        try {
            Jwts
                    .parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        } catch(Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private String generateAuthToken(Long id) {
        Date date = Date.from(LocalDateTime.now().plusMinutes(authLifetime).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts
                .builder()
                .subject(String.valueOf(id))
                .expiration(date)
                .signWith(getSignKey())
                .compact();
    }

    private String generateRefreshToken(Long id) {
        Date date = Date.from(LocalDateTime.now().plusDays(refreshLifetime).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts
                .builder()
                .subject(String.valueOf(id))
                .expiration(date)
                .signWith(getSignKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
