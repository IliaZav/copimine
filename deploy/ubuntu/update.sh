#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_log "Updating runtime"
copimine_require_root
update_started=0
cleanup_update() {
  local code=$?
  if [[ "$update_started" -eq 1 ]]; then
    copimine_start_services || copimine_log "ERROR: failed to restart services after update (exit=$code)"
  fi
  exit "$code"
}
trap cleanup_update EXIT
copimine_stop_services
update_started=1
copimine_install_flow
copimine_start_services
copimine_verify_runtime
update_started=0
copimine_log "Update flow complete"
