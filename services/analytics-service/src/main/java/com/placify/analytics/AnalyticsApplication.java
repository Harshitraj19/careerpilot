package com.placify.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Dashboards for every role, the placement-readiness score, the
 * machine-learning placement prediction, PDF/Excel reports and the audit log.
 */
@EnableAsync
@SpringBootApplication(scanBasePackages = "com.placify")
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
