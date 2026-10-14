package com.placify.gateway.security;

import com.placify.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runs before routing for every request:
 * <ol>
 *   <li>removes any identity headers a client tried to send</li>
 *   <li>blocks /internal/** completely</li>
 *   <li>lets public paths through (login, captcha, certificate verification ...)</li>
 *   <li>verifies the JWT signature and expiry</li>
 *   <li>checks the Redis session: single active session, logout and inactivity timeout</li>
 *   <li>restricts users who must still change their first-login password</li>
 *   <li>applies coarse role rules per path</li>
 *   <li>forwards the trusted identity headers plus the gateway secret</li>
 * </ol>
 */
@Slf4j
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String SESSION_KEY_PREFIX = "placify:session:";

    private static final List<String> IDENTITY_HEADERS = List.of(
            "X-User-Id", "X-User-Login", "X-User-Role", "X-User-Name", "X-Gateway-Secret", "X-Internal-Secret");

    private final GatewayProperties props;
    private final JwtVerifier jwtVerifier;
    private final ReactiveStringRedisTemplate redis;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AuthenticationGlobalFilter(GatewayProperties props, JwtVerifier jwtVerifier, ReactiveStringRedisTemplate redis) {
        this.props = props;
        this.jwtVerifier = jwtVerifier;
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (path.startsWith("/internal") || path.contains("/internal/")) {
            return error(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        }
        if (request.getMethod() == HttpMethod.OPTIONS || !path.startsWith("/api/")) {
            // CORS pre-flight, fallback endpoints and the WebSocket hub (ticket-checked by the service)
            return chain.filter(exchange.mutate().request(strip(request).build()).build());
        }
        if (isPublic(path)) {
            ServerHttpRequest forwarded = strip(request)
                    .header("X-Gateway-Secret", props.gatewaySecret())
                    .build();
            return chain.filter(exchange.mutate().request(forwarded).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Please log in");
        }

        JwtVerifier.TokenClaims claims;
        try {
            claims = jwtVerifier.verify(header.substring(7));
        } catch (Exception ex) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Your login is invalid or has expired");
        }

        String sessionKey = SESSION_KEY_PREFIX + claims.userId();
        Duration timeout = Duration.ofMinutes(props.sessionTimeoutMinutes());

        return redis.opsForValue().get(sessionKey)
                .defaultIfEmpty("")
                .flatMap(activeSid -> {
                    if (!activeSid.equals(claims.sessionId())) {
                        // logged out, logged in elsewhere, password changed or inactive too long
                        return error(exchange, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                                "Your session has ended. Please log in again.");
                    }
                    if (claims.firstLogin() && !matchesAny(path, props.firstLoginPaths())) {
                        return error(exchange, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                                "Please change your password before continuing");
                    }
                    if (!roleAllowed(path, claims.role())) {
                        return error(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Your role cannot access this");
                    }
                    ServerHttpRequest forwarded = strip(request)
                            .header("X-User-Id", claims.userId())
                            .header("X-User-Login", encode(claims.loginId()))
                            .header("X-User-Role", claims.role())
                            .header("X-User-Name", encode(claims.name()))
                            .header("X-Gateway-Secret", props.gatewaySecret())
                            .build();
                    // sliding inactivity timeout
                    return redis.expire(sessionKey, timeout)
                            .then(chain.filter(exchange.mutate().request(forwarded).build()));
                });
    }

    private ServerHttpRequest.Builder strip(ServerHttpRequest request) {
        return request.mutate().headers(h -> IDENTITY_HEADERS.forEach(h::remove));
    }

    private boolean isPublic(String path) {
        return matchesAny(path, props.publicPaths());
    }

    private boolean matchesAny(String path, List<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (String p : patterns) {
            if (matcher.match(p, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean roleAllowed(String path, String role) {
        if (props.roleRules() == null) {
            return true;
        }
        for (Map.Entry<String, List<String>> rule : props.roleRules().entrySet()) {
            if (matcher.match(rule.getKey(), path)) {
                return rule.getValue().contains(role);
            }
        }
        return true;
    }

    private static String encode(String v) {
        return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                Instant.now(), status.value(), code, message, exchange.getRequest().getURI().getPath());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
