-- Clean up unsynced stub albums with no YouTube playback data
DELETE FROM public.track WHERE album_id IN (
    SELECT a.id FROM public.album a
    WHERE a.youtube_playlist_id IS NULL
    AND NOT EXISTS (SELECT 1 FROM public.track t WHERE t.album_id = a.id AND t.youtube_video_id IS NOT NULL)
);

DELETE FROM public.album WHERE youtube_playlist_id IS NULL
AND NOT EXISTS (SELECT 1 FROM public.track t WHERE t.album_id = album.id AND t.youtube_video_id IS NOT NULL);

-- Clean up orphaned artists with no remaining albums
DELETE FROM public.artist WHERE NOT EXISTS (
    SELECT 1 FROM public.album a WHERE a.artist_id = artist.id
);

-- Create table to track albums synced and played by each individual user
CREATE TABLE IF NOT EXISTS public.user_album_history (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    album_id BIGINT NOT NULL,
    played_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_album_history_album FOREIGN KEY (album_id) REFERENCES public.album(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_album_history UNIQUE (username, album_id)
);

CREATE INDEX IF NOT EXISTS idx_user_album_history_user ON public.user_album_history(username);
CREATE INDEX IF NOT EXISTS idx_user_album_history_played_at ON public.user_album_history(played_at DESC);

-- Seed initial history for existing active users so previously discovered/reviewed releases appear
INSERT INTO public.user_album_history (username, album_id, synced, played_at)
SELECT 'kirk', id, true, CURRENT_TIMESTAMP
FROM public.album
WHERE id IN (122, 123, 124, 125, 126)
ON CONFLICT (username, album_id) DO NOTHING;

INSERT INTO public.user_album_history (username, album_id, synced, played_at)
SELECT 'shivesh', id, true, CURRENT_TIMESTAMP
FROM public.album
WHERE id IN (1, 2, 74, 117, 128)
ON CONFLICT (username, album_id) DO NOTHING;
