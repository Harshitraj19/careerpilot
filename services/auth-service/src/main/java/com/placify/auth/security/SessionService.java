package com.placify.auth.security;

import com.placify.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Server-side session registry in Redis, one entry per user:
 * <ul>
 *   <li>a new login overwrites the entry, so the old device is logged out (single active session)</li>
 *   <li>logout / password change / blocking delete it (token invalidated immediately)</li>
 *   <li>the gateway refreshes the TTL on every request (inactivity timeout)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    /** Must match AuthenticationGlobalFilter.SESSION_KEY_PREFIX in the gateway. */
    public static final String SESSION_KEY_PREFIX = "placify:session:";

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public String start(String userId) {
        String sessionId = UUID.randomUUID().toString();
        redis.opsForValue().set(SESSION_KEY_PREFIX + userId, sessionId, Duration.ofMinutes(props.sessionTimeoutMinutes()));
        return sessionId;
    }

    public void end(String userId) {
        redis.delete(SESSION_KEY_PREFIX + userId);
    }

    public boolean isActive(String userId) {
        return Boolean.TRUE.equals(redis.hasKey(SESSION_KEY_PREFIX + userId));
    }
}
