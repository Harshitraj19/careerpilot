package com.placify.web.controller;

import com.placify.web.gateway.GatewayClient;
import com.placify.web.session.SessionService;
import com.placify.web.session.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Backend-for-Frontend proxy: the page's JavaScript calls /bff/api/..., this
 * controller adds the user's token (kept server-side) and forwards the call to
 * the API gateway. CSRF-protected like every other state-changing request.
 */
@RestController
@RequiredArgsConstructor
public class BffController {

    private static final String PREFIX = "/bff";
    private static final long MAX_BODY = 7L * 1024 * 1024;
    /** Session-changing calls must go through the server-side pages. */
    private static final Set<String> BLOCKED = Set.of("/api/auth/login", "/api/auth/login/2fa", "/api/auth/logout",
            "/api/auth/password/change");
    private static final List<String> PASS_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION,
            HttpHeaders.CACHE_CONTROL, "X-RateLimit-Remaining", "Retry-After");

    private final GatewayClient gateway;
    private final SessionService sessions;

    @RequestMapping("/bff/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        UserSession user = sessions.current(request).orElse(null);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Your session has expired. Please log in again.");
        }
        String path = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
        if (path.contains("..") || path.contains("//") || !path.startsWith("/api/") || BLOCKED.contains(path)) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        }
        String query = request.getQueryString();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        byte[] body = null;
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            if (request.getContentLengthLong() > MAX_BODY) {
                return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "The upload is too large");
            }
            body = request.getInputStream().readNBytes((int) MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "The upload is too large");
            }
        }
        ResponseEntity<byte[]> res = gateway.raw(method, path + (query == null ? "" : "?" + query), body,
                request.getContentType(), user.getToken(), request);

        if (res.getStatusCode().value() == 401) {
            sessions.signOut(request);
            return error(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                    "Your session has ended (timed out, or you logged in somewhere else). Please log in again.");
        }
        HttpHeaders headers = new HttpHeaders();
        for (String h : PASS_HEADERS) {
            String v = res.getHeaders().getFirst(h);
            if (v != null) {
                headers.set(h, v);
            }
        }
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(res.getBody(), headers, res.getStatusCode());
    }

    private static ResponseEntity<byte[]> error(HttpStatus status, String code, String message) {
        String json = "{\"status\":" + status.value() + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.getBytes());
    }
}
