#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
archive_path="${1:-}"
[[ -n "$archive_path" ]] || copimine_fail "Usage: rollback.sh /path/to/backup.tar.gz"
copimine_require_path "$archive_path"

copimine_stop_services
rm -rf -- "$COPIMINE_ROOT"
mkdir -p "$(dirname "$COPIMINE_ROOT")"
tar -C "$(dirname "$COPIMINE_ROOT")" -xzf "$archive_path"
copimine_start_services
copimine_verify_runtime
copimine_log "Rollback complete from $archive_path"
