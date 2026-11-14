# Guardian and Wave 6/7 production update — 2026-11-14

Installed source: c390f85f1476b4e024253c2c09deaf62e7893b71.
Published branch: origin/codex/rift-guardian-polish-20261110.

The user explicitly requested production installation and delegated tests to DeepSeek.
Code/build/install/service readiness are confirmed; native combat, animation/audio quality,
full encounter/restart/player-count acceptance and current CI are NOT VERIFIED. No test tasks ran.

## Installed artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| CopiMineEndEvent.jar | 2486029 | 9c37509db2c1ea747118a612b693617cfb96c8d24312ff75b02ceec28153dc2e |
| CopiMineNarcotics.jar | 370581 | 396fa4055ae7f71b05f5133d6bad62e67c44377f7054654af269ab69a876dcf7 |
| CopiMineClient-0.1.4+26.3.jar | 30061084 | c8d15631ee742f4fa110983a32dc7d21c931b2e35d03d27cc4a3e84a54b630d1 |
| CopiMineResourcePack-26.3.zip | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c |

Both public HTTPS downloads were fetched independently using curl and matched exact size/hash.
Python urllib initially failed its TLS connection; no TLS verification was disabled.

## Production evidence

copimine-game.service is active on the existing :25565 runtime. New startup:
`Client bridge signer is ready` followed by `Done (45.563s)`. No ERROR/SEVERE in
the fresh startup log. Event remains 6294666a-c67d-4b40-82b4-d01bcc8706a0, COLLECTING,
no living event mobs/projectiles and four existing displays. No gameplay actors were spawned.

The first attempt rolled back automatically: its startup check read the old latest.log
before rotation and encountered an old shutdown-time RCON AsyncCatcher error. Its stack
was in Paper command shutdown, not the event plugin. The installer was corrected to
require a fresh first log line before accepting startup. The second attempt succeeded.

## Backups and rollback

Final backup: `/opt/copimine-backups/rift-code-20261114-guardian-wave67-retry1`.
The first complete snapshot is retained at the same path without `-retry1`.
Each contains full runtime.tar, validated custom PostgreSQL copimine.dump, globals.sql,
old plugin/config/property files and checksums.json. These are private server files.
Rollback: stop copimine-game.service; restore the backed-up two JARs, config.yml and
server.properties using the original copimine-game ownership/modes; restart the service.
Do not restore the database/world archive automatically over newer player progress.

## Client

The current ElyPrismLauncher Copimine profile received SHA-256 c8d15631ee742f4fa110983a32dc7d21c931b2e35d03d27cc4a3e84a54b630d1.
Minecraft was closed; nineteen other mod files remained byte-identical.
Local backup: `C:/Users/zavod/AppData/Roaming/ElyPrismLauncher/instances/Copimine/
copimine-backups/20261114-wave67-c390f85f` (one continuous path).

Client download: https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-c8d15631ee742f4f.jar
Pack download: https://copimine.ru/assets/CopiMineResourcePack-26.3-40f569decb8f8f36.zip

Implementation and DeepSeek acceptance handoff: WAVE67_CODE_AND_MUSIC_RELEASE.md.
Machine receipts and deploy scripts: artifacts/20261114-wave67-production/.
All older protected dirty checkouts were preserved. No unrelated plugin was replaced.
