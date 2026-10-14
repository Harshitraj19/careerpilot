package com.placify.auth.web;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.repository.LoginHistoryRepository;
import com.placify.auth.repository.UserRepository;
import com.placify.auth.service.AuthService;
import com.placify.auth.service.CaptchaService;
import com.placify.auth.service.OtpService;
import com.placify.auth.service.UserAdminService;
import com.placify.auth.web.AuthDtos.ChangePasswordRequest;
import com.placify.auth.web.AuthDtos.CompanyOtpRequest;
import com.placify.auth.web.AuthDtos.CompanyRegisterRequest;
import com.placify.auth.web.AuthDtos.CreatedAccount;
import com.placify.auth.web.AuthDtos.ForgotPasswordRequest;
import com.placify.auth.web.AuthDtos.LoginHistoryView;
import com.placify.auth.web.AuthDtos.LoginRequest;
import com.placify.auth.web.AuthDtos.LoginResponse;
import com.placify.auth.web.AuthDtos.ResetPasswordRequest;
import com.placify.auth.web.AuthDtos.StudentOtpRequest;
import com.placify.auth.web.AuthDtos.StudentRegisterRequest;
import com.placify.auth.web.AuthDtos.TwoFactorRequest;
import com.placify.auth.web.AuthDtos.TwoFactorToggle;
import com.placify.auth.web.AuthDtos.UserView;
import com.placify.common.security.CurrentUser;
import com.placify.common.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Login through the role portals, two-step login, logout, password change and reset,
 * company registration and student sign-up.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final UserAdminService adminService;
    private final OtpService otpService;
    private final UserRepository users;
    private final LoginHistoryRepository history;
    private final AuthProperties props;

    @Operation(summary = "Get a new captcha image")
    @GetMapping("/captcha")
    public Map<String, Object> captcha() {
        if (!props.captchaEnabled()) {
            return Map.of("enabled", false);
        }
        CaptchaService.Captcha c = captchaService.generate();
        return Map.of("enabled", true, "captchaId", c.captchaId(), "image", c.imageBase64());
    }

    @Operation(summary = "Log in through a role portal (Student, Company, TPO, Admin)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, clientIp(http), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @Operation(summary = "Complete login with the emailed two-factor code")
    @PostMapping("/login/2fa")
    public LoginResponse twoFactor(@Valid @RequestBody TwoFactorRequest req, HttpServletRequest http) {
        return authService.verifyTwoFactor(req.challengeId(), req.otp(), clientIp(http), http.getHeader(HttpHeaders.USER_AGENT));
    }

    @Operation(summary = "Log out and invalidate the session")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout(CurrentUser.id());
    }

    @GetMapping("/me")
    public UserView me() {
        return users.findById(CurrentUser.id()).map(UserView::of)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Operation(summary = "Change password (also completes the forced first-login change)")
    @PostMapping("/password/change")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return authService.changePassword(CurrentUser.id(), req);
    }

    @Operation(summary = "Send a password reset code to the registered email")
    @PostMapping("/password/forgot")
    public Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        String code = authService.forgotPassword(req);
        return Map.of("message", "If the account exists, a reset code has been sent to its registered email."
                + otpService.demoNote(code));
    }

    @PostMapping("/password/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return Map.of("message", "Password reset. You can now log in with your new password.");
    }

    @Operation(summary = "Company registration step 1: email OTP")
    @PostMapping("/company/register/otp")
    public Map<String, String> companyOtp(@Valid @RequestBody CompanyOtpRequest req) {
        String code = adminService.sendCompanyOtp(req.email());
        return Map.of("message", "Verification code sent to " + req.email() + "." + otpService.demoNote(code));
    }

    @Operation(summary = "Company registration step 2: create account (pending TPO approval)")
    @PostMapping("/company/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> companyRegister(@Valid @RequestBody CompanyRegisterRequest req) {
        adminService.registerCompany(req);
        return Map.of("message", "Registration submitted. You can log in once the placement office approves it.");
    }

    @Operation(summary = "Student sign-up step 1: email OTP")
    @PostMapping("/student/register/otp")
    public Map<String, String> studentOtp(@Valid @RequestBody StudentOtpRequest req) {
        String code = adminService.sendStudentOtp(req.regNo(), req.email());
        return Map.of("message", "Verification code sent to " + req.email() + "." + otpService.demoNote(code));
    }

    @Operation(summary = "Student sign-up step 2: create the account with the student's own password")
    @PostMapping("/student/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> studentRegister(@Valid @RequestBody StudentRegisterRequest req) {
        CreatedAccount account = adminService.registerStudent(req);
        return Map.of("message", "Account created. Log in with your registration number " + account.loginId()
                + " and your password.", "loginId", account.loginId());
    }

    @Operation(summary = "Turn two-factor login on or off (TPO and Admin)")
    @PutMapping("/me/two-factor")
    public Map<String, Boolean> twoFactor(@RequestBody TwoFactorToggle req) {
        authService.setTwoFactor(CurrentUser.id(), req.enabled());
        return Map.of("enabled", req.enabled());
    }

    @Operation(summary = "My recent logins")
    @GetMapping("/me/login-history")
    public Page<LoginHistoryView> myHistory(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return history.findByUserIdOrderByCreatedAtDesc(CurrentUser.id(), PageRequest.of(page, Math.min(size, 100)))
                .map(LoginHistoryView::of);
    }

    /** The original client address; the web portal forwards it in X-Forwarded-For. */
    static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
    }
}
