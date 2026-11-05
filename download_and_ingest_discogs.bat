@echo off
setlocal
chcp 65001 >nul
title Aficio Discogs-VI-YT Bulk Ingestion Pipeline

echo ======================================================================
echo       AFICIO DISCOGS-VI-YT CATALOG INGESTION (493,000 TRACKS)
echo ======================================================================
echo  This script will:
echo   1. Download Discogs-VI-YT archive (1.4 GB) directly from Zenodo
echo   2. Stream & extract 493,000 verified YouTube music mappings
echo   3. Bulk-upsert directly into PostgreSQL youtube_catalog on Azure VM
echo   4. Automatically skip duplicates and preserve existing records
echo ======================================================================
echo.

python scripts\ingest_discogs_catalog.py

echo.
pause

