# Last Seal: articulated spells, collapse and fury handoff

Source change: `f0474fd4`, based on `4472aaa6`.
User scope: update the selected Prism profile, continue Guardian implementation,
especially final-phase logic, Kagune animation and spell presentation. Tests
remain assigned to DeepSeek; no Minecraft/Paper playtest was launched here.

## Implemented

- The durable, generation-scoped `GuardianDeathLedger.cocoonBroken()` now owns
  open-shield eligibility. The obsolete timed vulnerability window and a stale
  decorative actor count cannot re-close or re-pin a destroyed cocoon.
- Landing-to-fury admission explicitly enables awareness/AI and gravity,
  invalidates old navigation scheduling and releases old close/ranged deadlines.
  Existing survivors remain: this does not delete small Kagune or spawn new ones.
- Descent accelerates from suspension while retaining the exact 40-tick fall
  and 20-tick landing boundaries and existing persistent checkpoints.
- Authored fall/landing JSONs now use six key poses each: suspension release,
  opposing torso/head twists, asymmetric arm recoil, impact compression and
  damped recovery. End/start brace poses match. Original model bones, pivots,
  hierarchy and artist clips are preserved. The generator remains source-owned.
- Spell Kagune receive a new articulated anticipation/extension/withdrawal
  sequence in code. Extra coil deformation finishes by the first permitted stab
  contact (progress 0.25); damaging root kinematics remain shared with Paper.
- Two narrow energy filaments wrap each temporary spell stem in the same locked
  heading/pitch frame. At most 40 points every four ticks for five stems, using
  the existing event particle budget. No extra AI entities, new damage paths or
  target homing were introduced.
- Landing uses existing bounded 3D fault geometry, staged impact/camera
  presentation, a scoped Kagune eruption cue and sparse dust. Removed the
  unrelated lava shower and unscoped whole-world explosion sounds.

## Resource-pack release repair

The earlier installation accidentally published the **legacy source ZIP**
`c466df56...`, not the converted 26.3 candidate. This also exposed stale
`resourcePackMigration` pins in GitHub Actions run `43867192483`: candidate
preparation failed before Python tests with
`Legacy resource pack does not match the pinned migration source`.

Rebuilt with `resourcepacks/build-migration-pack.py`, preserving its checksum
enforcement. The new migrated artifact is 34373458 bytes, SHA-256
`98d7950558e7da38e37b641d340b7fe89dc0310cbce6ad0671be62432d627176`.
It declares exact format `[97,1]`, contains 34 modern item definitions and 85
registered models. Candidate packaging now takes
`build/minecraft-26.3/CopiMineResourcePack-26.3.zip`, never the legacy ZIP.
Both legacy-source and converted-candidate hashes/sizes are updated in the lock.

## Evidence boundary

Server/bridge and clean client JAR builds succeeded on pinned JDK 25 / Gradle
9.8.1. Resource-pack conversion completed with strict pinned hash verification.
Packaging inspects ZIP CRCs, protocol classes, model/mixin entries, spell clips
and audio entries. `git diff --check` was run.

The initial Prism update installed the production `10d068b9...` mod, backing up
the old 30061084-byte JAR. The new candidate client is 30246359 bytes, SHA-256
`934526b84c0d07988e57cc9734c4da2b697140ff7062890a34afef61f7039102`;
its final installation is recorded with the runtime receipt.

Do not interpret compiled source as a completed visual/gameplay acceptance.
DeepSeek still needs to exercise shield break, stale actor removal, exact
landing timing, restart during descent, permanent fury, projectile retirement,
Kagune contact/miss/cancel, original rig continuity and final victory. No new
claim is made here that all six phases or every recovery path are verified.

## Installed receipt

Published source/pins: `2eaf2183852e8535d847fcf80c50a66055518ae2`.
CodeRabbit reviewed all eight source/animation files from `4472aaa6` through
`f0474fd4`, completed with **zero issues**. Migration pin changes were separately
checked by the real pack builder; they were outside that review snapshot.

Installed EndEvent: 2559497 bytes, SHA-256
`c5dfb34c9a0edba67d63fcb37387153f809109c0f7ac37afa50873664dca081c`.
Bridge remains the coordinated 378217-byte artifact, SHA-256
`4609582cdc08db60e64122b510e7a264a001baeef88fd10f905bee054be4f9a0`.
Client and converted resource-pack hashes/sizes are listed above. Both complete
HTTPS downloads were hashed and matched:

- https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-934526b84c0d0798.jar
- https://copimine.ru/assets/CopiMineResourcePack-26.3-98d7950558e7da38.zip

Server pack SHA-1: `03951b4e43b5564b4d45ea71b07235997ff2bbd7`.
This installation supersedes the earlier legacy-pack publication documented
in `PRODUCTION_INSTALLATION.md`; keep the older report as historical evidence.

Production backup:
`/opt/copimine-backups/end-rift-20261116T125815Z-f0474fd4` (root-only).
Database dump: 8635418 bytes,
SHA-256 `881d32d1d1fbaa4781245b851f36d586464e58c20773cadf53f1d0c014c8d36f`.
Stopped full runtime archive: 1197388865 bytes,
SHA-256 `ac5524be1fd43cf5742d4428f3b4532a595542e3a0be041f31f1c04fedcd2b09`.
Both indexes were validated before atomic JAR/config replacement. No database,
world or credential reset was performed. Plugin-only rollback uses the same
three archive members and procedure as the previous installation report.

`copimine-game.service` is active/running, PID `294199`. Host UTC+07 startup log:
bridge enabled 20:00:35, EndEvent enabling 20:00:44, Paper
`Done (45.435s)` at 20:00:47. No ERROR/SEVERE line was observed in the startup.
This is startup evidence, not a completed encounter or client handshake.

Selected Prism instance `Copimine` was confirmed from `elyprismlauncher.cfg`.
Minecraft was closed during both file replacements. Final installed paths:

```
C:/Users/zavod/AppData/Roaming/ElyPrismLauncher/instances/Copimine/minecraft/mods/CopiMineClient-0.1.4+26.3.jar
C:/Users/zavod/AppData/Roaming/ElyPrismLauncher/instances/Copimine/minecraft/resourcepacks/CopiMineResourcePack-26.3.zip
```

Installed hashes match the published client and converted pack. Previous files
are under the instance's `copimine-mod-backups/20261116-lastseal-155801` and
`copimine-mod-backups/20261116-pack-155858`. Pack order, other mods and user shader
settings were preserved. Minecraft was not launched automatically.

CI source/pin run: https://github.com/IliaZav/copimine/actions/runs/43871297327
At this receipt PostgreSQL migration passed; static/client build was in
progress. Full CI and native acceptance are not claimed.
