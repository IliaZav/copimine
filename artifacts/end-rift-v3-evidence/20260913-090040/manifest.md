# End Rift V3 evidence manifest

Date: 2026-09-13
Branch: `codex/end-rift-event`
Final source checkpoint: `e584480e9b18e8749adeb91b754dc233fd53d6f9`

## Local Paper restart

- Server directory: `local-runtime/end-rift-server`
- Endpoint: `[::]:25566` (RCON `127.0.0.1:25576`)
- World: `CopiMine`
- Paper/Purpur: `1.21.1-2329-ver/1.21.1@803bf62`
- Restart: `save-all flush` followed by a graceful local RCON `stop`, then
  `tests/StartEndRiftLocal.ps1 -ReadyTimeoutSeconds 360`.
- Startup result: `Done (28.934s)!`; `END_RIFT_LOG_ERRORS=0`.
- Post-restart status: `COLLECTING`, `wave=0`, `event-mobs=0`, `boss=none`,
  `rift-obelisks=0`, `rift-fireballs=0`, Core `CopiMine 8,68,-39`.

## HTTP resource pack verification

- URL: `http://127.0.0.1:8092/CopiMineResourcePack.zip`
- Bytes: `24147549`
- SHA-1: `73E44BED865225CBCE39F42AFA92AFF4DDE1E670`
- SHA-256: `9A5F444EA31F84EB3A5B476B3E65EE1A18B627AA2F57DA8E753E856DB634E47D`

## Artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `copimine-end-event/CopiMineEndEvent.jar` | 651440 | `3E6697B18DD6551121F683B2255BBD2E1057E8885AEEB6E951FD8004429FE9AE` |
| `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` | 9349447 | `1B07DE9FE4A2685D2F092851BFDF007E9DC7261CD2C480BE1769899C5D6DF449` |
| `resourcepacks/build/CopiMineResourcePack.zip` | 24147549 | `9A5F444EA31F84EB3A5B476B3E65EE1A18B627AA2F57DA8E753E856DB634E47D` |

## Evidence files

- `logs/paper-before-restart-latest.log`
- `logs/paper-after-restart-latest.log`
- `server/paper-start.out.log`
- `server/paper-start.err.log`
- `screenshots/` and `video/` are intentionally empty: the current execution
  environment exposes no controllable native Minecraft GUI (`apps=[]`).

## GitHub Actions

- Run `614`: <https://github.com/IliaZav/copimine/actions/runs/34741177389>
- Exact head SHA: `7dee013a4439122c47f0f86db226042d92d374d9`
- Conclusion: `success` (`static-and-contract` and `java-plugins`).

Native client screenshots, video, audio, FPS, camera-visible model alignment,
Z-fighting and manual 3/10/20-client runs remain `NOT VERIFIED`.

## Current full Paper run

- Command: `tests/RunEndRiftOfficialTwoPlayerLive.ps1 -FirstBotName
  EndRiftFinalA -SecondBotName EndRiftFinalB -BotDurationSeconds 900
  -TimeoutSeconds 900`
- Event: `3567fbc7-445c-4afd-bb87-eb33e4d47bd7`; generation `920`.
- Players: 2. W1 `RIFT_CARRIERS`, W2 `RIFT_HUNT`, W3 `RIFT_GATES` with 3
  portals, W4 `OBELISK_ASSAULT`, W5 `BLACK_FOG` with 3 cycles, W6
  `COLLAPSE_RINGS` with 3 rings, W7 `REALITY_SPLIT`: all PASS.
- Boss phases: `AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL`; victory `true`.
- Final RCON state: `UNLOCKED`, `wave=0`, `event-mobs=0`, `boss=none`,
  `rift-obelisks=0/6`, `rift-fireballs=0`.
- Result: exit code `0`; no End Rift error markers. The current map and Core
  were preserved.

## Exact final GitHub checkpoint

- Commit: `e584480e9b18e8749adeb91b754dc233fd53d6f9`.
- Branch: `codex/end-rift-event`; remote SHA matches exactly.
- GitHub Actions run `615`:
  <https://github.com/IliaZav/copimine/actions/runs/34741821764>
- Conclusion: `success` (`static-and-contract` and `java-plugins`).
