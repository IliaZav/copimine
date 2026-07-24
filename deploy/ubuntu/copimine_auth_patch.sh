#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
ARCHIVE_PATH="${1:-}"
SERVICE_NAME="${SERVICE_NAME:-copimine-minecraft.service}"

if [[ -z "$ARCHIVE_PATH" ]]; then
  echo "Usage: sudo bash copimine_auth_patch.sh /path/to/copimine-authme-patch.tar.gz"
  exit 1
fi

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run this patch with sudo/root."
  exit 1
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Archive not found: $ARCHIVE_PATH"
  exit 1
fi

VALIDATE_ARCHIVE="$PROJECT_ROOT/deploy/shared/validate_archive.py"
if [[ ! -r "$VALIDATE_ARCHIVE" ]]; then
  echo "Shared archive validator not found: $VALIDATE_ARCHIVE" >&2
  exit 1
fi

SHA256_PATH="${ARCHIVE_PATH}.sha256"
if [[ ! -f "$SHA256_PATH" ]]; then
  echo "Missing required SHA256 sidecar: $SHA256_PATH"
  exit 1
fi
EXPECTED_SHA256="$(awk '{print tolower($1)}' "$SHA256_PATH" | head -n 1)"
ACTUAL_SHA256="$(sha256sum "$ARCHIVE_PATH" | awk '{print tolower($1)}')"
if [[ ! "$EXPECTED_SHA256" =~ ^[0-9a-f]{64}$ ]] || [[ "$EXPECTED_SHA256" != "$ACTUAL_SHA256" ]]; then
  echo "Auth patch archive SHA256 verification failed."
  exit 1
fi

if [[ ! -d "$PROJECT_ROOT/minecraft/server/plugins" ]]; then
  echo "Plugins directory not found under $PROJECT_ROOT"
  exit 1
fi

WORK_ROOT="$(mktemp -d)"
BACKUP_ROOT="$PROJECT_ROOT/backups/auth-patch-$(date +%Y%m%d-%H%M%S)"
PAYLOAD_ROOT=""
SERVICE_WAS_ACTIVE=0

cleanup() {
  local code=$?
  if [[ "$code" -ne 0 && "$SERVICE_WAS_ACTIVE" -eq 1 ]]; then
    echo "Patch failed; restarting the previously active Minecraft service." >&2
    systemctl start "$SERVICE_NAME" || echo "WARNING: could not restart $SERVICE_NAME" >&2
  fi
  rm -rf -- "$WORK_ROOT"
  exit "$code"
}
trap cleanup EXIT

detect_payload_root() {
  local base="$1"
  if [[ -f "$base/minecraft/server/plugins/AuthMe-5.6.0.jar" ]]; then
    echo "$base"
    return 0
  fi
  local nested
  nested="$(find "$base" -type f -path '*/minecraft/server/plugins/AuthMe-5.6.0.jar' | head -n 1 || true)"
  if [[ -n "$nested" ]]; then
    dirname "$(dirname "$(dirname "$(dirname "$nested")")")"
    return 0
  fi
  return 1
}

echo "[1/8] Preparing backup"
mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
cp -a "$PROJECT_ROOT/minecraft/server/plugins/." "$BACKUP_ROOT/plugins-before/"

echo "[2/8] Stopping minecraft service"
if systemctl list-unit-files | grep -q "^${SERVICE_NAME}"; then
  if systemctl is-active --quiet "$SERVICE_NAME"; then SERVICE_WAS_ACTIVE=1; fi
  systemctl stop "$SERVICE_NAME"
  systemctl is-active --quiet "$SERVICE_NAME" && { echo "Service remained active after stop." >&2; exit 1; }
else
  echo "Service $SERVICE_NAME not found, continuing without stop."
fi

echo "[3/8] Extracting patch archive"
python3 "$VALIDATE_ARCHIVE" "$ARCHIVE_PATH"
tar -xzf "$ARCHIVE_PATH" -C "$WORK_ROOT" --no-same-owner --no-same-permissions

echo "[4/8] Detecting payload root"
PAYLOAD_ROOT="$(detect_payload_root "$WORK_ROOT")"
if [[ -z "$PAYLOAD_ROOT" ]]; then
  echo "Auth patch payload root not found in archive."
  exit 1
fi

PLUGIN_DIR="$PROJECT_ROOT/minecraft/server/plugins"
PAYLOAD_PLUGIN_DIR="$PAYLOAD_ROOT/minecraft/server/plugins"
[[ -f "$PAYLOAD_PLUGIN_DIR/AuthMe-5.6.0.jar" ]] || { echo 'AuthMe payload jar is missing.' >&2; exit 1; }
[[ -f "$PAYLOAD_PLUGIN_DIR/AuthEffects.jar" ]] || { echo 'AuthEffects payload jar is missing.' >&2; exit 1; }

echo "[5/8] Removing nLogin"
rm -f "$PLUGIN_DIR/nLogin.jar"
rm -rf "$PLUGIN_DIR/nLogin"

echo "[6/8] Installing AuthMe and AuthEffects"
install -m 0644 "$PAYLOAD_PLUGIN_DIR/AuthMe-5.6.0.jar" "$PLUGIN_DIR/AuthMe-5.6.0.jar"
install -m 0644 "$PAYLOAD_PLUGIN_DIR/AuthEffects.jar" "$PLUGIN_DIR/AuthEffects.jar"

if [[ -d "$PLUGIN_DIR/AuthMe" ]]; then
  echo "Existing AuthMe data directory preserved."
else
  echo "No existing AuthMe data directory found. Plugin will initialize fresh config on startup."
fi

echo "[7/8] Starting minecraft service"
if systemctl list-unit-files | grep -q "^${SERVICE_NAME}"; then
  systemctl start "$SERVICE_NAME"
  sleep 5
  systemctl --no-pager --full status "$SERVICE_NAME" | sed -n '1,25p'
else
  echo "Service $SERVICE_NAME not found, start it manually after patch."
fi

echo "[8/8] Done"
echo "Backup: $BACKUP_ROOT"
echo "Installed: $PLUGIN_DIR/AuthMe-5.6.0.jar"
echo "Installed: $PLUGIN_DIR/AuthEffects.jar"
echo "Removed: $PLUGIN_DIR/nLogin.jar"
