#!/usr/bin/env bash
set -euo pipefail

plugin_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_root="$(cd "$plugin_dir/.." && pwd)"
server_dir="$release_root/minecraft/server"
src="$plugin_dir/src/me/copimine/artifacts/CopiMineArtifacts.java"
classes="$plugin_dir/build/classes"
jar_file="$plugin_dir/CopiMineArtifacts.jar"
server_jar="$server_dir/plugins/CopiMineArtifacts.jar"

paper_api="${PAPER_API_JAR:-}"
if [[ -z "$paper_api" ]]; then
  paper_api="$(find "$HOME/.m2/repository" -name 'paper-api-*-R0.1-SNAPSHOT.jar' -type f 2>/dev/null | sort | tail -n 1 || true)"
fi
if [[ -z "$paper_api" || ! -f "$paper_api" ]]; then
  echo "Paper API jar not found. Set PAPER_API_JAR to paper-api-1.21.1-R0.1-SNAPSHOT.jar." >&2
  exit 1
fi

cp_entries=("$paper_api")
if [[ -d "$server_dir/libraries" ]]; then
  while IFS= read -r dep; do cp_entries+=("$dep"); done < <(find "$server_dir/libraries" -name '*.jar' -type f)
fi
if [[ -f "$release_root/copimine-admin-plugin/CopiMineUltimateAdminPlus.jar" ]]; then
  cp_entries+=("$release_root/copimine-admin-plugin/CopiMineUltimateAdminPlus.jar")
fi
classpath="$(IFS=:; echo "${cp_entries[*]}")"

rm -rf "$classes"
mkdir -p "$classes"
javac -encoding UTF-8 -cp "$classpath" -d "$classes" "$src"
cp "$plugin_dir/plugin.yml" "$classes/plugin.yml"
cp "$plugin_dir/config.yml" "$classes/config.yml"
cp "$plugin_dir/items.yml" "$classes/items.yml"
rm -f "$jar_file"
jar --create --file "$jar_file" -C "$classes" .
cp "$jar_file" "$server_jar"
echo "Built $jar_file"
echo "Copied $server_jar"
