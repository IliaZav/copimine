#!/usr/bin/env bash
set -Eeuo pipefail

# Safe release entrypoint used by the Windows uploader. It verifies the archive
# and delegates extraction/rollback to the hardened installer already shipped
# with the release. Runtime data and the database are preserved by default.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
LOG_FILE="${COPIMINE_INSTALL_LOG:-/var/log/copimine-install.log}"
mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"
chmod 600 "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1
ARCHIVE_PATH="${1:-}"
ARCHIVE_SHA256=""
DB_DUMP_PATH=""
WIPE_DB=0
WIPE_WORLDS=0
RESET_GAMEPLAY=0
RESET_TREASURY=0

usage() { printf 'Usage: sudo bash %s /path/to/release.tar.gz [sha256] [--wipe-worlds] [--db-dump path] [--reset-treasury]\n       sudo bash %s --cleanup-zabbix\n       sudo bash %s --cleanup-external-services\n' "$0" "$0" "$0" >&2; }
[[ "${EUID:-$(id -u)}" -eq 0 ]] || { echo 'Run this installer with sudo/root.' >&2; exit 2; }

cleanup_zabbix() {
  echo '[zabbix] stopping services'
  systemctl disable --now zabbix-agent.service zabbix-server.service 2>/dev/null || true

  echo '[zabbix] removing packages and unused dependencies'
  export DEBIAN_FRONTEND=noninteractive
  apt-get purge -y zabbix-agent zabbix-frontend-php zabbix-nginx-conf zabbix-release \
    zabbix-server-mysql zabbix-sql-scripts 2>&1 || true
  apt-get autoremove -y 2>&1 || true

  echo '[zabbix] removing configuration, logs and web pool'
  rm -rf -- /etc/zabbix /var/lib/zabbix /var/log/zabbix /var/cache/zabbix \
    /usr/share/zabbix /usr/share/zabbix-sql-scripts
  rm -f -- /etc/php/*/fpm/pool.d/zabbix-php-fpm.conf \
    /etc/nginx/disabled-duplicates-*/zabbix.conf.disabled \
    /etc/apt/sources.list.d/zabbix.list /etc/apt/sources.list.d/zabbix-tools.list \
    /etc/apt/trusted.gpg.d/zabbix-official-repo.gpg \
    /etc/apt/trusted.gpg.d/zabbix-official-repo-apr2024.gpg \
    /etc/apt/trusted.gpg.d/zabbix-tools.gpg
  rm -f -- /home/qwerty/zabbix-release*.deb
  rm -f -- /var/lib/apt/lists/repo.zabbix.com_*
  find /etc/systemd/system -path '*zabbix*' -type l -delete 2>/dev/null || true
  systemctl daemon-reload
  systemctl restart php8.3-fpm.service 2>/dev/null || true
  systemctl reload nginx.service 2>/dev/null || true

  if command -v mysql >/dev/null 2>&1; then
    echo '[zabbix] checking MySQL schema'
    mapfile -t zabbix_dbs < <(mysql --protocol=socket --batch --skip-column-names \
      -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME IN ('zabbix','zabbix_proxy');" 2>/dev/null || true)
    for db in "${zabbix_dbs[@]}"; do
      [[ "$db" =~ ^zabbix(_proxy)?$ ]] || continue
      mysql --protocol=socket --batch --skip-column-names -e "DROP DATABASE IF EXISTS \`$db\`;" \
        && echo "[zabbix] dropped MySQL database: $db" || echo "[zabbix] could not drop MySQL database: $db" >&2
    done
    mysql --protocol=socket --batch --skip-column-names -e \
      "DROP USER IF EXISTS 'zabbix'@'localhost','zabbix'@'127.0.0.1','zabbix'@'%'; FLUSH PRIVILEGES;" 2>/dev/null || true
  fi
  if getent passwd zabbix >/dev/null 2>&1; then userdel --remove zabbix 2>/dev/null || true; fi
  if getent group zabbix >/dev/null 2>&1; then groupdel zabbix 2>/dev/null || true; fi

  local remaining_zabbix
  remaining_zabbix="$(ps -eo pid=,args= | awk '$0 ~ /[z]abbix/ && $0 !~ /install_release\.sh --cleanup-zabbix/ && $0 !~ /awk.*zabbix/ {print}')"
  if [[ -n "$remaining_zabbix" ]]; then
    echo '[zabbix] WARNING: a Zabbix process is still present' >&2
    printf '%s\n' "$remaining_zabbix" >&2
    return 1
  fi
  echo '[zabbix] cleanup complete'
}

cleanup_external_services() {
  local stamp backup_root php_packages
  stamp="$(date +%Y%m%d-%H%M%S)"
  backup_root="/opt/copimine-backups/external-services-$stamp"
  mkdir -p "$backup_root"
  chmod 700 "$backup_root"
  echo "[cleanup] backup directory: $backup_root"

  # These services are not used by CopiMine. Stop them before taking a backup
  # so the retained copies are consistent, then remove only their packages.
  systemctl disable --now zapret_discord_youtube.service 2>/dev/null || true
  systemctl disable --now postfix-mta-sts-resolver.service php8.3-fpm.service 2>/dev/null || true
  systemctl disable --now mysql.service mssql-server.service 2>/dev/null || true

  if [[ -d /var/lib/mysql ]]; then
    tar -czf "$backup_root/mysql-data.tar.gz" -C / var/lib/mysql etc/mysql 2>/dev/null || true
  fi
  if [[ -d /var/opt/mssql || -d /opt/mssql ]]; then
    tar -czf "$backup_root/mssql-data.tar.gz" -C / var/opt/mssql opt/mssql etc/opt/mssql 2>/dev/null || true
  fi
  if [[ -d /opt/zapret-discord-youtube-linux ]]; then
    tar -czf "$backup_root/zapret-config.tar.gz" -C / opt/zapret-discord-youtube-linux etc/systemd/system/zapret_discord_youtube.service 2>/dev/null || true
  fi
  systemctl list-unit-files --no-legend >"$backup_root/unit-files.txt" 2>/dev/null || true
  dpkg-query -W -f='${binary:Package}\t${Version}\n' >"$backup_root/packages.txt" 2>/dev/null || true

  export DEBIAN_FRONTEND=noninteractive
  php_packages="$(dpkg-query -W -f='${binary:Package}\n' 2>/dev/null | grep -E '^php8\.3-|^php-mysql$|^php-common$' || true)"
  apt-get purge -y \
    mssql-server mssql-tools18 msodbcsql18 \
    mysql-server mysql-server-8.0 mysql-server-core-8.0 mysql-client-8.0 mysql-client-core-8.0 mysql-common \
    postfix-mta-sts-resolver \
    $php_packages 2>&1 || true

  rm -rf -- /opt/zapret-discord-youtube-linux
  rm -f -- /etc/systemd/system/zapret_discord_youtube.service

  # Hardware/desktop helpers are safe to disable on this headless server. They
  # are left installed so Ubuntu can be recovered without reinstalling it.
  for unit in ModemManager.service multipathd.service fwupd.service snapd.service \
              smartmontools.service upower.service udisks2.service; do
    systemctl disable --now "$unit" 2>/dev/null || true
  done

  systemctl daemon-reload
  systemctl reset-failed 2>/dev/null || true
  apt-get clean

  # Keep Java and the web panel responsive under load without changing gameplay
  # settings. These limits are reversible through the backup directory.
  install -d -m 0755 /etc/systemd/system/copimine-minecraft.service.d
  cat >/etc/systemd/system/copimine-minecraft.service.d/90-performance.conf <<'EOF'
[Service]
LimitNOFILE=1048576
Nice=-2
EOF
  install -d -m 0755 /etc/systemd/system/copimine-admin.service.d
  cat >/etc/systemd/system/copimine-admin.service.d/90-performance.conf <<'EOF'
[Service]
LimitNOFILE=65536
EOF
  cat >/etc/sysctl.d/99-copimine-performance.conf <<'EOF'
vm.swappiness=10
EOF
  sysctl --system >/dev/null 2>&1 || true
  systemctl daemon-reload

  systemctl restart copimine-admin.service
  systemctl restart nginx.service
  systemctl restart copimine-minecraft.service
  sleep 8
  for service in copimine-admin copimine-minecraft nginx postgresql@16-main; do
    systemctl is-active --quiet "$service" || { echo "[cleanup] service failed after optimization: $service" >&2; return 1; }
  done
  echo '[cleanup] external services removed and CopiMine services restarted'
  echo "[cleanup] backups retained at $backup_root"
}

refresh_resource_pack_url() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  local properties="$PROJECT_ROOT/minecraft/server/server.properties"
  [[ -f "$env_file" && -f "$properties" ]] || { echo '[pack] runtime files are missing' >&2; return 1; }
  python3 - "$env_file" "$properties" <<'PY'
from pathlib import Path
import sys

env_path, properties_path = map(Path, sys.argv[1:])
lines = env_path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
panel = 'http://copimine.ru:18080'
for line in lines:
    if line.startswith('PUBLIC_PANEL_URL='):
        panel = line.split('=', 1)[1].strip().strip('"').strip("'").rstrip('/')
pack_url = panel + '/resourcepacks/CopiMineResourcePack.zip?v=20260720r2'
out, seen = [], set()
for line in lines:
    key = line.split('=', 1)[0] if '=' in line else ''
    if key == 'RESOURCE_PACK_PUBLIC_URL':
        out.append(f'{key}={pack_url}')
        seen.add(key)
    else:
        out.append(line)
if 'RESOURCE_PACK_PUBLIC_URL' not in seen:
    out.append(f'RESOURCE_PACK_PUBLIC_URL={pack_url}')
env_path.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
props = properties_path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
escaped = pack_url.replace(':', r'\:')
out, seen = [], set()
for line in props:
    if line.startswith('resource-pack='):
        out.append('resource-pack=' + escaped)
        seen.add('resource-pack')
    else:
        out.append(line)
if 'resource-pack' not in seen:
    out.append('resource-pack=' + escaped)
properties_path.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
print(pack_url)
PY
  systemctl restart copimine-minecraft.service
  echo '[pack] cache-busting resource-pack URL applied and Minecraft restarted'
}

restore_zapret_from_official_repo() {
  local source_dir='/home/qwerty/zapret-discord-youtube-linux-src'
  local target_dir='/opt/zapret-discord-youtube-linux'
  [[ -x "$source_dir/service.sh" ]] || { echo "[zapret] source checkout is missing: $source_dir" >&2; return 1; }
  systemctl disable --now zapret_discord_youtube.service 2>/dev/null || true
  rm -rf -- "$target_dir"
  cp -a "$source_dir" "$target_dir"
  chown -R root:root "$target_dir"
  chmod 755 "$target_dir/service.sh"
  cd "$target_dir"
  bash ./service.sh download-deps --default
  local interface
  interface="$(ip route show default 2>/dev/null | awk 'NR==1 {print $5}')"
  interface="${interface:-any}"
  cat >conf.env <<EOF
interface=$interface
gamefiltertcp=true
gamefilterudp=true
strategy=general.bat
firewall_backend=auto
EOF
  bash ./service.sh service install
  systemctl enable --now zapret_discord_youtube.service
  systemctl is-active --quiet zapret_discord_youtube.service || { echo '[zapret] service failed to start' >&2; return 1; }
  echo "[zapret] installed from Sergeydigl3/zapret-discord-youtube-linux"
  git -C "$source_dir" rev-parse HEAD
  systemctl --no-pager --full status zapret_discord_youtube.service | sed -n '1,24p'
}

restart_discord_services() {
  systemctl restart copimine-discord-bot.service copimine-minecraft-discord-bridge.service
  sleep 8
  systemctl is-active --quiet copimine-discord-bot.service || { echo '[discord] bot failed to start' >&2; return 1; }
  systemctl is-active --quiet copimine-minecraft-discord-bridge.service || { echo '[discord] bridge failed to start' >&2; return 1; }
  echo '[discord] bot and Minecraft bridge restarted'
}

if [[ "$ARCHIVE_PATH" == "--cleanup-zabbix" ]]; then
  cleanup_zabbix
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--cleanup-external-services" ]]; then
  cleanup_external_services
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--refresh-resource-pack-url" ]]; then
  refresh_resource_pack_url
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--restore-zapret" ]]; then
  restore_zapret_from_official_repo
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--restart-discord" ]]; then
  restart_discord_services
  exit $?
fi
[[ -n "$ARCHIVE_PATH" ]] || { usage; exit 2; }
shift
if [[ "${1:-}" != --* && -n "${1:-}" ]]; then ARCHIVE_SHA256="$1"; shift; fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    --wipe-worlds) WIPE_WORLDS=1; RESET_GAMEPLAY=1; shift ;;
    --reset-gameplay) RESET_GAMEPLAY=1; shift ;;
    --reset-treasury) RESET_TREASURY=1; shift ;;
    --db-dump) [[ -n "${2:-}" ]] || { usage; exit 2; }; DB_DUMP_PATH="$2"; shift 2 ;;
    --wipe-db) echo 'Use repair_postgres_credentials.sh --recreate-db for an explicit database wipe.' >&2; exit 3 ;;
    *) usage; exit 2 ;;
  esac
done

preflight() {
  local actual expected dump_listing
  for command in sha256sum tar gzip python3 systemctl curl psql pg_restore pg_isready runuser; do
    command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 3; }
  done
  [[ -f "$ARCHIVE_PATH" && -s "$ARCHIVE_PATH" ]] || { echo "Archive not found or empty: $ARCHIVE_PATH" >&2; exit 3; }
  if [[ -z "$ARCHIVE_SHA256" && -f "${ARCHIVE_PATH}.sha256" ]]; then
    ARCHIVE_SHA256="$(awk '{print $1}' "${ARCHIVE_PATH}.sha256" | head -n1 | tr -d '\r\n')"
  fi
  [[ "$ARCHIVE_SHA256" =~ ^[0-9A-Fa-f]{64}$ ]] || { echo 'Archive SHA256 is invalid or missing.' >&2; exit 3; }
  actual="$(sha256sum "$ARCHIVE_PATH" | awk '{print tolower($1)}')"
  expected="${ARCHIVE_SHA256,,}"
  [[ "$actual" == "$expected" ]] || { echo "Archive SHA256 mismatch: expected=$expected actual=$actual" >&2; exit 3; }
  echo "[preflight] archive sha256 OK: $actual"
  tar -tzf "$ARCHIVE_PATH" >/dev/null
  if [[ -n "$DB_DUMP_PATH" ]]; then
    [[ -s "$DB_DUMP_PATH" ]] || { echo "Database dump not found: $DB_DUMP_PATH" >&2; exit 3; }
    dump_listing="$(mktemp /tmp/copimine-dump-list-XXXXXX)"
    pg_restore --list "$DB_DUMP_PATH" >"$dump_listing"
    rm -f -- "$dump_listing"
    echo '[preflight] external database dump is readable'
  fi
  if [[ -f "$PROJECT_ROOT/admin-web/.env" ]]; then
    local db_host db_port db_name db_user db_password
    # Strip only CRLF and double quotes. Do not use \x27 here: some tr
    # implementations interpret it as the characters 2 and 7, corrupting
    # 127.0.0.1 into 1.0.0.1 and 5432 into 543.
    db_host="$(awk -F= '$1=="POSTGRES_HOST" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_port="$(awk -F= '$1=="POSTGRES_PORT" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_name="$(awk -F= '$1=="POSTGRES_DB" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_user="$(awk -F= '$1=="POSTGRES_USER" {v=$2} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    db_password="$(awk -F= '$1=="POSTGRES_PASSWORD" {v=substr($0,index($0,"=")+1)} END{print v}' "$PROJECT_ROOT/admin-web/.env" | tr -d '\r"')"
    if [[ -n "$db_user" && -n "$db_name" && -n "$db_password" ]]; then
      local configured_host="${db_host:-127.0.0.1}" configured_port="${db_port:-5432}"
      if PGPASSWORD="$db_password" psql -h "$configured_host" -p "$configured_port" -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -Atc 'SELECT 1' >/dev/null 2>&1; then
        echo "[preflight] PostgreSQL credentials OK ($configured_host:$configured_port)"
      elif [[ "$configured_host" != "127.0.0.1" || "$configured_port" != "5432" ]] \
        && runuser -u postgres -- pg_isready -q >/dev/null 2>&1 \
        && PGPASSWORD="$db_password" psql -h 127.0.0.1 -p 5432 -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 -Atc 'SELECT 1' >/dev/null 2>&1; then
        python3 - "$PROJECT_ROOT/admin-web/.env" <<'PY'
import re, sys
from pathlib import Path
p = Path(sys.argv[1])
lines = p.read_text(encoding='utf-8', errors='replace').splitlines()
updates = {'POSTGRES_HOST': '127.0.0.1', 'POSTGRES_PORT': '5432'}
out = []
seen = set()
for line in lines:
    m = re.match(r'^([A-Za-z_][A-Za-z0-9_]*)=', line)
    if m and m.group(1) in updates:
        out.append(f"{m.group(1)}={updates[m.group(1)]}")
        seen.add(m.group(1))
    elif m and m.group(1) == 'DATABASE_URL':
        value = line.split('=', 1)[1]
        value = re.sub(r'@[^/:]+:\d+/', '@127.0.0.1:5432/', value, count=1)
        out.append(f'DATABASE_URL={value}')
        seen.add('DATABASE_URL')
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')
tmp = p.with_name('.env.install-tmp')
tmp.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
tmp.chmod(0o600)
tmp.replace(p)
PY
        echo "[preflight] PostgreSQL connection repaired: $configured_host:$configured_port -> 127.0.0.1:5432"
      else
        echo "[preflight] PostgreSQL connection failed for $configured_host:$configured_port" >&2
        echo '[preflight] Check POSTGRES_HOST/POSTGRES_PORT in /opt/copimine/admin-web/.env.' >&2
        return 1
      fi
    else
      echo '[preflight] PostgreSQL credentials are not configured yet; installer will bootstrap them.'
    fi
  else
    echo '[preflight] .env is absent; installer will create protected runtime credentials.'
  fi
}

verify_runtime() {
  local expected_sha actual_sha expected_modpack actual_modpack properties_sha
  for service in copimine-admin copimine-minecraft nginx; do
    systemctl is-active --quiet "$service" || { echo "Service is not active: $service" >&2; return 1; }
    echo "[verify] service active: $service"
  done
  expected_sha="$(sha1sum "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" | awk '{print $1}')"
  actual_sha="$(curl -fsS --max-time 30 http://127.0.0.1:18080/resourcepacks/CopiMineResourcePack.zip | sha1sum | awk '{print $1}')"
  [[ "$expected_sha" == "$actual_sha" ]] || { echo "Resource pack SHA1 mismatch: local=$expected_sha served=$actual_sha" >&2; return 1; }
  echo "[verify] resource pack SHA1 OK: $actual_sha"
  expected_modpack="$(sha256sum "$PROJECT_ROOT/thirdparty/CopiMineMods.zip" | awk '{print $1}')"
  actual_modpack="$(curl -fsS --max-time 30 http://127.0.0.1:18080/downloads/CopiMineMods.zip | sha256sum | awk '{print $1}')"
  [[ "$expected_modpack" == "$actual_modpack" ]] || { echo "Modpack SHA256 mismatch: local=$expected_modpack served=$actual_modpack" >&2; return 1; }
  echo "[verify] modpack SHA256 OK: $actual_modpack"
  grep -q '^require-resource-pack=true$' "$PROJECT_ROOT/minecraft/server/server.properties" || { echo 'require-resource-pack=true is missing' >&2; return 1; }
  properties_sha="$(sed -n 's/^resource-pack-sha1=//p' "$PROJECT_ROOT/minecraft/server/server.properties" | tr -d '\r\n')"
  [[ "$properties_sha" == "$expected_sha" ]] || { echo "server.properties resource-pack-sha1 mismatch: $properties_sha" >&2; return 1; }
  echo '[verify] server.properties resource-pack requirement and SHA1 OK'
  curl -fsS --max-time 15 http://127.0.0.1:18080/api/runtime >/dev/null
  echo '[verify] HTTP runtime endpoint OK'
}

reset_treasury() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  [[ -f "$env_file" ]] || { echo "Cannot reset treasury: missing $env_file" >&2; return 1; }
  local pg_host pg_port pg_db pg_user pg_password before tx_id now
  pg_host="$(awk -F= '$1=="POSTGRES_HOST" {v=$2} END{print v}' "$env_file" | tr -d '\r"')"
  pg_port="$(awk -F= '$1=="POSTGRES_PORT" {v=$2} END{print v}' "$env_file" | tr -d '\r"')"
  pg_db="$(awk -F= '$1=="POSTGRES_DB" {v=$2} END{print v}' "$env_file" | tr -d '\r"')"
  pg_user="$(awk -F= '$1=="POSTGRES_USER" {v=$2} END{print v}' "$env_file" | tr -d '\r"')"
  pg_password="$(awk -F= '$1=="POSTGRES_PASSWORD" {v=substr($0,index($0,"=")+1)} END{print v}' "$env_file" | tr -d '\r"')"
  pg_host="${pg_host:-127.0.0.1}"; pg_port="${pg_port:-5432}"
  [[ -n "$pg_db" && -n "$pg_user" && -n "$pg_password" ]] || { echo 'Cannot reset treasury: incomplete PostgreSQL settings.' >&2; return 1; }
  before="$(PGPASSWORD="$pg_password" psql -h "$pg_host" -p "$pg_port" -U "$pg_user" -d "$pg_db" -v ON_ERROR_STOP=1 -Atc "SELECT COALESCE(balance,0) FROM copimine.cmv4_bank_accounts WHERE account_id='PRESIDENT_BUDGET' LIMIT 1")"
  before="${before:-0}"
  [[ "$before" =~ ^[0-9]+$ ]] || { echo "Cannot reset treasury: invalid current balance '$before'." >&2; return 1; }
  if [[ "$before" == '0' ]]; then
    echo '[treasury] already at 0 AR'
    return 0
  fi
  tx_id="release-treasury-reset-$(date +%s)-$RANDOM"
  now="$(date +%s%3N)"
  PGPASSWORD="$pg_password" psql -h "$pg_host" -p "$pg_port" -U "$pg_user" -d "$pg_db" -v ON_ERROR_STOP=1 \
    -v tx_id="$tx_id" -v before="$before" -v now="$now" <<'SQL'
BEGIN;
UPDATE copimine.cmv4_bank_accounts
SET balance=0, version=version+1, updated_at=:'now'
WHERE account_id='PRESIDENT_BUDGET';
INSERT INTO copimine.cmv4_bank_ledger
  (tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details)
VALUES
  (:'tx_id','PRESIDENT_BUDGET','','','ADMIN_TREASURY_RESET',:'before',0,:'tx_id','COMMITTED',:'now','release-installer',
   json_build_object('source','release-installer','delta',-(:'before'::bigint),'reason','explicit treasury reset')::text);
COMMIT;
SQL
  echo "[treasury] reset from ${before} AR to 0 AR; audit transaction ${tx_id}"
}

enable_offline_voicechat() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  [[ -f "$env_file" ]] || return 0
  # The server is intentionally running offline-mode. The owner explicitly
  # accepted public voice chat, so persist the required exception before the
  # hardening step runs.
  python3 - "$env_file" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
updates = {
    'COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT': '1',
    'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON':
        'Offline mode is enabled; public voice chat was explicitly accepted by the server owner.',
}

lines = path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
out, seen = [], set()
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in updates:
        value = updates[key]
        if key == 'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON':
            value = '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
        out.append(f'{key}={value}')
        seen.add(key)
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        if key == 'COPIMINE_OFFLINE_VOICECHAT_EXCEPTION_REASON':
            value = '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
        out.append(f'{key}={value}')
tmp = path.with_name('.env.voicechat-tmp')
tmp.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
tmp.chmod(0o600)
tmp.replace(path)
PY
  chmod 600 "$env_file"
  echo '[preflight] Offline voice-chat exception enabled by explicit deployment request.'
}

remove_retired_frontend() {
  local frontend_root="$PROJECT_ROOT/admin-web/frontend"
  # The modern cabinet is the only supported admin surface.  These files are
  # old previews/SPA entrypoints and must not remain reachable after a full
  # replacement, otherwise browsers can cache or discover a second UI.
  rm -rf -- "$frontend_root/assets/js/legacy" "$frontend_root/assets/css/legacy.css"
  rm -f -- "$frontend_root/preview-admin.html" "$frontend_root/preview-player.html"
  echo '[cleanup] retired preview and legacy frontend files removed'
}

runtime_app_user() {
  local candidate path
  # The data directory is the authoritative owner for an existing install.
  # An old systemd unit may still mention a retired account and must not win
  # during a replacement.
  for path in "$PROJECT_ROOT/admin-web/data" "$PROJECT_ROOT/admin-web/.env"; do
    if [[ -e "$path" ]]; then
      candidate="$(stat -c '%U' "$path" 2>/dev/null || true)"
      if [[ -n "$candidate" && "$candidate" != "root" ]] && id "$candidate" >/dev/null 2>&1; then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done
  candidate="$(systemctl show copimine-admin.service -p User --value 2>/dev/null || true)"
  if [[ -n "$candidate" && "$candidate" != "root" ]] && id "$candidate" >/dev/null 2>&1; then
    printf '%s\n' "$candidate"
    return 0
  fi
  candidate="${SUDO_USER:-qwerty}"
  id "$candidate" >/dev/null 2>&1 && printf '%s\n' "$candidate" || printf 'qwerty\n'
}

normalize_runtime_env_owner() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  [[ -f "$env_file" ]] || return 0
  local app_user app_group
  app_user="$(runtime_app_user)"
  id "$app_user" >/dev/null 2>&1 || { echo "[preflight] Runtime user does not exist: $app_user" >&2; return 1; }
  app_group="$(id -gn "$app_user")"
  chown "$app_user:$app_group" "$env_file"
  chmod 600 "$env_file"
  echo "[preflight] Runtime env owner: $app_user:$app_group"
}

preflight
enable_offline_voicechat
normalize_runtime_env_owner

if [[ "$WIPE_DB" == "1" ]]; then
  [[ "${COPIMINE_ALLOW_DB_WIPE:-}" == "YES" ]] || { echo 'Refusing database wipe. Set COPIMINE_ALLOW_DB_WIPE=YES and pass --wipe-db.' >&2; exit 3; }
  [[ "${COPIMINE_CONFIRM_DB_NAME:-}" == "copimine" ]] || { echo 'Refusing database wipe. Set COPIMINE_CONFIRM_DB_NAME=copimine.' >&2; exit 3; }
  [[ -f "$PROJECT_ROOT/admin-web/.env" ]] || { echo "Missing $PROJECT_ROOT/admin-web/.env" >&2; exit 3; }
  # Backup first, then remove only the configured CopiMine schemas.
  source "$PROJECT_ROOT/deploy/shared/common.sh"
  mkdir -p "${BACKUP_ROOT:-/opt/copimine-backups}"
  pg_dump -h "$(copimine_env_value POSTGRES_HOST || echo 127.0.0.1)" -p "$(copimine_env_value POSTGRES_PORT || echo 5432)" -U "$(copimine_env_value POSTGRES_USER)" -d copimine -Fc -f "${BACKUP_ROOT:-/opt/copimine-backups}/pre-wipe-$(date +%Y%m%d-%H%M%S).dump"
  PGPASSWORD="$(copimine_env_value POSTGRES_PASSWORD)" psql -h "$(copimine_env_value POSTGRES_HOST || echo 127.0.0.1)" -p "$(copimine_env_value POSTGRES_PORT || echo 5432)" -U "$(copimine_env_value POSTGRES_USER)" -d copimine -v ON_ERROR_STOP=1 -c 'DROP SCHEMA IF EXISTS copimine CASCADE; CREATE SCHEMA copimine;'
fi

set +e
RUNTIME_APP_USER="$(runtime_app_user)"
APP_USER="$RUNTIME_APP_USER" COPIMINE_APP_USER="$RUNTIME_APP_USER" \
WIPE_WORLDS="$WIPE_WORLDS" CLEAN_WORLD_STATE="$RESET_GAMEPLAY" \
  "$PROJECT_ROOT/deploy/ubuntu/copimine_unpack_and_verify.sh" "$ARCHIVE_PATH" "$ARCHIVE_SHA256" "$DB_DUMP_PATH"
result=$?
set -e
if [[ "$result" -ne 0 ]]; then
  echo "INSTALL FAILED with exit code $result" >&2
  systemctl --no-pager --plain --full status copimine-admin copimine-minecraft nginx || true
  exit "$result"
fi
remove_retired_frontend
verify_runtime
if [[ "$RESET_TREASURY" == "1" ]]; then
  reset_treasury
fi
echo "INSTALL COMPLETE. Log: $LOG_FILE"
