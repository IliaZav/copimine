#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${PROJECT_ROOT:-/opt/copimine}"
ARCHIVE_PATH="${1:-}"
SERVICE_NAME="${SERVICE_NAME:-copimine-minecraft.service}"

if [[ -z "$ARCHIVE_PATH" ]]; then
  echo "Usage: sudo bash copimine_auth_patch.sh /path/to/copimine-authme-patch.tar.gz"
  exit 1
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "Archive not found: $ARCHIVE_PATH"
  exit 1
fi

if [[ ! -d "$PROJECT_ROOT/minecraft/server/plugins" ]]; then
  echo "Plugins directory not found under $PROJECT_ROOT"
  exit 1
fi

WORK_ROOT="$(mktemp -d)"
BACKUP_ROOT="$PROJECT_ROOT/backups/auth-patch-$(date +%Y%m%d-%H%M%S)"
PAYLOAD_ROOT=""

cleanup() {
  rm -rf "$WORK_ROOT"
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
cp -a "$PROJECT_ROOT/minecraft/server/plugins/." "$BACKUP_ROOT/plugins-before/" >/dev/null 2>&1 || true

echo "[2/8] Stopping minecraft service"
if systemctl list-unit-files | grep -q "^${SERVICE_NAME}"; then
  systemctl stop "$SERVICE_NAME"
else
  echo "Service $SERVICE_NAME not found, continuing without stop."
fi

echo "[3/8] Extracting patch archive"
tar -xzf "$ARCHIVE_PATH" -C "$WORK_ROOT"

echo "[4/8] Detecting payload root"
PAYLOAD_ROOT="$(detect_payload_root "$WORK_ROOT")"
if [[ -z "$PAYLOAD_ROOT" ]]; then
  echo "Auth patch payload root not found in archive."
  exit 1
fi

PLUGIN_DIR="$PROJECT_ROOT/minecraft/server/plugins"
PAYLOAD_PLUGIN_DIR="$PAYLOAD_ROOT/minecraft/server/plugins"

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
