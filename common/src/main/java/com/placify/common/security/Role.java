package com.placify.common.security;

/**
 * The four user roles of CareerPilot. Each role has its own login portal.
 */
public enum Role {
    STUDENT,
    COMPANY,
    TPO,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Role from(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        if (v.startsWith("ROLE_")) {
            v = v.substring(5);
        }
        return Role.valueOf(v);
    }
}
