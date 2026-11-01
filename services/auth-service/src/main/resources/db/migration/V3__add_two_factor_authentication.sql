ALTER TABLE app_user
    ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    RENAME COLUMN secret2fa TO secret_2fa;