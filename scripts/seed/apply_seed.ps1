# Run seed scripts inside Docker aficio-postgres container
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "=== Seeding auth_db with 15 realistic music enthusiast accounts ===" -ForegroundColor Cyan
Get-Content "$scriptDir\01_seed_auth_users.sql" | docker exec -i aficio-postgres psql -U aficio_user -d auth_db

Write-Host "=== Seeding social_db with authentic reviews, comments, follows, and lists ===" -ForegroundColor Cyan
Get-Content "$scriptDir\02_seed_social_community.sql" | docker exec -i aficio-postgres psql -U aficio_user -d social_db

Write-Host "=== Seeding catalog_db with listening histories ===" -ForegroundColor Cyan
Get-Content "$scriptDir\03_seed_catalog_history.sql" | docker exec -i aficio-postgres psql -U aficio_user -d catalog_db

Write-Host "=== Evicting Redis cache ===" -ForegroundColor Cyan
docker exec -i aficio-redis redis-cli FLUSHDB

Write-Host "=== Seed completed successfully! ===" -ForegroundColor Green

