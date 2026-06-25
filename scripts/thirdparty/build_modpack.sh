#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
STAGE="$PROJECT_ROOT/thirdparty/_modpack_stage"
ZIP="$PROJECT_ROOT/thirdparty/CopiMineMods.zip"
SHA="$PROJECT_ROOT/thirdparty/CopiMineMods.sha1"

rm -rf "$STAGE"
mkdir -p "$STAGE/mods"

for file in \
  "thirdparty/client-mods/CopiMineClient-0.1.0.jar" \
  "thirdparty/client-mods/emotecraft-for-MC1.21.1-2.4.12-fabric.jar" \
  "thirdparty/client-mods/fabric-api-0.116.12+1.21.1.jar"
do
  [[ -f "$PROJECT_ROOT/$file" ]] || { echo "Missing file for modpack: $file" >&2; exit 1; }
  cp "$PROJECT_ROOT/$file" "$STAGE/mods/"
done

for file in \
  "thirdparty/README_RU.txt" \
  "thirdparty/checksums.txt" \
  "thirdparty/modpack_manifest.json"
do
  [[ -f "$PROJECT_ROOT/$file" ]] || { echo "Missing file for modpack: $file" >&2; exit 1; }
  cp "$PROJECT_ROOT/$file" "$STAGE/"
done

rm -f "$ZIP"
(cd "$STAGE" && zip -qr "$ZIP" .)
sha1sum "$ZIP" | awk '{print $1}' > "$SHA"

echo "Built modpack:"
echo "  zip: $ZIP"
echo "  sha1: $(cat "$SHA")"
