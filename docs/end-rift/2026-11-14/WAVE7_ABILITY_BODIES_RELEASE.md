# Wave 7 ability bodies — production release 2026-11-14 (fourth increment)

Installed source: `a2f01a8b` (branch `codex/rift-wave7-visuals-20261114`).
Supersedes the artifacts in `WAVE7_MINIBOSS_LIVE_PATH_RELEASE.md` (installed
source `0fe0e779`), which stay here as the record of that deploy.

## What was wrong

Wave 7's named-trial abilities were authored as layered vanilla particle passes in
`Wave7AbilityEffectPolicy` and nothing else. `renderWave7AbilityEffect` drew its
passes and a release sound, then returned. The objective states plainly that "the
visual work is NOT complete merely because a particle trail exists", and the
player-facing report behind this rebuild was that the effects looked thin and
alike. A particle-only cast is exactly the substitute that rule forbids.

The miniboss rotation had the identical defect one increment earlier and was
repaired the same way. Wave 7 was the last spell family still drawn that way.

## What changed

- `Wave7AbilityEffectPolicy.bodyShape(String)` names a mesh body for each of the
  **15** abilities the effect runtime dispatches:

  | ability | body |
  |---|---|
  | `MARKSMAN_QUICK_SHOT`, `MARKSMAN_SPREAD_SHOT` | `ARROW_RACK` |
  | `MARKSMAN_HEAVY_SHOT`, `MARKSMAN_PAIRED_CROSSFIRE`, `ARCHMAGE_SPEAR` | `RIFT_LANCE` |
  | `MARKSMAN_SUPPORT` | `VOID_RUNE` |
  | `MARKSMAN_DEFENDER` | `SUMMON_ARCH` |
  | `ARCHMAGE_FANGS` | `INFERNO_CROWN` |
  | `ARCHMAGE_FANGS_GROUP` | `FIREBALL_CAGE` |
  | `ARCHMAGE_STEP` | `RIFT_GATE` |
  | `ARCHMAGE_BEAM` | `CHEST_IRIS` |
  | `WARDEN_NARROW_STRIKE` | `AMBUSH_TALONS` |
  | `WARDEN_WIDE_STRIKE`, `WARDEN_SLAM` | `SLAM_FAULT` |
  | `WARDEN_SONIC_LUNGE` | `ECHO_BLOOM` |

  Each reuses an authored spell-kit silhouette whose form matches the ability — an
  arrow rack for the volleys, a lance for the aimed pierces, a rune for the buff,
  an arch for the summon, a crown of fire for the fang line, a cage for the group
  fangs, a gate for the blink, an iris for the beam, talons for the narrow strike,
  a fault for the wide strike and the slam, a sonic bloom for the lunge — so no
  new mesh needs its own separate visual verification. Every referenced shape
  resolves to non-zero `customModelData`; `SWIPE` and `DEFEAT` remain the only
  deliberate zero-mesh shapes and neither is used here.

- `playMiniBossSpellModel` split: the shared implementation is now
  `playCasterSpellBody(caster, shape, label, at, ticks)`, and the miniboss entry
  point just resolves a shape from the spell table and delegates. One spawner and
  one lease implementation now cover every caster-owned body instead of a second
  copy for Wave 7.

- `renderWave7AbilityEffect` spawns the body alongside the passes. The anchor is
  taken from the shape's own geometry (`BossSpellGeometryPolicy.anchor`), not from
  the ability id: a `LOCKED_FLOOR` body (a rune, a fault, a cage) is placed on the
  target's mark where it will land, everything else on the caster.

- `WAVE7_ABILITY_BODY_TICKS = 44` bounds a Wave 7 body to the visible beat of the
  attack instead of letting it linger after the damage resolved. The lease itself
  is unchanged: caster validity + generation + event id, bounded by
  `MAX_MINI_BOSS_SPELL_MODELS`, cleared at the wave boundary from
  `clearWaveEntities`.

## Tests

| Check | Result |
|---|---|
| Filtered suite (`telegraph`, `spell`, `visual`, `wave6`, `wave7`, `boss_cue`, `boss_spell`, `boss_edge_volley`, `asset_checkout`, `release_job`, `elite`, `miniboss`, `model_lifecycle`) | **1012 passed, 0 failed, 0 errors** |
| `tests/test_spell_visual_authoring.py` | 9 passed |
| `tests/test_boss_spell_model_lifecycle.py` | 15 passed (9 boss + 6 executed miniboss scenarios) |
| `test_every_wave7_ability_dispatched_has_a_mesh_body_behind_its_particles` | takes the ids from the runtime's own dispatch table, requires a `bodyShape` arm for each, then compiles and runs `Wave7BodyProbe` against the real policy to prove each resolved shape yields non-zero `customModelData` |
| `test_the_wave7_renderer_spawns_the_mesh_body_and_not_only_particles` | pins the renderer: passes stay, a body is also spawned, and the floor anchor is honoured |
| miniboss lease guard | follows the delegation to `playCasterSpellBody` and still requires that neither Guardian key (`bossUuid`, `bossCastSequence`) appears |
| Mutation checks (byte-safe, real files) | pointing a body at the mesh-less `SWIPE`, deleting a body arm, and removing the renderer's spawn each **fail** as required; both sources restored with identical sha256 (`478cf96b…`, `63632183…`) |
| `PrepareMigrationCandidates.ps1` | exit 0, reproducible plugin bytes across two runs |

## Installed artifact

| Artifact | Bytes | SHA-256 | SHA-1 |
|---|---:|---|---|
| CopiMineEndEvent.jar | 2499849 | b9d04e08d106246a4cbf8d627d3018a04c1a1338a899e77cfd8eea047d2d2cb3 | 65456751186e9b1e7c4eccdef34f38aa6a5dddfc |
| CopiMineClient-0.1.4+26.3.jar (unchanged) | 30095762 | 962d2792f7c7bd6ad491fb3dc07806aaa2792a91922ae10bacd6725c1e1ce489 | 81dd17448aef1d65ea14044bd6c70107092f3113 |
| CopiMineResourcePack-26.3.zip (unchanged) | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c | — |

The plugin sha512 is `8a9499352d6c2ab4a1d25117a193c4b7e409606b47df0c500c1942009c097a077c2819c59ba43fb2ef7bfbd0fa0e934e6b03515cc2690644eb8e61c0c08b0df9`.
`tools/minecraft-26.3/server-plugins.lock.json` was re-pinned to this candidate,
with the superseded `eadd81ae…` recorded in its `replaceSha256` list.

This increment changes no client resource, so the client jar was not rebuilt,
re-pinned or re-uploaded; the installer asserts the published client is still
exactly `962d2792…` and aborts otherwise.

## Production evidence

- `systemctl is-active copimine-game.service` → `active`, new run 22:15:27 +07,
  `MainPID=4156369`.
- On disk: `plugins/CopiMineEndEvent.jar`, 2499849 bytes, sha256 `b9d04e08…`,
  `copimine-game:copimine 644`.
- Startup: `Enabling CopiMineEndEvent v0.1.0`,
  `END_EVENT_COMBAT_REINDEX … generation=408 restored=4`, `Done (45.843s)`.
- **Zero** `[ERROR]` / `SEVERE` / `Error occurred while enabling` /
  `Could not load` lines since the restart.
- Published client still served: `200`, 30095762 bytes.
- Event state untouched: `6294666a-c67d-4b40-82b4-d01bcc8706a0`, `COLLECTING`,
  `generation=408`, `wave=0`, `roster=0`, `boss=none`, `0/50` players online.

## Verification status

| Area | Status |
|---|---|
| Every dispatched Wave 7 ability names a real, meshed body | IMPLEMENTED |
| Wave 7 bodies leased to the caster, bounded, wave-cleared | IMPLEMENTED |
| Dispatch-table ↔ body-table coverage locked by an executed probe | AUTOMATED-TESTED |
| Renderer spawn + anchor selection pinned | AUTOMATED-TESTED |
| Artifacts installed on production, service ready, clean boot | LIVE-VERIFIED |
| Published client unchanged and still served byte-exact | LIVE-VERIFIED |
| Wave 7 bodies on screen, palettes, atlases | **BLOCKED** — needs a real client with F2 capture |
| Live combat at 2/5/10/11/20 players | **BLOCKED** — needs real clients |
| Current CI on this HEAD | **NOT VERIFIED** |

Wave 7 still refuses to spawn without an eligible online player
(`WAVE_TEST_START_REFUSED … reason=empty-sandbox-roster`,
`WAVE_INSPECTION_REFUSED … reason=empty-room-roster required=1`), by design, so no
runtime Wave 7 actor could be created from RCON. On-screen confirmation of these
bodies remains outstanding and is not claimed.

## Backups and rollback

Backup: `/opt/copimine-backups/rift-wave7-ability-bodies-20261114` — validated
custom PostgreSQL `copimine.dump`, `globals.sql`, `runtime.tar`, the replaced
plugin jar (`eadd81ae…`), `config.yml`, `server.properties` and `checksums.json`.

Rollback: stop `copimine-game.service`; restore `eadd81ae…` as
`plugins/CopiMineEndEvent.jar` (and the backed-up `config.yml` /
`server.properties`) with `copimine-game` ownership; restart. The client needs no
rollback: this release did not change it. Do not restore the database or world
archive over newer player progress.

## Commits

- `a2f01a8b` give every Wave 7 named-trial ability a real mesh body