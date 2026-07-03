#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/opt/copimine}"
SERVER_DIR="${SERVER_DIR:-$ROOT_DIR/minecraft/server}"
SERVER_PROPERTIES="$SERVER_DIR/server.properties"
SEED="${SEED:--1861153001556076901}"
MC_SERVICE="${MC_SERVICE:-copimine-minecraft}"

WORLD_DIRS=(
  "world"
  "world_nether"
  "world_the_end"
  "world_test"
  "world_test_nether"
  "world_test_the_end"
  "worldTestCP"
  "worldTestCP_nether"
  "worldTestCP_the_end"
  "CopiMine"
  "CopiMine_nether"
  "CopiMine_the_end"
)

require_path() {
  local path="$1"
  if [[ ! -e "$path" ]]; then
    echo "Missing required path: $path" >&2
    exit 1
  fi
}

rewrite_seed() {
  python3 - "$SERVER_PROPERTIES" "$SEED" <<'PY'
from pathlib import Path
import sys

props = Path(sys.argv[1])
seed = sys.argv[2]
lines = props.read_text(encoding="utf-8").splitlines()
updated = False
for idx, line in enumerate(lines):
    if line.startswith("level-seed="):
        lines[idx] = f"level-seed={seed}"
        updated = True
        break
if not updated:
    lines.append(f"level-seed={seed}")
props.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
}

stop_minecraft() {
  if systemctl list-unit-files | grep -q "^${MC_SERVICE}\.service"; then
    systemctl stop "$MC_SERVICE"
  fi
}

start_minecraft() {
  if systemctl list-unit-files | grep -q "^${MC_SERVICE}\.service"; then
    systemctl start "$MC_SERVICE"
  fi
}

wipe_worlds() {
  for world in "${WORLD_DIRS[@]}"; do
    local target="$SERVER_DIR/$world"
    if [[ -d "$target" ]]; then
      rm -rf -- "$target"
      echo "Removed world directory: $target"
    fi
  done
}

main() {
  require_path "$SERVER_DIR"
  require_path "$SERVER_PROPERTIES"
  rewrite_seed
  stop_minecraft
  wipe_worlds
  start_minecraft
  echo "World wipe complete."
  echo "Seed forced to: $SEED"
}

main "$@"
