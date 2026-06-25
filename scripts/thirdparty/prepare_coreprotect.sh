#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${2:-$(cd "$(dirname "$0")/../.." && pwd)}"
SOURCE_JAR="${1:-}"
TARGET_DIR="$PROJECT_ROOT/thirdparty/server-plugins"
mkdir -p "$TARGET_DIR"

if [[ -z "$SOURCE_JAR" ]]; then
  LOCAL_JAR="$PROJECT_ROOT/minecraft/server/plugins/CoreProtect-CE-23.0.jar"
  if [[ -f "$LOCAL_JAR" ]]; then
    SOURCE_JAR="$LOCAL_JAR"
  else
    echo "Укажи путь к официально скачанному CoreProtect jar или положи CoreProtect-CE-23.0.jar в minecraft/server/plugins" >&2
    exit 1
  fi
fi

if [[ ! -f "$SOURCE_JAR" ]]; then
  echo "Файл не найден: $SOURCE_JAR" >&2
  exit 1
fi

case "$SOURCE_JAR" in
  *.jar) ;;
  *) echo "Нужен .jar файл CoreProtect" >&2; exit 1 ;;
esac

TARGET="$TARGET_DIR/$(basename "$SOURCE_JAR")"
cp "$SOURCE_JAR" "$TARGET"
SHA1="$(sha1sum "$TARGET" | awk '{print $1}')"

echo "CoreProtect staged:"
echo "  file: $TARGET"
echo "  sha1: $SHA1"
echo "Official source:"
echo "  https://hangar.papermc.io/CORE/CoreProtect"
echo "  https://github.com/PlayPro/CoreProtect"
