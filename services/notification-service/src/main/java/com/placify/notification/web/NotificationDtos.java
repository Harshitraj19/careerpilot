package com.placify.notification.web;

import com.placify.notification.domain.Announcement;
import com.placify.notification.domain.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(Long id, String title, String message, String type, String link, boolean read,
                                   LocalDateTime createdAt) {

        public static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.getLink(), n.isRead(),
                    n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {
    }

    public record Ticket(String ticket, String endpoint) {
    }

    public record AnnouncementRequest(
            @NotNull Announcement.Audience audience,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 500) String link,
            boolean sendEmail) {
    }

    public record AnnouncementView(Long id, String author, Announcement.Audience audience, String title, String message,
                                   String link, boolean sendEmail, int recipients, LocalDateTime createdAt) {

        public static AnnouncementView of(Announcement a) {
            return new AnnouncementView(a.getId(), a.getAuthorName(), a.getAudience(), a.getTitle(), a.getMessage(),
                    a.getLink(), a.isSendEmail(), a.getRecipients(), a.getCreatedAt());
        }
    }
}
