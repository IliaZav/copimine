#!/usr/bin/env bash
set -Eeuo pipefail

# Safe release entrypoint used by the Windows uploader. It verifies the archive
# and delegates extraction/rollback to the hardened installer already shipped
# with the release. Runtime data and the database are preserved by default.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
LOG_FILE="${COPIMINE_INSTALL_LOG:-/var/log/copimine-install.log}"
mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"
chmod 600 "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1
ARCHIVE_PATH="${1:-}"
ARCHIVE_SHA256=""
DB_DUMP_PATH=""
WIPE_DB=0
WIPE_WORLDS=0
RESET_GAMEPLAY=0

usage() { printf 'Usage: sudo bash %s /path/to/release.tar.gz [sha256] [--wipe-worlds] [--db-dump path]\n' "$0" >&2; }
[[ -n "$ARCHIVE_PATH" ]] || { usage; exit 2; }
[[ "${EUID:-$(id -u)}" -eq 0 ]] || { echo 'Run this installer with sudo/root.' >&2; exit 2; }
shift
if [[ "${1:-}" != --* && -n "${1:-}" ]]; then ARCHIVE_SHA256="$1"; shift; fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    --wipe-worlds) WIPE_WORLDS=1; RESET_GAMEPLAY=1; shift ;;
    --reset-gameplay) RESET_GAMEPLAY=1; shift ;;
    --db-dump) [[ -n "${2:-}" ]] || { usage; exit 2; }; DB_DUMP_PATH="$2"; shift 2 ;;
    --wipe-db) echo 'Use repair_postgres_credentials.sh --recreate-db for an explicit database wipe.' >&2; exit 3 ;;
    *) usage; exit 2 ;;
  esac
done

preflight() {
  local actual expected dump_listing
  for command in sha256sum tar gzip python3 systemctl curl psql pg_restore pg_isready runuser; do
    command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 3; }
  done
  [[ -f "$ARCHIVE_PATH" && -s "$ARCHIVE_PATH" ]] || { echo "Archive not found or empty: $ARCHIVE_PATH" >&2; exit 3; }
  if [[ -z "$ARCHIVE_SHA256" && -f "${ARCHIVE_PATH}.sha256" ]]; then
    ARCHIVE_SHA256="$(awk '{print $1}' "${ARCHIVE_PATH}.sha256" | head -n1 | tr -d '\r\n')"
  fi
  [[ "$ARCHIVE_SHA256" =~ ^[0-9A-Fa-f]{64}$ ]] || { echo 'Archive SHA256 is invalid or missing.' >&2; exit 3; }
  actual="$(sha256sum "$ARCHIVE_PATH" | awk '{print tolower($1)}')"
  expected="${ARCHIVE_SHA256,,}"
  [[ "$actual" == "$expected" ]] || { echo "Archive SHA256 mismatch: expected=$expected actual=$actual" >&2; exit 3; }
  echo "[preflight] archive sha256 OK: $actual"
  tar -tzf "$ARCHIVE_PATH" >/dev/null
  if [[ -n "$DB_DUMP_PATH" ]]; then
    [[ -s "$DB_DUMP_PATH" ]] || { echo "Database dump not found: $DB_DUMP_PATH" >&2; exit 3; }
    dump_listing="$(mktemp /tmp/copimine-dump-list-XXXXXX)"
    pg_restore --list "$DB_DUMP_PATH" >"$dump_listing"
    rm -f -- "$dump_listing"
    echo '[preflight] external database dump is readable'
  fi
  if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
    local db_host db_port db_name db_user db_password
    # Strip only CRLF and double quotes. Do not use \x27 here: some tr
    # implementations interpret it as the characters 2 and 7, corrupting
    # 127.0.0.1 into 1.0.0.1 and 5432 into 543.
    db_host="$(awk -F= '$1=="POSTGRES_HOST" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_port="$(awk -F= '$1=="POSTGRES_PORT" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_name="$(awk -F= '$1=="POSTGRES_DB" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_user="$(awk -F= '$1=="POSTGRES_USER" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_password="$(awk -F= '$1=="POSTGRES_PASSWORD" {v=substr($0,index($0,"=")+1)} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    if [[ -n "$db_user" && -n "$db_name" && -n "$db_password" ]]; then
      local configured_host="${db_host:-127.0.0.1}" configured_port="${db_port:-5432}"
      if PGPASSWORD="$db_password" psql -h "$configured_host" -p "$configured_port" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -Atc 'SELECT 1' >/dev/null 2>&1; then
        echo "[preflight] PostgreSQL credentials OK ($configured_host:$configured_port)"
      elif [[ "$configured_host" != "127.0.0.1" || "$configured_port" != "5432" ]] \
        && runuser -u postgres -- pg_isready -q >/dev/null 2>&1 \
        && PGPASSWORD="$db_password" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -Atc 'SELECT 1' >/dev/null 2>&1; then
        python3 - "$PROJECT_ROOT/admin-web/.env" <<'PY'
import re, sys
from pathlib import Path
p = Path(sys.argv[1])
lines = p.read_text(encoding='utf-8', errors='replace').splitlines()
updates = {'POSTGRES_HOST': '127.0.0.1', 'POSTGRES_PORT': '5432'}
out = []
seen = set()
for line in lines:
    m = re.match(r'^([A-Za-z_][A-Za-z0-9_]*)=', line)
    if m and m.group(1) in updates:
        out.append(f"{m.group(1)}={updates[m.group(1)]}")
        seen.add(m.group(1))
    elif m and m.group(1) == 'DATABASE_URL':
        value = line.split('=', 1)[1]
        value = re.sub(r'@[^/:]+:\d+/', '@127.0.0.1:5432/', value, count=1)
        out.append(f'DATABASE_URL={value}')
        seen.add('DATABASE_URL')
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')
tmp = p.with_name('.env.install-tmp')
tmp.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
tmp.chmod(0o600)
tmp.replace(p)
PY
        echo "[preflight] PostgreSQL connection repaired: $configured_host:$configured_port -> 127.0.0.1:5432"
      else
        echo "[preflight] PostgreSQL connection failed for $configured_host:$configured_port" >&2
        echo '[preflight] Check POSTGRES_HOST/POSTGRES_PORT in /opt/copimine/admin-web/.env.' >&2
        return 1
      fi
    else
      echo '[preflight] PostgreSQL credentials are not configured yet; installer will bootstrap them.'
    fi
  else
    echo '[preflight] .env is absent; installer will create protected runtime credentials.'
  fi
}

verify_runtime() {
  local expected_sha actual_sha expected_modpack actual_modpack properties_sha
  for service in copimine-admin copimine-minecraft nginx; do
    systemctl is-active --quiet "$service" || { echo "Service is not active: $service" >&2; return 1; }
    echo "[verify] service active: $service"
  done
  expected_sha="$(sha1sum "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
  actual_sha="$(curl -fsS --max-time 30 http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip | sha1sum | awk '{print $1}')"
  [[ "$expected_sha" == "$actual_sha" ]] || { echo "Resource pack SHA1 mismatch: local=$expected_sha served=$actual_sha" >&2; return 1; }
  echo "[verify] resource pack SHA1 OK: $actual_sha"
  expected_modpack="$(sha256sum "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" | awk '{print $1}')"
  actual_modpack="$(curl -fsS --max-time 30 http://127.0.0.1:18080/downloads/CopiMineMods.zip | sha256sum | awk '{print $1}')"
  [[ "$expected_modpack" == "$actual_modpack" ]] || { echo "Modpack SHA256 mismatch: local=$expected_modpack served=$actual_modpack" >&2; return 1; }
  echo "[verify] modpack SHA256 OK: $actual_modpack"
  grep -q '^require-resource-pack=true$' "$PROJECT_ROOT/minecraft/server/server.properties" || { echo 'require-resource-pack=true is missing' >&2; return 1; }
  properties_sha="$(sed -n 's/^resource-pack-sha1=//p' "$PROJECT_ROOT/minecraft/server/server.properties" | tr -d '\r\n')"
  [[ "$properties_sha" == "$expected_sha" ]] || { echo "server.properties resource-pack-sha1 mismatch: $properties_sha" >&2; return 1; }
  echo '[verify] server.properties resource-pack requirement and SHA1 OK'
  curl -fsS --max-time 15 http://127.0.0.1:18080/api/runtime >/dev/null
  echo '[verify] HTTP runtime endpoint OK'
}

enable_offline_voicechat() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  [[ -f "$env_file" ]] || return 0
  # The server is intentionally running offline-mode. The owner explicitly
  # accepted public voice chat, so persist the required exception before the
  # hardening step runs.
  python3 - "$env_file" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
updates = {
    'COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT': '1',
    'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON':
        'Offline mode is enabled; public voice chat was explicitly accepted by the server owner.',
}
lines = path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
out, seen = [], set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in updates:
        out.append(f'{key}={updates[key]}')
        seen.add(key)
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')
tmp = path.with_name('.env.voicechat-tmp')
tmp.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
tmp.chmod(0o600)
tmp.replace(path)
PY
  chmod 600 "$env_file"
  echo '[preflight] Offline voice-chat exception enabled by explicit deployment request.'
}

runtime_app_user() {
  local candidate path
  # The data directory is the authoritative owner for an existing install.
  # An old systemd unit may still mention a retired account and must not win
  # during a replacement.
  for path in "$PROJECT_ROOT/admin-web/data" "$PROJECT_ROOT/admin-web/.env"; do
    if [[ -e "$path" ]]; then
      candidate="$(stat -c '%U' "$path" 2>/dev/null || true)"
      if [[ -n "$candidate" && "$candidate" != "root" ]] && id "$candidate" >/dev/null 2>&1; then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done
  candidate="$(systemctl show copimine-admin.service -p User --value 2>/dev/null || true)"
  if [[ -n "$candidate" && "$candidate" != "root" ]] && id "$candidate" >/dev/null 2>&1; then
    printf '%s\n' "$candidate"
    return 0
  fi
  candidate="${SUDO_USER:-qwerty}"
  id "$candidate" >/dev/null 2>&1 && printf '%s\n' "$candidate" || printf 'qwerty\n'
}

normalize_runtime_env_owner() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  [[ -f "$env_file" ]] || return 0
  local app_user app_group
  app_user="$(runtime_app_user)"
  id "$app_user" >/dev/null 2>&1 || { echo "[preflight] Runtime user does not exist: $app_user" >&2; return 1; }
  app_group="$(id -gn "$app_user")"
  chown "$app_user:$app_group" "$env_file"
  chmod 600 "$env_file"
  echo "[preflight] Runtime env owner: $app_user:$app_group"
}

preflight
enable_offline_voicechat
normalize_runtime_env_owner

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

set +e
RUNTIME_APP_USER="$(runtime_app_user)"
APP_USER="$RUNTIME_APP_USER" COPIMINE_APP_USER="$RUNTIME_APP_USER" \
WIPE_WORLDS="$WIPE_WORLDS" CLEAN_WORLD_STATE="$RESET_GAMEPLAY" \
  "$PROJECT_ROOT/deploy/ubuntu/copimine_unpack_and_verify.sh" "$ARCHIVE_PATH" "$ARCHIVE_SHA256" "$DB_DUMP_PATH"
result=$?
set -e
if [[ "$result" -ne 0 ]]; then
  echo "INSTALL FAILED with exit code $result" >&2
  systemctl --no-pager --plain --full status copimine-admin copimine-minecraft nginx || true
  exit "$result"
fi
verify_runtime
echo "INSTALL COMPLETE. Log: $LOG_FILE"
