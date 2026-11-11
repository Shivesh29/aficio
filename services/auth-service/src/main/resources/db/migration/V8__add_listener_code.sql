ALTER TABLE app_user ADD COLUMN IF NOT EXISTS listener_code VARCHAR(32);

-- Populate existing rows with unique random unguessable codes
UPDATE app_user
SET listener_code = CONCAT(
    'AF-',
    UPPER(SUBSTRING(MD5(RANDOM()::TEXT || id::TEXT || CLOCK_TIMESTAMP()::TEXT) FROM 1 FOR 4)),
    '-',
    UPPER(SUBSTRING(MD5(RANDOM()::TEXT || email::TEXT || id::TEXT || CLOCK_TIMESTAMP()::TEXT) FROM 5 FOR 4))
)
WHERE listener_code IS NULL;

ALTER TABLE app_user ALTER COLUMN listener_code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_listener_code ON app_user (listener_code);

