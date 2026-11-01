ALTER TABLE public.track
    ADD COLUMN IF NOT EXISTS youtube_video_id varchar(255);

ALTER TABLE public.album
    ALTER COLUMN cover_art_url TYPE varchar(1024);
