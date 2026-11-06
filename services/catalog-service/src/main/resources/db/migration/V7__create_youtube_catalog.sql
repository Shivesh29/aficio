CREATE TABLE IF NOT EXISTS youtube_catalog (
    song_id VARCHAR(11) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    artist VARCHAR(500) NOT NULL,
    album VARCHAR(500),
    duration INTEGER
);

CREATE INDEX IF NOT EXISTS idx_yt_cat_album_lower ON youtube_catalog (LOWER(album));
CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_album ON youtube_catalog (LOWER(artist), LOWER(album));
CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_lower ON youtube_catalog (LOWER(artist));
CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_title ON youtube_catalog (LOWER(artist), LOWER(title));

