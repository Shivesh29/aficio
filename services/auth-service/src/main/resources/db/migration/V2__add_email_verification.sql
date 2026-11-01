ALTER TABLE app_user
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN verification_token_expires_at TIMESTAMP;

CREATE UNIQUE INDEX idx_app_user_verification_token ON app_user(verification_token);