#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
STAGE="$PROJECT_ROOT/thirdparty/_modpack_stage"
ZIP="$PROJECT_ROOT/thirdparty/CopiMineMods.zip"
SHA="$PROJECT_ROOT/thirdparty/CopiMineMods.sha1"
FRONTEND_PUBLIC_DATA_DIR="$PROJECT_ROOT/admin-web/frontend/assets/public-data"
FRONTEND_SNAPSHOT="$FRONTEND_PUBLIC_DATA_DIR/modpack_snapshot.json"

rm -rf "$STAGE"
mkdir -p "$STAGE/mods"

for file in \
  "thirdparty/client-mods/CopiMineClient-0.1.0.jar" \
  "thirdparty/client-mods/emotecraft-for-MC1.21.1-2.4.12-fabric.jar" \
  "thirdparty/client-mods/fabric-api-0.116.11+1.21.1.jar" \
  "thirdparty/client-mods/voicechat-fabric-1.21.1-2.6.16.jar" \
  "thirdparty/client-mods/iris-fabric-1.8.8+mc1.21.1.jar" \
  "thirdparty/client-mods/sodium-fabric-0.6.13+mc1.21.1.jar"
do
  [[ -f "$PROJECT_ROOT/$file" ]] || { echo "Missing file for modpack: $file" >&2; exit 1; }
  cp "$PROJECT_ROOT/$file" "$STAGE/mods/"
done

for file in \
  "thirdparty/README_RU.txt" \
  "thirdparty/VOICE_CHAT_OFFICIAL_DOWNLOAD.txt" \
  "thirdparty/checksums.txt" \
  "thirdparty/modpack_manifest.json"
do
  [[ -f "$PROJECT_ROOT/$file" ]] || { echo "Missing file for modpack: $file" >&2; exit 1; }
  cp "$PROJECT_ROOT/$file" "$STAGE/"
done

rm -f "$ZIP"
(cd "$STAGE" && zip -qr "$ZIP" .)
sha1sum "$ZIP" | awk '{print $1}' > "$SHA"

mkdir -p "$FRONTEND_PUBLIC_DATA_DIR"
python3 - <<'PY' "$PROJECT_ROOT/thirdparty/modpack_manifest.json" "$ZIP" "$SHA" "$FRONTEND_SNAPSHOT"
import json
import os
import pathlib
import sys

manifest_path = pathlib.Path(sys.argv[1])
zip_path = pathlib.Path(sys.argv[2])
sha_path = pathlib.Path(sys.argv[3])
snapshot_path = pathlib.Path(sys.argv[4])

manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
sha1 = sha_path.read_text(encoding="utf-8").strip()
snapshot = {
    "available": True,
    "filename": zip_path.name,
    "downloadUrl": "/downloads/CopiMineMods.zip",
    "size": zip_path.stat().st_size,
    "sha1": sha1,
    "modified": int(zip_path.stat().st_mtime),
    "manifest": manifest,
}
snapshot_path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

echo "Built modpack:"
echo "  zip: $ZIP"
echo "  sha1: $(cat "$SHA")"
