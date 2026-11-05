import os
import io
import re
import sys
import json
import time
import zipfile
import subprocess
import requests
from tqdm import tqdm

# Force UTF-8 encoding in Windows console
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

KEY_PATH = r"C:\Users\sharm\Downloads\aficio-vm_key.pem"
VM_IP = "20.219.11.69"
VM_USER = "azureuser"
ZENODO_URL = "https://zenodo.org/records/13983028/files/main.zip?download=1"
DOWNLOAD_DEST = r"C:\Users\sharm\Downloads\discogs_main.zip"
BATCH_SIZE = 50000

def download_dataset(url, dest_path):
    print("=" * 70)
    print("      DISCOGS-VI-YT CATALOG INGESTION (493,000 VERIFIED TRACKS)     ")
    print("=" * 70)
    print(f"Target: {dest_path}")

    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    existing_bytes = 0
    if os.path.exists(dest_path):
        existing_bytes = os.path.getsize(dest_path)
        print(f"Found existing partial file: {existing_bytes / (1024*1024):.1f} MB")

    # Probe total size
    head_resp = requests.head(url, headers=headers, allow_redirects=True)
    total_size = int(head_resp.headers.get('content-length', 0))

    if existing_bytes > 0 and existing_bytes == total_size:
        print(f"✓ Archive already completely downloaded ({total_size / (1024*1024):.1f} MB).")
        return

    req_headers = dict(headers)
    if existing_bytes > 0:
        req_headers['Range'] = f'bytes={existing_bytes}-'

    print(f"Downloading from Zenodo ({total_size / (1024*1024):.1f} MB)...")
    res = requests.get(url, headers=req_headers, stream=True)
    
    if res.status_code == 206:
        mode = 'ab'
        pbar_initial = existing_bytes
    else:
        mode = 'wb'
        pbar_initial = 0
        if existing_bytes > 0:
            print("Note: Server does not support resume (HTTP 200 returned). Restarting download from beginning.")

    with open(dest_path, mode) as f:
        with tqdm(total=total_size, initial=pbar_initial, unit='B', unit_scale=True, desc="Download") as pbar:
            for chunk in res.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    f.write(chunk)
                    pbar.update(len(chunk))
    print("\n✓ Download completed successfully.")

def run_remote_sql(sql):
    cmd = [
        "ssh", "-i", KEY_PATH,
        "-o", "StrictHostKeyChecking=no",
        f"{VM_USER}@{VM_IP}",
        "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db"
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = proc.communicate(input=sql.encode('utf-8'))
    if proc.returncode != 0:
        raise RuntimeError(f"Remote SQL failed: {stderr.decode('utf-8', errors='replace')}")
    return stdout.decode('utf-8', errors='replace').strip()

def pipe_batch_to_postgres(csv_lines_text):
    # Load into staging table and upsert to avoid duplicate key conflicts
    sql = f"""
    CREATE TEMP TABLE stage_discogs (
        song_id varchar(11),
        title varchar(500),
        artist varchar(500),
        album varchar(500),
        duration int
    );
    \\copy stage_discogs (song_id, title, artist, album, duration) FROM STDIN WITH (FORMAT csv)
{csv_lines_text}
\\.
    INSERT INTO youtube_catalog (song_id, title, artist, album, duration)
    SELECT DISTINCT ON (song_id) song_id, title, artist, album, duration
    FROM stage_discogs
    ON CONFLICT (song_id) DO UPDATE 
    SET title = EXCLUDED.title, artist = EXCLUDED.artist, album = EXCLUDED.album, duration = EXCLUDED.duration;
    DROP TABLE stage_discogs;
    """

    cmd = [
        "ssh", "-i", KEY_PATH,
        "-o", "StrictHostKeyChecking=no",
        f"{VM_USER}@{VM_IP}",
        "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db -v ON_ERROR_STOP=1"
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = proc.communicate(input=sql.encode('utf-8'))
    if proc.returncode != 0:
        raise RuntimeError(f"PSQL COPY failed (code {proc.returncode}): {stderr.decode('utf-8', errors='replace')}")
    out_str = stdout.decode('utf-8', errors='replace').strip()
    return out_str

def extract_video_id(url_or_id):
    if not url_or_id or not isinstance(url_or_id, str):
        return None
    s = url_or_id.strip()
    if len(s) == 11 and re.match(r'^[a-zA-Z0-9_-]{11}$', s):
        return s
    if 'v=' in s:
        cand = s.split('v=')[1].split('&')[0].split('#')[0].strip()
        if len(cand) == 11:
            return cand
    if 'youtu.be/' in s:
        cand = s.split('youtu.be/')[1].split('?')[0].split('&')[0].split('#')[0].strip()
        if len(cand) == 11:
            return cand
    match = re.search(r'([a-zA-Z0-9_-]{11})', s)
    if match:
        return match.group(1)
    return None

def process_and_ingest():
    download_dataset(ZENODO_URL, DOWNLOAD_DEST)

    print(f"\nOpening {DOWNLOAD_DEST}...")
    with zipfile.ZipFile(DOWNLOAD_DEST, 'r') as zf:
        file_list = zf.namelist()
        print(f"Archive contains {len(file_list)} files.")
        
        target_name = None
        for name in file_list:
            if 'Discogs-VI-YT' in name and name.endswith('.jsonl'):
                target_name = name
                break
        
        if not target_name:
            for name in file_list:
                if 'YT' in name and (name.endswith('.jsonl') or name.endswith('.json')):
                    target_name = name
                    break

        if not target_name:
            raise FileNotFoundError(f"Could not find Discogs-VI-YT JSONL file in archive. Files: {file_list[:10]}")

        print(f"Located target dataset file: {target_name}")
        print("Parsing cliques and extracting verified YouTube tracks...")
        
        unique_songs = {}
        total_cliques = 0
        total_ingested = 0
        batch_count = 0

        with zf.open(target_name) as f:
            reader = io.TextIOWrapper(f, encoding='utf-8')
            for line in reader:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except Exception:
                    continue

                total_cliques += 1

                for v in obj.get('versions', []):
                    v_yt = v.get('youtube_video', [])
                    fallback_url = v_yt[0].get('url') if v_yt else None
                    
                    for t in v.get('tracks', []):
                        t_yt = t.get('youtube_video', [])
                        url = t_yt[0].get('url') if t_yt else fallback_url
                        vid = extract_video_id(url)
                        if not vid:
                            continue

                        # Extract metadata
                        raw_title = t.get('track_title') or 'Unknown Title'
                        raw_album = t.get('release_title') or t.get('title') or 'Single'
                        artists_list = t.get('release_artist_names') or t.get('track_artist_names') or []
                        raw_artist = ', '.join(artists_list) if artists_list else 'Unknown Artist'
                        dur = int(t.get('duration') or 0)

                        title = raw_title.replace('\r', ' ').replace('\n', ' ').replace('"', '""').strip()[:490]
                        artist = raw_artist.replace('\r', ' ').replace('\n', ' ').replace('"', '""').strip()[:490]
                        album = raw_album.replace('\r', ' ').replace('\n', ' ').replace('"', '""').strip()[:490]

                        # Store unique song by video ID
                        unique_songs[vid] = f'"{vid}","{title}","{artist}","{album}",{dur}'

                        if len(unique_songs) >= BATCH_SIZE:
                            batch_count += 1
                            csv_text = '\n'.join(unique_songs.values())
                            print(f"  [Batch {batch_count}] Flushing {len(unique_songs):,} unique tracks to PostgreSQL on Azure VM...")
                            res = pipe_batch_to_postgres(csv_text)
                            total_ingested += len(unique_songs)
                            print(f"  ✓ Batch {batch_count} committed: {res.splitlines()[-2] if len(res.splitlines()) > 1 else res}")
                            unique_songs.clear()

            if unique_songs:
                batch_count += 1
                csv_text = '\n'.join(unique_songs.values())
                print(f"  [Batch {batch_count}] Flushing final {len(unique_songs):,} unique tracks to PostgreSQL on Azure VM...")
                res = pipe_batch_to_postgres(csv_text)
                total_ingested += len(unique_songs)
                print(f"  ✓ Batch {batch_count} committed: {res.splitlines()[-2] if len(res.splitlines()) > 1 else res}")
                unique_songs.clear()

    print("\n" + "=" * 70)
    print(f"SUCCESS: Processed {total_cliques:,} cliques and ingested {total_ingested:,} unique verified tracks!")
    print("=" * 70)

if __name__ == '__main__':
    process_and_ingest()
