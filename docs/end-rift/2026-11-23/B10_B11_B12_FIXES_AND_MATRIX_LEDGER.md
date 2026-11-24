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

### B-10 — a lost channel callback could freeze the Guardian forever

- **Where**: `CopiMineEndEvent.reconcileBossCastProjection(LivingEntity, long)`.
- **Reproduction (paper)**: the chest beam, edge volley and chest strike each
  park the timeline reconcile through the
  `bossEdgeVolleyActive || bossChestStrikeActive || bossChestBeamActive` early
  return, while `tickCurrentBoss` re-roots the body every tick as long as
  `bossAbilityState != NONE`. Each channel clears its own flag from inside its
  repeating/later task. If that task ever died without running its finish/abort
  (an exception thrown inside a repeating renderer, or a scheduler that never
  ran the task), the flag stayed set, the early return skipped every release and
  the Guardian stayed rooted permanently — the "AI frozen" symptom the whole
  contract exists to prevent.
- **Fix**: because every cast and channel already stamps `bossCastDeadlineMillis`
  at start and its completion callback clears it, the reconcile runs a fail-safe
  first: once that deadline is overdue by
  `BossCastMobilityPolicy.STALE_CAST_MILLIS` (20 s, far longer than any authored
  cast), the channel callback is treated as lost and the single release contract
  runs (`cancelBossCastTask()` + `cancelBossFinalStrike()` + state `NONE` +
  hazard release + `releaseBossCastMobility(..., "stale-cast-watchdog")`). This
  gives the previously-unwired `BossCastMobilityPolicy.stale(...)` its first
  production call site.
- **Low-TPS safety (the first attempt regressed)**: a wall-clock-only check is
  *not* sufficient. The edge-volley and chest-strike probes advance the wall
  clock by 100 s to model one low-TPS frame where scheduled salvos are still
  legitimately owed; a single overdue reconcile there must **not** release the
  live channel, or a second major spell would overlap the in-flight one. The
  watchdog therefore counts **consecutive overdue reconciles**
  (`bossCastStaleReconcileTicks`) and only fires at
  `BossCastMobilityPolicy.STALE_RECONCILE_TICKS` (400). A lost callback is
  overdue on every per-tick reconcile so the counter keeps climbing; a lag spike
  is a single reconcile and the counter resets. Live-channel ownership is
  preserved under low TPS while a genuinely dead callback still self-heals.
- **Regression tests**: `tests/test_end_rift_boss_cast_mobility.py`
  (`test_lost_channel_callback_cannot_freeze_the_guardian`) pins that the
  watchdog is evaluated *before* the channel early return and that it applies
  the shared release contract and invalidates callbacks;
  `tests/test_boss_edge_volley_runtime.py` and
  `tests/test_boss_attack_damage_admission.py` pin that one overdue reconcile
  does **not** release a live volley / chest strike.
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

Item-scoped re-run 2026-11-23 (each group run with a fresh `--basetemp`;
summary lines captured in `bite2_summary.txt`). All groups exit 0.

| ID | Area | This session (item-scoped suite) | Evidence tier | Next action |
|---|---|---|---|---|
| B-01 | Owned wave mobs: sweep/crit/stunlock | **68 passed** — 10 files (`wave_ability_damage_admission`, `wave_skeleton_protection_runtime`, `mob_combat_runner_contract`, `event_combat_profile*`, `hive_ai_contract`, `wave_ai_roles`, `wave2_melee_attack_admission`) | `AUTO_TESTED_ONLY` | Native 2-player sweep/crit/stunlock pass |
| B-02 | Wave 1 carrier charge ownership | **69 passed** — 11 files (`wave_one_elite_carrier`, `wave1_*`, `wave_carrier_marker_adapter`, `wave_one_cleared_pack_continues`, `wave1_interaction_harness`) | `AUTO_TESTED_ONLY` | Native pickup/delivery pass |
| B-03 | Wave 2 phantoms / two-player anomalies | **54 passed** — 22 files (`wave_two_*`, `phantom_*`, `official_wave2_combat_probe`) | `AUTO_TESTED_ONLY` | Two real clients; `NATIVE` |
| B-04 | Wave 3 portals/elites/Giant | **94 passed** — 12 files (`wave3_*`, `portal_*`, `rift_giant_runtime`, `wave34_and_echo_harnesses`) | `AUTO_TESTED_ONLY` | Native wave pass |
| B-05 | Official Wave 4 entry | **54 passed** — 13 files (`wave4_*`, `end_rift_wave4_*`, `wave_four_obelisk_fallback`) | `AUTO_TESTED_ONLY` | Official 3→4 transition on a live server |
| B-06 | Wave 5 fog/safe rooms | **134 passed** — 20 files (`wave5_*`, `fog_effect_cleanup`, `shaderpack_withdrawal_safety`, `wave_coordinator_fog_lifecycle`, `shelter_mob_exclusion`, black-fog fallback) | `AUTO_TESTED_ONLY` | Native inside/outside fog comparison |
| B-07 | Wave 6 Ram/armor/skeleton | **349 passed, 4 warnings** — 36 files (`rift_ram_*`, `end_rift_wave_six_*`, `wave6_*`, `wave6_visual_mechanics`) | `AUTO_TESTED_ONLY` | Seven-impact native pass |
| B-08 | Wave 7 official start + minibosses | **387 passed** — 61 files (`wave7_*`, `reality_split_trials_contract`, `miniboss_counterplay`) | `AUTO_TESTED_ONLY` | Official 6→7 transition on a live server |
| B-09 | Guardian damage always breaks gameplay | **155 passed, 2 skipped** — 6 files (`boss_attack_damage_admission`, `guardian_death_wiring`, `boss_admin_death_cinematic`, `guardian_live_shield_adapter`, `ring_owner_acceptance`, `tentacle_combat_evidence_contract`) | `AUTO_TESTED_ONLY` | P0: multi-attacker live damage probe |
| B-10 | Guardian AI frozen / spells missing | **Fixed this session** | `SOURCE` + `AUTO` | Live timed multiplayer cast/impact count |
| B-11 | Phase 6 Kagune invulnerable/tiny/dead/corpses | **Fixed this session** (budget, pursuit, carrier cleanup) | `SOURCE` + `AUTO` | Native Phase 6 visual + damage pass |
| B-12 | Boss HUD blinking/stale bar | **Fixed this session** (stale bar on unsanctioned removal) | `SOURCE` | Native HUD comparison |
| B-13 | Client/bridge/audio/effects drift | Which tree builds the shipped jar is now resolved: the 26.3 client artifact is `CopiMineClient-0.1.4+26.3.jar` (30 367 332 B, SHA-512 `5cf53ffe632550d2b145d3f18c76b3d08aec4aeafad98ef2ccd4bb7fe4e91c11c1b33b7562521ca32fa380dc37ab4fa70c6e925f6919feb00ce5fe77430709dd`) pinned in `tools/minecraft-26.3/profile.lock.json → clientArtifact`, built from `tools/minecraft-26.3/client` with shared resources from `CopiMineClient/src/main/resources`, where `GuardianSpellRenderer`/`GuardianSpellMesh`/`LastSealSpellEffects` and the `guardian_spells` geometry are packaged. A stale `CopiMineClient-0.1.3+26.3.jar` (Oct 20) in the sibling `end-rift-26-3-integrated-final` worktree lacks them and is **not** the shipped client. Re-pinned 2026-11-24 to 30 368 290 B / SHA-512 `b6aa678ac5…6643814b` once the authored chest-beam family and cocoon-shatter cinematic were packaged into the client; the Windows CI build reproduced those exact bytes. Re-pinned again 2026-11-24 to 30 371 880 B / SHA-512 `4ad8f2c33a…45bc40` once the three authored floor-hazard bodies (rock eruption, rift crack, pressure fire column) replaced the shared ground-slam fragment in the packaged geometry. | `SOURCE` (+ prior `AUTO`) | Native V4-signed client/server pair check |
| B-14 | Historical/extra risks | **32 passed** — 7 files (`task5_projectile_regressions`, `rift_ram_restart_cleanup_runtime`, `scoped_cleanup`, `recovery_contract`, `evidence_portability`, `seal_lance_volley_guard`, `java_tests_are_all_executed`); carrier cleanup covered by B-11 | `AUTO_TESTED_ONLY` | Item-by-item native re-audit |

**Boss-AI evidence (objective §5, "нормальный ИИ боссу").** Dedicated group
**52 passed** — `test_boss_ai_polish.py`, `test_boss_ambush_major_cast.py`,
`test_end_rift_ai_phase_probe_contract.py`, `test_end_rift_guard_tactics_runtime.py`,
`test_end_rift_boss_cast_mobility.py`, `test_boss_cast_locomotion_runtime.py`,
`test_boss_cast_position_adapter.py`,
`test_boss_animations_resolve_to_authored_clips.py`,
`test_end_rift_wave_ai_roles.py`. Tier `AUTO_TESTED_ONLY`.

**`SOURCE`-tier visual pipeline (objective §6/§9A–§9L).** 16 authored spell
meshes under `CopiMineClient/src/main/resources/assets/copimineclient/geometry/guardian_spells/`;
`GuardianSpellRenderer` / `GuardianSpellMesh` / `LastSealSpellEffects` present in
`tools/minecraft-26.3/client`; 23 authored compositions in
`BossSpellVisualCompositionPolicy` (16 boss spells + `chest_beam` + `phase_shift`
and pool entries). Tier `SOURCE` — renders only after the owner's native pass.

**Native visual acceptance.** Per §2/§8 of `APPROVED_WAVE_CONTRACT.md`, visual
requirements cannot be satisfied by automated checks alone; an unavailable native
check is `BLOCKED`, never a pass. The owner-facing procedure is
`docs/end-rift/2026-11-23/NATIVE_F2_VISUAL_CHECKLIST.md`. Until the owner
replays it in the V4-signed `CopiMineClient-0.1.4+26.3.jar`, every §9A–§9L
visual item and the AAA-visual objective remain **`BLOCKED`**.

## 3. Verification evidence

- **Item-scoped re-run (2026-11-23), all groups exit 0** — B-01 68 / B-02 69 /
  B-03 54 / B-04 94 / B-05 54 / B-06 134 / B-07 349 (+4 warnings) / B-08 387 /
  B-09 155 (+2 skipped) / B-14 32 = **1396 passed, 2 skipped**, plus the boss-AI
  group **52 passed**. Each group ran with a fresh `--basetemp`. Summary lines:
  `C:\Users\zavod\AppData\Local\Temp\opencode\bite2_summary.txt`.
- **Full Python suite (final, against the deployed source):**
  `3803 passed, 11 skipped, 154 warnings, 2 subtests passed in 794.37s` — exit 0.
  (Run 3 exposed a watchdog regression — 8 failed + 101 errors — that is fixed and
  re-verified here; see the B-10 low-TPS section above.)
- **Curated End Rift gate** (`tests\RunEndRiftEventChecks.ps1 -SkipBuilds`):
  `GATE_EXIT 0`.
- **Pin re-validation** (`tests/test_minecraft_migration_server_plugins.py` +
  the first-party candidate test + the cast-mobility contract):
  `171 passed, 3 skipped`.
- **Built artifact:** `CopiMineEndEvent-26.3.jar`
  sha256 `084cc0f53db7906e7fbae01852dc22a956bff3bba44ecb0fb800ec90d7547836`,
  size 2638503 B,
  sha512 `a5277268d8c44fd327e2a88bd5d0c8b1d4530631ee50187333d9c9890bfc04eb90b595b98abd709fc815a6c36f84aa63de1364fa124b6705de5ab277df958e01`.
- **Git:** on `codex/end-rift-live-playtest-repair`: `e883da7e` (B-10/B-11/B-12
  fixes) → `d2d090e1` (low-TPS-safe watchdog) → `c4cde67a` (deploy record) →
  `38e62421` (B-01…B-14 item-scoped evidence + native F2 checklist). Latest push
  `c4cde67a..38e62421`. Unrelated dirty files (`thirdparty/*`,
  `copimine-narcotics/*.jar`, `admin-web/.../modpack_snapshot.json`,
  `.serena/`, `CopiMineClient/bin/`) were left untouched.
- **Production deploy:** `qwerty@90.188.117.117:2222`,
  `copimine-game.service`, `/opt/copimine-game/minecraft/server`.
  - jar `98644244…` → `084cc0f5…` (B-10 low-TPS-safe watchdog); `config.yml`
    `small-kagune.max-active: 30` preserved.
  - backup `/opt/copimine-backups/end-rift-2026-11-23-stale-cast-lptsafe-98644244-20261123-155210`
    (`CopiMineEndEvent.jar`, `config.yml`, old jar sha256).
  - two cold starts, both `active` and `Done (44.2 s / 44.4 s)`; on-disk jar
    sha256 confirmed `084cc0f5…`; after startup:
    `END_RIFT_INVARIANTS failures=0 last=none countersNonNegative=true
    entityCount=2 tasks=0 bossProxies=0 wave7Blocks=0`.
  - diagnostics: `submitted=80 accepted=80 written=80 dropped=0 failures=0`.
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

## 5. N23-P1 increments (2026-11-24)

Evidence tiers: `SOURCE` / `AUTO` / `NATIVE_BLOCKED`, as in section 1.

### N23-P1-03 — chest beam no longer re-selects targets at fire time

- **Where**: `CopiMineEndEvent.startChestBeam` and the new
  `CopiMineEndEvent.cancelChestBeamWithoutLock`.
- **Defect**: the fire branch kept a defensive fallback that called
  `ChestBeamPolicy.selectLaneIndices` whenever the generation-locked lane
  snapshot was absent, so a cast could fire along a lane the party never watched
  lock — the exact silent retarget the no-retarget contract forbids. The old
  contract test explicitly permitted it.
- **Fix**: fire consumes ONLY the frozen snapshot
  (`bossChestBeamLockedGeneration == callbackGeneration && chest != null &&
  !aims.isEmpty()`). Otherwise it fails closed: logs
  `BOSS_CHEST_BEAM_CANCEL_LOCK_LOST`, drops the stale snapshot, clears any staged
  stripe (`clearWorldVfxBeamsByPrefix(CHEST_BEAM_VFX_PREFIX)`), returns to a short
  `RECOVERY`, and refunds the hazard reservation (`chest-beam-lock-lost`).
- **Regression tests**: `tests/test_end_rift_chest_beam_core_stage_guard.py`
  (`test_fire_reuses_the_snapshot_and_never_retargets` now asserts fire-time
  `selectLaneIndices` is absent; new
  `test_lock_loss_cancels_refunds_and_clears_the_beam`).
- **Status**: `SOURCE` + `AUTO`.

### N23-P1-14 — proof and production log noise

- **Where**: new pure policy
  `copimine-end-event/src/me/copimine/endevent/domain/BoundedDiagnosticCounterPolicy.java`
  plus the three hot per-event INFO sites in `CopiMineEndEvent`
  (`WAVE_MOB_ENVIRONMENT_DAMAGE_BLOCKED`, `ELITE_DRAGON_BREATH_CLOUD_DAMAGE_REJECTED`,
  `ELITE_DRAGON_BREATH_CLOUD_DAMAGE`) now routed through
  `logThrottledInfo(...)`.
- **Fix**: one line per key per 30 s window; the reopening line carries the exact
  suppressed total (`suppressed=N`). The key set is bounded (budget + one
  `DIAGNOSTIC_KEY_OVERFLOW` bucket), so an unknown/hostile key cannot grow the
  tracker. The first line of a key stays byte-identical to the old output
  (empty suffix).
- **Regression test**: `tests/BoundedDiagnosticCounterPolicyTest.java` (window
  batching, exact suppressed totals, per-key independence, key-budget bound,
  null/blank fail-closed, clock-rewind reopen), registered in
  `RunEndRiftEventChecks.ps1` `$pureTests`.
- **RCON async-dispatch warnings**: NOT reproduced and NOT attributable here.
  The only asynchronous scheduler task in `CopiMineEndEvent` is
  `checkMainThreadHeartbeat` (reads a heartbeat timestamp; no world access), and
  the `cmend` command executor runs on the server thread. The Paper RCON bridge is
  third-party and cannot be exercised without a running server. Owner/time/stack
  capture and safe reproduction stay `NATIVE_BLOCKED`.
- **Status**: `SOURCE` + `AUTO`; the RCON investigation is `NATIVE_BLOCKED`.

