package com.placify.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * placify.gateway.* settings.
 *
 * @param jwtSecret          base64 HMAC key shared with auth-service
 * @param gatewaySecret      secret attached to every forwarded request
 * @param sessionTimeoutMinutes  inactivity timeout (sliding, kept in Redis)
 * @param publicPaths        paths that need no login
 * @param firstLoginPaths    the only paths allowed before the forced password change
 * @param roleRules          path pattern -> roles allowed (coarse checks; services do fine checks)
 * @param allowedOrigins     CORS origins
 * @param rateLimit          requests per second and burst per client IP
 */
@ConfigurationProperties(prefix = "placify.gateway")
public record GatewayProperties(
        String jwtSecret,
        String gatewaySecret,
        int sessionTimeoutMinutes,
        List<String> publicPaths,
        List<String> firstLoginPaths,
        Map<String, List<String>> roleRules,
        List<String> allowedOrigins,
        RateLimit rateLimit) {

    public record RateLimit(int replenishRate, int burstCapacity) {
    }
}
