#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE catalog_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalog_db')\gexec
    SELECT 'CREATE DATABASE social_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'social_db')\gexec
EOSQL
