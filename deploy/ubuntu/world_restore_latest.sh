#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Restore the newest complete world snapshot. The helper is intentionally
# root-only and requires an explicit confirmation because it overwrites the
# live world directories. A fresh pre-restore snapshot is created first.
ROOT="${COPIMINE_ROOT:-/opt/copimine}"
SERVER_DIR="$ROOT/minecraft/server"
BACKUP_ROOT="${COPIMINE_WORLD_BACKUP_ROOT:-/opt/copimine-backups/worlds}"
LOCK_FILE="${COPIMINE_WORLD_BACKUP_LOCK:-/opt/copimine-backups/.world-backup.lock}"
WORLD_BACKUP_SCRIPT="$ROOT/deploy/ubuntu/world_backup.sh"
CONFIRM="${COPIMINE_CONFIRM_WORLD_RESTORE:-}"
was_active=0
stopped=0
started=0

log() { printf '[copimine-world-restore][%s] %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail 'run as root'
[[ "$CONFIRM" == 'YES' ]] || fail 'set COPIMINE_CONFIRM_WORLD_RESTORE=YES'
for command in awk date find flock head realpath rsync sort stat systemctl; do
  command -v "$command" >/dev/null 2>&1 || fail "missing command: $command"
done
[[ -d "$SERVER_DIR" ]] || fail "missing server directory: $SERVER_DIR"
[[ -d "$BACKUP_ROOT" ]] || fail "missing world backup directory: $BACKUP_ROOT"
[[ -x "$WORLD_BACKUP_SCRIPT" ]] || fail "missing world backup helper: $WORLD_BACKUP_SCRIPT"

cleanup() {
  local status=$?
  if (( stopped == 1 && was_active == 1 && started == 0 )); then
    systemctl start copimine-minecraft || log 'WARNING: Minecraft service could not be restarted automatically'
  fi
  exit "$status"
}
trap cleanup EXIT

exec 9>"$LOCK_FILE"
flock -x 9

latest_name="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'worlds-*' -printf '%f\n' | sort -r | head -n 1)"
[[ "$latest_name" =~ ^worlds-[0-9]{8}-[0-9]{6}$ ]] || fail 'no complete world snapshot found'
latest_path="$BACKUP_ROOT/$latest_name"
resolved_latest="$(realpath -e -- "$latest_path")"
[[ "$resolved_latest" == "$BACKUP_ROOT/"* ]] || fail 'latest snapshot escaped the backup directory'
[[ -f "$resolved_latest/manifest.txt" ]] || fail 'latest snapshot manifest is missing'

mapfile -t world_names < <(awk -F= '$1=="world" {print $2}' "$resolved_latest/manifest.txt")
(( ${#world_names[@]} > 0 )) || fail 'latest snapshot contains no declared worlds'
for world_name in "${world_names[@]}"; do
  [[ "$world_name" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe world name in manifest: $world_name"
  source_path="$resolved_latest/$world_name"
  [[ -d "$source_path" ]] || fail "snapshot world is missing: $world_name"
  [[ -z "$(find "$source_path" -type l -print -quit)" ]] || fail "snapshot world contains a symlink: $world_name"
done

# Keep the chosen source path before making the safety snapshot: the new
# pre-restore snapshot must not become the restore source by accident.
COPIMINE_WORLD_BACKUP_REASON=pre-restore \
COPIMINE_WORLD_BACKUP_REQUIRED=1 \
COPIMINE_WORLD_BACKUP_PRESERVE_SNAPSHOT="$latest_name" \
  "$WORLD_BACKUP_SCRIPT" >/dev/null
[[ -d "$resolved_latest" ]] || fail 'restore source disappeared after safety snapshot'

if systemctl is-active --quiet copimine-minecraft; then
  was_active=1
  systemctl stop copimine-minecraft
  stopped=1
  systemctl is-active --quiet copimine-minecraft && fail 'Minecraft service is still active'
fi

for world_name in "${world_names[@]}"; do
  source_path="$resolved_latest/$world_name"
  target_path="$SERVER_DIR/$world_name"
  if [[ -e "$target_path" || -L "$target_path" ]]; then
    resolved_target="$(realpath -e -- "$target_path")"
  else
    resolved_target="$(realpath -m -- "$target_path")"
  fi
  [[ "$resolved_target" == "$SERVER_DIR/"* ]] || fail "restore target escaped server directory: $target_path"
  [[ ! -L "$target_path" ]] || fail "restore target is a symlink: $target_path"
  mkdir -p "$target_path"
  nice -n 19 ionice -c 3 rsync --archive --delete --numeric-ids "$source_path/" "$target_path/"
  log "restored world: $world_name"
done

if (( was_active == 1 )); then
  systemctl start copimine-minecraft
  started=1
  systemctl is-active --quiet copimine-minecraft || fail 'Minecraft service did not start after restore'
fi
log "restore completed from $resolved_latest"
