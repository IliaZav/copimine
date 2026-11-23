# End Rift — B-10/B-11/B-12 fixes and B-01…B-14 root-cause ledger

Date: 2026-11-23
Worktree: `C:\Users\zavod\.codex\worktrees\end-rift-live-playtest-repair\cm-merge-20261117`
Branch: `codex/end-rift-live-playtest-repair`
Base HEAD before this work: `60a63b67`

Evidence tiers used below (never conflated):

- `SOURCE` — the change exists in source and is traceable to a class/method.
- `AUTO` — covered by an automated test that passed in this session.
- `PAPER-LIVE` — verified against a running Paper server over rcon/log.
- `NATIVE` — verified by the owner in a real signed client (cannot be produced here).
- `BLOCKED` — the check could not be performed (say so; never a pass).

## 1. Defects fixed in this increment

### B-10 — Guardian cast release left stale mobility/target clocks

- **Where**: `CopiMineEndEvent.cancelBossCastForPhaseChange(LivingEntity)`,
  the forced-spell-replacement block in `castBossSpell`, and
  `CopiMineEndEvent.tickCurrentBoss`.
- **Reproduction (paper)**: trigger a phase transition while a cast is in its
  telegraph. Before the fix the cast task was cancelled but
  `releaseBossCastMobility(...)` never ran, so `bossCastMobilityActive`, the
  suppressed-target state and `lastBossPathRequestMillis` survived the
  transition. The Guardian kept the pre-transition focus and skipped path
  requests for up to one cadence.
- **Fix**: both cancellation paths now call the single release contract
  `releaseBossCastMobility(boss, reason)`. `BossCastMobilityPolicy.recovery()`
  is documented as the only release contract.
- **Regression test**: `tests/test_end_rift_boss_cast_mobility.py`
  (`test_every_cast_release_applies_the_shared_contract`).
- **Status**: `SOURCE` + `AUTO`.

### B-10 — a refused spell silenced melee for the retry window

- **Where**: `CopiMineEndEvent.tickCurrentBoss` (`BOSS_SPELL_RETRY` branch) and
  `BossCloseCombatPolicy.canStrike`.
- **Reproduction (paper)**: when `castBossSpell` refused (no line of sight,
  hazard/terrain rejection), the old code wrote `nextSpellMillis = now + 1000`.
  `BossCloseCombatPolicy.canStrike` treats `now < nextSpellMillis` as "a major
  cast is imminent" and suppresses melee. Every refused spell therefore froze
  all close combat for ~1 s — the visible "Guardian stopped attacking" window.
- **Fix**: a refused attempt writes the new `bossSpellRetryAtMillis` clock. Only
  an accepted cast consumes the real cooldown and clears the retry clock.
- **Regression test**: `tests/test_end_rift_boss_spell_dispatch_contract.py`
  (accepted branch consumes the cooldown and clears the retry clock; refusal
  branch must not write `nextSpellMillis`; the retry gate is consulted).
- **Status**: `SOURCE` + `AUTO`.

### B-11 — `max-active` was a dead key

- **Where**: `CopiMineEndEvent.smallKaguneLivingCap()`,
  `spawnSmallKaguneBatch`, `EventConfig.SmallKaguneTuning`, `config.yml`.
- **Reproduction (paper)**: `max-active` was parsed and validated but never
  read; the batch loop compared against the hard ceiling
  `SmallKaguneSpawnPolicy.MAX_LIVING` (30), so thirty always spawned.
- **Fix**: the live budget is now `smallKaguneLivingCap()`, clamped to
  `1..SmallKaguneSpawnPolicy.MAX_LIVING`. The record bound was widened to the
  contract ceiling and the default / shipped `config.yml` value is 30, so an
  untouched config keeps the approved pressure and an operator may lower it.
- **Regression test**: `tests/test_small_kagune_config.py` (bounds 0/31 rejected,
  1/30 accepted, direct construction of 31 rejected).
- **Status**: `SOURCE` + `AUTO`.

### B-11 — small Kagune stood still (static dolls)

- **Where**: `CopiMineEndEvent.pursueSmallKagune`,
  `tickSmallKagune`.
- **Reproduction (paper)**: the tick only attacked a player who already walked
  into the fixed 3-block radius. Nothing ever moved the display, so the summons
  were decorative until touched.
- **Fix**: between attacks the small Kagune creeps toward the nearest legal
  participant at `SMALL_KAGUNE_PURSUE_STEP` per 4-tick stride, stopping just
  inside strike reach, never outside the arena, never more than 2 blocks of Y
  difference. The windup/strike frames stay rooted so the telegraph remains
  readable. The paired invisible Giant hitbox moves with the display so the
  line-of-sight admission and the visible position cannot drift apart.
- **Regression test**: `tests/test_end_rift_small_kagune_wiring.py`
  (`test_small_kagune_pursue_the_party_instead_of_standing_still`) plus the
  adapter stub in `tests/test_small_kagune_strike_adapter.py`.
- **Status**: `SOURCE` + `AUTO`. Visual reading: `NATIVE` pending.

### B-11 — orphaned invisible Giant carrier

- **Where**: `CopiMineEndEvent` (both invalid-display branches of the tentacle
  tick and the guardian tick).
- **Reproduction (paper)**: a display that failed `isValid()`/ownership was
  removed with a bare `tentacleController.remove(...)`, leaving the paired
  invisible `Giant` hitbox and the UUID maps alive.
- **Fix**: both paths use `removeTentacle(state.entityId(), "tentacle-visual-invalid")`,
  which removes the visual, the carrier and the map entries together.
- **Status**: `SOURCE`.

### B-12 — boss bar latched forever after an unsanctioned removal

- **Where**: `CopiMineEndEvent` `BOSS_DEATH_UNSANCTIONED` path and the
  `onOwnedEntityRemove` UNLOAD branch.
- **Reproduction (paper)**: `/kill`, cleanup or an actor unload moved the event
  to `RECOVERY_REQUIRED` but never retired the bar. The `RECOVERY_REQUIRED`
  tick is a no-op, so the bar froze on the last HP value until the encounter
  object was disabled.
- **Fix**: both unsanctioned-removal paths call `retireBossBar()`.
- **Status**: `SOURCE`. Live HUD reading: `NATIVE` pending.

## 2. B-01…B-14 ledger

| ID | Area | This session | Evidence tier | Next action |
|---|---|---|---|---|
| B-01 | Owned wave mobs: sweep/crit/stunlock | Not re-verified this session | `AUTO` (prior suites) | Needs a real 2-player native combat pass |
| B-02 | Wave 1 carrier charge ownership | Not re-verified this session | `AUTO` (prior suites) | Native pickup/delivery pass |
| B-03 | Wave 2 phantoms / two-player anomalies | Not re-verified this session | `AUTO` (prior suites) | Two real clients; `NATIVE` |
| B-04 | Wave 3 portals/elites/Giant | Not re-verified this session | `AUTO` (prior suites) | Native wave pass |
| B-05 | Official Wave 4 entry | Not re-verified this session | `AUTO` (prior suites) | Official 3→4 transition on a live server |
| B-06 | Wave 5 fog/safe rooms | Not re-verified this session | `AUTO` (prior suites) | Native inside/outside fog comparison |
| B-07 | Wave 6 Ram/armor/skeleton | Not re-verified this session | `AUTO` (prior suites) | Seven-impact native pass |
| B-08 | Wave 7 official start + minibosses | Not re-verified this session | `AUTO` (prior suites) | Official 6→7 transition on a live server |
| B-09 | Guardian damage always breaks gameplay | Not re-verified this session | `AUTO` (prior suites) | P0: multi-attacker live damage probe |
| B-10 | Guardian AI frozen / spells missing | **Fixed this session** | `SOURCE` + `AUTO` | Live timed multiplayer cast/impact count |
| B-11 | Phase 6 Kagune invulnerable/tiny/dead/corpses | **Fixed this session** (budget, pursuit, carrier cleanup) | `SOURCE` + `AUTO` | Native Phase 6 visual + damage pass |
| B-12 | Boss HUD blinking/stale bar | **Fixed this session** (stale bar on unsanctioned removal) | `SOURCE` | Native HUD comparison |
| B-13 | Client/bridge/audio/effects drift | Not re-verified this session; two client trees exist (`CopiMineClient/` and `tools/minecraft-26.3/client/`) | `BLOCKED` | Confirm which tree builds the shipped jar; signed V4 pair check |
| B-14 | Historical/extra risks | Partially covered by the B-11 carrier cleanup | `SOURCE` | Item-by-item re-audit |

## 3. Verification evidence

- **Full Python suite (final, against the deployed source):**
  `3802 passed, 11 skipped, 154 warnings, 2 subtests passed in 788.04s` — exit 0.
- **Curated End Rift gate** (`tests\RunEndRiftEventChecks.ps1 -SkipBuilds`):
  `GATE_EXIT 0`.
- **Pin re-validation** (`tests/test_minecraft_migration_server_plugins.py` +
  the first-party candidate test): `141 passed, 1 skipped`.
- **Built artifact:** `CopiMineEndEvent-26.3.jar`
  sha256 `986442443194942b8c359b1004ff7d23f6b5ca4c161aaf69efe4f2913c99649b`,
  size 2638154 B,
  sha512 `b361ad3d32f7a02ce9da469565333dd9c2c8d7195bf758049c203ad88c9568cfa7af12eb9b42206995edc8a698e3e092e946238b916582fba67aa5cbb723cc07`.
- **Git:** commit `e883da7e` on `codex/end-rift-live-playtest-repair`,
  pushed `60a63b67..e883da7e`. Unrelated dirty files (`thirdparty/*`,
  `copimine-narcotics/*.jar`, `admin-web/.../modpack_snapshot.json`,
  `.serena/`, `CopiMineClient/bin/`) were left untouched.
- **Production deploy:** `qwerty@90.188.117.117:2222`,
  `copimine-game.service`, `/opt/copimine-game/minecraft/server`.
  - jar `211c0859…` → `98644244…`, config `max-active` 4 → 30.
  - backup `/opt/copimine-backups/end-rift-2026-11-23-b10-b12-211c0859-20261123-150919`
    (`CopiMineEndEvent.jar`, `config.yml`, both old hashes).
  - two cold starts, both `active` and `Done`; after each:
    `END_RIFT_INVARIANTS failures=0 last=none countersNonNegative=true
    entityCount=2 tasks=0 bossProxies=0 wave7Blocks=0`.
  - diagnostics: `submitted=32 accepted=32 written=32 dropped=0 failures=0`.
  - `small-kagune.max-active: 30` confirmed on disk after restart.

## 4. Honest limits

- Visual quality ("AAA spell VFX") is `SOURCE` only. The authored compositions
  (`BossSpellVisualCompositionPolicy`, `BossSpellGeometryPolicy`,
  `GuardianSpellRenderer`/`GuardianSpellMesh`, `LastSealSpellEffects`,
  world-beam rendering) exist and are packaged in the client assets, but no
  owner F2 in a real signed client was performed. Do not read this document as
  a visual pass.
- The chest beam (§7/§9H) aim-lock is already implemented and was live-verified
  in the previous increment; it is not changed here.
- No 2-/20-player playtest was possible: no players were online.
