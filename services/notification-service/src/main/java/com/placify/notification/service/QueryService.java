package com.placify.notification.service;

import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.notification.domain.QueryMessage;
import com.placify.notification.domain.StudentQuery;
import com.placify.notification.repository.QueryMessageRepository;
import com.placify.notification.repository.StudentQueryRepository;
import com.placify.notification.web.QueryDtos.MessageView;
import com.placify.notification.web.QueryDtos.NewQuery;
import com.placify.notification.web.QueryDtos.QueryDetail;
import com.placify.notification.web.QueryDtos.QueryStats;
import com.placify.notification.web.QueryDtos.QueryView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Student help desk: students raise queries, the placement office (TPO and admins) answers and
 * resolves them, and both sides are notified in-app (and by e-mail for the student) at each step.
 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private static final List<String> STAFF = List.of("ROLE:TPO", "ROLE:ADMIN");

    private final StudentQueryRepository queries;
    private final QueryMessageRepository messages;
    private final EventPublisher publisher;

    @Transactional
    public QueryDetail create(NewQuery req) {
        UserPrincipal me = CurrentUser.get();
        if (me.role() != Role.STUDENT) {
            throw ApiException.forbidden("Only students can raise queries");
        }
        StudentQuery q = queries.save(StudentQuery.builder()
                .studentUserId(me.userId()).studentName(me.name()).studentLogin(me.loginId())
                .category(req.category()).subject(req.subject().trim())
                .status(StudentQuery.Status.PENDING).awaitingStudent(false)
                .build());
        addMessage(q, me, req.message());
        publisher.notifyUsers(STAFF, "New student query " + q.reference(),
                me.name() + " (" + me.loginId() + "): " + q.getSubject(), "QUERY", "/tpo/queries?id=" + q.getId(), false);
        return detail(q, me);
    }

    public Page<QueryView> list(StudentQuery.Status status, StudentQuery.Category category, String text, int page, int size) {
        UserPrincipal me = CurrentUser.get();
        String owner = staff(me) ? null : me.userId();
        return queries.search(owner, status, category, text == null || text.isBlank() ? null : text.trim(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50))).map(this::view);
    }

    public QueryDetail one(Long id) {
        UserPrincipal me = CurrentUser.get();
        return detail(visible(id, me), me);
    }

    /** A reply from the student (re-opens a resolved query) or from the placement office. */
    @Transactional
    public QueryDetail reply(Long id, String message) {
        UserPrincipal me = CurrentUser.get();
        StudentQuery q = visible(id, me);
        addMessage(q, me, message);
        if (staff(me)) {
            q.setAwaitingStudent(true);
            publisher.notifyUser(q.getStudentUserId(), "Reply to your query " + q.reference(),
                    "The placement office replied to \"" + q.getSubject() + "\": " + shorten(message), "QUERY",
                    "/student/queries?id=" + q.getId(), true);
        } else {
            q.setAwaitingStudent(false);
            if (q.getStatus() == StudentQuery.Status.RESOLVED) {
                reopen(q);
            }
            publisher.notifyUsers(STAFF, "Student replied on " + q.reference(),
                    me.name() + ": " + shorten(message), "QUERY", "/tpo/queries?id=" + q.getId(), false);
        }
        q.setUpdatedAt(LocalDateTime.now());
        return detail(q, me);
    }

    /** Marks the query resolved, optionally with a final answer; students can close their own queries too. */
    @Transactional
    public QueryDetail resolve(Long id, String message) {
        UserPrincipal me = CurrentUser.get();
        StudentQuery q = visible(id, me);
        if (message != null && !message.isBlank()) {
            addMessage(q, me, message);
        }
        q.setStatus(StudentQuery.Status.RESOLVED);
        q.setAwaitingStudent(false);
        q.setResolvedAt(LocalDateTime.now());
        q.setResolvedByName(staff(me) ? me.name() + " (placement office)" : me.name());
        q.setUpdatedAt(LocalDateTime.now());
        if (staff(me)) {
            publisher.notifyUser(q.getStudentUserId(), "Query resolved: " + q.reference(),
                    "Your query \"" + q.getSubject() + "\" was resolved by the placement office."
                            + (message == null || message.isBlank() ? "" : " Answer: " + shorten(message)),
                    "QUERY", "/student/queries?id=" + q.getId(), true);
        }
        return detail(q, me);
    }

    @Transactional
    public QueryDetail reopen(Long id) {
        UserPrincipal me = CurrentUser.get();
        StudentQuery q = visible(id, me);
        reopen(q);
        if (!staff(me)) {
            publisher.notifyUsers(STAFF, "Query re-opened: " + q.reference(), me.name() + " re-opened \"" + q.getSubject() + "\".",
                    "QUERY", "/tpo/queries?id=" + q.getId(), false);
        }
        return detail(q, me);
    }

    public QueryStats stats() {
        UserPrincipal me = CurrentUser.get();
        if (staff(me)) {
            return new QueryStats(queries.countByStatus(StudentQuery.Status.PENDING), queries.countByStatus(StudentQuery.Status.RESOLVED));
        }
        return new QueryStats(queries.countByStudentUserIdAndStatus(me.userId(), StudentQuery.Status.PENDING),
                queries.countByStudentUserIdAndStatus(me.userId(), StudentQuery.Status.RESOLVED));
    }

    // ---------------------------------------------------------------- helpers

    private void reopen(StudentQuery q) {
        q.setStatus(StudentQuery.Status.PENDING);
        q.setResolvedAt(null);
        q.setResolvedByName(null);
        q.setUpdatedAt(LocalDateTime.now());
    }

    private void addMessage(StudentQuery q, UserPrincipal me, String text) {
        messages.save(QueryMessage.builder().queryId(q.getId()).authorUserId(me.userId()).authorName(me.name())
                .authorRole(me.role() == null ? "SYSTEM" : me.role().name()).message(text.trim()).build());
    }

    private StudentQuery visible(Long id, UserPrincipal me) {
        StudentQuery q = queries.findById(id).orElseThrow(() -> ApiException.notFound("Query not found"));
        if (!staff(me) && !q.getStudentUserId().equals(me.userId())) {
            throw ApiException.forbidden("You can only see your own queries");
        }
        return q;
    }

    private static boolean staff(UserPrincipal me) {
        return me.role() == Role.TPO || me.role() == Role.ADMIN;
    }

    private QueryView view(StudentQuery q) {
        List<QueryMessage> thread = messages.findByQueryIdOrderByCreatedAtAsc(q.getId());
        String last = thread.isEmpty() ? null : shorten(thread.get(thread.size() - 1).getMessage());
        return new QueryView(q.getId(), q.reference(), q.getStudentUserId(), q.getStudentName(), q.getStudentLogin(),
                q.getCategory(), q.getCategory().label(), q.getSubject(), q.getStatus(), q.isAwaitingStudent(),
                q.getResolvedByName(), thread.size(), last, q.getCreatedAt(), q.getUpdatedAt(), q.getResolvedAt());
    }

    private QueryDetail detail(StudentQuery q, UserPrincipal me) {
        List<MessageView> thread = messages.findByQueryIdOrderByCreatedAtAsc(q.getId()).stream()
                .map(m -> new MessageView(m.getId(), m.getAuthorName(), m.getAuthorRole(), m.getAuthorUserId().equals(me.userId()),
                        m.getMessage(), m.getCreatedAt()))
                .toList();
        return new QueryDetail(view(q), thread);
    }

    private static String shorten(String text) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return t.length() > 160 ? t.substring(0, 157) + "..." : t;
    }
}
