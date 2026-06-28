#!/usr/bin/env bash
set -euo pipefail

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
ENV_FILE="$ROOT/admin-web/.env"
PLUGINS_DIR="$ROOT/minecraft/server/plugins"
SERVER_PROPERTIES="$ROOT/minecraft/server/server.properties"
MODPACK_ZIP="$ROOT/thirdparty/CopiMineMods.zip"
MODPACK_SHA1_FILE="$ROOT/thirdparty/CopiMineMods.sha1"
RESOURCEPACK_ZIP="$ROOT/resourcepacks/build/CopiMineResourcePack.zip"
EXPECTED_PLUGIN_COUNT="${EXPECTED_PLUGIN_COUNT:-28}"
ERRORS=0

fail() {
  echo "ERROR: $1"
  ERRORS=$((ERRORS + 1))
}

warn() {
  echo "WARN: $1"
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command $1"
}

need systemctl
need curl
need ss
need sha1sum
need python3

[[ -d "$ROOT" ]] || fail "Missing root $ROOT"
[[ -f "$ENV_FILE" ]] || fail "Missing env file $ENV_FILE"
[[ "$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo '?')" == "600" ]] || fail ".env permissions must be 600"

for service in copimine-admin copimine-minecraft copimine-discord-bot copimine-minecraft-discord-bridge nginx; do
  if ! systemctl is-active --quiet "$service"; then
    fail "Service not active: $service"
  fi
done

ss -ltn | grep -q ':8090 ' || fail "Port 8090 is not listening"
ss -ltn | grep -q ':18080 ' || fail "Port 18080 is not listening"
ss -ltn | grep -q ':25565 ' || fail "Port 25565 is not listening"

curl -fsS http://127.0.0.1:8090/api/health >/dev/null || fail "Backend /api/health direct check failed"
for path in / /server.html /shops.html /mods.html /signin.html /register.html /downloads/CopiMineMods.zip /resourcepacks/CopiMineResourcePack.zip; do
  curl -fsSI -H 'Host: copimine.ru:18080' "http://127.0.0.1:18080$path" >/dev/null || fail "Nginx check failed for $path via copimine.ru"
done
curl -fsSI -H 'Host: admin.copimine.ru:18080' http://127.0.0.1:18080/ >/dev/null || fail "Nginx check failed for admin.copimine.ru"

[[ -f "$MODPACK_ZIP" ]] || fail "Missing modpack zip"
[[ -f "$MODPACK_SHA1_FILE" ]] || fail "Missing modpack sha1"
[[ -f "$RESOURCEPACK_ZIP" ]] || fail "Missing resourcepack zip"

ACTUAL_MODPACK_SHA1="$(sha1sum "$MODPACK_ZIP" | awk '{print $1}')"
RECORDED_MODPACK_SHA1="$(tr -d '\r\n' < "$MODPACK_SHA1_FILE" | awk '{print $1}')"
[[ "$ACTUAL_MODPACK_SHA1" == "$RECORDED_MODPACK_SHA1" ]] || fail "Modpack SHA1 mismatch"

ACTUAL_RESOURCEPACK_SHA1="$(sha1sum "$RESOURCEPACK_ZIP" | awk '{print $1}')"
grep -q "^resource-pack-sha1=$ACTUAL_RESOURCEPACK_SHA1$" "$SERVER_PROPERTIES" || fail "resource-pack-sha1 mismatch in server.properties"

PLUGIN_COUNT="$(find "$PLUGINS_DIR" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
[[ "$PLUGIN_COUNT" == "$EXPECTED_PLUGIN_COUNT" ]] || fail "Expected $EXPECTED_PLUGIN_COUNT plugin jars, found $PLUGIN_COUNT"

for jar in CopiMineUltimateAdminPlus.jar CopiMineArtifacts.jar CopiMineEconomyCore.jar CopiMineElectionCore.jar CopiMineNarcotics.jar CopiMineWorldCore.jar; do
  [[ -f "$PLUGINS_DIR/$jar" ]] || fail "Missing $jar"
done

if journalctl -u copimine-minecraft --since '10 minutes ago' --no-pager | grep -Eiq 'No suitable driver|Disabling CopiMineElectionCore|ClassNotFound|NoClassDefFoundError'; then
  fail "Minecraft logs contain PostgreSQL/plugin load errors"
fi

if journalctl -u copimine-discord-bot --since '10 minutes ago' --no-pager | grep -Eiq 'PrivilegedIntentsRequired|FileNotFoundError'; then
  fail "Discord bot logs contain startup errors"
fi

if [[ "$ERRORS" -eq 0 ]]; then
  echo "ERRORS=0"
  echo "RESULT: OK"
else
  echo "ERRORS=$ERRORS"
  echo "RESULT: FAIL"
  exit 1
fi
