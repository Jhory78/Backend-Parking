package com.parking.infrastructure.security;

import com.parking.application.port.out.AccessTokenIssuerPort;
import com.parking.domain.entity.AccessToken;
import com.parking.domain.entity.User;
import com.parking.infrastructure.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static com.parking.infrastructure.utils.constants.SecurityConstants.CLAIM_ID;
import static com.parking.infrastructure.utils.constants.SecurityConstants.CLAIM_NAME;
import static com.parking.infrastructure.utils.constants.SecurityConstants.CLAIM_ROLE;

/**
 * Outbound adapter of {@link AccessTokenIssuerPort}: issues (login) and
 * validates (every request) JWT access tokens.
 * <p>
 * Claims: {@code sub} (email), {@code id}, {@code name}, {@code role}.
 */
@Slf4j
@Component
public class JwtTokenService implements AccessTokenIssuerPort {

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final Duration expiration;

    public JwtTokenService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.expiration = properties.expiration();
    }

    @Override
    public AccessToken issueFor(User user) {
        Instant issuedAt = Instant.now();

        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_ID, user.getId())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new AccessToken(token, expiration);
    }

    /**
     * @return the token subject (email), or empty if the token is malformed,
     * has an invalid signature or has expired.
     */
    public Optional<String> extractValidSubject(String token) {
        try {
            return Optional.ofNullable(parser.parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
