# Wave 7 miniboss spell meshes — production release 2026-11-14 (second increment)

Installed source: c5cfaa41 (branch `codex/rift-wave7-visuals-20261114`).
Supersedes the artifacts in `WAVE7_VISUALS_RELEASE.md` (installed source a7de87e1),
which stay in this directory as the record of that deploy.

The user asked for the Wave 7 miniboss spells to be fully reworked. A review pass over
the previous increment found that the rework had stopped short for four of them.

## What was wrong

`BossSpellGeometryPolicy.modelData` returned `0` for `RIFT_GATE`, `SNARE_CHAINS`,
`ECHO_BLOOM` and `EUPHORIA_HELIX`, and `playBossSpellModel` returns immediately on a
`0` selector. Those four shapes are exactly `rift_step`, `void_snare`, `echo_pulse` and
`rift_euphoria` — Wave 7 miniboss abilities, dispatched live at
`telegraphMiniBossSpell` and `renderWave7AbilityEffect`. So the four spells whose whole
purpose is closing on the player were the four rendered as nothing but
`END_ROD`/`DUST` points from `BossSpellPresentationEffects.frame`. That is the
"particle-only substitute" the specification forbids, and the comment above the `Shape`
enum claimed those shapes had avoided it — the comment contradicted `modelData`.

## What changed

- The committed generator `CopiMineClient/tools/generate_guardian_spell_meshes.py`
  now authors sixteen meshes instead of twelve: a gate with real depth, six chains
  drawn inward toward a base ring, a three-latitude bloom on a column, and a
  twin-strand helix on a base ring. Regenerating leaves the original twelve
  byte-identical (`existing_changed=0`), so nothing else moved.
- `GuardianSpellMesh.NAMES` grew to sixteen; `GuardianSpellRenderer` maps
  `830212`–`830215` to the new meshes, sized 1.35/1.5/1.4/1.3 to a caster body
  rather than to a boss-scale target.
- `telegraphMiniBossSpell` now spawns the body once per cast, mirroring the boss path:
  `LOCKED_FLOOR` shapes land on the committed mark (`+0.6`), everything else sits on
  the caster (`+1.2`).
- `SWIPE` and `DEFEAT` are documented as the only deliberate zero-mesh shapes: a
  contact arc has to track the boss's own swing rather than a spawned body, and
  `DEFEAT` is a dissolution that a frozen mesh would contradict.
- A new contract test in `tests/test_spell_visual_authoring.py` walks the whole chain
  — enum arm → `modelData` → `GuardianSpellRenderer` table → `GuardianSpellMesh.NAMES`
  → generated JSON — and fails if any link breaks or if a mesh file has no cuboids.
  It was verified by deleting `rift_gate.json` and watching it fail, then restoring it.

Two pre-existing stale guards were repaired because this change touched them:
`tests/test_end_rift_supplied_asset_checkout.py` hard-coded `len(models) == 10` while
twelve meshes were already tracked (it failed at the previous HEAD too), and
`docs/minecraft-26.3/README.md` documented a 17 063 548-byte client. Both now state
measured values.

## Installed artifacts

| Artifact | Bytes | SHA-256 | SHA-1 |
|---|---:|---|---|
| CopiMineEndEvent.jar | 2496508 | 081f7daf1811da6d4c874ea49855d33d3053c238c9747c1701fd886835bf8860 | 553238171f690e21bd2e92386b179b3c9d2a45c2 |
| CopiMineClient-0.1.4+26.3.jar | 30095762 | 962d2792f7c7bd6ad491fb3dc07806aaa2792a91922ae10bacd6725c1e1ce489 | 81dd17448aef1d65ea14044bd6c70107092f3113 |
| CopiMineResourcePack-26.3.zip (unchanged) | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c | — |

Both artifacts are reproducible, not snapshots:

- the client jar was deleted and rebuilt, producing the identical SHA-256 `962d2792…`;
- `PrepareMigrationCandidates.ps1` was run twice and produced the identical plugin
  SHA-256 `081f7daf…` (exit 0 on the third run, once the lock was re-pinned).

Client download: https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-962d2792f7c7bd6a.jar
The site's download link is admin-assigned (`modUrl`), so publishing the asset does not
change what players are handed; the pack is byte-identical and was not re-uploaded.

## Production evidence

- `systemctl is-active copimine-game.service` → `active`, new run 20:09:11 +07.
- On disk: `plugins/CopiMineEndEvent.jar`, 2496508 bytes, sha256 `081f7daf…`,
  `copimine-game:copimine 644` — identity and ownership preserved.
- Startup: `Enabling CopiMineEndEvent v0.1.0`,
  `END_EVENT_COMBAT_REINDEX … generation=408 restored=4`, `Done (49.950s)`.
- **Zero** `[ERROR]` / `SEVERE` / `Error occurred while enabling` / `Could not load`
  lines since the restart.
- The published jar was fetched back over HTTPS: `200`, 30095762 bytes, and its
  SHA-256 equals the built artifact exactly. All **16** `guardian_spells/*.json`
  resources, including `rift_gate`, `snare_chains`, `echo_bloom` and
  `euphoria_helix`, are present inside the downloaded file.
- Event state untouched: `6294666a-c67d-4b40-82b4-d01bcc8706a0`, `COLLECTING`,
  `generation=408`, `wave=0`, `roster=0`, `boss=none`, `0/50` players online.

## Automated evidence

| Check | Result |
|---|---|
| Filtered suite (`telegraph`, `spell`, `visual`, `wave6`, `wave7`, `boss_cue`, `boss_spell`, `boss_edge_volley`, `asset_checkout`, `release_job`) | **941 passed, 0 failed, 0 errors** |
| Client Gradle `test jar` (JDK 25.0.4.1) | exit 0, all suites green, jar built |
| `GuardianSpellMeshTest` / `Wave7ModelAtlasCoverageTest` | green; 16 meshes parse with bounded faces, in-range UVs and unit normals |
| `PrepareMigrationCandidates.ps1` | exit 0, full pinned set staged |
| Mesh contract test negative check | fails when one mesh file is removed, passes when restored |

## Verification status

| Area | Status |
|---|---|
| Four miniboss signatures have a real client mesh and a mesh-spawn call per cast | IMPLEMENTED |
| Mesh chain (enum → selector → renderer → NAMES → JSON) locked by a test | AUTOMATED-TESTED |
| Artifacts installed on production, service ready, clean boot | LIVE-VERIFIED |
| Published client is byte-identical and contains all 16 meshes | LIVE-VERIFIED |
| Miniboss spells on screen, boss bars, poses, repainted atlases | **BLOCKED** — needs a real client with F2 capture |
| Live combat at 2/5/10/11/20 players | **BLOCKED** — needs real clients |
| Current CI on this HEAD | **NOT VERIFIED** |

Wave 7 still refuses to spawn without an eligible online player
(`WAVE_TEST_START_REFUSED … reason=empty-sandbox-roster`,
`WAVE_INSPECTION_REFUSED … reason=empty-room-roster required=1`), by design, so no
runtime Wave 7 actor could be created from RCON. Confirming that the new bodies appear
on screen remains outstanding and is not claimed.

## Backups and rollback

Backup: `/opt/copimine-backups/rift-wave7-meshes-20261114` — full `runtime.tar`,
validated custom PostgreSQL `copimine.dump`, `globals.sql`, the replaced plugin jar
(`ef457be6…`), `config.yml`, `server.properties`, and `checksums.json`.

Rollback: stop `copimine-game.service`; restore `ef457be6…` as
`plugins/CopiMineEndEvent.jar` plus the backed-up `config.yml` and `server.properties`
with their original `copimine-game` ownership; restart; then republish
`CopiMineClient-0.1.4+26.3-4f492998252ddb99.jar` if the client must move back too.
Do not restore the database or world archive over newer player progress.

## Commits

- `77be5128` give the four miniboss signatures a real body, not dust
- `fa846e6f` re-pin the client artifact for the miniboss meshes
- `c5cfaa41` pin the rebuilt CopiMineEndEvent candidate