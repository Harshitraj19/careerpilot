package com.placify.common.security;

import com.placify.common.web.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Static helper to read the authenticated user anywhere in a request.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UserPrincipal get() {
        return find().orElseThrow(() -> ApiException.unauthorized("Not authenticated"));
    }

    public static String id() {
        return get().userId();
    }

    public static Role role() {
        return get().role();
    }
}
