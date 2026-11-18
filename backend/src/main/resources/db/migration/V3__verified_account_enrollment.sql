CREATE TABLE account_invitations (
    id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email varchar(254) NOT NULL,
    name varchar(100) NOT NULL,
    role enum('ADMIN','ATHLETE','COACH') NOT NULL,
    athlete_id bigint,
    created_by bigint NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    code_hash varchar(64) NOT NULL,
    expires_at datetime(6) NOT NULL,
    code_expires_at datetime(6) NOT NULL,
    sent_at datetime(6) NOT NULL,
    created_at datetime(6) NOT NULL,
    failures int NOT NULL,
    sends int NOT NULL,
    verified_at datetime(6),
    consumed_at datetime(6),
    user_id bigint,
    version bigint NOT NULL,
    KEY idx_account_invitation_email (email, consumed_at)
) ENGINE=InnoDB;

ALTER TABLE invitation_email_outbox MODIFY user_id bigint NULL, MODIFY invitation_id bigint NULL;
ALTER TABLE invitation_email_outbox ADD enrollment_id bigint NULL;
CREATE INDEX idx_invitation_mail_enrollment ON invitation_email_outbox (enrollment_id, id);
