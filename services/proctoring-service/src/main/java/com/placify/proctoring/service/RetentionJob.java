package com.placify.proctoring.service;

import com.placify.common.storage.FileStorageService;
import com.placify.proctoring.config.ProctoringProperties;
import com.placify.proctoring.domain.Violation;
import com.placify.proctoring.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Privacy: deletes evidence snapshots after the retention period (default 90 days).
 * The violation records stay, only the images are removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionJob {

    private final ViolationRepository violations;
    private final FileStorageService storage;
    private final ProctoringProperties props;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldSnapshots() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(props.snapshotRetentionDays());
        int removed = 0;
        for (Violation v : violations.findBySnapshotPathIsNotNullAndOccurredAtBefore(cutoff)) {
            storage.delete(v.getSnapshotPath());
            v.setSnapshotPath(null);
            removed++;
        }
        if (removed > 0) {
            log.info("Deleted {} proctoring snapshots older than {} days", removed, props.snapshotRetentionDays());
        }
    }
}
