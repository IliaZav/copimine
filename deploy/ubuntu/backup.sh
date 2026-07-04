#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
backup_path="$(copimine_backup_snapshot "${1:-}")"
copimine_log "Backup created: $backup_path"
