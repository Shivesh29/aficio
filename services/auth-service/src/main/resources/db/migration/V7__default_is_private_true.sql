-- Set is_private default to true for all new accounts
ALTER TABLE app_user ALTER COLUMN is_private SET DEFAULT TRUE;

