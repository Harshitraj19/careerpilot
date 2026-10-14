package com.placify.notification.websocket;

import com.placify.notification.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Browsers cannot send an Authorization header when opening a WebSocket, so a
 * logged-in user first asks for a short-lived one-time ticket over normal
 * (authenticated) HTTP and passes it in the WebSocket URL.
 */
@Service
@RequiredArgsConstructor
public class WsTicketService {

    private static final String PREFIX = "placify:ws-ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final NotificationProperties properties;

    public String issue(String userId) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + ticket, userId, Duration.ofSeconds(properties.getTicketTtlSeconds()));
        return ticket;
    }

    /** Returns the user of the ticket and deletes it, so it cannot be reused. */
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 64) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().getAndDelete(PREFIX + ticket));
    }
}
