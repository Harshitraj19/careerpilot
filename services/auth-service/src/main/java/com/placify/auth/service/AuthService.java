package com.placify.auth.service;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.domain.LoginHistory;
import com.placify.auth.domain.OtpCode;
import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.LoginHistoryRepository;
import com.placify.auth.repository.UserRepository;
import com.placify.auth.security.JwtService;
import com.placify.auth.security.SessionService;
import com.placify.auth.web.AuthDtos.ChangePasswordRequest;
import com.placify.auth.web.AuthDtos.ForgotPasswordRequest;
import com.placify.auth.web.AuthDtos.LoginRequest;
import com.placify.auth.web.AuthDtos.LoginResponse;
import com.placify.auth.web.AuthDtos.ResetPasswordRequest;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.Role;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Login, two-factor login, logout and password management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TWO_FACTOR_PREFIX = "placify:2fa:";

    private final UserRepository users;
    private final LoginHistoryRepository history;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final SessionService sessions;
    private final CaptchaService captcha;
    private final OtpService otpService;
    private final StringRedisTemplate redis;
    private final EventPublisher publisher;
    private final AuthProperties props;

    /** Used to spend the same time on unknown users (prevents user enumeration by timing). */
    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = encoder.encode(UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    /** Normalises a login id the way it is stored for that portal. */
    public static String normalizeLoginId(Role portal, String loginId) {
        String v = loginId == null ? "" : loginId.trim();
        return switch (portal) {
            case STUDENT, TPO -> v.toUpperCase();
            case COMPANY, ADMIN -> v.toLowerCase();
        };
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        captcha.verify(req.captchaId(), req.captchaAnswer());
        String loginId = normalizeLoginId(req.portal(), req.loginId());

        Optional<User> found = users.findByLoginIdAndRole(loginId, req.portal());
        if (found.isEmpty()) {
            encoder.matches(req.password(), dummyHash());
            record(null, loginId, req.portal(), ip, userAgent, false, "UNKNOWN_USER");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login ID or password for this portal");
        }
        User user = found.get();

        if (user.isLocked()) {
            long minutes = Math.max(1, ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockedUntil()) + 1);
            record(user.getId(), loginId, req.portal(), ip, userAgent, false, "LOCKED");
            throw ApiException.locked("Account locked after too many failed attempts. Try again in " + minutes + " minute(s).");
        }

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= props.maxFailedAttempts()) {
                user.setFailedAttempts(0);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(props.lockoutMinutes()));
                record(user.getId(), loginId, req.portal(), ip, userAgent, false, "LOCKED_NOW");
                publisher.notifyUser(user.getId(), "Account locked",
                        "Your account was locked for " + props.lockoutMinutes() + " minutes after "
                                + props.maxFailedAttempts() + " wrong passwords. If this wasn't you, reset your password.",
                        "SECURITY", null, true);
                throw ApiException.locked("Too many failed attempts. Account locked for " + props.lockoutMinutes() + " minutes.");
            }
            user.setFailedAttempts(attempts);
            record(user.getId(), loginId, req.portal(), ip, userAgent, false, "BAD_PASSWORD");
            int left = props.maxFailedAttempts() - attempts;
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid login ID or password. " + left + " attempt(s) left before the account is locked.");
        }

        checkStatus(user, loginId, req.portal(), ip, userAgent);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        if (user.isTwoFactorEnabled()) {
            String challengeId = UUID.randomUUID().toString();
            redis.opsForValue().set(TWO_FACTOR_PREFIX + challengeId, user.getId(), Duration.ofMinutes(props.otpValidityMinutes()));
            otpService.send(user.getEmail(), OtpCode.Purpose.TWO_FACTOR, "signing in");
            record(user.getId(), loginId, req.portal(), ip, userAgent, false, "2FA_SENT");
            return LoginResponse.challenge(challengeId, OtpService.mask(user.getEmail()));
        }
        return completeLogin(user, ip, userAgent);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse verifyTwoFactor(String challengeId, String otp, String ip, String userAgent) {
        String key = TWO_FACTOR_PREFIX + challengeId;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw ApiException.badRequest("CHALLENGE_EXPIRED", "Login session expired. Please log in again.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account not found"));
        otpService.verify(user.getEmail(), OtpCode.Purpose.TWO_FACTOR, otp);
        redis.delete(key);
        checkStatus(user, user.getLoginId(), user.getRole(), ip, userAgent);
        return completeLogin(user, ip, userAgent);
    }

    private LoginResponse completeLogin(User user, String ip, String userAgent) {
        user.setLastLoginAt(LocalDateTime.now());
        String sessionId = sessions.start(user.getId());
        record(user.getId(), user.getLoginId(), user.getRole(), ip, userAgent, true, "OK");
        return LoginResponse.token(jwtService.issue(user, sessionId), user, props.sessionTimeoutMinutes());
    }

    private void checkStatus(User user, String loginId, Role portal, String ip, String userAgent) {
        switch (user.getStatus()) {
            case ACTIVE -> {
                return;
            }
            case PENDING -> {
                record(user.getId(), loginId, portal, ip, userAgent, false, "PENDING_APPROVAL");
                throw ApiException.forbidden("Your company account is waiting for approval by the Training & Placement Office.");
            }
            case REJECTED -> {
                record(user.getId(), loginId, portal, ip, userAgent, false, "REJECTED");
                throw ApiException.forbidden("Your registration was rejected"
                        + (Texts.isBlank(user.getStatusReason()) ? "." : ": " + user.getStatusReason()));
            }
            case BLOCKED -> {
                record(user.getId(), loginId, portal, ip, userAgent, false, "BLOCKED");
                throw ApiException.forbidden("Your account has been blocked. Contact the placement office.");
            }
            default -> throw ApiException.forbidden("Account not active");
        }
    }

    public void logout(String userId) {
        sessions.end(userId);
    }

    @Transactional
    public LoginResponse changePassword(String userId, ChangePasswordRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "New password must be different from the current one");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setFirstLogin(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        sessions.end(userId);
        String sessionId = sessions.start(userId);
        publisher.notifyUser(userId, "Password changed",
                "Your CareerPilot password was changed. If you did not do this, contact the placement office immediately.",
                "SECURITY", null, true);
        return LoginResponse.token(jwtService.issue(user, sessionId), user, props.sessionTimeoutMinutes());
    }

    @Transactional
    /** @return the code that was sent, or null; the caller shows the same message either way */
    public String forgotPassword(ForgotPasswordRequest req) {
        String loginId = normalizeLoginId(req.portal(), req.loginId());
        return users.findByLoginIdAndRole(loginId, req.portal())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> otpService.send(u.getEmail(), OtpCode.Purpose.PASSWORD_RESET, "resetting your password"))
                .orElse(null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String loginId = normalizeLoginId(req.portal(), req.loginId());
        User user = users.findByLoginIdAndRole(loginId, req.portal())
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Invalid or expired code"));
        otpService.verify(user.getEmail(), OtpCode.Purpose.PASSWORD_RESET, req.otp());
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setFirstLogin(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setPasswordChangedAt(LocalDateTime.now());
        sessions.end(user.getId());
        publisher.notifyUser(user.getId(), "Password reset",
                "Your CareerPilot password was reset using an email code.", "SECURITY", null, true);
    }

    @Transactional
    public void setTwoFactor(String userId, boolean enabled) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() != Role.TPO && user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Two-factor login is available for TPO and Admin accounts");
        }
        user.setTwoFactorEnabled(enabled);
    }

    private void record(String userId, String loginId, Role portal, String ip, String userAgent, boolean success, String reason) {
        history.save(LoginHistory.builder()
                .userId(userId)
                .loginId(loginId)
                .portal(portal == null ? null : portal.name())
                .ip(ip)
                .userAgent(Texts.truncate(userAgent, 500))
                .success(success)
                .reason(reason)
                .build());
    }
}
