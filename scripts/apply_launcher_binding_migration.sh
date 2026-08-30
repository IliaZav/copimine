#!/usr/bin/env bash
set -euo pipefail

# This runner is deliberately narrower than backend.main's legacy v4
# self-heal. It creates only the two Launcher binding tables and their indexes.
# The caller must make and verify a durable database backup first.

die() {
  printf 'LAUNCHER_BINDING_MIGRATION=FAIL %s\n' "$1" >&2
  exit 1
}

[ "${COPIMINE_ALLOW_LAUNCHER_BINDING_MIGRATION:-}" = "1" ] \
  || die 'set COPIMINE_ALLOW_LAUNCHER_BINDING_MIGRATION=1 explicitly'
[ -n "${DATABASE_URL:-}" ] || die 'DATABASE_URL is required'
[ -n "${COPIMINE_LAUNCHER_BINDING_BACKUP_FILE:-}" ] \
  || die 'COPIMINE_LAUNCHER_BINDING_BACKUP_FILE is required'
[ -s "$COPIMINE_LAUNCHER_BINDING_BACKUP_FILE" ] \
  || die 'verified backup file is missing or empty'
[ -s "${COPIMINE_LAUNCHER_BINDING_BACKUP_FILE}.sha256" ] \
  || die 'backup checksum file is missing'
[ -f "${COPIMINE_LAUNCHER_BINDING_MIGRATION_FILE:-}" ] \
  || die 'COPIMINE_LAUNCHER_BINDING_MIGRATION_FILE must point to the checked-in SQL file'

sha256sum --check "$COPIMINE_LAUNCHER_BINDING_BACKUP_FILE.sha256" >/dev/null \
  || die 'database backup checksum does not match'

psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 \
  -f "$COPIMINE_LAUNCHER_BINDING_MIGRATION_FILE" >/dev/null

verification="$(psql "$DATABASE_URL" -X -At -v ON_ERROR_STOP=1 -c \
  "SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('launcher_link_challenges','launcher_account_links')) || '|' || (SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename IN ('launcher_link_challenges','launcher_account_links'));" \
  | tr -d '\r\n')"
[ "$verification" = '2|5' ] \
  || die "binding schema verification returned $verification, expected 2|5"

printf 'LAUNCHER_BINDING_MIGRATION=PASS tables=2 indexes=5\n'
