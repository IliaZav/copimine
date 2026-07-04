#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
systemctl is-active --quiet copimine-admin || copimine_fail "copimine-admin is not active"
systemctl is-active --quiet copimine-minecraft || copimine_fail "copimine-minecraft is not active"
if systemctl list-unit-files | grep -q '^copimine-discord-bot\.service'; then
  systemctl is-active --quiet copimine-discord-bot || copimine_fail "copimine-discord-bot is not active"
fi
curl -fsS http://127.0.0.1:8090/api/health >/dev/null || copimine_fail "/api/health failed"
curl -fsS http://127.0.0.1:8090/api/runtime >/dev/null || copimine_fail "/api/runtime failed"
curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/downloads/CopiMineMods.zip >/dev/null || copimine_fail "modpack download route failed"
curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip >/dev/null || copimine_fail "resourcepack download route failed"
copimine_verify_runtime
copimine_log "Verify complete"
