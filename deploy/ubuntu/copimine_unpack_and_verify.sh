#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
TS="$(date +%Y%m%d-%H%M%S)"

PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
SECRETS_ROOT="${SECRETS_ROOT:-/opt/copimine-secrets}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/copimine-backups}"
LOG_FILE="${LOG_FILE:-/var/log/copimine-unpack.log}"
TMP_ROOT=""
EXTRACT_ROOT=""
PAYLOAD_ROOT=""
PRESERVE_ROOT=""
PREVIOUS_RELEASE=""
ROLLBACK_ARMED=0

ARCHIVE_PATH="${1:-}"
ARCHIVE_SHA256="${2:-}"
DB_DUMP_PATH="${3:-}"
WIPE_WORLDS="${WIPE_WORLDS:-0}"
WORLD_SEED="${WORLD_SEED:--1861153001556076901}"

APP_USER="${APP_USER:-$(stat -c '%U' "$PROJECT_ROOT" 2>/dev/null || echo qwerty)}"
APP_GROUP="${APP_GROUP:-$(stat -c '%G' "$PROJECT_ROOT" 2>/dev/null || echo "$APP_USER")}"

NGINX_TEMPLATE_REL="admin-web/deploy/nginx-copimine-admin-18080.conf"
NGINX_AVAILABLE="${NGINX_AVAILABLE:-/etc/nginx/sites-available/copimine-admin.conf}"
NGINX_ENABLED="${NGINX_ENABLED:-/etc/nginx/sites-enabled/copimine-admin.conf}"

SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
)

REQUIRED_RUNTIME_PATHS=(
  "admin-web"
  "minecraft"
  "resourcepacks"
  "thirdparty"
  "deploy"
)

PRESERVE_PATHS=(
  "admin-web/.env"
  "admin-web/data"
  "admin-web/backups"
  "minecraft/server/eula.txt"
  "minecraft/server/server.properties"
  "minecraft/server/whitelist.json"
  "minecraft/server/ops.json"
  "minecraft/server/banned-players.json"
  "minecraft/server/banned-ips.json"
  "minecraft/server/usercache.json"
  "minecraft/server/plugins/LuckPerms"
  "minecraft/server/plugins/AuthMe"
  "minecraft/server/plugins/FastLogin"
  "minecraft/server/plugins/nLogin"
  "minecraft/server/logs"
  "thirdparty/CopiMineMods.sha1"
  "thirdparty/CopiMineMods.sha256"
  "thirdparty/modpack_manifest.json"
)

SYSTEMD_UNITS=(
  "copimine-admin.service"
  "copimine-discord-bot.service"
  "copimine-minecraft-discord-bridge.service"
  "copimine-minecraft.service"
)

log() {
  local line="[copimine-unpack][$(date '+%Y-%m-%d %H:%M:%S')] $*"
  printf '%s\n' "$line"
  mkdir -p "$(dirname "$LOG_FILE")"
  printf '%s\n' "$line" >> "$LOG_FILE"
}

die() {
  log "ERROR: $*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

require_root() {
  [[ "${EUID:-$(id -u)}" -eq 0 ]] || die "Run with sudo/root."
}

require_file() {
  [[ -f "$1" ]] || die "File not found: $1"
}

require_dir() {
  [[ -d "$1" ]] || die "Directory not found: $1"
}

cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && $ROLLBACK_ARMED -eq 1 ]]; then
    log "Failure detected. Starting rollback."
    rollback_release || log "Rollback failed. Manual restore may be required."
  fi
  [[ -n "$TMP_ROOT" && -d "$TMP_ROOT" ]] && rm -rf "$TMP_ROOT"
  exit $exit_code
}

trap cleanup EXIT

rollback_release() {
  if [[ -d "$PROJECT_ROOT" ]]; then
    rm -rf "${PROJECT_ROOT}.failed-$TS"
    mv "$PROJECT_ROOT" "${PROJECT_ROOT}.failed-$TS"
  fi
  if [[ -n "$PREVIOUS_RELEASE" && -d "$PREVIOUS_RELEASE" ]]; then
    mv "$PREVIOUS_RELEASE" "$PROJECT_ROOT"
    restore_preserved_state "$PROJECT_ROOT"
    install_system_files
    systemctl daemon-reload || true
    restart_services || true
    log "Rollback restored $PROJECT_ROOT"
  fi
}

preflight() {
  log "[1/16] System preflight"
  require_root
  need bash
  need awk
  need sed
  need grep
  need find
  need tar
  need gzip
  need sha256sum
  need sha1sum
  need python3
  need curl
  need systemctl
  need stat
  need install
  need runuser
  need psql
  need pg_restore
  need nginx
  need jar
  if command -v unzip >/dev/null 2>&1; then
    :
  fi
  [[ -r /etc/os-release ]] || die "Missing /etc/os-release"
  mkdir -p "$BACKUP_ROOT" "$SECRETS_ROOT"
  chmod 700 "$SECRETS_ROOT"
  touch "$LOG_FILE"
  chmod 600 "$LOG_FILE"
  log "OS: $(grep '^PRETTY_NAME=' /etc/os-release | cut -d= -f2- | tr -d '\"')"
  log "Kernel: $(uname -r)"
  log "Project root: $PROJECT_ROOT"
  log "App user/group: $APP_USER:$APP_GROUP"
}

validate_inputs() {
  log "[2/16] Validate inputs"
  [[ -n "$ARCHIVE_PATH" ]] || die "Usage: sudo bash $SCRIPT_NAME /path/to/copimine-runtime.tar.gz [sha256] [db-dump]"
  require_file "$ARCHIVE_PATH"
  [[ -s "$ARCHIVE_PATH" ]] || die "Archive is empty: $ARCHIVE_PATH"
  case "$ARCHIVE_PATH" in
    *.tar.gz|*.tgz)
      tar -tzf "$ARCHIVE_PATH" >/dev/null || die "Archive is not readable: $ARCHIVE_PATH"
      ;;
    *.zip)
      need unzip
      unzip -tq "$ARCHIVE_PATH" >/dev/null || die "Zip archive is not readable: $ARCHIVE_PATH"
      ;;
    *)
      die "Unsupported archive format: $ARCHIVE_PATH"
      ;;
  esac
  if [[ -n "$ARCHIVE_SHA256" ]]; then
    local actual
    actual="$(sha256sum "$ARCHIVE_PATH" | awk '{print tolower($1)}')"
    [[ "${ARCHIVE_SHA256,,}" == "$actual" ]] || die "Archive SHA256 mismatch. Expected ${ARCHIVE_SHA256,,}, got $actual"
    log "Archive SHA256 verified: $actual"
  else
    log "WARNING: archive SHA256 was not provided."
  fi
  if [[ -n "$DB_DUMP_PATH" ]]; then
    require_file "$DB_DUMP_PATH"
    [[ -s "$DB_DUMP_PATH" ]] || die "Database dump is empty: $DB_DUMP_PATH"
  fi
}

prepare_temp() {
  log "[3/16] Prepare temporary workspace"
  TMP_ROOT="$(mktemp -d /tmp/copimine-unpack-XXXXXX)"
  EXTRACT_ROOT="$TMP_ROOT/extracted"
  PRESERVE_ROOT="$TMP_ROOT/preserved"
  mkdir -p "$EXTRACT_ROOT" "$PRESERVE_ROOT"
}

extract_archive() {
  log "[4/16] Extract archive"
  case "$ARCHIVE_PATH" in
    *.tar.gz|*.tgz)
      tar -xzf "$ARCHIVE_PATH" -C "$EXTRACT_ROOT"
      ;;
    *.zip)
      unzip -q "$ARCHIVE_PATH" -d "$EXTRACT_ROOT"
      ;;
  esac
}

detect_payload() {
  log "[5/16] Detect payload root"
  if [[ -d "$EXTRACT_ROOT/copimine" ]]; then
    PAYLOAD_ROOT="$EXTRACT_ROOT/copimine"
  elif [[ -d "$EXTRACT_ROOT/opt/copimine" ]]; then
    PAYLOAD_ROOT="$EXTRACT_ROOT/opt/copimine"
  elif [[ -d "$EXTRACT_ROOT/admin-web" && -d "$EXTRACT_ROOT/minecraft" ]]; then
    PAYLOAD_ROOT="$EXTRACT_ROOT"
  else
    local nested
    nested="$(find "$EXTRACT_ROOT" -mindepth 1 -maxdepth 2 -type d -name copimine | head -n 1 || true)"
    [[ -n "$nested" ]] && PAYLOAD_ROOT="$nested"
  fi
  [[ -n "$PAYLOAD_ROOT" && -d "$PAYLOAD_ROOT" ]] || die "Could not detect archive payload root."
  for relative in "${REQUIRED_RUNTIME_PATHS[@]}"; do
    [[ -e "$PAYLOAD_ROOT/$relative" ]] || die "Runtime archive is incomplete. Missing: $relative"
  done
}

backup_current_release() {
  log "[6/16] Backup current release"
  local backup_dir="$BACKUP_ROOT/runtime-replace-$TS"
  mkdir -p "$backup_dir"
  if [[ -d "$PROJECT_ROOT" ]]; then
    cp -a "$PROJECT_ROOT" "$backup_dir/copimine-pre-replace"
  fi
  for relative in "${PRESERVE_PATHS[@]}"; do
    if [[ -e "$PROJECT_ROOT/$relative" ]]; then
      mkdir -p "$PRESERVE_ROOT/$(dirname "$relative")"
      cp -a "$PROJECT_ROOT/$relative" "$PRESERVE_ROOT/$relative"
    fi
  done
  if [[ -d "$PROJECT_ROOT/minecraft/server" ]]; then
    while IFS= read -r -d '' world_dir; do
      local rel="${world_dir#"$PROJECT_ROOT/"}"
      mkdir -p "$PRESERVE_ROOT/$(dirname "$rel")"
      cp -a "$world_dir" "$PRESERVE_ROOT/$rel"
    done < <(find "$PROJECT_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world' -o -name 'world_*' -o -name 'paper-world*' \) -print0)
  fi
  if [[ -d /etc/systemd/system ]]; then
    mkdir -p "$backup_dir/systemd"
    for unit in "${SYSTEMD_UNITS[@]}"; do
      [[ -f "/etc/systemd/system/$unit" ]] && cp -a "/etc/systemd/system/$unit" "$backup_dir/systemd/$unit" || true
    done
  fi
  if [[ -d /etc/nginx/sites-available ]]; then
    mkdir -p "$backup_dir/nginx"
    [[ -f "$NGINX_AVAILABLE" ]] && cp -a "$NGINX_AVAILABLE" "$backup_dir/nginx/" || true
    [[ -L "$NGINX_ENABLED" || -f "$NGINX_ENABLED" ]] && cp -a "$NGINX_ENABLED" "$backup_dir/nginx/" || true
  fi
  log "Backup saved to: $backup_dir"
}

stop_services() {
  log "[7/16] Stop services"
  for service in "${SERVICES[@]}"; do
    systemctl stop "$service" 2>/dev/null || true
  done
  systemctl stop nginx 2>/dev/null || true
}

install_payload() {
  log "[8/16] Replace project tree"
  rm -rf "${PROJECT_ROOT}.new"
  mkdir -p "${PROJECT_ROOT}.new"
  cp -a "$PAYLOAD_ROOT/." "${PROJECT_ROOT}.new/"
  PREVIOUS_RELEASE="${PROJECT_ROOT}.old-$TS"
  rm -rf "$PREVIOUS_RELEASE"
  if [[ -d "$PROJECT_ROOT" ]]; then
    mv "$PROJECT_ROOT" "$PREVIOUS_RELEASE"
  fi
  mv "${PROJECT_ROOT}.new" "$PROJECT_ROOT"
  restore_preserved_state "$PROJECT_ROOT"
  ROLLBACK_ARMED=1
}

restore_preserved_state() {
  local target_root="$1"
  [[ -d "$PRESERVE_ROOT" ]] || return 0
  for relative in "${PRESERVE_PATHS[@]}"; do
    if [[ -e "$PRESERVE_ROOT/$relative" ]]; then
      mkdir -p "$target_root/$(dirname "$relative")"
      rm -rf "$target_root/$relative"
      cp -a "$PRESERVE_ROOT/$relative" "$target_root/$relative"
    fi
  done
  if [[ "$WIPE_WORLDS" != "1" && -d "$PRESERVE_ROOT/minecraft/server" ]]; then
    while IFS= read -r -d '' preserved_world; do
      local rel="${preserved_world#"$PRESERVE_ROOT/"}"
      mkdir -p "$target_root/$(dirname "$rel")"
      rm -rf "$target_root/$rel"
      cp -a "$preserved_world" "$target_root/$rel"
    done < <(find "$PRESERVE_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world' -o -name 'world_*' -o -name 'paper-world*' \) -print0)
  fi
}

wipe_worlds_if_requested() {
  log "[9/16] Handle worlds"
  if [[ "$WIPE_WORLDS" != "1" ]]; then
    log "World wipe disabled; preserved worlds restored."
    return
  fi
  local server_dir="$PROJECT_ROOT/minecraft/server"
  require_dir "$server_dir"
  for world in world world_nether world_the_end; do
    rm -rf "$server_dir/$world"
  done
  python3 - "$server_dir/server.properties" "$WORLD_SEED" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
seed = sys.argv[2]
lines = path.read_text(encoding="utf-8").splitlines()
updated = False
for idx, line in enumerate(lines):
    if line.startswith("level-seed="):
        lines[idx] = f"level-seed={seed}"
        updated = True
        break
if not updated:
    lines.append(f"level-seed={seed}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
  log "World wipe enabled; next server start will generate a new world with seed $WORLD_SEED"
}

restore_database_if_requested() {
  log "[10/16] Restore database"
  local selected_dump=""
  if [[ -n "$DB_DUMP_PATH" ]]; then
    selected_dump="$DB_DUMP_PATH"
  elif [[ -f "$PROJECT_ROOT/db/runtime/copimine.dump" ]]; then
    selected_dump="$PROJECT_ROOT/db/runtime/copimine.dump"
  fi
  if [[ -z "$selected_dump" ]]; then
    log "No database dump provided. Existing database remains untouched."
    return
  fi
  local env_file="$PROJECT_ROOT/admin-web/.env"
  require_file "$env_file"
  local pg_password pg_user pg_db
  pg_password="$(python3 - "$env_file" <<'PY'
from pathlib import Path
import sys

for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig', errors='replace').splitlines():
    if line.startswith('POSTGRES_PASSWORD='):
        print(line.split('=', 1)[1].strip())
        break
PY
)"
  pg_user="$(python3 - "$env_file" <<'PY'
from pathlib import Path
import sys

for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig', errors='replace').splitlines():
    if line.startswith('POSTGRES_USER='):
        print(line.split('=', 1)[1].strip())
        break
PY
)"
  pg_db="$(python3 - "$env_file" <<'PY'
from pathlib import Path
import sys

for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig', errors='replace').splitlines():
    if line.startswith('POSTGRES_DB='):
        print(line.split('=', 1)[1].strip())
        break
PY
)"
  [[ -n "$pg_password" && -n "$pg_user" && -n "$pg_db" ]] || die "PostgreSQL credentials are incomplete in $env_file"
  sudo -u postgres dropdb --if-exists "$pg_db"
  sudo -u postgres createdb -O "$pg_user" "$pg_db"
  case "$selected_dump" in
    *.sql)
      PGPASSWORD="$pg_password" psql -h 127.0.0.1 -U "$pg_user" -v ON_ERROR_STOP=1 -d "$pg_db" -f "$selected_dump" >/dev/null
      ;;
    *)
      PGPASSWORD="$pg_password" pg_restore -h 127.0.0.1 -U "$pg_user" -v -d "$pg_db" "$selected_dump" >/dev/null
      ;;
  esac
  log "Database restored from: $selected_dump"
}

fix_permissions() {
  log "[11/16] Fix ownership and permissions"
  chown -R "$APP_USER:$APP_GROUP" "$PROJECT_ROOT"
  find "$PROJECT_ROOT" -type d -exec chmod 755 {} \;
  find "$PROJECT_ROOT" -type f -exec chmod 644 {} \;
  find "$PROJECT_ROOT" -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod 755 {} \; || true
  [[ -f "$PROJECT_ROOT/admin-web/.env" ]] && chmod 600 "$PROJECT_ROOT/admin-web/.env"
}

install_system_files() {
  log "[12/16] Install system files"
  local deploy_dir="$PROJECT_ROOT/admin-web/deploy"
  [[ -d "$deploy_dir" ]] || return 0
  for unit in "${SYSTEMD_UNITS[@]}"; do
    if [[ -f "$deploy_dir/$unit" ]]; then
      install -m 0644 "$deploy_dir/$unit" "/etc/systemd/system/$unit"
    fi
  done
  if [[ -f "$PROJECT_ROOT/$NGINX_TEMPLATE_REL" ]]; then
    install -m 0644 "$PROJECT_ROOT/$NGINX_TEMPLATE_REL" "$NGINX_AVAILABLE"
    mkdir -p "$(dirname "$NGINX_ENABLED")"
    ln -sfn "$NGINX_AVAILABLE" "$NGINX_ENABLED"
    rm -f /etc/nginx/sites-enabled/default
    nginx -t || die "nginx configuration test failed"
  fi
  systemctl daemon-reload
}

validate_runtime_tree() {
  log "[13/16] Validate runtime tree"
  require_dir "$PROJECT_ROOT/admin-web/backend"
  require_dir "$PROJECT_ROOT/minecraft/server/plugins"
  python3 -m compileall "$PROJECT_ROOT/admin-web/backend" >/dev/null || die "FastAPI backend compile check failed"
  if [[ -f "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" ]]; then
    if command -v unzip >/dev/null 2>&1; then
      unzip -tq "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" >/dev/null || die "Resource pack zip is corrupted"
    fi
  fi
  if [[ -f "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" ]]; then
    if command -v unzip >/dev/null 2>&1; then
      unzip -tq "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" >/dev/null || die "Modpack zip is corrupted"
    fi
    if [[ -f "$PROJECT_ROOT/thirdparty/CopiMineMods.sha1" ]]; then
      local expected_sha1 actual_sha1
      expected_sha1="$(python3 -c "from pathlib import Path; print(Path(r'$PROJECT_ROOT/thirdparty/CopiMineMods.sha1').read_text(encoding='utf-8-sig').strip())")"
      actual_sha1="$(sha1sum "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" | awk '{print $1}')"
      [[ "$expected_sha1" == "$actual_sha1" ]] || die "Modpack SHA1 mismatch. Expected $expected_sha1, got $actual_sha1"
    fi
  fi
  local found_jar=0
  while IFS= read -r -d '' jar_path; do
    found_jar=1
    jar tf "$jar_path" >/dev/null || die "Broken plugin jar: $jar_path"
  done < <(find "$PROJECT_ROOT/minecraft/server/plugins" -maxdepth 1 -type f -name '*.jar' -print0)
  [[ $found_jar -eq 1 ]] || log "WARNING: plugin jar files were not found in minecraft/server/plugins"
}

restart_services() {
  for service in "${SERVICES[@]}"; do
    if systemctl list-unit-files | grep -q "^${service}\.service"; then
      systemctl enable "$service" >/dev/null 2>&1 || true
      systemctl restart "$service"
    fi
  done
  if systemctl list-unit-files | grep -q '^nginx\.service'; then
    systemctl enable nginx >/dev/null 2>&1 || true
    systemctl restart nginx
  fi
}

wait_for_service() {
  local service="$1"
  local timeout="${2:-90}"
  local elapsed=0
  while (( elapsed < timeout )); do
    if systemctl is-active --quiet "$service"; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

verify_services() {
  log "[14/16] Start services"
  restart_services
  for service in "${SERVICES[@]}"; do
    if systemctl list-unit-files | grep -q "^${service}\.service"; then
      wait_for_service "$service" 90 || die "Service did not become active: $service"
    fi
  done
  if systemctl list-unit-files | grep -q '^nginx\.service'; then
    wait_for_service nginx 30 || die "nginx did not become active"
  fi
}

verify_http() {
  log "[15/16] HTTP verification"
  curl -fsS http://127.0.0.1:8090/api/health >/dev/null || die "/api/health failed"
  curl -fsS http://127.0.0.1:8090/api/runtime >/dev/null || die "/api/runtime failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/downloads/CopiMineMods.zip >/dev/null || die "Modpack download route failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip >/dev/null || die "Resource pack download route failed"
}

final_summary() {
  log "[16/16] Summary"
  log "Release installed successfully."
  log "Project root: $PROJECT_ROOT"
  log "Previous release backup: $PREVIOUS_RELEASE"
  log "World wipe enabled: $WIPE_WORLDS"
  log "World seed: $WORLD_SEED"
  log "Log file: $LOG_FILE"
  log "Use 'systemctl status copimine-admin copimine-minecraft --no-pager' for quick status."
}

main() {
  preflight
  validate_inputs
  prepare_temp
  extract_archive
  detect_payload
  backup_current_release
  stop_services
  install_payload
  wipe_worlds_if_requested
  restore_database_if_requested
  fix_permissions
  install_system_files
  validate_runtime_tree
  verify_services
  verify_http
  final_summary
}

main "$@"
