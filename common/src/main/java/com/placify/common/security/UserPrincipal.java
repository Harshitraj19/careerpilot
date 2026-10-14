package com.placify.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The authenticated caller of a request, built from the headers the gateway adds
 * after validating the JWT. For service-to-service calls the principal is the
 * internal system user (role is null, {@link #internal()} is true).
 */
public record UserPrincipal(String userId, String loginId, Role role, String name, boolean internal) {

    public static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";

    public static UserPrincipal system() {
        return new UserPrincipal("system", "system", null, "System", true);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        if (role != null) {
            list.add(new SimpleGrantedAuthority(role.authority()));
        }
        if (internal) {
            list.add(new SimpleGrantedAuthority(INTERNAL_AUTHORITY));
        }
        return list;
    }

    public boolean is(Role r) {
        return role == r;
    }

    public boolean isStaff() {
        return role == Role.TPO || role == Role.ADMIN;
    }
}
