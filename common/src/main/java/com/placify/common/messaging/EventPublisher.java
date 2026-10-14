package com.placify.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Small wrapper around RabbitTemplate.
 * <ul>
 *   <li>Inside a database transaction, events are sent only after the commit,
 *       so consumers never see data that was rolled back or not yet saved.</li>
 *   <li>Publishing never breaks the business operation: broker errors are logged.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(routingKey, event);
                }
            });
        } else {
            send(routingKey, event);
        }
    }

    private void send(String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(RoutingKeys.EXCHANGE, routingKey, event);
        } catch (Exception ex) {
            log.error("Could not publish event {} with key {}: {}", event.getClass().getSimpleName(), routingKey, ex.getMessage());
        }
    }

    public void notify(Events.NotificationEvent event) {
        boolean noUsers = event.userIds() == null || event.userIds().isEmpty();
        boolean noEmails = event.extraEmails() == null || event.extraEmails().isEmpty();
        if (noUsers && noEmails) {
            return;
        }
        publish(RoutingKeys.NOTIFICATION, event);
    }

    public void notifyUser(String userId, String title, String message, String type, String link, boolean email) {
        notify(email
                ? Events.NotificationEvent.withEmail(List.of(userId), title, message, type, link)
                : Events.NotificationEvent.inApp(List.of(userId), title, message, type, link));
    }

    public void notifyUsers(List<String> userIds, String title, String message, String type, String link, boolean email) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        notify(email
                ? Events.NotificationEvent.withEmail(userIds, title, message, type, link)
                : Events.NotificationEvent.inApp(userIds, title, message, type, link));
    }
}
