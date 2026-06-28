#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo."
  exit 1
fi

ROOT="${COPIMINE_ROOT:-/opt/copimine}"
SERVER_PROPERTIES="$ROOT/minecraft/server/server.properties"
MC_SERVICE="${MC_SERVICE:-copimine-minecraft}"
PLAYERS=("$@")

if [[ "${#PLAYERS[@]}" -eq 0 ]]; then
  PLAYERS=("Alvarez_227" "SudoKillDash9")
fi

python_read_props() {
  python3 - "$SERVER_PROPERTIES" <<'PY'
from pathlib import Path
import json
import sys

props = {}
for raw in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if "=" in raw and not raw.startswith("#"):
        key, value = raw.split("=", 1)
        props[key] = value
print(json.dumps(props, ensure_ascii=False))
PY
}

ORIGINAL_JSON="$(python_read_props)"
ORIGINAL_RCON_ENABLED="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("enable-rcon","false"))' "$ORIGINAL_JSON")"
ORIGINAL_RCON_PASSWORD="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("rcon.password",""))' "$ORIGINAL_JSON")"
RCON_PORT="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("rcon.port","25575"))' "$ORIGINAL_JSON")"
TEMP_PASSWORD="$ORIGINAL_RCON_PASSWORD"
NEEDS_RESTORE=0

if [[ "${ORIGINAL_RCON_ENABLED,,}" != "true" || -z "$ORIGINAL_RCON_PASSWORD" ]]; then
  NEEDS_RESTORE=1
  TEMP_PASSWORD="$(openssl rand -hex 24)"
  python3 - "$SERVER_PROPERTIES" "$TEMP_PASSWORD" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
password = sys.argv[2]
updates = {
    "enable-rcon": "true",
    "rcon.port": "25575",
    "rcon.password": password,
}
lines = path.read_text(encoding="utf-8").splitlines()
seen = set()
out = []
for line in lines:
    if "=" not in line or line.startswith("#"):
        out.append(line)
        continue
    key, _value = line.split("=", 1)
    if key in updates:
        out.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")
path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY
  systemctl restart "$MC_SERVICE"
  sleep 12
fi

RCON_PASSWORD="$TEMP_PASSWORD" python3 - "$RCON_PORT" "${PLAYERS[@]}" <<'PY'
import os
import socket
import struct
import sys

port = int(sys.argv[1])
players = sys.argv[2:]
password = os.environ["RCON_PASSWORD"]

SERVERDATA_AUTH = 3
SERVERDATA_EXECCOMMAND = 2

def send_packet(sock: socket.socket, req_id: int, packet_type: int, body: str) -> None:
    payload = struct.pack("<ii", req_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)

def recv_packet(sock: socket.socket):
    raw_len = sock.recv(4)
    if len(raw_len) != 4:
        raise RuntimeError("RCON length read failed")
    (length,) = struct.unpack("<i", raw_len)
    payload = b""
    while len(payload) < length:
        chunk = sock.recv(length - len(payload))
        if not chunk:
            raise RuntimeError("RCON payload read failed")
        payload += chunk
    req_id, packet_type = struct.unpack("<ii", payload[:8])
    body = payload[8:-2].decode("utf-8", errors="replace")
    return req_id, packet_type, body

with socket.create_connection(("127.0.0.1", port), timeout=8) as sock:
    send_packet(sock, 1, SERVERDATA_AUTH, password)
    auth_id, _ptype, _body = recv_packet(sock)
    if auth_id == -1:
        raise SystemExit("RCON auth failed")
    commands = [
        "lp creategroup admin",
        "lp group admin permission set * true",
    ]
    for player in players:
        commands.append(f"op {player}")
        commands.append(f"lp user {player} parent add admin")
    for index, command in enumerate(commands, start=10):
        send_packet(sock, index, SERVERDATA_EXECCOMMAND, command)
        recv_packet(sock)
PY

if [[ "$NEEDS_RESTORE" -eq 1 ]]; then
  python3 - "$SERVER_PROPERTIES" "$ORIGINAL_RCON_ENABLED" "$ORIGINAL_RCON_PASSWORD" "$RCON_PORT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
enabled = sys.argv[2]
password = sys.argv[3]
port = sys.argv[4]
updates = {
    "enable-rcon": enabled,
    "rcon.port": port,
    "rcon.password": password,
}
lines = path.read_text(encoding="utf-8").splitlines()
out = []
for line in lines:
    if "=" not in line or line.startswith("#"):
        out.append(line)
        continue
    key, _value = line.split("=", 1)
    if key in updates:
        out.append(f"{key}={updates[key]}")
    else:
        out.append(line)
path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY
  systemctl restart "$MC_SERVICE"
fi

echo "ADMIN_GRANT_OK"
printf 'Players: %s\n' "${PLAYERS[*]}"
