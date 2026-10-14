package com.placify.proctoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * placify.proctoring.* tuning values. All thresholds should be tuned with real
 * test sessions (lighting, cameras) before an exam.
 */
@ConfigurationProperties(prefix = "placify.proctoring")
public record ProctoringProperties(
        int debounceSeconds,
        int identityCheckEvery,
        double identityThreshold,
        double movementThresholdPercent,
        double darkThreshold,
        int flagBelowTrustScore,
        int snapshotRetentionDays,
        PhoneDetection phoneDetection,
        String faceModelUrl) {

    public record PhoneDetection(boolean enabled, String modelUrl, double minConfidence) {
    }
}
