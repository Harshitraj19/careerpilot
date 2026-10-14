-- Auth service schema

CREATE TABLE users (
    id                  CHAR(36)      NOT NULL,
    login_id            VARCHAR(320)  NOT NULL,
    email               VARCHAR(320)  NOT NULL,
    password_hash       VARCHAR(255)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    phone               VARCHAR(20)   NULL,
    role                VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    first_login         BOOLEAN       NOT NULL DEFAULT FALSE,
    failed_attempts     INT           NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)   NULL,
    two_factor_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    status_reason       VARCHAR(255)  NULL,
    last_login_at       DATETIME(6)   NULL,
    password_changed_at DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login (login_id),
    KEY idx_users_role_status (role, status),
    KEY idx_users_email (email)
);

CREATE TABLE company_signups (
    user_id        CHAR(36)     NOT NULL,
    company_name   VARCHAR(255) NOT NULL,
    website        VARCHAR(500) NULL,
    industry       VARCHAR(255) NULL,
    contact_person VARCHAR(255) NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_signup_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE login_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    CHAR(36)     NULL,
    login_id   VARCHAR(320) NOT NULL,
    portal     VARCHAR(20)  NULL,
    ip         VARCHAR(64)  NULL,
    user_agent VARCHAR(500) NULL,
    success    BOOLEAN      NOT NULL,
    reason     VARCHAR(100) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_history_user (user_id, created_at),
    KEY idx_history_time (created_at)
);

CREATE TABLE otp_codes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(320) NOT NULL,
    purpose    VARCHAR(30)  NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_otp_email_purpose (email, purpose, created_at)
);
