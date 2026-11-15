# Wave 7 miniboss spells — live-path repair, production release 2026-11-14 (third increment)

Installed source: `0fe0e779` (branch `codex/rift-wave7-visuals-20261114`).
Supersedes the artifacts in `WAVE7_MINIBOSS_SPELL_MESHES_RELEASE.md` (installed
source `c5cfaa41`), which stay here as the record of that deploy.

A review pass over the second increment found that it had authored the wrong half
of the miniboss spell set. This increment repairs that, and a second defect that
would have hidden the bodies even on a correct client.

## What was wrong

**1. The authored spells were not the ones cast.** `miniBossSpellFor` emits
exactly `ARROW_RAIN`, `RIFT_BOLT`, `DRAGON_BREATH`, `VOID_BURST` and
`WEB_SHOT`. It never emits `RIFT_STEP`, `VOID_SNARE`, `ECHO_PULSE` or
`RIFT_EUPHORIA`, which are the four ids the previous increment gave a profile,
a palette and a mesh. Measured before the fix:

| spell | profile | composition | shape |
|---|---|---|---|
| `rift_step` | yes | yes | RIFT_GATE |
| `void_snare` | yes | yes | SNARE_CHAINS |
| `echo_pulse` | yes | yes | ECHO_BLOOM |
| `rift_euphoria` | yes | yes | EUPHORIA_HELIX |
| `arrow_rain` | **no** | **no** | **none** |
| `rift_bolt` | **no** | **no** | **none** |
| `dragon_breath` | **no** | **no** | **none** |
| `void_burst` | **no** | **no** | **none** |
| `web_shot` | **no** | **no** | **none** |

`renderSpellTelegraphVisual` returns on its first line when
`SpellVisualPolicy.profile(spellId)` is null, so the five spells a Wave 1–6
elite actually casts drew no authored visual at all — not a degraded one, none.

**2. The miniboss body was deleted on the first tick.** `telegraphMiniBossSpell`
called `playBossSpellModel`, whose task fence requires
`Objects.equals(bossUuid, owner)`. `bossUuid` is the Rift Guardian or null, never
a miniboss, so the display was spawned and removed one tick later. Even with
correct meshes, no miniboss body could have been seen. This means the second
increment's four new meshes were never observable in play, independent of the
first defect.

## What changed

- Five new profiles in `SpellVisualPolicy`, five new colour/particle
  compositions in `BossSpellVisualCompositionPolicy`
  (`arrow_rain 0xF2C14E`, `rift_bolt 0x2FB4FF`, `dragon_breath 0x9B4BFF`,
  `void_burst 0x5B2BD6`, `web_shot 0xDDDDEE`), and five new `shape()` arms.
- The shapes reuse authored bodies that already match the ability — arrow rack,
  rift lance, inferno crown, void sphere, snare chains — rather than inventing a
  second family that would need its own visual verification.
- The display setup is now shared by `spawnSpellModelDisplay(at, modelData)`;
  the leases stay separate. The boss keeps `bossUuid` + `bossCastSequence`, the
  miniboss uses caster validity + generation + event id, bounded to eight
  concurrent bodies (`MAX_MINI_BOSS_SPELL_MODELS`) and cleared at the wave
  boundary from `clearWaveEntities`.
- New guards in `tests/test_spell_visual_authoring.py` take the live rotation as
  their source of truth: they parse the `miniBossSpellFor` switch, resolve each
  constant to its id through `MiniBossSpell`, and require a profile, a
  composition and a meshed shape for every one. Authoring the undispatched half
  again cannot pass.
- `tests/test_boss_spell_model_lifecycle.py` now **executes** the miniboss lease
  rather than only reading it: the body must survive the first tick, survive
  four more, expire at its TTL, and die on caster death, generation change,
  event change or explicit clear, staying inside the budget.
- `tests/test_end_rift_wave6_visual_mechanics.py` counts the new wave-boundary
  clear, so a body cannot outlive the wave that owns it.

## Installed artifact

| Artifact | Bytes | SHA-256 | SHA-1 |
|---|---:|---|---|
| CopiMineEndEvent.jar | 2499010 | eadd81ae7c8568fcb13e91cf19c9e8a12d905b5923aa8a569d86714816b30803 | 2069d3bc3a7ae749cb5796161a2202ed265b3902 |
| CopiMineClient-0.1.4+26.3.jar (unchanged) | 30095762 | 962d2792f7c7bd6ad491fb3dc07806aaa2792a91922ae10bacd6725c1e1ce489 | 81dd17448aef1d65ea14044bd6c70107092f3113 |
| CopiMineResourcePack-26.3.zip (unchanged) | 34241093 | 40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c | — |

The plugin build is reproducible: two `PrepareMigrationCandidates.ps1` runs
produced identical bytes, exit 0. This increment changed no client resource, so
the client jar was not rebuilt, re-pinned or re-uploaded; the installer asserts
the published client is still exactly `962d2792…` and aborts otherwise.

## Production evidence

- `systemctl is-active copimine-game.service` → `active`, new run 21:10:48 +07,
  `MainPID=4136817`.
- On disk: `plugins/CopiMineEndEvent.jar`, 2499010 bytes, sha256 `eadd81ae…`,
  `copimine-game:copimine 644` — identity and ownership preserved.
- Startup: `Enabling CopiMineEndEvent v0.1.0`,
  `END_EVENT_COMBAT_REINDEX … generation=408 restored=4`, `Done (44.705s)`.
- **Zero** `[ERROR]` / `SEVERE` / `Error occurred while enabling` /
  `Could not load` lines since the restart.
- Published client still served: `200`, 30095762 bytes.
- Event state untouched: `6294666a-c67d-4b40-82b4-d01bcc8706a0`, `COLLECTING`,
  `generation=408`, `wave=0`, `roster=0`, `boss=none`, `0/50` players online.

## Automated evidence

| Check | Result |
|---|---|
| Filtered suite (`telegraph`, `spell`, `visual`, `wave6`, `wave7`, `boss_cue`, `boss_spell`, `boss_edge_volley`, `asset_checkout`, `release_job`, `elite`, `miniboss`, `model_lifecycle`) | **1010 passed, 0 failed, 0 errors** |
| `tests/test_boss_spell_model_lifecycle.py` | 15 passed (9 boss scenarios + 6 executed miniboss scenarios) |
| `tests/test_end_rift_wave6_visual_mechanics.py` | 37 passed |
| `tests/test_spell_visual_authoring.py` | 7 passed |
| Live-path guard mutation check | fails when one live spell's `shape()` arm is removed |
| Lease guard mutation check | fails when the Guardian lease is reintroduced |
| `PrepareMigrationCandidates.ps1` | exit 0, reproducible plugin bytes |

## Verification status

| Area | Status |
|---|---|
| Every spell the miniboss rotation emits has a profile, palette and meshed shape | IMPLEMENTED |
| Miniboss bodies are leased to the miniboss, bounded, and wave-cleared | IMPLEMENTED |
| Live rotation ↔ authoring table coverage locked by a test | AUTOMATED-TESTED |
| Miniboss lease behaviour (spawn, survive, expire, die, budget, clear) executed in a JVM probe | AUTOMATED-TESTED |
| Artifacts installed on production, service ready, clean boot | LIVE-VERIFIED |
| Published client unchanged and still served byte-exact | LIVE-VERIFIED |
| Spells on screen, boss bars, poses, repainted atlases | **BLOCKED** — needs a real client with F2 capture |
| Live combat at 2/5/10/11/20 players | **BLOCKED** — needs real clients |
| Current CI on this HEAD | **NOT VERIFIED** |

Wave 7 still refuses to spawn without an eligible online player
(`WAVE_TEST_START_REFUSED … reason=empty-sandbox-roster`,
`WAVE_INSPECTION_REFUSED … reason=empty-room-roster required=1`), by design, so no
runtime Wave 7 actor could be created from RCON. On-screen confirmation of these
bodies remains outstanding and is not claimed.

## Backups and rollback

Backup: `/opt/copimine-backups/rift-wave7-miniboss-lease-20261114` — validated
custom PostgreSQL `copimine.dump`, `globals.sql`, `runtime.tar`, the replaced
plugin jar (`081f7daf…`), `config.yml`, `server.properties` and
`checksums.json`.

Rollback: stop `copimine-game.service`; restore `081f7daf…` as
`plugins/CopiMineEndEvent.jar` (and the backed-up `config.yml` /
`server.properties`) with `copimine-game` ownership; restart. The client needs
no rollback: this release did not change it. Do not restore the database or
world archive over newer player progress.

## Commits

- `0fe0e779` author the spells the miniboss rotation actually casts