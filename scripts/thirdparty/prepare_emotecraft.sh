#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${4:-$(cd "$(dirname "$0")/../.." && pwd)}"
CLIENT_JAR="${1:-}"
SERVER_JAR="${2:-}"
FABRIC_API_JAR="${3:-}"
CLIENT_DIR="$PROJECT_ROOT/thirdparty/client-mods"
SERVER_DIR="$PROJECT_ROOT/thirdparty/server-plugins"
mkdir -p "$CLIENT_DIR" "$SERVER_DIR"

stage_jar() {
  local source_path="$1"
  local target_dir="$2"
  local url="$3"
  local name="$4"
  local expected_sha256="$5"
  local target="$target_dir/$name"
  if [[ -n "$source_path" ]]; then
    [[ -f "$source_path" ]] || { echo "Файл не найден: $source_path" >&2; exit 1; }
    [[ "$source_path" == *.jar ]] || { echo "Нужен .jar файл: $source_path" >&2; exit 1; }
    cp "$source_path" "$target"
  else
    curl --fail --location --proto '=https' --tlsv1.2 "$url" -o "$target"
  fi
  local actual_sha256
  actual_sha256="$(sha256sum "$target" | awk '{print $1}')"
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    rm -f "$target"
    echo "SHA-256 mismatch for $name" >&2
    exit 1
  fi
  printf '%s\n' "$actual_sha256"
}

CLIENT_SHA256="$(stage_jar "$CLIENT_JAR" "$CLIENT_DIR" "https://cdn.modrinth.com/data/pZ2wrerK/versions/daqt5qcK/emotecraft-for-MC1.21.1-2.4.12-fabric.jar" "emotecraft-for-MC1.21.1-2.4.12-fabric.jar" "633a77711f650dbf0bd071a43c086b7946d6e117b28ac1a414d038ea7b339f7c")"
SERVER_SHA256="$(stage_jar "$SERVER_JAR" "$SERVER_DIR" "https://cdn.modrinth.com/data/pZ2wrerK/versions/DVp3FUqR/emotecraft-2.4.12-bukkit.jar" "emotecraft-2.4.12-bukkit.jar" "b8defd7f557262db50b9d0c411544d09ced5bb7f10703e1ffa05f4b38c851e23")"
FABRIC_SHA256="$(stage_jar "$FABRIC_API_JAR" "$CLIENT_DIR" "https://cdn.modrinth.com/data/P7dR8mSH/versions/IpaMcBLh/fabric-api-0.116.11%2B1.21.1.jar" "fabric-api-0.116.11+1.21.1.jar" "b791de6f6dce9c58d4ea2af6c713bbcc6dc64d0a5995a8bad6f225ee58cf17d2")"

echo "Emotecraft/Fabric API staged:"
echo "  client: $CLIENT_DIR/emotecraft-for-MC1.21.1-2.4.12-fabric.jar sha256=$CLIENT_SHA256"
echo "  server: $SERVER_DIR/emotecraft-2.4.12-bukkit.jar sha256=$SERVER_SHA256"
echo "  fabric-api: $CLIENT_DIR/fabric-api-0.116.11+1.21.1.jar sha256=$FABRIC_SHA256"
echo "Official source:"
echo "  https://modrinth.com/project/pZ2wrerK"
echo "  https://modrinth.com/project/P7dR8mSH"
