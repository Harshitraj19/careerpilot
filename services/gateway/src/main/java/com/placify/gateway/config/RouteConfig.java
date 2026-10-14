package com.placify.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * All routes of the system. Services are resolved through Eureka (lb://name),
 * so nothing is hardcoded to localhost. /internal/** is never routed.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(GatewayProperties props) {
        return new RedisRateLimiter(props.rateLimit().replenishRate(), props.rateLimit().burstCapacity(), 1);
    }

    /** Rate limit per client IP (first X-Forwarded-For entry when behind a proxy). */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getAddress)
                    .map(a -> a.getHostAddress())
                    .orElse("unknown");
            return Mono.just(ip);
        };
    }

    @Bean
    public RouteLocator placifyRoutes(RouteLocatorBuilder builder, RedisRateLimiter limiter, KeyResolver keyResolver) {
        return builder.routes()
                .route("auth", r -> r.path("/api/auth/**").filters(f -> common(f, limiter, keyResolver, "auth")).uri("lb://auth-service"))
                .route("profile", r -> r.path("/api/profile/**").filters(f -> common(f, limiter, keyResolver, "profile")).uri("lb://profile-service"))
                .route("placement", r -> r.path("/api/placement/**").filters(f -> common(f, limiter, keyResolver, "placement")).uri("lb://placement-service"))
                .route("coding", r -> r.path("/api/coding/**").filters(f -> common(f, limiter, keyResolver, "coding")).uri("lb://coding-service"))
                .route("submission", r -> r.path("/api/submissions/**").filters(f -> common(f, limiter, keyResolver, "submission")).uri("lb://submission-service"))
                .route("assessment", r -> r.path("/api/assessment/**").filters(f -> common(f, limiter, keyResolver, "assessment")).uri("lb://assessment-service"))
                .route("proctoring", r -> r.path("/api/proctoring/**").filters(f -> f.circuitBreaker(c -> c.setName("proctoring").setFallbackUri("forward:/fallback/proctoring"))).uri("lb://proctoring-service"))
                .route("ai", r -> r.path("/api/ai/**").filters(f -> common(f, limiter, keyResolver, "ai")).uri("lb://ai-helper-service"))
                .route("interview", r -> r.path("/api/interviews/**").filters(f -> common(f, limiter, keyResolver, "interview")).uri("lb://interview-service"))
                .route("notification", r -> r.path("/api/notifications/**").filters(f -> common(f, limiter, keyResolver, "notification")).uri("lb://notification-service"))
                .route("analytics", r -> r.path("/api/analytics/**").filters(f -> common(f, limiter, keyResolver, "analytics")).uri("lb://analytics-service"))
                // Real-time hub (in-app notifications, proctoring warnings, live leaderboards)
                .route("websocket", r -> r.path("/ws/**").uri("lb:ws://notification-service"))
                .build();
    }

    /**
     * Filters for every HTTP route: rate limit per IP and a circuit breaker
     * that returns a friendly error when a service is down.
     * (Proctoring frames are sent every few seconds, so that route is not rate limited.)
     */
    private UriSpec common(GatewayFilterSpec f, RedisRateLimiter limiter, KeyResolver keyResolver, String name) {
        return f.requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(keyResolver))
                .circuitBreaker(c -> c.setName(name).setFallbackUri("forward:/fallback/" + name));
    }
}
