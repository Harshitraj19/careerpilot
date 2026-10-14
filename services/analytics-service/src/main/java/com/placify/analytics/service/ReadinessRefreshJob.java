package com.placify.analytics.service;

import com.placify.analytics.client.AnalyticsClients;
import com.placify.analytics.client.AnalyticsClients.StudentSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Recomputes every student's readiness each night so TPO dashboards stay current. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadinessRefreshJob {

    private final AnalyticsClients clients;
    private final ReadinessService readiness;

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshAll() {
        List<StudentSummary> students;
        try {
            students = clients.students(null, null);
        } catch (Exception ex) {
            log.warn("Readiness refresh skipped: {}", ex.getMessage());
            return;
        }
        int ok = 0;
        for (StudentSummary s : students) {
            try {
                readiness.compute(s.userId(), s);
                ok++;
            } catch (Exception ex) {
                log.debug("Readiness for {} failed: {}", s.userId(), ex.getMessage());
            }
        }
        log.info("Readiness refreshed for {}/{} students", ok, students.size());
    }
}
