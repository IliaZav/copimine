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

usage() { printf 'Usage: sudo bash %s /path/to/release.tar.gz [sha256] [--wipe-worlds] [--db-dump path] [--reset-treasury]\n       sudo bash %s --cleanup-zabbix\n       sudo bash %s --cleanup-external-services\n       sudo bash %s --configure-https [primary-host]\n       sudo bash %s --configure-release-trust [allowlist-path]\n' "$0" "$0" "$0" "$0" "$0" >&2; }
[[ "${EUID:-$(id -u)}" -eq 0 ]] || { echo 'Run this installer with sudo/root.' >&2; exit 2; }

configure_release_trust() {
  local source_path="${2:-$SCRIPT_DIR/release-signing.allowed}"
  local target_path='/etc/copimine/release-signing.allowed'
  local script_root source_real
  script_root="$(realpath -e -- "$SCRIPT_DIR")"
  source_real="$(realpath -e -- "$source_path" 2>/dev/null || true)"
  [[ -n "$source_real" && -f "$source_real" ]] || { echo "[trust] allowlist is missing: $source_path" >&2; return 1; }
  [[ "$source_real" == "$script_root"/* ]] || {
    echo '[trust] allowlist source must be inside the controlled upload directory' >&2
    return 1
  }
  mapfile -t trust_lines < <(sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$source_real")
  [[ "${#trust_lines[@]}" -eq 1 ]] || { echo '[trust] allowlist must contain exactly one non-comment key' >&2; return 1; }
  [[ "${trust_lines[0]}" =~ ^[[:space:]]*release[[:space:]]+ssh-ed25519[[:space:]]+[A-Za-z0-9+/]+={0,2}([[:space:]]+[^[:space:]]*)?[[:space:]]*$ ]] || {
    echo '[trust] allowlist must contain a release ssh-ed25519 key' >&2
    return 1
  }
  install -d -m 0755 /etc/copimine
  install -o root -g root -m 0644 -- "$source_real" "$target_path"
  [[ "$(stat -c '%U:%G %a' "$target_path")" == 'root:root 644' ]] || {
    echo '[trust] installed allowlist has unsafe ownership or mode' >&2
    return 1
  }
  echo "[trust] release signing allowlist installed: $target_path"
}

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

cleanup_legacy_artifacts() {
  local home_root='/home/qwerty'
  local upload_root="$home_root/copimine-upload"
  local target resolved base

  # This mode is intentionally explicit and separate from a normal release
  # install.  It is for the one-time server opening cleanup requested by the
  # operator; it only touches the explicit home allowlist, the inventoried
  # legacy roots below, and exact timestamped rollback directories.
  for target in \
    "$home_root/copimine-preserved-before-wipe-20260515-220317" \
    "$home_root/nginx-backup-resourcepack-20260621-164420" \
    "$upload_root/backups"; do
    [[ -e "$target" || -L "$target" ]] || continue
    resolved="$(readlink -f -- "$target" 2>/dev/null || true)"
    case "$resolved" in
      "$home_root"/*) ;;
      *) echo "[cleanup] refusing legacy target outside $home_root: $target -> $resolved" >&2; return 1 ;;
    esac
    [[ "$resolved" != "$home_root" ]] || { echo '[cleanup] refusing home root' >&2; return 1; }
    rm -rf -- "$resolved"
    echo "[cleanup] removed legacy home artifact: $resolved"
  done

  # These roots are historical backup stores from before the managed daily
  # backup service.  Keep the active /opt/copimine-backups root untouched;
  # only remove the exact, previously inventoried legacy roots below.
  local -a legacy_opt_roots=(
    '/opt/copimine-db-backups'
    '/opt/copimine-full-backups'
    '/opt/copimine.backup-20260611-192047'
    '/opt/copimine.backup.2026-06-28-210147'
    '/opt/copimine.backup-before-fixed-20260612-061503'
    '/opt/copimine-old-before-replace-20260606-074851'
    '/opt/copimine-old-before-replace-20260607-001732'
    '/opt/copimine-preserve-20260607-001732'
    '/opt/copimine.old-2026-06-29-182416'
  )
  for target in "${legacy_opt_roots[@]}"; do
    [[ -e "$target" || -L "$target" ]] || continue
    resolved="$(readlink -f -- "$target" 2>/dev/null || true)"
    case "$resolved" in
      /opt/copimine-db-backups|/opt/copimine-full-backups|/opt/copimine.backup-20260611-192047|/opt/copimine.backup.2026-06-28-210147|/opt/copimine.backup-before-fixed-20260612-061503|/opt/copimine-old-before-replace-20260606-074851|/opt/copimine-old-before-replace-20260607-001732|/opt/copimine-preserve-20260607-001732|/opt/copimine.old-2026-06-29-182416) ;;
      *) echo "[cleanup] refusing unexpected legacy root: $target -> $resolved" >&2; return 1 ;;
    esac
    rm -rf -- "$resolved"
    echo "[cleanup] removed legacy opt backup root: $resolved"
  done

  # Older failed replacements used separate .broken/.failed roots and one
  # un-timestamped copimine.old directory. They are not rollback candidates:
  # the managed retention block below keeps only the three newest exact
  # copimine.old-YYYYMMDD-HHMMSS directories. Remove only these explicit
  # top-level names, never a wildcard below an arbitrary path.
  local stale_root stale_name
  while IFS= read -r stale_root; do
    [[ -n "$stale_root" ]] || continue
    stale_name="$(basename -- "$stale_root")"
    case "$stale_name" in
      copimine.broken-*|copimine.failed-*|copimine.old|copimine_unpack_tmp) ;;
      *) echo "[cleanup] refusing unexpected stale release root: $stale_root" >&2; return 1 ;;
    esac
    resolved="$(readlink -f -- "$stale_root" 2>/dev/null || true)"
    [[ "$resolved" == "/opt/$stale_name" ]] || {
      echo "[cleanup] refusing stale release symlink: $stale_root -> $resolved" >&2
      return 1
    }
    rm -rf -- "$resolved"
    echo "[cleanup] removed stale release root: $resolved"
  done < <(find /opt -mindepth 1 -maxdepth 1 -type d \( -name 'copimine.broken-*' -o -name 'copimine.failed-*' -o -name 'copimine.old' -o -name 'copimine_unpack_tmp' \) -print 2>/dev/null | sort)

  # The upload directory is a staging area, not a backup archive.  Keep the
  # newest complete release archive for a manual rollback and remove older
  # archives plus their exact sidecars/bootstrap files and split parts.
  local upload_archive upload_stem keep_upload_archive keep_upload_stem
  local -a upload_archives=()
  while IFS= read -r upload_archive; do
    [[ -n "$upload_archive" ]] && upload_archives+=("$upload_archive")
  done < <(find "$upload_root" -mindepth 1 -maxdepth 1 -type f -name 'copimine-opt-full-*.tar.gz' -printf '%f\n' 2>/dev/null | sort -r)
  keep_upload_archive="${upload_archives[0]:-}"
  keep_upload_stem="${keep_upload_archive%.tar.gz}"
  for upload_archive in "${upload_archives[@]:1}"; do
    [[ "$upload_archive" =~ ^copimine-opt-full-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}\.tar\.gz$ ]] || {
      echo "[cleanup] refusing unexpected upload archive: $upload_archive" >&2
      return 1
    }
    upload_stem="${upload_archive%.tar.gz}"
    for target in "$upload_archive" "${upload_archive}.sha256" "${upload_stem}.tar.bootstrap.json"; do
      rm -f -- "$upload_root/$target"
    done
    find "$upload_root" -mindepth 1 -maxdepth 1 -type f -name "${upload_stem}.part-[0-9][0-9]" -delete
    echo "[cleanup] removed old upload release: $upload_stem"
  done
  if [[ -n "$keep_upload_stem" ]]; then
    [[ "$keep_upload_archive" =~ ^copimine-opt-full-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}\.tar\.gz$ ]] || {
      echo "[cleanup] refusing unexpected newest upload archive: $keep_upload_archive" >&2
      return 1
    }
    find "$upload_root" -mindepth 1 -maxdepth 1 -type f -name "${keep_upload_stem}.part-[0-9][0-9]" -delete
    echo "[cleanup] upload release retention enforced: keep=$keep_upload_archive"
  else
    echo '[cleanup] upload release retention: no complete archive found'
  fi

  # Every normal release replacement leaves a top-level rollback directory.
  # Retain only the three newest exact timestamped copies.  The live project
  # is /opt/copimine and can never match this allowlist.
  local runtime_keep=3 runtime_name runtime_path
  local -a runtime_backups=()
  while IFS= read -r runtime_name; do
    [[ -n "$runtime_name" ]] && runtime_backups+=("$runtime_name")
  done < <(find /opt -mindepth 1 -maxdepth 1 -type d -name 'copimine.old-*' -printf '%f\n' 2>/dev/null | sort -r)
  if (( ${#runtime_backups[@]} > runtime_keep )); then
    for runtime_name in "${runtime_backups[@]:runtime_keep}"; do
      [[ "$runtime_name" =~ ^copimine\.old-[0-9]{8}-[0-9]{6}$ ]] || {
        echo "[cleanup] refusing unexpected runtime backup: $runtime_name" >&2
        return 1
      }
      runtime_path="/opt/$runtime_name"
      resolved="$(readlink -f -- "$runtime_path" 2>/dev/null || true)"
      [[ "$resolved" == "$runtime_path" ]] || {
        echo "[cleanup] refusing runtime backup symlink: $runtime_path -> $resolved" >&2
        return 1
      }
      rm -rf -- "$resolved"
      echo "[cleanup] pruned old runtime backup: $resolved"
    done
  fi
  echo "[cleanup] runtime backup retention enforced: keep=$runtime_keep"

  # Keep the most recent pre-wipe dump for recovery from the destructive
  # opening reset.  Daily backups are pruned by backup.sh and its timer.
  local -a game_wipes=()
  while IFS= read -r target; do
    [[ -n "$target" ]] && game_wipes+=("$target")
  done < <(find /opt/copimine-backups -mindepth 1 -maxdepth 1 -type d -name 'game-wipe-*' -printf '%f\n' 2>/dev/null | sort -r)
  local keep_game_wipe="${game_wipes[0]:-}"
  while IFS= read -r target; do
    [[ -n "$target" ]] || continue
    base="$(basename -- "$target")"
    case "$base" in
      copimine-daily-*) continue ;;
      game-wipe-*) [[ "$base" == "$keep_game_wipe" ]] && continue ;;
    esac
    resolved="$(readlink -f -- "$target" 2>/dev/null || true)"
    case "$resolved" in
      /opt/copimine-backups/*) ;;
      *) echo "[cleanup] refusing backup target outside /opt/copimine-backups: $target -> $resolved" >&2; return 1 ;;
    esac
    rm -rf -- "$resolved"
    echo "[cleanup] removed legacy backup: $resolved"
  done < <(find /opt/copimine-backups -mindepth 1 -maxdepth 1 -printf '%p\n' 2>/dev/null | sort)

  local daily_keep=3 archive stem suffix
  local -a daily_archives=()
  while IFS= read -r archive; do
    [[ -n "$archive" ]] && daily_archives+=("$archive")
  done < <(find /opt/copimine-backups -mindepth 1 -maxdepth 1 -type f -name 'copimine-daily-*.tar.gz' -printf '%f\n' 2>/dev/null | sort -r)
  if (( ${#daily_archives[@]} > daily_keep )); then
    for archive in "${daily_archives[@]:daily_keep}"; do
      stem="${archive%.tar.gz}"
      [[ "$stem" =~ ^copimine-daily-[0-9]{8}-[0-9]{6}$ ]] || { echo "[cleanup] refusing unexpected daily backup: $archive" >&2; return 1; }
      for suffix in '.tar.gz' '.tar.gz.sha256' '.dump' '.dump.sha256' '.manifest'; do
        rm -f -- "/opt/copimine-backups/${stem}${suffix}"
      done
      echo "[cleanup] pruned old daily backup: $stem"
    done
  fi
  echo "[cleanup] daily backup retention enforced: keep=$daily_keep"
  echo "[cleanup] legacy artifact cleanup complete; retained pre-wipe backup: ${keep_game_wipe:-none}"
}

configure_http_only_public() {
  local config='/etc/nginx/sites-available/copimine-public'
  local enabled='/etc/nginx/sites-enabled/copimine-public'
  local backup_root='/opt/copimine-backups'
  local backup_path="${backup_root}/http-public-before-http-only-$(date +%Y%m%d-%H%M%S)"

  command -v nginx >/dev/null 2>&1 || { echo '[http] nginx is required' >&2; return 1; }
  mkdir -p "$backup_root"
  chmod 700 "$backup_root"
  if [[ -f "$config" ]]; then
    cp -a -- "$config" "$backup_path"
    chmod 600 "$backup_path"
  fi

  cat >"$config" <<'NGINX'
server {
    listen 80 default_server;
    listen [::]:80 default_server;

    server_name copimine.ru www.copimine.ru;
    client_max_body_size 8m;

    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "same-origin" always;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_connect_timeout 30s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
NGINX
  ln -sfn "$config" "$enabled"
  nginx -t
  systemctl reload nginx.service
  systemctl is-active --quiet nginx.service || { echo '[http] nginx is not active' >&2; return 1; }
  echo '[http] public port 80 enabled; public 443 configuration disabled.'
  [[ -f "$backup_path" ]] && echo "[http] previous public config saved at $backup_path"
}

configure_https_public() {
  local primary_host="${2:-copimine.ru}"
  local cert_path="${COPIMINE_TLS_CERT_PATH:-/etc/letsencrypt/live/copimine.ru/fullchain.pem}"
  local key_path="${COPIMINE_TLS_KEY_PATH:-/etc/letsencrypt/live/copimine.ru/privkey.pem}"
  local server_names='copimine.ru www.copimine.ru 90.188.115.155'
  local env_file="$PROJECT_ROOT/admin-web/.env"
  local config='/etc/nginx/sites-available/copimine-public'
  local enabled='/etc/nginx/sites-enabled/copimine-public'
  local backup_root='/opt/copimine-backups'
  local backup_path="${backup_root}/https-public-before-reconfigure-$(date +%Y%m%d-%H%M%S)"
  local env_user env_group resource_sha1 resource_version

  [[ "$primary_host" =~ ^[A-Za-z0-9.-]+$ ]] || { echo '[https] invalid primary hostname' >&2; return 2; }
  [[ -s "$cert_path" ]] || { echo "[https] certificate is missing or empty: $cert_path" >&2; return 1; }
  [[ -s "$key_path" ]] || { echo "[https] private key is missing or empty: $key_path" >&2; return 1; }
  [[ -f "$env_file" ]] || { echo "[https] runtime environment is missing: $env_file" >&2; return 1; }
  command -v nginx >/dev/null 2>&1 || { echo '[https] nginx is required' >&2; return 1; }

  mkdir -p "$backup_root"
  chmod 700 "$backup_root"
  if [[ -f "$config" ]]; then
    cp -a -- "$config" "$backup_path"
    chmod 600 "$backup_path"
  fi

  resource_sha1="$(sha1sum "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" 2>/dev/null | awk '{print $1}' || true)"
  if [[ "$resource_sha1" =~ ^[0-9a-fA-F]{40}$ ]]; then
    resource_version="${resource_sha1:0:12}"
  else
    resource_version='runtime'
  fi

  # Update only transport/public-origin keys. Secrets, accounts and all other
  # runtime settings remain untouched.
  COPIMINE_HTTPS_PRIMARY="$primary_host" \
  COPIMINE_HTTPS_CERT="$cert_path" \
  COPIMINE_HTTPS_KEY="$key_path" \
  COPIMINE_HTTPS_NAMES="$server_names" \
  COPIMINE_HTTPS_RESOURCE_VERSION="$resource_version" \
    python3 - "$env_file" <<'PY'
import os
from pathlib import Path
import sys

path = Path(sys.argv[1])
primary = os.environ["COPIMINE_HTTPS_PRIMARY"]
cert = os.environ["COPIMINE_HTTPS_CERT"]
key = os.environ["COPIMINE_HTTPS_KEY"]
server_names = os.environ["COPIMINE_HTTPS_NAMES"].split()
resource_version = os.environ["COPIMINE_HTTPS_RESOURCE_VERSION"]
values = {}
for raw in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
    if "=" in raw and not raw.lstrip().startswith("#"):
        name, value = raw.split("=", 1)
        values[name.strip()] = value.strip().strip('"').strip("'")

values.update(
    {
        "ADMIN_PUBLIC_BASE_URL": f"https://{primary}",
        "PUBLIC_PANEL_URL": f"https://{primary}",
        "RESOURCE_PACK_PUBLIC_URL": f"https://{primary}/resourcepacks/CopiMineResourcePack.zip?v={resource_version}",
        "COPIMINE_TLS_ENABLED": "1",
        "COPIMINE_TLS_CERT_PATH": cert,
        "COPIMINE_TLS_KEY_PATH": key,
        "COPIMINE_PUBLIC_TLS_EXTERNAL": "1",
        "AUTH_COOKIE_SECURE": "1",
        "ALLOW_INSECURE_HTTP_AUTH": "0",
        "NGINX_SERVER_NAMES": " ".join(server_names),
    }
)
origins = [item.strip() for item in values.get("ALLOWED_ORIGINS", "").split(",") if item.strip()]
for name in server_names:
    origin = f"https://{name}"
    if origin not in origins:
        origins.append(origin)
values["ALLOWED_ORIGINS"] = ",".join(origins)

ordered = [f"{name}={values[name]}" for name in sorted(values)]
temporary = path.with_name(".env.https-tmp")
temporary.write_text("\n".join(ordered).rstrip() + "\n", encoding="utf-8")
temporary.chmod(0o600)
temporary.replace(path)
PY

  env_user="$(stat -c '%U' "$env_file" 2>/dev/null || true)"
  env_group="$(stat -c '%G' "$env_file" 2>/dev/null || true)"
  [[ -n "$env_user" && "$env_user" != 'root' ]] || env_user='qwerty'
  [[ -n "$env_group" && "$env_group" != 'root' ]] || env_group="$env_user"
  chown "$env_user:$env_group" "$env_file"
  chmod 600 "$env_file"

  cat >"$config" <<'NGINX'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name __COPIMINE_PUBLIC_SERVER_NAMES__;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2 default_server;
    listen [::]:443 ssl http2 default_server;
    server_name __COPIMINE_PUBLIC_SERVER_NAMES__;
    client_max_body_size 8m;

    ssl_certificate __COPIMINE_PUBLIC_CERT__;
    ssl_certificate_key __COPIMINE_PUBLIC_KEY__;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "same-origin" always;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_connect_timeout 30s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
NGINX
  COPIMINE_PUBLIC_SERVER_NAMES="$server_names" \
  COPIMINE_PUBLIC_CERT="$cert_path" \
  COPIMINE_PUBLIC_KEY="$key_path" \
    python3 - "$config" <<'PY'
import os
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
for marker, value in {
    "__COPIMINE_PUBLIC_SERVER_NAMES__": os.environ["COPIMINE_PUBLIC_SERVER_NAMES"],
    "__COPIMINE_PUBLIC_CERT__": os.environ["COPIMINE_PUBLIC_CERT"],
    "__COPIMINE_PUBLIC_KEY__": os.environ["COPIMINE_PUBLIC_KEY"],
}.items():
    text = text.replace(marker, value)
if "__COPIMINE_PUBLIC_" in text:
    raise SystemExit("unresolved HTTPS nginx marker")
path.write_text(text, encoding="utf-8")
PY
  ln -sfn "$config" "$enabled"
  nginx -t
  systemctl restart copimine-admin.service
  systemctl reload nginx.service
  systemctl is-active --quiet copimine-admin.service || { echo '[https] admin service is not active' >&2; return 1; }
  systemctl is-active --quiet nginx.service || { echo '[https] nginx is not active' >&2; return 1; }
  echo '[https] nginx now terminates TLS on public port 443 and redirects port 80'
  echo "[https] public origin: https://$primary_host"
  echo "[https] static-IP route: https://90.188.115.155 (certificate must cover the requested hostname)"
  [[ -f "$backup_path" ]] && echo "[https] previous public config saved at $backup_path"
}

refresh_resource_pack_url() {
  local env_file="$PROJECT_ROOT/admin-web/.env"
  local properties="$PROJECT_ROOT/minecraft/server/server.properties"
  [[ -f "$env_file" && -f "$properties" ]] || { echo '[pack] runtime files are missing' >&2; return 1; }
  local resourcepack_sha1 resourcepack_version
  resourcepack_sha1="$(sha1sum "$PROJECT_ROOT/resourcepacks/build/CopiMineResourcePack.zip" 2>/dev/null | awk '{print $1}' || true)"
  if [[ "$resourcepack_sha1" =~ ^[0-9a-fA-F]{40}$ ]]; then
    resourcepack_version="${resourcepack_sha1:0:12}"
  else
    resourcepack_version="runtime"
  fi
  python3 - "$env_file" "$properties" "$resourcepack_version" <<'PY'
from pathlib import Path
import sys

env_path, properties_path = map(Path, sys.argv[1:3])
resourcepack_version = sys.argv[3]
lines = env_path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
panel = 'https://copimine.ru'
for line in lines:
    if line.startswith('PUBLIC_PANEL_URL='):
        panel = line.split('=', 1)[1].strip().strip('"').strip("'").rstrip('/')
pack_url = panel + '/resourcepacks/CopiMineResourcePack.zip?v=' + resourcepack_version
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
  local expected_commit="${ZAPRET_EXPECTED_COMMIT:-}"
  [[ -x "$source_dir/service.sh" ]] || { echo "[zapret] source checkout is missing: $source_dir" >&2; return 1; }
  [[ "$expected_commit" =~ ^[0-9a-fA-F]{40}$ ]] || { echo '[zapret] set ZAPRET_EXPECTED_COMMIT to the reviewed 40-character commit before restoring' >&2; return 1; }
  local actual_commit
  actual_commit="$(git -C "$source_dir" rev-parse HEAD 2>/dev/null || true)"
  [[ "$actual_commit" == "$expected_commit" ]] || { echo "[zapret] checkout commit mismatch: expected=$expected_commit actual=$actual_commit" >&2; return 1; }
  git -C "$source_dir" diff --quiet || { echo '[zapret] source checkout has uncommitted changes; refusing root installation' >&2; return 1; }
  [[ "${ZAPRET_ALLOW_DEPENDENCY_DOWNLOAD:-0}" == '1' ]] || { echo '[zapret] set ZAPRET_ALLOW_DEPENDENCY_DOWNLOAD=1 after reviewing dependency pins' >&2; return 1; }
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

set_zapret_strategy() {
  local strategy="${2:-general.bat}"
  local zapret_root='/opt/zapret-discord-youtube-linux'
  [[ "$strategy" =~ ^[A-Za-z0-9_.-]+\.bat$ ]] || { echo '[zapret] invalid strategy name' >&2; return 1; }
  [[ -f "$zapret_root/zapret-latest/$strategy" || -f "$zapret_root/custom-strategies/$strategy" ]] || { echo "[zapret] strategy not found: $strategy" >&2; return 1; }
  sed -i -E "s/^strategy=.*/strategy=$strategy/" "$zapret_root/conf.env"
  systemctl restart zapret_discord_youtube.service
  sleep 3
  systemctl is-active --quiet zapret_discord_youtube.service || { echo '[zapret] service failed after strategy change' >&2; return 1; }
  echo "[zapret] active strategy: $strategy"
}

if [[ "$ARCHIVE_PATH" == "--cleanup-zabbix" ]]; then
  cleanup_zabbix
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--cleanup-external-services" ]]; then
  cleanup_external_services
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--cleanup-legacy" ]]; then
  cleanup_legacy_artifacts
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--configure-release-trust" ]]; then
  configure_release_trust "$@"
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--configure-http-only" ]]; then
  configure_http_only_public
  exit $?
fi
if [[ "$ARCHIVE_PATH" == "--configure-https" ]]; then
  configure_https_public "$@"
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
if [[ "$ARCHIVE_PATH" == "--set-zapret-strategy" ]]; then
  set_zapret_strategy "$@"
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
  local expected_sha actual_sha expected_modpack actual_modpack properties_sha service_timeout
  wait_for_runtime_service() {
    local service="$1" timeout="$2" elapsed=0
    while (( elapsed < timeout )); do
      if systemctl is-active --quiet "$service"; then
        return 0
      fi
      sleep 2
      elapsed=$((elapsed + 2))
    done
    return 1
  }
  for service in copimine-admin copimine-minecraft nginx; do
    service_timeout=90
    if [[ "$service" == "copimine-minecraft" ]]; then
      # A fresh world can take several minutes to generate before systemd and
      # the post-wipe restart settle.  Do not report a healthy release as
      # failed merely because the second Java start is still activating.
      service_timeout="${COPIMINE_MINECRAFT_START_TIMEOUT_SECONDS:-360}"
    fi
    if ! wait_for_runtime_service "$service" "$service_timeout"; then
      echo "Service is not active after ${service_timeout}s: $service" >&2
      systemctl --no-pager --full status "$service" || true
      journalctl -u "$service" -n 120 --no-pager || true
      return 1
    fi
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

remove_retired_frontend() {
  local frontend_root="$PROJECT_ROOT/admin-web/frontend"
  # The modern cabinet is the only supported admin surface.  These files are
  # old previews/SPA entrypoints and must not remain reachable after a full
  # replacement, otherwise browsers can cache or discover a second UI.
  rm -rf -- "$frontend_root/assets/js/legacy" "$frontend_root/assets/css/legacy.css"
  rm -f -- "$frontend_root/preview-admin.html" "$frontend_root/preview-player.html"
  echo '[cleanup] retired preview and legacy frontend files removed'
}

normalize_vanilla_mob_gameplay() {
  local properties="$PROJECT_ROOT/minecraft/server/server.properties"
  [[ -f "$properties" ]] || { echo "[gameplay] missing $properties" >&2; return 1; }
  python3 - "$properties" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
updates = {
    'view-distance': '10',
    'simulation-distance': '5',
    'entity-broadcast-range-percentage': '100',
    'difficulty': 'hard',
    'spawn-animals': 'true',
    'spawn-monsters': 'true',
    'spawn-npcs': 'true',
}

lines = path.read_text(encoding='utf-8-sig', errors='replace').splitlines()
out, seen = [], set()
for line in lines:
    if '=' in line and not line.startswith('#'):
        key = line.split('=', 1)[0]
        if key in updates:
            out.append(f'{key}={updates[key]}')
            seen.add(key)
            continue
    out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')
path.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
PY
  grep -q '^view-distance=10$' "$properties" || { echo '[gameplay] view-distance was not normalized' >&2; return 1; }
  grep -q '^simulation-distance=5$' "$properties" || { echo '[gameplay] simulation-distance was not normalized' >&2; return 1; }
  grep -q '^difficulty=hard$' "$properties" || { echo '[gameplay] difficulty was not normalized to hard' >&2; return 1; }
  grep -q '^spawn-animals=true$' "$properties" || { echo '[gameplay] spawn-animals is disabled' >&2; return 1; }
  grep -q '^spawn-monsters=true$' "$properties" || { echo '[gameplay] spawn-monsters is disabled' >&2; return 1; }
  grep -q '^spawn-npcs=true$' "$properties" || { echo '[gameplay] spawn-npcs is disabled' >&2; return 1; }
  echo '[gameplay] vanilla mob spawning and view distances verified'
}

reset_gameplay_state_if_requested() {
  if [[ "$RESET_GAMEPLAY" != "1" ]]; then
    return 0
  fi
  local reset_script="$PROJECT_ROOT/deploy/ubuntu/reset_game_state_preserve_accounts.sh"
  [[ -x "$reset_script" ]] || {
    echo "[wipe] missing guarded reset script: $reset_script" >&2
    return 1
  }
  # copimine_unpack_and_verify.sh has already stopped the services, replaced
  # the tree and (when --wipe-worlds was supplied) recreated the world
  # boundary while preserving whitelist.json and the configured seed.  The
  # guarded reset now clears only gameplay state in PostgreSQL, writes a dump
  # before doing so, and restarts the services with the clean state.
  echo '[wipe] resetting elections, taxes, banks, shops and runtime state; site accounts and whitelist are preserved'
  COPIMINE_CONFIRM_GAME_WIPE=YES "$reset_script"
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
# Never silently enable the offline-mode voice-chat exception during an
# upgrade. The existing .env value is operator state and the shared runtime
# hardening gate fails closed when it is absent or undocumented.
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
normalize_vanilla_mob_gameplay
reset_gameplay_state_if_requested
remove_retired_frontend
verify_runtime
if [[ "$RESET_TREASURY" == "1" ]]; then
  reset_treasury
fi
echo "INSTALL COMPLETE. Log: $LOG_FILE"
