# Wave 7 miniboss hit and death poses — production release 2026-11-15 (fifth increment)

Installed source: `8d94cb58` (branch `codex/rift-wave7-visuals-20261114`).
Supersedes the artifacts in `WAVE7_ABILITY_BODIES_RELEASE.md` (installed source
`a2f01a8b`), which stay here as the record of that deploy.

## What was wrong

The three named Wave 7 trial actors (Warden, Personal Echo, Mobile Marksman) could
be damaged and killed, but the only poses the client knew were the rotation ones
(`WAVE_COMBAT`, `WAVE_WINDUP`, `WAVE_RELEASE`, `WAVE_RECOVER`). A landed hit and a
kill therefore drew exactly the same body as standing still: the party could not
tell a hit from a miss, and the body vanished on the removal tick with no reaction.
That is a presentation defect on an actor the player is actively fighting, and the
objective forbids leaving a combat reaction to "a particle trail" or to nothing.

## What changed

Server (`copimine-end-event`):

- `Wave7MinibossPosePolicy.Pose` gains `HIT` and `DEATH` with their own lease
  windows: `HIT_MILLIS 300` (a flinch shorter than three ticks is invisible at
  20 TPS) and `DEATH_MILLIS 1500` (one authored collapse, then the body is removed).
- `insertRelease` no longer fabricates a `RELEASE` frame in front of `HIT` or
  `DEATH`. Both are player-caused interruptions; a manufactured release frame would
  hide the only reaction the player can attribute to their own attack.
- `CopiMineEndEvent` publishes `HIT` from an `EntityDamageEvent` listener at
  `MONITOR` with `ignoreCancelled`, so a canceled or immune hit never flinches and
  the handler cannot change damage, health or the receipt. A killing blow is skipped
  because the death path owns that frame.
- `DEATH` is published from the owned-entity death path before the Ram, loot and
  progression branches, which can return early.

Client (`tools/minecraft-26.3/client`, embedded into the legacy jar the pack ships):

- `EndEventClientState` accepts `WAVE_HIT` and `WAVE_DEATH` as known poses, so the
  wire values are not dropped as unknown phases.
- The three trial rigs (`RiftEventSkeletonModel`, plus the Echo and Archmage rigs)
  gain the two branches instead of falling through to the idle pose; the flinch is a
  shallow recoil and the death is one authored collapse.

## Tests

| Check | Result |
|---|---|
| `tests/test_minecraft_migration_server_plugins.py` + `tests/test_own_plugin_inventory_contract.py` (pin literals) | **140 passed, 5 skipped** |
| `tests/test_copimine_client_mob_artifact.py` + `test_end_event_current_contract.py::test_staged_client_artifact_matches_current_source_build` (artifact gates) | **8 passed, 1 skipped** |
| Fixed drift batch: `test_end_rift_echo_wiring`, `test_end_rift_inferno_wiring`, `test_boss_target_roster_adapter`, `test_rift_ram_charge_permit_runtime`, `test_boss_attack_damage_admission`, `test_boss_projectile_release_owner` | **113 passed, 2 skipped** |
| Repaired probe fixtures: `test_boss_chest_strike_release`, `test_boss_rage_summon_adapter`, `test_cocoon_fall_adapter_runtime` | **4 passed / 3 passed / 7 passed** |
| Full `python -m pytest -q tests/` | **3649 passed, 22 skipped, 0 failed, 0 errors** (831.08 s, exit 0) |

Guard strength was checked by mutation, not by reading the source. Each of the four
strengthened guards was executed against a deliberately broken product source and
had to FAIL; every file was then restored byte-identically:

| Mutation applied to product source | Guard | Result |
|---|---|---|
| remove `registerOwnedEntity(entity);` from the echo-decoy spawn path | `test_end_rift_echo_wiring` | FAILS-AS-REQUIRED, source restored (`CopiMineEndEvent.java` `76cb47e0…`) |
| make the Inferno restore write `Material.STONE` instead of the captured `BlockData` | `test_end_rift_inferno_wiring` | FAILS-AS-REQUIRED, source restored |
| re-add `recover();` on an accepted Ram charge hit | `test_rift_ram_charge_permit_runtime` | FAILS-AS-REQUIRED, source restored (`RiftRamEncounter.java` `33b001f4…`) |
| delete both `release.run();` calls from the spell-release adapter | `test_boss_projectile_release_owner` | FAILS-AS-REQUIRED, source restored |

## Pre-existing test/probe drift repaired in this increment

None of the following was caused by the pose work; every one was reproduced at the
increment's base revision `44853f6b` and repaired in tests/probes only, with the
product behaviour confirmed correct:

| File | Drift | Why it was stale |
|---|---|---|
| `test_end_rift_echo_wiring.py` | asserted `registerOwnedEntity`/`spawnOwnedMob(` inside `summonEchoDecoys` | the summon was moved onto the shared `spawnOwnedMobAtLocation` helper; the guard now checks the helper *and* that the helper registers ownership |
| `test_end_rift_inferno_wiring.py` | asserted `restoreBlock(` and the exact `hazardJournal != null && !markRestored()` literal | the restore now writes the captured `BlockData` inline and the ownership guard is a different but equivalent condition; the behavioural claims (exact BlockData, journal retirement, retry) are kept |
| `test_boss_target_roster_adapter.py` | probe lacked `bossLastFocusedMillis` and the `Collectors` import | `rotateBossTarget` gained the fairness map |
| `test_rift_ram_charge_permit_runtime.py` | probe lacked `chargeTell` and `ENTITY_RAVAGER_STEP`; asserted `recoveries==1` on an accepted hit | `charge()` gained the run-up tell and, in `0578c711`, deliberately stopped cancelling the charge on a body hit ("body-blocking must not cancel the pillar run"). The guard now asserts one damage, one hit receipt and **no** cancel |
| `test_boss_attack_damage_admission.py` | probe lacked `isBossCombatRuntimeActive(LivingEntity)`, `Snowball.getLocation()/getWorld()`, `Sound.BLOCK_AMETHYST_BLOCK_BREAK`, `renderAuthoredBossImpact` | the rift-projectile path gained the runtime-activity gate, the impact sound and the authored impact silhouette |
| `test_boss_projectile_release_owner.py` | expected `flights==(physical?0:1)`, `releases==(physical?1:0)` | the adapter now releases every spell once at the authored release frame; the duplicate fake flight is gone for all spells. The guard is stronger: `flights==0 && releases==1` for **every** spell |
| `test_boss_chest_strike_release.py` | probe lacked the `bossFuryCombo` collaborator | `cancelBossCastTask` now clears the whole fury combo, so a forced replacement, phase change or defeat cannot let an old follow-up resume |
| `test_boss_rage_summon_adapter.py` | probe's `Entity` lacked `isValid()/isDead()`, and the probe lacked `ownedEntities`, `generation`, `ownedBySession` | the servant sweep prunes dead bodies through the owned map and the session receipt |
| `test_cocoon_fall_adapter_runtime.py` | probe lacked `activeRiftFireballs`, `RiftFireballRuntimeState`, `currentLastSealVisualTask`, `musicForPhase()/playEventMusic` | the cocoon break now drains the separate rift-fireball registry and the scheduled LAST_SEAL visual task, and fury entry restores the phase music |

`tests/test_migration_postgres_start_process.py` reported one 18 997 ms failure in an
intermediate full run. The PowerShell module and the test are byte-identical to
`44853f6b`, and the test passes in isolation (**2 passed, 3.34 s**); it was machine
load, not a defect.

The staged third-party payload was also re-synchronised: the pinned
`thirdparty/client-mods/CopiMineClient-0.1.1.jar` was 13 008 097 bytes, built before
the 16 `guardian_spells` geometries, the 6 guardian animation clips, the Wave 7 GUI
textures, the spell-material atlas and `BossAnimationPlayback.class` existed. A
clean build from the current tracked source produces 15 051 471 bytes; the jar delta
was inspected entry by entry — **35 added entries, 17 changed, 0 removed**, all
tracked source. `scripts/thirdparty/build_modpack.ps1 -SyncBuiltClient` re-staged it
and rebuilt `CopiMineMods.zip` (27 225 151 bytes, sha256 `f332bbe9…`).

This changes the **repository** modpack payload only. The production install package
does not ship `CopiMineMods.zip` and the published resource pack is unchanged, so no
production byte moves with that re-stage.

## Installed artifacts

| Artifact | Bytes | SHA-256 | SHA-1 |
|---|---:|---|---|
| CopiMineEndEvent.jar | 2500191 | f12397d3b0ae724e3df92021f6cb4a33245a18c6e350056a596213f5427bfb63 | b385b4c57de6cb7382473c8a08dabb1fb2d5dfa4 |
| CopiMineClient-0.1.4+26.3.jar | 30096804 | 580669730a2177addb8331d1043e7bee599f5a5aff6141f5478aa4411d1e4616 | 7d0aa3dc96138c3603a290d0a744ad8094f956d6 |
| CopiMineResourcePack-26.3.zip (unchanged) | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c | — |

Client download:
https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-580669730a2177ad.jar

## Production evidence

Re-verified against production on 2026-11-15 (all commands over
`ssh -p 2222 qwerty@90.188.117.117`):

- `systemctl is-active copimine-game.service` → `active`;
  `ActiveEnterTimestamp=Sun 2026-11-15 05:14:17 +07`, `MainPID=85071`.
- On disk: `plugins/CopiMineEndEvent.jar`, 2 500 191 bytes, sha256 `f12397d3…`,
  `copimine-game:copimine 644`.
- Startup: `[CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0`, `Done (43.888s)`.
- **Zero** `[ERROR]` / `SEVERE` / `Error occurred while enabling` / `Could not load`
  lines since the restart.
- Published client: `status=200 bytes=30096804` at
  `https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-580669730a2177ad.jar`.
- Published pack (unchanged): `status=200 bytes=34241093`.
- Installer receipt: `status=INSTALLED_SERVICE_READY`, `sourceCommit=8d94cb58`,
  `clientChanged=true`, `clientServedBytes=30096804`,
  `backup=/opt/copimine-backups/rift-wave7-visuals-20261115`.
- Event state untouched: event `6294666a-c67d-4b40-82b4-d01bcc8706a0`,
  `state=COLLECTING`, `generation=408`, `wave=0`, `boss=none`, `0/50` players online.

The later commits `85c28778` (pin literals in one test and one JSON inventory) and
the test/thirdparty/doc commits of this pass touch no product byte that ships to
production, so the server was not re-deployed for them.

## Verification status

| Area | Status |
|---|---|
| HIT/DEATH poses implemented on both sides of the wire | IMPLEMENTED |
| Pose leases bounded, killing blow left to the death path, canceled hits never flinch | IMPLEMENTED |
| Artifact gates (staged client, modpack, staged build) | AUTOMATED-TESTED |
| Pin literals and inventory match the shipped candidate | AUTOMATED-TESTED |
| Four strengthened guards fail against a mutation and pass against the real source | AUTOMATED-TESTED |
| Plugin + client installed on production, service ready, clean boot | LIVE-VERIFIED |
| Flinch and collapse actually on screen | **BLOCKED** — needs a real client with F2 capture |
| Live Wave 7 combat at 2/5/10/11/20 players | **BLOCKED** — `/cmend test wave 7` refuses with `empty-sandbox-roster`, and `/cmend wave spawn 7` refuses with `required=1` while 0 players are online |
| Obelisk `full_hd` texture resample (1240×1269 vs 1254×1254) | **BLOCKED** — see `TEXTURE_REVIEW_2026-11-14.md`; needs new client+pack pins and human sign-off |
| Current CI on this HEAD | **NOT VERIFIED** |

## Backups and rollback

Backup: `/opt/copimine-backups/rift-wave7-visuals-20261115` — plugin jar, client jar,
`config.yml`, `server.properties`, `checksums.json` and the database/runtime dump
taken by `install_release.py` before the swap.

Rollback: stop `copimine-game.service`; restore the backed-up
`plugins/CopiMineEndEvent.jar` and the previously published client asset with
`copimine-game` ownership; restart. The pack was not changed by this increment, so it
needs no rollback. Do not restore the database or world archive over newer player
progress.

## Commits

- `6bdf6d07` fix(test): stop treating intermission_4 as a retired sound
- `f265121c` fix(end-rift): give the Wave 7 minibosses a hit pose and a death pose
- `3819a3d4` docs(end-rift): record the Wave 7 texture review
- `e8f732d3` chore(client): re-pin the Fabric client for the miniboss hit/death poses
- `8d94cb58` chore(plugin): re-pin the End Event plugin for the miniboss hit/death poses
- `85c28778` chore(pins): repin the End Event candidate literals to the shipped build
