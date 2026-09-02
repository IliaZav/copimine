#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  replace_copimine_static_launcher_release.sh \
    --package DIR | --archive FILE \
    --web-root DIR \
    --release-id ID \
    [--current-link FILE] [--reload-nginx]

The web server must point its document root at --current-link. The script
creates a new immutable release and switches only that symlink. Old releases
are retained for rollback; Minecraft, Paper, worlds and databases are never
addressed by this script.
EOF
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

PACKAGE_ROOT=''
ARCHIVE=''
WEB_ROOT=''
RELEASE_ID=''
CURRENT_LINK=''
RELOAD_NGINX=0

while (($#)); do
  case "$1" in
    --package)
      [[ $# -ge 2 ]] || die '--package requires a directory'
      PACKAGE_ROOT=$2
      shift 2
      ;;
    --archive)
      [[ $# -ge 2 ]] || die '--archive requires a tar.gz file'
      ARCHIVE=$2
      shift 2
      ;;
    --web-root)
      [[ $# -ge 2 ]] || die '--web-root requires a directory'
      WEB_ROOT=$2
      shift 2
      ;;
    --release-id)
      [[ $# -ge 2 ]] || die '--release-id requires an identifier'
      RELEASE_ID=$2
      shift 2
      ;;
    --current-link)
      [[ $# -ge 2 ]] || die '--current-link requires a path'
      CURRENT_LINK=$2
      shift 2
      ;;
    --reload-nginx)
      RELOAD_NGINX=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "unknown argument: $1"
      ;;
  esac
done

[[ -n "$PACKAGE_ROOT" || -n "$ARCHIVE" ]] || die 'provide exactly one of --package or --archive'
[[ -z "$PACKAGE_ROOT" || -z "$ARCHIVE" ]] || die 'provide only one of --package or --archive'
[[ -n "$WEB_ROOT" ]] || die '--web-root is required'
[[ -n "$RELEASE_ID" && "$RELEASE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,80}$ ]] || die 'release id contains unsafe characters'

absolute_path() {
  local value=$1
  [[ "$value" = /* ]] || die "path must be absolute: $value"
  printf '%s' "${value%/}"
}

WEB_ROOT=$(absolute_path "$WEB_ROOT")
if [[ -z "$CURRENT_LINK" ]]; then
  CURRENT_LINK="$WEB_ROOT/current"
else
  CURRENT_LINK=$(absolute_path "$CURRENT_LINK")
fi

forbidden_static_path() {
  local candidate=${1,,}
  [[ "$candidate" =~ (^|/)(world|world_nether|world_the_end|playerdata|stats|advancements|plugins|authme)(/|$) ]] && return 0
  [[ "$candidate" =~ (^|/)[^/]*(\.db|\.sqlite|\.sqlite3|\.env)$ ]] && return 0
  [[ "$candidate" =~ (^|/)(minecraft|paper|purpur|server\.jar)(/|$) ]] && return 0
  return 1
}

safe_static_path() {
  local candidate=$1
  [[ "$candidate" = /* && "$candidate" != "/" ]] || die "unsafe static path: $candidate"
  if forbidden_static_path "$candidate"; then
    die "refusing a game or persistent-data path: $candidate"
  fi
}

safe_static_path "$WEB_ROOT"
safe_static_path "$CURRENT_LINK"
[[ "$CURRENT_LINK" == "$WEB_ROOT"/* ]] || die '--current-link must be inside --web-root'

if [[ -n "$PACKAGE_ROOT" ]]; then
  PACKAGE_ROOT=$(absolute_path "$PACKAGE_ROOT")
  [[ -d "$PACKAGE_ROOT" ]] || die "package directory does not exist: $PACKAGE_ROOT"
  if forbidden_static_path "$PACKAGE_ROOT"; then
    die "package path is not static: $PACKAGE_ROOT"
  fi
else
  ARCHIVE=$(absolute_path "$ARCHIVE")
  [[ -f "$ARCHIVE" ]] || die "archive does not exist: $ARCHIVE"
  if forbidden_static_path "$ARCHIVE"; then
    die "archive path is not static: $ARCHIVE"
  fi
fi

validate_tree() {
  local root=$1
  [[ -f "$root/index.html" ]] || die 'release is missing index.html'
  [[ -f "$root/launcher.html" ]] || die 'release is missing launcher.html'
  [[ -f "$root/news.html" ]] || die 'release is missing news.html'
  [[ -f "$root/mods.html" ]] || die 'release is missing compatibility mods.html'
  [[ -f "$root/assets/public-data/launcher/latest.json" ]] || die 'release is missing launcher metadata'
  [[ -d "$root/downloads/launcher" ]] || die 'release is missing launcher downloads'
  if forbidden_static_path "$root"; then
    die "release root is not static: $root"
  fi

  local item relative
  while IFS= read -r -d '' item; do
    relative=${item#"$root/"}
    if forbidden_static_path "/$relative"; then
      die "release contains protected data: $relative"
    fi
    if [[ -L "$item" ]]; then
      die "release contains a symlink: $relative"
    fi
  done < <(find "$root" -mindepth 1 -print0)
}

TMP_ROOT=$(mktemp -d /tmp/copimine-static-release.XXXXXX)
cleanup() {
  rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT

SOURCE_ROOT=''
if [[ -n "$PACKAGE_ROOT" ]]; then
  SOURCE_ROOT=$PACKAGE_ROOT
else
  archive_entry_list=$(tar -tzf "$ARCHIVE") || die 'cannot inspect release archive'
  while IFS= read -r entry; do
    entry=${entry#./}
    if [[ -z "$entry" ]]; then
      continue
    fi
    [[ "$entry" != /* && "$entry" != ../* && "$entry" != */../* ]] || die "archive contains traversal path: $entry"
    if forbidden_static_path "/$entry"; then
      die "archive contains protected data: $entry"
    fi
  done <<< "$archive_entry_list"
  SOURCE_ROOT="$TMP_ROOT/extracted"
  mkdir -p "$SOURCE_ROOT"
  tar -xzf "$ARCHIVE" -C "$SOURCE_ROOT"
  if [[ ! -f "$SOURCE_ROOT/index.html" ]]; then
    mapfile -t children < <(find "$SOURCE_ROOT" -mindepth 1 -maxdepth 1 -type d -print)
    [[ ${#children[@]} -eq 1 ]] || die 'archive must contain static files at its root'
    SOURCE_ROOT=${children[0]}
  fi
fi

validate_tree "$SOURCE_ROOT"

RELEASES_ROOT="$WEB_ROOT/.copimine-releases"
TARGET_ROOT="$RELEASES_ROOT/$RELEASE_ID"
safe_static_path "$RELEASES_ROOT"
safe_static_path "$TARGET_ROOT"
[[ ! -e "$TARGET_ROOT" ]] || die "release already exists: $TARGET_ROOT"
mkdir -p "$RELEASES_ROOT"
cp -a "$SOURCE_ROOT/." "$TARGET_ROOT/"
validate_tree "$TARGET_ROOT"

old_target=''
if [[ -L "$CURRENT_LINK" ]]; then
  old_target=$(readlink "$CURRENT_LINK")
elif [[ -e "$CURRENT_LINK" ]]; then
  die "refusing to replace a non-symlink current path: $CURRENT_LINK"
fi

if ((RELOAD_NGINX)); then
  nginx -t
fi

TEMP_LINK="$WEB_ROOT/.copimine-current.$$.tmp"
[[ ! -e "$TEMP_LINK" ]] || die "temporary link already exists: $TEMP_LINK"
ln -s "$TARGET_ROOT" "$TEMP_LINK"
mv -Tf "$TEMP_LINK" "$CURRENT_LINK"

rollback_current() {
  local rollback_link="$WEB_ROOT/.copimine-rollback.$$.tmp"
  if [[ -n "$old_target" ]]; then
    ln -s "$old_target" "$rollback_link"
    mv -Tf "$rollback_link" "$CURRENT_LINK"
  else
    rm -f -- "$CURRENT_LINK"
  fi
}

if ((RELOAD_NGINX)); then
  if ! nginx -t; then
    rollback_current
    die 'nginx validation failed after the static release switch; previous current link was restored'
  fi
  if ! nginx -s reload; then
    rollback_current
    die 'nginx reload failed; previous current link was restored'
  fi
fi

printf 'STATIC_RELEASE_ID=%s\n' "$RELEASE_ID"
printf 'STATIC_RELEASE_ROOT=%s\n' "$TARGET_ROOT"
printf 'STATIC_CURRENT_LINK=%s\n' "$CURRENT_LINK"
printf 'NGINX_RELOADED=%s\n' "$([[ $RELOAD_NGINX -eq 1 ]] && printf yes || printf no)"
