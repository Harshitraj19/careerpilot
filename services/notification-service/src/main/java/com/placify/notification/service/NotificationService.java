package com.placify.notification.service;

import com.placify.common.messaging.Events.NotificationEvent;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.notification.client.AuthUsersClient;
import com.placify.notification.client.AuthUsersClient.BasicUser;
import com.placify.notification.domain.Announcement;
import com.placify.notification.domain.Notification;
import com.placify.notification.repository.AnnouncementRepository;
import com.placify.notification.repository.NotificationRepository;
import com.placify.notification.web.NotificationDtos.AnnouncementRequest;
import com.placify.notification.web.NotificationDtos.AnnouncementView;
import com.placify.notification.web.NotificationDtos.NotificationView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivers notification events: expands role audiences ("ROLE:TPO"), stores
 * in-app notifications, pushes them live over WebSocket and sends e-mails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String ROLE_PREFIX = "ROLE:";

    private final NotificationRepository notifications;
    private final AnnouncementRepository announcements;
    private final AuthUsersClient users;
    private final EmailService email;
    private final SimpMessagingTemplate ws;

    @Transactional
    public int deliver(NotificationEvent event) {
        List<BasicUser> recipients = resolve(event.userIds());
        boolean inApp = !"EMAIL".equals(event.type());
        String type = event.type() == null ? "INFO" : event.type();
        String title = truncate(event.title() == null ? "CareerPilot" : event.title(), 255);
        String message = truncate(event.message() == null ? "" : event.message(), 2000);

        if (inApp) {
            List<Notification> saved = notifications.saveAll(recipients.stream()
                    .map(u -> Notification.builder().userId(u.id()).title(title).message(message).type(type)
                            .link(truncate(event.link(), 500)).read(false).build())
                    .toList());
            for (Notification n : saved) {
                push(n);
            }
        }
        if (event.sendEmail()) {
            String subject = event.emailSubject() == null ? title : event.emailSubject();
            Set<String> sent = new LinkedHashSet<>();
            for (BasicUser u : recipients) {
                if (u.email() != null && sent.add(u.email().toLowerCase())) {
                    email.send(new EmailService.Mail(u.email(), u.name(), "[CareerPilot] " + subject, title, message, event.link()));
                }
            }
            if (event.extraEmails() != null) {
                for (String e : event.extraEmails()) {
                    if (e != null && sent.add(e.toLowerCase())) {
                        email.send(new EmailService.Mail(e, null, "[CareerPilot] " + subject, title, message, event.link()));
                    }
                }
            }
        }
        return recipients.size();
    }

    /** Turns user ids and "ROLE:X" audiences into active users (de-duplicated). */
    List<BasicUser> resolve(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<String, BasicUser> out = new java.util.LinkedHashMap<>();
        List<String> direct = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (id.startsWith(ROLE_PREFIX)) {
                users.byRole(id.substring(ROLE_PREFIX.length())).stream().filter(BasicUser::active)
                        .forEach(u -> out.putIfAbsent(u.id(), u));
            } else {
                direct.add(id);
            }
        }
        List<String> missing = direct.stream().filter(id -> !out.containsKey(id)).distinct().toList();
        Map<String, BasicUser> found = users.batch(missing).stream()
                .collect(Collectors.toMap(BasicUser::id, Function.identity(), (a, b) -> a));
        for (String id : missing) {
            BasicUser u = found.get(id);
            // users not (yet) known to auth still get the in-app notification
            out.putIfAbsent(id, u != null ? u : new BasicUser(id, null, null, null, null, "ACTIVE"));
        }
        return new ArrayList<>(out.values());
    }

    private void push(Notification n) {
        try {
            ws.convertAndSendToUser(n.getUserId(), "/queue/notifications", NotificationView.of(n));
        } catch (Exception ex) {
            log.debug("Live push failed: {}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ user API

    public Page<NotificationView> list(boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        String me = CurrentUser.id();
        return (unreadOnly ? notifications.findByUserIdAndReadFalseOrderByCreatedAtDesc(me, pr)
                : notifications.findByUserIdOrderByCreatedAtDesc(me, pr)).map(NotificationView::of);
    }

    public long unread() {
        return notifications.countByUserIdAndReadFalse(CurrentUser.id());
    }

    @Transactional
    public NotificationView markRead(Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.getUserId().equals(CurrentUser.id())) {
            throw ApiException.notFound("Notification not found");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
        }
        return NotificationView.of(n);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(CurrentUser.id(), LocalDateTime.now());
    }

    // ------------------------------------------------------------------ announcements

    @Transactional
    public AnnouncementView announce(AnnouncementRequest req) {
        UserPrincipal me = CurrentUser.get();
        List<String> audience = switch (req.audience()) {
            case ALL_STUDENTS -> List.of("ROLE:STUDENT");
            case ALL_COMPANIES -> List.of("ROLE:COMPANY");
            case ALL_TPOS -> List.of("ROLE:TPO");
            case EVERYONE -> List.of("ROLE:STUDENT", "ROLE:COMPANY", "ROLE:TPO", "ROLE:ADMIN");
        };
        int count = deliver(new NotificationEvent(audience, req.title(), req.message(), "ANNOUNCEMENT", req.link(),
                req.sendEmail(), List.of(), req.title()));
        Announcement a = announcements.save(Announcement.builder().authorUserId(me.userId()).authorName(me.name())
                .audience(req.audience()).title(req.title()).message(req.message()).link(req.link())
                .sendEmail(req.sendEmail()).recipients(count).build());
        return AnnouncementView.of(a);
    }

    public Page<AnnouncementView> announcements(int page, int size) {
        return announcements.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))))
                .map(AnnouncementView::of);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
