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
| B-13 | Client/bridge/audio/effects drift | Which tree builds the shipped jar is now resolved: the 26.3 client artifact is `CopiMineClient-0.1.4+26.3.jar` (30 367 332 B, SHA-512 `5cf53ffe632550d2b145d3f18c76b3d08aec4aeafad98ef2ccd4bb7fe4e91c11c1b33b7562521ca32fa380dc37ab4fa70c6e925f6919feb00ce5fe77430709dd`) pinned in `tools/minecraft-26.3/profile.lock.json → clientArtifact`, built from `tools/minecraft-26.3/client` with shared resources from `CopiMineClient/src/main/resources`, where `GuardianSpellRenderer`/`GuardianSpellMesh`/`LastSealSpellEffects` and the `guardian_spells` geometry are packaged. A stale `CopiMineClient-0.1.3+26.3.jar` (Oct 20) in the sibling `end-rift-26-3-integrated-final` worktree lacks them and is **not** the shipped client. Re-pinned 2026-11-24 to 30 368 290 B / SHA-512 `b6aa678ac5…6643814b` once the authored chest-beam family and cocoon-shatter cinematic were packaged into the client; the Windows CI build reproduced those exact bytes. Re-pinned again 2026-11-24 to 30 371 880 B / SHA-512 `4ad8f2c33a…45bc40` once the three authored floor-hazard bodies (rock eruption, rift crack, pressure fire column) replaced the shared ground-slam fragment in the packaged geometry. Re-pinned a third time 2026-11-24 to 30 384 667 B / SHA-512 `4e26b09fdd…1b6c10` once the three Phase 4 infernal summon skins (magma cube / blaze / wither skeleton) were packaged into the shared resources and their 26.3 renderer mixins were added; a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. Re-pinned a fourth time 2026-11-24 to 30 389 722 B / SHA-512 `5967abfa42…97d6506a` once the three residual Kagune spell bodies (stab spine, floor eruption, small claw-knot) replaced three borrowed bodies in the packaged geometry; a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. Re-pinned a fifth time 2026-11-24 to 30 417 908 B / SHA-512 `59eec2ba1e…31ef6469` once the V5.1 authored spell meshes (`SpellVisualGeometry`/`SpellVisualRenderer`, 26.3 client) and their emissive texture were packaged; a clean `gradle test jar` reproduced the exact bytes twice. Re-pinned a sixth time 2026-11-24 to 30 418 805 B / SHA-512 `d2282e70…8a422977` once those V5.1 meshes gained authored depth (Crown crest blades and an animated radial burst, a counter-spinning Circle sigil with ley-spokes, the five-ring Echo shell and layered Guardian fan bands); a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. Re-pinned a seventh time 2026-11-24 to 30 422 528 B / SHA-512 `3873f770bd…3841d1c3` once every V5.1 family gained continuous cast-clock animation and the renderer began sampling the arc texture across the ribbon width; a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. Re-pinned an eighth time 2026-11-24 to 30 424 217 B / SHA-512 `432375e775…67e7599` once the chest beam gained its own full-length layered 3D cross-section (N23-P1-05) and the renderer a full 3D frame; a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. Re-pinned a ninth time 2026-11-24 to 30 425 923 B / SHA-512 `2fb6870c84…531a61484` once the Phase 3 eruption gained its own authored rock/obsidian column (N23-P1-08: growing floor fissures, pressure-glow rings, jagged throat ribs, volumetric flame sheets, ash/debris and a collapsing crown, all inside the authored 1.58-block visual radius / `[0,12]` height); a clean two-run `gradle test jar` on the pinned JDK reproduced both bytes exactly. | `SOURCE` (+ prior `AUTO`) | Native V4-signed client/server pair check |
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
  plus four hot per-event INFO sites in `CopiMineEndEvent`
  (`WAVE_MOB_ENVIRONMENT_DAMAGE_BLOCKED`, `ELITE_DRAGON_BREATH_CLOUD_DAMAGE_REJECTED`,
  `ELITE_DRAGON_BREATH_CLOUD_DAMAGE`, `SKELETON_ARROW_COOLDOWN_BLOCKED` — the
  last one fires on every shot attempt a skeleton makes while its volley
  cooldown runs, so it was the highest-volume line of all) now routed through
  `logThrottledInfo(...)`.
- **Note on warnings**: the remaining `WARN` sites in this plugin are one-shot
  lifecycle/exception reports (`*_REJECTED`, `*_REPAIRED`, rebuild deferred), not
  per-tick repeats, so they are deliberately left unbatched — suppressing those
  would hide exactly the evidence the spec asks to collect. Only demonstrably hot
  repeats are bounded.
- **Fix**: one line per key per 30 s window; the reopening line carries the exact
  suppressed total (`suppressed=N`). The key set is bounded (budget + one
  `DIAGNOSTIC_KEY_OVERFLOW` bucket), so an unknown/hostile key cannot grow the
  tracker. The first line of a key stays byte-identical to the old output
  (empty suffix).
- **Regression test**: `tests/BoundedDiagnosticCounterPolicyTest.java` (window
  batching, exact suppressed totals, per-key independence, key-budget bound,
  null/blank fail-closed, clock-rewind reopen), registered in
  `RunEndRiftEventChecks.ps1` `$pureTests`.
- **Probe-harness parity**: `tests/test_wave_coordinator_fog_lifecycle.py` and
  `tests/test_wave6_converted_arrow_runtime.py` compile *extracted production
  method bodies* into stub classes, so each stub now declares a faithful
  `logThrottledInfo` (always prints the built line). The first full gate run for
  this change failed exactly there, which is what proved the probe coverage is
  real; the fix keeps the probe's combat-fencing contract intact instead of
  weakening the assertion.
- **RCON async-dispatch warnings**: NOT reproduced and NOT attributable here.
  The only asynchronous scheduler task in `CopiMineEndEvent` is
  `checkMainThreadHeartbeat` (reads a heartbeat timestamp; no world access), and
  the `cmend` command executor runs on the server thread. The Paper RCON bridge is
  third-party and cannot be exercised without a running server. Owner/time/stack
  capture and safe reproduction stay `NATIVE_BLOCKED`.
- **Status**: `SOURCE` + `AUTO`; the RCON investigation is `NATIVE_BLOCKED`.

### N23-P1-11 — six authored phase-transition cinematics

- **Where**: new pure policy
  `copimine-end-event/src/me/copimine/endevent/domain/BossPhaseTransitionCinematicPolicy.java`,
  new authored geometry
  `BossImpactGeometryPolicy.phaseTransition(signature, radius, progress)`, new
  finite rig `BossPresentationEffects.playPhaseTransition(...)`, and the
  `synchronizeBossPhase` opener in `CopiMineEndEvent` (plus the two helpers
  `playBossPhaseTransitionPresentation` / `playBossPhaseTransitionCue`).
- **Defect**: every Guardian health phase opened the same way — a fixed 2.2 s
  hold, the banner `«СТРАЖ РАЗЛОМА / Разлом меняет правила боя»`, the
  `PHASE_SHIFT` pose (which the client has **no clip** for, so it rendered as
  `identity()`), the same `phase_shift:impact` cue and the same shared
  `Kind.PHASE` impact ring. Only `LAST_SEAL` had a dedicated intro. Six distinct
  rules of the fight therefore read as one repeated event.
- **Fix**: each phase now names its own authored cinematic — distinct wire id,
  Russian title/subtitle, duration (1600–2600 ms, all distinct), pose clip (all
  resolvable through `BossAnimationId` and all shipped by the client:
  `CAST_CHARGE`, `CAST_RELEASE`, `GROUND_SLAM`, `CHEST_STRIKE`, `MELEE_SWIPE`,
  `LAST_STAND_INTRO`), authored cue (`phase_shift`, `rift_projectile`,
  `rift_obelisks`, `arena_inferno`, `void_blast`, `final_strike` at RELEASE), a
  distinct world-space geometry signature (`ascension`, `stalker`, `fracture`,
  `ember`, `roar`, `last-stand`) and a distinct accent colour. The body is held
  for the cinematic's own bounded window instead of a hard-coded 2.2 s.
- **Fail-closed by construction**: `build()` throws if any axis (id, title,
  subtitle, pose, cue, signature, colour, duration) is duplicated, so a future
  phase cannot silently reuse another phase's moment; an unknown signature draws
  nothing rather than reusing a generic ring; `grantsInvulnerability(...)` is
  `false` for every phase and an unknown phase reuses the authored `AWAKENING`
  cinematic instead of inventing one.
- **Preserved contracts**: the `BOSS_STAGE_TRANSITION` line three live scripts
  wait on (`RunEndRiftAiPhasesLive.ps1`, `RunEndRiftOfficialTwoPlayerLive.ps1`,
  `RunEndRiftSpellMatrixLive.ps1`) is unchanged; a new additive
  `BOSS_PHASE_CINEMATIC` line records id/pose/signature/duration. The `LAST_SEAL`
  block keeps its ascension burst and its 5.2 s last-stand window, and the
  duplicate pose send there was removed because the generic opener now already
  sends `LAST_STAND_INTRO`. `playBossImpactPresentation` is untouched, so the
  existing impact-ownership probes keep their exact contract.
- **Regression test**: `tests/BossPhaseTransitionCinematicPolicyTest.java`
  (one cinematic per phase; distinct durations/ids/titles/subtitles/poses/cues/
  signatures/colours; pose+cue resolvability; six distinct non-empty bounded
  geometries with unknown/null/NaN fail-closed; never-shields; unknown-phase
  fallback), registered in `RunEndRiftEventChecks.ps1` `$pureTests`.
- **Status**: `SOURCE` + `AUTO`. The rendered look of the six rigs and their
  audio is `NATIVE_BLOCKED` (no GUI client, no signed V4 client, no running
  server in this environment).

### N23-P1-11 — attack-starvation instrumentation and a compatible alternative

- **Where**: new pure policy
  `copimine-end-event/src/me/copimine/endevent/domain/BossCastAvailabilityPolicy.java`,
  the `tickCurrentBoss` spell-scheduler guard and refusal receipt, and the
  pool filter in `CopiMineEndEvent.castCurrentBossSpell(LivingEntity, boolean)`.
- **Defect**: `tickCurrentBoss` did `if (isKaguneRingCastActive()) return;`
  immediately after the melee scheduler, so a multi-row Last Seal ring timeline
  produced a long stretch of **zero ranged pressure** even though ordinary melee
  already overlaps the ring (the melee call is one line above). The starvation
  was invisible: nothing recorded that the scheduler had been skipped.
- **Fix**: the ring gate is now an explicit slot. `BusySlot.RING` keeps exactly
  one light, telegraphed, dodgeable spell (`RIFT_PROJECTILE`) and defers every
  body- or floor-owning cast; `BusySlot.AMBUSH` still pauses **all** major casts,
  matching the original overlap-safety rule; `BusySlot.NONE` is unchanged.
  `castCurrentBossSpell` applies the same filter to its dispatchable pool
  (after `bossActionAvailable`), so no second major-cast owner can run under a
  ring. A refusal now emits a bounded `BOSS_CAST_REFUSED_RING` (or
  `BOSS_CAST_REFUSED` when idle) receipt via the existing key-based
  `logThrottledInfo`, so starvation is measurable instead of silent. The
  `BOSS_SPELL_RETRY` FINE line and the "only an accepted cast consumes the major
  cooldown" contract are untouched.
- **Fail-closed by construction**: an unknown/null slot keeps the whole pool
  (never blocks a legal cast); the filter returns an immutable copy; the
  ambush grab still returns before the scheduler; the ring cannot admit a second
  body- or floor-owner because `compatibleWhileRing` is exactly one spell.
- **Regression test**: `tests/BossCastAvailabilityPolicyTest.java` (exactly one
  ring-compatible spell; ambush blocks all, ring does not; NONE/null keep the
  pool in order; RING keeps only the compatible spell; AMBUSH yields nothing;
  null/empty pools; immutable results; no body/floor owner ever leaks through a
  ring), registered in `RunEndRiftEventChecks.ps1` `$pureTests`. The existing
  behavioural probe `tests/test_boss_spell_availability_adapter.py` still
  compiles the real `castCurrentBossSpell` body and passes unchanged.
- **Status**: `SOURCE` + `AUTO`. Whether one overlapping projectile feels fair at
  2/5/10/11/20 real players, and the exact phase pressure curve, is a measured
  `NATIVE_BLOCKED` question (no GUI client, no running Paper in this
  environment).

### N23-P1-04 — beam attempted/accepted ledger and torso-accurate cover

- **Where**: new pure policy
  `copimine-end-event/src/me/copimine/endevent/domain/ChestBeamAdmissionPolicy.java`,
  the locked-lane damage loop and the new resolve ledger line in
  `CopiMineEndEvent.startChestBeam`.
- **Defect 1 (honesty)**: the loop did
  `alreadyHit.add(player.getUniqueId());` **before**
  `applyContactHit(...)` returned, so a protection-cancelled, absorption-drained
  or line-of-sight-refused hit still marked that player as hit. The closing
  receipt then reported a "hit" that never landed.
- **Defect 2 (cover geometry)**: `ChestBeamPolicy.beamHits(...)` intersects the
  damage capsule at the **torso** (`getHeight() * 0.5`), but solid cover was
  sampled with `hasProjectileBlockLineOfSight(chest, player.getEyeLocation())`.
  A wall or slab that shields the torso but leaves the head visible therefore
  produced a hit **through cover** — untelegraphed harm, which the spec forbids.
- **Fix**: cover is now sampled at the same body point the damage is resolved
  against (`ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE == TORSO`), and the
  per-tick verdict goes through `decide(inLane, covered, alreadyAttempted)`.
  The set is renamed `attempted` and is still populated *before* the damage call
  (so a refused event cannot re-roll every tick), but `hits[0]` and
  `renderBossContactImpact` fire **only** on an accepted
  `applyContactHit`. Because the lane test no longer short-circuits on
  "already hit", an out-of-lane or covered player is skipped by explicit
  decision rather than by a pre-emptive set insertion.
- **New ledger**: `BOSS_CHEST_BEAM_RESOLVE attempted=<n> accepted=<n>
  lanes=<n> generation=<g>`. `attempted > accepted` is the honest signature of a
  refused hit; a protected player can no longer appear as a successful one.
- **Fail-closed by construction**: out-of-lane dominates attempted, attempted
  dominates covered, and only `ATTEMPT` is admitted, so a covered body is never
  hit through terrain and an attempted body is never re-rolled. The pure policy
  is the single source of truth for the order.
- **Regression test**: `tests/ChestBeamAdmissionPolicyTest.java` (damageable
  sample is the torso; cover follows the damageable body; a head-height slab does
  *not* cover the torso while the rejected eye rule would have admitted it;
  in-lane clear body admitted; out-of-lane never an attempt; attempted never
  retried; covered never admitted; precedence; only ATTEMPT admitted), registered
  in `RunEndRiftEventChecks.ps1` `$pureTests`.
- **Preserved contracts**: fire still gates on the generation-frozen snapshot,
  still copies the frozen lanes verbatim, still never re-selects targets and still
  cancels safely without a lock —
  `tests/test_end_rift_chest_beam_core_stage_guard.py` and
  `tests/test_chest_beam_family.py` pass unchanged.
- **Status**: `SOURCE` + `AUTO`. Whether torso-sampled cover reads fairly from all
  angles, and the real protected-hit rate, is `NATIVE_BLOCKED` here (no GUI
  client, no running Paper, no signed V4 client).

### N23-P1-10 - the moving arena-wide earthquake (Trample)

- **Where**: new pure policy
  `copimine-end-event/src/me/copimine/endevent/domain/RamMovingEarthquakePolicy.java`;
  the `TRAMPLE_WARN`/`TRAMPLE` states and `trample(...)`/`drawTrampleFront(...)`
  methods in `RiftRamEncounter`, replacing the single stationary radius-12 seam.
- **Defect**: the penultimate circle was one snapshot origin with one radius-12
  expansion. The owner read it as "stationary circle spam": the Ram planted its
  feet and pulsed a flat ring from one spot, the body never visibly charged
  across the arena, the tell and the hit shared one origin so a player could not
  tell where the next edge would open, and `avoidPillar` wrote a tangent velocity
  without reorienting the body, so the Ram slid sideways near the active pillar.
- **Fix (bounded multitbeat)**: `beatsFor(livingPlayers)` picks **5 footfalls**
  normally and **6** at `LARGE_PARTY`; each footfall owns a fixed timeline
  (`BEAT_WARNING_TICKS=16` tell, `BEAT_FRONT_TICKS=24` one damage window,
  `BEAT_GAP_TICKS=8` planted gap), so the whole Trample is bounded to
  `totalTicks(6)=288` ticks. `beatIndex`/`withinBeat`/`phaseWithin` are pure, so
  the tell, the live front and the planted gap can never overlap.
- **Fix (a moving front, not one ring)**: each foot contact releases **one owned
  front** from where the foot fell; `frontRadius` grows it to
  `FRONT_MAX_RADIUS=26` (deliberately larger than the old radius-12 seam) across
  the window. `frontHits` is a swept annulus, so a grounded player is hit **once
  per front that passes them**, a player already behind the edge is safe, a fast
  tick cannot tunnel, and a player who jumps (or stands on a raised block) is
  safe. The Ram keeps its AI on during the cast and re-targets the nearest body,
  so the footfall origins genuinely advance.
- **Fix (honesty)**: `trampleBeatResolved` is reset each footfall, but a body is
  only recorded in `trampleHits` **and** given the secondary slow after
  `host.damage(...)` returns `true`. A refused (protected) application is
  resolved once for that front and never counted or slowed, so the shown front can
  never disagree with an accepted receipt (same rule as N23-P1-04).
- **Fix (gait)**: `avoidPillar` now rotates the body into the tangent with
  `RamMovingEarthquakePolicy.gaitYaw(dx,dz)` (the one yaw convention `face(...)`
  already uses) before accelerating, so the near-pillar sidestep no longer slides
  sideways. A turn-in-place of a few ticks is the spec's accepted compromise.
- **Fix (coordination)**: the pillar marksman no longer refuses to cast during
  the Trample (only a committed charge pauses it), so the quake window is a real
  coordinated dangerous window instead of a quiet gap in the pressure. The swarm
  release on each destroyed pillar is untouched, including the last pillar.
- **Bounded and preserved**: `BEAT_DAMAGE=5`, `FINAL_BEAT_DAMAGE=6.5` (heavier on
  the visibly final footfall, still under two melee hits), slowness refresh-only
  at `BEAT_SLOW_TICKS=40`/amplifier 0, so repeated fronts cannot stack. The 4+1
  pillar roster, seven impacts and journal semantics are unchanged; the 5-mage
  sphere is not resurrected.
- **Regression test**: `tests/RamMovingEarthquakePolicyTest.java` (beat band
  5..6; per-beat timeline; monotonic beat index; the front is a travelling
  annulus, not an instant radius; tunneling refused; once-per-front; jump and
  elevation clear; past-maximum and fail-closed degenerate input; the final
  footfall is heavier but bounded), registered in `RunEndRiftEventChecks.ps1`
  `$pureTests`. The rewritten `tests/test_end_rift_wave_six_seismic_stomp.py`
  drives the same rules, and the new
  `tests/test_rift_ram_trample_runtime.py` compiles the production
  `trample`/`drawTrampleFront` body against a Paper stub (travel, once per front,
  jump clears, beyond-maximum is safe, late join takes nothing, a refused hit is
  never recorded, the cast cleans up its bounded sets). The old
  `tests/test_rift_ram_stomp_runtime.py` was removed with the replaced seam.
- **Gate-caught defect (honest record)**: the first full gate run **failed** on
  `RamMovingEarthquakePolicyTest.gaitYawPointsAlongTranslation` with
  `-Z is yaw 180`. Java's `Math.atan2(-0.0D, -1.0D)` returns `-180`, not `180`,
  so the documented "yaw 180 faces -Z" convention did not hold for the straight
  backward case and the body would have faced a half-turn wrong when retreating
  along -Z. `gaitYaw` now canonicalises exactly that boundary
  (`if (yaw == -180.0D) yaw = 180.0D;`) instead of loosening the test; the
  assertion stays strict. This is why the candidate was rebuilt: the pinned
  Gradle artifact moved from `622db6b5...` (2723629 B) to
  `8cb640050942a157d79a9edf6c6a5e8b39f87cf54372da18e3fb29365c3198e4`
  (2723673 B), and `622db6b5...` is prepended to `replaceSha256`.
- **Status**: `SOURCE` + `AUTO`. Whether the stampede reads as a genuine moving
  quake at 2/5/10/11/20 real players, and whether the jump timing feels fair, is
  `NATIVE_BLOCKED` here (no GUI client, no running Paper, no signed V4 client).
  Camera shake is a client-bound concern (N23-P1-07), not faked server-side.

### N23-P1-09 — the arena inferno now fields a bounded infernal pack

- **Requirement (V5.1 master)**: while the arena inferno front is live, a bounded
  roster of authored infernal mobile threats must share the same single cast, so
  the eruption is a real pressure window rather than scenery. The vanilla
  behaviour of those mobs must not add unmeasured melee damage.
- **Runtime**: `CopiMineEndEvent.java` gained `tickArenaInferno` (per-tick sweep),
  `tickInfernoPack`, `maybeSpawnInfernoElite`, `findInfernoSpawnLocation`,
  `spawnInfernoElite` (real `EntityType` bodies: magma cube / blaze / wither
  skeleton), `tickInfernoElite`/`tickInfernoMagma|Blaze|Wither`,
  `retireInfernoElite`, `clearInfernoElites`, `renderInfernoBreach` and
  `renderInfernoScenery`; `clearArenaInferno` now also calls `clearInfernoElites`,
  and `onOwnedEntityDeath` retires a fallen elite. Each elite has zeroed vanilla
  melee (no pickup, no attack goal) and is driven only by the authored spell
  budget; magma daughters are capped.
- **Pure policies**: `domain/InfernoMobWindowPolicy.java` (window/roster/emission
  budget) and `domain/InfernoSceneryPolicy.java` (per-layer LOD: `maxLod` is the
  coarsest LOD at which a layer still draws; `drawsAt(lod)=clamp(lod)<=maxLod`,
  with SKY_VORTEX 2, FIRE_FRONT 2, EMBER_STORM_HIGH 1, EMBER_STORM_LOW 1,
  ENERGY_CLOUD 1, FIRE_ARCH 0).
- **Admission exception**: `domain/BossSummonAdmissionPolicy.java` gained a narrow
  infernal-mobile exception so the pack can coexist with the boss cast without
  weakening the general summon gate.
- **Client visuals**: three authored 64×32 summon atlases
  (`end_rift_inferno_{magma,blaze,wither}.png`, generated by
  `CopiMineClient/tools/generate_inferno_summon_atlases.py`) plus three renderer
  mixins and three catalog ids (`END_RIFT_INFERNO_MAGMA_V1/BLAZE_V1/WITHER_V1`),
  registered in `copimineclient.mixins.json`; the server emits the matching
  `clientVisualId`s. These are modal overrides of the vanilla renderer textures,
  not recoloured substitutes of another rift mob.
- **Regression tests**: `tests/InfernoMobWindowPolicyTest.java` and
  `tests/InfernoSceneryPolicyTest.java` (registered in `RunEndRiftEventChecks.ps1`
  `$pureTests`); `tests/test_inferno_runtime_cadence.py` still pins the vent
  cadence and now carries a no-op `tickInfernoPack` stub so the extracted probe
  holds the cadence contract with the pack present.
- **Candidate re-pin**: the Gradle EndEvent artifact moved from
  `8cb640050942a157d79a9edf6c6a5e8b39f87cf54372da18e3fb29365c3198e4`
  (2723673 B) to
  `7519540b017053f4f5d4b9cfd5609b693e8872304e316fbd3c6d2519d7cd3d9d`
  (2736951 B); `8cb64005...` is prepended to `replaceSha256`. The lock, migration
  pin test, `OWN_PLUGIN_INVENTORY.json/.md` and the F2 checklist were updated
  together, and the 31 plugins were reinstalled.
- **Status**: `SOURCE` + `AUTO`. Whether the infernal pack reads as authored
  bodies (silhouette/colour/sound) during a real eruption, and whether the window
  feels fair at 2/5/10/11/20 real players, is `NATIVE_BLOCKED` here (no GUI
  client, no running Paper, no signed V4 client).

### N23-P1-07 (partial): per-viewer spell VFX LOD and a bounded per-frame point budget

V6 is explicit that the interim server emitter is `O(samples * viewers * frames)`
and that a large reduction must be shown, not guessed, and that art must never
starve combat/gameplay packets. The full deliverable - one signed cast packet
that lets a single client-local instance own mesh/sprite evolution - still needs
the 26.3 client work and a native, measured run, so it stays open. This increment
lands the server-side half that is provably correct without a live server.

- **Pure policy**: `domain/SpellVfxLodPolicy.java`. A viewer resolves to
  `LOD_FULL`/`LOD_REDUCED`/`LOD_SILHOUETTE` from its distance and the living
  party size (bands tighten from 14/28 to 11/24 to 8/20 blocks as the party
  grows); a frame ships at most `framePointBudget` = `min(480, 128 + 24*(n-1))`
  points across all viewers, spent nearest first; a per-viewer count is
  `max(1, ceil(base/stride))`, so an effect is coarsened but never erased; and
  `totalPoints` mirrors the emitter exactly, so the budget cannot drift.
- **Emitter**: `BossPresentationEffects.play` and `.playPhaseTransition` now
  build the admitted viewer list nearest-first (`admittedViewers`) and walk each
  geometry at the viewer's stride under the shared budget. Sound is never
  budgeted away; only particles are.
- **Tests**: `tests/SpellVfxLodPolicyTest.java` (registered in `$pureTests`)
  pins monotonicity in distance, tightening with party size, the never-erase
  floor, a bounded monotonic budget, `shipped <= budget <= unbounded`, a >=4x
  reduction at a 20-player raid, and safe clamping of non-finite/negative input.
- **Status**: `SOURCE` + `AUTO`. The percentage reduction is a *contract*, not a
  measurement: real packet counts, bytes/s, MSPT, packet queue, CPU and client
  GPU at 2/5/10/11/20 players are `NATIVE_BLOCKED` here (no running Paper, no GUI
  client). Bridge signing/wiring and the client-local mesh ownership are still
  open (see the next subsection).

### N23-P1-07 (packet shape): the bounded cast-packet wire contract

V6 asks for "a signed small cast-start/stage/cancel packet carrying
epoch/generation, spell ID, origin/rotation or locked lane array, seed, tick
timing, stage, style and VFX LOD". This increment lands that packet's shape and a
bounded codec; wiring it into the bridge `Type` table and the 26.3 client
consumer is still open.

- **Types**: `domain/SpellCastPacket.java` (record: phase, generation, spellId,
  seed, startTick, durationTicks, stage, style, vfxLod, origin, yaw, and up to
  three frozen lanes; cosmetic-only, clamped) and `domain/SpellCastPacketCodec.java`
  (`encode`/`decode`, `MAX_BYTES=256`, `MAX_SPELL_ID_BYTES=32`, `MAX_LANES=3`,
  magic/version framing, hard rejection of bad magic/version/phase/lane-count/
  truncation).
- **Tests**: `tests/SpellCastPacketCodecTest.java` (registered in `$pureTests`)
  pins exact round-trip for START-with-3-lanes and CANCEL-with-0-lanes, the byte
  ceiling on the widest packet, field clamping, and rejection of malformed input.
- **Status**: `SOURCE` + `AUTO`. The packet is *not yet* signed by the bridge nor
  consumed by the 26.3 client; that integration and the measured reduction stay
  `NATIVE_BLOCKED`/open.
- **Candidate re-pin (CI repair)**: both this codec and `acb63a0a` (the LOD
  emitter change in `BossPresentationEffects`) alter
  `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, so run
  `45382546101` for `acb63a0a` failed at step 12 ("Build and verify pinned
  Minecraft 26.3 candidates") — the rebuilt jar no longer matched the pin
  `7519540b…` (2 736 951 B). Not a code defect; the pin invariant fired exactly
  as designed. The candidate was rebuilt twice from current source with the CI
  toolchain (Temurin 25.0.4.1) and is byte-identical across builds:
  `1cac8e0d46e538cd915b2a9131250bea19b8a094ecf3d2bf04aae1d3a415bfd2`
  (2 747 337 B, sha512 `fb03327e…`). The previous candidate `7519540b…` is
  prepended to `replaceSha256` (index 0) and `8cb64005…` shifts to index 1; the
  lock, migration pin test, `OWN_PLUGIN_INVENTORY.json/.md` and the F2 checklist
  were updated together, and `install_migration_server_plugins.py --stage-only`
  then verified "31 pinned plugin candidates". One pre-existing duplicate
  (`f12397d3…`, indices 68 and 244) was left untouched, because the documented
  convention is to never dedupe this list.

### N23-P2-15 — resource and post-processing drift

Master V5.1 (N23-P2-15) requires the signed client JAR, plugin JAR, client
bridge, resourcepack and optional Iris+BSL pipeline to agree exactly, with
verified UV/atlas provenance (event art shared from
`CopiMineClient/src/main/resources`, never a second divergent copy) and event
fog isolated from non-event shader packs. The native half (F1/F2/F5,
16:9/16:10, shader on/off, reduced-motion, low-FPS, disconnect/rejoin and two
simultaneous viewers) needs a GUI client and a live server, so it stays
`NATIVE_BLOCKED`.

- **Contract**: `tests/test_end_rift_resource_and_postprocess_drift.py` pins the
  half provable from the repository. The four authoring artefacts of a shipped
  spell cast must never drift apart: the twelve families in
  `SpellVisualGeometry`, one registered `spell/<token>` sound plus its Ogg in
  `sounds.json`, one source-derived preview sheet per family, and the single
  client-artifact hash named identically in `profile.lock.json`,
  `docs/minecraft-26.3/README.md` and `tests/test_minecraft_migration_profile.py`.
- **Provenance**: the same test asserts the client build still shares
  `CopiMineClient/src/main/resources`, that `SpellVisualRenderer` resolves exactly
  one shared texture (`end_rift_spell_arc.png`) and builds one
  `entityTranslucentEmissive(TEXTURE)` layer, and that the atlas is a real
  128x64 8-bit RGBA PNG (not a stub). It also fails if any `end_rift_spell*`
  file leaks into a `shaderpacks/` directory (the Iris menu).
- **Status**: `SOURCE` + `AUTO`. The drift contract is offline-verifiable and
  currently green (4 passed); everything requiring a real frame or a real
  disconnect/rejoin stays `NATIVE_BLOCKED`, recorded in
  `NATIVE_F2_VISUAL_CHECKLIST.md`.

### V6 section 9E Phase 4 - the Core memorial survives a full raid roster

V6 asks for `CoreMemorialService`: "persistent names and clean large-roster
viewport/interaction; must not own gameplay HP". The memorial existed only as a
single `TextDisplay` whose body was one unbounded join
(`names.sorted().reduce((l, r) -> l + ", " + r)`) under `setLineWidth(240)`, so
the body grew linearly with the roster and had to be wrapped or clipped by the
client. There was no cap, no overflow statement and no protection against a
corrupt name.

- **Policy**: `domain/CoreMemorialLayoutPolicy.java` is state-free and owns no
  HP, reward or gameplay value.
  - `sanitize` strips `§` formatting codes and control characters, collapses
    whitespace and caps a name at 16 characters, so a corrupt or hostile profile
    cannot push the viewport off screen.
  - `canonicalNames` collapses *exact* duplicates only and sorts
    case-insensitively with a natural tie-break. Two accounts that differ only by
    case both stay: merging `Ann` and `ann` would silently delete a real winner
    from the record.
  - `layout` packs three names per line, closes a line early past 48 characters,
    caps the block at five lines, and gives the last line to `…и ещё N` when the
    roster does not fit. `shown + overflow == total` always holds, **and**
    `shown` equals the number of names actually rendered. The first draft
    replaced the final line with the summary while still counting the displaced
    names as shown, which would have overstated the memorial; the test now pins
    `renderedNameCount(view) == view.shown()` (40 names -> 12 rendered + 28
    hidden, five lines).
- **Wiring**: `spawnVictoryMemorialVisuals` renders
  `String.join("\n§f", memorial.lines())` instead of one joined line, and
  `END_EVENT_VICTORY_MEMORIAL` now logs `winners=<shown>/<total>` plus
  `hidden=<overflow>` when truncated, so a large roster is diagnosable without
  dumping every name into the log. `victoryMemorialNames()` keeps its signature
  for existing callers and now returns the canonical, deduped join.
- **Tests**: `tests/CoreMemorialLayoutPolicyTest.java` (registered in
  `$pureTests`) covers sanitising, exact-only dedupe, join-order independence, a
  three-player roster, a 40-player truncation with a truthful count, the
  per-line character budget, empty and null rosters, and canonicalisation
  stability.
- **Status**: `SOURCE` + `AUTO`. How a large memorial actually reads in the real
  `TextDisplay` (font metrics, client wrapping, visibility from the Core) is
  `NATIVE_BLOCKED` here - no GUI client, no running Paper, no signed V4 client.
  No HP, reward or roster-membership semantics changed.
- **Re-pin**: this change alters the Gradle `migrationPlugins` candidate, so the
  pin moved again. Candidate `CopiMineEndEvent-26.3.jar` is now
  sha256 `958130c959cc5fd4458b6c4d3613bdd57aa656673be49d1c272862c1576bba6c`
  (2 751 628 B, sha512 `c14357d3…`), built twice from the same source with the
  CI toolchain (Temurin 25.0.4.1) and byte-identical both times. The displaced
  `1cac8e0d…` is prepended to `candidate.replaceSha256` (newest first), and the
  lock, the migration pin test (both the sequential assertions and the
  full-list pin), `OWN_PLUGIN_INVENTORY.json/.md` and the F2 checklist header
  move together. `install_migration_server_plugins.py --stage-only` verifies all
  31 candidates, and the local 26.3 runtime was reinstalled from the new lock.

### N23-P1-06 residual - three Kagune roles, three authored bodies

The V6 audit named the remaining body shares exactly: `kagune_stab ->
RIFT_LANCE` (the ordinary rift projectile), `kagune_eruption -> GEYSER` (the
rock eruption) and `small_kagune -> KAGUNE_KNOT` / `830206` (the ambush talon
body). All three are real Last-Seal mechanics, and the canonical boss spec keeps
three distinct roles - defensive guardians, the ambush grab and the small
melee-only attackers - so borrowing another mechanic's main visible object made
a Kagune cast indistinguishable from a rift bolt, a rock geyser or an ambush.

- **Geometry**: three new `BossSpellGeometryPolicy.Shape` values, each with its
  own `sample()` silhouette in all three beats:
  - `KAGUNE_STAB` (`830220`) is a base-heavy curved impaling spine that bows
    sideways and drives forward to a barbed point - the opposite taper and axis
    of the straight `RIFT_LANCE`.
  - `KAGUNE_ERUPTION` (`830221`) is four S-curved tentacles climbing out of a
    thorned floor ring and hooking inward, anchored `LOCKED_FLOOR`. The S-curve
    is what separates it from the straight rock shards and the vertical fire
    ribbons of the other hazards.
  - `KAGUNE_SMALL` (`830222`) is a compact claw-knot of three short curled
    tendrils over a low bramble, sized and shaped for the melee-only summon; it
    has no grab volume and no shield presentation.
- **Meshes**: `generate_guardian_spell_meshes.py` authors `kagune_stab.json`,
  `kagune_eruption.json` and `kagune_small.json` (11, 25 and 19 cuboids) on the
  existing standalone material atlas. `GuardianSpellMesh.NAMES`, the 26.3
  renderer table and three new `Motion` profiles (translucent-emissive, with
  charge/release phases and distinct tints) move together, and the authored set
  grew 20 -> 23.
- **Tests**: `tests/test_kagune_family.py` compiles and runs the policy off
  Bukkit. It pins each spell id -> shape -> model id -> anchor, bounds every
  silhouette to `MAX_POINTS` with real side/up/forward extent, proves the three
  bodies are distinct from each other *and* from `RIFT_LANCE`, `GEYSER`,
  `KAGUNE_KNOT` and `AMBUSH_TALONS`, compares the authored cuboid signatures so
  a renamed copy cannot pass, and re-runs the generator with `--check`.
  `tests/test_geyser_family.py` was updated: `kagune_eruption` no longer belongs
  to the geyser family, while `GEYSER`/`830217` stays as the authored rock body
  that no spell borrows. `AUTHORED_MESHES` and the generator count moved to 23.
- **Status**: `SOURCE` + `AUTO` for geometry, wiring and bounds. Whether the
  three bodies read apart in-game from four angles - and the spec's screenshot
  distinction - is `NATIVE_BLOCKED` here: no GUI client, no running Paper, no
  signed V4 client. No damage, targeting, guard-slot or summon semantics changed.

### V6 OV4.10 - the room, gate, reward and mob cores get bounded pure policies

The V4 decomposition (V6 master §OV4.10) names `DungeonRoomController`,
`PhysicalGateController`, `DungeonMobTemplateRegistry` and `DungeonRewardLedger`.
Only their data model and state machines existed: nothing bounded the doorway
block mask, proved two gates cannot share a doorway, clamped a wave to the room's
live cap, fenced a reward receipt by generation, or tabulated a mob's bounded
capabilities.

- **`PhysicalGatePolicy`** (`copimine-dungeon/.../domain`) owns the deterministic,
  Bukkit-free part of the gate contract: the exact inclusive door mask
  (fail-closed above 4096 blocks), the cross-gate overlap test plus the conflict
  listing (same world, no self-conflict), the per-style animation id
  (`gate.<token>`), the public/secret classification, and the ordered source
  snapshot the controller restores verbatim. An occupied doorway is reported so
  closure defers to `BLOCKED_BY_ENTITY` instead of entombing a player.
- **`RoomWaveSchedulePolicy`** plans a room's waves as a pure function of the
  room and the eligible roster: fixed / per-player / adaptive scaling, a hard
  live cap (128; an unset field means "use the ceiling", never "unbounded"),
  clamped per-wave counts flagged `truncated`, and cumulative wave start ticks.
  A replayed or simultaneous activation computes exactly the same plan instead
  of standing up a second controller generation.
- **`DungeonRewardLedger`** records once-only receipts keyed by room generation
  and player: a repeated/lag-duplicated claim returns `ALREADY_CLAIMED`, a claim
  fenced behind a newer generation returns `STALE_GENERATION` (an old payout is
  never resurrected), and an interrupted grant rolls back exactly once so the
  player can legitimately be paid again after recovery. It owns no gameplay HP
  or world state.
- **`DungeonMobRegistry`** is the pure capability/AI-limit table: exactly three
  vanilla-derived species survive (`newSpeciesCount() == 3`), each extension mob
  has its own distinct, stable ability set (silverfish burrow+leap, breeze wind
  projectile, bogged poison bolt), every template is vanilla-derived with a
  bounded aggro range (ceiling 48, fail-closed above it), and there is no new
  behavioural scripting runtime.
- **Tests**: `tests/test_dungeon_controllers.py` compiles and runs the shipped
  domain classes off Bukkit: exact/bounded masks, overlap pairs, six templates
  with six animation ids and exactly one secret, occupied-doorway deferral, the
  bounded/deterministic wave plan (live-cap helpers), the once-only/fenced/
  rollback-safe reward ledger, and the three-species bounded mob registry.
- **Re-pin**: each source change rebuilds `CopiMineDungeon-26.3.jar`, so the
  migration lock candidate, `OWN_PLUGIN_INVENTORY.json/.md` and the local 26.3
  runtime receipt move together; the displaced candidate digests are prepended to
  `candidate.replaceSha256` (newest first) per the documented convention, and the
  EndEvent candidate stays byte-for-byte at `b88c758f…`, so its pin is untouched.
  The final candidate is 149 876 B, sha256
  `33858dfa1a8dfeec699cfc7d3d8517f8cf4c7d0318a1bd32179644145dfeb5a2`, built
  twice with the CI toolchain and byte-identical both times; `2c1a65ec…` and
  `998f3c2e…` now head `replaceSha256`. `--stage-only` re-verifies all 31
  candidates and the local 26.3 runtime was reinstalled from the new lock. (This
  pin was later superseded by the room/gate runtime increment at the end of this
  ledger: 176 452 B, sha256
  `416e40917e08e956230e2e602dbb51233986174f7988ac4d62bf857185d73e61`.)
- **Status**: `SOURCE` + `AUTO`. Whether the six doors look distinct in-game
  (G01-G09), the six templates' real open/close timing, the live wave population,
  real reward delivery and the real Paper mob behaviour (R05) are
  `NATIVE_BLOCKED` here: no GUI client, no running Paper, no signed V4 client.
  End Rift's own seven-wave owner is untouched.

### V6 OV4.10 - the arena, victory and cinematic cores

The V4 decomposition (V6 master §OV4.10) also names `ArenaTransformationController`,
`EndRiftVictoryCoordinator` and `CinematicEffectCoordinator`. Each existed only as
game logic reached through Paper call sites: nothing bounded a transformation mask,
proved a defeat commits exactly once, or kept two casts off the same rig.

- **`ArenaTransformationPolicy`** (`copimine-end-event/.../domain`) owns the
  deterministic geometry of the approved square -> circular transformation: the two
  validated masks (square footprint, circular hall) with a hard half-extent ceiling
  (96) and a block budget (400 000) that fail closed; the exact cell/block counts in
  closed form; a containment test proving the hall lies inside the snapshot so the
  reveal is reversible (`revealIsReversible`); the exact number of cells the reveal
  actually changes (`revealCellDelta`); a monotone five-step ladder
  (`SNAPSHOT -> CIRCULAR_REVEAL -> PHASE_SIX_OPENING -> PORTALS -> REBUILD`) that never
  skips or moves backwards; and a true void rescue (`voidRescueY`) that returns a
  below-void-floor or non-finite Y to the validated safe landing above it. It touches
  no world and mutates no block.
- **`EndRiftVictoryCoordinatorPolicy`** owns the durable official victory path
  (`ACCEPTED LEGAL LETHAL HIT -> DURABLE DEFEAT -> DYING -> REWARDS -> END_OPEN ->
  MEMORIAL -> CLEANUP -> UNLOCKED`): an exactly-once defeat commit with a frozen
  official roster (two same-tick lethal hits -> one commit), a refusal of a
  diagnostic/admin kill (`NOT_OFFICIAL`, nothing recorded), a monotone stage ladder,
  once-only reward operations keyed by operation id, a once-only WorldCore End-open
  intent that is only visible after a live authoritative read-back, generation fencing
  for stale callbacks, and a versioned `encode`/`decode` that fails closed on a
  foreign generation, an unknown stage or a contradictory receipt.
- **`CinematicEffectCoordinatorPolicy`** owns the shared cinematic bookkeeping: one
  live lease per owner key (a second cast is refused instead of replacing a committed
  animation), a monotone seven-rung ladder (`SELECT -> WARN -> LOCK -> EXECUTE ->
  RECOVERY -> CLEANUP -> DONE`) with an overrun past the cast deadline returning
  `EXPIRED` so a stuck cast must clean up, a once-only outcome settle, a bounded (16)
  resource ledger with a deterministic cleanup plan ordered by kind then id, a
  once-only release that frees the owner slot, and a reduced-motion fallback that
  drops the flourish (EXECUTE/RECOVERY; three flourish layers -> zero) while never
  dropping the WARN dodge window, the LOCK or the CLEANUP. It reuses
  `SpellVfxLodPolicy` for the per-viewer cost ladder rather than inventing a second
  budget.
- **Tests**: `tests/ArenaTransformationPolicyTest.java`,
  `tests/EndRiftVictoryCoordinatorPolicyTest.java` and
  `tests/CinematicEffectCoordinatorPolicyTest.java` (all registered in `$pureTests`)
  compile and run the shipped classes off Bukkit and pin the exact geometry, the
  fail-closed bounds, both monotone ladders, the once-only commits and releases, the
  bounded ordered cleanup plan, the generation fence, durable recovery and the
  reduced-motion contract.
- **Re-pin**: the three new classes change the Gradle `migrationPlugins` candidate
  `CopiMineEndEvent-26.3.jar`, so the pin moved. It is now sha256
  `9806720acb38743760b60a6ab98419fa4f1f582d2aec015327ab236743a774c8` (2 783 295 B,
  sha512 `4c84f423…`), built twice from the same source with the CI toolchain
  (Temurin 25.0.4.1) and byte-identical both times; the Dungeon candidate stayed
  byte-for-byte at `33858dfa…`, so its pin is untouched. The displaced `b88c758f…` is
  prepended to `candidate.replaceSha256` (newest first), and the lock, the migration
  pin test (sequential assertions, the index block and the full-list pin),
  `OWN_PLUGIN_INVENTORY.json/.md` and the F2 checklist header move together.
  `install_migration_server_plugins.py --stage-only` re-verifies all 31 candidates and
  the local 26.3 runtime was reinstalled from the new lock.
- **Status**: `SOURCE` + `AUTO`. Whether the circular hall reads correctly in the real
  world (block-by-block visual and collision), the two-second `Dying2` presentation,
  the real reward delivery, the real WorldCore End open and the authored cinematic
  timeline on the signed 26.3 client are `NATIVE_BLOCKED` here: no GUI client, no
  running Paper, no signed V4 client. No HP, damage, reward or world state changed.

### V6 OV4.10 - the Core memorial's durable winner ledger

The previous section's `CoreMemorialLayoutPolicy` fixed how a roster is *drawn*, but
nothing fixed what the roster *is*. The memorial body was still built from the live
display names at victory time with no durable record, so a renamed account rewrote
history, two different accounts sharing a nickname were indistinguishable, and there
was no way to tell a genuine repeat victory from a replayed write.

- **Policy**: `domain/CoreMemorialLedgerPolicy.java` is the durable record behind that
  viewport. It is Bukkit-free and owns no health, damage, reward or End-access state:
  `EndRiftVictoryCoordinatorPolicy` owns the victory commit, and this class only
  records the outcome it was given.
  - `Entry` is keyed by `attemptId:uuid`, not by display name. The UUID is parsed
    strictly (32 hex digits, optional standard dashes) and canonicalised to lowercase,
    because a lenient parse could map two different accounts onto one key and merge
    two winners. The nickname is sanitised through `CoreMemorialLayoutPolicy.sanitize`
    and frozen at the moment of victory; the victory timestamp must be non-negative.
  - `Ledger.record` is exactly-once per attempt and participant. A replayed write, a
    double death or a restart that re-reads the same roster returns `DUPLICATE` with
    the *same* ledger instance, so nothing is appended and the stored nickname and
    time are never refreshed. The same account winning a second attempt, and a second
    account in the same attempt, both record normally.
  - At `MAX_ENTRIES` (4096) the ledger returns `FULL` rather than evicting an older
    winner: a memorial that silently dropped the first people who beat the encounter
    would be worse than one that stops accepting new lines.
  - `sorted` is total and independent of insertion order (victory time, then attempt
    id, then UUID), and `encode` writes a versioned header plus one tab-separated row
    per entry, so the same set of victories produces byte-identical files on two
    servers that accepted them in a different order.
  - `decode` is fail-closed on meaning. A missing payload is `EMPTY` (the first
    victory legitimately starts from nothing), but a header that is absent or names a
    different version is `REJECTED` and contributes no entries, because a foreign
    format may mean something else by the same columns. Once the header matches, every
    malformed row, every duplicate key and every row beyond the cap is counted in
    `Loaded.rejectedRows` and left out, so the operator sees the exact loss instead of
    discovering a missing name months later.
- **Wiring**: the real `CoreMemorialService` storage, the `TextDisplay` body and the
  per-attempt freeze still come from the Paper plugin, which remains unchanged in this
  batch; this batch lands the pure record/format/uniqueness contract it must satisfy.
- **Tests**: `tests/CoreMemorialLedgerPolicyTest.java` (registered in `$pureTests`)
  covers strict UUID canonicalisation, entry validation, exactly-once recording, the
  frozen nickname and timestamp, refusal to evict at the cap, insertion-order
  independence of both order and bytes, an encode/decode round-trip, rejection of a
  foreign and of a future format version, malformed/duplicate/over-cap row accounting,
  and the bounded display reused from `CoreMemorialLayoutPolicy`.
- **Re-pin**: this change alters the Gradle `migrationPlugins` candidate, so the
  pin moved again. Candidate `CopiMineEndEvent-26.3.jar` is now sha256
  `b577d1f6dcb867e54254e90d213bdc521b1d7a5fcbc6a623f65deb88255e160e`
  (2 795 026 B, sha512 `6f1b618f…`), built twice from the same source with the
  CI toolchain (Temurin 25.0.4.1) and byte-identical both times
  (`ENDEVENT_DETERMINISM=OK`, `ENDEVENT_CHANGED=OK`). The Dungeon candidate stayed
  byte-for-byte at `33858dfa…`, so its pin is untouched
  (`DUNGEON_PIN_UNCHANGED=OK`). The displaced `9806720a…` is prepended to
  `candidate.replaceSha256` (newest first), and the lock, the migration pin test
  (sequential assertions, the index block and the full-list pin),
  `OWN_PLUGIN_INVENTORY.json/.md` and the F2 checklist header move together.
  `install_migration_server_plugins.py --stage-only` re-verifies all 31 candidates
  and the local 26.3 runtime was reinstalled from the new lock.
- **Status**: `SOURCE` + `AUTO`. The `javac` + `java` probe passes
  (`CoreMemorialLedgerPolicyTest OK`). Whether the durable file survives a real
  restart on the production host, and whether the memorial reads correctly in the real
  `TextDisplay` from the Core, are `NATIVE_BLOCKED` here: no GUI client, no running
  Paper, no signed V4 client. No HP, reward or roster-membership semantics changed.

### V6 OV4.10 - the dungeon room/gate runtime (Bukkit-free controllers + opt-in adapter)

The dungeon shipped only as pure, Bukkit-free domain cores plus the editor,
command and store layers; the actual room/gate runtime named by V6 OV4.10
(`DungeonRoomController`, `PhysicalGateController`) did not exist anywhere, so
nothing actually sealed a doorway, spawned a bounded wave, committed a clear or
paid a reward. This increment lands that runtime, split so every rule stays
testable off a live server.

- **`DungeonEncounterPlanPolicy`** (`copimine-dungeon/.../domain`) turns the
  bounded `RoomWaveSchedulePolicy` schedule into concrete, deterministic spawn
  assignments against the room's validated anchors: only anchors of the same room
  and world are eligible (stable id order, foreign rooms ignored), the same room,
  anchors and roster always produce the same plan, and a room with no waves or no
  usable anchor is `viable == false` and must not start (fail closed, no invented
  spawn position).
- **`PhysicalGateTransaction`** (`domain`) is the real block-mask transaction the
  gate controller needs: `open` snapshots every doorway cell and writes air,
  `close` restores the exact snapshot and refuses to entomb an occupant
  (`BLOCKED_BY_ENTITY`), a missing journal is `RECOVERY_REQUIRED` instead of a
  guess, and an unloaded chunk is `CHUNK_UNLOADED` instead of a blind write. The
  block storage is an injected `GateWorld`, so the whole transaction is unit
  tested without a running server.
- **`DungeonRoomLifecycle`** (`domain`) is the authorised room state machine
  (`PUBLISHED_IDLE -> ARMING -> SEALING -> FIGHTING -> CLEAR_COMMITTED -> OPENING
  -> REWARD_READY -> RESETTING -> PUBLISHED_IDLE`): activation is only possible
  from idle and bumps a generation, so a simultaneous or duplicated trigger
  cannot stand up a second controller; waves spawn on the plan's cumulative
  schedule; the exit opens only after a genuine clear (all waves spawned and zero
  live enemies); the reward is requested exactly once per generation; and an
  abort or failed seal opens the exit so nobody is trapped. Every side effect is
  an injected `Ports`.
- **`DungeonRuntime`** (`copimine-dungeon/.../runtime`) is the only dungeon class
  that touches the live world; `BukkitGateWorld` is the only one that reads/writes
  blocks. It registers a `PlayerMoveEvent` activation trigger, drives
  `DungeonRoomLifecycle.advance` from a one-second tick task, materialises spawns
  from the plan and reuses the pure `DungeonRewardLedger` for once-only reward
  receipts. It is disabled by default via a new `dungeons.runtime.enabled: false`
  config key, starts only enabled rooms in loaded worlds with anchors, and logs a
  diagnostic snapshot; item delivery stays a configured follow-up and is never
  fabricated.
- **Tests**: `tests/test_dungeon_controllers.py` now also compiles and runs
  `DungeonEncounterPlanProbe` (deterministic, proportional, anchor-bounded,
  fail-closed plan), `DungeonGateTransactionProbe` (snapshot/open, occupied-door
  deferral, exact restore, restart recovery, unloaded-chunk refusal) and
  `DungeonLifecycleProbe` (generation fence, scheduled waves, real clear, exit
  open, once-only reward, safe abort/failed-seal).
- **Re-pin**: the new sources change the Gradle `migrationPlugins` candidate
  `CopiMineDungeon-26.3.jar`. It is now sha256
  `416e40917e08e956230e2e602dbb51233986174f7988ac4d62bf857185d73e61`
  (176 452 B, sha512 `a2d0916a...`), built twice from the same source with the CI
  toolchain (Temurin 25.0.4.1) and byte-identical both times
  (`DUNGEON_DETERMINISM=True`). The displaced `33858dfa...` is prepended to
  `candidate.replaceSha256` (newest first). The EndEvent candidate stayed
  byte-for-byte at `b577d1f6...`, so its pin is untouched. The lock,
  `OWN_PLUGIN_INVENTORY.json/.md` and the local 26.3 runtime receipt move
  together; `install_migration_server_plugins.py --stage-only` re-verified all 29
  runtime candidates and the runtime was reinstalled from the new lock (receipt
  now records `416e4091...` in both `installed` and `pluginInventory`).
- **Status**: `SOURCE` + `AUTO`. Real Paper behaviour - a door that actually
  blocks and restores, the six gate styles' timing, the live wave population,
  real reward delivery and the trigger volume - is `NATIVE_BLOCKED` here: no GUI
  client, no running Paper, no signed V4 client. The runtime is off by default and
  no world block, player or reward state was changed by this batch.

### V5.1 - authored animated 3D spell VFX (client geometry + renderer) and the server cue stream

Задание V5.1 требует, чтобы заклы босса, Стража и миниибоссов читались как
авторские анимированные 3D-эффекты, а не как набор частиц. До этого инкремента
клиент умел рисовать только одиночный луч (`END_WORLD_BEAM`), поэтому сложные
силуэты (сжимающаяся сфера, вращающаяся печать, копьё кагуне, веер Стража,
вена скользкого следа) не существовали нигде. Этот инкремент добавляет
оригинальную авторскую геометрию, рендерер и компактный серверный поток
подсказок, который её запускает.

- **`SpellVisualGeometry`** (`CopiMineClient/.../client`) - чистая, без зависимости
  от GL, геометрия всех десяти реперных заклов: `crown`, `fangline`, `circle`,
  `separation`, `sweep`, `sphere`, `sculk`, `gravity`, `fan`, `heavy`. Каждый закл
  собирается из параметризованных «полос» (дуги, кольца, рёбра, вихри, сферы),
  детерминированно (`splitUnit` даёт стабильное разбиение без дрожания кадра),
  с жёсткими потолками вершин/полос и LOD-уровнями. Плюс парсер компактного ключа
  `spellCue`/`cueSeed` (`v51-<spell-token>-<seed>`) для сетевой реконструкции.
- **`SpellVisualRenderer`** - мировой рендерер: строит квады в локальном кадре
  закла (x = направление каста/луча, z = боковое, y = вверх) и кладёт их на
  единственную серверную пару «якорь → досягаемость»; эмиссивная текстура
  `assets/copimineclient/textures/entity/end_rift_spell_arc.png`.
- **Клиентская проводка**: `RitualSpellPresentationPolicy` получает
  `PassKind.SPELL` и `worldPass`-маршрутизацию; `EndEventWorldVfxManager` получает
  слой `SPELL` и `draw`-ветки. Клиентский `activeWorldVfxInstances` ограничен 64,
  версий инстанса 256, время жизни луча 2000 мс, ширина зажата 0.02-0.45; повторная
  отправка сохраняет `startedAtMillis`, пока инстанс жив, поэтому анимация не
  перезапускается.
- **Серверный поток подсказок** (`CopiMineEndEvent.java`): перегрузка
  `sendWorldBeamPacket(...durationMillis)`, `broadcastSpellVisualCue` /
  `clearSpellVisualCue`, константа `SPELL_VISUAL_DURATION_MILLIS = 1400`. Подсказки
  эмитятся для `crown` (канал + телеграф заряда + очистка), `fangline`, `circle`,
  `separation` (Архимаг), `sweep`, `sphere` (телеграф удара, с seed-параметром),
  `gravity` (+очистка), `sculk` (направленная вена) и веера/тяжёлого
  удара/разъединения Стража (с троттлингом `%10`). Seed стабилен на актёра/поколение
  (`cueSeedFor(uuid)` для короны/печати/линии; `cueSeedFor(warden)` для
  свипа/сферы/скользкого; `cueSeedFor(guardian)` для веера/тяжёлого;
  `crushingGravityGeneration` для гравитации) - это ограничивает число живых
  инстансов и не создаёт новый тип пакета.
- **Клиентская вена следа скользкого** - одиночная направленная жила от первой к
  последней реальной точке `traceSegments`; точный коридор по-прежнему рисуется
  частицами и не заявлен как точный путь.
- **Тесты**: `SpellVisualGeometryTest` (14 поведенческих проверок: детерминизм,
  границы, потолки, парсер ключа, LOD). Клиентская сборка `gradle test build`
  зелёная: всего 301 тест, `FAIL=0 ERR=0 SKIP=0`. Сервер компилируется и
  упаковывается чисто (`BUILD SUCCESSFUL`).
- **Re-pin**: изменение серверных исходников меняет Gradle-кандидат
  `migrationPlugins` `CopiMineEndEvent-26.3.jar`. Он теперь sha256
  `7a6a7d570d396f3b664684ffd8eaed8d86a135caacdebe9e229e393411ea9cc5`
  (2 796 480 B, sha512 `1ef8f05231...`), собран дважды из одного исходника на
  инструментальной цепочке CI (Temurin 25.0.4.1) и оба раза байт-в-байт
  идентичен. Смещённый `b577d1f6...` добавлен в начало
  `candidate.replaceSha256` (сначала новое), локальный 26.3-рантайм переустановлен
  из нового лока (`--stage-only` перепроверил 29 кандидатов; установленный jar и
  запись в реестре равны новому отпечатку), а `OWN_PLUGIN_INVENTORY.json/.md`,
  миграционный пин-тест и шапка F2-чек-листа обновлены вместе. Кандидат Дана
  (Dungeon) остался байт-в-байт на `416e4091...`; его пин не тронут.
- **Re-pin (N23-P1-05 + N23-P1-08, 2026-11-24)**: серверные правки авторских
  каст-куев (полноразмерный грудной луч N23-P1-05 и столб гейзера N23-P1-08)
  снова сместили Gradle-кандидат `migrationPlugins` `CopiMineEndEvent-26.3.jar`.
  Он теперь sha256
  `22f511f347bfb3e55b71fd2713be5db71b080a19c8ab6f7f169b2dd0cad30cc5`
  (2 796 627 B, sha512 `b1a1b5750c...`), собран дважды из одного исходника на
  инструментальной цепочке CI (Temurin 25.0.4.1 + Gradle 9.8.1) и оба раза
  байт-в-байт идентичен. Смещённый `7a6a7d57...` добавлен в начало
  `candidate.replaceSha256` (сначала новое); `OWN_PLUGIN_INVENTORY.json/.md`,
  миграционный пин-тест и шапка F2-чек-листа обновлены вместе. Кандидат Данжа
  (Dungeon) не тронут: правки подземелья остаются незакоммиченным инкрементом
  владельца и в этот коммит не входят.
- **Причина красных CI и её фикс (2026-11-24)**: `45474114967`/`45481911282`
  падали из-за **устаревшего** пина кандидата `CopiMineEndEvent`: чистый
  `gradle --offline migrationPlugins` из коммита даёт 2 796 627 B / `22f511f3…`,
  а лок пиновал 2 796 480 B / `7a6a7d57…`. `78b31ef4` перепиновал кандидат
  (наблюдение CI run `45485135934`: шаг «Build and verify pinned Minecraft 26.3
  candidates» теперь success, падение переехало на pytest). `533518f5` довёл
  **полный** литерал `candidate['replaceSha256']==[…]` (был `b577d1f6…`), после
  чего обе точки совпадают с локом. Локальный 26.3-рантайм переустановлен из
  нового лока (receipt = `22f511f3…`); пин dungeon и незакоммиченный инкремент
  подземелья не тронуты.
- **Статус**: `SOURCE` + `AUTO`. Как новые силуэты заклов выглядят на живом кадре
  (силуэт+цвет+звук, отсутствие призрачных лучей после фазы/смерти/рестарта) и как
  они совмещаются с реальными попаданиями - `NATIVE_BLOCKED`: в этой среде нет
  GUI-клиента, запущенного Paper и подписанного V4-клиента. Ни один живой мир,
  игрок или ассет не изменён этим инкрементом. Не заявлены как сделанные:
  подпись моста N23-P1-07 и передача клиентской геометрии; переиспользуемые
  `SpellVisualDefinition/Runtime`; прод-релиз.

### V5.1 - порт авторских заклов в поставляемый 26.3-клиент

При проверке выяснилось важное: поставляемый клиент (тот, что закреплён в
`tools/minecraft-26.3/profile.lock.json` и который обязан использовать владелец
для F2) собирается из проекта `tools/minecraft-26.3/client`, а НЕ из
`CopiMineClient` (последний - независимая сборка 1.21.1, версия `0.1.1`, её
собирает отдельный legacy-шаг гейта и стейджит в modpack). То есть авторская
геометрия, добавленная только в `CopiMineClient`, не попала бы в клиент, который
тестирует владелец. Этот инкремент переносит эффект в реальный 26.3-клиент.

- **Перенесено дословно**: `SpellVisualGeometry` (чистая геометрия без
  зависимости от GL) и `SpellVisualGeometryTest` (14 тестов) скопированы в
  `tools/minecraft-26.3/client/src/main/java/me/copimine/client/` и
  `.../src/test/java/me/copimine/client/`.
- **Адаптировано под API 26.3**: `SpellVisualRenderer` в 26.3-дереве рисует те же
  ленточные квады через отложенный рендерер - `PoseStack` + `RenderTypes.entityTranslucentEmissive`
  + `Identifier.fromNamespaceAndPath` + `VertexConsumer.addVertex(Pose, ...).setColor(int).setUv(...).setOverlay(...).setLight(...).setNormal(...)`.
  Математика та же (локальный кадр x=направление, z=бок, y=вверх; `Vec3.scale/cross/normalize`).
- **Проводка**: `RitualSpellPresentationPolicy` получил `PassKind.SPELL` и маршрут
  `v51-<token>-<seed>` в `worldPass`, а `drawWorldPasses` - отдельный проход
  `SPELL`; `EndEventWorldVfxManager.render` получил слой `SPELL` и его отрисовку.
  Ключи `v51-` не подпадают под другие префиксы, а неизвестный ключ по-прежнему
  падает в `RIBBON` - регрессии для существующих лучей нет.
- **Текстура**: `end_rift_spell_arc.png` лежит в общих ресурсах
  `CopiMineClient/src/main/resources` и подключается 26.3-клиентом через
  `resources.srcDir`, поэтому она уже была в поставляемом jar (именно она сдвинула
  пин), а тест `authoredTextureIsShipped` проверяет её наличие на classpath.
- **Сборка и воспроизводимость**: `gradle --no-daemon --offline -p tools/minecraft-26.3/client test jar`
  на инструментальной цепочке CI (Temurin 25.0.4.1, Gradle 9.8.1): 408 тестов,
  `FAIL=0 ERR=0 SKIP=0`, включая `SpellVisualGeometryTest` (16). Чистая пересборка
  (`clean` + `test jar`) дала sha512
  `2fb6870c84f1bcbebb97c91f643b25a1a1ec928009848b3a05a9e40c0c9a92b30f3d305c4c701c2b6d9b34589d85371b2a32f8a3d1677cda5317860531a61484`
  (30 425 923 B) и повторилась байт-в-байт двумя прогонами (пин N23-P1-08: авторское
  тело гейзера Фазы 3).
- **Пресеты качества §9.3 (25 ноября)**: клиент получил сохраняемый ключ
  `spell_visual_quality` (`full`/`balanced`/`reduced`), применяемый при старте и
  `/copimineclient reload`, команду `/copimineclient spell-quality` и строку
  `spellQuality=` в `/copimineclient status`.
- **Снижение движения §9A.04/§9A.05 (25 ноября)**: новый чистый
  `SpellVisualMotionPolicy` + `SpellVisualRenderer.setReducedMotion`, применяемый
  при старте и `/copimineclient reload`, команда
  `/copimineclient reduced-motion on|off`, строка `reducedMotion=` в
  `/copimineclient status`. Гасит пульсацию бегущей волны мировых мешей, силуэт и
  опасную зону не меняет (проверено `reducedMotionDoesNotChangeSampleDensity`).
  Одиннадцатый перепин клиента →
  `45c839f3f0c64fcc4248a36a07d0acb3559375721b6ee6962d3cb0ed6872db74b3685c0728f21ca25ae42d795dd89ad71c00489f5a876bed94e6079d204098c6`
  (30 428 023 B), подтверждён двумя полными `--rerun-tasks test jar` прогонами;
  обновлены `profile.lock.json`, `docs/minecraft-26.3/README.md`,
  `tests/test_minecraft_migration_profile.py`, `SpellVisualMotionPolicyTest` и
  новые контракты в `tests/test_end_rift_spell_presentation.py`.
- **Горячий тон и объём геройских штрихов §9A.01/§9A.02 (25 ноября)**: старый
  рендер умножал один цвет на яркость и рисовал каждую ленту одной плоскостью.
  Новый чистый `SpellVisualToneMap` даёт шкалу «глубокая тень → базовый цвет →
  раскалённый белый» (`COLD_SCALE=0.42`, `HOT_START=0.66`, `HOT_MIX=0.62`), а
  флаг `Style.volumetric()` рисует `ARM/JAW/WEDGE/TRACE/FIELD` парой скрещённых
  лент (`CROSS_VOLUME_INSET=0.86`, строго в пределах авторской полуширины).
  Двенадцатый перепин клиента (`d381d1b2…`, 30 429 320 B) подтвердил этот слой,
  но был **перекрыт** тринадцатым перепином, когда в тот же jar добавились
  авторские звуки (§ ниже);
  `SpellVisualToneMapTest` (6/6) + контракт в `SpellVisualGeometryTest` (20/20).
- **Оригинальный звук каждого заклинания §9A.03(F)/§9A.05 (25 ноября)**: раньше у
  заклов не было ни одного собственного аудиоассета. Добавлены **12
  оригинальных** Ogg Vorbis (`assets/copimineclient/sounds/spell/<token>.ogg`),
  генерируемых детерминированным скриптом
  `scripts/minecraft/GenerateEndRiftSpellSounds.py` (numpy + soundfile, без
  сторонних сэмплов), зарегистрированы как `spell.<token>` в `sounds.json`.
  Новый `SpellVisualSoundRuntime` играет позиционно **один голос на каст**
  (ключ id+метка старта; обновление телеграфа не звучит, новый каст звучит
  снова; `reset()`/`clear()` гасят; `MAX_SOUNDS_PER_FRAME=4`; питч 0.94–1.06 по
  seed), вызывается из `render()` по тому же подписанному cue. Тринадцатый
  перепин клиента →
  `63850b145a04b9f55f2344218b8789fc0625d812f87bbb09e916de2b9c4a7a4f8d327355b64fe483230c1f5679c6454649f6f45b787df2a04b5912c86002d859`
  (30 679 295 B), подтверждён двумя полными `--rerun-tasks test jar` прогонами;
  `SpellVisualSoundRuntimeTest` (5/5) + контракт
  `test_every_authored_spell_ships_its_own_original_sound`, всего по клиенту
  **427 тестов, 0 падений**.
- **Авторская глубина мешей**: корона получила вертикальные клинки-гребни на кончиках
  лучей и анимированную радиальную вспышку (пульсирует по часам каста), круг -
  контр-вращающуюся внутреннюю печать с радиальными лучами, эхо - пятикольцевую
  оболочку вместо трёх, веер Стража - три вложенные полосы. Все добавления
  детерминированы по (seed, elapsed) и остаются строго внутри уже проверенных
  границ: 5 ARM/20 JAW короны, 9 JAW круга, `maxX==4.0`/`maxZ==2.5` Стража-удара,
  `maxX==4.8` веера, ≥7 оболочек эха в радиусе r+0.35 с сжатием на заряде, поле
  гравитации по горизонтали ≤10.5.
- **Финальный анимационный проход**: каждая семья теперь непрерывно
  перестраивается по часам каста, а не только по seed. Корона: волнообразные лучи,
  «дышащие» клыки, два контр-вращающихся ореола, по одному орбитальному шеврону на
  луч, восходящие спиральные искры и двойная треугольная печать. Круг: пульсирующие
  кольца давления, восемь орбитальных искр, 12 рунных насечек и четыре вертикальных
  клинка. Эхо: семь широтных оболочек, шесть меридианов, внутренняя решётка,
  орбитальные узлы и осевой столб. Гравитация: девять закрученных вихревых колонн,
  сжимающаяся спираль, парящий мусор и четыре кольца захвата. Веер Стража:
  бегущая наружу волна по конусу, вертикальные стены на кромках и искры внутри
  сектора. Разделение: четыре расширяющихся кольца и искровой фронт. Итог по
  бюджетам остаётся прежним: ≤96 штрихов, ≤1024 сегментов на каст, все точки
  конечны и внутри `maxReach`.
- **Рендер**: `SpellVisualRenderer` теперь семплит авторскую текстуру дуги поперёк
  ширины ленты (яркое ядро, мягкие края) и гонит по длине бегущую волну яркости,
  поэтому меши читаются как текущая энергия, а не как статичная наклейка.
- **N23-P1-05 — собственный полноразмерный луч сундука**: у луча теперь своя
  семейная геометрия `BEAM` (`chordSheets` заполняют настоящий круговой срез трубы
  ярусами хорд, две контр-вращающиеся спирали, поперечные кольца, раструб-плита
  дула с концентрическими кольцами, посевные ветви, расширяющаяся детонация на
  конце), а `maxReach(BEAM)=27`. Рендерер переведён на полный ортонормальный кадр
  (forward/right/up) с потолком ширины 1.72 ровно под радиус капсулы, поэтому
  диагональный и почти вертикальный луч не схлопывается в линию. `renderChestBeamStage`
  эмитит `v51-beam-<seed>` на дорожку через существующий транспорт, а
  `clearChestBeamCueLanes()` идемпотентно снимает след на завершении/отмене/потере
  лока. Проверки: `SpellVisualGeometryTest` (15, байт-идентичен в двух деревьях) и
  Python-пробник презентации; живой кадр — `NATIVE_BLOCKED`.
- **Re-pin клиента**: `tools/minecraft-26.3/profile.lock.json` (`clientArtifact`
  size/sha512), `docs/minecraft-26.3/README.md`,
  `tests/test_minecraft_migration_profile.py` и шапка
  `NATIVE_F2_VISUAL_CHECKLIST.md` обновлены вместе. Списка `replaceSha256` у этого
  пина нет: клиент перезаписывается целиком.
- **Статус**: `SOURCE` + `AUTO`. Как силуэты заклов выглядят на живом кадре
  (силуэт+цвет+звук, отсутствие призрачных лучей после фазы/смерти/рестарта),
  совмещение с реальными попаданиями и пара V4-подписанного клиента/сервера -
  `NATIVE_BLOCKED`: в этой среде нет GUI-клиента, запущенного Paper и подписанного
  V4-клиента. Ни один живой мир, игрок или ассет не изменён.

### V5.1 — концы строк упакованного `sounds.json` (красный CI) и превью-листы мешей (§9L)

- **Симптом**: CI-прогон `45503984072` (коммит `b7dc28aa`) упал **только** на шаге
  «Build and verify pinned Minecraft 26.3 candidates»:
  `CopiMineClient build output differs from its pinned artifact digest and size:
  pinned 30679295 / 63850b14…; built 30679300 B / 048b6fad…`.
- **Диагноз**: сравнение пер-записных отпечатков
  (`FullName:Length:CompressedLength`) показало у двух jar **773 записи** в одном
  порядке и расхождение ровно в одной — `assets/copimineclient/sounds.json`, на CI
  **1887 B** против **1825 B** у пина (**+62 байта**). В новом `sounds.json` **62
  строки**; в репозитории (blob `HEAD:`) он хранится с LF
  (1825 B / 62 LF / 0 CRLF), но не был описан в `.gitattributes`, а раннер
  GitHub Actions выкачивает репозиторий с `core.autocrlf=true`. Каждая из 62 строк
  получила `CRLF` — ровно +62 байта в упакованном ресурсе и в jar.
- **Фикс**: в `.gitattributes` добавлено правило
  `tools/minecraft-26.3/client/src/main/resources/assets/copimineclient/sounds.json text eol=lf`
  с комментарием про эти +62 байта. Это тот же приём, которым репозиторий уже
  закрывал «+400 байт»/«+110 байт» CRLF-дрейфа у других пиновых ресурсов.
  Правка **не меняет байты локального артефакта**, поэтому пин `63850b14…` /
  30679295 остаётся действующим и перепин не нужен. Полный обход текстовых
  ресурсов дерева 26.3 (`check-attr eol` + посчёт LF) подтвердил, что этот файл был
  единственным реально неописанным упакованным исходником; остальные уже покрыты
  `eol=lf`/`-text`.
- **Превью-листы мешей (§9L)**: `scripts/minecraft/RenderSpellVisualPreviews.py`
  компилирует настоящий `SpellVisualGeometry.java` (и `SpellVisualEnvelope.java`)
  пробником и раскладывает штрихи **семи авторских смысловых ключей каста**
  §9A.03(C) (`0/240/540/860/1180/1580/2000` мс — `dormant → anticipation →
  energized → release → peak → dissipate → completed`, seed `4242`, tier `FULL`,
  с собственным re-time и нагревом материала каждого семейства) в 12
  изометрических SVG `docs/end-rift/2026-11-23/spell_previews/<token>.svg`.
  Каждый лист в самом XML подписан как «превью из исходника, не скриншот», а
  нативный F2 — `BLOCKED`. `--check` детерминированно перерисовывает во временную
  папку и падает при расхождении; контракт
  `tests/test_end_rift_spell_visual_previews.py` (2 теста) гоняет `--check` и
  требует ровно один лист на токен, ненулевое содержимое и все семь ключей по
  порядку. Только docs/скрипты/тесты — основные исходники и ресурсы клиента не
  менялись, пин прежний.
- **Статус**: `SOURCE` + `AUTO` (локально: `--check` = «12 authored preview sheets
  match the shipped geometry», `exit 0`; превью+презентация = 8 passed). Живой кадр
  и нативное прослушивание — по-прежнему `NATIVE_BLOCKED`.

### V5.1 — авторская дуга каста на каждое семейство (§9A.03C)

- **Пробел**: §9A.03(C) требует 4–8 смысловых анимационных ключей и авторских
  кривых (ease-out ударных фронтов, короткий импульс с затуханием у камня, быстрое
  раскрытие/медленная посадка у челюстей). Меши были авторскими по силуэту и
  палитре, но жили на непрерывных синусоидах: у каста не было замаха, выброса и
  успокоения — закл одинаково «дышал» всё время жизни инстанса.
- **`SpellVisualEnvelope`** (новый, чистый JDK-Java в 26.3-дереве): 7 ключей
  `DORMANT → COMPLETED` в окне 2000 мс (= `MAX_BEAM_LIFETIME_MILLIS`), авторский
  `tempoMillis` — кусочно-smoothstep, **строго монотонный** re-time часов каста с
  продолжением 1:1 после окна, и `heat` — кривая нагрева материала (тусклый первый
  кадр → пик на выбросе → остывание).
- **Инвариант силуэта**: `heat` масштабирует только `Stroke.brightness`; точки и
  полуширины от него не зависят. `SpellVisualGeometry.build` = `authored(spell,
  tempoMillis(...))` + нагрев, где пакетно-приватный `authored(...)` — shape-only
  билд; тест сравнивает их поэлементно.
- **Свой ритм**: identity-часы оставлены ровно трём семействам, у которых уже есть
  внутренний фазовый арк (гравитационное поле, луч сундука, гейзер); остальные
  девять получили собственные кривые (Пробуждение — медленный замах и щелчок,
  Охота — почти мгновенный разрыв, челюсти — быстрое раскрытие/медленная посадка,
  и т.д.).
- **Проверки**: `SpellVisualEnvelopeTest` (7 тестов) — 7 монотонных ключей на
  семейство, отсутствие обратного хода часов на 0..5000 мс, 1:1 после окна,
  ≥7 различимых ритмов, кривая нагрева/остывания, порядок смысловых битов,
  «нагрев не двигает ни одну точку/полуширину», стабильность арифметики.
  `RenderSpellVisualPreviews.py` компилирует и `SpellVisualEnvelope.java`, поэтому
  12 SVG перерисованы из настоящего исходника; Python-контракты —
  `test_end_rift_spell_presentation.py` + `test_end_rift_spell_visual_previews.py`
  = 8 passed.
- **Пин**: правка основных исходников клиента ⇒ jar пересобран дважды
  (`--rerun-tasks test jar`, обе сборки совпали байт-в-байт) и пин
  `profile.lock.json` / README / pin-теста обновлён (см. отчёт §1.18).
- **Статус**: `SOURCE` + `AUTO`. CI на финальном SHA `41500fc3`, run `45511745562` —
  все джобы `success`: шаг 12 воспроизвёл новый пин **30 684 452 / `4e70f379…`** из
  исходников, шаг 17 «Run Python contract tests» — success, нативные гейты —
  `skipped` (честно, не passed). Как дуга читается на живом кадре и в нативном
  миксе — `NATIVE_BLOCKED` (нет GUI-клиента и запущенного Paper в этой среде).
