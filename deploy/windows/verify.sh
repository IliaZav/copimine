#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
powershell -ExecutionPolicy Bypass -File "$SCRIPT_DIR/verify.ps1" "$@"
