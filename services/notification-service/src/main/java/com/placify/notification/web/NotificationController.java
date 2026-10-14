package com.placify.notification.web;

import com.placify.common.audit.Audited;
import com.placify.common.security.CurrentUser;
import com.placify.notification.service.NotificationService;
import com.placify.notification.web.NotificationDtos.AnnouncementRequest;
import com.placify.notification.web.NotificationDtos.AnnouncementView;
import com.placify.notification.web.NotificationDtos.NotificationView;
import com.placify.notification.web.NotificationDtos.Ticket;
import com.placify.notification.web.NotificationDtos.UnreadCount;
import com.placify.notification.websocket.WsTicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final WsTicketService tickets;

    @GetMapping
    public Page<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return notifications.list(unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread() {
        return new UnreadCount(notifications.unread());
    }

    @PostMapping("/{id}/read")
    public NotificationView read(@PathVariable Long id) {
        return notifications.markRead(id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead());
    }

    /** One-time ticket for opening the WebSocket (valid for a minute). */
    @PostMapping("/ws-ticket")
    public Ticket ticket() {
        return new Ticket(tickets.issue(CurrentUser.id()), "/ws/notifications");
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    @Audited(action = "ANNOUNCEMENT_SENT", target = "#req.audience()")
    public AnnouncementView announce(@Valid @RequestBody AnnouncementRequest req) {
        return notifications.announce(req);
    }

    @GetMapping("/announcements")
    public Page<AnnouncementView> announcements(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return notifications.announcements(page, size);
    }
}
