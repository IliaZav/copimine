#!/usr/bin/env bash
set -euo pipefail

COPIMINE_ROOT="${COPIMINE_ROOT:-/opt/copimine}"
COPIMINE_APP_USER="${COPIMINE_APP_USER:-qwerty}"
COPIMINE_APP_GROUP="${COPIMINE_APP_GROUP:-$COPIMINE_APP_USER}"
COPIMINE_ADMIN_DIR="${COPIMINE_ADMIN_DIR:-$COPIMINE_ROOT/admin-web}"
COPIMINE_ENV_FILE="${COPIMINE_ENV_FILE:-$COPIMINE_ADMIN_DIR/.env}"
COPIMINE_SECRETS_DIR="${COPIMINE_SECRETS_DIR:-/opt/copimine-secrets}"
COPIMINE_SERVER_DIR="${COPIMINE_SERVER_DIR:-$COPIMINE_ROOT/minecraft/server}"
COPIMINE_SERVER_PROPERTIES="${COPIMINE_SERVER_PROPERTIES:-$COPIMINE_SERVER_DIR/server.properties}"
COPIMINE_WORLD_SEED="${COPIMINE_WORLD_SEED:--1861153001556076901}"
COPIMINE_BACKUP_DIR="${COPIMINE_BACKUP_DIR:-/opt/copimine-backups}"
COPIMINE_RUNTIME_METADATA="${COPIMINE_RUNTIME_METADATA:-$COPIMINE_ROOT/deploy/runtime_metadata.json}"
COPIMINE_RELEASE_MANIFEST="${COPIMINE_RELEASE_MANIFEST:-$COPIMINE_ROOT/deploy/release_manifest.json}"
COPIMINE_INSTALLER_MANIFEST="${COPIMINE_INSTALLER_MANIFEST:-$COPIMINE_ROOT/deploy/installer_manifest.json}"
COPIMINE_CLEAN_WORLD_STATE_SQL="${COPIMINE_CLEAN_WORLD_STATE_SQL:-$COPIMINE_ROOT/db/runtime/clean_world_state.sql}"
COPIMINE_NGINX_TEMPLATE="${COPIMINE_NGINX_TEMPLATE:-$COPIMINE_ADMIN_DIR/deploy/nginx-copimine-admin-18080.conf}"
COPIMINE_NGINX_AVAILABLE="${COPIMINE_NGINX_AVAILABLE:-/etc/nginx/sites-available/copimine-admin.conf}"
COPIMINE_NGINX_ENABLED="${COPIMINE_NGINX_ENABLED:-/etc/nginx/sites-enabled/copimine-admin.conf}"
COPIMINE_SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
)

copimine_log() {
  printf '[copimine] %s\n' "$*"
}

copimine_fail() {
  printf '[copimine] ERROR: %s\n' "$*" >&2
  exit 1
}

copimine_need() {
  command -v "$1" >/dev/null 2>&1 || copimine_fail "Missing command: $1"
}

copimine_require_root() {
  if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
    copimine_fail "Run with sudo/root."
  fi
}

copimine_require_path() {
  local path="$1"
  [[ -e "$path" ]] || copimine_fail "Missing required path: $path"
}

copimine_run_as_app() {
  runuser -u "$COPIMINE_APP_USER" -- "$@"
}

copimine_stop_services() {
  for service in "${COPIMINE_SERVICES[@]}"; do
    systemctl stop "$service" 2>/dev/null || true
  done
}

copimine_start_services() {
  systemctl daemon-reload
  for service in "${COPIMINE_SERVICES[@]}"; do
    if systemctl list-unit-files | grep -q "^${service}\.service"; then
      systemctl enable "$service" >/dev/null 2>&1 || true
      systemctl restart "$service"
    fi
  done
  if systemctl list-unit-files | grep -q '^nginx\.service'; then
    systemctl enable nginx >/dev/null 2>&1 || true
    systemctl restart nginx
  fi
}

copimine_ensure_layout() {
  mkdir -p "$COPIMINE_SECRETS_DIR" "$COPIMINE_BACKUP_DIR"
  chmod 700 "$COPIMINE_SECRETS_DIR"
}

copimine_secret() {
  local name="$1"
  local bytes="${2:-32}"
  local path="$COPIMINE_SECRETS_DIR/$name"
  if [[ ! -s "$path" ]]; then
    openssl rand -hex "$bytes" > "$path"
  fi
  chmod 600 "$path"
  chown root:root "$path"
  tr -d '\r\n' < "$path"
}

copimine_write_env() {
  local postgres_password="$1"
  local secret_key="$2"
  local plugin_api_key="$3"
  local rcon_password="$4"
  local env_example="$COPIMINE_ADMIN_DIR/.env.example"
  copimine_require_path "$env_example"
  python3 - "$env_example" "$COPIMINE_ENV_FILE" "$postgres_password" "$secret_key" "$plugin_api_key" "$rcon_password" <<'PY'
from pathlib import Path
import sys

example = Path(sys.argv[1])
target = Path(sys.argv[2])
postgres_password = sys.argv[3]
secret_key = sys.argv[4]
plugin_api_key = sys.argv[5]
rcon_password = sys.argv[6]

def parse(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values

values = parse(example)
values.update(parse(target))
values.update({
    "POSTGRES_HOST": "127.0.0.1",
    "POSTGRES_PORT": "5432",
    "POSTGRES_DB": "copimine",
    "POSTGRES_USER": "copimine",
    "POSTGRES_SCHEMA": "copimine",
    "POSTGRES_PASSWORD": postgres_password,
    "DATABASE_URL": f"postgresql://copimine:{postgres_password}@127.0.0.1:5432/copimine",
    "COPIMINE_ENV_FILE": "/opt/copimine/admin-web/.env",
    "ADMIN_PUBLIC_BASE_URL": values.get("ADMIN_PUBLIC_BASE_URL", "http://copimine.ru:18080"),
    "PUBLIC_PANEL_URL": values.get("PUBLIC_PANEL_URL", "http://copimine.ru:18080"),
    "BACKEND_INTERNAL_BASE_URL": values.get("BACKEND_INTERNAL_BASE_URL", "http://127.0.0.1:8090"),
    "MINECRAFT_SERVICE": values.get("MINECRAFT_SERVICE", "copimine-minecraft"),
    "RCON_HOST": values.get("RCON_HOST", "127.0.0.1"),
    "RCON_PORT": values.get("RCON_PORT", "25575"),
    "RCON_PASSWORD": rcon_password,
    "AUTH_COOKIE_SECURE": values.get("AUTH_COOKIE_SECURE", "0") if values.get("AUTH_COOKIE_SECURE", "").strip() not in {"", "CHANGE_ME"} else "0",
})
if values.get("SECRET_KEY", "CHANGE_ME") in {"", "CHANGE_ME"}:
    values["SECRET_KEY"] = secret_key
if values.get("PLUGIN_API_KEY", "CHANGE_ME") in {"", "CHANGE_ME"}:
    values["PLUGIN_API_KEY"] = plugin_api_key

origins = [x.strip() for x in values.get("ALLOWED_ORIGINS", "").split(",") if x.strip()]
for origin in (values["ADMIN_PUBLIC_BASE_URL"], values["PUBLIC_PANEL_URL"], values["BACKEND_INTERNAL_BASE_URL"]):
    if origin and origin not in origins:
        origins.append(origin)
values["ALLOWED_ORIGINS"] = ",".join(origins)

target.parent.mkdir(parents=True, exist_ok=True)
ordered = [f"{key}={values[key]}" for key in sorted(values)]
target.write_text("\n".join(ordered) + "\n", encoding="utf-8")
PY
  chown "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_ENV_FILE"
  chmod 600 "$COPIMINE_ENV_FILE"
}

copimine_ensure_postgres() {
  local postgres_password="$1"
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 postgres <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'copimine') THEN
        CREATE ROLE copimine LOGIN PASSWORD '${postgres_password}';
    ELSE
        ALTER ROLE copimine WITH LOGIN PASSWORD '${postgres_password}';
    END IF;
END
\$\$;
SQL

  if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname='copimine'" | grep -q 1; then
    runuser -u postgres -- createdb -O copimine copimine
  fi

  runuser -u postgres -- psql -v ON_ERROR_STOP=1 copimine <<SQL
GRANT ALL PRIVILEGES ON DATABASE copimine TO copimine;
GRANT ALL ON SCHEMA public TO copimine;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO copimine;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO copimine;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO copimine;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO copimine;
CREATE SCHEMA IF NOT EXISTS copimine AUTHORIZATION copimine;
GRANT ALL ON SCHEMA copimine TO copimine;
ALTER DEFAULT PRIVILEGES IN SCHEMA copimine GRANT ALL ON TABLES TO copimine;
ALTER DEFAULT PRIVILEGES IN SCHEMA copimine GRANT ALL ON SEQUENCES TO copimine;
SQL
}

copimine_env_value() {
  local key="$1"
  copimine_require_path "$COPIMINE_ENV_FILE"
  python3 - "$COPIMINE_ENV_FILE" "$key" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
key = sys.argv[2]
for raw in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    left, value = line.split("=", 1)
    if left.strip() == key:
        print(value.strip().strip('"').strip("'"))
        break
PY
}

copimine_apply_clean_world_state() {
  copimine_need psql
  copimine_require_path "$COPIMINE_CLEAN_WORLD_STATE_SQL"
  local pg_user pg_db pg_password
  pg_user="$(copimine_env_value POSTGRES_USER)"
  pg_db="$(copimine_env_value POSTGRES_DB)"
  pg_password="$(copimine_env_value POSTGRES_PASSWORD)"
  [[ -n "$pg_user" && -n "$pg_db" && -n "$pg_password" ]] || copimine_fail "PostgreSQL credentials are incomplete in $COPIMINE_ENV_FILE"
  PGPASSWORD="$pg_password" psql -h 127.0.0.1 -U "$pg_user" -v ON_ERROR_STOP=1 -d "$pg_db" -f "$COPIMINE_CLEAN_WORLD_STATE_SQL" >/dev/null
  copimine_log "Cleaned world-bound runtime state in PostgreSQL."
}

copimine_python_env() {
  copimine_require_path "$COPIMINE_ADMIN_DIR/requirements.txt"
  cd "$COPIMINE_ADMIN_DIR"
  rm -rf .venv
  python3 -m venv .venv
  [[ -x ".venv/bin/python" ]] || copimine_fail "Python venv bootstrap failed: missing $COPIMINE_ADMIN_DIR/.venv/bin/python"
  .venv/bin/python -m pip install --upgrade pip
  [[ -x ".venv/bin/pip" ]] || copimine_fail "Python venv bootstrap failed: missing $COPIMINE_ADMIN_DIR/.venv/bin/pip"
  .venv/bin/pip install -r requirements.txt
  .venv/bin/python -m backend.startup_checks --strict >/dev/null
  chown -R "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_ADMIN_DIR/.venv"
}

copimine_build_assets() {
  copimine_require_path "$COPIMINE_ROOT/resourcepacks/build-resourcepack.py"
  python3 "$COPIMINE_ROOT/resourcepacks/build-resourcepack.py"
  if [[ -x "$COPIMINE_ROOT/scripts/thirdparty/build_modpack.sh" ]]; then
    "$COPIMINE_ROOT/scripts/thirdparty/build_modpack.sh" "$COPIMINE_ROOT"
  fi
}

copimine_release_value() {
  local dotted_path="$1"
  if [[ ! -f "$COPIMINE_RELEASE_MANIFEST" ]]; then
    return 0
  fi
  python3 - "$COPIMINE_RELEASE_MANIFEST" "$dotted_path" <<'PY'
from pathlib import Path
import json
import sys

manifest_path = Path(sys.argv[1])
dotted = sys.argv[2]
payload = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
current = payload
for part in dotted.split("."):
    if isinstance(current, dict) and part in current:
        current = current[part]
    else:
        current = ""
        break
if current is None:
    current = ""
print(current)
PY
}

copimine_sync_server_properties() {
  local resourcepack_sha1
  resourcepack_sha1="$(sha1sum "$COPIMINE_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
  local resourcepack_url
  resourcepack_url="$(copimine_release_value "resourcePack.downloadUrl")"
  if [[ -z "$resourcepack_url" ]]; then
    resourcepack_url="http://admin.copimine.ru:18080/resourcepacks/CopiMineResourcePack.zip"
  fi
  python3 - "$COPIMINE_SERVER_PROPERTIES" "$resourcepack_sha1" "$resourcepack_url" "$COPIMINE_WORLD_SEED" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
sha1 = sys.argv[2]
resource_pack_url = sys.argv[3].replace(":", r"\:")
world_seed = sys.argv[4]
lines = path.read_text(encoding="utf-8").splitlines()
updates = {
    "level-seed": world_seed,
    "resource-pack": resource_pack_url,
    "resource-pack-sha1": sha1,
}
seen = set()
output = []
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
path.write_text("\n".join(output) + "\n", encoding="utf-8")
PY
}

copimine_write_modpack_hashes() {
  local modpack_zip="$COPIMINE_ROOT/thirdparty/CopiMineMods.zip"
  local sha1_path="$COPIMINE_ROOT/thirdparty/CopiMineMods.sha1"
  local sha256_path="$COPIMINE_ROOT/thirdparty/CopiMineMods.sha256"
  [[ -f "$modpack_zip" ]] || copimine_fail "Missing modpack zip: $modpack_zip"
  local modpack_sha1 modpack_sha256
  modpack_sha1="$(sha1sum "$modpack_zip" | awk '{print $1}')"
  modpack_sha256="$(sha256sum "$modpack_zip" | awk '{print $1}')"
  printf '%s\n' "$modpack_sha1" > "$sha1_path"
  printf '%s\n' "$modpack_sha256" > "$sha256_path"
}

copimine_install_system_files() {
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-admin.service" /etc/systemd/system/copimine-admin.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-discord-bot.service" /etc/systemd/system/copimine-discord-bot.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-minecraft-discord-bridge.service" /etc/systemd/system/copimine-minecraft-discord-bridge.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-minecraft.service" /etc/systemd/system/copimine-minecraft.service
  if [[ -f "$COPIMINE_NGINX_TEMPLATE" ]]; then
    install -m 0644 "$COPIMINE_NGINX_TEMPLATE" "$COPIMINE_NGINX_AVAILABLE"
    ln -sfn "$COPIMINE_NGINX_AVAILABLE" "$COPIMINE_NGINX_ENABLED"
    rm -f /etc/nginx/sites-enabled/default
    nginx -t
  fi
}

copimine_write_runtime_metadata() {
  local git_commit
  git_commit="$(git -C "$COPIMINE_ROOT" rev-parse --short HEAD 2>/dev/null || true)"
  python3 - "$COPIMINE_RUNTIME_METADATA" "$COPIMINE_ROOT" "$git_commit" <<'PY'
from pathlib import Path
import json
import sys
import time

target = Path(sys.argv[1])
root = Path(sys.argv[2])
git_commit = sys.argv[3].strip()
payload = {
    "generatedAt": int(time.time()),
    "gitCommit": git_commit,
    "root": str(root),
    "resourcePack": str(root / "resourcepacks" / "build" / "CopiMineResourcePack.zip"),
    "modPack": str(root / "thirdparty" / "CopiMineMods.zip"),
    "clientMod": str(root / "thirdparty" / "client-mods" / "CopiMineClient-0.1.0.jar"),
}
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

copimine_refresh_release_artifacts() {
  copimine_write_modpack_hashes
  copimine_sync_server_properties
  copimine_write_runtime_metadata
}

copimine_validate_release_contract() {
  python3 - "$COPIMINE_ROOT" "$COPIMINE_RELEASE_MANIFEST" "$COPIMINE_INSTALLER_MANIFEST" "$COPIMINE_SERVER_PROPERTIES" <<'PY'
from __future__ import annotations

from pathlib import Path
import hashlib
import json
import sys

root = Path(sys.argv[1])
release_manifest_path = Path(sys.argv[2])
installer_manifest_path = Path(sys.argv[3])
server_properties_path = Path(sys.argv[4])
errors: list[str] = []

def read_json(path: Path) -> dict:
    if not path.is_file():
        errors.append(f"Missing manifest: {path}")
        return {}
    return json.loads(path.read_text(encoding="utf-8-sig"))

def sha1(path: Path) -> str:
    return hashlib.sha1(path.read_bytes()).hexdigest()

def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

release_manifest = read_json(release_manifest_path)
installer_manifest = read_json(installer_manifest_path)

modpack = root / "thirdparty" / "CopiMineMods.zip"
modpack_sha1_path = root / "thirdparty" / "CopiMineMods.sha1"
modpack_sha256_path = root / "thirdparty" / "CopiMineMods.sha256"
resource_pack = root / "resourcepacks" / "build" / "CopiMineResourcePack.zip"
client_mod = root / "thirdparty" / "client-mods" / "CopiMineClient-0.1.0.jar"
modpack_snapshot = root / "admin-web" / "frontend" / "assets" / "public-data" / "modpack_snapshot.json"

for required in (modpack, modpack_sha1_path, modpack_sha256_path, resource_pack, client_mod, modpack_snapshot, server_properties_path):
    if not required.is_file():
        errors.append(f"Missing required release file: {required}")

if not errors:
    actual_modpack_sha1 = sha1(modpack)
    actual_modpack_sha256 = sha256(modpack)
    actual_resource_sha1 = sha1(resource_pack)
    actual_resource_sha256 = sha256(resource_pack)
    actual_client_sha1 = sha1(client_mod)
    sidecar_sha1 = modpack_sha1_path.read_text(encoding="utf-8-sig").strip()
    sidecar_sha256 = modpack_sha256_path.read_text(encoding="utf-8-sig").strip()

    if actual_modpack_sha1 != sidecar_sha1:
        errors.append(f"Modpack SHA1 mismatch: sidecar={sidecar_sha1} actual={actual_modpack_sha1}")
    if actual_modpack_sha256 != sidecar_sha256:
        errors.append(f"Modpack SHA256 mismatch: sidecar={sidecar_sha256} actual={actual_modpack_sha256}")

    release_modpack = release_manifest.get("modpack", {})
    release_resource = release_manifest.get("resourcePack", {})
    release_client = release_manifest.get("clientMod", {})
    if release_modpack.get("sha1") != actual_modpack_sha1:
        errors.append(f"release_manifest modpack.sha1 mismatch: manifest={release_modpack.get('sha1')} actual={actual_modpack_sha1}")
    if release_modpack.get("sha256") != actual_modpack_sha256:
        errors.append(f"release_manifest modpack.sha256 mismatch: manifest={release_modpack.get('sha256')} actual={actual_modpack_sha256}")
    if release_resource.get("sha1") != actual_resource_sha1:
        errors.append(f"release_manifest resourcePack.sha1 mismatch: manifest={release_resource.get('sha1')} actual={actual_resource_sha1}")
    if release_resource.get("sha256") != actual_resource_sha256:
        errors.append(f"release_manifest resourcePack.sha256 mismatch: manifest={release_resource.get('sha256')} actual={actual_resource_sha256}")
    if release_client.get("sha1") != actual_client_sha1:
        errors.append(f"release_manifest clientMod.sha1 mismatch: manifest={release_client.get('sha1')} actual={actual_client_sha1}")

    snapshot = json.loads(modpack_snapshot.read_text(encoding="utf-8-sig"))
    if snapshot.get("sha1") != actual_modpack_sha1:
        errors.append(f"modpack_snapshot sha1 mismatch: snapshot={snapshot.get('sha1')} actual={actual_modpack_sha1}")
    if snapshot.get("sha256") != actual_modpack_sha256:
        errors.append(f"modpack_snapshot sha256 mismatch: snapshot={snapshot.get('sha256')} actual={actual_modpack_sha256}")
    if snapshot.get("downloadUrl") != "/downloads/CopiMineMods.zip":
        errors.append(f"modpack_snapshot downloadUrl mismatch: {snapshot.get('downloadUrl')}")

    installer_hashes = installer_manifest.get("artifacts", {})
    if installer_hashes.get("modpack", {}).get("sha1") != actual_modpack_sha1:
        errors.append("installer_manifest modpack SHA1 mismatch")
    if installer_hashes.get("modpack", {}).get("sha256") != actual_modpack_sha256:
        errors.append("installer_manifest modpack SHA256 mismatch")
    if installer_hashes.get("resourcePack", {}).get("sha1") != actual_resource_sha1:
        errors.append("installer_manifest resourcePack SHA1 mismatch")
    if installer_hashes.get("resourcePack", {}).get("sha256") != actual_resource_sha256:
        errors.append("installer_manifest resourcePack SHA256 mismatch")
    if installer_hashes.get("clientMod", {}).get("sha1") != actual_client_sha1:
        errors.append("installer_manifest clientMod SHA1 mismatch")

    props = {}
    for raw in server_properties_path.read_text(encoding="utf-8").splitlines():
        if "=" in raw and not raw.startswith("#"):
            k, v = raw.split("=", 1)
            props[k] = v
    if props.get("resource-pack-sha1") != actual_resource_sha1:
        errors.append(f"server.properties resource-pack-sha1 mismatch: props={props.get('resource-pack-sha1')} actual={actual_resource_sha1}")

if errors:
    for entry in errors:
        print(entry, file=sys.stderr)
    sys.exit(1)
PY
}

copimine_backup_snapshot() {
  local backup_name="${1:-copimine-backup-$(date +%Y%m%d-%H%M%S).tar.gz}"
  local backup_path="$COPIMINE_BACKUP_DIR/$backup_name"
  mkdir -p "$COPIMINE_BACKUP_DIR"
  tar -C /opt -czf "$backup_path" \
    "$(basename "$COPIMINE_ROOT")" \
    "$(basename "$COPIMINE_SECRETS_DIR")" 2>/dev/null || true
  printf '%s\n' "$backup_path"
}

copimine_wipe_worlds() {
  local world
  for world in world world_nether world_the_end; do
    if [[ -d "$COPIMINE_SERVER_DIR/$world" ]]; then
      rm -rf -- "$COPIMINE_SERVER_DIR/$world"
      copimine_log "Removed world directory: $COPIMINE_SERVER_DIR/$world"
    fi
  done
  python3 - "$COPIMINE_SERVER_PROPERTIES" "$COPIMINE_WORLD_SEED" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
seed = sys.argv[2]
lines = path.read_text(encoding="utf-8").splitlines()
updated = False
for idx, line in enumerate(lines):
    if line.startswith("level-seed="):
        lines[idx] = f"level-seed={seed}"
        updated = True
        break
if not updated:
    lines.append(f"level-seed={seed}")
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
  copimine_apply_clean_world_state
}

copimine_verify_runtime() {
  copimine_need curl
  copimine_need sha1sum
  systemctl is-active --quiet copimine-admin || copimine_fail "copimine-admin is not active"
  systemctl is-active --quiet copimine-minecraft || copimine_fail "copimine-minecraft is not active"
  if systemctl list-unit-files | grep -q '^copimine-discord-bot\.service'; then
    systemctl is-active --quiet copimine-discord-bot || copimine_fail "copimine-discord-bot is not active"
  fi
  curl -fsS http://127.0.0.1:8090/api/health >/dev/null || copimine_fail "/api/health failed"
  curl -fsS http://127.0.0.1:8090/api/runtime >/dev/null || copimine_fail "/api/runtime failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/downloads/CopiMineMods.zip >/dev/null || copimine_fail "modpack download route failed"
  curl -fsSI -H 'Host: copimine.ru:18080' http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip >/dev/null || copimine_fail "resourcepack download route failed"
}

copimine_install_flow() {
  copimine_require_root
  copimine_need python3
  copimine_need openssl
  copimine_need runuser
  copimine_need systemctl
  copimine_need psql
  copimine_need nginx
  copimine_ensure_layout
  local postgres_password secret_key plugin_api_key rcon_password
  postgres_password="$(copimine_secret postgres-password.txt 24)"
  secret_key="$(copimine_secret secret-key.txt 32)"
  plugin_api_key="$(copimine_secret plugin-api-key.txt 32)"
  rcon_password="$(copimine_secret rcon-password.txt 32)"
  copimine_write_env "$postgres_password" "$secret_key" "$plugin_api_key" "$rcon_password"
  chown -R "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_ROOT"
  copimine_ensure_postgres "$postgres_password"
  copimine_python_env
  copimine_build_assets
  copimine_refresh_release_artifacts
  copimine_install_system_files
  copimine_validate_release_contract
}
