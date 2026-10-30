# CopiMineWebsiteBridge — signed website → game bridge

Source of the Paper plugin that lets the website perform signed in-game actions.
It used to live only on one PC, outside version control, so a rebuild could not be
reproduced and nobody could prove the server accepts exactly the operations the
admin UI offers. `tests/test_game_bridge_contract.py` now checks the Java source
against `backend.domain.SUPPORTED_ACTIONS`.

## Files

- `plugin.yml` — plugin descriptor (command `cmwebbridge`, console-only).
- `src/me/copimine/website/CopiMineWebsiteBridge.java` — the whole plugin.

## Protocol

The website writes `requests/<32 hex>.json` containing `{"payload": "<json>",
"signature": "<hmac-sha256 hex>"}`. The payload is the JSON request body with
`id`, `createdAt`, `operation` and `params`. The bridge:

1. rejects a symlink, a non-file, or a file over 64 KiB;
2. verifies HMAC-SHA256 with `PLUGIN_API_KEY` from the environment (the plugin
   disables itself when the secret is missing or shorter than 32 characters);
3. writes a receipt `responses/<id>.json` with the payload hash **before**
   executing, so a crash can never silently replay a destructive action;
4. replays the saved result when the same id arrives with the same hash, and
   refuses (`idempotency_conflict` / `operation_uncertain`) otherwise;
5. rejects requests older than 120 s or from the future (`request_expired`).

Responses come back on RCON as `CMWEB:` + base64 JSON. `cmwebbridge health` skips
the file protocol entirely — the website polls it, so it must not allocate files.

## Operations

Player: `kick`, `ban`, `unban`, `heal`, `feed`, `kill`, `gamemode`, `freeze`,
`unfreeze`, `teleport`, `whitelist.add`, `whitelist.remove`.
Server: `time.day`, `time.night`, `weather.clear`, `weather.rain`, `save`.
Snapshots: `inventory.snapshot`, `inventory.remove` (version-fingerprinted).
Bridge: `health`, `link.send`, `map.health`, `map.control`, `map.update`, `map.flush`.

`backend.domain.SUPPORTED_ACTIONS` must stay a subset of what is handled here.

## Build

The plugin needs the Paper API jar for Minecraft 26.3 plus the server's
`libraries/` jars on the classpath. Example on a machine with a Paper server:

```bash
javac -encoding UTF-8 -cp "$PAPER_API_JAR:$(find "$SERVER/libraries" -name '*.jar' | paste -sd:)" \
  -d build/classes src/me/copimine/website/CopiMineWebsiteBridge.java
cp plugin.yml build/classes/
jar --create --file CopiMineWebsiteBridge.jar -C build/classes .
```

Install the jar as `plugins/CopiMineWebsiteBridge.jar` and set `PLUGIN_API_KEY`
in the game server environment to the same value the website uses.