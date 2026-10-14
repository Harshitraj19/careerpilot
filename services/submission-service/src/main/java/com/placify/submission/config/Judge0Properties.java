package com.placify.submission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * placify.judge0.* settings. Works with a self-hosted Judge0 or the RapidAPI version.
 */
@ConfigurationProperties(prefix = "placify.judge0")
public record Judge0Properties(
        String baseUrl,
        String rapidApiKey,
        String rapidApiHost,
        String authToken,
        String callbackBaseUrl,
        long pollIntervalMs,
        int runMaxPolls,
        long runPollDelayMs,
        String runner) {

    public boolean useRapidApi() {
        return rapidApiKey != null && !rapidApiKey.isBlank();
    }

    /**
     * "local" runs code on this machine, "judge0" always uses Judge0, "auto" (default) uses Judge0
     * only when one is configured: a RapidAPI key or a self-hosted URL.
     */
    public boolean useLocalRunner() {
        String mode = runner == null ? "auto" : runner.trim().toLowerCase();
        if (mode.equals("local")) {
            return true;
        }
        if (mode.equals("judge0")) {
            return false;
        }
        boolean selfHosted = baseUrl != null && !baseUrl.isBlank() && !baseUrl.contains("rapidapi.com");
        return !useRapidApi() && !selfHosted;
    }

    public boolean callbacksEnabled() {
        return callbackBaseUrl != null && !callbackBaseUrl.isBlank();
    }
}
