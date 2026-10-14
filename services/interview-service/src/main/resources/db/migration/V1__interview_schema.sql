-- Interview service schema

CREATE TABLE interview_evaluations (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    job_id            BIGINT        NOT NULL,
    round_id          BIGINT        NOT NULL,
    student_user_id   CHAR(36)      NOT NULL,
    company_user_id   CHAR(36)      NOT NULL,
    interviewer_name  VARCHAR(255)  NULL,
    technical         INT           NOT NULL,
    problem_solving   INT           NOT NULL,
    communication     INT           NOT NULL,
    culture_fit       INT           NOT NULL,
    overall_score     INT           NOT NULL,
    result            VARCHAR(15)   NOT NULL,
    strengths         TEXT          NULL,
    concerns          TEXT          NULL,
    feedback          TEXT          NULL,
    share_feedback    BIT(1)        NOT NULL DEFAULT b'0',
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_round_student (round_id, student_user_id),
    KEY idx_eval_student (student_user_id)
);

CREATE TABLE interview_experiences (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    author_user_id   CHAR(36)      NOT NULL,
    author_name      VARCHAR(255)  NULL,
    anonymous        BIT(1)        NOT NULL DEFAULT b'0',
    company_name     VARCHAR(255)  NOT NULL,
    company_key      VARCHAR(255)  NOT NULL,
    job_role         VARCHAR(255)  NOT NULL,
    interview_year   INT           NOT NULL,
    result           VARCHAR(15)   NOT NULL,
    difficulty       VARCHAR(10)   NOT NULL,
    rounds_summary   TEXT          NULL,
    content          TEXT          NOT NULL,
    questions_asked  TEXT          NULL,
    tips             TEXT          NULL,
    status           VARCHAR(15)   NOT NULL,
    review_note      VARCHAR(1000) NULL,
    reviewed_by      CHAR(36)      NULL,
    upvotes          INT           NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL,
    reviewed_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_exp_company (company_key, status),
    KEY idx_exp_status (status, created_at)
);

CREATE TABLE experience_votes (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    experience_id BIGINT      NOT NULL,
    user_id       CHAR(36)    NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vote (experience_id, user_id),
    CONSTRAINT fk_vote_exp FOREIGN KEY (experience_id) REFERENCES interview_experiences (id) ON DELETE CASCADE
);

CREATE TABLE prep_summaries (
    company_key       VARCHAR(255) NOT NULL,
    company_name      VARCHAR(255) NOT NULL,
    summary_json      TEXT         NOT NULL,
    experience_count  INT          NOT NULL,
    generated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (company_key)
);
