# End Rift Combat and Visual Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Исправить боевые, AI, objective и визуальные дефекты End Rift локально, не меняя текущую карту и production.

**Architecture:** Чистые правила вынести в domain policy-классы и покрыть unit-тестами; Bukkit/Paper adapter оставить ответственным за события, entities, displays, particles, sounds и scheduler. Safe-зоны и порталы будут event-owned и очищаться через существующий registry/generation lifecycle. Клиентские asset/model изменения остаются UUID/visual-id scoped и не затрагивают vanilla.

**Tech Stack:** Java 21, Paper/Purpur Bukkit API, Fabric client mod, JUnit-style Java tests, Python contract tests, PowerShell local live tests, resource-pack JSON/PNG, Git.

**Spec:** `docs/superpowers/specs/2026-09-02-end-rift-combat-visual-fixes.md`

## Global Constraints

- Только `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` и его local-runtime.
- Production server, production world и production databases не подключать.
- Текущую локальную карту не удалять, не регенерировать и не заменять.
- Не менять глобальные vanilla-текстуры.
- Сохранять virtual HP, стадии, награды, persistence/recovery, cleanup и client protocol.
- Safe-зоны используют event-owned displays/particles, без временной записи блоков в карту.
- Максимум активных `RIFT_OBELISKS` — 4, запуск — один раз за бой.

### Task 1: Damage regression contracts

**Files:**
- Modify: `tests/BossDamagePolicyTest.java`
- Modify: `tests/test_end_event_boss_virtual_health_contract.py`
- Modify: `tests/RunEndRiftBossDamageLive.ps1`
- Modify: `tests/RunEndRiftBossMultiPlayerDamageLive.ps1`
- Create: `tests/test_end_event_damage_cancellation_contract.py`

**Interfaces:**
- Consume existing `BossDamagePolicy`, `BossVirtualHealthPolicy` and live harness commands.
- Produce assertions for pre-cancelled events, multiple final-damage inputs, exhausted multiplier, threshold transitions and ordinary mob damage.

- [ ] **Step 1: Write the failing tests.** Add pure cases for `10 + 14 + 8` damage from 5000 to 4968, melee/projectile combinations, five hits in one logical tick, one `EXHAUSTED` multiplier per hit, blocked cast damage, threshold crossing and a pre-cancelled event that must not apply virtual damage. Add contract assertions that the runtime has an early cancellation guard and logs the cancellation reason without bypassing AuthEffects/AdminPlus.
- [ ] **Step 2: Run the focused tests and record RED.** Run `mvn -q -Dtest=BossDamagePolicyTest test` or the repository's existing Java test command discovered from the module build files, then `python tests/test_end_event_boss_virtual_health_contract.py` and `python tests/test_end_event_damage_cancellation_contract.py`. The new cancellation contract must fail against the current handler because `onBossDamage` currently continues after `event.isCancelled()`.
- [ ] **Step 3: Add the live assertions.** Make the two-player harness compare authoritative boss virtual HP before and after two independent attacks, and the five-player harness compare the exact expected sum. Add a mob target probe that records a cancelled/accepted damage pair and `noDamageTicks`.
- [ ] **Step 4: Re-run to confirm the tests still express the real failure.** Confirm RED is caused by the missing cancellation guard or an actual runtime trace, not by a test parser error.

### Task 2: Minimal damage pipeline repair

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` near `onBossDamage`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/BossDamagePolicy.java` only if the new regression exposes a pure-rule defect
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/BossVirtualHealthPolicy.java` only if the new multi-hit contract exposes a pure-rule defect

**Interfaces:**
- Consume the failing tests from Task 1.
- Produce one authoritative damage transaction per accepted Bukkit event and no virtual-health mutation for already-cancelled events.

- [ ] **Step 1: Implement the smallest guard.** In `onBossDamage`, after identifying the owned boss and before any virtual-health mutation, return when the Bukkit event is already cancelled; log a bounded diagnostic. Keep Rift Fireball filtering, cast gates and the existing virtual-health projection unchanged.
- [ ] **Step 2: Fix only proven event-mob causes.** Use the live diagnostic to distinguish `noDamageTicks`, authentication cancellation, AdminPlus check mode, anti-cheat cancellation and a plugin handler. Change only the proven End Rift path; do not un-cancel protected events and do not replace a root cause with a blanket event exemption.
- [ ] **Step 3: Run the focused unit, contract and live damage tests.** Expected result: exact virtual HP arithmetic, no double EXHAUSTED multiplier, ordinary player damage remains accepted after a Rift Fireball, and event-mob attacks are accepted when the test player is authenticated and outside check mode.
- [ ] **Step 4: Refactor only after green.** Keep the transaction code small and preserve existing cleanup/persistence behavior.

### Task 3: Boss target hysteresis and damage balance

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossTargetPolicy.java`
- Create: `tests/BossTargetPolicyTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` in `rotateBossTarget`/`selectBossSpellTarget`
- Modify: `copimine-end-event/config.yml`
- Modify: `tests/test_end_event_boss_ai_contract.py`

**Interfaces:**
- `BossTargetPolicy.chooseTarget(List<UUID> candidates, UUID current, List<UUID> recent, long nowMillis, long lockUntilMillis, long cursor)` returns a deterministic `TargetDecision(UUID target, long newLockUntilMillis)`.
- `WaveDamagePolicy.reduce(double configuredDamage, double reduction)` returns a clamped non-negative value; use existing balance policy if one already owns this rule.

- [ ] **Step 1: Write failing target tests.** Cover current target remaining valid during the lock, invalid/offline target replacement, fairness after the lock, two-player anti-ping-pong behavior, and a deterministic five-player choice. Add a balance assertion that wave/mini-boss attack damage is reduced by 4 without changing the boss attack setting.
- [ ] **Step 2: Run the tests and confirm RED.** Run the focused Java test and the AI contract. The target-lock test must fail because current `chooseFairTarget` rotates when alternatives exist.
- [ ] **Step 3: Implement the policy and integrate it.** Use a configurable lock window longer than the 2.5-second tactic refresh; keep tactic movement/orbit around the same target. Retarget only on invalid target, leaving the arena, or lock expiry. Apply the `-4` balance to wave/mini-boss outgoing damage at the existing stats configuration point and clamp the result.
- [ ] **Step 4: Verify green and inspect logs.** Run unit/contract tests plus the local AI live probe; assert no rapid alternation and no target outside the arena.

### Task 4: Safe-zone duration and visual objects

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/ZoneVisualPolicy.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` in `renderWaveOneArenaZones`, `updateRiftStormObjective`, cleanup paths
- Modify: `copimine-end-event/config.yml`
- Modify: `tests/ZoneVisualPolicyTest.java`
- Modify: `tests/test_end_event_zone_visual_contract.py`
- Create: `tests/SafeZoneVisualRuntimeTest.java`

**Interfaces:**
- `ZoneVisualPolicy.safeZoneDurationMillis(EventConfig config)` returns the validated duration, defaulting to 60 seconds.
- Event-owned safe-zone visual handles are registered in the existing task/entity registry and cleared by generation cleanup.

- [ ] **Step 1: Write failing tests.** Assert a 60-second default/configured duration, safe-zone state classification, emerald display material/model, green beam emission, bounded object count, and cleanup on objective completion, reset and generation change.
- [ ] **Step 2: Run RED.** Run zone Java/contract tests; duration and emerald/beam assertions must fail because the runtime hardcodes 30 seconds and emits particles only.
- [ ] **Step 3: Implement the display layer.** Add event-owned `BlockDisplay` floor plates using emerald visual material/model and green vertical beam particles/segments. Anchor them to the actual combat floor and safe-zone positions, never write world blocks, and register every display for cleanup.
- [ ] **Step 4: Replace the hardcoded timer.** Read the validated config duration in `updateRiftStormObjective`, keep actionbar countdown consistent, and restore/clear all zone state at the same deadline.
- [ ] **Step 5: Verify green and local visual state.** Run unit/contract tests and a local wave-one live probe; count displays before/after cleanup and confirm the current map block snapshots are unchanged.

### Task 5: Portals, spell visuals and obelisk visibility

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` portal/spell/obelisk adapter paths
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/SpellVisualPolicy.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/RiftObeliskCastPolicy.java`
- Modify: `tests/SpellVisualPolicyTest.java`
- Modify: `tests/RiftObeliskCastPolicyTest.java`
- Modify: `tests/RunEndRiftObeliskLive.ps1`
- Modify: `tests/RunEndRiftObeliskLoadLive.ps1`
- Modify: `tests/RunEndRiftBossVisualLive.ps1`
- Modify: `tests/test_end_event_portal_visual_contract.py`
- Modify: `tests/test_end_event_obelisk_contract.py`

**Interfaces:**
- Existing `SpellVisualPolicy` returns bounded telegraph/release/impact particle budgets and cues.
- Existing obelisk state keeps generation, one-shot cast, HP and cleanup ownership.

- [ ] **Step 1: Add failing diagnostics/tests.** Assert that every configured boss spell produces TELEGRAPH, RELEASE and IMPACT evidence for the current audience, that portal model IDs resolve to existing model/texture files, and that a forced `DISTORTION` cast produces visible obelisk state with exactly one cast.
- [ ] **Step 1a: Add portal objective regression cases.** Extend the pure portal policy tests with `occupied -> unoccupied` progress decay, asserting that progress remains positive immediately after exit, decreases monotonically at each update, reaches zero after the configured decay window, and never becomes negative. Add a runtime contract that wave-3 objective logic does not apply mob knockback/velocity changes.
- [ ] **Step 2: Run RED/live reproduction.** Run the focused contracts and the local visual/obelisk probes against the current server. Capture `cmend status`, client visual-binding logs, entity counts and event log markers; distinguish absent state from client-applied-pack absence.
- [ ] **Step 3: Repair the proven path.** Keep portal `ItemDisplay` custom model data event-scoped; correct model transform/origin or client binding only if the live evidence identifies it. Replace the wave-3 portal palette/asset with a black-purple rift visual without touching vanilla assets. Remove only portal-objective knockback from wave-3 mob setup; preserve unrelated combat knockback. Add a validated decay-rate config to `PortalCapturePolicy` and render the decreasing percentage. Ensure spell effects use visible, bounded rings/trails/impact bursts and reach the same audience as the boss cue. Make the obelisk spawn telegraph and fallback server particles visible while preserving `DISTORTION` and one-shot guards.
- [ ] **Step 4: Verify green.** Run portal, spell matrix, obelisk, cleanup and load tests; assert ordinary vanilla fireball/portal behavior is unchanged.

### Task 6: Ten-second control lifecycle

**Files:**
- Modify: `copimine-end-event/config.yml`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java` only if live client evidence shows stale expiry
- Modify: `tests/test_end_event_boss_regressions_contract.py`
- Modify: `tests/EndEventClientStateTest.java`

**Interfaces:**
- Server control packet duration and client `EndEventClientState.isReverseActive(nowMillis)` use the same 10-second duration and instance ID.

- [ ] **Step 1: Write failing tests.** Replace the old 20-second expectation with exact 10-second server/client expiry, matching-instance stop, generation cleanup and no stale control after reconnect/state reset.
- [ ] **Step 2: Run RED.** Run the server contract and client state tests; the server config assertion must fail while it is 20 seconds.
- [ ] **Step 3: Implement the config/lifecycle correction.** Set the default to 10 seconds and keep server deadline, packet duration, scheduler expiry and cleanup aligned. Modify client state only if the live trace proves packet expiry is not honored.
- [ ] **Step 4: Verify green.** Run server contract, client unit and local spell/control live test.

### Task 7: Clean boss and event-model assets

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModelRenderer.java`
- Modify: `CopiMineClient/tools/generate_end_rift_texture_atlases.py` only if the generator remains the source of truth
- Create/replace: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_*.png`
- Create/replace: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_*.png`
- Modify: `tests/test_end_event_client_assets_contract.py` and the existing texture/model contracts

**Interfaces:**
- Preserve existing phase/animation IDs, UUID-scoped renderer selection and vanilla fallback.
- Keep model UV dimensions and generated asset dimensions synchronized in one change.

- [ ] **Step 1: Add asset contract expectations.** Require clean opaque/transparent material regions, left/right symmetry where intended, no generator noise/panel-guide lines, all referenced phase/mob files, and unchanged vanilla renderer fallbacks.
- [ ] **Step 2: Run RED against the current atlas.** The contract must detect the current random grain/panel lines or the old silhouette dimensions.
- [ ] **Step 3: Create the new visual source.** Use the approved image-generation workflow for high-resolution raster artwork, then integrate deterministic crops/alpha and update the generator/model UVs together. Make the boss taller and slimmer, preserve readable silhouette at gameplay distance, and apply the same clean material language to event enderman, spider and skeleton variants.
- [ ] **Step 4: Verify assets independently.** Validate PNG dimensions/alpha, model JSON, client catalog entries and vanilla fallback tests before server packaging.
- [ ] **Step 5: Build the client and resource pack.** Record ZIP SHA1/SHA256 and confirm the pack contains portal, obelisk, fireball and all redesigned entity assets.

### Task 8: Full local verification and Git checkpoint

**Files:**
- Modify only if required: `tests/StartEndRiftLocal.ps1`, `tests/StartEndRiftLocalUserSession.ps1`, local start helper/batch configuration
- Create: `docs/superpowers/evidence/2026-09-02-end-rift-combat-visual-fixes.md`

**Interfaces:**
- Use the existing local start/stop scripts and current map/runtime data.
- Evidence report records commands, exit codes, counts, hashes, logs and known limitations.

- [ ] **Step 1: Run all focused unit/contract tests.** Include damage, AI, zones, spells, portals, controls, obelisks, cleanup and client assets; capture exact pass/fail counts.
- [ ] **Step 2: Build server plugin, client mod and resource pack.** Fail the checkpoint on any compiler, pack, JSON or asset error.
- [ ] **Step 3: Start only local runtime.** Preserve the current map and database; verify server/resource-pack/site listener addresses are local/test-only before connecting any test client.
- [ ] **Step 4: Run live scenarios.** Run two-player and five-player damage, wave-one safe zone, wave-three portal, boss spell matrix, obelisk, control expiry, AI target lock, cleanup and bounded performance probes. Record logs and screenshots only after behavior is proven.
- [ ] **Step 5: Restart the local server from the existing local launcher.** Verify map block snapshots, whitelist/config persistence, plugin list, resource-pack HTTP SHA1 and readiness after restart.
- [ ] **Step 6: Review the complete diff and commit.** Run `git diff --check`, inspect changed files, commit the verified checkpoint on `codex/end-rift-event`, then push only this branch to `https://github.com/IliaZav/copimine`.
- [ ] **Step 7: Produce the evidence report.** Include modified files, assets, exact commands, fresh outputs, live limitations and explicit confirmation that production was not contacted.
