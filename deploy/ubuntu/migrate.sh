#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="${COPIMINE_ROOT:-/opt/copimine}"
ENV_FILE="${COPIMINE_ENV_FILE:-$PROJECT_ROOT/admin-web/.env}"
MIGRATIONS_DIR="${COPIMINE_MIGRATIONS_DIR:-$PROJECT_ROOT/db/migrations}"
LOCK_FILE="${COPIMINE_MIGRATION_LOCK_FILE:-/var/lock/copimine-migrations.lock}"
PLAN_FILE=""

fail() {
  printf '[copimine-migrate] ERROR: %s\n' "$*" >&2
  exit 1
}

read_env_value() {
  local key="$1"
  python3 - "$ENV_FILE" "$key" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
key = sys.argv[2]
for raw in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    name, value = line.split("=", 1)
    if name.strip() == key:
        print(value.strip().strip('"').strip("'"))
        break
PY
}

require_identifier() {
  local label="$1"
  local value="$2"
  [[ "$value" =~ ^[A-Za-z_][A-Za-z0-9_]{0,48}$ ]] || fail "$label must be a PostgreSQL identifier"
}

cleanup() {
  if [[ -n "$PLAN_FILE" && -f "$PLAN_FILE" ]]; then
    rm -f -- "$PLAN_FILE"
  fi
  # An EXIT trap must not replace a successful migration with status 1 just
  # because there was no temporary file left to remove.
  return 0
}
trap cleanup EXIT

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail "Run with sudo/root."
command -v psql >/dev/null 2>&1 || fail "Missing command: psql"
command -v flock >/dev/null 2>&1 || fail "Missing command: flock"
[[ -f "$ENV_FILE" ]] || fail "Missing environment file: $ENV_FILE"
[[ -d "$MIGRATIONS_DIR" ]] || fail "Missing migrations directory: $MIGRATIONS_DIR"

pg_host="$(read_env_value POSTGRES_HOST)"
pg_port="$(read_env_value POSTGRES_PORT)"
pg_database="$(read_env_value POSTGRES_DB)"
pg_user="$(read_env_value POSTGRES_USER)"
pg_password="$(read_env_value POSTGRES_PASSWORD)"
pg_host="${pg_host:-127.0.0.1}"
pg_port="${pg_port:-5432}"

require_identifier "POSTGRES_DB" "$pg_database"
require_identifier "POSTGRES_USER" "$pg_user"
[[ -n "$pg_password" && "$pg_password" != "CHANGE_ME" ]] || fail "POSTGRES_PASSWORD is missing in $ENV_FILE"

mapfile -t migrations < <(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -name '[0-9]*.sql' -print | LC_ALL=C sort)
(( ${#migrations[@]} > 0 )) || fail "No numbered SQL migrations found in $MIGRATIONS_DIR"

mkdir -p "$(dirname "$LOCK_FILE")"
chmod 700 "$(dirname "$LOCK_FILE")"

(
  flock -x 9
  PLAN_FILE="$(mktemp /tmp/copimine-migrations-XXXXXX.sql)"
  chmod 600 "$PLAN_FILE"
  {
    printf '%s\n' '\set ON_ERROR_STOP on'
    printf '%s\n' 'BEGIN;'
    printf '%s\n' "SELECT pg_advisory_xact_lock(hashtext('copimine_schema_migrations')) ;"
    printf '%s\n' 'CREATE SCHEMA IF NOT EXISTS copimine;'
    printf '%s\n' 'CREATE TABLE IF NOT EXISTS copimine.copimine_schema_migrations (version TEXT PRIMARY KEY, applied_at BIGINT NOT NULL, checksum TEXT NOT NULL);'
    printf '%s\n' 'SET LOCAL search_path TO copimine, public;'
    for migration in "${migrations[@]}"; do
      version="$(basename "$migration" .sql)"
      checksum="$(sha256sum "$migration" | awk '{print $1}')"
      [[ "$version" =~ ^[0-9][A-Za-z0-9_.-]*$ ]] || fail "Unsafe migration filename: $migration"
      printf "DO \$\$ BEGIN IF EXISTS (SELECT 1 FROM copimine.copimine_schema_migrations WHERE version = '%s' AND checksum <> '%s') THEN RAISE EXCEPTION 'Migration checksum mismatch for %%', '%s'; END IF; END \$\$;\n" "$version" "$checksum" "$version"
      printf "SELECT NOT EXISTS (SELECT 1 FROM copimine.copimine_schema_migrations WHERE version = '%s') AS copimine_apply_migration \\gset\n" "$version"
      printf '%s\n' '\if :copimine_apply_migration'
      printf '%s\n' "\\echo Applying $version"
      printf '%s\n' "\\i $migration"
      printf "INSERT INTO copimine.copimine_schema_migrations(version, applied_at, checksum) VALUES ('%s', EXTRACT(EPOCH FROM clock_timestamp())::BIGINT * 1000, '%s');\n" "$version" "$checksum"
      printf '%s\n' '\endif'
    done
    printf '%s\n' 'COMMIT;'
  } > "$PLAN_FILE"

  PGPASSWORD="$pg_password" psql \
    -h "$pg_host" \
    -p "$pg_port" \
    -U "$pg_user" \
    -d "$pg_database" \
    -v ON_ERROR_STOP=1 \
    -f "$PLAN_FILE"
  rm -f -- "$PLAN_FILE"
  PLAN_FILE=""
) 9>"$LOCK_FILE"

printf '[copimine-migrate] Applied migration plan successfully.\n'
