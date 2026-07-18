#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
ADMIN_DIR="$ROOT/admin-web"
ENV_FILE="$ADMIN_DIR/.env"
ENV_EXAMPLE="$ADMIN_DIR/.env.example"
PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
PG_PORT="${POSTGRES_PORT:-5432}"
PG_DB="${POSTGRES_DB:-copimine}"
PG_USER="${POSTGRES_USER:-copimine}"
PG_SCHEMA="${POSTGRES_SCHEMA:-copimine}"
MC_SERVICE="${MINECRAFT_SERVICE:-copimine-minecraft}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/copimine-backups}"
PASSWORD_FILE="${PG_PASSWORD_FILE:-$ADMIN_DIR/.postgres-password}"
LOG_FILE="/tmp/copimine-postgres-repair-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1

fail() { echo; echo "ERROR: $*"; echo "Log: $LOG_FILE"; exit 1; }
trap 'fail "Command failed at line $LINENO."' ERR

[[ "${EUID:-$(id -u)}" -eq 0 ]] || fail "Run this script through sudo."
[[ "${1:-}" == "--recreate-db" ]] || fail "This repair recreates the database. Run with --recreate-db."
[[ "${COPIMINE_CONFIRM_DB_RECREATE:-}" == "YES" ]] || fail "Set COPIMINE_CONFIRM_DB_RECREATE=YES to confirm the database recreation."
for cmd in openssl python3 psql pg_isready runuser systemctl; do
  command -v "$cmd" >/dev/null 2>&1 || fail "Missing command: $cmd"
done
for value in "$PG_DB" "$PG_USER" "$PG_SCHEMA"; do
  [[ "$value" =~ ^[A-Za-z_][A-Za-z0-9_]{0,48}$ ]] || fail "Unsafe PostgreSQL identifier: $value"
done

echo "CopiMine PostgreSQL repair started"
echo "Log: $LOG_FILE"
runuser -u postgres -- pg_isready -q || { systemctl start postgresql; sleep 3; }
runuser -u postgres -- pg_isready -q || fail "PostgreSQL is not ready."

PG_PASSWORD="$(openssl rand -hex 48)"
[[ "${#PG_PASSWORD}" -eq 96 ]] || fail "Could not generate PostgreSQL password."
install -d -m 0700 "$BACKUP_ROOT"
install -d -m 0755 "$ADMIN_DIR"
printf '%s\n' "$PG_PASSWORD" > "$PASSWORD_FILE"
chmod 600 "$PASSWORD_FILE"
echo "Generated a new database password in $PASSWORD_FILE (not printed)."
SQL_FILE="$(mktemp /tmp/copimine-pg-repair-XXXXXX.sql)"
chmod 600 "$SQL_FILE"
trap 'rm -f -- "$SQL_FILE"; fail "Command failed at line $LINENO."' ERR
cat >"$SQL_FILE" <<SQL
\set ON_ERROR_STOP on
\set pg_password '$PG_PASSWORD'
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$PG_USER') THEN
    CREATE ROLE "$PG_USER" LOGIN;
  END IF;
END \$\$;
ALTER ROLE "$PG_USER" LOGIN PASSWORD :'pg_password';
SQL
runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d postgres -f "$SQL_FILE"
rm -f -- "$SQL_FILE"
trap 'fail "Command failed at line $LINENO."' ERR

DB_EXISTS="$(runuser -u postgres -- psql -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname = '$PG_DB';")"
if [[ "$DB_EXISTS" == "1" ]]; then
  BACKUP_DUMP="$BACKUP_ROOT/copimine-pre-recreate-$(date +%Y%m%d-%H%M%S).dump"
  runuser -u postgres -- pg_dump -Fc -d "$PG_DB" -f "$BACKUP_DUMP"
  chmod 600 "$BACKUP_DUMP"
  echo "Database backup saved to $BACKUP_DUMP"
  for service in copimine-admin copimine-discord-bot copimine-minecraft-discord-bridge "$MC_SERVICE"; do
    systemctl stop "$service" 2>/dev/null || true
  done
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$PG_DB' AND pid <> pg_backend_pid();"
  runuser -u postgres -- dropdb --if-exists "$PG_DB"
fi
runuser -u postgres -- createdb --owner="$PG_USER" "$PG_DB"
runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d "$PG_DB" <<SQL
CREATE SCHEMA IF NOT EXISTS "$PG_SCHEMA" AUTHORIZATION "$PG_USER";
GRANT USAGE, CREATE ON SCHEMA "$PG_SCHEMA" TO "$PG_USER";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA "$PG_SCHEMA" TO "$PG_USER";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA "$PG_SCHEMA" TO "$PG_USER";
ALTER DEFAULT PRIVILEGES IN SCHEMA "$PG_SCHEMA" GRANT ALL ON TABLES TO "$PG_USER";
ALTER DEFAULT PRIVILEGES IN SCHEMA "$PG_SCHEMA" GRANT ALL ON SEQUENCES TO "$PG_USER";
SQL

install -d -m 0755 "$ADMIN_DIR"
[[ -f "$ENV_FILE" ]] || cp "$ENV_EXAMPLE" "$ENV_FILE"
ENV_FILE="$ENV_FILE" PG_PASSWORD="$PG_PASSWORD" PG_HOST="$PG_HOST" PG_PORT="$PG_PORT" PG_DB="$PG_DB" PG_USER="$PG_USER" PG_SCHEMA="$PG_SCHEMA" MC_SERVICE="$MC_SERVICE" python3 - <<'PY'
import os, re, tempfile
from pathlib import Path
path = Path(os.environ['ENV_FILE'])
values = {
    'POSTGRES_HOST': os.environ['PG_HOST'], 'POSTGRES_PORT': os.environ['PG_PORT'],
    'POSTGRES_DB': os.environ['PG_DB'], 'POSTGRES_USER': os.environ['PG_USER'],
    'POSTGRES_SCHEMA': os.environ['PG_SCHEMA'], 'POSTGRES_PASSWORD': os.environ['PG_PASSWORD'],
    'DATABASE_URL': f"postgresql://{os.environ['PG_USER']}:{os.environ['PG_PASSWORD']}@{os.environ['PG_HOST']}:{os.environ['PG_PORT']}/{os.environ['PG_DB']}",
    'COPIMINE_ENV_FILE': str(path), 'MINECRAFT_SERVICE': os.environ['MC_SERVICE'],
}
lines = path.read_text(encoding='utf-8', errors='replace').splitlines() if path.exists() else []
pattern = re.compile(r'^([A-Za-z_][A-Za-z0-9_]*)=.*$')
seen, result = set(), []
for line in lines:
    match = pattern.match(line.strip())
    if match and match.group(1) in values:
        key = match.group(1); result.append(f'{key}={values[key]}'); seen.add(key)
    else: result.append(line)
for key, value in values.items():
    if key not in seen: result.append(f'{key}={value}')
fd, temporary = tempfile.mkstemp(dir=path.parent, prefix='.env.', text=True)
with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as output:
    output.write('\n'.join(result).rstrip() + '\n')
os.chmod(temporary, 0o600); os.replace(temporary, path)
PY

if [[ -x "$ROOT/deploy/ubuntu/migrate.sh" ]]; then
  COPIMINE_ROOT="$ROOT" bash "$ROOT/deploy/ubuntu/migrate.sh"
fi

SERVICE_USER="$(systemctl show "$MC_SERVICE" -p User --value 2>/dev/null || true)"
SERVICE_USER="${SERVICE_USER:-${SUDO_USER:-copimine}}"
id "$SERVICE_USER" >/dev/null 2>&1 || fail "Minecraft service user does not exist: $SERVICE_USER"
chown "$SERVICE_USER:$(id -gn "$SERVICE_USER")" "$ENV_FILE"
chmod 600 "$ENV_FILE"
chown "$SERVICE_USER:$(id -gn "$SERVICE_USER")" "$PASSWORD_FILE"
chmod 600 "$PASSWORD_FILE"
PGPASSWORD="$PG_PASSWORD" psql "host=$PG_HOST port=$PG_PORT dbname=$PG_DB user=$PG_USER connect_timeout=8" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user;" >/dev/null
echo "PostgreSQL authentication succeeded. Password was not displayed."

if systemctl cat "$MC_SERVICE" >/dev/null 2>&1; then
  systemctl restart "$MC_SERVICE"
  sleep 15
  systemctl is-active --quiet "$MC_SERVICE" || fail "$MC_SERVICE is not active."
else
  echo "WARNING: $MC_SERVICE was not found; database repair is complete."
fi
echo "SUCCESS: PostgreSQL, schema access and .env configuration are ready."
