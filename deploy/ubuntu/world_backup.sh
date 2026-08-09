#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Make a point-in-time copy of the live worlds without stopping the server.
# save-off prevents chunk files from changing while rsync reads them; the
# server keeps ticking, and rsync itself runs at idle I/O priority.  Each
# completed snapshot is published with one atomic rename and unchanged files
# are hard-linked from the previous snapshot to keep the five-hour rotation
# cheap on disk.

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
SERVER_DIR="$ROOT/minecraft/server"
BACKUP_ROOT="${COPIMINE_WORLD_BACKUP_ROOT:-/opt/copimine-backups/worlds}"
LOCK_FILE="${COPIMINE_WORLD_BACKUP_LOCK:-/opt/copimine-backups/.world-backup.lock}"
RETENTION_SECONDS="${COPIMINE_WORLD_BACKUP_RETENTION_SECONDS:-43200}"
REASON="${COPIMINE_WORLD_BACKUP_REASON:-scheduled}"
REQUIRED="${COPIMINE_WORLD_BACKUP_REQUIRED:-0}"
TS="$(date -u +%Y%m%d-%H%M%S)"
SNAPSHOT_NAME="worlds-$TS"
TEMP_PATH="$BACKUP_ROOT/.$SNAPSHOT_NAME.tmp"
FINAL_PATH="$BACKUP_ROOT/$SNAPSHOT_NAME"
SAVE_OFF=0

log() { printf '[copimine-world-backup][%s] %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

for command in awk date find flock ionice nice realpath rsync stat; do
  command -v "$command" >/dev/null 2>&1 || fail "missing command: $command"
done
[[ -d "$SERVER_DIR" ]] || fail "missing server directory: $SERVER_DIR"
[[ "$RETENTION_SECONDS" =~ ^[0-9]+$ ]] || fail "retention must be numeric"
[[ "$REASON" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe backup reason"

mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
exec 9>"$LOCK_FILE"
if [[ "$REQUIRED" == "1" ]]; then
  flock -x 9
else
  flock -n 9 || { log 'another world backup is already running; this run is skipped'; exit 0; }
fi

configured_world="$(awk -F= '$1=="level-name" {print substr($0,index($0,"=")+1); exit}' "$SERVER_DIR/server.properties" | tr -d '\r')"
[[ "$configured_world" =~ ^[A-Za-z0-9._-]+$ ]] || fail 'invalid level-name'

world_names=()
declare -A seen_worlds=()
while IFS= read -r world_name; do
  [[ -n "$world_name" ]] || continue
  [[ -n "${seen_worlds[$world_name]:-}" ]] && continue
  seen_worlds[$world_name]=1
  world_names+=("$world_name")
done < <(printf '%s\n' "$configured_world" "${configured_world}_nether" "${configured_world}_the_end" world world_nether world_the_end)
while IFS= read -r -d '' world_path; do
  world_name="$(basename "$world_path")"
  [[ -n "${seen_worlds[$world_name]:-}" ]] && continue
  seen_worlds[$world_name]=1
  world_names+=("$world_name")
done < <(find "$SERVER_DIR" -mindepth 1 -maxdepth 1 -type d -name 'paper-world*' -print0)

world_paths=()
for world_name in "${world_names[@]}"; do
  source_path="$SERVER_DIR/$world_name"
  [[ -d "$source_path" ]] || continue
  resolved="$(realpath -e -- "$source_path")"
  [[ "$resolved" == "$SERVER_DIR/"* && "$resolved" != "$SERVER_DIR" ]] \
    || fail "world path escapes server directory: $resolved"
  [[ -z "$(find "$resolved" -type l -print -quit)" ]] \
    || fail "world contains a symlink; refusing unsafe backup: $resolved"
  world_paths+=("$resolved")
done
(( ${#world_paths[@]} > 0 )) || fail 'no world directories were found'

read_property() {
  local key="$1"
  awk -F= -v wanted="$key" '$1==wanted {print substr($0,index($0,"=")+1); exit}' \
    "$SERVER_DIR/server.properties" | tr -d '\r'
}

rcon_enabled="$(read_property enable-rcon)"
rcon_host="$(read_property rcon.ip)"
rcon_port="$(read_property rcon.port)"
rcon_password="$(read_property rcon.password)"
[[ "$rcon_enabled" == 'true' ]] || fail 'RCON is disabled; cannot create a consistent live snapshot'
[[ "$rcon_host" =~ ^[A-Za-z0-9_.:-]+$ ]] || fail 'invalid RCON host'
[[ "$rcon_port" =~ ^[0-9]+$ ]] || fail 'invalid RCON port'
[[ -n "$rcon_password" && "$rcon_password" != __COPIMINE_RCON_PASSWORD_AT_INSTALL__ ]] \
  || fail 'RCON password is not configured'
command -v mcrcon >/dev/null 2>&1 || fail 'missing command: mcrcon'

run_rcon() {
  MCRCON_HOST="$rcon_host" MCRCON_PORT="$rcon_port" MCRCON_PASS="$rcon_password" \
    mcrcon -s "$@" >/dev/null
}

cleanup() {
  local status=$?
  if (( SAVE_OFF == 1 )); then
    run_rcon save-on || log 'WARNING: could not re-enable world saving'
    run_rcon 'save-all flush' || log 'WARNING: could not flush world after backup'
  fi
  if [[ -d "$TEMP_PATH" ]]; then
    rm -rf -- "$TEMP_PATH"
  fi
  exit "$status"
}
trap cleanup EXIT

run_rcon save-off
SAVE_OFF=1
run_rcon 'save-all flush'
log "saving disabled only for the file copy; server keeps ticking (reason=$REASON)"

latest_snapshot=""
while IFS= read -r candidate; do
  [[ -n "$candidate" ]] || continue
  latest_snapshot="$candidate"
  break
done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'worlds-*' -printf '%f\n' | sort -r)

rm -rf -- "$TEMP_PATH"
mkdir -p "$TEMP_PATH"
chmod 700 "$TEMP_PATH"
{
  printf 'created_at=%s\n' "$(date --iso-8601=seconds)"
  printf 'reason=%s\n' "$REASON"
  printf 'source=%s\n' "$SERVER_DIR"
  printf 'retention_seconds=%s\n' "$RETENTION_SECONDS"
  printf 'consistency=save-off-save-all-flush\n'
} > "$TEMP_PATH/manifest.txt"

for source_path in "${world_paths[@]}"; do
  world_name="$(basename "$source_path")"
  destination="$TEMP_PATH/$world_name"
  mkdir -p "$destination"
  link_args=()
  if [[ -n "$latest_snapshot" && -d "$BACKUP_ROOT/$latest_snapshot/$world_name" ]]; then
    link_args+=("--link-dest=$BACKUP_ROOT/$latest_snapshot/$world_name")
  fi
  nice -n 19 ionice -c 3 rsync --archive --delete --numeric-ids "${link_args[@]}" \
    "$source_path/" "$destination/"
  find "$destination" -type f -printf '%P\t%s\t%T@\n' | sort \
    >> "$TEMP_PATH/manifest.txt"
done

mv -- "$TEMP_PATH" "$FINAL_PATH"
log "published snapshot: $FINAL_PATH"

now="$(date +%s)"
while IFS= read -r snapshot_name; do
  [[ -n "$snapshot_name" ]] || continue
  [[ "$snapshot_name" =~ ^worlds-[0-9]{8}-[0-9]{6}$ ]] || fail "unexpected snapshot name: $snapshot_name"
  [[ "$snapshot_name" == "$SNAPSHOT_NAME" ]] && continue
  snapshot_path="$BACKUP_ROOT/$snapshot_name"
  snapshot_mtime="$(stat -c %Y -- "$snapshot_path")"
  if (( now - snapshot_mtime > RETENTION_SECONDS )); then
    resolved_snapshot="$(realpath -e -- "$snapshot_path")"
    [[ "$resolved_snapshot" == "$BACKUP_ROOT/"* ]] || fail "retention target escaped backup root"
    rm -rf -- "$resolved_snapshot"
    log "expired snapshot removed: $resolved_snapshot"
  fi
done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'worlds-*' -printf '%f\n' | sort -r)
