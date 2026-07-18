#!/usr/bin/env bash
set -Eeuo pipefail

# Safe release entrypoint used by the Windows uploader. It verifies the archive
# and delegates extraction/rollback to the hardened installer already shipped
# with the release. Runtime data and the database are preserved by default.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
ARCHIVE_PATH="${1:-}"
ARCHIVE_SHA256=""
DB_DUMP_PATH=""
WIPE_DB=0
WIPE_WORLDS=0

usage() { printf 'Usage: sudo bash %s /path/to/release.tar.gz [sha256] [--wipe-worlds] [--db-dump path]\n' "$0" >&2; }
[[ -n "$ARCHIVE_PATH" ]] || { usage; exit 2; }
shift
if [[ "${1:-}" != --* && -n "${1:-}" ]]; then ARCHIVE_SHA256="$1"; shift; fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    --wipe-worlds) WIPE_WORLDS=1; shift ;;
    --db-dump) [[ -n "${2:-}" ]] || { usage; exit 2; }; DB_DUMP_PATH="$2"; shift 2 ;;
    --wipe-db) echo 'Use repair_postgres_credentials.sh --recreate-db for an explicit database wipe.' >&2; exit 3 ;;
    *) usage; exit 2 ;;
  esac
done

if [[ "$WIPE_DB" == "1" ]]; then
  [[ "${COPIMINE_ALLOW_DB_WIPE:-}" == "YES" ]] || { echo 'Refusing database wipe. Set COPIMINE_ALLOW_DB_WIPE=YES and pass --wipe-db.' >&2; exit 3; }
  [[ "${COPIMINE_CONFIRM_DB_NAME:-}" == "copimine" ]] || { echo 'Refusing database wipe. Set COPIMINE_CONFIRM_DB_NAME=copimine.' >&2; exit 3; }
  [[ -f "$PROJECT_ROOT/admin-web/.env" ]] || { echo "Missing $PROJECT_ROOT/admin-web/.env" >&2; exit 3; }
  # Backup first, then remove only the configured CopiMine schemas.
  source "$PROJECT_ROOT/deploy/shared/common.sh"
  mkdir -p "${BACKUP_ROOT:-/opt/copimine-backups}"
  pg_dump -h "$(copimine_env_value POSTGRES_HOST || echo 127.0.0.1)" -p "$(copimine_env_value POSTGRES_PORT || echo 5432)" -U "$(copimine_env_value POSTGRES_USER)" -d copimine -Fc -f "${BACKUP_ROOT:-/opt/copimine-backups}/pre-wipe-$(date +%Y%m%d-%H%M%S).dump"
  PGPASSWORD="$(copimine_env_value POSTGRES_PASSWORD)" psql -h "$(copimine_env_value POSTGRES_HOST || echo 127.0.0.1)" -p "$(copimine_env_value POSTGRES_PORT || echo 5432)" -U "$(copimine_env_value POSTGRES_USER)" -d copimine -v ON_ERROR_STOP=1 -c 'DROP SCHEMA IF EXISTS copimine CASCADE; CREATE SCHEMA copimine;'
fi

WIPE_WORLDS="$WIPE_WORLDS" exec "$PROJECT_ROOT/deploy/ubuntu/copimine_unpack_and_verify.sh" "$ARCHIVE_PATH" "$ARCHIVE_SHA256" "$DB_DUMP_PATH"
