-- Coding service schema

CREATE TABLE questions (
    id            CHAR(36)     NOT NULL,
    title         VARCHAR(500) NOT NULL,
    description   TEXT         NOT NULL,
    input_format  TEXT         NULL,
    output_format TEXT         NULL,
    constraints   TEXT         NULL,
    sample_input  TEXT         NULL,
    sample_output TEXT         NULL,
    explanation   TEXT         NULL,
    difficulty    VARCHAR(10)  NOT NULL,
    tags          VARCHAR(500) NULL,
    rating        INT          NOT NULL,
    visibility    VARCHAR(15)  NOT NULL,
    time_limit    DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    memory_limit  DECIMAL(8,2) NOT NULL DEFAULT 256.00,
    created_by    CHAR(36)     NULL,
    solved_count  INT          NOT NULL DEFAULT 0,
    attempt_count INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_q_difficulty (difficulty),
    KEY idx_q_visibility (visibility, active),
    KEY idx_q_rating (rating)
);

CREATE TABLE testcases (
    id              CHAR(36)     NOT NULL,
    question_id     CHAR(36)     NOT NULL,
    input           TEXT         NOT NULL,
    expected_output TEXT         NOT NULL,
    hidden          BOOLEAN      NOT NULL DEFAULT TRUE,
    time_limit      DECIMAL(5,2) NULL,
    memory_limit    DECIMAL(8,2) NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tc_question (question_id),
    CONSTRAINT fk_tc_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);

CREATE TABLE user_ratings (
    user_id         CHAR(36)     NOT NULL,
    name            VARCHAR(255) NULL,
    rating          INT          NOT NULL,
    solved          INT          NOT NULL DEFAULT 0,
    submissions     INT          NOT NULL DEFAULT 0,
    easy_solved     INT          NOT NULL DEFAULT 0,
    medium_solved   INT          NOT NULL DEFAULT 0,
    hard_solved     INT          NOT NULL DEFAULT 0,
    contests_joined INT          NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_rating (rating)
);

CREATE TABLE user_question_status (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         CHAR(36)    NOT NULL,
    question_id     CHAR(36)    NOT NULL,
    solved          BOOLEAN     NOT NULL DEFAULT FALSE,
    attempts        INT         NOT NULL DEFAULT 0,
    first_solved_at DATETIME(6) NULL,
    last_attempt_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_uqs (user_id, question_id)
);

CREATE TABLE contests (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(255)  NOT NULL,
    description VARCHAR(2000) NULL,
    start_time  DATETIME(6)   NOT NULL,
    end_time    DATETIME(6)   NOT NULL,
    created_by  CHAR(36)      NOT NULL,
    finalized   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_contest_time (start_time, end_time)
);

CREATE TABLE contest_questions (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    contest_id  BIGINT   NOT NULL,
    question_id CHAR(36) NOT NULL,
    points      INT      NOT NULL,
    order_index INT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cq (contest_id, question_id),
    CONSTRAINT fk_cq_contest FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE CASCADE
);

CREATE TABLE contest_participants (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    contest_id      BIGINT       NOT NULL,
    user_id         CHAR(36)     NOT NULL,
    name            VARCHAR(255) NULL,
    score           INT          NOT NULL DEFAULT 0,
    penalty_minutes BIGINT       NOT NULL DEFAULT 0,
    final_rank      INT          NULL,
    joined_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cp (contest_id, user_id),
    CONSTRAINT fk_cp_contest FOREIGN KEY (contest_id) REFERENCES contests (id) ON DELETE CASCADE
);

CREATE TABLE contest_problem_status (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    contest_id     BIGINT      NOT NULL,
    user_id        CHAR(36)    NOT NULL,
    question_id    CHAR(36)    NOT NULL,
    solved         BOOLEAN     NOT NULL DEFAULT FALSE,
    wrong_attempts INT         NOT NULL DEFAULT 0,
    solved_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cps (contest_id, user_id, question_id)
);

CREATE TABLE badges (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     CHAR(36)     NOT NULL,
    code        VARCHAR(60)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    awarded_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_badge (user_id, code)
);
