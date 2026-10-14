package com.placify.web.session;

import java.io.Serial;
import java.io.Serializable;

/**
 * The logged-in user, kept in the server-side HTTP session. The JWT never
 * reaches the browser (no XSS token theft); the browser only holds the
 * HttpOnly session cookie.
 */
public class UserSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String KEY = "placify.user";
    public static final String TWO_FACTOR_KEY = "placify.2fa";

    private String token;
    private final String userId;
    private final String loginId;
    private final String name;
    private final String role;
    private boolean firstLogin;

    public UserSession(String token, String userId, String loginId, String name, String role, boolean firstLogin) {
        this.token = token;
        this.userId = userId;
        this.loginId = loginId;
        this.name = name;
        this.role = role;
        this.firstLogin = firstLogin;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        this.firstLogin = firstLogin;
    }

    public boolean isStaff() {
        return "TPO".equals(role) || "ADMIN".equals(role);
    }

    /** Landing page of each portal. */
    public String home() {
        return switch (role) {
            case "STUDENT" -> "/student/dashboard";
            case "COMPANY" -> "/company/dashboard";
            case "TPO" -> "/tpo/dashboard";
            case "ADMIN" -> "/admin/dashboard";
            default -> "/";
        };
    }

    /** Pending second factor between the password step and the OTP step. */
    public record TwoFactorChallenge(String challengeId, String portal, String otpSentTo) implements Serializable {
    }
}
