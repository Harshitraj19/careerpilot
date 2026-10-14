package com.placify.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * In-app notifications, e-mails, announcements and every live (WebSocket)
 * channel: notification badge, exam proctoring warnings and contest leaderboards.
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = "com.placify")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
