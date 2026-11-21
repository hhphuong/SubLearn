package org.example.sublearn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.example.sublearn.common.Sha256;
import org.example.sublearn.domain.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record ParsedToken(Long userId, UserRole role) {
    }

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(@Value("${sublearn.jwt.secret}") String secret,
                       @Value("${sublearn.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
                       @Value("${sublearn.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "sublearn.jwt.secret is empty - set the JWT_SECRET environment variable before starting the app");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public String generateAccessToken(Long userId, String email) {
        return generateAccessToken(userId, email, UserRole.USER);
    }

    public String generateAccessToken(Long userId, String email, UserRole role) {
        UserRole effectiveRole = role != null ? role : UserRole.USER;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", effectiveRole.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<ParsedToken> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = parseRole(claims.get("role", String.class));
            return Optional.of(new ParsedToken(userId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<Long> parseUserId(String token) {
        return parseAccessToken(token).map(ParsedToken::userId);
    }

    private static UserRole parseRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(rawRole);
        } catch (IllegalArgumentException e) {
            return UserRole.USER;
        }
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        return Sha256.hash(rawToken);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }
}
