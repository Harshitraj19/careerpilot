package com.placify.notification.service;

import com.placify.notification.NotificationProperties;
import com.placify.notification.repository.EmailLogRepository;
import com.placify.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Nightly clean-up of old read notifications and e-mail logs. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionJob {

    private final NotificationRepository notifications;
    private final EmailLogRepository emailLog;
    private final NotificationProperties properties;

    @Transactional
    @Scheduled(cron = "0 30 2 * * *")
    public void clean() {
        LocalDateTime before = LocalDateTime.now().minusDays(properties.getRetentionDays());
        int n = notifications.deleteReadBefore(before);
        int m = emailLog.deleteBefore(before);
        log.info("Retention: removed {} notifications and {} e-mail log rows", n, m);
    }
}
