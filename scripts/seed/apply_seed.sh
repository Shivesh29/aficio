#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

echo "=== Seeding auth_db with 15 realistic music enthusiast accounts ==="
docker exec -i aficio-postgres psql -U aficio_user -d auth_db < "$DIR/01_seed_auth_users.sql"

echo "=== Seeding social_db with authentic reviews, comments, follows, and lists ==="
docker exec -i aficio-postgres psql -U aficio_user -d social_db < "$DIR/02_seed_social_community.sql"

echo "=== Seeding catalog_db with listening histories ==="
docker exec -i aficio-postgres psql -U aficio_user -d catalog_db < "$DIR/03_seed_catalog_history.sql"

echo "=== Evicting Redis cache so new reviews and counts reflect immediately ==="
docker exec -i aficio-redis redis-cli FLUSHDB || true

echo "=== Seed completed successfully! ==="

