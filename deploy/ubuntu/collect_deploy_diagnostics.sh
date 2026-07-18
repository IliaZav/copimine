#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
export PAGER=cat
export PSQL_PAGER=cat

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
MODE="archive"
if [[ "${1:-}" == "--stdout" ]]; then
  MODE="stdout"
  shift
fi
OUT="${1:-/tmp/copimine-diagnostics-$(date +%Y%m%d-%H%M%S)}"
ARCHIVE="${OUT}.tar.gz"
mkdir -p "$OUT"
chmod 700 "$OUT"

log() { printf '[copimine-diagnostics] %s\n' "$*"; }
run_capture() { local name="$1"; shift; "$@" >"$OUT/$name" 2>&1 || printf 'command failed (exit %s)\n' "$?" >>"$OUT/$name"; }
env_value() {
  local key="$1" file="${COPIMINE_ENV_FILE:-$ROOT/admin-web/.env}"
  [[ -f "$file" ]] || return 0
  awk -F= -v k="$key" '$1==k {sub(/^[^=]*=/,"",$0); print $0; exit}' "$file" | sed "s/^['\"]//;s/['\"]$//"
}

log "Collecting sanitized deployment diagnostics in $OUT"
printf 'generated_at=%s\nroot=%s\nscript_version=1\n' "$(date -Is)" "$ROOT" >"$OUT/summary.txt"
run_capture os.txt uname -a
run_capture os-release.txt cat /etc/os-release
run_capture disk.txt df -h "$ROOT"
run_capture memory.txt free -h
run_capture services.txt systemctl --no-pager --plain --full status copimine-admin copimine-minecraft copimine-discord-bot copimine-minecraft-discord-bridge nginx
for service in copimine-admin copimine-minecraft copimine-discord-bot copimine-minecraft-discord-bridge nginx; do
  run_capture "journal-$service.txt" journalctl -u "$service" -n 250 --no-pager
done

if [[ -f "$ROOT/admin-web/.env" ]]; then
  python3 - "$ROOT/admin-web/.env" "$OUT/env-sanitized.txt" <<'PY'
from pathlib import Path
import re, sys
src, dst = map(Path, sys.argv[1:])
secret = re.compile(r'(password|secret|token|api.?key|cookie|private|dsn|database_url|rcon)', re.I)
out=[]
for raw in src.read_text(encoding='utf-8', errors='replace').splitlines():
    if not raw or raw.lstrip().startswith('#') or '=' not in raw:
        out.append(raw); continue
    key, _ = raw.split('=', 1)
    out.append(f'{key}=<redacted>' if secret.search(key) else raw)
dst.write_text('\n'.join(out)+'\n', encoding='utf-8')
PY
fi

if [[ -f "$ROOT/minecraft/server/server.properties" ]]; then
  sed -E 's/^(rcon\.password|resource-pack-sha1)=.*/\1=<redacted>/' "$ROOT/minecraft/server/server.properties" >"$OUT/server.properties.sanitized"
fi

python3 - "$ROOT/minecraft/server/plugins" "$OUT/plugins.tsv" <<'PY'
from pathlib import Path
import hashlib, re, sys, zipfile
root, out = map(Path, sys.argv[1:])
rows=['jar\tversion\tsha256\tsize']
for jar in sorted(root.glob('*.jar')):
    version='unknown'
    try:
        with zipfile.ZipFile(jar) as z:
            for name in ('plugin.yml','paper-plugin.yml'):
                if name in z.namelist():
                    text=z.read(name).decode('utf-8','replace')
                    match=re.search(r'''(?m)^version:\s*["']?([^"'\r\n]+)''', text)
                    if match: version=match.group(1).strip()
                    break
    except Exception:
        version='unreadable'
    h=hashlib.sha256(jar.read_bytes()).hexdigest()
    rows.append(f'{jar.name}\t{version}\t{h}\t{jar.stat().st_size}')
out.write_text('\n'.join(rows)+'\n', encoding='utf-8')
PY

: >"$OUT/artifacts.sha256"
for file in "$ROOT/resourcepacks/build/CopiMineResourcePack.zip" "$ROOT/thirdparty/CopiMineMods.zip"; do
  [[ -f "$file" ]] && sha256sum "$file" >>"$OUT/artifacts.sha256"
done

PG_HOST="$(env_value POSTGRES_HOST)"; PG_PORT="$(env_value POSTGRES_PORT)"; PG_DB="$(env_value POSTGRES_DB)"; PG_USER="$(env_value POSTGRES_USER)"; PG_PASSWORD="$(env_value POSTGRES_PASSWORD)"
if [[ -n "$PG_USER" && -n "$PG_PASSWORD" && -n "$PG_DB" ]] && command -v psql >/dev/null 2>&1; then
  PGPASSWORD="$PG_PASSWORD" psql "host=${PG_HOST:-127.0.0.1} port=${PG_PORT:-5432} dbname=$PG_DB user=$PG_USER connect_timeout=8" -Atqc "SELECT current_database(), current_user; SELECT table_schema, count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') GROUP BY table_schema ORDER BY table_schema;" >"$OUT/database-sanity.txt" 2>&1 || true
else
  echo 'Database credentials were not available; no database connection attempted.' >"$OUT/database-sanity.txt"
fi

if command -v curl >/dev/null 2>&1; then
  curl -fsS --max-time 10 http://127.0.0.1:18080/api/runtime >"$OUT/http-runtime.json" 2>&1 || true
fi

if [[ "$MODE" == "stdout" ]]; then
  printf '\n===== COPIMINE DIAGNOSTICS BEGIN =====\n'
  for file in "$OUT"/*; do
    [[ -f "$file" ]] || continue
    printf '\n===== %s =====\n' "$(basename "$file")"
    cat "$file"
  done
  printf '\n===== COPIMINE DIAGNOSTICS END =====\n'
  rm -rf "$OUT"
else
  tar -czf "$ARCHIVE" -C "$(dirname "$OUT")" "$(basename "$OUT")"
  chmod 600 "$ARCHIVE"
  if [[ -n "${SUDO_USER:-}" ]] && id "$SUDO_USER" >/dev/null 2>&1; then
    chown "$SUDO_USER:$(id -gn "$SUDO_USER")" "$ARCHIVE"
  fi
  rm -rf "$OUT"
  log "Created $ARCHIVE"
  printf '%s\n' "$ARCHIVE"
fi
