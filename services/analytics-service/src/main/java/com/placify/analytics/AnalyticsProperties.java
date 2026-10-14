package com.placify.analytics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "placify.analytics")
public class AnalyticsProperties {

    private int readinessCacheMinutes = 60;
    private int atRiskThreshold = 40;
    private int syntheticHistorySize = 800;
    private int auditRetentionDays = 365;
}
