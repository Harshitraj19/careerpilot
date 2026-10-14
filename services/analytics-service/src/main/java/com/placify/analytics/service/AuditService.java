package com.placify.analytics.service;

import com.placify.analytics.AnalyticsProperties;
import com.placify.analytics.domain.AuditLog;
import com.placify.analytics.repository.AuditLogRepository;
import com.placify.analytics.web.AnalyticsDtos.AuditView;
import com.placify.common.messaging.Events.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/** Central audit trail for the whole platform (written from every service's audit events). */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository logs;
    private final AnalyticsProperties properties;

    public void record(AuditEvent e) {
        logs.save(AuditLog.builder()
                .service(cut(e.service(), 60))
                .actorUserId(cut(e.actorUserId(), 36))
                .actorRole(cut(e.actorRole(), 20))
                .action(cut(e.action() == null ? "UNKNOWN" : e.action(), 80))
                .target(cut(e.target(), 255))
                .details(cut(e.details(), 2000))
                .success(e.success())
                .occurredAt(e.timestamp() == null ? LocalDateTime.now() : e.timestamp())
                .build());
    }

    public Page<AuditView> search(String service, String action, String actor, Boolean success, LocalDateTime from,
                                  LocalDateTime to, int page, int size) {
        return logs.search(blank(service), blank(action), blank(actor), success,
                from == null ? LocalDateTime.now().minusDays(30) : from, to == null ? LocalDateTime.now().plusMinutes(1) : to,
                PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)))).map(AuditView::of);
    }

    public String csv(String service, String action, String actor, Boolean success, LocalDateTime from, LocalDateTime to) {
        List<AuditView> rows = search(service, action, actor, success, from, to, 0, 200).getContent();
        StringWriter w = new StringWriter();
        w.append("time,service,actor,role,action,target,success,details\n");
        for (AuditView r : rows) {
            w.append(csvCell(String.valueOf(r.occurredAt()))).append(',')
                    .append(csvCell(r.service())).append(',')
                    .append(csvCell(r.actorUserId())).append(',')
                    .append(csvCell(r.actorRole())).append(',')
                    .append(csvCell(r.action())).append(',')
                    .append(csvCell(r.target())).append(',')
                    .append(String.valueOf(r.success())).append(',')
                    .append(csvCell(r.details())).append('\n');
        }
        return w.toString();
    }

    @Transactional
    @Scheduled(cron = "0 45 3 * * *")
    public void purge() {
        logs.deleteBefore(LocalDateTime.now().minusDays(properties.getAuditRetentionDays()));
    }

    /** Quotes a CSV cell and neutralises spreadsheet formula injection. */
    static String csvCell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
