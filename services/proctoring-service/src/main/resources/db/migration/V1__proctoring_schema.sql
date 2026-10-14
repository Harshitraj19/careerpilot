-- Proctoring service schema

CREATE TABLE proctoring_sessions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id           BIGINT       NOT NULL,
    test_id              BIGINT       NOT NULL,
    test_owner_user_id   CHAR(36)     NULL,
    student_user_id      CHAR(36)     NOT NULL,
    reference_photo_path VARCHAR(500) NULL,
    identity_verified    BOOLEAN      NULL,
    identity_similarity  DOUBLE       NULL,
    trust_score          INT          NOT NULL DEFAULT 100,
    violation_count      INT          NOT NULL DEFAULT 0,
    high_count           INT          NOT NULL DEFAULT 0,
    medium_count         INT          NOT NULL DEFAULT 0,
    low_count            INT          NOT NULL DEFAULT 0,
    frames_analysed      INT          NOT NULL DEFAULT 0,
    max_violations       INT          NOT NULL,
    auto_submit          BOOLEAN      NOT NULL,
    camera_required      BOOLEAN      NOT NULL,
    enabled_checks       VARCHAR(500) NULL,
    status               VARCHAR(15)  NOT NULL,
    consent_at           DATETIME(6)  NOT NULL,
    started_at           DATETIME(6)  NOT NULL,
    ended_at             DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_attempt (attempt_id),
    KEY idx_session_test (test_id),
    KEY idx_session_student (student_user_id)
);

CREATE TABLE proctoring_violations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    session_id    BIGINT       NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    severity      VARCHAR(10)  NOT NULL,
    source        VARCHAR(10)  NOT NULL,
    detail        VARCHAR(500) NULL,
    snapshot_path VARCHAR(500) NULL,
    occurred_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_violation_session (session_id, occurred_at),
    KEY idx_violation_time (occurred_at),
    CONSTRAINT fk_violation_session FOREIGN KEY (session_id) REFERENCES proctoring_sessions (id) ON DELETE CASCADE
);
