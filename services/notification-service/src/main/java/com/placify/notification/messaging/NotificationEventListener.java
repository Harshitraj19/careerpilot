package com.placify.notification.messaging;

import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.notification.service.LiveChannelService;
import com.placify.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationEventListener {

    static final String NOTIFY_QUEUE = "notification.send";
    static final String WARNING_QUEUE = "notification.proctoring-warning";
    static final String LEADERBOARD_QUEUE = "notification.leaderboard";

    private final NotificationService notifications;
    private final LiveChannelService live;

    @Bean
    Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    /** Live-only messages are useless when late: keep them for 30 seconds at most. */
    @Bean
    Queue warningQueue() {
        return QueueBuilder.durable(WARNING_QUEUE).ttl(30_000).build();
    }

    @Bean
    Queue leaderboardQueue() {
        return QueueBuilder.durable(LEADERBOARD_QUEUE).ttl(30_000).build();
    }

    @Bean
    Binding notifyBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(notifyQueue()).to(placifyExchange).with(RoutingKeys.NOTIFICATION);
    }

    @Bean
    Binding warningBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(warningQueue()).to(placifyExchange).with(RoutingKeys.PROCTORING_WARNING);
    }

    @Bean
    Binding leaderboardBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(leaderboardQueue()).to(placifyExchange).with(RoutingKeys.LEADERBOARD_UPDATED);
    }

    @RabbitListener(queues = NOTIFY_QUEUE)
    public void onNotification(Events.NotificationEvent e) {
        int n = notifications.deliver(e);
        log.debug("Delivered '{}' to {} users", e.title(), n);
    }

    @RabbitListener(queues = WARNING_QUEUE)
    public void onWarning(Events.ProctoringWarningEvent e) {
        live.proctoringWarning(e);
    }

    @RabbitListener(queues = LEADERBOARD_QUEUE)
    public void onLeaderboard(Events.LeaderboardUpdatedEvent e) {
        live.leaderboard(e);
    }
}
