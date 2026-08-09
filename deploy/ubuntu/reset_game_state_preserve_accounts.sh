#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
ADMIN_DIR="$ROOT/admin-web"
ENV_FILE="$ADMIN_DIR/.env"
SERVER_DIR="$ROOT/minecraft/server"
BACKUP_ROOT="${COPIMINE_BACKUP_ROOT:-/opt/copimine-backups}"
SQL_FILE="$ROOT/db/runtime/reset_game_state_preserve_accounts.sql"
SERVICES=(copimine-admin copimine-discord-bot copimine-minecraft-discord-bridge copimine-minecraft copimine-game-hardening)
WIPE_WORLDS=0
LOG_FILE="/var/log/copimine-game-wipe.log"
WORLD_BACKUP_SCRIPT="$ROOT/deploy/ubuntu/world_backup.sh"
WORLD_BACKUP_LOCK="${COPIMINE_WORLD_BACKUP_LOCK:-$BACKUP_ROOT/.world-backup.lock}"
world_snapshot_path=""

log() { printf '[copimine-wipe][%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" | tee -a "$LOG_FILE"; }
fail() { log "ERROR: $*"; exit 1; }
[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail "запускайте через sudo/root"
[[ "${COPIMINE_CONFIRM_GAME_WIPE:-}" == "YES" ]] || fail "для подтверждения задайте COPIMINE_CONFIRM_GAME_WIPE=YES"
[[ -f "$ENV_FILE" ]] || fail "не найден $ENV_FILE"
[[ -f "$SQL_FILE" ]] || fail "не найден $SQL_FILE"
[[ -d "$SERVER_DIR" ]] || fail "не найден каталог сервера $SERVER_DIR"
[[ "${1:-}" != "--wipe-worlds" ]] || WIPE_WORLDS=1
for command in systemctl runuser psql pg_dump sha256sum awk sed find realpath flock cmp cp; do
  command -v "$command" >/dev/null 2>&1 || fail "не найдена команда $command"
done

read_env() {
  local key="$1" default="$2" value
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)"
  value="${value%$'\r'}"
  printf '%s' "${value:-$default}"
}

PG_DB="$(read_env POSTGRES_DB copimine)"
PG_SCHEMA="$(read_env POSTGRES_SCHEMA copimine)"
[[ "$PG_DB" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "некорректное имя базы"
[[ "$PG_SCHEMA" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "некорректная схема"

mkdir -p "$BACKUP_ROOT"
if (( WIPE_WORLDS == 1 )); then
  [[ -x "$WORLD_BACKUP_SCRIPT" ]] || fail "world wipe requires world_backup.sh"
  pending_world_backup="$(mktemp -p /tmp 'copimine-game-wipe-worlds.XXXXXX')" \
    || fail "could not create world-backup marker"
  if ! COPIMINE_WORLD_BACKUP_REASON=pre-wipe \
    COPIMINE_WORLD_BACKUP_REQUIRED=1 \
    "$WORLD_BACKUP_SCRIPT" > "$pending_world_backup"; then
    rm -f -- "$pending_world_backup"
    fail "world snapshot failed before wipe"
  fi
  world_snapshot_path="$(awk -F'published snapshot: ' '/published snapshot: / {print $2; exit}' "$pending_world_backup")"
  [[ -n "$world_snapshot_path" ]] || {
    rm -f -- "$pending_world_backup"
    fail "world snapshot path was not reported before wipe"
  }
fi

exec 9>"$WORLD_BACKUP_LOCK"
flock -x 9

backup_dir="$BACKUP_ROOT/game-wipe-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
chmod 700 "$BACKUP_ROOT" "$backup_dir"
if (( WIPE_WORLDS == 1 )); then
  install -m 600 "$pending_world_backup" "$backup_dir/world-backup.txt"
  world_snapshot_resolved="$(realpath -e -- "$world_snapshot_path")"
  world_backup_root_resolved="$(realpath -e -- "$BACKUP_ROOT/worlds")"
  [[ "$world_snapshot_resolved" == "$world_backup_root_resolved/worlds-"* ]] \
    || fail "pre-wipe snapshot escaped the world backup root: $world_snapshot_resolved"
  [[ "$(basename -- "$world_snapshot_resolved")" =~ ^worlds-[0-9]{8}-[0-9]{6}$ ]] \
    || fail "pre-wipe snapshot has an invalid name: $world_snapshot_resolved"
  mkdir -p "$backup_dir/worlds-pre-wipe"
  cp -al -- "$world_snapshot_resolved/." "$backup_dir/worlds-pre-wipe/"
  chmod -R a-w "$backup_dir/worlds-pre-wipe"
  log "durable pre-wipe world snapshot saved as hardlinks: $backup_dir/worlds-pre-wipe"
  rm -f -- "$pending_world_backup"
fi
log "резервная копия базы: $backup_dir/copimine-before-wipe.dump"
tmp_dump="$(mktemp -p /tmp 'copimine-before-wipe.XXXXXX.dump')" || fail "не удалось подготовить временный дамп"
rm -f -- "$tmp_dump"
if ! runuser -u postgres -- pg_dump --format=custom --no-owner --no-acl -d "$PG_DB" -f "$tmp_dump"; then
  rm -f -- "$tmp_dump"
  fail "резервная копия базы не создана"
fi
install -m 600 "$tmp_dump" "$backup_dir/copimine-before-wipe.dump" || {
  rm -f -- "$tmp_dump"
  fail "не удалось сохранить бэкап базы"
}
rm -f -- "$tmp_dump"
[[ -s "$backup_dir/copimine-before-wipe.dump" ]] || fail "дамп базы пустой"
sha256sum "$backup_dir/copimine-before-wipe.dump" > "$backup_dir/copimine-before-wipe.dump.sha256"

scalar() {
  runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -d "$PG_DB" -Atqc "$1" | tr -d '[:space:]'
}
protected_tables=(
  site_accounts player_web_accounts cm_admin_users cm_admin_sessions
  cm_refresh_sessions minecraft_account_links cmv4_account_links
  whitelist_account_links whitelist_requests auth_users_imported
  auth_migration_state auth_whitelist_sync site_cms_entries
  password_hashes bank_pin_hashes bank_account_pins
  ar_settings artifact_items_catalog narcotics_schema_version
  narcotics_config_values
)
record_protected_counts() {
  local output="$1" table count
  : > "$output"
  for table in "${protected_tables[@]}"; do
    count="$(scalar "SELECT count(*) FROM \"$PG_SCHEMA\".\"$table\"")"
    printf '%s=%s\n' "$table" "$count" >> "$output"
  done
}
protected_before="$backup_dir/protected-counts.before"
record_protected_counts "$protected_before"
accounts_before="$(awk -F= '$1=="site_accounts" {print $2}' "$protected_before")"
whitelist_before="$(awk -F= '$1=="whitelist_account_links" {print $2}' "$protected_before")"
log "сохраняемые записи до вайпа: аккаунты=$accounts_before whitelist=$whitelist_before"

for service in "${SERVICES[@]}"; do
  if systemctl list-unit-files | grep -q "^${service}\.service"; then
    if systemctl is-active --quiet "$service"; then
      log "остановка $service"
      systemctl stop "$service" || fail "не удалось остановить $service"
      systemctl is-active --quiet "$service" && fail "$service всё ещё работает"
    fi
  fi
done

log "очистка игровых таблиц (аккаунты и whitelist не затрагиваются)"
runuser -u postgres -- psql -X -v ON_ERROR_STOP=1 -v copimine_schema="$PG_SCHEMA" -d "$PG_DB" -f "$SQL_FILE"

protected_after="$backup_dir/protected-counts.after"
record_protected_counts "$protected_after"
accounts_after="$(awk -F= '$1=="site_accounts" {print $2}' "$protected_after")"
whitelist_after="$(awk -F= '$1=="whitelist_account_links" {print $2}' "$protected_after")"
cmp -s "$protected_before" "$protected_after" || fail "protected identity or configuration rows changed"
[[ "$accounts_before" == "$accounts_after" ]] || fail "изменилось число аккаунтов сайта: $accounts_before -> $accounts_after"
[[ "$whitelist_before" == "$whitelist_after" ]] || fail "изменилось число whitelist-связей: $whitelist_before -> $whitelist_after"

if (( WIPE_WORLDS == 1 )); then
  level_name="$(awk -F= '$1=="level-name" {print substr($0,index($0,"=")+1); exit}' "$SERVER_DIR/server.properties" | tr -d '\r')"
  [[ "$level_name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "level-name содержит недопустимые символы"
  while IFS= read -r world_name; do
    [[ -n "$world_name" ]] || continue
    target="$SERVER_DIR/$world_name"
    if [[ -d "$target" ]]; then
      resolved="$(realpath -e "$target")"
      [[ "$resolved" == "$SERVER_DIR/"* ]] || fail "отказ: путь мира вне каталога сервера: $resolved"
      [[ "$resolved" != "$SERVER_DIR" ]] || fail "отказ: попытка удалить каталог сервера"
      log "удаление мира $resolved (seed и whitelist сохраняются)"
      rm -rf -- "$resolved"
    fi
  done < <(printf '%s\n' "$level_name" "${level_name}_nether" "${level_name}_the_end" world world_nether world_the_end; find "$SERVER_DIR" -mindepth 1 -maxdepth 1 -type d -name 'paper-world*' -printf '%f\n')
fi

for service in "${SERVICES[@]}"; do
  if systemctl list-unit-files | grep -q "^${service}\.service"; then
    log "запуск $service"
    systemctl start "$service" || fail "не удалось запустить $service"
  fi
done

log "готово: аккаунты=$accounts_after whitelist=$whitelist_after backup=$backup_dir"
