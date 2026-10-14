package com.placify.auth.service;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.domain.OtpCode;
import com.placify.auth.repository.OtpCodeRepository;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Creates and checks 6-digit email OTPs (registration, password reset, 2FA).
 * Codes expire, allow a limited number of attempts and have a resend cooldown.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpCodeRepository repository;
    private final EventPublisher publisher;
    private final SecureRandom random;
    private final AuthProperties props;

    /** Sends a code and returns it (callers only reveal it in demo mode, see {@link #demoNote}). */
    @Transactional
    public String send(String email, OtpCode.Purpose purpose, String reasonText) {
        String normalized = email.trim().toLowerCase();
        repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalized, purpose).ifPresent(last -> {
            if (last.getCreatedAt().plusSeconds(props.otpResendCooldownSeconds()).isAfter(LocalDateTime.now())) {
                throw ApiException.tooMany("Please wait a minute before requesting another code");
            }
        });
        String code = String.format("%06d", random.nextInt(1_000_000));
        repository.save(OtpCode.builder()
                .email(normalized)
                .purpose(purpose)
                .codeHash(hash(code))
                .expiresAt(LocalDateTime.now().plusMinutes(props.otpValidityMinutes()))
                .build());

        String message = "Your CareerPilot verification code for " + reasonText + " is " + code
                + ". It is valid for " + props.otpValidityMinutes() + " minutes. Do not share it with anyone.";
        publisher.notify(Events.NotificationEvent.emailOnly(normalized, "CareerPilot verification code", message));
        return code;
    }

    /** Text appended to API messages when codes may be shown on screen (EXPOSE_OTP=true, e-mail switched off). */
    public String demoNote(String code) {
        return props.exposeOtp() && code != null ? " Demo mode (e-mail is switched off): your code is " + code + "." : "";
    }

    /** Checks the latest code; failures are counted and saved even though the caller fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(String email, OtpCode.Purpose purpose, String code) {
        String normalized = email.trim().toLowerCase();
        OtpCode otp = repository.findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(normalized, purpose)
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "Invalid or expired code"));
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            throw ApiException.badRequest("OTP_EXPIRED", "The code has expired. Request a new one.");
        }
        if (otp.getAttempts() >= props.otpMaxAttempts()) {
            otp.setUsed(true);
            throw ApiException.tooMany("Too many wrong attempts. Request a new code.");
        }
        boolean ok = code != null && MessageDigest.isEqual(
                hash(code.trim()).getBytes(StandardCharsets.UTF_8),
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw ApiException.badRequest("OTP_INVALID", "Invalid code");
        }
        otp.setUsed(true);
    }

    static String hash(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "*".repeat(Math.max(1, at - 2)) + email.charAt(at - 1) + email.substring(at);
    }
}
