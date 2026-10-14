package com.placify.auth.security;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues signed JWTs. The token carries the user id, role and the id of the
 * Redis session; the gateway checks both the signature and the session.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final AuthProperties props;

    public JwtService(AuthProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwtSecret()));
    }

    public String issue(User user, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("login", user.getLoginId())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .claim("sid", sessionId)
                .claim("fl", user.isFirstLogin())
                .issuer("placify-auth")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.tokenValidityHours(), ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
