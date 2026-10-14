-- Analytics service schema

CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    service       VARCHAR(60)   NOT NULL,
    actor_user_id VARCHAR(36)   NULL,
    actor_role    VARCHAR(20)   NULL,
    action        VARCHAR(80)   NOT NULL,
    target        VARCHAR(255)  NULL,
    details       VARCHAR(2000) NULL,
    success       BIT(1)        NOT NULL,
    occurred_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_time (occurred_at),
    KEY idx_audit_actor (actor_user_id, occurred_at),
    KEY idx_audit_action (action, occurred_at)
);

CREATE TABLE readiness_snapshots (
    user_id        CHAR(36)     NOT NULL,
    score          INT          NOT NULL,
    coding         DOUBLE       NOT NULL,
    aptitude       DOUBLE       NOT NULL,
    ats            DOUBLE       NOT NULL,
    profile        DOUBLE       NOT NULL,
    mock           DOUBLE       NOT NULL,
    contests       DOUBLE       NOT NULL,
    cgpa           DECIMAL(4,2) NULL,
    backlogs       INT          NOT NULL DEFAULT 0,
    branch         VARCHAR(100) NULL,
    batch          INT          NULL,
    computed_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_ready_batch (batch, score)
);

CREATE TABLE placement_records (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    student_user_id CHAR(36)      NOT NULL,
    offer_id        BIGINT        NOT NULL,
    company_name    VARCHAR(255)  NOT NULL,
    ctc_lpa         DECIMAL(6,2)  NULL,
    tier            VARCHAR(20)   NULL,
    placed_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_offer (offer_id),
    KEY idx_record_student (student_user_id)
);

-- Training data for the placement-chance model: one row per student per finished season
CREATE TABLE historical_outcomes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    source      VARCHAR(15)  NOT NULL,
    batch       INT          NULL,
    cgpa        DOUBLE       NOT NULL,
    backlogs    INT          NOT NULL,
    coding      DOUBLE       NOT NULL,
    aptitude    DOUBLE       NOT NULL,
    ats         DOUBLE       NOT NULL,
    mock        DOUBLE       NOT NULL,
    contests    DOUBLE       NOT NULL,
    placed      BIT(1)       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_hist_source (source)
);
