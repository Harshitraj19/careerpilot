package com.placify.notification.websocket;

import com.placify.notification.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * STOMP over WebSocket at /ws/notifications?ticket=...
 * <ul>
 *   <li>/user/queue/notifications - new in-app notifications</li>
 *   <li>/user/queue/proctoring    - live exam warnings</li>
 *   <li>/topic/contest/{id}       - contest leaderboard</li>
 * </ul>
 * Clients can only subscribe; they cannot send messages to other users.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String USER_ATTRIBUTE = "placifyUserId";

    private final WsTicketService tickets;
    private final NotificationProperties properties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns(properties.getAllowedOrigins().toArray(String[]::new))
                .addInterceptors(new TicketInterceptor())
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                                      Map<String, Object> attributes) {
                        String userId = (String) attributes.get(USER_ATTRIBUTE);
                        return () -> userId;
                    }
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {20000, 20000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    return null; // read-only channel
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String dest = Optional.ofNullable(accessor.getDestination()).orElse("");
                    boolean allowed = dest.startsWith("/user/queue/") || dest.startsWith("/topic/contest/");
                    if (!allowed) {
                        return null;
                    }
                }
                return message;
            }
        });
    }

    private org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    /** Accepts the handshake only with a valid one-time ticket. */
    private class TicketInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servlet)) {
                return false;
            }
            Optional<String> userId = tickets.consume(servlet.getServletRequest().getParameter("ticket"));
            if (userId.isEmpty()) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(USER_ATTRIBUTE, userId.get());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Exception exception) {
            // nothing to do
        }
    }
}
