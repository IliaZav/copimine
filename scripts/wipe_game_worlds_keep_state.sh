#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$ROOT/deploy/shared/common.sh"

copimine_require_root
copimine_require_path "$COPIMINE_SERVER_DIR"
copimine_require_path "$COPIMINE_SERVER_PROPERTIES"
copimine_stop_services
copimine_wipe_worlds
copimine_start_services
copimine_log "World wipe complete"
copimine_log "Seed forced to: $COPIMINE_WORLD_SEED"
