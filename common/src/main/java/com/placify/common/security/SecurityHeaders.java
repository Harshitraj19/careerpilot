package com.placify.common.security;

/**
 * Header names shared by the gateway and every service.
 * The gateway strips these from incoming client requests and re-adds them
 * only after it has validated the JWT, so services can trust them as long as
 * the gateway secret matches.
 */
public final class SecurityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_LOGIN = "X-User-Login";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_NAME = "X-User-Name";
    public static final String GATEWAY_SECRET = "X-Gateway-Secret";
    public static final String INTERNAL_SECRET = "X-Internal-Secret";

    private SecurityHeaders() {
    }
}
