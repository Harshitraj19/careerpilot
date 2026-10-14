package com.placify.auth.domain;

/**
 * Account status. Only ACTIVE accounts can log in.
 * PENDING = company waiting for TPO approval.
 */
public enum UserStatus {
    ACTIVE,
    PENDING,
    REJECTED,
    BLOCKED
}
