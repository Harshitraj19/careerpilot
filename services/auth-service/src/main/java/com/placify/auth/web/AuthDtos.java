package com.placify.auth.web;

import com.placify.auth.domain.LoginHistory;
import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.auth.security.StrongPassword;
import com.placify.common.security.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request and response bodies of the auth API.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotNull Role portal,
            @NotBlank @Size(max = 320) String loginId,
            @NotBlank @Size(max = 64) String password,
            String captchaId,
            String captchaAnswer) {
    }

    public record TwoFactorRequest(@NotBlank String challengeId, @NotBlank String otp) {
    }

    /** Either a token or a 2FA challenge. */
    public record LoginResponse(
            String token,
            String tokenType,
            String userId,
            String loginId,
            String name,
            Role role,
            boolean firstLogin,
            int sessionTimeoutMinutes,
            boolean twoFactorRequired,
            String challengeId,
            String otpSentTo) {

        public static LoginResponse token(String token, User u, int timeout) {
            return new LoginResponse(token, "Bearer", u.getId(), u.getLoginId(), u.getName(), u.getRole(),
                    u.isFirstLogin(), timeout, false, null, null);
        }

        public static LoginResponse challenge(String challengeId, String maskedEmail) {
            return new LoginResponse(null, null, null, null, null, null, false, 0, true, challengeId, maskedEmail);
        }
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @StrongPassword String newPassword) {
    }

    public record ForgotPasswordRequest(@NotNull Role portal, @NotBlank String loginId) {
    }

    public record ResetPasswordRequest(
            @NotNull Role portal,
            @NotBlank String loginId,
            @NotBlank String otp,
            @NotBlank @StrongPassword String newPassword) {
    }

    public record CompanyOtpRequest(@NotBlank @Email String email) {
    }

    public record CompanyRegisterRequest(
            @NotBlank @Size(max = 255) String companyName,
            @NotBlank @Email String email,
            @NotBlank @StrongPassword String password,
            @Size(max = 500) String website,
            @Size(max = 255) String industry,
            @NotBlank @Size(max = 255) String contactPerson,
            @Size(max = 20) String phone,
            @NotBlank String otp,
            String captchaId,
            String captchaAnswer) {
    }

    public record StudentOtpRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5,20}", message = "Use 5-20 letters and digits, e.g. 22BCE1234") String regNo,
            @NotBlank @Email String email) {
    }

    public record StudentRegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5,20}", message = "Use 5-20 letters and digits, e.g. 22BCE1234") String regNo,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(max = 100) String branch,
            @NotNull @Min(2000) @Max(2100) Integer batch,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cgpa,
            @NotBlank @StrongPassword String password,
            @NotBlank String otp,
            String captchaId,
            String captchaAnswer) {
    }

    public record CreateTpoRequest(
            @NotBlank @Size(max = 50) String employeeId,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email String email,
            @Size(max = 20) String phone) {
    }

    public record CreateAdminRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email String email) {
    }

    /** One student row for single creation (bulk import uses CSV/Excel). */
    public record CreateStudentRequest(
            @NotBlank @Size(max = 30) String regNo,
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String branch,
            @NotNull Integer batch,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cgpa,
            String phone) {
    }

    public record CreatedAccount(String userId, String loginId, String name, String email, String temporaryPassword) {
    }

    public record ImportReport(int totalRows, int created, int skipped, List<CreatedAccount> accounts, List<String> errors) {
    }

    public record StatusRequest(String reason) {
    }

    public record TwoFactorToggle(boolean enabled) {
    }

    public record UserView(
            String id,
            String loginId,
            String email,
            String name,
            String phone,
            Role role,
            UserStatus status,
            boolean firstLogin,
            boolean twoFactorEnabled,
            boolean locked,
            String statusReason,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt) {

        public static UserView of(User u) {
            return new UserView(u.getId(), u.getLoginId(), u.getEmail(), u.getName(), u.getPhone(), u.getRole(),
                    u.getStatus(), u.isFirstLogin(), u.isTwoFactorEnabled(), u.isLocked(), u.getStatusReason(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record PendingCompanyView(
            String userId,
            String companyName,
            String email,
            String website,
            String industry,
            String contactPerson,
            String phone,
            LocalDateTime registeredAt) {
    }

    public record LoginHistoryView(Long id, String loginId, String portal, String ip, String userAgent,
                                   boolean success, String reason, LocalDateTime time) {

        public static LoginHistoryView of(LoginHistory h) {
            return new LoginHistoryView(h.getId(), h.getLoginId(), h.getPortal(), h.getIp(), h.getUserAgent(),
                    h.isSuccess(), h.getReason(), h.getCreatedAt());
        }
    }

    public record BasicUser(String id, String loginId, String name, String email, Role role, UserStatus status) {

        public static BasicUser of(User u) {
            return new BasicUser(u.getId(), u.getLoginId(), u.getName(), u.getEmail(), u.getRole(), u.getStatus());
        }
    }

    public record UserIdsRequest(List<String> ids) {
    }

    public record AuthStats(
            long students,
            long companies,
            long pendingCompanies,
            long tpos,
            long admins,
            long blockedUsers,
            long successfulLogins7d,
            long failedLogins7d) {
    }
}
