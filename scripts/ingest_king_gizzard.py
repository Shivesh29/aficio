import sys
import os
import json
import time
import urllib.request
import subprocess
from ytmusicapi import YTMusic
import requests

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

KEY_PATH = r"C:\Users\sharm\Downloads\aficio-vm_key.pem"
VM_IP = "20.219.11.69"
VM_USER = "azureuser"

def run_remote_sql(sql):
    cmd = f'ssh -i "{KEY_PATH}" -o StrictHostKeyChecking=no {VM_USER}@{VM_IP} "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db"'
    proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    stdout, stderr = proc.communicate(input=sql)
    if proc.returncode != 0:
        print(f"SQL Error: {stderr.strip()}", file=sys.stderr)
    return stdout.strip()

def fetch_mb_json(url):
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'AficioMusicApp/1.0 ( support@aficio.internal )'})
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            if e.code == 503:
                wait = 2.0 * (attempt + 1)
                print(f"  [MusicBrainz Rate Limited 503 - waiting {wait}s...]")
                time.sleep(wait)
            else:
                raise
        except Exception as ex:
            time.sleep(1.0)
    raise RuntimeError(f"MusicBrainz failed after 5 attempts: {url}")

def get_artist_albums(mbid):
    url = f"https://musicbrainz.org/ws/2/artist/{mbid}?inc=release-groups&fmt=json"
    data = fetch_mb_json(url)
    albums = [rg for rg in data.get('release-groups', []) if rg.get('primary-type') == 'Album' and not rg.get('secondary-types')]
    albums.sort(key=lambda x: x.get('first-release-date', ''))
    return albums

def get_release_group_tracks(rg_id):
    url = f"https://musicbrainz.org/ws/2/release?release-group={rg_id}&inc=recordings&fmt=json"
    data = fetch_mb_json(url)
    releases = data.get('releases', [])
    if not releases:
        return []
    
    best_release = max(releases, key=lambda r: len(r.get('media', [{}])[0].get('tracks', [])) if r.get('media') else 0)
    tracks = []
    for medium in best_release.get('media', []):
        for t in medium.get('tracks', []):
            tracks.append({
                'title': t.get('title'),
                'length': (t.get('length') or 0) // 1000,
                'position': t.get('position')
            })
    return tracks

def main():
    print("=" * 70)
    print("   AFICIO CATALOG INGESTION & TRANSPOSITION ENGINE: KING GIZZARD   ")
    print("=" * 70)
    
    mbid = "f58384a4-2ad2-4f24-89c5-c7b74ae1cce7"
    artist_name = "King Gizzard & The Lizard Wizard"
    
    yt = YTMusic()
    
    print("\n[1/4] Fetching official albums from MusicBrainz...")
    albums = get_artist_albums(mbid)
    print(f"Found {len(albums)} studio albums.")
    
    all_resolved = []
    
    print("\n[2/4] Resolving and verifying working video IDs for all albums...")
    for idx, alb in enumerate(albums, 1):
        alb_title = alb.get('title')
        rg_id = alb.get('id')
        print(f"\n--- [{idx}/{len(albums)}] Album: {alb_title} ---")
        
        try:
            time.sleep(1.2) # Polite MusicBrainz rate limit
            tracks = get_release_group_tracks(rg_id)
        except Exception as e:
            print(f"Failed to fetch MB tracks for {alb_title}: {e}")
            continue
            
        print(f"Found {len(tracks)} canonical tracks on MusicBrainz.")
        
        for t in tracks:
            title = t['title']
            expected_sec = t['length']
            
            # Search YouTube
            query = f"{artist_name} {title}"
            candidates = []
            
            try:
                # First try videos (music videos & bootlegger uploads which are embeddable)
                res_vids = yt.search(query, filter="videos")
                for r in (res_vids or [])[:4]:
                    candidates.append(r)
                
                # Also try songs
                res_songs = yt.search(query, filter="songs")
                for r in (res_songs or [])[:3]:
                    candidates.append(r)
            except Exception as ex:
                print(f"  Search error for '{title}': {ex}")
            
            best_vid = None
            best_dur = expected_sec
            
            for c in candidates:
                vid = c.get('videoId')
                if not vid:
                    continue
                
                dur_str = c.get('duration', '0:00')
                parts = dur_str.split(':')
                if len(parts) == 2:
                    d_sec = int(parts[0]) * 60 + int(parts[1])
                elif len(parts) == 3:
                    d_sec = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
                else:
                    d_sec = 0
                
                # If we have an expected duration, check tolerance (+- 20s)
                if expected_sec > 0 and abs(d_sec - expected_sec) > 20:
                    continue
                
                # Check oEmbed
                try:
                    oe = requests.get(f"https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={vid}&format=json", timeout=4)
                    if oe.status_code == 200:
                        best_vid = vid
                        best_dur = d_sec if d_sec > 0 else expected_sec
                        break
                except Exception:
                    continue
            
            if best_vid:
                print(f"  ✓ '{title}' -> {best_vid} ({best_dur}s)")
                all_resolved.append({
                    'song_id': best_vid,
                    'title': title,
                    'artist': artist_name,
                    'album': alb_title,
                    'duration': best_dur
                })
            else:
                print(f"  ✗ Could not find working embed for '{title}'")
            
            time.sleep(0.2) # Gentle request pacing
            
    print(f"\n[3/4] Successfully resolved {len(all_resolved)} verified tracks.")
    
    print("\n[4/4] Transposing to PostgreSQL (youtube_catalog + track table)...")
    
    # Generate bulk SQL insert for youtube_catalog
    escaped_rows = []
    for r in all_resolved:
        s_id = r['song_id'].replace("'", "''")
        t_tit = r['title'].replace("'", "''")
        t_art = r['artist'].replace("'", "''")
        t_alb = r['album'].replace("'", "''")
        t_dur = r['duration']
        escaped_rows.append(f"('{s_id}', '{t_tit}', '{t_art}', '{t_alb}', {t_dur})")
    
    if escaped_rows:
        sql = f"""
        BEGIN;
        INSERT INTO youtube_catalog (song_id, title, artist, album, duration)
        VALUES {', '.join(escaped_rows)}
        ON CONFLICT (song_id) DO UPDATE 
        SET title = EXCLUDED.title, artist = EXCLUDED.artist, album = EXCLUDED.album, duration = EXCLUDED.duration;
        
        -- Also heal local tracks for King Gizzard
        UPDATE track t
        SET youtube_video_id = yc.song_id
        FROM youtube_catalog yc, album a, artist ar
        WHERE t.album_id = a.id
          AND a.artist_id = ar.id
          AND LOWER(ar.name) LIKE '%gizzard%'
          AND LOWER(t.title) = LOWER(yc.title)
          AND yc.song_id IS NOT NULL;
        COMMIT;
        """
        out = run_remote_sql(sql)
        print(f"Database commit output:\n{out}")
        
    print("\nTransposition completed successfully!")

if __name__ == '__main__':
    main()
