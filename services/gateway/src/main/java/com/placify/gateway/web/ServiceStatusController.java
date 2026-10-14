package com.placify.gateway.web;

import com.placify.gateway.security.AuthenticationGlobalFilter;
import com.placify.gateway.security.JwtVerifier;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Which services are registered in Eureka (and how many instances), for the
 * admin's system overview. Handled by the gateway itself, so it checks the
 * token and the active session here (global filters only run on routed calls).
 */
@RestController
public class ServiceStatusController {

    static final List<String> EXPECTED = List.of("auth-service", "profile-service", "placement-service", "coding-service",
            "submission-service", "assessment-service", "proctoring-service", "ai-helper-service", "interview-service",
            "notification-service", "analytics-service");

    private final ReactiveDiscoveryClient discovery;
    private final JwtVerifier jwt;
    private final ReactiveStringRedisTemplate redis;

    public ServiceStatusController(ReactiveDiscoveryClient discovery, JwtVerifier jwt, ReactiveStringRedisTemplate redis) {
        this.discovery = discovery;
        this.jwt = jwt;
        this.redis = redis;
    }

    public record ServiceStatus(String name, int instances, boolean up) {
    }

    @GetMapping("/api/gateway/services")
    public Mono<ResponseEntity<List<ServiceStatus>>> services(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        JwtVerifier.TokenClaims claims;
        try {
            if (auth == null || !auth.startsWith("Bearer ")) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
            }
            claims = jwt.verify(auth.substring(7));
        } catch (Exception ex) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        if (!"ADMIN".equals(claims.role())) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return redis.opsForValue().get(AuthenticationGlobalFilter.SESSION_KEY_PREFIX + claims.userId())
                .filter(sid -> sid.equals(claims.sessionId()))
                .flatMap(ok -> Flux.fromIterable(EXPECTED)
                        .flatMap(name -> discovery.getInstances(name).count()
                                .map(n -> new ServiceStatus(name, n.intValue(), n > 0))
                                .onErrorReturn(new ServiceStatus(name, 0, false)))
                        .sort((a, b) -> a.name().compareTo(b.name()))
                        .collectList()
                        .map(ResponseEntity::ok))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
