-- Placement service schema

CREATE TABLE placement_policy (
    id                      BIGINT       NOT NULL,
    dream_min_ctc           DECIMAL(6,2) NOT NULL,
    super_dream_min_ctc     DECIMAL(6,2) NOT NULL,
    one_offer_policy        BOOLEAN      NOT NULL,
    max_active_applications INT          NULL,
    ppt_mandatory           BOOLEAN      NOT NULL,
    resume_required         BOOLEAN      NOT NULL,
    updated_by              CHAR(36)     NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO placement_policy (id, dream_min_ctc, super_dream_min_ctc, one_offer_policy, max_active_applications,
                              ppt_mandatory, resume_required, updated_at)
VALUES (1, 10.00, 20.00, TRUE, 15, FALSE, TRUE, NOW(6));

CREATE TABLE jobs (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    company_user_id      CHAR(36)      NOT NULL,
    company_name         VARCHAR(255)  NOT NULL,
    title                VARCHAR(255)  NOT NULL,
    description          TEXT          NULL,
    required_skills      TEXT          NULL,
    location             VARCHAR(255)  NULL,
    job_type             VARCHAR(30)   NOT NULL,
    ctc_lpa              DECIMAL(6,2)  NULL,
    stipend              INT           NULL,
    tier                 VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    min_cgpa             DECIMAL(4,2)  NULL,
    allowed_branches     TEXT          NULL,
    max_backlogs         INT           NULL,
    eligible_batch       INT           NULL,
    min_tenth            DECIMAL(5,2)  NULL,
    min_twelfth          DECIMAL(5,2)  NULL,
    application_deadline DATETIME(6)   NULL,
    review_note          VARCHAR(1000) NULL,
    published_at         DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_jobs_company (company_user_id),
    KEY idx_jobs_status (status, published_at)
);

CREATE TABLE rounds (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    job_id             BIGINT        NOT NULL,
    sequence           INT           NOT NULL,
    name               VARCHAR(255)  NOT NULL,
    round_type         VARCHAR(30)   NOT NULL,
    scheduled_at       DATETIME(6)   NULL,
    description        VARCHAR(2000) NULL,
    assessment_test_id BIGINT        NULL,
    cutoff_percent     DECIMAL(5,2)  NULL,
    PRIMARY KEY (id),
    KEY idx_rounds_job (job_id, sequence),
    CONSTRAINT fk_round_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE TABLE applications (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    job_id           BIGINT        NOT NULL,
    student_user_id  CHAR(36)      NOT NULL,
    student_name     VARCHAR(255)  NULL,
    reg_no           VARCHAR(30)   NULL,
    branch           VARCHAR(100)  NULL,
    status           VARCHAR(20)   NOT NULL,
    current_round_id BIGINT        NULL,
    match_score      INT           NULL,
    rank_score       INT           NULL,
    status_note      VARCHAR(1000) NULL,
    applied_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_application (job_id, student_user_id),
    KEY idx_app_student (student_user_id, status),
    KEY idx_app_job_status (job_id, status),
    CONSTRAINT fk_app_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE TABLE round_results (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    round_id        BIGINT        NOT NULL,
    application_id  BIGINT        NOT NULL,
    student_user_id CHAR(36)      NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    score           DECIMAL(6,2)  NULL,
    remarks         VARCHAR(2000) NULL,
    decided_by      CHAR(36)      NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_round_result (round_id, application_id),
    KEY idx_rr_student (student_user_id),
    CONSTRAINT fk_rr_round FOREIGN KEY (round_id) REFERENCES rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_rr_app FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
);

CREATE TABLE calendar_events (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(30)   NOT NULL,
    description     VARCHAR(2000) NULL,
    job_id          BIGINT        NULL,
    company_name    VARCHAR(255)  NULL,
    start_time      DATETIME(6)   NOT NULL,
    end_time        DATETIME(6)   NOT NULL,
    location        VARCHAR(500)  NULL,
    attendance_code VARCHAR(12)   NULL,
    created_by      CHAR(36)      NOT NULL,
    reminder_sent   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_events_time (start_time),
    KEY idx_events_job (job_id)
);

CREATE TABLE event_attendance (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    event_id        BIGINT      NOT NULL,
    student_user_id CHAR(36)    NOT NULL,
    marked_by       CHAR(36)    NOT NULL,
    marked_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance (event_id, student_user_id),
    CONSTRAINT fk_att_event FOREIGN KEY (event_id) REFERENCES calendar_events (id) ON DELETE CASCADE
);

CREATE TABLE interview_slots (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    job_id          BIGINT       NOT NULL,
    round_id        BIGINT       NOT NULL,
    company_user_id CHAR(36)     NOT NULL,
    start_time      DATETIME(6)  NOT NULL,
    end_time        DATETIME(6)  NOT NULL,
    location        VARCHAR(500) NULL,
    booked_by       CHAR(36)     NULL,
    booked_at       DATETIME(6)  NULL,
    reminder_sent   BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_slots_job (job_id, round_id),
    KEY idx_slots_student (booked_by, start_time),
    CONSTRAINT fk_slot_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
);

CREATE TABLE offers (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    application_id    BIGINT        NULL,
    job_id            BIGINT        NULL,
    student_user_id   CHAR(36)      NOT NULL,
    company_user_id   CHAR(36)      NULL,
    company_name      VARCHAR(255)  NOT NULL,
    role              VARCHAR(255)  NOT NULL,
    ctc_lpa           DECIMAL(6,2)  NULL,
    tier              VARCHAR(20)   NOT NULL,
    offer_letter_path VARCHAR(500)  NULL,
    status            VARCHAR(20)   NOT NULL,
    off_campus        BOOLEAN       NOT NULL DEFAULT FALSE,
    response_deadline DATETIME(6)   NULL,
    responded_at      DATETIME(6)   NULL,
    verified_by       CHAR(36)      NULL,
    verified_at       DATETIME(6)   NULL,
    remarks           VARCHAR(1000) NULL,
    reminder_sent     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_offers_student (student_user_id, status),
    KEY idx_offers_company (company_user_id),
    KEY idx_offers_status (status)
);

CREATE TABLE certificates (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    offer_id          BIGINT       NOT NULL,
    student_user_id   CHAR(36)     NOT NULL,
    verification_code VARCHAR(40)  NOT NULL,
    student_name      VARCHAR(255) NOT NULL,
    reg_no            VARCHAR(30)  NOT NULL,
    branch            VARCHAR(100) NULL,
    company_name      VARCHAR(255) NOT NULL,
    role              VARCHAR(255) NOT NULL,
    ctc_lpa           DECIMAL(6,2) NULL,
    issued_at         DATETIME(6)  NOT NULL,
    revoked           BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cert_offer (offer_id),
    UNIQUE KEY uk_cert_code (verification_code),
    KEY idx_cert_student (student_user_id)
);
