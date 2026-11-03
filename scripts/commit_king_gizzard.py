import re
import sys
import subprocess

KEY_PATH = r"C:\Users\sharm\Downloads\aficio-vm_key.pem"
VM_IP = "20.219.11.69"
VM_USER = "azureuser"
LOG_PATH = r"C:\Users\sharm\.gemini\antigravity\brain\9c6d4ecd-d6d9-4379-a205-7181c7e4c404\.system_generated\tasks\task-12457.log"

def run_remote_sql_bytes(sql_bytes):
    cmd = [
        "ssh", "-i", KEY_PATH,
        "-o", "StrictHostKeyChecking=no",
        f"{VM_USER}@{VM_IP}",
        "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db"
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = proc.communicate(input=sql_bytes)
    return stdout.decode('utf-8', errors='replace'), stderr.decode('utf-8', errors='replace')

def main():
    with open(LOG_PATH, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    current_album = None
    raw_tracks = []
    
    album_re = re.compile(r"^---\s+\[\d+/\d+\]\s+Album:\s+(.+?)\s+---")
    track_re = re.compile(r"^[✓\u2713]\s+'(.+?)'\s+->\s+([A-Za-z0-9_-]+)\s+\((\d+)s\)")

    for line in lines:
        line_str = line.strip()
        m_alb = album_re.match(line_str)
        if m_alb:
            current_album = m_alb.group(1).strip()
            continue
        
        m_trk = track_re.match(line_str)
        if m_trk and current_album:
            title = m_trk.group(1).strip()
            vid = m_trk.group(2).strip()
            dur = int(m_trk.group(3))
            raw_tracks.append({
                'album': current_album,
                'title': title,
                'video_id': vid,
                'duration': dur,
                'artist': 'King Gizzard & The Lizard Wizard'
            })

    print(f"Parsed {len(raw_tracks)} verified tracks.")
    # Deduplicate by video_id for the INSERT
    unique_by_vid = {}
    for t in raw_tracks:
        unique_by_vid[t['video_id']] = t
    tracks = list(unique_by_vid.values())
    print(f"Deduplicated to {len(tracks)} unique song_ids.")

    escaped_rows = []
    for t in tracks:
        s_id = t['video_id'].replace("'", "''")
        t_tit = t['title'].replace("'", "''")
        t_art = t['artist'].replace("'", "''")
        t_alb = t['album'].replace("'", "''")
        t_dur = t['duration']
        escaped_rows.append(f"('{s_id}', '{t_tit}', '{t_art}', '{t_alb}', {t_dur})")

    # Step 1: Insert into youtube_catalog
    print("\n[Step 1] Inserting unique rows into youtube_catalog...")
    insert_sql = f"""
    INSERT INTO youtube_catalog (song_id, title, artist, album, duration)
    VALUES {', '.join(escaped_rows)}
    ON CONFLICT (song_id) DO UPDATE 
    SET title = EXCLUDED.title, artist = EXCLUDED.artist, album = EXCLUDED.album, duration = EXCLUDED.duration;
    """
    out, err = run_remote_sql_bytes(insert_sql.encode('utf-8'))
    print("STDOUT:", out.strip())
    if err.strip():
        print("STDERR:", err.strip())

    # Step 2: Clear poisonous topic playlists
    print("\n[Step 2] Clearing poisonous playlist IDs from album table...")
    clear_sql = """
    UPDATE album a
    SET youtube_playlist_id = NULL
    FROM artist ar
    WHERE a.artist_id = ar.id
      AND LOWER(ar.name) LIKE '%gizzard%';
    """
    out, err = run_remote_sql_bytes(clear_sql.encode('utf-8'))
    print("STDOUT:", out.strip())
    if err.strip():
        print("STDERR:", err.strip())

    # Step 3: Heal track table
    print("\n[Step 3] Healing local tracks in track table...")
    update_sql = """
    UPDATE track t
    SET youtube_video_id = yc.song_id
    FROM youtube_catalog yc, album a, artist ar
    WHERE t.album_id = a.id
      AND a.artist_id = ar.id
      AND LOWER(ar.name) LIKE '%gizzard%'
      AND LOWER(t.title) = LOWER(yc.title)
      AND yc.song_id IS NOT NULL;
    """
    out, err = run_remote_sql_bytes(update_sql.encode('utf-8'))
    print("STDOUT:", out.strip())
    if err.strip():
        print("STDERR:", err.strip())

    # Step 4: Fuzzy healing for quotes/hyphens
    print("\n[Step 4] Fuzzy healing for quotes/hyphens...")
    fuzzy_sql = """
    UPDATE track t
    SET youtube_video_id = yc.song_id
    FROM youtube_catalog yc, album a, artist ar
    WHERE t.album_id = a.id
      AND a.artist_id = ar.id
      AND LOWER(ar.name) LIKE '%gizzard%'
      AND (t.youtube_video_id IS NULL OR t.youtube_video_id = '')
      AND replace(replace(replace(LOWER(t.title), '’', ''''), '–', '-'), '‐', '-') = 
          replace(replace(replace(LOWER(yc.title), '’', ''''), '–', '-'), '‐', '-')
      AND yc.song_id IS NOT NULL;
    """
    out, err = run_remote_sql_bytes(fuzzy_sql.encode('utf-8'))
    print("STDOUT:", out.strip())
    if err.strip():
        print("STDERR:", err.strip())

if __name__ == '__main__':
    main()
