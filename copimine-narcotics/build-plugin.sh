#!/usr/bin/env bash
set -euo pipefail

plugin_dir="$(cd "$(dirname "$0")" && pwd)"
release_root="$(cd "$plugin_dir/.." && pwd)"
server_dir="$release_root/minecraft/server"
src_dir="$plugin_dir/src"
classes="$plugin_dir/build/classes"
jar_path="$plugin_dir/CopiMineNarcotics.jar"
server_jar="$server_dir/plugins/CopiMineNarcotics.jar"
server_data_dir="$server_dir/plugins/CopiMineNarcotics"

paper_api="${PAPER_API_JAR:-}"
if [[ -z "$paper_api" ]]; then
  paper_api="$(find "$HOME/.m2/repository" -name 'paper-api-*-R0.1-SNAPSHOT.jar' 2>/dev/null | sort | tail -n 1 || true)"
fi
if [[ -z "$paper_api" || ! -f "$paper_api" ]]; then
  echo "Paper API jar not found. Set PAPER_API_JAR to paper-api-1.21.1-R0.1-SNAPSHOT.jar." >&2
  exit 1
fi

cp_entries=("$paper_api")
if [[ -d "$server_dir/libraries" ]]; then
  while IFS= read -r -d '' file; do
    cp_entries+=("$file")
  done < <(find "$server_dir/libraries" -name '*.jar' -print0)
fi
for dependency in \
  "$release_root/copimine-economy-core/CopiMineEconomyCore.jar" \
  "$release_root/copimine-admin-plugin/CopiMineUltimateAdminPlus.jar" \
  "$release_root/copimine-election-core/CopiMineElectionCore.jar" \
  "$release_root/copimine-artifacts/CopiMineArtifacts.jar"; do
  [[ -f "$dependency" ]] && cp_entries+=("$dependency")
done

mapfile -t sources < <(find "$src_dir" -name '*.java' | sort)
if [[ "${#sources[@]}" -eq 0 ]]; then
  echo "No Java sources found for CopiMineNarcotics." >&2
  exit 1
fi

rm -rf "$classes"
mkdir -p "$classes"
javac -encoding UTF-8 -cp "$(IFS=:; echo "${cp_entries[*]}")" -d "$classes" "${sources[@]}"
cp "$plugin_dir/plugin.yml" "$classes/plugin.yml"
cp "$plugin_dir/config.yml" "$classes/config.yml"
rm -f "$jar_path"
jar --create --file "$jar_path" -C "$classes" .
echo "Built $jar_path"
cp "$jar_path" "$server_jar"
mkdir -p "$server_data_dir"
cp "$plugin_dir/config.yml" "$server_data_dir/config.yml"
echo "Copied $server_jar"
echo "Copied default config to $server_data_dir"
