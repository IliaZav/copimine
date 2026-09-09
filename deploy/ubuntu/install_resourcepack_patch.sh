#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'resource pack patch failed: %s\n' "$*" >&2
  exit 1
}

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail 'run as root'
[[ $# -eq 3 ]] || fail "usage: $0 /home/qwerty/copimine-upload/pack.zip /home/qwerty/copimine-upload/pack.sha1 /home/qwerty/copimine-upload/pack.sha256"

staged_zip="$1"
staged_sha1="$2"
staged_sha256="$3"
project_root="${COPIMINE_ROOT:-/opt/copimine}"
pack_dir="$project_root/resourcepacks/build"
server_properties="$project_root/minecraft/server/server.properties"
backup_root='/opt/copimine-backups'

for path in "$staged_zip" "$staged_sha1" "$staged_sha256"; do
  [[ -f "$path" ]] || fail "missing staged file: $path"
  case "$(realpath -e -- "$path")" in
    /home/qwerty/copimine-upload/*) ;;
    *) fail "staged file is outside the upload directory: $path" ;;
  esac
done

[[ -d "$pack_dir" ]] || fail "resource pack directory is missing: $pack_dir"
[[ -f "$server_properties" ]] || fail "server.properties is missing: $server_properties"

actual_sha1="$(sha1sum -- "$staged_zip" | awk '{print $1}')"
actual_sha256="$(sha256sum -- "$staged_zip" | awk '{print $1}')"
declared_sha1="$(tr -d '[:space:]' < "$staged_sha1")"
declared_sha256="$(tr -d '[:space:]' < "$staged_sha256")"
[[ "$declared_sha1" =~ ^[0-9a-fA-F]{40}$ ]] || fail 'invalid SHA1 sidecar'
[[ "$declared_sha256" =~ ^[0-9a-fA-F]{64}$ ]] || fail 'invalid SHA256 sidecar'
[[ "${actual_sha1,,}" == "${declared_sha1,,}" ]] || fail 'SHA1 sidecar does not match ZIP'
[[ "${actual_sha256,,}" == "${declared_sha256,,}" ]] || fail 'SHA256 sidecar does not match ZIP'

unzip -tq -- "$staged_zip" >/dev/null || fail 'resource pack ZIP is invalid'
required_entries=(
  'assets/copimine/models/item/artifacts/repair_kit.json'
  'assets/copimine/textures/item/artifacts/repair_kit.png'
  'assets/copimine/models/item/artifacts/return_stone.json'
  'assets/copimine/textures/item/artifacts/return_stone.png'
  'assets/copimine/models/item/artifacts/infinite_torch.json'
  'assets/copimine/textures/item/artifacts/infinite_torch.png'
  'assets/copimine/models/item/artifacts/gravedigger_contract.json'
  'assets/copimine/textures/item/artifacts/gravedigger_contract.png'
  'assets/copimine/models/item/artifacts/signal_bell.json'
  'assets/copimine/textures/item/artifacts/signal_bell.png'
  'assets/copimine/models/item/artifacts/berserker_heart.json'
  'assets/copimine/textures/item/artifacts/berserker_heart.png'
  'assets/copimine/models/item/artifacts/zhilorez_pickaxe.json'
  'assets/copimine/textures/item/artifacts/zhilorez_pickaxe.png'
  'assets/copimine/models/item/artifacts/night_cloak.json'
  'assets/copimine/textures/item/artifacts/night_cloak.png'
)
for entry in "${required_entries[@]}"; do
  unzip -Z1 -- "$staged_zip" | grep -Fqx -- "$entry" || fail "required pack entry is missing: $entry"
done

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$backup_root/resourcepack-$timestamp"
install -d -m 0750 -- "$backup_dir"
for current in \
  "$pack_dir/CopiMineResourcePack.zip" \
  "$pack_dir/CopiMineResourcePack.sha1" \
  "$pack_dir/CopiMineResourcePack.sha256" \
  "$server_properties"; do
  if [[ -f "$current" ]]; then
    cp -a -- "$current" "$backup_dir/"
  fi
done

install -o root -g root -m 0644 -- "$staged_zip" "$pack_dir/CopiMineResourcePack.zip"
install -o root -g root -m 0644 -- "$staged_sha1" "$pack_dir/CopiMineResourcePack.sha1"
install -o root -g root -m 0644 -- "$staged_sha256" "$pack_dir/CopiMineResourcePack.sha256"

resource_url="https://copimine.ru/resourcepacks/CopiMineResourcePack.zip?v=${actual_sha1:0:12}"
python3 - "$server_properties" "$actual_sha1" "$resource_url" <<'PY'
from pathlib import Path
import os
import sys
import tempfile

path = Path(sys.argv[1])
sha1 = sys.argv[2]
resource_url = sys.argv[3].replace(":", r"\:")
updates = {
    "resource-pack": resource_url,
    "resource-pack-sha1": sha1,
}
lines = path.read_text(encoding="utf-8").splitlines()
output = []
seen = set()
for line in lines:
    if "=" not in line or line.startswith("#"):
        output.append(line)
        continue
    key, _ = line.split("=", 1)
    if key in updates:
        output.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        output.append(line)
for key, value in updates.items():
    if key not in seen:
        output.append(f"{key}={value}")

fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
try:
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
        stream.write("\n".join(output) + "\n")
    os.chmod(temporary, path.stat().st_mode & 0o777)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY

grep -Fqx -- "resource-pack-sha1=$actual_sha1" "$server_properties" || fail 'server.properties SHA1 update failed'
/usr/bin/systemctl restart copimine-minecraft
for attempt in $(seq 1 60); do
  if /usr/bin/systemctl is-active --quiet copimine-minecraft; then
    break
  fi
  sleep 1
done
/usr/bin/systemctl is-active --quiet copimine-minecraft || fail 'copimine-minecraft did not become active'

printf 'resource pack installed: sha1=%s sha256=%s backup=%s\n' "$actual_sha1" "$actual_sha256" "$backup_dir"
