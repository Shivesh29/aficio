-- Add profile & privacy fields to app_user
ALTER TABLE app_user
    ADD COLUMN display_name VARCHAR(100),
    ADD COLUMN bio TEXT,
    ADD COLUMN avatar_url VARCHAR(512),
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;
