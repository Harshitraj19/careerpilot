package com.placify.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "placify.web")
public record WebProperties(String gatewayUrl, String publicWsUrl, int proctoringFrameSeconds, String appName) {
}
