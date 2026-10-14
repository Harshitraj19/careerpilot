package com.placify.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Turns the trusted headers added by the gateway (or by another service on an
 * internal call) into a Spring Security authentication.
 *
 * Headers are only trusted when the shared secret matches, so a client that
 * reaches a service directly cannot impersonate a user.
 */
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

    private final PlacifySecurityProperties properties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        boolean fromGateway = matches(request.getHeader(SecurityHeaders.GATEWAY_SECRET), properties.getGatewaySecret());
        boolean internal = matches(request.getHeader(SecurityHeaders.INTERNAL_SECRET), properties.getInternalSecret());

        if (fromGateway || internal) {
            String userId = request.getHeader(SecurityHeaders.USER_ID);
            String roleHeader = request.getHeader(SecurityHeaders.USER_ROLE);
            UserPrincipal principal;
            if (userId != null && !userId.isBlank() && roleHeader != null && !roleHeader.isBlank()) {
                principal = new UserPrincipal(
                        userId,
                        decode(request.getHeader(SecurityHeaders.USER_LOGIN)),
                        Role.from(roleHeader),
                        decode(request.getHeader(SecurityHeaders.USER_NAME)),
                        internal);
            } else if (internal) {
                principal = UserPrincipal.system();
            } else {
                principal = null;
            }
            if (principal != null) {
                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean matches(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
