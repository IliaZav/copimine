#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_PATH="${1:-}"
DB_DUMP_PATH="${2:-}"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/copimine-backups}"
APP_USER="${APP_USER:-qwerty}"
APP_GROUP="${APP_GROUP:-$APP_USER}"
PG_DB="${PG_DB:-copimine}"
PG_OWNER="${PG_OWNER:-copimine}"
PRESERVE_DB_IF_NO_DUMP="${PRESERVE_DB_IF_NO_DUMP:-1}"
TS="$(date +%Y%m%d-%H%M%S)"

SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
)

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

stop_service_if_present() {
  local service="$1"
  if systemctl list-unit-files | grep -q "^${service}\.service"; then
    systemctl stop "$service" 2>/dev/null || true
  fi
}

start_service_if_present() {
  local service="$1"
  if systemctl list-unit-files | grep -q "^${service}\.service"; then
    systemctl restart "$service" 2>/dev/null || true
  fi
}

copy_preserved_path() {
  local source="$1"
  local targetRoot="$2"
  if [[ -e "$source" ]]; then
    mkdir -p "$targetRoot/$(dirname "$source")"
    cp -a "$source" "$targetRoot/$source"
  fi
}

restore_preserved_path() {
  local relative="$1"
  local sourceRoot="$2"
  local targetRoot="$3"
  if [[ -e "$sourceRoot/$relative" ]]; then
    mkdir -p "$(dirname "$targetRoot/$relative")"
    rm -rf "$targetRoot/$relative"
    cp -a "$sourceRoot/$relative" "$targetRoot/$relative"
  fi
}

extract_archive() {
  local archive="$1"
  local destination="$2"
  case "$archive" in
    *.tar.gz|*.tgz)
      tar -xzf "$archive" -C "$destination"
      ;;
    *.zip)
      need unzip
      unzip -q "$archive" -d "$destination"
      ;;
    *)
      echo "Unsupported archive format: $archive"
      exit 1
      ;;
  esac
}

detect_payload_root() {
  local extracted="$1"
  if [[ -d "$extracted/copimine" ]]; then
    echo "$extracted/copimine"
    return
  fi
  if [[ -d "$extracted/opt/copimine" ]]; then
    echo "$extracted/opt/copimine"
    return
  fi
  if [[ -d "$extracted/admin-web" && -d "$extracted/minecraft" ]]; then
    echo "$extracted"
    return
  fi
  echo ""
}

restore_database_dump() {
  local dumpPath="$1"
  need psql
  if ! command -v pg_restore >/dev/null 2>&1; then
    echo "Missing pg_restore for database restore"
    exit 1
  fi
  sudo -u postgres dropdb --if-exists "$PG_DB"
  sudo -u postgres createdb -O "$PG_OWNER" "$PG_DB"
  case "$dumpPath" in
    *.sql)
      sudo -u postgres psql -v ON_ERROR_STOP=1 -d "$PG_DB" -f "$dumpPath"
      ;;
    *)
      sudo -u postgres pg_restore -v -d "$PG_DB" "$dumpPath"
      ;;
  esac
}

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run this script with sudo."
  exit 1
fi

if [[ -z "$ARCHIVE_PATH" ]]; then
  echo "Usage: sudo bash copimine_full_replace.sh /path/to/CopiMine-opt-full.tar.gz [/path/to/copimine-db.dump]"
  exit 1
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Archive not found: $ARCHIVE_PATH"
  exit 1
fi

need tar

TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT
EXTRACT_ROOT="$TMP_ROOT/extracted"
PRESERVE_ROOT="$TMP_ROOT/preserve"
mkdir -p "$EXTRACT_ROOT" "$PRESERVE_ROOT"

echo "[1/12] Stop services"
for service in "${SERVICES[@]}"; do
  stop_service_if_present "$service"
done

echo "[2/12] Backup current project"
mkdir -p "$BACKUP_ROOT"
if [[ -d "$PROJECT_ROOT" ]]; then
  cp -a "$PROJECT_ROOT" "$BACKUP_ROOT/copimine-opt-$TS"
fi

echo "[3/12] Preserve runtime state"
if [[ -d "$PROJECT_ROOT" ]]; then
  for relative in \
    "admin-web/.env" \
    "minecraft/server/whitelist.json" \
    "minecraft/server/ops.json" \
    "minecraft/server/banned-players.json" \
    "minecraft/server/banned-ips.json" \
    "minecraft/server/usercache.json" \
    "minecraft/server/plugins/LuckPerms" \
    "minecraft/server/plugins/nLogin"
  do
    copy_preserved_path "$PROJECT_ROOT/$relative" "$PRESERVE_ROOT"
  done
  if [[ -d "$PROJECT_ROOT/minecraft/server" ]]; then
    while IFS= read -r -d '' worldDir; do
      copy_preserved_path "$worldDir" "$PRESERVE_ROOT"
    done < <(find "$PROJECT_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world*' -o -name 'paper-world*' \) -print0)
  fi
fi

echo "[4/12] Extract archive"
extract_archive "$ARCHIVE_PATH" "$EXTRACT_ROOT"

echo "[5/12] Detect payload root"
PAYLOAD_ROOT="$(detect_payload_root "$EXTRACT_ROOT")"
if [[ -z "$PAYLOAD_ROOT" || ! -d "$PAYLOAD_ROOT" ]]; then
  echo "Failed to detect payload root inside archive."
  exit 1
fi

echo "[6/12] Replace project tree"
rm -rf "${PROJECT_ROOT}.new"
mkdir -p "${PROJECT_ROOT}.new"
cp -a "$PAYLOAD_ROOT/." "${PROJECT_ROOT}.new/"
rm -rf "${PROJECT_ROOT}.old"
if [[ -d "$PROJECT_ROOT" ]]; then
  mv "$PROJECT_ROOT" "${PROJECT_ROOT}.old"
fi
mv "${PROJECT_ROOT}.new" "$PROJECT_ROOT"

echo "[7/12] Restore preserved runtime state"
for relative in \
  "admin-web/.env" \
  "minecraft/server/whitelist.json" \
  "minecraft/server/ops.json" \
  "minecraft/server/banned-players.json" \
  "minecraft/server/banned-ips.json" \
  "minecraft/server/usercache.json" \
  "minecraft/server/plugins/LuckPerms" \
  "minecraft/server/plugins/nLogin"
do
  restore_preserved_path "$PROJECT_ROOT/$relative" "$PRESERVE_ROOT" ""
done
if [[ -d "$PRESERVE_ROOT/$PROJECT_ROOT/minecraft/server" ]]; then
  while IFS= read -r -d '' preservedWorld; do
    relative="${preservedWorld#"$PRESERVE_ROOT/"}"
    restore_preserved_path "$relative" "$PRESERVE_ROOT" ""
  done < <(find "$PRESERVE_ROOT/$PROJECT_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world*' -o -name 'paper-world*' \) -print0)
fi

echo "[8/12] Restore database if dump was supplied"
BUNDLED_DUMP="$PROJECT_ROOT/db/runtime/copimine.dump"
if [[ -n "$DB_DUMP_PATH" ]]; then
  if [[ ! -f "$DB_DUMP_PATH" ]]; then
    echo "Database dump not found: $DB_DUMP_PATH"
    exit 1
  fi
  restore_database_dump "$DB_DUMP_PATH"
elif [[ -f "$BUNDLED_DUMP" ]]; then
  restore_database_dump "$BUNDLED_DUMP"
elif [[ "$PRESERVE_DB_IF_NO_DUMP" == "1" ]]; then
  echo "No database dump supplied. Keeping existing PostgreSQL database unchanged."
else
  echo "No database dump supplied and PRESERVE_DB_IF_NO_DUMP=0."
  exit 1
fi

echo "[9/12] Fix ownership and executable bits"
chown -R "$APP_USER:$APP_GROUP" "$PROJECT_ROOT" || true
find "$PROJECT_ROOT" -type d -exec chmod 755 {} \;
find "$PROJECT_ROOT" -type f -exec chmod 644 {} \;
find "$PROJECT_ROOT" -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod 755 {} \; || true

echo "[10/12] Refresh system services and nginx config if present"
if [[ -f "$PROJECT_ROOT/admin-web/deploy/copimine-admin.service" ]]; then
  install -m 0644 "$PROJECT_ROOT/admin-web/deploy/copimine-admin.service" /etc/systemd/system/copimine-admin.service
fi
if [[ -f "$PROJECT_ROOT/admin-web/deploy/copimine-discord-bot.service" ]]; then
  install -m 0644 "$PROJECT_ROOT/admin-web/deploy/copimine-discord-bot.service" /etc/systemd/system/copimine-discord-bot.service
fi
if [[ -f "$PROJECT_ROOT/admin-web/deploy/copimine-minecraft-discord-bridge.service" ]]; then
  install -m 0644 "$PROJECT_ROOT/admin-web/deploy/copimine-minecraft-discord-bridge.service" /etc/systemd/system/copimine-minecraft-discord-bridge.service
fi
if [[ -f "$PROJECT_ROOT/admin-web/deploy/copimine-minecraft.service" ]]; then
  install -m 0644 "$PROJECT_ROOT/admin-web/deploy/copimine-minecraft.service" /etc/systemd/system/copimine-minecraft.service
fi
if [[ -f "$PROJECT_ROOT/admin-web/deploy/nginx-copimine-admin-18080.conf" ]]; then
  install -m 0644 "$PROJECT_ROOT/admin-web/deploy/nginx-copimine-admin-18080.conf" /etc/nginx/sites-available/copimine-admin.conf
  ln -sfn /etc/nginx/sites-available/copimine-admin.conf /etc/nginx/sites-enabled/copimine-admin.conf
fi
systemctl daemon-reload

echo "[11/12] Restart services"
for service in "${SERVICES[@]}"; do
  start_service_if_present "$service"
done
if systemctl list-unit-files | grep -q '^nginx\.service'; then
  systemctl reload nginx 2>/dev/null || systemctl restart nginx 2>/dev/null || true
fi

echo "[12/12] Done"
echo "Project root: $PROJECT_ROOT"
echo "Backup root: $BACKUP_ROOT/copimine-opt-$TS"
echo "Previous project copy: ${PROJECT_ROOT}.old"
if [[ -f "$PROJECT_ROOT/deploy/release_manifest.json" ]]; then
  echo "Release manifest:"
  cat "$PROJECT_ROOT/deploy/release_manifest.json"
fi
