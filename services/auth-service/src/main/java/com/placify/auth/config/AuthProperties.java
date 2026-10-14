package com.placify.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * placify.auth.* settings.
 */
@ConfigurationProperties(prefix = "placify.auth")
public record AuthProperties(
        String jwtSecret,
        int tokenValidityHours,
        int sessionTimeoutMinutes,
        int maxFailedAttempts,
        int lockoutMinutes,
        int otpValidityMinutes,
        int otpMaxAttempts,
        int otpResendCooldownSeconds,
        boolean captchaEnabled,
        boolean exposeTempPasswords,
        boolean exposeOtp,
        boolean studentSelfRegistration,
        Admin admin) {

    public record Admin(String username, String password, String email) {
    }
}
