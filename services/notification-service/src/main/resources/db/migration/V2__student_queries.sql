-- Help desk: queries raised by students and the conversation with the placement office

CREATE TABLE student_queries (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    student_user_id   CHAR(36)      NOT NULL,
    student_name      VARCHAR(255)  NULL,
    student_login     VARCHAR(100)  NULL,
    category          VARCHAR(40)   NOT NULL,
    subject           VARCHAR(200)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    awaiting_student  BIT(1)        NOT NULL DEFAULT b'0',
    resolved_by_name  VARCHAR(255)  NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    resolved_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_query_student (student_user_id, status, updated_at),
    KEY idx_query_status (status, updated_at)
);

CREATE TABLE query_messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    query_id        BIGINT        NOT NULL,
    author_user_id  CHAR(36)      NOT NULL,
    author_name     VARCHAR(255)  NULL,
    author_role     VARCHAR(20)   NOT NULL,
    message         VARCHAR(4000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_qmsg_query (query_id, created_at),
    CONSTRAINT fk_qmsg_query FOREIGN KEY (query_id) REFERENCES student_queries (id) ON DELETE CASCADE
);
