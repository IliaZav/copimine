#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/copimine-backups}"
APP_USER="${APP_USER:-$(stat -c '%U' "$PROJECT_ROOT" 2>/dev/null || echo copimine)}"
APP_GROUP="${APP_GROUP:-$(stat -c '%G' "$PROJECT_ROOT" 2>/dev/null || echo "$APP_USER")}"
ARCHIVE_PATH="${1:-}"
DB_DUMP_PATH="${2:-}"
LOG_FILE="${LOG_FILE:-/var/log/copimine-installer.log}"
TMP_ROOT=""
EXTRACT_ROOT=""
PAYLOAD_ROOT=""
STAGING_ROOT=""
PRESERVE_ROOT=""
ROLLBACK_READY=0
ACTIVE_RELEASE_BACKUP=""
TS="$(date +%Y%m%d-%H%M%S)"

SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
)

RUNTIME_PATHS=(
  "admin-web/.env"
  "admin-web/data"
  "admin-web/backups"
  "minecraft/server/server.properties"
  "minecraft/server/eula.txt"
  "minecraft/server/whitelist.json"
  "minecraft/server/ops.json"
  "minecraft/server/banned-players.json"
  "minecraft/server/banned-ips.json"
  "minecraft/server/usercache.json"
  "minecraft/server/plugins/LuckPerms"
  "minecraft/server/plugins/AuthMe"
  "minecraft/server/plugins/nLogin"
  "minecraft/server/plugins/FastLogin"
  "minecraft/server/logs"
  "resourcepacks/build"
  "thirdparty/CopiMineMods.zip"
  "thirdparty/CopiMineMods.sha1"
  "thirdparty/CopiMineMods.sha256"
  "thirdparty/modpack_manifest.json"
  "deploy/runtime_metadata.json"
)

REQUIRED_PAYLOAD_PATHS=(
  "admin-web"
  "minecraft"
  "resourcepacks"
  "thirdparty"
  "deploy"
  "scripts"
)

SYSTEM_FILES=(
  "copimine-admin.service:/etc/systemd/system/copimine-admin.service"
  "copimine-discord-bot.service:/etc/systemd/system/copimine-discord-bot.service"
  "copimine-minecraft-discord-bridge.service:/etc/systemd/system/copimine-minecraft-discord-bridge.service"
  "copimine-minecraft.service:/etc/systemd/system/copimine-minecraft.service"
)

NginxTemplateRelative="admin-web/deploy/nginx-copimine-admin-18080.conf"
NginxAvailable="${NGINX_AVAILABLE:-/etc/nginx/sites-available/copimine-admin.conf}"
NginxEnabled="${NGINX_ENABLED:-/etc/nginx/sites-enabled/copimine-admin.conf}"

log() {
  local line="[copimine][$(date '+%Y-%m-%d %H:%M:%S')] $*"
  printf '%s\n' "$line"
  printf '%s\n' "$line" >> "$LOG_FILE"
}

fail() {
  log "ERROR: $*"
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

ensure_root() {
  [[ "${EUID:-$(id -u)}" -eq 0 ]] || fail "Run this installer with sudo/root."
}

mkdir_parent() {
  mkdir -p "$(dirname "$1")"
}

cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && $ROLLBACK_READY -eq 1 ]]; then
    log "Failure detected. Running automatic rollback."
    rollback_active_release || log "Rollback failed. Check backups under $BACKUP_ROOT."
  fi
  [[ -n "$TMP_ROOT" && -d "$TMP_ROOT" ]] && rm -rf "$TMP_ROOT"
  exit $exit_code
}

trap cleanup EXIT

rollback_active_release() {
  local live_root="$PROJECT_ROOT"
  local current_broken="${PROJECT_ROOT}.broken-$TS"
  if [[ -d "$live_root" ]]; then
    mv "$live_root" "$current_broken"
  fi
  if [[ -n "$ACTIVE_RELEASE_BACKUP" && -d "$ACTIVE_RELEASE_BACKUP" ]]; then
    mv "$ACTIVE_RELEASE_BACKUP" "$live_root"
    log "Rollback restored previous release to $live_root"
  fi
  restore_runtime_state "$live_root"
  restore_system_files
  systemctl daemon-reload || true
  restart_services || true
  return 0
}

require_file() {
  [[ -f "$1" ]] || fail "File not found: $1"
}

require_dir() {
  [[ -d "$1" ]] || fail "Directory not found: $1"
}

check_disk_space() {
  local path="$1"
  local min_mb="$2"
  local free_mb
  free_mb="$(df -Pm "$path" | awk 'NR==2 {print $4}')"
  [[ -n "$free_mb" ]] || fail "Unable to read free disk space for $path"
  (( free_mb >= min_mb )) || fail "Not enough free space on $path. Need ${min_mb}MB, have ${free_mb}MB."
}

check_inode_space() {
  local path="$1"
  local min_inodes="$2"
  local free_inodes
  free_inodes="$(df -Pi "$path" | awk 'NR==2 {print $4}')"
  [[ -n "$free_inodes" ]] || fail "Unable to read free inode count for $path"
  (( free_inodes >= min_inodes )) || fail "Not enough free inodes on $path. Need ${min_inodes}, have ${free_inodes}."
}

sha256_file() {
  local file="$1"
  sha256sum "$file" | awk '{print $1}'
}

sha1_file() {
  local file="$1"
  sha1sum "$file" | awk '{print $1}'
}

preflight_system() {
  log "[1/14] System preflight"
  ensure_root
  need bash
  need tar
  need gzip
  need find
  need awk
  need sed
  need stat
  need sha256sum
  need sha1sum
  need python3
  need systemctl
  need runuser
  need psql
  need pg_dump
  need pg_restore
  need nginx
  need java
  need curl
  if command -v unzip >/dev/null 2>&1; then
    :
  fi
  [[ -r /etc/os-release ]] || fail "Missing /etc/os-release"
  check_disk_space /opt 4096
  check_inode_space /opt 10000
  check_disk_space /var 1024
  mkdir -p "$(dirname "$LOG_FILE")" "$BACKUP_ROOT"
  touch "$LOG_FILE"
  chmod 600 "$LOG_FILE"
  log "Ubuntu: $(grep '^PRETTY_NAME=' /etc/os-release | cut -d= -f2- | tr -d '"')"
  log "Kernel: $(uname -r)"
  log "Bash: ${BASH_VERSION:-unknown}"
  log "Project root: $PROJECT_ROOT"
  log "App user/group: $APP_USER:$APP_GROUP"
}

validate_archive() {
  log "[2/14] Validate release archive"
  [[ -n "$ARCHIVE_PATH" ]] || fail "Usage: sudo bash copimine_full_replace.sh /path/to/release.tar.gz [/path/to/copimine.dump]"
  require_file "$ARCHIVE_PATH"
  [[ -s "$ARCHIVE_PATH" ]] || fail "Archive is empty: $ARCHIVE_PATH"

  case "$ARCHIVE_PATH" in
    *.tar.gz|*.tgz)
      tar -tzf "$ARCHIVE_PATH" >/dev/null || fail "tar cannot read archive: $ARCHIVE_PATH"
      ;;
    *.zip)
      need unzip
      unzip -tq "$ARCHIVE_PATH" >/dev/null || fail "unzip test failed: $ARCHIVE_PATH"
      ;;
    *)
      fail "Unsupported archive format: $ARCHIVE_PATH"
      ;;
  esac

  local sidecar=""
  if [[ -f "${ARCHIVE_PATH}.sha256" ]]; then
    sidecar="${ARCHIVE_PATH}.sha256"
  elif [[ "$ARCHIVE_PATH" =~ \.tar\.gz$ && -f "${ARCHIVE_PATH%.tar.gz}.sha256" ]]; then
    sidecar="${ARCHIVE_PATH%.tar.gz}.sha256"
  elif [[ "$ARCHIVE_PATH" =~ \.tgz$ && -f "${ARCHIVE_PATH%.tgz}.sha256" ]]; then
    sidecar="${ARCHIVE_PATH%.tgz}.sha256"
  elif [[ "$ARCHIVE_PATH" =~ \.zip$ && -f "${ARCHIVE_PATH%.zip}.sha256" ]]; then
    sidecar="${ARCHIVE_PATH%.zip}.sha256"
  fi

  if [[ -n "$sidecar" ]]; then
    local expected actual
    expected="$(awk '{print tolower($1)}' "$sidecar" | head -n 1)"
    actual="$(sha256_file "$ARCHIVE_PATH")"
    [[ "$expected" == "$actual" ]] || fail "SHA256 mismatch for archive. Expected $expected, got $actual."
    log "Archive SHA256 verified: $actual"
  else
    log "WARNING: SHA256 sidecar not found. Continuing without checksum file."
  fi
}

prepare_temp_dirs() {
  log "[3/14] Prepare staging directories"
  TMP_ROOT="$(mktemp -d /tmp/copimine-installer-XXXXXX)"
  EXTRACT_ROOT="$TMP_ROOT/extracted"
  STAGING_ROOT="$TMP_ROOT/staging"
  PRESERVE_ROOT="$TMP_ROOT/preserve"
  mkdir -p "$EXTRACT_ROOT" "$STAGING_ROOT" "$PRESERVE_ROOT"
}

extract_archive() {
  log "[4/14] Extract archive"
  case "$ARCHIVE_PATH" in
    *.tar.gz|*.tgz)
      tar -xzf "$ARCHIVE_PATH" -C "$EXTRACT_ROOT"
      ;;
    *.zip)
      unzip -q "$ARCHIVE_PATH" -d "$EXTRACT_ROOT"
      ;;
  esac
}

detect_payload_root() {
  log "[5/14] Detect payload root"
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
  [[ -n "$PAYLOAD_ROOT" && -d "$PAYLOAD_ROOT" ]] || fail "Unable to detect archive payload root."
  for relative in "${REQUIRED_PAYLOAD_PATHS[@]}"; do
    [[ -e "$PAYLOAD_ROOT/$relative" ]] || fail "Archive payload is incomplete. Missing: $relative"
  done
}

snapshot_runtime_state() {
  log "[6/14] Backup current project and runtime state"
  local live_root="$PROJECT_ROOT"
  local backup_dir="$BACKUP_ROOT/full-replace-$TS"
  mkdir -p "$backup_dir"

  if [[ -d "$live_root" ]]; then
    cp -a "$live_root" "$backup_dir/copimine-pre-replace"
  fi

  if command -v pg_dump >/dev/null 2>&1; then
    local env_file="$live_root/admin-web/.env"
    local pg_password=""
    local pg_db="copimine"
    local pg_user="copimine"
    if [[ -f "$env_file" ]]; then
      pg_password="$(grep -E '^POSTGRES_PASSWORD=' "$env_file" | tail -n 1 | cut -d= -f2- || true)"
      pg_db="$(grep -E '^POSTGRES_DB=' "$env_file" | tail -n 1 | cut -d= -f2- || echo copimine)"
      pg_user="$(grep -E '^POSTGRES_USER=' "$env_file" | tail -n 1 | cut -d= -f2- || echo copimine)"
    fi
    if [[ -n "$pg_password" ]]; then
      PGPASSWORD="$pg_password" pg_dump -h 127.0.0.1 -U "$pg_user" -d "$pg_db" -Fc -f "$backup_dir/copimine-db.dump" >/dev/null 2>&1 || log "WARNING: PostgreSQL backup skipped."
    else
      log "WARNING: PostgreSQL password not found in .env, database backup skipped."
    fi
  fi

  for runtime_path in "${RUNTIME_PATHS[@]}"; do
    if [[ -e "$PROJECT_ROOT/$runtime_path" ]]; then
      mkdir -p "$PRESERVE_ROOT/$(dirname "$runtime_path")"
      cp -a "$PROJECT_ROOT/$runtime_path" "$PRESERVE_ROOT/$runtime_path"
    fi
  done

  if [[ -d "$PROJECT_ROOT/minecraft/server" ]]; then
    while IFS= read -r -d '' world_dir; do
      local rel
      rel="${world_dir#"$PROJECT_ROOT/"}"
      mkdir -p "$PRESERVE_ROOT/$(dirname "$rel")"
      cp -a "$world_dir" "$PRESERVE_ROOT/$rel"
    done < <(find "$PROJECT_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world' -o -name 'world_*' -o -name 'paper-world*' \) -print0)
  fi

  if [[ -d /etc/nginx/sites-available ]]; then
    mkdir -p "$backup_dir/nginx"
    cp -a /etc/nginx/sites-available "$backup_dir/nginx/" 2>/dev/null || true
    cp -a /etc/nginx/sites-enabled "$backup_dir/nginx/" 2>/dev/null || true
  fi
  mkdir -p "$backup_dir/systemd"
  for item in "${SYSTEM_FILES[@]}"; do
    local target="${item#*:}"
    [[ -f "$target" ]] && cp -a "$target" "$backup_dir/systemd/" || true
  done

  log "Backup saved to: $backup_dir"
}

stop_services() {
  log "[7/14] Stop services"
  for service in "${SERVICES[@]}"; do
    if systemctl list-unit-files | grep -q "^${service}\.service"; then
      systemctl stop "$service" || true
    fi
  done
}

stage_payload() {
  log "[8/14] Stage new release tree"
  rm -rf "${PROJECT_ROOT}.new"
  mkdir -p "${PROJECT_ROOT}.new"
  cp -a "$PAYLOAD_ROOT/." "${PROJECT_ROOT}.new/"
}

restore_runtime_state() {
  local target_root="$1"
  [[ -d "$PRESERVE_ROOT" ]] || return 0
  for runtime_path in "${RUNTIME_PATHS[@]}"; do
    if [[ -e "$PRESERVE_ROOT/$runtime_path" ]]; then
      mkdir -p "$target_root/$(dirname "$runtime_path")"
      rm -rf "$target_root/$runtime_path"
      cp -a "$PRESERVE_ROOT/$runtime_path" "$target_root/$runtime_path"
    fi
  done
  if [[ -d "$PRESERVE_ROOT/minecraft/server" ]]; then
    while IFS= read -r -d '' preserved_world; do
      local rel
      rel="${preserved_world#"$PRESERVE_ROOT/"}"
      mkdir -p "$target_root/$(dirname "$rel")"
      rm -rf "$target_root/$rel"
      cp -a "$preserved_world" "$target_root/$rel"
  done < <(find "$PRESERVE_ROOT/minecraft/server" -mindepth 1 -maxdepth 1 -type d \( -name 'world' -o -name 'world_*' -o -name 'paper-world*' \) -print0)
  fi
}

restore_system_files() {
  local live_root="$PROJECT_ROOT"
  for item in "${SYSTEM_FILES[@]}"; do
    local source_name="${item%%:*}"
    local target_path="${item#*:}"
    local source_path="$live_root/admin-web/deploy/$source_name"
    if [[ -f "$source_path" ]]; then
      install -m 0644 "$source_path" "$target_path"
    fi
  done
  if [[ -f "$live_root/$NginxTemplateRelative" ]]; then
    install -m 0644 "$live_root/$NginxTemplateRelative" "$NginxAvailable"
    mkdir -p "$(dirname "$NginxEnabled")"
    ln -sfn "$NginxAvailable" "$NginxEnabled"
  fi
}

atomic_swap() {
  log "[9/14] Atomic replace"
  ACTIVE_RELEASE_BACKUP="${PROJECT_ROOT}.old-$TS"
  rm -rf "$ACTIVE_RELEASE_BACKUP"
  if [[ -d "$PROJECT_ROOT" ]]; then
    mv "$PROJECT_ROOT" "$ACTIVE_RELEASE_BACKUP"
  fi
  mv "${PROJECT_ROOT}.new" "$PROJECT_ROOT"
  restore_runtime_state "$PROJECT_ROOT"
  ROLLBACK_READY=1
}

restore_database() {
  log "[10/14] Restore database if dump is provided"
  local bundled_dump="$PROJECT_ROOT/db/runtime/copimine.dump"
  local selected_dump=""
  if [[ -n "$DB_DUMP_PATH" ]]; then
    require_file "$DB_DUMP_PATH"
    selected_dump="$DB_DUMP_PATH"
  elif [[ -f "$bundled_dump" ]]; then
    selected_dump="$bundled_dump"
  fi

  if [[ -z "$selected_dump" ]]; then
    log "No database dump supplied. Existing PostgreSQL database is preserved."
    return 0
  fi

  local env_file="$PROJECT_ROOT/admin-web/.env"
  require_file "$env_file"
  local pg_password pg_db pg_user
  pg_password="$(grep -E '^POSTGRES_PASSWORD=' "$env_file" | tail -n 1 | cut -d= -f2-)"
  pg_db="$(grep -E '^POSTGRES_DB=' "$env_file" | tail -n 1 | cut -d= -f2-)"
  pg_user="$(grep -E '^POSTGRES_USER=' "$env_file" | tail -n 1 | cut -d= -f2-)"
  [[ -n "$pg_password" && -n "$pg_db" && -n "$pg_user" ]] || fail "PostgreSQL credentials are incomplete in $env_file"

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
}

fix_permissions() {
  log "[11/14] Fix ownership and permissions"
  chown -R "$APP_USER:$APP_GROUP" "$PROJECT_ROOT"
  find "$PROJECT_ROOT" -type d -exec chmod 755 {} \;
  find "$PROJECT_ROOT" -type f -exec chmod 644 {} \;
  find "$PROJECT_ROOT" -type f \( -name '*.sh' -o -name '*.py' \) -exec chmod 755 {} \; || true
  if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
    chmod 600 "$PROJECT_ROOT/admin-web/.env"
  fi
}

install_system_files() {
  log "[12/14] Refresh systemd and nginx config"
  for item in "${SYSTEM_FILES[@]}"; do
    local source_name="${item%%:*}"
    local target_path="${item#*:}"
    local source_path="$PROJECT_ROOT/admin-web/deploy/$source_name"
    if [[ -f "$source_path" ]]; then
      install -m 0644 "$source_path" "$target_path"
    fi
  done

  if [[ -f "$PROJECT_ROOT/$NginxTemplateRelative" ]]; then
    install -m 0644 "$PROJECT_ROOT/$NginxTemplateRelative" "$NginxAvailable"
    mkdir -p "$(dirname "$NginxEnabled")"
    ln -sfn "$NginxAvailable" "$NginxEnabled"
    rm -f /etc/nginx/sites-enabled/default
    nginx -t || fail "nginx config test failed"
  fi

  systemctl daemon-reload
}

python_backend_check() {
  require_dir "$PROJECT_ROOT/admin-web/backend"
  python3 -m compileall "$PROJECT_ROOT/admin-web/backend" >/dev/null
}

validate_zip_artifact() {
  local file="$1"
  [[ -f "$file" ]] || fail "Missing artifact: $file"
  if command -v unzip >/dev/null 2>&1; then
    unzip -tq "$file" >/dev/null || fail "Corrupted zip artifact: $file"
  elif command -v zipinfo >/dev/null 2>&1; then
    zipinfo -t "$file" >/dev/null || fail "Corrupted zip artifact: $file"
  fi
}

validate_java_artifacts() {
  require_dir "$PROJECT_ROOT/minecraft/server/plugins"
  local found=0
  while IFS= read -r -d '' jar_file; do
    found=1
    jar tf "$jar_file" >/dev/null || fail "Broken jar: $jar_file"
  done < <(find "$PROJECT_ROOT/minecraft/server/plugins" -maxdepth 1 -type f -name '*.jar' -print0)
  [[ $found -eq 1 ]] || log "WARNING: No plugin jars found in minecraft/server/plugins"
}

validate_release_tree() {
  log "[13/14] Validate extracted release"
  python_backend_check
  validate_java_artifacts
  if [[ -f "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" ]]; then
    validate_zip_artifact "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip"
  fi
  if [[ -f "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" ]]; then
    validate_zip_artifact "$PROJECT_ROOT/thirdparty/CopiMineMods.zip"
  fi
  if [[ -f "$PROJECT_ROOT/thirdparty/CopiMineMods.sha1" ]]; then
    local expected_sha1
    expected_sha1="$(python3 -c "from pathlib import Path; print(Path(r'$PROJECT_ROOT/thirdparty/CopiMineMods.sha1').read_text(encoding='utf-8-sig').strip())")"
    [[ -n "$expected_sha1" ]] || fail "thirdparty/CopiMineMods.sha1 is empty"
    [[ "$(sha1_file "$PROJECT_ROOT/thirdparty/CopiMineMods.zip")" == "$expected_sha1" ]] || fail "Modpack SHA1 mismatch"
  fi
}

wait_for_service() {
  local service="$1"
  local timeout="${2:-60}"
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

http_check() {
  local url="$1"
  local host_header="${2:-}"
  local extra=()
  [[ -n "$host_header" ]] && extra=(-H "Host: $host_header")
  curl -fsS "${extra[@]}" "$url" >/dev/null || fail "HTTP check failed: $url"
}

verify_runtime() {
  log "[14/14] Start services and run health checks"
  restart_services
  for service in "${SERVICES[@]}"; do
    if systemctl list-unit-files | grep -q "^${service}\.service"; then
      wait_for_service "$service" 90 || fail "Service failed to become active: $service"
    fi
  done

  if systemctl list-unit-files | grep -q '^nginx\.service'; then
    wait_for_service nginx 30 || fail "nginx is not active"
  fi

  http_check "http://127.0.0.1:8090/api/health"
  http_check "http://127.0.0.1:8090/api/runtime"
  http_check "http://127.0.0.1:18080/downloads/CopiMineMods.zip" "copimine.ru:18080"
  http_check "http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip" "copimine.ru:18080"
  log "Install verification passed."
}

print_summary() {
  log "DONE"
  log "Project root: $PROJECT_ROOT"
  log "Archive: $ARCHIVE_PATH"
  log "Previous live tree backup: $ACTIVE_RELEASE_BACKUP"
  log "Runtime preserve dir: $PRESERVE_ROOT"
  if [[ -f "$PROJECT_ROOT/deploy/release_manifest.json" ]]; then
    log "Release manifest:"
    sed 's/^/[manifest] /' "$PROJECT_ROOT/deploy/release_manifest.json" | tee -a "$LOG_FILE" >/dev/null
  fi
}

main() {
  preflight_system
  validate_archive
  prepare_temp_dirs
  extract_archive
  detect_payload_root
  snapshot_runtime_state
  stop_services
  stage_payload
  atomic_swap
  restore_database
  fix_permissions
  install_system_files
  validate_release_tree
  verify_runtime
  print_summary
}

main "$@"
