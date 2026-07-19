#!/usr/bin/env bash
set -Eeuo pipefail

COPIMINE_ROOT="${COPIMINE_ROOT:-/opt/copimine}"
COMMON_SCRIPT="$COPIMINE_ROOT/deploy/shared/common.sh"

[[ -f "$COMMON_SCRIPT" ]] || {
  printf '%s\n' "CopiMine game hardening: missing shared deployment helper: $COMMON_SCRIPT" >&2
  exit 1
}

# shellcheck source=/dev/null
source "$COMMON_SCRIPT"

copimine_require_root
copimine_sync_game_runtime_hardening
copimine_fix_runtime_plugin_ownership
copimine_apply_post_start_game_hardening
