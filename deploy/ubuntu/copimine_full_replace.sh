#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_PATH="${1:-}"
DB_DUMP_PATH="${2:-}"
SERVICE_USER="${SERVICE_USER:-qwerty}"
PROJECT_ROOT="/opt/copimine"
BACKUP_ROOT="/opt/copimine-backups"
TS="$(date +%Y%m%d-%H%M%S)"

if [[ -z "$ARCHIVE_PATH" ]]; then
  echo "Usage: sudo bash copimine_full_replace.sh /path/to/CopiMine-opt-full.zip [/path/to/copimine-db.dump]"
  exit 1
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Archive not found: $ARCHIVE_PATH"
  exit 1
fi

echo "[1/9] Stop services"
systemctl stop copimine-admin || true
systemctl stop copimine-minecraft || true
systemctl stop copimine-discord || true

echo "[2/9] Backup current project"
mkdir -p "$BACKUP_ROOT"
if [[ -d "$PROJECT_ROOT" ]]; then
  cp -a "$PROJECT_ROOT" "$BACKUP_ROOT/copimine-opt-$TS"
fi

echo "[3/9] Prepare temp extraction"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT
mkdir -p "$TMP_ROOT/extracted"

echo "[4/9] Extract archive"
unzip -q "$ARCHIVE_PATH" -d "$TMP_ROOT/extracted"

echo "[5/9] Replace /opt/copimine"
rm -rf "${PROJECT_ROOT}.new"
mkdir -p "${PROJECT_ROOT}.new"
cp -a "$TMP_ROOT/extracted/." "${PROJECT_ROOT}.new/"
rm -rf "${PROJECT_ROOT}.old"
if [[ -d "$PROJECT_ROOT" ]]; then
  mv "$PROJECT_ROOT" "${PROJECT_ROOT}.old"
fi
mv "${PROJECT_ROOT}.new" "$PROJECT_ROOT"

echo "[6/9] Optional database restore"
if [[ -n "$DB_DUMP_PATH" ]]; then
  if [[ ! -f "$DB_DUMP_PATH" ]]; then
    echo "Database dump not found: $DB_DUMP_PATH"
    exit 1
  fi
  sudo -u postgres dropdb --if-exists copimine
  sudo -u postgres createdb -O copimine copimine
  sudo -u postgres pg_restore -d copimine "$DB_DUMP_PATH"
fi

echo "[7/9] Fix ownership"
chown -R "$SERVICE_USER:$SERVICE_USER" "$PROJECT_ROOT" || true
find "$PROJECT_ROOT" -type d -exec chmod 755 {} \;
find "$PROJECT_ROOT" -type f -exec chmod 644 {} \;

echo "[8/9] Restart services"
systemctl daemon-reload
systemctl restart copimine-admin || true
systemctl restart copimine-minecraft || true
systemctl restart copimine-discord || true

echo "[9/9] Done"
echo "Project root: $PROJECT_ROOT"
echo "Backup root: $BACKUP_ROOT"
echo "Previous project copy: ${PROJECT_ROOT}.old"
