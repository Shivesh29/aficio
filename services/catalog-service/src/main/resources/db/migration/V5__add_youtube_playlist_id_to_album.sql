ALTER TABLE album
    ADD COLUMN IF NOT EXISTS youtube_playlist_id varchar(255);
