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
copimine_verify_public_endpoints
public_panel_url="$(copimine_env_value PUBLIC_PANEL_URL)"
modpack_url="${public_panel_url%/}/downloads/CopiMineMods.zip"
resourcepack_url="${public_panel_url%/}/resourcepacks/CopiMineResourcePack.zip"
curl -fsSI "$modpack_url" >/dev/null || copimine_fail "modpack download route failed"
curl -fsSI "$resourcepack_url" >/dev/null || copimine_fail "resourcepack download route failed"
tmp_modpack="$(mktemp /tmp/copimine-modpack-XXXXXX.zip)"
tmp_resourcepack="$(mktemp /tmp/copimine-resourcepack-XXXXXX.zip)"
trap 'rm -f "$tmp_modpack" "$tmp_resourcepack"' EXIT
curl -fsS "$modpack_url" -o "$tmp_modpack" || copimine_fail "modpack payload download failed"
curl -fsS "$resourcepack_url" -o "$tmp_resourcepack" || copimine_fail "resourcepack payload download failed"
local_modpack_sha="$(sha256sum "$COPIMINE_ROOT/thirdparty/CopiMineMods.zip" | awk '{print $1}')"
remote_modpack_sha="$(sha256sum "$tmp_modpack" | awk '{print $1}')"
[[ "$local_modpack_sha" == "$remote_modpack_sha" ]] || copimine_fail "Served modpack SHA256 mismatch. Runtime=$local_modpack_sha download=$remote_modpack_sha"
local_resourcepack_sha="$(sha256sum "$COPIMINE_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
remote_resourcepack_sha="$(sha256sum "$tmp_resourcepack" | awk '{print $1}')"
[[ "$local_resourcepack_sha" == "$remote_resourcepack_sha" ]] || copimine_fail "Served resourcepack SHA256 mismatch. Runtime=$local_resourcepack_sha download=$remote_resourcepack_sha"
copimine_verify_runtime
copimine_log "Verify complete"
