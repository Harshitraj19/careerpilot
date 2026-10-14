package com.placify.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.placify.web.gateway.GatewayClient;
import com.placify.web.gateway.GatewayException;
import com.placify.web.session.SessionService;
import com.placify.web.session.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * VTOP-style login portals (Student / Company / TPO / Admin) with captcha,
 * optional e-mail OTP second factor, forgot/reset password, company
 * self-registration with e-mail OTP and the forced first-login password change.
 */
@Controller
@RequiredArgsConstructor
public class AuthPagesController {

    private static final Set<String> PORTALS = Set.of("STUDENT", "COMPANY", "TPO", "ADMIN");
    /** Fields of the company registration form that are shown again after an error. */
    private static final List<String> REGISTER_FIELDS = List.of("companyName", "email", "website", "industry",
            "contactPerson", "phone");
    /** Fields of the student sign-up form that are shown again after an error. */
    private static final List<String> STUDENT_FIELDS = List.of("regNo", "name", "email", "phone", "branch", "batch", "cgpa");

    /** Captcha shown on the login and registration forms. */
    public record Captcha(boolean enabled, String id, String image, boolean unavailable) {

        static Captcha disabled() {
            return new Captcha(false, null, null, false);
        }

        static Captcha unreachable() {
            return new Captcha(false, null, null, true);
        }
    }

    private final GatewayClient gateway;
    private final SessionService sessions;

    /** The home page is the login screen; signed-in users go straight to their dashboard. */
    @GetMapping("/")
    public String index(HttpServletRequest request, Model model) {
        return login("STUDENT", request, model);
    }

    // ------------------------------------------------------------------ login

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "STUDENT") String portal, HttpServletRequest request, Model model) {
        if (sessions.current(request).isPresent()) {
            return "redirect:" + sessions.current(request).get().home();
        }
        model.addAttribute("portal", PORTALS.contains(portal) ? portal : "STUDENT");
        model.addAttribute("captcha", captcha(request));
        return "auth/login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String portal, @RequestParam String loginId, @RequestParam String password,
                          @RequestParam(required = false) String captchaId,
                          @RequestParam(required = false) String captchaAnswer,
                          HttpServletRequest request, Model model) {
        String p = PORTALS.contains(portal) ? portal : "STUDENT";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("portal", p);
        body.put("loginId", loginId);
        body.put("password", password);
        body.put("captchaId", captchaId);
        body.put("captchaAnswer", captchaAnswer);
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/login", body, null, request);
            if (res.path("twoFactorRequired").asBoolean(false)) {
                request.getSession(true).setAttribute(UserSession.TWO_FACTOR_KEY,
                        new UserSession.TwoFactorChallenge(res.path("challengeId").asText(), p, res.path("otpSentTo").asText()));
                return "redirect:/login/2fa";
            }
            UserSession user = sessions.signIn(res, request);
            return "redirect:" + (user.isFirstLogin() ? "/account/change-password" : user.home());
        } catch (GatewayException ex) {
            model.addAttribute("portal", p);
            model.addAttribute("loginId", loginId);
            model.addAttribute("error", ex.display());
            model.addAttribute("captcha", captcha(request));
            return "auth/login";
        }
    }

    @GetMapping("/login/2fa")
    public String twoFactor(HttpServletRequest request, Model model) {
        UserSession.TwoFactorChallenge c = challenge(request);
        if (c == null) {
            return "redirect:/login";
        }
        model.addAttribute("sentTo", c.otpSentTo());
        return "auth/two-factor";
    }

    @PostMapping("/login/2fa")
    public String doTwoFactor(@RequestParam String otp, HttpServletRequest request, Model model) {
        UserSession.TwoFactorChallenge c = challenge(request);
        if (c == null) {
            return "redirect:/login";
        }
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/login/2fa",
                    Map.of("challengeId", c.challengeId(), "otp", otp), null, request);
            UserSession user = sessions.signIn(res, request);
            return "redirect:" + (user.isFirstLogin() ? "/account/change-password" : user.home());
        } catch (GatewayException ex) {
            model.addAttribute("sentTo", c.otpSentTo());
            model.addAttribute("error", ex.display());
            return "auth/two-factor";
        }
    }

    private UserSession.TwoFactorChallenge challenge(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s == null ? null : (UserSession.TwoFactorChallenge) s.getAttribute(UserSession.TWO_FACTOR_KEY);
    }

    // ------------------------------------------------------------------ password

    @GetMapping("/forgot-password")
    public String forgot(@RequestParam(defaultValue = "STUDENT") String portal, Model model) {
        model.addAttribute("portal", PORTALS.contains(portal) ? portal : "STUDENT");
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String doForgot(@RequestParam String portal, @RequestParam String loginId, HttpServletRequest request,
                           Model model, RedirectAttributes flash) {
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/password/forgot",
                    Map.of("portal", portal, "loginId", loginId), null, request);
            flash.addFlashAttribute("info", res.path("message").asText("If the account exists, an OTP has been sent."));
            flash.addAttribute("portal", portal);
            flash.addAttribute("loginId", loginId);
            return "redirect:/reset-password";
        } catch (GatewayException ex) {
            model.addAttribute("portal", portal);
            model.addAttribute("error", ex.display());
            return "auth/forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String reset(@RequestParam(defaultValue = "STUDENT") String portal,
                        @RequestParam(defaultValue = "") String loginId, Model model) {
        model.addAttribute("portal", portal);
        model.addAttribute("loginId", loginId);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String doReset(@RequestParam String portal, @RequestParam String loginId, @RequestParam String otp,
                          @RequestParam String newPassword, @RequestParam String confirmPassword,
                          HttpServletRequest request, Model model, RedirectAttributes flash) {
        model.addAttribute("portal", portal);
        model.addAttribute("loginId", loginId);
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "The two passwords do not match");
            return "auth/reset-password";
        }
        try {
            gateway.json(HttpMethod.POST, "/api/auth/password/reset",
                    Map.of("portal", portal, "loginId", loginId, "otp", otp, "newPassword", newPassword), null, request);
            flash.addFlashAttribute("info", "Password changed. You can log in now.");
            return "redirect:/login?portal=" + portal;
        } catch (GatewayException ex) {
            model.addAttribute("error", ex.display());
            return "auth/reset-password";
        }
    }

    @GetMapping("/account/change-password")
    public String changePassword(HttpServletRequest request, Model model) {
        model.addAttribute("forced", sessions.current(request).map(UserSession::isFirstLogin).orElse(false));
        return "auth/change-password";
    }

    @PostMapping("/account/change-password")
    public String doChangePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                   @RequestParam String confirmPassword, HttpServletRequest request, Model model,
                                   RedirectAttributes flash) {
        UserSession user = sessions.current(request).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("forced", user.isFirstLogin());
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "The two passwords do not match");
            return "auth/change-password";
        }
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/password/change",
                    Map.of("currentPassword", currentPassword, "newPassword", newPassword), user.getToken(), request);
            // the API issues a fresh token without the first-login restriction
            user.setToken(res.path("token").asText(user.getToken()));
            user.setFirstLogin(false);
            request.getSession().setAttribute(UserSession.KEY, user);
            flash.addFlashAttribute("info", "Your password has been changed.");
            return "redirect:" + user.home();
        } catch (GatewayException ex) {
            if (ex.getStatus() == 401) {
                sessions.signOut(request);
                return "redirect:/login?expired";
            }
            model.addAttribute("error", ex.display());
            return "auth/change-password";
        }
    }

    // ------------------------------------------------------------------ company registration

    @GetMapping("/register/company")
    public String register(HttpServletRequest request, Model model) {
        model.addAttribute("captcha", captcha(request));
        model.addAttribute("form", registerForm(Map.of()));
        return "auth/register-company";
    }

    @PostMapping("/register/company/otp")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> sendOtp(@RequestParam String email,
                                                                                HttpServletRequest request) {
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/company/register/otp", Map.of("email", email), null, request);
            return org.springframework.http.ResponseEntity.ok(Map.of("ok", true,
                    "message", res.path("message").asText("OTP sent to " + email)));
        } catch (GatewayException ex) {
            return org.springframework.http.ResponseEntity.status(ex.getStatus()).body(Map.of("ok", false, "message", ex.display()));
        }
    }

    @PostMapping("/register/company")
    public String doRegister(@RequestParam Map<String, String> form, HttpServletRequest request, Model model,
                             RedirectAttributes flash) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String k : new String[] {"companyName", "email", "password", "website", "industry", "contactPerson", "phone",
                "otp", "captchaId", "captchaAnswer"}) {
            String v = form.get(k);
            body.put(k, v == null || v.isBlank() ? null : v.trim());
        }
        if (!String.valueOf(form.get("password")).equals(form.get("confirmPassword"))) {
            return registerError(model, request, form, "The two passwords do not match");
        }
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/company/register", body, null, request);
            flash.addFlashAttribute("info", res.path("message").asText(
                    "Registration received. You can log in once the placement office approves your account."));
            return "redirect:/login?portal=COMPANY";
        } catch (GatewayException ex) {
            return registerError(model, request, form, ex.display());
        }
    }

    private String registerError(Model model, HttpServletRequest request, Map<String, String> form, String error) {
        model.addAttribute("form", registerForm(form));
        model.addAttribute("error", error);
        model.addAttribute("captcha", captcha(request));
        return "auth/register-company";
    }

    // ------------------------------------------------------------------ student sign-up

    @GetMapping("/register/student")
    public String registerStudent(HttpServletRequest request, Model model) {
        model.addAttribute("captcha", captcha(request));
        model.addAttribute("form", formOf(STUDENT_FIELDS, Map.of()));
        return "auth/register-student";
    }

    @PostMapping("/register/student/otp")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> sendStudentOtp(@RequestParam String email,
                                                                                       @RequestParam String regNo,
                                                                                       HttpServletRequest request) {
        try {
            JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/student/register/otp",
                    Map.of("email", email, "regNo", regNo), null, request);
            return org.springframework.http.ResponseEntity.ok(Map.of("ok", true,
                    "message", res.path("message").asText("Code sent to " + email)));
        } catch (GatewayException ex) {
            return org.springframework.http.ResponseEntity.status(ex.getStatus()).body(Map.of("ok", false, "message", ex.display()));
        }
    }

    @PostMapping("/register/student")
    public String doRegisterStudent(@RequestParam Map<String, String> form, HttpServletRequest request, Model model,
                                    RedirectAttributes flash) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String k : new String[] {"regNo", "name", "email", "phone", "branch", "password", "otp", "captchaId", "captchaAnswer"}) {
            String v = form.get(k);
            body.put(k, v == null || v.isBlank() ? null : v.trim());
        }
        body.put("batch", number(form.get("batch")));
        body.put("cgpa", number(form.get("cgpa")));
        String error = !String.valueOf(form.get("password")).equals(form.get("confirmPassword")) ? "The two passwords do not match" : null;
        if (error == null) {
            try {
                JsonNode res = gateway.json(HttpMethod.POST, "/api/auth/student/register", body, null, request);
                flash.addFlashAttribute("info", res.path("message").asText("Account created. You can log in now."));
                return "redirect:/login?portal=STUDENT";
            } catch (GatewayException ex) {
                error = ex.display();
            }
        }
        model.addAttribute("form", formOf(STUDENT_FIELDS, form));
        model.addAttribute("error", error);
        model.addAttribute("captcha", captcha(request));
        return "auth/register-student";
    }

    private static Object number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    /** Only the non-secret fields, each present (possibly empty) so the template can read all of them. */
    private static Map<String, String> registerForm(Map<String, String> submitted) {
        return formOf(REGISTER_FIELDS, submitted);
    }

    private static Map<String, String> formOf(List<String> fields, Map<String, String> submitted) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String field : fields) {
            form.put(field, submitted.getOrDefault(field, ""));
        }
        return form;
    }

    /** Captcha image as data URL (disabled when captcha is switched off or the API is down). */
    private Captcha captcha(HttpServletRequest request) {
        try {
            JsonNode c = gateway.json(HttpMethod.GET, "/api/auth/captcha", null, null, request);
            if (!c.path("enabled").asBoolean(false)) {
                return Captcha.disabled();
            }
            return new Captcha(true, c.path("captchaId").asText(), c.path("image").asText(), false);
        } catch (GatewayException ex) {
            return Captcha.unreachable();
        }
    }

    @GetMapping("/login/captcha")
    @org.springframework.web.bind.annotation.ResponseBody
    public Captcha refreshCaptcha(HttpServletRequest request) {
        return captcha(request);
    }
}
