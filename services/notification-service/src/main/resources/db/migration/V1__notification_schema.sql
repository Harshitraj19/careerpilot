-- Notification service schema

CREATE TABLE notifications (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    CHAR(36)      NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    type       VARCHAR(40)   NOT NULL,
    link       VARCHAR(500)  NULL,
    is_read    BIT(1)        NOT NULL DEFAULT b'0',
    created_at DATETIME(6)   NOT NULL,
    read_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_notif_user (user_id, is_read, created_at)
);

CREATE TABLE announcements (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    author_user_id  CHAR(36)      NOT NULL,
    author_name     VARCHAR(255)  NULL,
    audience        VARCHAR(40)   NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    message         VARCHAR(2000) NOT NULL,
    link            VARCHAR(500)  NULL,
    send_email      BIT(1)        NOT NULL DEFAULT b'0',
    recipients      INT           NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ann_created (created_at)
);

CREATE TABLE email_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    recipient  VARCHAR(255)  NOT NULL,
    subject    VARCHAR(255)  NOT NULL,
    status     VARCHAR(10)   NOT NULL,
    error      VARCHAR(500)  NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_email_created (created_at)
);
