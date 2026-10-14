package com.placify.gateway.security;

import com.placify.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Verifies the signature and expiry of JWTs issued by auth-service.
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(GatewayProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwtSecret()));
    }

    /** @throws io.jsonwebtoken.JwtException when the token is invalid or expired */
    public TokenClaims verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new TokenClaims(
                claims.getSubject(),
                claims.get("login", String.class),
                claims.get("role", String.class),
                claims.get("name", String.class),
                claims.get("sid", String.class),
                Boolean.TRUE.equals(claims.get("fl", Boolean.class)));
    }

    public record TokenClaims(String userId, String loginId, String role, String name, String sessionId, boolean firstLogin) {
    }
}
