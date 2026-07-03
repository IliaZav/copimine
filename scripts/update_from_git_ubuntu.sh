#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run this script with sudo."
  exit 1
fi

APP_USER="${APP_USER:-qwerty}"
REPO_DIR="${REPO_DIR:-/opt/copimine}"
BRANCH="${BRANCH:-main}"
MC_SERVICE="${MC_SERVICE:-copimine-minecraft}"
WEB_SERVICE="${WEB_SERVICE:-copimine-admin}"
BOT_SERVICE="${BOT_SERVICE:-copimine-discord-bot}"
SERVER_PROPERTIES="$REPO_DIR/minecraft/server/server.properties"
RESOURCEPACK_BUILD="$REPO_DIR/resourcepacks/build/CopiMineResourcePack.zip"
TAB_CONFIG="$REPO_DIR/minecraft/server/plugins/TAB/config.yml"

run_as_app() {
  runuser -u "$APP_USER" -- "$@"
}

require_path() {
  local path="$1"
  if [[ ! -e "$path" ]]; then
    echo "Missing required path: $path"
    exit 1
  fi
}

echo "[1/8] Checking paths"
require_path "$REPO_DIR/.git"
require_path "$REPO_DIR/resourcepacks/build-resourcepack.py"
require_path "$REPO_DIR/minecraft/server/plugins"

echo "[2/8] Stopping services"
systemctl stop "$BOT_SERVICE" 2>/dev/null || true
systemctl stop "$WEB_SERVICE" 2>/dev/null || true
systemctl stop "$MC_SERVICE" 2>/dev/null || true

echo "[3/8] Syncing repository to origin/$BRANCH"
run_as_app git -C "$REPO_DIR" fetch --all --prune
run_as_app git -C "$REPO_DIR" checkout "$BRANCH"
run_as_app git -C "$REPO_DIR" reset --hard "origin/$BRANCH"
run_as_app git -C "$REPO_DIR" clean -fd

echo "[4/8] Copying plugin jars"
declare -a plugin_jars=(
  "copimine-admin-plugin/CopiMineUltimateAdminPlus.jar"
  "copimine-artifacts/CopiMineArtifacts.jar"
  "copimine-economy-core/CopiMineEconomyCore.jar"
  "copimine-election-core/CopiMineElectionCore.jar"
  "copimine-narcotics/CopiMineNarcotics.jar"
  "copimine-world-core/CopiMineWorldCore.jar"
)

for rel in "${plugin_jars[@]}"; do
  src="$REPO_DIR/$rel"
  require_path "$src"
  cp -f "$src" "$REPO_DIR/minecraft/server/plugins/"
done

echo "[5/8] Rebuilding resource pack"
run_as_app python3 "$REPO_DIR/resourcepacks/build-resourcepack.py"
require_path "$RESOURCEPACK_BUILD"

RESOURCEPACK_SHA1="$(sha1sum "$RESOURCEPACK_BUILD" | awk '{print $1}')"
python3 - "$SERVER_PROPERTIES" "$RESOURCEPACK_SHA1" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
sha1 = sys.argv[2]
text = path.read_text(encoding="utf-8")
lines = text.splitlines()
updated = False
for idx, line in enumerate(lines):
    if line.startswith("resource-pack-sha1="):
        lines[idx] = f"resource-pack-sha1={sha1}"
        updated = True
        break
if not updated:
    lines.append(f"resource-pack-sha1={sha1}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

echo "[6/8] Patching TAB header banner"
mkdir -p "$(dirname "$TAB_CONFIG")"
python3 - "$TAB_CONFIG" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
if not path.exists():
    raise SystemExit(0)

text = path.read_text(encoding="utf-8")
lines = text.splitlines()

header_line = '      - "&f\\uE300"'
president_line = '      - "&7\\u041f\\u0440\\u0435\\u0437\\u0438\\u0434\\u0435\\u043d\\u0442: &6%copimine_president%"'
online_line = '      - "&7\\u041e\\u043d\\u043b\\u0430\\u0439\\u043d: &f%online% &8| &7\\u0410\\u0434\\u043c\\u0438\\u043d\\u043e\\u0432: &f%staffonline%"'
player_line = '      - "&f%player% &8| &7Ping: &a%ping% ms"'
footer_line = '      - "&7TPS: &f%tps% &8| &7MSPT: &f%mspt% &8| &7Ping: &f%ping% ms"'

for idx, line in enumerate(lines):
    stripped = line.strip()
    if stripped in {'- "&f\\uE300"', '- "&f\\ue300"'}:
        lines[idx] = header_line
    elif "%copimine_president%" in line:
        lines[idx] = president_line
    elif "%online%" in line and "%staffonline%" in line:
        lines[idx] = online_line
    elif "%player%" in line and "%ping%" in line:
        lines[idx] = player_line
    elif "%tps%" in line and "%mspt%" in line:
        lines[idx] = footer_line

text = "\n".join(lines) + "\n"
if header_line not in text and "header:" in text:
    text = text.replace("      - \"\"\n", "      - \"\"\n" + header_line + "\n", 1)

path.write_text(text, encoding="utf-8")
PY

echo "[7/8] Starting services"
systemctl daemon-reload
systemctl start "$WEB_SERVICE"
systemctl start "$BOT_SERVICE" 2>/dev/null || true
systemctl start "$MC_SERVICE"
sleep 8

echo "[8/8] Status"
echo "Git commit: $(run_as_app git -C "$REPO_DIR" rev-parse --short HEAD)"
echo "Resource pack SHA1: $RESOURCEPACK_SHA1"
echo
systemctl --no-pager --full status "$WEB_SERVICE" | sed -n '1,18p' || true
echo
systemctl --no-pager --full status "$BOT_SERVICE" | sed -n '1,18p' || true
echo
systemctl --no-pager --full status "$MC_SERVICE" | sed -n '1,18p' || true
