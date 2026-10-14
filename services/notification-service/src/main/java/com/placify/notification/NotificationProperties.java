package com.placify.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "placify.notification")
public class NotificationProperties {

    private boolean mailEnabled;
    private String mailFrom = "CareerPilot <no-reply@careerpilot.local>";
    private String publicBaseUrl = "http://localhost:3000";
    private List<String> allowedOrigins = new ArrayList<>();
    private int ticketTtlSeconds = 60;
    private int retentionDays = 90;
}
