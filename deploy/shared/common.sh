#!/usr/bin/env bash
set -euo pipefail

COPIMINE_ROOT="${COPIMINE_ROOT:-/opt/copimine}"
COPIMINE_APP_USER="${COPIMINE_APP_USER:-copimine}"
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
COPIMINE_NGINX_TLS_TEMPLATE="${COPIMINE_NGINX_TLS_TEMPLATE:-$COPIMINE_ADMIN_DIR/deploy/nginx-copimine-admin-https.conf}"
COPIMINE_NGINX_AVAILABLE="${COPIMINE_NGINX_AVAILABLE:-/etc/nginx/sites-available/copimine-admin.conf}"
COPIMINE_NGINX_ENABLED="${COPIMINE_NGINX_ENABLED:-/etc/nginx/sites-enabled/copimine-admin.conf}"
COPIMINE_GAME_HARDENING_SCRIPT="${COPIMINE_GAME_HARDENING_SCRIPT:-$COPIMINE_ROOT/deploy/shared/harden_game_runtime.py}"
COPIMINE_GAME_HARDENING_POLICY="${COPIMINE_GAME_HARDENING_POLICY:-$COPIMINE_ROOT/deploy/templates/game-runtime-hardening.json}"
COPIMINE_VOICECHAT_TEMPLATE="${COPIMINE_VOICECHAT_TEMPLATE:-$COPIMINE_ROOT/deploy/templates/voicechat-server.properties}"
COPIMINE_TLS_ENABLED="${COPIMINE_TLS_ENABLED:-}"
COPIMINE_TLS_CERT_PATH="${COPIMINE_TLS_CERT_PATH:-}"
COPIMINE_TLS_KEY_PATH="${COPIMINE_TLS_KEY_PATH:-}"
COPIMINE_SERVER_NAMES="${COPIMINE_SERVER_NAMES:-}"
COPIMINE_DATABASE_RESTORE_ACTIVE_DB="${COPIMINE_DATABASE_RESTORE_ACTIVE_DB:-}"
COPIMINE_DATABASE_RESTORE_BACKUP_DB="${COPIMINE_DATABASE_RESTORE_BACKUP_DB:-}"
COPIMINE_SERVICES=(
  "copimine-admin"
  "copimine-discord-bot"
  "copimine-minecraft-discord-bridge"
  "copimine-minecraft"
  "copimine-game-hardening"
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

copimine_http_wait() {
  local url="$1"
  local host_header="${2:-}"
  local timeout="${3:-60}"
  local elapsed=0
  local extra=()
  [[ -n "$host_header" ]] && extra=(-H "Host: $host_header")
  while (( elapsed < timeout )); do
    if curl -fsS "${extra[@]}" "$url" >/dev/null; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

copimine_url_host() {
  python3 - "$1" <<'PY'
from urllib.parse import urlparse
import sys

parsed = urlparse(sys.argv[1])
if parsed.scheme not in {"http", "https"} or not parsed.hostname:
    raise SystemExit("invalid public URL")
print(parsed.hostname)
PY
}

copimine_verify_public_endpoints() {
  local admin_url public_url tls_enabled public_host http_health_url
  admin_url="$(copimine_env_value ADMIN_PUBLIC_BASE_URL)"
  public_url="$(copimine_env_value PUBLIC_PANEL_URL)"
  tls_enabled="${COPIMINE_TLS_ENABLED:-$(copimine_env_value COPIMINE_TLS_ENABLED)}"
  tls_enabled="${tls_enabled:-0}"
  [[ "$admin_url" =~ ^https?:// ]] || copimine_fail "ADMIN_PUBLIC_BASE_URL must use http:// or https://"
  [[ "$public_url" =~ ^https?:// ]] || copimine_fail "PUBLIC_PANEL_URL must use http:// or https://"

  copimine_http_wait "${admin_url%/}/api/health" "" 90 || copimine_fail "public admin health endpoint failed: $admin_url"
  copimine_http_wait "${public_url%/}/downloads/CopiMineMods.zip" "" 90 || copimine_fail "public modpack endpoint failed: $public_url"
  copimine_http_wait "${public_url%/}/resourcepacks/CopiMineResourcePack.zip" "" 90 || copimine_fail "public resourcepack endpoint failed: $public_url"

  if [[ "$tls_enabled" == "1" ]]; then
    public_host="$(copimine_url_host "$public_url")"
    http_health_url="http://${public_host}:18080/api/health"
    # TLS mode keeps the legacy HTTP listener for game downloads and status;
    # verify it explicitly so both configured transports stay usable.
    copimine_http_wait "$http_health_url" "$public_host" 90 || copimine_fail "HTTP compatibility endpoint failed: $http_health_url"
    copimine_http_wait "http://${public_host}:18080/downloads/CopiMineMods.zip" "$public_host" 90 || copimine_fail "HTTP modpack compatibility endpoint failed"
    copimine_http_wait "http://${public_host}:18080/resourcepacks/CopiMineResourcePack.zip" "$public_host" 90 || copimine_fail "HTTP resourcepack compatibility endpoint failed"
  fi
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
  chmod 700 "$COPIMINE_BACKUP_DIR"
}

copimine_ensure_app_user() {
  if ! getent group "$COPIMINE_APP_GROUP" >/dev/null 2>&1; then
    groupadd --system "$COPIMINE_APP_GROUP"
  fi
  if ! id -u "$COPIMINE_APP_USER" >/dev/null 2>&1; then
    useradd \
      --system \
      --gid "$COPIMINE_APP_GROUP" \
      --home-dir "$COPIMINE_ROOT" \
      --shell /usr/sbin/nologin \
      --no-create-home \
      "$COPIMINE_APP_USER"
  fi
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
  COPIMINE_POSTGRES_PASSWORD="$postgres_password" \
  COPIMINE_SECRET_KEY="$secret_key" \
  COPIMINE_PLUGIN_API_KEY="$plugin_api_key" \
  COPIMINE_RCON_PASSWORD="$rcon_password" \
  python3 - "$env_example" "$COPIMINE_ENV_FILE" <<'PY'
import os
from pathlib import Path
import sys
from urllib.parse import urlparse

example = Path(sys.argv[1])
target = Path(sys.argv[2])
postgres_password = os.environ["COPIMINE_POSTGRES_PASSWORD"]
secret_key = os.environ["COPIMINE_SECRET_KEY"]
plugin_api_key = os.environ["COPIMINE_PLUGIN_API_KEY"]
rcon_password = os.environ["COPIMINE_RCON_PASSWORD"]

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
    "ADMIN_PUBLIC_BASE_URL": values.get("ADMIN_PUBLIC_BASE_URL", "http://admin.copimine.ru:18080"),
    "PUBLIC_PANEL_URL": values.get("PUBLIC_PANEL_URL", "http://copimine.ru:18080"),
    "BACKEND_INTERNAL_BASE_URL": values.get("BACKEND_INTERNAL_BASE_URL", "http://127.0.0.1:8090"),
    "MINECRAFT_SERVICE": values.get("MINECRAFT_SERVICE", "copimine-minecraft"),
    "RCON_HOST": values.get("RCON_HOST", "127.0.0.1"),
    "RCON_PORT": values.get("RCON_PORT", "25575"),
    "RCON_PASSWORD": rcon_password,
    "NGINX_SERVER_NAMES": values.get("NGINX_SERVER_NAMES", "admin.copimine.ru copimine.ru www.copimine.ru"),
    "COPIMINE_TLS_ENABLED": values.get("COPIMINE_TLS_ENABLED", "0"),
    "COPIMINE_TLS_CERT_PATH": values.get("COPIMINE_TLS_CERT_PATH", ""),
    "COPIMINE_TLS_KEY_PATH": values.get("COPIMINE_TLS_KEY_PATH", ""),
})
for url_key in ("ADMIN_PUBLIC_BASE_URL", "PUBLIC_PANEL_URL"):
    parsed = urlparse(values[url_key])
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise SystemExit(f"{url_key} must be an absolute http:// or https:// URL")
tls_enabled = values.get("COPIMINE_TLS_ENABLED", "0").strip()
if tls_enabled not in {"0", "1"}:
    raise SystemExit("COPIMINE_TLS_ENABLED must be 0 or 1")
admin_is_https = values["ADMIN_PUBLIC_BASE_URL"].lower().startswith("https://")
panel_is_https = values["PUBLIC_PANEL_URL"].lower().startswith("https://")
if tls_enabled == "1" and not admin_is_https:
    raise SystemExit("TLS configuration requires ADMIN_PUBLIC_BASE_URL to use https://")
if tls_enabled == "1" and not panel_is_https:
    raise SystemExit("TLS configuration requires PUBLIC_PANEL_URL to use https://")
if tls_enabled == "0" and admin_is_https:
    raise SystemExit("HTTPS public URL requires COPIMINE_TLS_ENABLED=1")
if tls_enabled == "0" and panel_is_https:
    raise SystemExit("HTTPS public URL requires COPIMINE_TLS_ENABLED=1")
if tls_enabled == "1":
    values["AUTH_COOKIE_SECURE"] = "1"
    values["ALLOW_INSECURE_HTTP_AUTH"] = "0"
else:
    values["AUTH_COOKIE_SECURE"] = "0"
    if values.get("ALLOW_INSECURE_HTTP_AUTH", "").strip() in {"", "CHANGE_ME"}:
        # This keeps the temporary no-TLS installation usable. Operators can
        # explicitly set 0 to disable HTTP login before TLS is configured.
        values["ALLOW_INSECURE_HTTP_AUTH"] = "1"
if values.get("ALLOW_INSECURE_HTTP_AUTH", "") not in {"0", "1"}:
    raise SystemExit("ALLOW_INSECURE_HTTP_AUTH must be 0 or 1")
if values.get("RESOURCE_PACK_PUBLIC_URL", "").strip() in {"", "CHANGE_ME"}:
    values["RESOURCE_PACK_PUBLIC_URL"] = values["PUBLIC_PANEL_URL"].rstrip("/") + "/resourcepacks/CopiMineResourcePack.zip"
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
  [[ -n "$postgres_password" && "$postgres_password" != "CHANGE_ME" ]] || copimine_fail "POSTGRES_PASSWORD is missing"
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 -v db_password="$postgres_password" postgres <<'SQL'
SELECT format('CREATE ROLE copimine LOGIN PASSWORD %L', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'copimine')
\gexec
SELECT format('ALTER ROLE copimine WITH LOGIN PASSWORD %L', :'db_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'copimine')
\gexec
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
matched = None
for raw in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    left, current = line.split("=", 1)
    if left.strip() == key:
        matched = current.strip().strip('"').strip("'")
if matched is not None:
    print(matched)
PY
}

copimine_validate_voicechat_security() {
  local allow_exception exception_reason
  allow_exception="${COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT:-$(copimine_env_value COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT)}"
  exception_reason="${COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON:-$(copimine_env_value COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON)}"
  allow_exception="${allow_exception:-0}"
  python3 "$COPIMINE_GAME_HARDENING_SCRIPT" check-voicechat \
    --server-properties "$COPIMINE_SERVER_PROPERTIES" \
    --voicechat-config "$COPIMINE_SERVER_DIR/plugins/voicechat/voicechat-server.properties" \
    --allow-insecure-offline-voicechat "$allow_exception" \
    --exception-reason "$exception_reason"
}

copimine_sync_game_runtime_hardening() {
  copimine_require_path "$COPIMINE_GAME_HARDENING_SCRIPT"
  copimine_require_path "$COPIMINE_GAME_HARDENING_POLICY"
  copimine_require_path "$COPIMINE_VOICECHAT_TEMPLATE"
  python3 "$COPIMINE_GAME_HARDENING_SCRIPT" sync \
    --server-dir "$COPIMINE_SERVER_DIR" \
    --policy "$COPIMINE_GAME_HARDENING_POLICY" \
    --voicechat-template "$COPIMINE_VOICECHAT_TEMPLATE"
  copimine_validate_voicechat_security
  copimine_log "Game runtime config hardening is synchronized."
}

copimine_apply_post_start_game_hardening() {
  local rcon_password admin_group
  rcon_password="$(copimine_env_value RCON_PASSWORD)"
  [[ -n "$rcon_password" && "$rcon_password" != "CHANGE_ME" ]] || copimine_fail "RCON_PASSWORD is missing in $COPIMINE_ENV_FILE"
  admin_group="${COPIMINE_IMAGEFRAME_ADMIN_GROUP:-$(copimine_env_value COPIMINE_IMAGEFRAME_ADMIN_GROUP)}"
  admin_group="${admin_group:-admin}"
  COPIMINE_RCON_PASSWORD="$rcon_password" python3 "$COPIMINE_GAME_HARDENING_SCRIPT" apply-luckperms-imageframe \
    --server-properties "$COPIMINE_SERVER_PROPERTIES" \
    --password-env COPIMINE_RCON_PASSWORD \
    --timeout-seconds "${COPIMINE_GAME_HARDENING_RCON_TIMEOUT_SECONDS:-90}" \
    --admin-group "$admin_group"
  copimine_log "ImageFrame LuckPerms hardening policy applied."
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

copimine_apply_migrations() {
  local migration_runner="$COPIMINE_ROOT/deploy/ubuntu/migrate.sh"
  copimine_require_path "$migration_runner"
  COPIMINE_ROOT="$COPIMINE_ROOT" \
  COPIMINE_ENV_FILE="$COPIMINE_ENV_FILE" \
  COPIMINE_MIGRATIONS_DIR="$COPIMINE_ROOT/db/migrations" \
  bash "$migration_runner"
  copimine_log "PostgreSQL migrations are current."
}

copimine_restore_database_safely() {
  local dump_path="$1"
  copimine_require_path "$dump_path"

  local pg_host pg_port pg_user pg_db pg_password
  pg_host="$(copimine_env_value POSTGRES_HOST)"
  pg_port="$(copimine_env_value POSTGRES_PORT)"
  pg_user="$(copimine_env_value POSTGRES_USER)"
  pg_db="$(copimine_env_value POSTGRES_DB)"
  pg_password="$(copimine_env_value POSTGRES_PASSWORD)"
  pg_host="${pg_host:-127.0.0.1}"
  pg_port="${pg_port:-5432}"
  [[ "$pg_user" =~ ^[A-Za-z_][A-Za-z0-9_]{0,48}$ ]] || copimine_fail "POSTGRES_USER must be a PostgreSQL identifier"
  [[ "$pg_db" =~ ^[A-Za-z_][A-Za-z0-9_]{0,40}$ ]] || copimine_fail "POSTGRES_DB must be a PostgreSQL identifier"
  [[ -n "$pg_password" && "$pg_password" != "CHANGE_ME" ]] || copimine_fail "POSTGRES_PASSWORD is missing in $COPIMINE_ENV_FILE"

  local timestamp staging_db backup_db database_exists
  timestamp="$(date +%Y%m%d%H%M%S)"
  staging_db="${pg_db}_restore_${timestamp}_$RANDOM"
  backup_db="${pg_db}_pre_restore_${timestamp}"
  staging_db="${staging_db:0:63}"
  backup_db="${backup_db:0:63}"

  runuser -u postgres -- dropdb --if-exists "$staging_db"
  runuser -u postgres -- createdb -O "$pg_user" "$staging_db"
  if [[ "$dump_path" == *.sql ]]; then
    PGPASSWORD="$pg_password" psql -h "$pg_host" -p "$pg_port" -U "$pg_user" -v ON_ERROR_STOP=1 -d "$staging_db" -f "$dump_path" >/dev/null
  else
    PGPASSWORD="$pg_password" pg_restore -h "$pg_host" -p "$pg_port" -U "$pg_user" -v -d "$staging_db" "$dump_path" >/dev/null
  fi
  PGPASSWORD="$pg_password" psql -h "$pg_host" -p "$pg_port" -U "$pg_user" -v ON_ERROR_STOP=1 -d "$staging_db" -tAc 'SELECT 1' >/dev/null

  database_exists="$(runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$pg_db'" postgres | tr -d '[:space:]')"
  if [[ "$database_exists" == "1" ]]; then
    runuser -u postgres -- psql -v ON_ERROR_STOP=1 postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$pg_db' AND pid <> pg_backend_pid();
ALTER DATABASE "$pg_db" RENAME TO "$backup_db";
ALTER DATABASE "$staging_db" RENAME TO "$pg_db";
SQL
    COPIMINE_DATABASE_RESTORE_ACTIVE_DB="$pg_db"
    COPIMINE_DATABASE_RESTORE_BACKUP_DB="$backup_db"
    copimine_log "Database restore activated. Previous database is preserved as $backup_db."
  else
    runuser -u postgres -- psql -v ON_ERROR_STOP=1 postgres -c "ALTER DATABASE \"$staging_db\" RENAME TO \"$pg_db\";"
    copimine_log "Database restore activated for newly created database $pg_db."
  fi
}

copimine_rollback_database_restore() {
  local active_db="$COPIMINE_DATABASE_RESTORE_ACTIVE_DB"
  local backup_db="$COPIMINE_DATABASE_RESTORE_BACKUP_DB"
  [[ -n "$active_db" && -n "$backup_db" ]] || return 0
  [[ "$active_db" =~ ^[A-Za-z_][A-Za-z0-9_]{0,40}$ ]] || copimine_fail "Unsafe active database rollback identifier"
  [[ "$backup_db" =~ ^[A-Za-z_][A-Za-z0-9_]{0,62}$ ]] || copimine_fail "Unsafe backup database rollback identifier"
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$active_db' AND pid <> pg_backend_pid();
ALTER DATABASE "$active_db" RENAME TO "${active_db}_failed_$(date +%s)";
ALTER DATABASE "$backup_db" RENAME TO "$active_db";
SQL
  COPIMINE_DATABASE_RESTORE_ACTIVE_DB=""
  COPIMINE_DATABASE_RESTORE_BACKUP_DB=""
  copimine_log "Restored the database that was active before the failed replacement."
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

copimine_sync_server_secrets() {
  local rcon_password
  rcon_password="$(copimine_env_value RCON_PASSWORD)"
  [[ -n "$rcon_password" && "$rcon_password" != "CHANGE_ME" ]] || copimine_fail "RCON_PASSWORD is missing in $COPIMINE_ENV_FILE"
  COPIMINE_RCON_PASSWORD="$rcon_password" python3 - "$COPIMINE_SERVER_PROPERTIES" <<'PY'
import os
from pathlib import Path
import sys

path = Path(sys.argv[1])
rcon_password = os.environ["COPIMINE_RCON_PASSWORD"]
lines = path.read_text(encoding="utf-8").splitlines()
updates = {
    "rcon.password": rcon_password,
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

copimine_sync_server_properties() {
  local resourcepack_sha1 resourcepack_url public_panel_url
  resourcepack_sha1="$(sha1sum "$COPIMINE_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
  resourcepack_url="$(copimine_env_value RESOURCE_PACK_PUBLIC_URL)"
  # Older .env files sometimes stored an URL escaped for server.properties
  # (http\://...). Normalize that form before validating it.
  resourcepack_url="${resourcepack_url//\\/}"
  if [[ ! "$resourcepack_url" =~ ^https?:// ]]; then
    public_panel_url="$(copimine_env_value PUBLIC_PANEL_URL)"
    public_panel_url="${public_panel_url//\\/}"
    [[ "$public_panel_url" =~ ^https?:// ]] || public_panel_url="http://admin.copimine.ru:18080"
    resourcepack_url="${public_panel_url%/}/resourcepacks/CopiMineResourcePack.zip"
  fi
  [[ "$resourcepack_url" =~ ^https?:// ]] || copimine_fail "RESOURCE_PACK_PUBLIC_URL must use http:// or https://"
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
  copimine_sync_server_secrets
}

copimine_sync_runtime_urls() {
  local public_panel_url
  public_panel_url="$(copimine_env_value PUBLIC_PANEL_URL)"
  public_panel_url="${public_panel_url//\\/}"
  if [[ ! "$public_panel_url" =~ ^https?:// ]]; then
    public_panel_url="http://admin.copimine.ru:18080"
  fi
  # Persist normalized URLs so the web process and later verification steps
  # use the same value, even when an old .env contained escaped values.
  python3 - "$COPIMINE_ENV_FILE" "$public_panel_url" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
panel = sys.argv[2].rstrip('/')
updates = {
    'PUBLIC_PANEL_URL': panel,
    'RESOURCE_PACK_PUBLIC_URL': panel + '/resourcepacks/CopiMineResourcePack.zip',
}
lines = path.read_text(encoding='utf-8-sig', errors='replace').splitlines() if path.exists() else []
out, seen = [], set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in updates:
        out.append(f'{key}={updates[key]}')
        seen.add(key)
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')
tmp = path.with_name('.env.urls-tmp')
tmp.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
tmp.chmod(0o600)
tmp.replace(path)
PY
  [[ "$public_panel_url" =~ ^https?:// ]] || copimine_fail "PUBLIC_PANEL_URL must use http:// or https://"
  COPIMINE_PUBLIC_PANEL_URL="$public_panel_url" python3 - \
    "$COPIMINE_ROOT/minecraft/server/plugins/CopiMineArtifacts/config.yml" \
    "$COPIMINE_ROOT/copimine-artifacts/config.yml" \
    "$COPIMINE_ROOT/minecraft/server/plugins/CopiMineArtifacts/items.yml" \
    "$COPIMINE_ROOT/copimine-artifacts/items.yml" <<'PY'
import os
from pathlib import Path
import sys

url = os.environ["COPIMINE_PUBLIC_PANEL_URL"].rstrip("/")

def update_mapping(path: Path, section: str) -> None:
    if not path.is_file():
        return
    lines = path.read_text(encoding="utf-8").splitlines()
    output: list[str] = []
    in_section = False
    replaced = False
    section_indent = ""
    for line in lines:
        stripped = line.lstrip()
        indent = line[: len(line) - len(stripped)]
        if line == f"{section}:":
            in_section = True
            section_indent = indent
            output.append(line)
            continue
        if in_section and stripped and not stripped.startswith("#") and len(indent) <= len(section_indent):
            if not replaced:
                output.append(f'{section_indent}  website-base-url: "{url}"')
                replaced = True
            in_section = False
        if in_section and stripped.startswith("website-base-url:"):
            output.append(f'{indent}website-base-url: "{url}"')
            replaced = True
        else:
            output.append(line)
    if in_section and not replaced:
        output.append(f'{section_indent}  website-base-url: "{url}"')
    path.write_text("\n".join(output) + "\n", encoding="utf-8")

for candidate in map(Path, sys.argv[1:]):
    update_mapping(candidate, "donation" if candidate.name == "config.yml" else "donation-catalog")
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

copimine_render_nginx_config() {
  local template tls_enabled cert_path key_path server_names
  tls_enabled="${COPIMINE_TLS_ENABLED:-$(copimine_env_value COPIMINE_TLS_ENABLED)}"
  tls_enabled="${tls_enabled:-0}"
  server_names="${COPIMINE_SERVER_NAMES:-$(copimine_env_value NGINX_SERVER_NAMES)}"
  server_names="${server_names:-admin.copimine.ru copimine.ru www.copimine.ru}"
  template="$COPIMINE_NGINX_TEMPLATE"
  cert_path=""
  key_path=""
  case "$tls_enabled" in
    0)
      ;;
    1)
      template="$COPIMINE_NGINX_TLS_TEMPLATE"
      cert_path="${COPIMINE_TLS_CERT_PATH:-$(copimine_env_value COPIMINE_TLS_CERT_PATH)}"
      key_path="${COPIMINE_TLS_KEY_PATH:-$(copimine_env_value COPIMINE_TLS_KEY_PATH)}"
      [[ -n "$cert_path" && -f "$cert_path" ]] || copimine_fail "COPIMINE_TLS_CERT_PATH must point to a readable certificate when TLS is enabled"
      [[ -n "$key_path" && -f "$key_path" ]] || copimine_fail "COPIMINE_TLS_KEY_PATH must point to a readable private key when TLS is enabled"
      ;;
    *)
      copimine_fail "COPIMINE_TLS_ENABLED must be 0 or 1"
      ;;
  esac
  copimine_require_path "$template"
  COPIMINE_NGINX_SERVER_NAMES="$server_names" \
  COPIMINE_NGINX_TLS_CERT_PATH="$cert_path" \
  COPIMINE_NGINX_TLS_KEY_PATH="$key_path" \
  python3 - "$template" "$COPIMINE_NGINX_AVAILABLE" <<'PY'
import os
from pathlib import Path
import sys

template = Path(sys.argv[1]).read_text(encoding="utf-8")
target = Path(sys.argv[2])
replacements = {
    "__COPIMINE_SERVER_NAMES__": os.environ["COPIMINE_NGINX_SERVER_NAMES"],
    "__COPIMINE_TLS_CERT_PATH__": os.environ["COPIMINE_NGINX_TLS_CERT_PATH"],
    "__COPIMINE_TLS_KEY_PATH__": os.environ["COPIMINE_NGINX_TLS_KEY_PATH"],
}
for marker, value in replacements.items():
    template = template.replace(marker, value)
if "__COPIMINE_" in template:
    raise SystemExit("Unresolved CopiMine nginx template marker")
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(template, encoding="utf-8")
PY
  chmod 644 "$COPIMINE_NGINX_AVAILABLE"
}

copimine_install_system_files() {
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-admin.service" /etc/systemd/system/copimine-admin.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-discord-bot.service" /etc/systemd/system/copimine-discord-bot.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-minecraft-discord-bridge.service" /etc/systemd/system/copimine-minecraft-discord-bridge.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-minecraft.service" /etc/systemd/system/copimine-minecraft.service
  install -m 0644 "$COPIMINE_ADMIN_DIR/deploy/copimine-game-hardening.service" /etc/systemd/system/copimine-game-hardening.service
  copimine_render_nginx_config
  ln -sfn "$COPIMINE_NGINX_AVAILABLE" "$COPIMINE_NGINX_ENABLED"
  rm -f /etc/nginx/sites-enabled/default
  nginx -t
  systemctl daemon-reload
  # Recreate the install symlink when the unit's WantedBy target changes
  # between releases; a plain enable would retain an obsolete boot target.
  systemctl reenable copimine-game-hardening.service >/dev/null
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
  copimine_sync_runtime_urls
  copimine_sync_game_runtime_hardening
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
  chmod 700 "$COPIMINE_BACKUP_DIR"
  (
    umask 077
    tar -C /opt -czf "$backup_path" \
      "$(basename "$COPIMINE_ROOT")" \
      "$(basename "$COPIMINE_SECRETS_DIR")"
  )
  chmod 600 "$backup_path"
  sha256sum "$backup_path" | awk '{print $1}' > "${backup_path}.sha256"
  chmod 600 "${backup_path}.sha256"
  printf '%s\n' "$backup_path"
}

copimine_wipe_worlds() {
  local world existing_seed
  for world in world world_nether world_the_end; do
    if [[ -d "$COPIMINE_SERVER_DIR/$world" ]]; then
      rm -rf -- "$COPIMINE_SERVER_DIR/$world"
      copimine_log "Removed world directory: $COPIMINE_SERVER_DIR/$world"
    fi
  done
  # A world reset must regenerate the same seed, not silently switch to the
  # template default. Operators can still override it with KEEP_WORLD_SEED=0.
  if [[ "${KEEP_WORLD_SEED:-1}" == "1" && -f "$COPIMINE_SERVER_PROPERTIES" ]]; then
    existing_seed="$(awk -F= '$1=="level-seed" {print substr($0,index($0,"=")+1); exit}' "$COPIMINE_SERVER_PROPERTIES")"
    [[ -n "$existing_seed" ]] && COPIMINE_WORLD_SEED="$existing_seed"
  fi
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
  copimine_http_wait "http://127.0.0.1:8090/api/health" "" 90 || copimine_fail "/api/health failed"
  copimine_http_wait "http://127.0.0.1:8090/api/runtime" "" 90 || copimine_fail "/api/runtime failed"
  copimine_verify_public_endpoints
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
  copimine_ensure_app_user
  local postgres_password secret_key plugin_api_key rcon_password
  postgres_password="$(copimine_secret postgres-password.txt 24)"
  secret_key="$(copimine_secret secret-key.txt 32)"
  plugin_api_key="$(copimine_secret plugin-api-key.txt 32)"
  rcon_password="$(copimine_secret rcon-password.txt 32)"
  copimine_write_env "$postgres_password" "$secret_key" "$plugin_api_key" "$rcon_password"
  chown -R "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_ROOT"
  copimine_ensure_postgres "$postgres_password"
  copimine_apply_migrations
  copimine_python_env
  copimine_build_assets
  copimine_refresh_release_artifacts
  copimine_install_system_files
  copimine_validate_release_contract
}
