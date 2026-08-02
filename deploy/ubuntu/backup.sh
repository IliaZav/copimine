#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
stamp="$(date +%Y%m%d-%H%M%S)"
backup_stem="${1:-copimine-daily-$stamp}"
backup_stem="${backup_stem%.tar.gz}"
[[ "$backup_stem" =~ ^[A-Za-z0-9._-]+$ ]] || copimine_fail "Unsafe backup name: $backup_stem"
runtime_path="$(copimine_backup_snapshot "${backup_stem}.tar.gz")"
if ! database_path="$(copimine_backup_database "$backup_stem")"; then
  rm -f -- "$runtime_path" "${runtime_path}.sha256"
  exit 1
fi
manifest_path="$COPIMINE_BACKUP_DIR/${backup_stem}.manifest"
{
  printf 'created_at=%s\n' "$(date --iso-8601=seconds)"
  printf 'runtime_archive=%s\n' "$(basename "$runtime_path")"
  printf 'database_dump=%s\n' "$(basename "$database_path")"
  printf 'git_commit=%s\n' "$(git -C "$COPIMINE_ROOT" rev-parse --short HEAD 2>/dev/null || true)"
} > "$manifest_path"
chmod 600 "$manifest_path"
copimine_prune_daily_backups 3
copimine_log "Backup created: $runtime_path and $database_path"
