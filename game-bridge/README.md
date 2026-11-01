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

Punishment: `kick`, `ban` (with `expiresAt`), `ban.ip`, `unban`, `mute`, `warn`,
`jail`, `unjail`.
Access: `whitelist.add`, `whitelist.remove`.
State: `heal`, `feed`, `kill`, `extinguish`, `effect.clear`, `fly`, `god`, `gamemode`,
`freeze`, `unfreeze`.
Movement: `teleport`, `tp.spawn`, `tp.bed`, `tp.up10`, `tp.random`.
Items: `give`, `inventory.snapshot`, `inventory.remove` (version-fingerprinted),
`inventory.clear`, `inventory.armor-off`, `inventory.repair-hand`.
Effects: `fun.title`, `fun.lightning`, `fun.launch`, `fun.knockback`, `fun.blind`,
`fun.pumpkin`, `effects.burn5`, `effects.slow10`, `effects.god10`.
Server: `time.day`, `time.night`, `weather.clear`, `weather.rain`, `save`.
Bridge: `health`, `link.send`, `map.health`, `map.control`, `map.update`, `map.flush`.

Effects are capped at amplifier 4, `give` at 64 items, teleports refuse ungenerated
chunks, and `jail` only applies a slowness effect — it never moves the player or
touches their inventory.

`backend.domain.SUPPORTED_ACTIONS` must stay a subset of what is handled here.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File game-bridge/build-plugin.ps1
```

Paper API 26.3 is compiled for Java 25 (class file 69), so the build must use a
Java 25 toolchain — a Java 21 `javac` fails with `wrong version 69.0, should be 65.0`.
`build-plugin.ps1` verifies the Paper API against `profile.lock.json`, pulls the
transitive dependencies from `java-plugin-compile-dependencies.lock.json` and produces
a reproducible jar (`--date=2020-01-01T00:00:00Z`).

Install the jar as `plugins/CopiMineWebsiteBridge.jar` and set `PLUGIN_API_KEY`
in the game server environment to the same value the website uses.