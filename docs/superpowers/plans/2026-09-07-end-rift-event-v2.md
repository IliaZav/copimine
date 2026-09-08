# End Rift Event V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy End Rift Event official flow with a server-authoritative, persistent, visually readable End Rift Event V2 that can be completed by two players and scales safely to twenty.

**Architecture:** Retain the existing Paper plugin as a Bukkit adapter, but move V2 calculations and immutable decisions into new `domain` policies. Add small runtime controllers for event state, trace collection, waves, chambers, boss and tentacles; each controller owns only its task registrations and event-owned runtime entities. Extend the existing client bridge into a versioned V3 visual stream and keep all client code rendering-only.

**Tech Stack:** Java 21, Paper/Purpur API, Fabric client, JUnit 5, Python pytest contracts, PowerShell local Paper/RCON harness, resource-pack JSON/PNG assets.

**Spec:** `docs/superpowers/specs/2026-09-07-end-rift-event-v2.md`

## Global Constraints

- Keep all work on `codex/end-rift-event`; preserve the current local world, whitelist and local database. Never access production services.
- Use test-first development. Every new domain function and every defect fix requires a demonstrably failing regression test before production code.
- Diagnose the missing-damage defect through Combat Trace before making damage-handler changes. Do not use `noDamageTicks = 0`, arbitrary timing or a safety-check bypass.
- Boss combat uses actual entity health and a bounded boss-bar projection only. Legacy virtual HP never participates in official V2 damage, thresholds, persistence, command output or recovery.
- No official V2 transition may route through legacy `FINAL_*`, `COUNTDOWN` or `VICTORY` stages. Snapshot migration reads them only to recover safely.
- Client effects must be bounded; continuous beams/ribbons are rendered by the client, not as dense ordinary-particle lines.
- Use existing WorldCore mutation journal and Artifacts authenticity/owner-binding APIs. Reward intent is persisted before physical delivery.
- Do not claim visual or Paper behavior from compilation alone. Record screenshots/logs/manual verification separately.

---

## File map

- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` remains the listener/command adapter while delegating V2 behavior.
- `copimine-end-event/src/me/copimine/endevent/EventSnapshot.java` and `EventStateStore.java` own schema migration and durable V2 attempt/reward state.
- `copimine-end-event/src/me/copimine/endevent/domain/*Policy.java` contains pure wave, roster, boss, chamber, reward and combat decisions.
- `copimine-end-event/src/me/copimine/endevent/runtime/*` will contain Bukkit-only lifecycle, trace, wave/chamber, boss/tentacle and visual-runtime controllers.
- `CopiMineClient/src/main/java/me/copimine/client/*` owns protocol decoding, state and renderer integration; it does not decide damage, targets or rewards.
- `CopiMineClient/src/main/resources/assets/copimineclient/end_rift/*` and `resourcepacks/src/assets/copimine/*` hold V2 models, 128/256-pixel textures, animation descriptors and sound registrations.
- `tests/*` contains pure Java tests, state-store tests, Python source contracts and local Paper/RCON integration tests.

## Task 1: Establish a V2 state and persistence boundary

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventSnapshot.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventStateStore.java`
- Modify: `tests/EndEventStateMachineTest.java`, `tests/EventStateStoreTest.java`, `tests/RunEndRiftEventChecks.ps1`
- Create: `copimine-end-event/src/me/copimine/endevent/V2SnapshotMigrationPolicy.java`
- Create: `tests/V2SnapshotMigrationPolicyTest.java`

**Interfaces:**
- Produces `V2SnapshotMigrationPolicy.migrate(EventSnapshot legacy, int targetSchema): EventSnapshot`.
- Produces canonical `EventPhase` transitions and `EndEventStateMachine.recoveryPhase(EventPhase)`.

- [ ] **Step 1: Write failing state and migration tests**

```java
check(EndEventStateMachine.allowed(EventPhase.READY_FOR_PLAYERS, EventPhase.START_RITUAL));
check(!EndEventStateMachine.allowed(EventPhase.BOSS_ACTIVE, EventPhase.FINAL_DRAIN));
EventSnapshot migrated = V2SnapshotMigrationPolicy.migrate(legacyFinalDrain, 3);
check(migrated.eventPhase() == EventPhase.READY_FOR_PLAYERS);
check(migrated.resourceRequirements().equals(legacyFinalDrain.resourceRequirements()));
```

- [ ] **Step 2: Run the focused tests and verify the legacy-flow assertion fails**

Run: `javac ... tests/EndEventStateMachineTest.java tests/V2SnapshotMigrationPolicyTest.java; java ... EndEventStateMachineTest; java ... V2SnapshotMigrationPolicyTest`

Expected: failure because legacy `FINAL_DRAIN` is still an official transition and migration is absent.

- [ ] **Step 3: Implement the canonical enum, transitions and lossless migration**

```java
public static EventPhase recoverV2(EventPhase phase) {
    return switch (phase) {
        case WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2, WAVE_3,
             INTERMISSION_3, WAVE_4, INTERMISSION_4, WAVE_5, INTERMISSION_5,
             WAVE_6, PRE_BOSS_COOLDOWN, BOSS_CINEMATIC, BOSS_ACTIVE,
             BOSS_FINISH, VICTORY_PROCESSING -> EventPhase.READY_FOR_PLAYERS;
        default -> phase;
    };
}
```

- [ ] **Step 4: Run focused and complete state-store tests**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftEventChecks.ps1`

Expected: state persistence tests pass and no old flow is reachable by a V2 transition.

- [ ] **Step 5: Commit**

```powershell
git add copimine-end-event/src/me/copimine/endevent tests docs/superpowers
git commit -m "feat(end-event): migrate lifecycle to v2 phases"
```

## Task 2: Add Combat Trace and reproduce missing damage

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/runtime/CombatTraceService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/CombatTraceRecord.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/config.yml`
- Create: `tests/CombatTraceRecordTest.java`, `tests/RunEndRiftCombatTraceLive.ps1`
- Modify: `tests/RunEndRiftEventChecks.ps1`

**Interfaces:**
- Produces `CombatTraceService.record(CombatTraceRecord)` and `drainAttempt(UUID eventId)`.
- Each record captures tick, time, attacker/victim UUID, cause, raw/final damage, cancellation before/after, invulnerable/no-damage ticks/max, last damage, health before/next tick, event phase, cast/shield state and MSPT.

- [ ] **Step 1: Write a failing serialization/redaction test and a live trace assertion**

```java
CombatTraceRecord record = CombatTraceRecord.from("PLAYER", 7.0D, 5.0D, false, true, 12L);
check(record.finalDamage() == 5.0D);
check(record.cancelledAfter());
check(record.toLogLine().contains("final=5.0"));
```

- [ ] **Step 2: Run the test and confirm the missing type/log field is the failure**

Run: `javac ... tests/CombatTraceRecordTest.java; java ... CombatTraceRecordTest`

- [ ] **Step 3: Implement a ring-buffered service and listener boundary capture**

```java
trace.record(CombatTraceRecord.capture(server.getCurrentTick(), event, victim, phase,
        castState, mobShielded, server.getAverageTickTime()));
server.getScheduler().runTask(plugin, () -> trace.recordNextTick(victim));
```

- [ ] **Step 4: Run the trace probe against several event mob types and the test boss**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftCombatTraceLive.ps1`

Expected: one trace chain from packet/damage event through final cancellation and next-tick health for each attempt.

- [ ] **Step 5: Record the reproducible root-cause finding in `docs/testing/end-rift-combat-trace.md` and commit**

```powershell
git add copimine-end-event tests docs/testing
git commit -m "test(end-event): add combat trace diagnostics"
```

## Task 3: Repair the confirmed missing-damage root cause

**Files:**
- Modify: the single listener/controller identified by Task 2 trace evidence
- Modify: `tests/RunEndRiftCombatTraceLive.ps1`
- Create or modify: `tests/CombatDamageRegressionTest.java`
- Modify: `tests/test_end_event_damage_cancellation_contract.py`

**Interfaces:**
- Consumes `CombatTraceRecord` evidence from Task 2.
- Produces a documented damage invariant: each valid player hit is processed once; each rejected hit reports its actual rejecting state.

- [ ] **Step 1: Write a failing regression that reproduces the exact rejected valid hit**

```java
DamageDecision decision = damageController.decide(validPlayerHit, activeWaveMob);
check(decision.accepted());
check(decision.reason().equals("ACCEPTED"));
```

- [ ] **Step 2: Verify the test fails for the trace-proven reason**

Run: `javac ... tests/CombatDamageRegressionTest.java; java ... CombatDamageRegressionTest`

- [ ] **Step 3: Apply the smallest root-cause repair and preserve all unrelated cancellation paths**

```java
if (!ownedByCurrentAttempt(victim) || !validCombatParticipant(attacker)) return REJECT_NOT_OWNED;
return shieldState.blocks(hit) ? REJECT_SHIELD : ACCEPTED;
```

- [ ] **Step 4: Run trace, mob-combat and boss-combat live probes**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftCombatTraceLive.ps1; powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftMobCombatLive.ps1`

Expected: valid hits change health exactly once; protected hits retain a traceable rejection reason.

- [ ] **Step 5: Commit**

```powershell
git add copimine-end-event tests docs/testing
git commit -m "fix(end-event): restore traced player damage delivery"
```

## Task 4: Build the V2 roster, transition-rune and wipe controllers

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/TransitionRunePolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/runtime/TransitionRuneController.java`
- Modify: `CopiMineEndEvent.java`, `EventSnapshot.java`, `EventStateStore.java`, `HazardMutationJournal.java`
- Create: `tests/TransitionRunePolicyTest.java`, `tests/RunEndRiftAttemptWipeLive.ps1`

**Interfaces:**
- `TransitionRunePolicy.evaluate(Set<UUID> roster, Map<UUID, RuneOccupancy> occupancy): RuneCheck`.
- `AttemptLifecycleController.performAttemptWipe(String cause)` is the only wipe entry point.

- [ ] **Step 1: Write failing tests for unique occupancy, leave/death reset, five-second hold and idempotent wipe**
- [ ] **Step 2: Run tests and observe absent V2 rune/lifecycle behavior**
- [ ] **Step 3: Implement owner-specific pads, outer-arena placement after Wave 4, and a single cleanup path**
- [ ] **Step 4: Run unit tests, Paper wipe/restart probe and journal restore probe**
- [ ] **Step 5: Commit**

## Task 5: Implement V2 Waves 1–3 with real visual protocol V3

**Files:**
- Create: `domain/WaveOneCarrierPolicy.java`, `domain/WaveTwoMarkPolicy.java`, `domain/WaveThreePortalPolicy.java`
- Create: `runtime/WaveOneController.java`, `runtime/WaveTwoController.java`, `runtime/WaveThreeController.java`, `runtime/V3VisualEmitter.java`
- Modify: `CopiMineEndEvent.java`, `EventConfig.java`, `config.yml`, `ClientBridgeProtocol.java`, `EndEventPacket.java`, `EndEventClientState.java`
- Create: `tests/WaveOneCarrierPolicyTest.java`, `tests/WaveTwoMarkPolicyTest.java`, `tests/WaveThreePortalPolicyTest.java`, `tests/RunEndRiftWaves123Live.ps1`
- Modify: client packet tests and portal visual contracts

**Interfaces:**
- `V3VisualEmitter.emit(EndRiftVisualEvent event)` sends typed, bounded, generation-tagged visual events.
- Wave 3 emits exactly three sequential portal instances and no more than two pushers per defending pack.

- [ ] **Step 1: Write failing pure tests for three deliveries, three mark cycles/rotation and three sequential portals**
- [ ] **Step 2: Verify the tests fail against legacy generic wave behavior**
- [ ] **Step 3: Implement the controllers, spawn budget and client protocol events**
- [ ] **Step 4: Run wave tests and a Paper two-player Wave 3 screenshot probe; verify depth, transforms and no Z-fighting**
- [ ] **Step 5: Commit**

## Task 6: Implement V2 Wave 4 safe zones

**Files:**
- Create: `domain/SafeZonePolicy.java`, `runtime/WaveFourSafeZoneController.java`
- Modify: `HazardMutationJournal.java`, `CopiMineEndEvent.java`, config/client V3 event types
- Create: `tests/SafeZonePolicyTest.java`, `tests/RunEndRiftWaveFourLive.ps1`

**Interfaces:**
- `SafeZonePolicy.seriesForPlayers(int)` returns `min(5, ceil(players/2))`, then minus one/minimum one, with 3×3/2×2/1×1 geometry and capacities 2.

- [ ] **Step 1: Write failing geometry/capacity/effect tests**
- [ ] **Step 2: Run tests and confirm legacy zone behavior does not satisfy V2**
- [ ] **Step 3: Implement emerald floor, barriers, fog timing, exact damage/effects and mutation journaling**
- [ ] **Step 4: Run Paper test for complete restoration after normal completion and wipe**
- [ ] **Step 5: Commit**

## Task 7: Implement V2 Wave 5 rotating rings

**Files:**
- Create: `domain/RingPressurePolicy.java`, `runtime/WaveFiveRingController.java`
- Modify: `CopiMineEndEvent.java`, config/client V3 renderer
- Create: `tests/RingPressurePolicyTest.java`, `tests/RunEndRiftWaveFiveLive.ps1`

- [ ] **Step 1: Write failing tests for 72-display cap, duo revival window, prisoner 3 HP/50 seconds and one-full-pressure guard**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Implement bounded ring state and guarded objective hand-off**
- [ ] **Step 4: Run 2/10-player Paper scenarios and cleanup check**
- [ ] **Step 5: Commit**

## Task 8: Implement V2 Wave 6 chamber isolation

**Files:**
- Create: `domain/ChamberScalingPolicy.java`, `domain/ChamberIsolationPolicy.java`
- Create: `runtime/WaveSixChamberController.java`
- Modify: `CopiMineEndEvent.java`, `HazardMutationJournal.java`
- Create: `tests/ChamberScalingPolicyTest.java`, `tests/ChamberIsolationPolicyTest.java`, `tests/RunEndRiftWaveSixLive.ps1`

- [ ] **Step 1: Write failing tests for 2/3/4 chamber mapping and room-local player counts**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Implement isolated entity sets and rules for target, path, projectile, AoE and teleport**
- [ ] **Step 4: Test all four room archetypes at 2, 3, 10 and 20 players**
- [ ] **Step 5: Commit**

## Task 9: Migrate boss damage to real entity health

**Files:**
- Create: `domain/V2BossHealthScalingPolicy.java`, `domain/V2BossStagePolicy.java`, `runtime/V2BossController.java`
- Modify: `CopiMineEndEvent.java`, `EventSnapshot.java`, `EventStateStore.java`, `EndRiftBossBarHud.java`
- Modify: `tests/BossDamagePolicyTest.java`, `tests/BossHealthScalingPolicyTest.java`, `tests/test_end_event_boss_virtual_health_contract.py`, `tests/RunEndRiftBossMultiPlayerDamageLive.ps1`
- Create: `tests/V2BossHealthScalingPolicyTest.java`, `tests/RunEndRiftBossRealHealthLive.ps1`

**Interfaces:**
- `V2BossHealthScalingPolicy.maxHealthFor(int players)` follows the supplied 2–20 table.
- `V2BossController.onDamage(EntityDamageEvent)` accepts normal player melee/projectile hits exactly once and projects boss-bar fraction from actual health.

- [ ] **Step 1: Write failing tests that reject legacy PDC virtual HP and assert real 5,000/6,000/13,500/20,000 entity HP**
- [ ] **Step 2: Run tests and confirm current virtual-health path fails them**
- [ ] **Step 3: Implement real HP, one-way V2 stages, boss-bar-without-numbers and snapshot migration**
- [ ] **Step 4: Run live multi-player test with 2 and 5 independent bots and compare entity HP delta to summed final damage**
- [ ] **Step 5: Commit**

## Task 10: Implement boss V2 stage controllers, one-shot obelisks and Last Seal

**Files:**
- Create: `domain/V2BossSpellPolicy.java`, `domain/TentacleScalingPolicy.java`, `runtime/BossSpellController.java`, `runtime/TentacleController.java`
- Modify: existing obelisk policies/controllers, `CopiMineEndEvent.java`, config and tests
- Create: tests for stage spell availability, obelisk one-shot, permanent/temp tentacle caps and 15-second vulnerability.

- [ ] **Step 1: Write failing stage/one-shot/tentacle-cap tests**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Implement Awakening/Hunt/Rift/Overload/Rage/Last Seal and remove legacy official casts**
- [ ] **Step 4: Run spell matrix, boss reset and boss cleanup integration tests**
- [ ] **Step 5: Commit**

## Task 11: Integrate the supplied boss assets and build the tentacle asset

**Files:**
- Create: client tentacle model/animation descriptors and 128/256px textures under `CopiMineClient/src/main/resources/assets/copimineclient/end_rift/`
- Modify: `RiftGuardianModel.java`, `RiftGuardianModelRenderer.java`, `EndEventTextureCatalog.java`, client bridge state/renderer
- Create: `EndRiftTentacleModel.java`, `EndRiftTentacleRenderer.java`, tests and asset validators
- Add: high-resolution rift portal, beam/ribbon and boss visual assets under client/resource-pack assets

**Interfaces:**
- `TentacleVisualState` carries pose, animation id, start tick, `grabSocket` world transform and generation.
- `TentacleAnimationMarker` enumerates `CONTACT`, `HOLD_LOCK`, `THROW_RELEASE`, `RECOVERY_START`, `HIDE_BELOW_FLOOR`.

- [ ] **Step 1: Write failing asset/marker and renderer-state tests**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Convert/adapter-integrate supplied boss model; create segmented tentacle with base, seg_01..seg_04, tip, claws and grab socket**
- [ ] **Step 4: Add idle/emerge/grab/hold/throw/miss/hurt/death/retract/spawn/shield animations with supplied timing markers**
- [ ] **Step 5: Build the client and perform visual checks for player without armor, with armor, adjacent tentacles and throw release; save evidence**
- [ ] **Step 6: Commit**

## Task 12: Implement client V3 ribbons, portal geometry and bounded boss effects

**Files:**
- Create: client V3 ribbon/portal/telegraph renderer classes and shaders/models
- Modify: `EndEventPacket.java`, `EndEventClientState.java`, `ClientBridgeProtocol.java`, `EndEventTextureCatalog.java`
- Create: `tests/EndRiftV3VisualStateTest.java`, source contracts and manual visual script

- [ ] **Step 1: Write failing protocol and display-budget tests**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Implement generation-checked continuous ribbon and three-dimensional portal geometry with fallback that leaves gameplay intact**
- [ ] **Step 4: Verify missing asset fallback and vanilla entities outside the event remain unchanged**
- [ ] **Step 5: Commit**

## Task 13: Implement durable personal rewards and Rift Core Shard

**Files:**
- Create: `domain/V2RewardPolicy.java`, `runtime/V2RewardController.java`, `runtime/RiftCoreShardController.java`
- Modify: snapshot/store, `CopiMineEndEvent.java`, Artifacts integration boundary and item/resource-pack configs
- Create: reward policy/persistence tests and restart/retry Paper test

- [ ] **Step 1: Write failing tests for persisted pre-roll, independent 30% cloak decision, guaranteed shard and no reroll after restart**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Use typed Artifacts API to persist intent, deliver authentic bound items, then persist complete state**
- [ ] **Step 4: Implement shard abilities and exact yellow title/lore without capability listing**
- [ ] **Step 5: Run restart/retry and authenticity tests**
- [ ] **Step 6: Commit**

## Task 14: Migrate/remove unreachable legacy official mechanics

**Files:**
- Modify: all legacy flow callers identified by `rg "FINAL_DRAIN|FINAL_RITUAL|FINAL_WAVE|BossVirtualHealth"`
- Modify: docs, Python contracts, Java tests and command output
- Create: `tests/test_end_event_v2_legacy_unreachable_contract.py`

- [ ] **Step 1: Write failing source/runtime contract that a V2 attempt cannot enter legacy flow or virtual HP handler**
- [ ] **Step 2: Verify red state**
- [ ] **Step 3: Delete dead code or isolate it solely in explicit snapshot migration compatibility**
- [ ] **Step 4: Run complete End Rift static suite**
- [ ] **Step 5: Commit**

## Task 15: Build local integration and release evidence

**Files:**
- Modify: `tests/RunEndRiftEventChecks.ps1`, `tests/StartEndRiftLocal.ps1`, current local-only launch script
- Create: `tests/RunEndRiftV2TwoPlayerLive.ps1`, `tests/RunEndRiftV2TenPlayerLive.ps1`, `tests/RunEndRiftV2TwentyPlayerStress.ps1`, `tests/RunEndRiftV2RestartCleanupLive.ps1`, `docs/testing/end-rift-v2-release-evidence.md`

- [ ] **Step 1: Write failing scripts that require V2 phase, real HP, artifact hashes and cleanup assertions**
- [ ] **Step 2: Verify each fails before its matching capability exists**
- [ ] **Step 3: Run full builds, resource-pack validation and local server scenarios**
- [ ] **Step 4: Capture 2-player completion, 10-player scaling, 20-player MSPT/TPS/heap smoke, restart/wipe cleanup and visual screenshots**
- [ ] **Step 5: Commit evidence and final implementation, then push only this branch**

## Plan self-review

- V2 lifecycle, migration, waves, chambers, actual boss health, one-shot obelisks, tentacles, client rendering, rewards, cleanup/recovery, player counts and performance each have a dedicated task.
- Combat Trace precedes any missing-damage repair; Task 3 is explicitly contingent on Task 2 evidence.
- Every behavioral task begins with a failing test and ends with targeted plus integrated verification.
- No official path retains virtual health or legacy final-flow behavior; migration compatibility is tested separately.
