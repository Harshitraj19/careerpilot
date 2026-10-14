package com.placify.web.session;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Creates and reads the browser session after a successful API login. */
@Service
public class SessionService {

    /** Starts the session from the gateway's login response. */
    public UserSession signIn(JsonNode login, HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate(); // new session id after login (session fixation protection)
        }
        HttpSession session = request.getSession(true);
        UserSession user = new UserSession(login.path("token").asText(), login.path("userId").asText(),
                login.path("loginId").asText(), login.path("name").asText(), login.path("role").asText(),
                login.path("firstLogin").asBoolean(false));
        int minutes = login.path("sessionTimeoutMinutes").asInt(30);
        session.setMaxInactiveInterval(Math.max(5, minutes) * 60);
        session.setAttribute(UserSession.KEY, user);

        var auth = new UsernamePasswordAuthenticationToken(user.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return user;
    }

    public Optional<UserSession> current(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return Optional.ofNullable(s == null ? null : (UserSession) s.getAttribute(UserSession.KEY));
    }

    /** The API session ended (expired, logged in elsewhere, blocked): end the web session too. */
    public void signOut(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
