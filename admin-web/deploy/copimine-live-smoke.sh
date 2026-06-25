#!/usr/bin/env bash
set -euo pipefail

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
ADMIN_WEB="$ROOT/admin-web"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8090}"
POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-copimine}"
POSTGRES_USER="${POSTGRES_USER:-copimine}"
POSTGRES_SCHEMA="${POSTGRES_SCHEMA:-copimine}"
HEALTH_ENDPOINT="${HEALTH_ENDPOINT:-$BACKEND_URL/api/health}"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

echo "[1/6] Tooling"
need systemctl
need curl
need python3
need psql

echo "[2/6] systemd units"
systemctl is-active --quiet copimine-admin
systemctl is-active --quiet copimine-discord-bot
systemctl is-active --quiet minecraft || echo "minecraft service is not active; continuing with web/database checks"

echo "[3/6] Backend health"
curl -fsS "$HEALTH_ENDPOINT" >/dev/null

echo "[4/6] Python smoke"
cd "$ADMIN_WEB"
if [[ -x "$ADMIN_WEB/.venv/bin/python" ]]; then
  "$ADMIN_WEB/.venv/bin/python" scripts/backend_smoketest.py
else
  python3 scripts/backend_smoketest.py
fi

echo "[5/6] PostgreSQL schema probes"
psql \
  -h "$POSTGRES_HOST" \
  -p "$POSTGRES_PORT" \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 \
  -c "SET search_path TO $POSTGRES_SCHEMA; SELECT count(*) FROM information_schema.tables WHERE table_schema = '$POSTGRES_SCHEMA';" \
  -c "SET search_path TO $POSTGRES_SCHEMA; SELECT count(*) FROM site_accounts;" \
  -c "SET search_path TO $POSTGRES_SCHEMA; SELECT count(*) FROM discord_status_state;" \
  -c "SET search_path TO $POSTGRES_SCHEMA; SELECT count(*) FROM auth_migration_state;" >/dev/null

echo "[6/6] systemd logs tail"
journalctl -u copimine-admin -n 20 --no-pager
journalctl -u copimine-discord-bot -n 20 --no-pager || true

echo "CopiMine live smoke OK"
