#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'bed respawn patch failed: %s\n' "$*" >&2
  exit 1
}

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail 'run as root'

project_root="${COPIMINE_ROOT:-/opt/copimine}"
config="$project_root/minecraft/server/plugins/Essentials/config.yml"
backup_root="${COPIMINE_BACKUP_ROOT:-/opt/copimine-backups}"
[[ -f "$config" ]] || fail "Essentials config is missing: $config"
case "$(realpath -e -- "$config")" in
  "$project_root"/*) ;;
  *) fail "refusing config outside CopiMine root: $config" ;;
esac

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$backup_root/bed-respawn-$timestamp"
install -d -m 0750 -- "$backup_dir"
cp -a -- "$config" "$backup_dir/config.yml"

python3 - "$config" <<'PY'
from pathlib import Path
import os
import sys
import tempfile

path = Path(sys.argv[1])
updates = {
    "respawn-listener-priority": "none",
    "respawn-at-home": "false",
    "respawn-at-home-bed": "true",
    "random-respawn-location": '"none"',
    "random-spawn-location": '"none"',
}

lines = path.read_text(encoding="utf-8-sig").splitlines()
output = []
seen = set()
for line in lines:
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and ":" in stripped:
        key = stripped.split(":", 1)[0].strip()
        if key in updates:
            indent = line[: len(line) - len(line.lstrip())]
            output.append(f"{indent}{key}: {updates[key]}")
            seen.add(key)
            continue
    output.append(line)
for key, value in updates.items():
    if key not in seen:
        output.append(f"{key}: {value}")

fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
try:
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(output).rstrip("\n") + "\n")
    os.chmod(temporary, path.stat().st_mode & 0o777)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY

grep -Fqx -- 'respawn-listener-priority: none' "$config" || fail 'Essentials respawn listener was not disabled'
grep -Fqx -- 'respawn-at-home: false' "$config" || fail 'Essentials home respawn was not disabled'
grep -Fqx -- 'respawn-at-home-bed: true' "$config" || fail 'respawn-at-home-bed was not enabled'
grep -Fqx -- 'random-respawn-location: "none"' "$config" || fail 'random respawn override was not disabled'

/usr/bin/systemctl restart copimine-minecraft
for attempt in $(seq 1 60); do
  if /usr/bin/systemctl is-active --quiet copimine-minecraft; then
    printf 'bed respawn patch installed: backup=%s\n' "$backup_dir"
    exit 0
  fi
  sleep 1
done
fail 'copimine-minecraft did not become active'
