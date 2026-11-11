-- ==============================================================================
-- 01_seed_auth_users.sql
-- Database: auth_db
-- Populates 15 realistic music enthusiast accounts for demo and community vibe
-- Password for all seed accounts: TestPilotPass123!
-- ==============================================================================

INSERT INTO app_user (
    id, username, email, password_hash, display_name, bio, avatar_url,
    enabled, email_verified, two_factor_enabled, is_private,
    api_key_storage_preference, auth_provider
) VALUES
(
    101,
    'elena_vinyl',
    'elena.vinyl@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Elena Rostova',
    'Analog purist, shoegaze obsessive & bedroom guitarist. Always hunting for original 90s pressings.',
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    102,
    'marcus_beats',
    'marcus.beats@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Marcus Vance',
    'Digging through 70s jazz fusion, boom-bap drum breaks & modular synthesizers. Hi-Fi audiophile.',
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    103,
    'julian_postpunk',
    'julian.postpunk@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Julian K.',
    '80s coldwave, post-punk, chorus guitars & dark romanticism. Joy Division, The Cure & modern goth.',
    'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    104,
    'sophia_ambient',
    'sophia.ambient@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Sophia Chen',
    'Modern classical, tape loops, Brian Eno & drone textures. Music for rainy afternoons and focus.',
    'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    105,
    'taro_psych',
    'taro.psych@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Taro Tanaka',
    'Tokyo psych rock, King Gizzard fanatic, Krautrock & fuzzy wah-wah pedals. Nonagon never stops.',
    'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    106,
    'maya_indie',
    'maya.indie@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Maya Lin',
    'Jangle pop, twee, Alvvays on repeat, lo-fi cassette demos. Pop melodies with bittersweet lyrics.',
    'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    107,
    'david_grunge',
    'david.grunge@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Dave Reynolds',
    'Sub Pop 90s era, raw guitar feedback, Kurt Cobain acoustic bootlegs & Pacific Northwest rock.',
    'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    108,
    'lucas_prog',
    'lucas.prog@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Lucas Meyer',
    'Time signatures in 7/8, polyrhythms, microtonal tuning & Canterbury scene prog rock.',
    'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    109,
    'clara_folk',
    'clara.folk@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Clara Morales',
    'Nick Drake, fingerpicked acoustic guitar, autumnal chamber folk & melancholic storytelling.',
    'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    110,
    'nina_synth',
    'nina.synth@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Nina Vane',
    'French touch, Daft Punk, electro-funk & Roland Juno-106 analog warmth.',
    'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    111,
    'sam_slowcore',
    'sam.slowcore@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Sam Wright',
    'Low, Duster, Bedhead, slow tempos, massive room reverb & hypnotic minimalism.',
    'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    112,
    'arjun_fusion',
    'arjun.fusion@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Arjun Patel',
    'Snarky Puppy, progressive jazz, math rock, Jaco Pastorius basslines & syncopation.',
    'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    113,
    'leo_altrock',
    'leo.altrock@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Leo Bennett',
    'Pavement, Guided by Voices, Dinosaur Jr., slacker rock & vintage college radio anthems.',
    'https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    114,
    'hannah_shoegaze',
    'hannah.shoegaze@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Hannah Brooks',
    'Reverse reverb, Jazzmaster vibrato, feedback loops & wall-of-sound textures.',
    'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
),
(
    115,
    'oliver_kraut',
    'oliver.kraut@aficio.internal',
    '$2a$10$cUg.2BSr.ohlqj8dXjUeAuqL.BvSvkYfJGlsHHn2CaaFoCPyLse.S',
    'Oliver Schmidt',
    'Can, Neu!, motorik 4/4 driving rhythms, tape experiments & hypnotic German avant-garde.',
    'https://images.unsplash.com/photo-1528892952291-009c663ce843?auto=format&fit=crop&w=250&q=80',
    true, true, false, false, 'NONE', 'LOCAL'
)
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    bio = EXCLUDED.bio,
    avatar_url = EXCLUDED.avatar_url;

-- Ensure auto-increment sequence remains well ahead of seed IDs
SELECT setval('app_user_id_seq', (SELECT GREATEST(MAX(id), 150) FROM app_user));

