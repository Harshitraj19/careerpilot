-- AI helper service schema

CREATE TABLE tutor_sessions (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     CHAR(36)    NOT NULL,
    question_id CHAR(36)    NOT NULL,
    hint_level  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tutor_session (user_id, question_id)
);

CREATE TABLE tutor_messages (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    sender     VARCHAR(10) NOT NULL,
    content    TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tutor_msg_session (session_id),
    CONSTRAINT fk_tutor_msg_session FOREIGN KEY (session_id) REFERENCES tutor_sessions (id) ON DELETE CASCADE
);

CREATE TABLE mock_interviews (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        CHAR(36)     NOT NULL,
    target_role    VARCHAR(255) NOT NULL,
    interview_type VARCHAR(15)  NOT NULL,
    skills         VARCHAR(500) NULL,
    difficulty     VARCHAR(10)  NOT NULL,
    question_count INT          NOT NULL,
    status         VARCHAR(15)  NOT NULL,
    overall_score  INT          NULL,
    summary_json   TEXT         NULL,
    created_at     DATETIME(6)  NOT NULL,
    completed_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_mock_user (user_id, created_at)
);

CREATE TABLE mock_interview_turns (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    interview_id BIGINT      NOT NULL,
    seq          INT         NOT NULL,
    question     TEXT        NOT NULL,
    answer       TEXT        NULL,
    score        INT         NULL,
    feedback     TEXT        NULL,
    ideal_points TEXT        NULL,
    asked_at     DATETIME(6) NOT NULL,
    answered_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_turn_interview (interview_id, seq),
    CONSTRAINT fk_turn_interview FOREIGN KEY (interview_id) REFERENCES mock_interviews (id) ON DELETE CASCADE
);
