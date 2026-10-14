-- Submission service schema

CREATE TABLE submissions (
    id               CHAR(36)      NOT NULL,
    user_id          CHAR(36)      NOT NULL,
    question_id      CHAR(36)      NOT NULL,
    language_id      INT           NOT NULL,
    source_code      MEDIUMTEXT    NOT NULL,
    status           VARCHAR(30)   NOT NULL,
    mode             VARCHAR(15)   NOT NULL,
    contest_id       BIGINT        NULL,
    attempt_id       BIGINT        NULL,
    test_id          BIGINT        NULL,
    testcases_passed INT           NOT NULL DEFAULT 0,
    testcases_failed INT           NOT NULL DEFAULT 0,
    total_testcases  INT           NOT NULL DEFAULT 0,
    execution_time   DECIMAL(8,3)  NULL,
    memory_used      DECIMAL(10,2) NULL,
    score            INT           NOT NULL DEFAULT 0,
    callback_secret  VARCHAR(64)   NULL,
    compile_output   TEXT          NULL,
    ai_review        TEXT          NULL,
    event_published  BOOLEAN       NOT NULL DEFAULT FALSE,
    submission_time  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sub_user (user_id, submission_time),
    KEY idx_sub_question (question_id),
    KEY idx_sub_contest (contest_id),
    KEY idx_sub_test (test_id),
    KEY idx_sub_status (status)
);

CREATE TABLE submission_results (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    submission_id  CHAR(36)      NOT NULL,
    testcase_id    CHAR(36)      NOT NULL,
    hidden         BOOLEAN       NOT NULL DEFAULT FALSE,
    status         VARCHAR(30)   NOT NULL,
    execution_time DECIMAL(8,3)  NULL,
    memory_used    DECIMAL(10,2) NULL,
    output         TEXT          NULL,
    error_message  TEXT          NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_result (submission_id, testcase_id),
    CONSTRAINT fk_result_sub FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
);

CREATE TABLE submission_tokens (
    token         VARCHAR(64) NOT NULL,
    submission_id CHAR(36)    NOT NULL,
    testcase_id   CHAR(36)    NOT NULL,
    hidden        BOOLEAN     NOT NULL DEFAULT FALSE,
    status        VARCHAR(15) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (token),
    KEY idx_token_sub (submission_id, testcase_id),
    KEY idx_token_status (status, expires_at),
    CONSTRAINT fk_token_sub FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
);

CREATE TABLE plagiarism_checks (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    scope                VARCHAR(15)  NOT NULL,
    scope_id             BIGINT       NOT NULL,
    threshold            DECIMAL(4,3) NOT NULL,
    submissions_compared INT          NOT NULL DEFAULT 0,
    pairs_flagged        INT          NOT NULL DEFAULT 0,
    engine               VARCHAR(60)  NULL,
    requested_by         CHAR(36)     NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_plag_scope (scope, scope_id)
);

CREATE TABLE plagiarism_pairs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    check_id     BIGINT       NOT NULL,
    question_id  CHAR(36)     NOT NULL,
    submission_a CHAR(36)     NOT NULL,
    submission_b CHAR(36)     NOT NULL,
    user_a       CHAR(36)     NOT NULL,
    user_b       CHAR(36)     NOT NULL,
    similarity   DECIMAL(5,4) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pair_check (check_id),
    CONSTRAINT fk_pair_check FOREIGN KEY (check_id) REFERENCES plagiarism_checks (id) ON DELETE CASCADE
);
