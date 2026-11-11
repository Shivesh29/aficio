-- ==============================================================================
-- 03_seed_catalog_history.sql
-- Database: catalog_db
-- Populates listening history for seed users to power personalized feeds & history tabs
-- ==============================================================================

INSERT INTO user_album_history (username, album_id, played_at, synced) VALUES
-- Elena (Shoegaze & dream pop)
('elena_vinyl', 2, NOW() - INTERVAL '3 days', true),
('elena_vinyl', 28, NOW() - INTERVAL '4 days', true),
('elena_vinyl', 134, NOW() - INTERVAL '5 days', true),

-- Taro (Psych rock & King Gizzard)
('taro_psych', 20, NOW() - INTERVAL '2 days', true),
('taro_psych', 22, NOW() - INTERVAL '5 days', true),
('taro_psych', 8, NOW() - INTERVAL '7 days', true),

-- Maya (Indie & jangle pop)
('maya_indie', 28, NOW() - INTERVAL '1 day', true),
('maya_indie', 29, NOW() - INTERVAL '2 days', true),
('maya_indie', 2, NOW() - INTERVAL '6 days', true),

-- Marcus (Audiophile classics & jazz/rock)
('marcus_beats', 74, NOW() - INTERVAL '3 days', true),
('marcus_beats', 134, NOW() - INTERVAL '4 days', true),

-- David (Grunge & 90s alt)
('david_grunge', 134, NOW() - INTERVAL '1 day', true),
('david_grunge', 1, NOW() - INTERVAL '3 days', true),
('david_grunge', 76, NOW() - INTERVAL '5 days', true)

ON CONFLICT (username, album_id) DO UPDATE SET
    played_at = EXCLUDED.played_at,
    synced = EXCLUDED.synced;

