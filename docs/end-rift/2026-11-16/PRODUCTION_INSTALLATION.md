# Production installation — November 16, 2026

Explicit user authorization: resume production deployment; server accessible.
Source candidate `5c00a22c03c54b33ede1bd46282d429df93fcef8`;
source/pins published as `a3f55b1461a8568f9c72a7e621424e968df89d39`.
Branch: `origin/codex/rift-guardian-polish-20261110`.

## Installed and published

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| CopiMineEndEvent.jar | 2558505 | ad9b2dbbce968a270d6865f6215217d6d0d4651d3a3e5048c26ca1b63f49c7cb |
| CopiMineNarcotics.jar | 378217 | 4609582cdc08db60e64122b510e7a264a001baeef88fd10f905bee054be4f9a0 |
| CopiMineClient-0.1.4+26.3.jar | 30245226 | 10d068b998667aceb9a8b51cbb9b47b3fbd17ccaf16ab8cd93d1acab83c7153f |
| CopiMineResourcePack.zip | 27938837 | c466df569030e045890d2c2bcbffa6ce653ad9ede39996aeb88c2d759345455f |

Public immutable client:
https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-10d068b998667ace.jar

Public immutable resource pack:
https://copimine.ru/assets/CopiMineResourcePack-26.3-c466df569030e045.zip

Both complete HTTPS responses were hashed after installation and matched the
candidate. `server.properties` now points to this pack and SHA-1
`c1246a657207a3eb83cbcd31e6b1622e1bc107e7`.
No website code, account configuration or unrelated plugin was replaced.
The current Prism profile was not changed in this installation. Replace its
old CopiMineClient JAR with this release and restart Minecraft; do not keep two
versions of the same mod. The required asset bundle remains in the client.

## Restorable backup and replacement

Target host `server-rpgrp`, service `copimine-game.service`, runtime
`/opt/copimine-game/minecraft/server`, native PostgreSQL database `copimine`.
Public status reported zero players before the controlled stop.

Root-only backup directory:
`/opt/copimine-backups/end-rift-20261116T122656Z-5c00a22c`.

- `copimine.dump`: 8630487 bytes, PostgreSQL custom-format dump;
  SHA-256 `2d2ba4da4652a3f2c21cf9127fec723616d9cff6a8fa2350d30d9fcd8c27a8b4`.
- `runtime.tar.gz`: 1197445988 bytes, complete stopped runtime;
  SHA-256 `6cb281258c449f5e641d435d9814791b157cdab846d4d189406608b17d2fca67`.
- PostgreSQL globals, archive indexes, `SHA256SUMS`, candidate manifest and
  `installation.json` are retained inside that protected directory.

`pg_restore --list` and `tar -tzf` completed before replacement. A database
restore was not performed. No worlds, credentials or schemas were reset.
Old plugin hashes were explicitly checked: EndEvent `116a757f...`, bridge
`396fa405...`. Staged, `.new` and final hashes were verified; original plugin
owner/group/modes were preserved, followed by atomic rename and sync.

For a plugin-only rollback, stop `copimine-game`, restore only these members
from the backup archive into `/opt/copimine-game/minecraft`, then start it:

```
server/plugins/CopiMineEndEvent.jar
server/plugins/CopiMineNarcotics.jar
server/server.properties
```

Do not restore the entire world or database over subsequent player progress.
Restore the matching older client when reverting the signed presentation
protocol. Backups contain private configuration and must remain root-only.

## Observed runtime and review

Remote log timestamps below use the host's UTC+07 timezone:

- 19:29:16 — bridge enabled with mandatory CopiMineClient support.
- 19:29:25 — EndEvent enabling.
- 19:29:27 — Paper `Done (44.737s)`.
- Main PID after replacement: `284348`; systemd `active/running`.
- Event retained `6294666a-c67d-4b40-82b4-d01bcc8706a0`, phase `COLLECTING`,
  no living event enemies or projectiles. No new encounter was forced.
- No `ERROR` or `SEVERE` entry was observed in this startup journal.

CodeRabbit CLI completed with zero issues for the eight source files changed
from `960576aa` through `5c00a22c`. Earlier Echo review from `3750397c` through
`bb08f613` also completed with zero issues; later Echo corrections were
manually reviewed and compiled, not included in that earlier review scope.

GitHub Actions for the source/pin commit:
https://github.com/IliaZav/copimine/actions/runs/43866628690

At this snapshot `migration-postgres-contract` passed; `static-and-contract`
was still running. This is not a final green-CI claim.

Status: IMPLEMENTED, BUILT, ARTIFACT-INSPECTED, PRODUCTION-INSTALLED,
STARTUP-OBSERVED. Automated gameplay suites remain assigned to DeepSeek.
No real Minecraft connection, F2 inspection, full wave/boss completion,
performance benchmark or recovery exercise was performed. Therefore native
visuals and official gameplay remain NOT VERIFIED.
