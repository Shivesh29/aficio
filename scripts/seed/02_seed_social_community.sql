-- ==============================================================================
-- 02_seed_social_community.sql
-- Database: social_db
-- Populates authentic community reviews, comments, likes, follower graphs, and curated lists
-- ==============================================================================

-- 1. Curated Album Reviews
INSERT INTO review (id, user_id, album_id, rating, content, created_at) VALUES
(
    101, 107, 134, 5.0,
    'The definitive live acoustic performance in rock history. Cobain''s vocal break on "Where Did You Sleep Last Night" still gives chills thirty years later. The Meat Puppets covers are pure magic.',
    NOW() - INTERVAL '5 days'
),
(
    102, 101, 134, 5.0,
    'A masterclass in restraint. Stripping away the loud grunge distortion laid bare how genuinely phenomenal Kurt''s songwriting and chord progressions were. Pennyroyal Tea hits so differently here.',
    NOW() - INTERVAL '4 days'
),
(
    103, 109, 134, 4.5,
    'Lori Goldston''s acoustic cello on Dumb and Something in the Way elevates this from an MTV special to high art. Unmatched atmosphere.',
    NOW() - INTERVAL '2 days'
),
(
    104, 114, 2, 5.0,
    'The holy grail of dream pop and shoegaze. The glide guitar technique Kevin Shields created will never be replicated. It feels like swimming in warm pink clouds of sound.',
    NOW() - INTERVAL '12 days'
),
(
    105, 111, 2, 5.0,
    'Every single listen reveals another layer of buried frequencies. "To Here Knows When" and "Sometimes" are transcendental masterworks.',
    NOW() - INTERVAL '10 days'
),
(
    106, 106, 2, 4.5,
    'Loud, intoxicating, and completely uncompromising. An essential benchmark for guitar music and sound design.',
    NOW() - INTERVAL '8 days'
),
(
    107, 106, 28, 5.0,
    '"Archie, Marry Me" is arguably the greatest jangle-pop anthem of the 2010s. Molly Rankin''s vocal melodies are timeless and infectious.',
    NOW() - INTERVAL '7 days'
),
(
    108, 101, 28, 4.5,
    'Sparkling reverb, surf riffs, and clever lyricism. "Adult Diversion" and "Next of Kin" set such a high bar for indie pop debuts.',
    NOW() - INTERVAL '6 days'
),
(
    109, 106, 29, 5.0,
    'They didn''t just top their debut, they pushed into shoegaze fuzz on "Pharmacist" and dreamy motorik pop on "After The Earthquake". 14 tracks with zero skips.',
    NOW() - INTERVAL '3 days'
),
(
    110, 111, 29, 4.5,
    '"Tile by Tile" is sheer emotional brilliance. The fuzz pedals hit so much harder on this record while keeping the melodies pristine.',
    NOW() - INTERVAL '2 days'
),
(
    111, 105, 20, 5.0,
    'An endless sonic loop that never stops. "Gamma Knife" into "People-Vultures" is the most adrenaline-fueled garage-psych sequence ever recorded.',
    NOW() - INTERVAL '14 days'
),
(
    112, 108, 20, 5.0,
    'The seamless transitions make the entire album feel like a single 41-minute frantic suite. Genius pacing and relentless kinetic energy.',
    NOW() - INTERVAL '11 days'
),
(
    113, 108, 22, 5.0,
    'Polyrhythms in 7/8 and microtonal modular synth arpeggios. "Crumbling Castle" alone is an absolute epic.',
    NOW() - INTERVAL '9 days'
),
(
    114, 105, 22, 4.5,
    'The fact that they released this for free into the public domain while writing some of their tightest prog rock is legend status.',
    NOW() - INTERVAL '7 days'
),
(
    115, 102, 74, 5.0,
    'The production standard against which all studio albums will forever be measured. Alan Parsons'' engineering and Clare Torry''s vocals on "The Great Gig in the Sky" are unmatched.',
    NOW() - INTERVAL '15 days'
),
(
    116, 104, 74, 5.0,
    'Seamless transitions between ambient tape loops, clock ticking, heartbeat pulses, and Gilmour''s stratocaster tone. Timeless perfection.',
    NOW() - INTERVAL '13 days'
),
(
    117, 113, 31, 4.5,
    'Malkmus at his most unhinged and eclectic. "Grounded", "Rattled by the Rush", and "Father to a Sister of Thought" are peak 90s indie.',
    NOW() - INTERVAL '6 days'
),
(
    118, 107, 76, 4.5,
    'Raw, vulnerable, feedback-drenched guitars. The feedback squeal at the beginning of "Tired of Sex" is iconic 90s alternative rock.',
    NOW() - INTERVAL '8 days'
),
(
    119, 113, 10, 5.0,
    'Butch Vig''s production changed the entire landscape of popular music overnight. "Drain You" remains the best track on the record.',
    NOW() - INTERVAL '10 days'
),
(
    120, 105, 56, 4.5,
    'Planet B and Mars for the Rich prove they can master thrash metal just as convincingly as garage psych. The bass distortion is disgusting in the best way.',
    NOW() - INTERVAL '4 days'
)
ON CONFLICT (user_id, album_id) DO UPDATE SET
    content = EXCLUDED.content,
    rating = EXCLUDED.rating;

-- 2. Community Comments on Reviews
INSERT INTO review_comment (id, review_id, user_id, content, created_at) VALUES
(
    101, 104, 106,
    'Completely agree about "Sometimes" - that acoustic strumming buried underneath the crushing fuzz wall is mesmerizing.',
    NOW() - INTERVAL '7 days'
),
(
    102, 111, 108,
    'The loop back from "Road Train" right into "Robot Stop" still blows my mind when listening with gapless playback.',
    NOW() - INTERVAL '10 days'
),
(
    103, 101, 101,
    'The cover of Bowie''s "The Man Who Sold the World" might honestly be better than the original recording. Unbelievable atmosphere.',
    NOW() - INTERVAL '3 days'
),
(
    104, 107, 114,
    'Saw them play "Archie" live in Toronto and the entire room erupted. Pure joy.',
    NOW() - INTERVAL '5 days'
),
(
    105, 115, 104,
    'The quadraphonic synthesizer panning on "On the Run" was literally decades ahead of its time.',
    NOW() - INTERVAL '12 days'
)
ON CONFLICT (id) DO NOTHING;

-- 3. Review Likes (Social Validation)
INSERT INTO review_like (review_id, user_id, created_at) VALUES
(101, 101, NOW() - INTERVAL '4 days'),
(101, 102, NOW() - INTERVAL '3 days'),
(101, 106, NOW() - INTERVAL '3 days'),
(101, 109, NOW() - INTERVAL '2 days'),
(102, 107, NOW() - INTERVAL '3 days'),
(102, 113, NOW() - INTERVAL '2 days'),
(104, 101, NOW() - INTERVAL '11 days'),
(104, 106, NOW() - INTERVAL '9 days'),
(104, 111, NOW() - INTERVAL '8 days'),
(107, 101, NOW() - INTERVAL '6 days'),
(107, 113, NOW() - INTERVAL '5 days'),
(109, 101, NOW() - INTERVAL '2 days'),
(109, 114, NOW() - INTERVAL '1 day'),
(111, 102, NOW() - INTERVAL '13 days'),
(111, 108, NOW() - INTERVAL '12 days'),
(115, 104, NOW() - INTERVAL '14 days'),
(115, 108, NOW() - INTERVAL '13 days')
ON CONFLICT (user_id, review_id) DO NOTHING;

-- 4. Follow Relationships (Social Graph)
-- Connect seed users with each other + follow user ID 8 (Shivesh)
INSERT INTO follow (follower_id, following_id, status, created_at) VALUES
-- Seed users following Shivesh (ID 8)
(101, 8, 'ACCEPTED', NOW() - INTERVAL '4 days'),
(102, 8, 'ACCEPTED', NOW() - INTERVAL '3 days'),
(106, 8, 'ACCEPTED', NOW() - INTERVAL '3 days'),
(107, 8, 'ACCEPTED', NOW() - INTERVAL '2 days'),
(114, 8, 'ACCEPTED', NOW() - INTERVAL '1 day'),
-- Interconnected seed graph
(101, 114, 'ACCEPTED', NOW() - INTERVAL '10 days'),
(114, 101, 'ACCEPTED', NOW() - INTERVAL '9 days'),
(101, 106, 'ACCEPTED', NOW() - INTERVAL '8 days'),
(106, 101, 'ACCEPTED', NOW() - INTERVAL '8 days'),
(105, 108, 'ACCEPTED', NOW() - INTERVAL '12 days'),
(108, 105, 'ACCEPTED', NOW() - INTERVAL '11 days'),
(102, 104, 'ACCEPTED', NOW() - INTERVAL '7 days'),
(104, 102, 'ACCEPTED', NOW() - INTERVAL '6 days'),
(107, 113, 'ACCEPTED', NOW() - INTERVAL '5 days'),
(113, 107, 'ACCEPTED', NOW() - INTERVAL '5 days'),
(106, 111, 'ACCEPTED', NOW() - INTERVAL '4 days'),
(111, 106, 'ACCEPTED', NOW() - INTERVAL '3 days')
ON CONFLICT (follower_id, following_id) DO UPDATE SET status = 'ACCEPTED';

-- 5. Curated Public Music Lists
INSERT INTO music_list (id, owner_id, title, description, is_public, created_at) VALUES
(
    101, 101,
    'Essential 90s Alternative & Shoegaze',
    'From walls of reverse reverb to stripped-down acoustic grunge. Essential listening for guitar nerds.',
    true, NOW() - INTERVAL '8 days'
),
(
    102, 105,
    'Hypnotic Psych & Endless Loops',
    'Motorik beats, non-stop loops, fuzzy wah-wah pedals, and microtonal mind journeys.',
    true, NOW() - INTERVAL '10 days'
),
(
    103, 106,
    'Jangle Pop & Dream Fuzz',
    'Sweet melodies, jangly Rickenbackers, and bittersweet lyrics. The best indie pop records.',
    true, NOW() - INTERVAL '5 days'
),
(
    104, 102,
    'Late Night Hi-Fi Listening',
    'Masterful engineering, pristine dynamic range, and immersive stereo separation.',
    true, NOW() - INTERVAL '12 days'
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description;

-- 6. Items in Curated Lists
INSERT INTO music_list_item (list_id, album_id, position) VALUES
-- List 101: Essential 90s Alternative & Shoegaze
(101, 2, 1),    -- Loveless
(101, 134, 2),  -- MTV Unplugged in New York
(101, 1, 3),    -- In Utero
(101, 31, 4),   -- Wowee Zowee
(101, 76, 5),   -- Pinkerton
-- List 102: Hypnotic Psych & Endless Loops
(102, 20, 1),   -- Nonagon Infinity
(102, 22, 2),   -- Polygondwanaland
(102, 8, 3),    -- Murder of the Universe
(102, 23, 4),   -- Paper Mâché Dream Balloon
-- List 103: Jangle Pop & Dream Fuzz
(103, 28, 1),   -- Alvvays
(103, 29, 2),   -- Blue Rev
(103, 2, 3),    -- Loveless
-- List 104: Late Night Hi-Fi Listening
(104, 74, 1),   -- The Dark Side of the Moon
(104, 2, 2),    -- Loveless
(104, 134, 3)   -- MTV Unplugged in New York
ON CONFLICT (list_id, album_id) DO NOTHING;

-- Update sequences so subsequent inserts continue smoothly
SELECT setval('review_id_seq', (SELECT GREATEST(MAX(id), 250) FROM review));
SELECT setval('review_comment_id_seq', (SELECT GREATEST(MAX(id), 200) FROM review_comment));
SELECT setval('review_like_id_seq', (SELECT GREATEST(MAX(id), 200) FROM review_like));
SELECT setval('follow_id_seq', (SELECT GREATEST(MAX(id), 150) FROM follow));
SELECT setval('music_list_id_seq', (SELECT GREATEST(MAX(id), 150) FROM music_list));
SELECT setval('music_list_item_id_seq', (SELECT GREATEST(MAX(id), 200) FROM music_list_item));

