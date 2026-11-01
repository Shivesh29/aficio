-- Add AES-256-GCM encrypted YouTube API key and user storage preference
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS encrypted_youtube_api_key TEXT,
    ADD COLUMN IF NOT EXISTS api_key_storage_preference VARCHAR(30) DEFAULT 'NONE';
