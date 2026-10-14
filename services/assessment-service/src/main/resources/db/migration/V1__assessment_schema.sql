-- Assessment service schema

CREATE TABLE mcq_questions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    topic         VARCHAR(20)  NOT NULL,
    subtopic      VARCHAR(100) NULL,
    difficulty    VARCHAR(10)  NOT NULL,
    question_text TEXT         NOT NULL,
    options_json  TEXT         NOT NULL,
    correct_index INT          NOT NULL,
    explanation   TEXT         NULL,
    source        VARCHAR(10)  NOT NULL,
    approved      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by    CHAR(36)     NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_mcq_topic (topic, approved, difficulty)
);

CREATE TABLE tests (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    title                     VARCHAR(255)  NOT NULL,
    instructions              VARCHAR(2000) NULL,
    test_type                 VARCHAR(10)   NOT NULL,
    status                    VARCHAR(10)   NOT NULL,
    owner_user_id             CHAR(36)      NOT NULL,
    owner_name                VARCHAR(255)  NULL,
    job_id                    BIGINT        NULL,
    round_id                  BIGINT        NULL,
    duration_minutes          INT           NOT NULL,
    window_start              DATETIME(6)   NOT NULL,
    window_end                DATETIME(6)   NOT NULL,
    cutoff_percent            DECIMAL(5,2)  NOT NULL,
    mcq_count                 INT           NOT NULL DEFAULT 0,
    marks_per_mcq             INT           NOT NULL DEFAULT 1,
    coding_count              INT           NOT NULL DEFAULT 0,
    randomize                 BOOLEAN       NOT NULL DEFAULT TRUE,
    proctored                 BOOLEAN       NOT NULL DEFAULT FALSE,
    camera_required           BOOLEAN       NOT NULL DEFAULT FALSE,
    max_violations            INT           NOT NULL DEFAULT 5,
    auto_submit_on_violations BOOLEAN       NOT NULL DEFAULT TRUE,
    proctoring_checks         VARCHAR(500)  NULL,
    reminder_sent             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tests_owner (owner_user_id),
    KEY idx_tests_round (round_id),
    KEY idx_tests_window (status, window_start, window_end)
);

CREATE TABLE test_pool_items (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    test_id BIGINT      NOT NULL,
    kind    VARCHAR(10) NOT NULL,
    ref_id  VARCHAR(36) NOT NULL,
    points  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pool (test_id, kind, ref_id),
    CONSTRAINT fk_pool_test FOREIGN KEY (test_id) REFERENCES tests (id) ON DELETE CASCADE
);

CREATE TABLE attempts (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    test_id            BIGINT       NOT NULL,
    student_user_id    CHAR(36)     NOT NULL,
    student_name       VARCHAR(255) NULL,
    student_login      VARCHAR(64)  NULL,
    status             VARCHAR(20)  NOT NULL,
    started_at         DATETIME(6)  NOT NULL,
    deadline_at        DATETIME(6)  NOT NULL,
    submitted_at       DATETIME(6)  NULL,
    mcq_score          DECIMAL(7,2) NULL,
    coding_score       DECIMAL(7,2) NULL,
    total_score        DECIMAL(7,2) NULL,
    max_score          DECIMAL(7,2) NULL,
    percent            DECIMAL(5,2) NULL,
    passed             BOOLEAN      NULL,
    trust_score        INT          NULL,
    proctoring_flagged BOOLEAN      NOT NULL DEFAULT FALSE,
    termination_reason VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt (test_id, student_user_id),
    KEY idx_attempt_student (student_user_id),
    KEY idx_attempt_status (status, deadline_at),
    CONSTRAINT fk_attempt_test FOREIGN KEY (test_id) REFERENCES tests (id) ON DELETE CASCADE
);

CREATE TABLE attempt_questions (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id         BIGINT       NOT NULL,
    kind               VARCHAR(10)  NOT NULL,
    ref_id             VARCHAR(36)  NOT NULL,
    order_index        INT          NOT NULL,
    option_order       VARCHAR(100) NULL,
    selected_index     INT          NULL,
    correct            BOOLEAN      NULL,
    max_marks          DECIMAL(7,2) NOT NULL,
    marks              DECIMAL(7,2) NULL,
    best_submission_id CHAR(36)     NULL,
    coding_passed      INT          NULL,
    coding_total       INT          NULL,
    PRIMARY KEY (id),
    KEY idx_aq_attempt (attempt_id, order_index),
    CONSTRAINT fk_aq_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
);

CREATE TABLE practice_stats (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    CHAR(36)    NOT NULL,
    topic      VARCHAR(20) NOT NULL,
    attempted  INT         NOT NULL DEFAULT 0,
    correct    INT         NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice (user_id, topic)
);
