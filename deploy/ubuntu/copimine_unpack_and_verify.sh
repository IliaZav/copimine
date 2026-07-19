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
CLEAN_WORLD_STATE="${CLEAN_WORLD_STATE:-${COPIMINE_CLEAN_WORLD_STATE:-0}}"

CURRENT_ENV_USER=""
CURRENT_DATA_USER=""
CURRENT_UNIT_USER=""
if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
  CURRENT_ENV_USER="$(stat -c '%U' "$PROJECT_ROOT/admin-web/.env" 2>/dev/null || true)"
fi
if [[ -d "$PROJECT_ROOT/admin-web/data" ]]; then
  CURRENT_DATA_USER="$(stat -c '%U' "$PROJECT_ROOT/admin-web/data" 2>/dev/null || true)"
fi
if command -v systemctl >/dev/null 2>&1; then
  CURRENT_UNIT_USER="$(systemctl show copimine-admin.service -p User --value 2>/dev/null || true)"
fi
# Keep the account already owning the runtime configuration.  Falling back to
# a new `copimine` account on an existing qwerty deployment makes systemd use a
# different user and breaks access to the protected .env file.
APP_USER="${APP_USER:-${COPIMINE_APP_USER:-${CURRENT_DATA_USER:-${CURRENT_UNIT_USER:-${CURRENT_ENV_USER:-${SUDO_USER:-copimine}}}}}}"
if [[ "$APP_USER" == "root" || -z "$APP_USER" ]]; then
  APP_USER="${CURRENT_DATA_USER:-${CURRENT_UNIT_USER:-${SUDO_USER:-copimine}}}"
fi
APP_GROUP="${APP_GROUP:-$APP_USER}"

NGINX_TEMPLATE_REL="admin-web/deploy/nginx-copimine-admin-18080.conf"
NGINX_AVAILABLE="${NGINX_AVAILABLE:-/etc/nginx/sites-available/copimine-admin.conf}"
NGINX_ENABLED="${NGINX_ENABLED:-/etc/nginx/sites-enabled/copimine-admin.conf}"

SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
  "copimine-game-hardening"
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
)

SYSTEMD_UNITS=(
  "copimine-admin.service"
  "copimine-discord-bot.service"
  "copimine-minecraft-discord-bridge.service"
  "copimine-minecraft.service"
  "copimine-game-hardening.service"
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

validate_archive_members() {
  python3 - "$ARCHIVE_PATH" <<'PY'
from pathlib import PurePosixPath
import stat
import sys
import tarfile
import zipfile

archive = sys.argv[1]

def safe_name(value: str) -> bool:
    value = value.replace("\\", "/")
    path = PurePosixPath(value)
    return bool(value) and not path.is_absolute() and ".." not in path.parts

if archive.endswith((".tar.gz", ".tgz")):
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle.getmembers():
            if not safe_name(member.name):
                raise SystemExit(f"Unsafe archive member path: {member.name!r}")
            if (member.issym() or member.islnk()) and not safe_name(member.linkname):
                raise SystemExit(f"Unsafe archive link: {member.name!r}")
elif archive.endswith(".zip"):
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            if not safe_name(member.filename):
                raise SystemExit(f"Unsafe archive member path: {member.filename!r}")
            if stat.S_ISLNK(member.external_attr >> 16):
                raise SystemExit(f"Archive symlinks are not allowed: {member.filename!r}")
else:
    raise SystemExit("Unsupported archive extension")
PY
}

load_shared_helpers() {
  local common_script="$PROJECT_ROOT/deploy/shared/common.sh"
  [[ -f "$common_script" ]] || die "Missing shared deploy helpers: $common_script"
  COPIMINE_ROOT="$PROJECT_ROOT"
  COPIMINE_APP_USER="$APP_USER"
  COPIMINE_APP_GROUP="$APP_GROUP"
  COPIMINE_ADMIN_DIR="$PROJECT_ROOT/admin-web"
  COPIMINE_ENV_FILE="$PROJECT_ROOT/admin-web/.env"
  COPIMINE_SERVER_DIR="$PROJECT_ROOT/minecraft/server"
  COPIMINE_SERVER_PROPERTIES="$PROJECT_ROOT/minecraft/server/server.properties"
  COPIMINE_BACKUP_DIR="$BACKUP_ROOT"
  COPIMINE_RELEASE_MANIFEST="$PROJECT_ROOT/deploy/release_manifest.json"
  COPIMINE_INSTALLER_MANIFEST="$PROJECT_ROOT/deploy/installer_manifest.json"
  COPIMINE_RUNTIME_METADATA="$PROJECT_ROOT/deploy/runtime_metadata.json"
  COPIMINE_NGINX_AVAILABLE="$NGINX_AVAILABLE"
  COPIMINE_NGINX_ENABLED="$NGINX_ENABLED"
  # shellcheck source=/dev/null
  source "$common_script"
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
    copimine_rollback_database_restore || log "WARNING: database rollback could not be completed automatically."
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
  chmod 700 "$BACKUP_ROOT" "$SECRETS_ROOT"
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
  validate_archive_members
  if [[ -z "$ARCHIVE_SHA256" && -f "${ARCHIVE_PATH}.sha256" ]]; then
    ARCHIVE_SHA256="$(awk '{print $1}' "${ARCHIVE_PATH}.sha256" | head -n 1)"
  fi
  [[ -n "$ARCHIVE_SHA256" ]] || die "Archive SHA256 is required as argument 2 or ${ARCHIVE_PATH}.sha256 sidecar."
  [[ "${#ARCHIVE_SHA256}" -eq 64 ]] || die "Archive SHA256 must contain exactly 64 hex characters."
  [[ "$ARCHIVE_SHA256" =~ ^[0-9A-Fa-f]{64}$ ]] || die "Archive SHA256 contains non-hex characters."
  local actual
  actual="$(sha256sum "$ARCHIVE_PATH" | awk '{print tolower($1)}')"
  [[ "${ARCHIVE_SHA256,,}" == "$actual" ]] || die "Archive SHA256 mismatch. Expected ${ARCHIVE_SHA256,,}, got $actual"
  log "Archive SHA256 verified: $actual"
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
      tar -xzf "$ARCHIVE_PATH" -C "$EXTRACT_ROOT" --no-same-owner --no-same-permissions
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
  chmod 700 "$backup_dir"
  if [[ -d "$PROJECT_ROOT" ]]; then
    cp -a "$PROJECT_ROOT" "$backup_dir/copimine-pre-replace"
  fi
  if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
    load_shared_helpers
    local pg_password pg_user pg_db
    pg_password="$(copimine_env_value POSTGRES_PASSWORD)"
    pg_user="$(copimine_env_value POSTGRES_USER)"
    pg_db="$(copimine_env_value POSTGRES_DB)"
    if [[ -n "$pg_password" && -n "$pg_user" && -n "$pg_db" ]]; then
      PGPASSWORD="$pg_password" pg_dump -h 127.0.0.1 -U "$pg_user" -d "$pg_db" -Fc -f "$backup_dir/copimine-db.dump" >/dev/null 2>&1 || log "WARNING: PostgreSQL backup skipped."
      [[ -f "$backup_dir/copimine-db.dump" ]] && chmod 600 "$backup_dir/copimine-db.dump"
    fi
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

bootstrap_runtime_environment() {
  log "[8b/16] Bootstrap protected runtime configuration"
  load_shared_helpers
  copimine_ensure_layout
  copimine_ensure_app_user
  if [[ ! -f "$COPIMINE_ENV_FILE" ]]; then
    local postgres_password secret_key plugin_api_key rcon_password
    postgres_password="$(copimine_secret postgres-password.txt 24)"
    secret_key="$(copimine_secret secret-key.txt 32)"
    plugin_api_key="$(copimine_secret plugin-api-key.txt 32)"
    rcon_password="$(copimine_secret rcon-password.txt 32)"
    copimine_write_env "$postgres_password" "$secret_key" "$plugin_api_key" "$rcon_password"
  fi
  copimine_normalize_transport_auth
  copimine_ensure_postgres "$(copimine_env_value POSTGRES_PASSWORD)"
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
  if [[ "${KEEP_WORLD_SEED:-1}" == "1" && -f "$PRESERVE_ROOT/minecraft/server/server.properties" ]]; then
    preserved_seed="$(awk -F= '$1=="level-seed" {print substr($0,index($0,"=")+1); exit}' "$PRESERVE_ROOT/minecraft/server/server.properties")"
    [[ -n "$preserved_seed" ]] && WORLD_SEED="$preserved_seed"
  fi
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
  load_shared_helpers
  copimine_restore_database_safely "$selected_dump"
  log "Database restored from: $selected_dump"
}

apply_database_migrations() {
  log "[10a/16] Apply ordered database migrations"
  load_shared_helpers
  copimine_apply_migrations
}

clean_world_state_if_requested() {
  local should_clean="$CLEAN_WORLD_STATE"
  if [[ "$WIPE_WORLDS" == "1" ]]; then
    should_clean="1"
  fi
  if [[ "$should_clean" != "1" ]]; then
    return 0
  fi
  log "[10b/16] Clean world-bound database state"
  load_shared_helpers
  copimine_apply_clean_world_state
}

fix_permissions() {
  log "[11/16] Fix ownership and permissions"
  load_shared_helpers
  copimine_ensure_app_user
  chown -R "$APP_USER:$APP_GROUP" "$PROJECT_ROOT"
  find "$PROJECT_ROOT" -type d -exec chmod 755 {} \;
  find "$PROJECT_ROOT" -type f -exec chmod 644 {} \;
  find "$PROJECT_ROOT" -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod 755 {} \; || true
  if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
    chown "$APP_USER:$APP_GROUP" "$PROJECT_ROOT/admin-web/.env"
    chmod 600 "$PROJECT_ROOT/admin-web/.env"
    [[ "$(stat -c '%U:%G' "$PROJECT_ROOT/admin-web/.env")" == "$APP_USER:$APP_GROUP" ]] \
      || die "Runtime environment file is not readable by $APP_USER"
  fi
  for private_dir in "$PROJECT_ROOT/admin-web/data" "$PROJECT_ROOT/admin-web/backups"; do
    [[ -d "$private_dir" ]] && chmod 700 "$private_dir"
  done
}

refresh_managed_release_artifacts() {
  log "[12/16] Refresh managed hashes and runtime metadata"
  load_shared_helpers
  copimine_refresh_release_artifacts
  copimine_validate_release_contract
}

prepare_python_runtime() {
  log "[12b/16] Rebuild admin-web Python runtime"
  load_shared_helpers
  copimine_python_env
}

install_system_files() {
  log "[13/16] Install system files"
  load_shared_helpers
  copimine_install_system_files
  systemctl daemon-reload
}

validate_runtime_tree() {
  log "[14/16] Validate runtime tree"
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
    local modpack_sha256_path="$PROJECT_ROOT/thirdparty/CopiMineMods.sha256"
    [[ -f "$modpack_sha256_path" ]] || die "Missing modpack SHA256 sidecar"
    local expected_sha256 actual_sha256
    expected_sha256="$(tr -d '\r\n' < "$modpack_sha256_path" | tr '[:upper:]' '[:lower:]')"
    actual_sha256="$(sha256sum "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" | awk '{print tolower($1)}')"
    [[ "$expected_sha256" =~ ^[0-9a-f]{64}$ ]] || die "Modpack SHA256 sidecar is malformed"
    [[ "$expected_sha256" == "$actual_sha256" ]] || die "Modpack SHA256 mismatch."
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
    # `systemctl list-unit-files | grep` is unreliable in non-interactive
    # sudo sessions (and can return an empty list even for installed units).
    # Ask systemd directly, then start the unit and keep its real error.
    # Admin and Minecraft are mandatory for a usable release.  Never let a
    # false-negative unit lookup silently skip either one.
    if [[ "$service" == "copimine-admin" || "$service" == "copimine-minecraft" ]] \
      || systemctl cat "$service.service" >/dev/null 2>&1; then
      log "Starting service: $service"
      systemctl enable "$service.service" >/dev/null 2>&1 || true
      if ! systemctl restart "$service.service"; then
        log "ERROR: failed to restart $service"
        systemctl --no-pager --full status "$service.service" || true
        journalctl -u "$service.service" -n 160 --no-pager || true
        return 1
      fi
    else
      log "Optional service is not installed; skipping: $service"
    fi
  done
  if systemctl cat nginx.service >/dev/null 2>&1; then
    systemctl enable nginx.service >/dev/null 2>&1 || true
    if ! systemctl restart nginx.service; then
      log 'ERROR: failed to restart nginx'
      systemctl --no-pager --full status nginx.service || true
      journalctl -u nginx.service -n 160 --no-pager || true
      return 1
    fi
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
  log "[15/16] Start services"
  restart_services || die 'Service restart failed; see the journal above.'
  for service in "${SERVICES[@]}"; do
    if [[ "$service" == "copimine-admin" || "$service" == "copimine-minecraft" ]] \
      || systemctl cat "$service.service" >/dev/null 2>&1; then
      if ! wait_for_service "$service" 90; then
        log "ERROR: service did not become active: $service"
        systemctl --no-pager --full status "$service" || true
        journalctl -u "$service" -n 120 --no-pager || true
        die "Service did not become active: $service"
      fi
    fi
  done
  if systemctl cat nginx.service >/dev/null 2>&1; then
    if ! wait_for_service nginx 30; then
      log 'ERROR: nginx did not become active'
      systemctl --no-pager --full status nginx || true
      journalctl -u nginx -n 120 --no-pager || true
      die 'nginx did not become active'
    fi
  fi
}

verify_http() {
  log "[16/16] HTTP verification"
  local health_ok=0 attempt
  for attempt in $(seq 1 45); do
    if curl -fsS --max-time 3 http://127.0.0.1:8090/api/health >/dev/null 2>&1; then
      health_ok=1
      break
    fi
    sleep 2
  done
  if [[ "$health_ok" != "1" ]]; then
    log 'ERROR: /api/health failed after 90 seconds'
    systemctl --no-pager --full status copimine-admin || true
    journalctl -u copimine-admin -n 160 --no-pager || true
    ss -ltnp || true
    die '/api/health failed'
  fi
  curl -fsS --max-time 10 http://127.0.0.1:8090/api/runtime >/dev/null || die "/api/runtime failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/downloads/CopiMineMods.zip >/dev/null || die "Modpack download route failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip >/dev/null || die "Resource pack download route failed"
  local tmp_modpack tmp_resourcepack local_modpack_sha remote_modpack_sha local_resourcepack_sha remote_resourcepack_sha
  tmp_modpack="$TMP_ROOT/CopiMineMods.zip"
  tmp_resourcepack="$TMP_ROOT/CopiMineResourcePack.zip"
  curl -fsS -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/downloads/CopiMineMods.zip -o "$tmp_modpack" || die "Modpack payload download failed"
  curl -fsS -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip -o "$tmp_resourcepack" || die "Resource pack payload download failed"
  local_modpack_sha="$(sha256sum "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" | awk '{print $1}')"
  remote_modpack_sha="$(sha256sum "$tmp_modpack" | awk '{print $1}')"
  [[ "$local_modpack_sha" == "$remote_modpack_sha" ]] || die "Served modpack SHA256 mismatch. Runtime=$local_modpack_sha download=$remote_modpack_sha"
  local_resourcepack_sha="$(sha256sum "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
  remote_resourcepack_sha="$(sha256sum "$tmp_resourcepack" | awk '{print $1}')"
  [[ "$local_resourcepack_sha" == "$remote_resourcepack_sha" ]] || die "Served resource pack SHA256 mismatch. Runtime=$local_resourcepack_sha download=$remote_resourcepack_sha"
}

final_summary() {
  log "[17/17] Summary"
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
  bootstrap_runtime_environment
  wipe_worlds_if_requested
  restore_database_if_requested
  apply_database_migrations
  clean_world_state_if_requested
  fix_permissions
  refresh_managed_release_artifacts
  prepare_python_runtime
  install_system_files
  validate_runtime_tree
  verify_services
  verify_http
  final_summary
}

main "$@"
