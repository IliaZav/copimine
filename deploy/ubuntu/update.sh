#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_log "Updating runtime"
copimine_require_root
copimine_stop_services
copimine_install_flow
copimine_start_services
copimine_verify_runtime
copimine_log "Update flow complete"
