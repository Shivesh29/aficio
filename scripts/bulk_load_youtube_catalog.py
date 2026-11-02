import os
import io
import time
import subprocess
import duckdb
import glob
import sys

# Ensure UTF-8 output on Windows console
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
KEY_PATH = os.path.expanduser('~/Downloads/aficio-vm_key.pem')
VM_IP = '20.219.11.69'
VM_USER = 'azureuser'
PARQUET_DIR = os.path.expanduser(r'~\Downloads\LAION-DISCO-12M')
BATCH_SIZE = 150000

def run_remote_sql(sql):
    cmd = f'ssh -i "{KEY_PATH}" -o StrictHostKeyChecking=no {VM_USER}@{VM_IP} "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db"'
    proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    stdout, stderr = proc.communicate(input=sql)
    if proc.returncode != 0:
        raise RuntimeError(f"Remote SQL failed: {stderr}")
    return stdout.strip()

def pipe_csv_to_postgres(csv_bytes):
    cmd = f'ssh -i "{KEY_PATH}" -o StrictHostKeyChecking=no {VM_USER}@{VM_IP} "docker exec -i aficio-postgres psql -U aficio_user -d catalog_db -c \\"\\copy youtube_catalog (song_id, title, artist, album, duration) FROM STDIN WITH (FORMAT csv)\\""'
    proc = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = proc.communicate(input=csv_bytes)
    if proc.returncode != 0:
        raise RuntimeError(f"PSQL COPY failed: {stderr.decode('utf-8', errors='replace')}")
    return stdout.decode('utf-8', errors='replace').strip()

def main():
    print("=====================================================================")
    print("      AFICIO BULK INGESTION: LAION-DISCO-12M -> AZURE POSTGRES       ")
    print("=====================================================================")

    # 1. Check current count in PostgreSQL
    count_out = run_remote_sql("SELECT count(*) FROM youtube_catalog;")
    current_count = 0
    for line in count_out.split('\n'):
        if line.strip().isdigit():
            current_count = int(line.strip())
            break
    print(f"\n[1/5] Current rows in youtube_catalog: {current_count:,}")

    # 2. Locate Parquet Shards
    parquet_files = sorted(glob.glob(os.path.join(PARQUET_DIR, '*.parquet')))
    if not parquet_files:
        raise FileNotFoundError(f"No parquet files found in {PARQUET_DIR}")
    print(f"\n[2/5] Located {len(parquet_files)} Parquet shards:")
    for f in parquet_files:
        print(f"  - {os.path.basename(f)} ({os.path.getsize(f)/(1024*1024):.1f} MB)")

    # 3. Stream Parquet Shards to PostgreSQL
    print(f"\n[3/5] Streaming ingestion (batch size: {BATCH_SIZE:,} rows)...")
    total_loaded = current_count
    start_time = time.time()
    con = duckdb.connect()

    for shard_idx, shard_path in enumerate(parquet_files):
        shard_name = os.path.basename(shard_path)
        shard_count = con.execute(f"SELECT count(*) FROM read_parquet('{shard_path}')").fetchone()[0]

        # Skip shard if already completely loaded (e.g. shard 1 was 2,464,184 rows)
        if current_count >= 2464184 and shard_idx == 0:
            print(f"\n--> Skipping Shard 1/5 ({shard_name}): Already loaded in PostgreSQL.")
            continue

        shard_t0 = time.time()
        print(f"\n--> Ingesting Shard {shard_idx+1}/{len(parquet_files)}: {shard_name} ({shard_count:,} rows)")

        offset = 0
        while offset < shard_count:
            batch_t0 = time.time()
            df = con.execute(f"""
                SELECT 
                    song_id,
                    REPLACE(REPLACE(LEFT(title, 490), E'\r', ' '), E'\n', ' ') as title,
                    REPLACE(REPLACE(LEFT(array_to_string(artist_names, ', '), 490), E'\r', ' '), E'\n', ' ') as artist,
                    REPLACE(REPLACE(LEFT(album_name, 490), E'\r', ' '), E'\n', ' ') as album,
                    duration
                FROM read_parquet('{shard_path}')
                LIMIT {BATCH_SIZE} OFFSET {offset}
            """).df()

            if len(df) == 0:
                break

            # Convert to CSV
            csv_buf = io.StringIO()
            df.to_csv(csv_buf, index=False, header=False, sep=',', quoting=1)
            csv_bytes = csv_buf.getvalue().encode('utf-8')

            # Pipe to Postgres
            pipe_csv_to_postgres(csv_bytes)

            offset += len(df)
            total_loaded += len(df)
            batch_dur = time.time() - batch_t0
            rate = len(df) / batch_dur if batch_dur > 0 else 0
            pct = (offset / shard_count) * 100
            print(f"   [{pct:5.1f}%] Shard {shard_idx+1}: {offset:,}/{shard_count:,} rows | Batch: {batch_dur:.1f}s ({rate:,.0f} rows/s) | Total: {total_loaded:,}")

        print(f"  [OK] Finished {shard_name} in {time.time()-shard_t0:.1f}s")

    total_duration = time.time() - start_time
    print(f"\n  [OK] ALL SHARDS STREAMED! {total_loaded:,} tracks loaded in {total_duration/60:.2f} minutes.")

    # 4. Rebuild Fast B-Tree Indexes
    print("\n[4/5] Rebuilding B-Tree search indexes on Azure PostgreSQL (this takes ~60-90s)...")
    idx_t0 = time.time()
    index_sql = """
    SET maintenance_work_mem = '256MB';
    CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_lower ON youtube_catalog (LOWER(artist));
    CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_title ON youtube_catalog (LOWER(artist), LOWER(title));
    CREATE INDEX IF NOT EXISTS idx_yt_cat_artist_album ON youtube_catalog (LOWER(artist), LOWER(album));
    """
    run_remote_sql(index_sql)
    print(f"  [OK] Indexes rebuilt successfully in {time.time()-idx_t0:.1f}s!")

    # 5. Reconcile Existing Tracks & Verify Table Stats
    print("\n[5/5] Reconciling existing database tracks and verifying final stats...")
    reconcile_sql = """
    UPDATE track t
    SET youtube_video_id = sub.song_id
    FROM (
      SELECT DISTINCT ON (t2.id) t2.id as track_id, y.song_id
      FROM track t2
      JOIN album a ON t2.album_id = a.id
      JOIN artist art ON a.artist_id = art.id
      JOIN youtube_catalog y ON LOWER(art.name) = LOWER(y.artist) AND LOWER(t2.title) = LOWER(y.title)
      ORDER BY t2.id, (LOWER(a.title) = LOWER(y.album)) DESC
    ) sub
    WHERE t.id = sub.track_id
      AND (t.youtube_video_id IS NULL OR t.youtube_video_id != sub.song_id);

    SELECT count(*) as total_youtube_catalog_rows FROM youtube_catalog;
    """
    stats_out = run_remote_sql(reconcile_sql)
    print(stats_out)

    # Check disk usage
    disk_out = run_remote_sql("SELECT pg_size_pretty(pg_total_relation_size('youtube_catalog')) as table_and_index_size;")
    print("Table + Index Size in PostgreSQL:")
    print(disk_out)

    print("\n=====================================================================")
    print("                 INGESTION COMPLETED SUCCESSFULLY!                   ")
    print("=====================================================================")

if __name__ == '__main__':
    main()
