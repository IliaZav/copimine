#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../shared/common.sh"

copimine_require_root
copimine_need tar
copimine_need sha256sum

archive_path="${1:-}"
[[ -n "$archive_path" ]] || copimine_fail "Usage: rollback.sh /path/to/backup.tar.gz"
copimine_require_path "$archive_path"
copimine_require_path "${archive_path}.sha256"

expected_sha256="$(awk '{print tolower($1)}' "${archive_path}.sha256" | head -n 1)"
actual_sha256="$(sha256sum "$archive_path" | awk '{print tolower($1)}')"
[[ "$expected_sha256" =~ ^[0-9a-f]{64}$ ]] || copimine_fail "Malformed backup SHA256 sidecar."
[[ "$expected_sha256" == "$actual_sha256" ]] || copimine_fail "Backup SHA256 mismatch."

stage_root="$(mktemp -d /tmp/copimine-rollback-XXXXXX)"
rollback_timestamp="$(date +%Y%m%d-%H%M%S)"
old_root="${COPIMINE_ROOT}.pre-rollback-${rollback_timestamp}"
old_secrets="${COPIMINE_SECRETS_DIR}.pre-rollback-${rollback_timestamp}"
rollback_armed=0

cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && $rollback_armed -eq 1 ]]; then
    if [[ -d "$old_root" && ! -d "$COPIMINE_ROOT" ]]; then
      mv "$old_root" "$COPIMINE_ROOT" || true
    fi
    if [[ -d "$old_secrets" && ! -d "$COPIMINE_SECRETS_DIR" ]]; then
      mv "$old_secrets" "$COPIMINE_SECRETS_DIR" || true
    fi
    copimine_start_services || true
  fi
  rm -rf -- "$stage_root"
  exit "$exit_code"
}
trap cleanup EXIT

tar -tzf "$archive_path" >/dev/null
tar -xzf "$archive_path" -C "$stage_root" --no-same-owner --no-same-permissions
[[ -d "$stage_root/$(basename "$COPIMINE_ROOT")" ]] || copimine_fail "Backup does not contain the CopiMine project tree."

copimine_stop_services
if [[ -d "$COPIMINE_ROOT" ]]; then
  mv "$COPIMINE_ROOT" "$old_root"
fi
if [[ -d "$COPIMINE_SECRETS_DIR" ]]; then
  mv "$COPIMINE_SECRETS_DIR" "$old_secrets"
fi
rollback_armed=1

mv "$stage_root/$(basename "$COPIMINE_ROOT")" "$COPIMINE_ROOT"
if [[ -d "$stage_root/$(basename "$COPIMINE_SECRETS_DIR")" ]]; then
  mv "$stage_root/$(basename "$COPIMINE_SECRETS_DIR")" "$COPIMINE_SECRETS_DIR"
fi
chmod 700 "$COPIMINE_SECRETS_DIR"
copimine_start_services
copimine_verify_runtime
rollback_armed=0
copimine_log "Rollback complete from $archive_path. Previous files are retained at $old_root."
