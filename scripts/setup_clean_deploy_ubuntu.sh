#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo."
  exit 1
fi

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
APP_USER="${COPIMINE_APP_USER:-qwerty}"
APP_GROUP="${COPIMINE_APP_GROUP:-$APP_USER}"
ENV_EXAMPLE="$ROOT/admin-web/.env.example"
ENV_FILE="$ROOT/admin-web/.env"
SECRETS_DIR="${COPIMINE_SECRETS_DIR:-/opt/copimine-secrets}"
PG_SECRET_FILE="$SECRETS_DIR/postgres-password.txt"
MC_SERVER_DIR="$ROOT/minecraft/server"
SERVER_PROPERTIES="$MC_SERVER_DIR/server.properties"
NGINX_AVAILABLE="/etc/nginx/sites-available/copimine-admin.conf"
NGINX_ENABLED="/etc/nginx/sites-enabled/copimine-admin.conf"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 1; }
}

need python3
need openssl
need sha1sum
need systemctl
need runuser
need psql

mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"

if [[ ! -s "$PG_SECRET_FILE" ]]; then
  openssl rand -hex 24 > "$PG_SECRET_FILE"
fi
chmod 600 "$PG_SECRET_FILE"
chown root:root "$PG_SECRET_FILE"

POSTGRES_PASSWORD="$(tr -d '\r\n' < "$PG_SECRET_FILE")"
SECRET_KEY="$(openssl rand -hex 32)"
PLUGIN_API_KEY="$(openssl rand -hex 32)"
RCON_PASSWORD="$(openssl rand -hex 32)"

python3 - "$ENV_EXAMPLE" "$ENV_FILE" "$POSTGRES_PASSWORD" "$SECRET_KEY" "$PLUGIN_API_KEY" <<'PY'
from pathlib import Path
import sys

example = Path(sys.argv[1])
target = Path(sys.argv[2])
postgres_password = sys.argv[3]
secret_key = sys.argv[4]
plugin_api_key = sys.argv[5]

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
    "AUTH_COOKIE_SECURE": values.get("AUTH_COOKIE_SECURE", "0") if values.get("AUTH_COOKIE_SECURE", "").strip() not in {"", "CHANGE_ME"} else "0",
    "ADMIN_PUBLIC_BASE_URL": "http://copimine.ru:18080",
    "PUBLIC_PANEL_URL": "http://copimine.ru:18080",
    "BACKEND_INTERNAL_BASE_URL": "http://127.0.0.1:8090",
    "MINECRAFT_SERVICE": "copimine-minecraft",
})
if values.get("SECRET_KEY", "CHANGE_ME") in {"", "CHANGE_ME"}:
    values["SECRET_KEY"] = secret_key
if values.get("PLUGIN_API_KEY", "CHANGE_ME") in {"", "CHANGE_ME"}:
    values["PLUGIN_API_KEY"] = plugin_api_key

target.parent.mkdir(parents=True, exist_ok=True)
ordered = [f"{key}={values[key]}" for key in sorted(values)]
target.write_text("\n".join(ordered) + "\n", encoding="utf-8")
PY

chown "$APP_USER:$APP_GROUP" "$ENV_FILE"
chmod 600 "$ENV_FILE"
chown -R "$APP_USER:$APP_GROUP" "$ROOT"

runuser -u postgres -- psql -v ON_ERROR_STOP=1 postgres <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'copimine') THEN
        CREATE ROLE copimine LOGIN PASSWORD '${POSTGRES_PASSWORD}';
    ELSE
        ALTER ROLE copimine WITH LOGIN PASSWORD '${POSTGRES_PASSWORD}';
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

PGPASSWORD="$POSTGRES_PASSWORD" psql \
  -h 127.0.0.1 \
  -p 5432 \
  -U copimine \
  -d copimine \
  -v ON_ERROR_STOP=1 \
  -c "SELECT 1" >/dev/null

cd "$ROOT/admin-web"
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt
chown -R "$APP_USER:$APP_GROUP" "$ROOT/admin-web/.venv"

python3 "$ROOT/resourcepacks/build-resourcepack.py"
if [[ -x "$ROOT/scripts/thirdparty/build_modpack.sh" ]]; then
  "$ROOT/scripts/thirdparty/build_modpack.sh" "$ROOT"
fi
RESOURCEPACK_SHA1="$(sha1sum "$ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"

python3 - "$SERVER_PROPERTIES" "$RESOURCEPACK_SHA1" "$RCON_PASSWORD" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
sha1 = sys.argv[2]
rcon_password = sys.argv[3]

updates = {
    "enable-rcon": "true",
    "rcon.port": "25575",
    "rcon.password": rcon_password,
    "resource-pack": r"http\://admin.copimine.ru\:18080/resourcepacks/CopiMineResourcePack.zip",
    "resource-pack-sha1": sha1,
}
lines = path.read_text(encoding="utf-8").splitlines()
seen = set()
output = []
for line in lines:
    if "=" not in line or line.startswith("#"):
        output.append(line)
        continue
    key, _value = line.split("=", 1)
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

install -m 0644 "$ROOT/admin-web/deploy/copimine-admin.service" /etc/systemd/system/copimine-admin.service
install -m 0644 "$ROOT/admin-web/deploy/copimine-discord-bot.service" /etc/systemd/system/copimine-discord-bot.service
install -m 0644 "$ROOT/admin-web/deploy/copimine-minecraft-discord-bridge.service" /etc/systemd/system/copimine-minecraft-discord-bridge.service
install -m 0644 "$ROOT/admin-web/deploy/copimine-minecraft.service" /etc/systemd/system/copimine-minecraft.service
install -m 0644 "$ROOT/admin-web/deploy/nginx-copimine-admin-18080.conf" "$NGINX_AVAILABLE"
ln -sfn "$NGINX_AVAILABLE" "$NGINX_ENABLED"
rm -f /etc/nginx/sites-enabled/default

systemctl daemon-reload
systemctl enable copimine-admin copimine-discord-bot copimine-minecraft-discord-bridge copimine-minecraft nginx
systemctl restart copimine-admin
systemctl restart copimine-discord-bot
systemctl restart copimine-minecraft-discord-bridge
systemctl restart copimine-minecraft
systemctl reload nginx

echo "SETUP_OK"
echo "Environment: $ENV_FILE"
echo "PostgreSQL password file: $PG_SECRET_FILE"
echo "Resource pack SHA1: $RESOURCEPACK_SHA1"
