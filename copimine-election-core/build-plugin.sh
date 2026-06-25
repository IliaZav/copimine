#!/usr/bin/env bash
set -euo pipefail

plugin_dir="$(cd "$(dirname "$0")" && pwd)"
release_root="$(cd "$plugin_dir/.." && pwd)"
server_dir="$release_root/minecraft/server"
src_root="$plugin_dir/src"
classes="$plugin_dir/build/classes"
jar_file="$plugin_dir/CopiMineElectionCore.jar"
server_jar="$server_dir/plugins/CopiMineElectionCore.jar"

paper_api="${PAPER_API_JAR:-}"
if [[ -z "$paper_api" ]]; then
  paper_api="$(find "$HOME/.m2/repository" -name 'paper-api-*-R0.1-SNAPSHOT.jar' 2>/dev/null | sort | tail -n 1 || true)"
fi
if [[ -z "$paper_api" || ! -f "$paper_api" ]]; then
  echo "Paper API jar not found. Set PAPER_API_JAR to paper-api-1.21.1-R0.1-SNAPSHOT.jar." >&2
  exit 1
fi

mapfile -t sources < <(find "$src_root" -name '*.java' -print)
if [[ ${#sources[@]} -eq 0 ]]; then
  echo "No Java sources found for CopiMineElectionCore." >&2
  exit 1
fi

rm -rf "$classes"
mkdir -p "$classes"
javac -encoding UTF-8 -cp "$paper_api" -d "$classes" "${sources[@]}"
cp "$plugin_dir/plugin.yml" "$classes/plugin.yml"
if [[ -f "$plugin_dir/config.yml" ]]; then
  cp "$plugin_dir/config.yml" "$classes/config.yml"
fi
rm -f "$jar_file"
jar --create --file "$jar_file" -C "$classes" .
cp "$jar_file" "$server_jar"
echo "Built $jar_file"
echo "Copied $server_jar"
