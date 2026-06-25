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
  local target="$target_dir/$name"
  if [[ -n "$source_path" ]]; then
    [[ -f "$source_path" ]] || { echo "Файл не найден: $source_path" >&2; exit 1; }
    [[ "$source_path" == *.jar ]] || { echo "Нужен .jar файл: $source_path" >&2; exit 1; }
    cp "$source_path" "$target"
  else
    curl -L --fail "$url" -o "$target"
  fi
  sha1sum "$target" | awk '{print $1}'
}

CLIENT_SHA1="$(stage_jar "$CLIENT_JAR" "$CLIENT_DIR" "https://cdn.modrinth.com/data/pZ2wrerK/versions/daqt5qcK/emotecraft-for-MC1.21.1-2.4.12-fabric.jar" "emotecraft-for-MC1.21.1-2.4.12-fabric.jar")"
SERVER_SHA1="$(stage_jar "$SERVER_JAR" "$SERVER_DIR" "https://cdn.modrinth.com/data/pZ2wrerK/versions/DVp3FUqR/emotecraft-2.4.12-bukkit.jar" "emotecraft-2.4.12-bukkit.jar")"
FABRIC_SHA1="$(stage_jar "$FABRIC_API_JAR" "$CLIENT_DIR" "https://cdn.modrinth.com/data/P7dR8mSH/versions/Lwt6YYHL/fabric-api-0.116.12%2B1.21.1.jar" "fabric-api-0.116.12+1.21.1.jar")"

echo "Emotecraft/Fabric API staged:"
echo "  client: $CLIENT_DIR/emotecraft-for-MC1.21.1-2.4.12-fabric.jar sha1=$CLIENT_SHA1"
echo "  server: $SERVER_DIR/emotecraft-2.4.12-bukkit.jar sha1=$SERVER_SHA1"
echo "  fabric-api: $CLIENT_DIR/fabric-api-0.116.12+1.21.1.jar sha1=$FABRIC_SHA1"
echo "Official source:"
echo "  https://modrinth.com/project/pZ2wrerK"
echo "  https://modrinth.com/project/P7dR8mSH"
