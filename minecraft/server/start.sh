#!/usr/bin/env bash
set -Eeuo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# Paper/Purpur invokes this script after a crash.  Keep the command small and
# deterministic so systemd and the server's restart hook use the same JVM.
JAVA_BIN="${JAVA_BIN:-java}"
JVM_OPTS="${JVM_OPTS:--Xms2G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -Dfile.encoding=UTF-8}"
exec "${JAVA_BIN}" ${JVM_OPTS} -jar purpur.jar nogui
