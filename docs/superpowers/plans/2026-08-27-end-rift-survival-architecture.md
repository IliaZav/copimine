# End Rift Survival Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the local End Rift event a five-wave, restart-safe Survival encounter with bounded combat, visible objectives, reliable client visuals, fair loot, and evidence-backed performance behavior, without touching production.

**Architecture:** Keep Paper authoritative for state, damage, objectives, cleanup, and rewards. Move deterministic rules into small pure Java policies (`WaveObjectivePolicy`, `PortalCapturePolicy`, `TowerDefensePolicy`, `HazardPlanner`, `BossStagePolicy`, and `BossMovementPolicy`), then let one generation-owned controller per live mechanic orchestrate Bukkit entities and blocks. Extend the existing client bridge with optional phase/model packets while preserving the UUID-scoped vanilla fallback. Treat temporary block changes and physical rewards as journaled, idempotent operations.

**Tech Stack:** Java 21, Paper/Purpur 1.21.1 API, isolated local PostgreSQL, PowerShell/RCON, Fabric 1.21.1 client, Gradle, Python/Pytest, JUnit-style repository tests, PNG/ZIP asset validation.

**Spec:** `C:/Users/zavod/.codex/attachments/513aaa18-cd70-44e2-a35f-ee6725b9b196/pasted-text-1.txt`

## Global Constraints

- Work only in `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` on branch `codex/end-rift-event`.
- Never connect to, restart, upload to, or mutate a production server, production DB, production world, launcher, or website.
- Preserve the existing local world, local playerdata, and local PostgreSQL directories; use `save-all`, RCON `stop`, dated backups before state/schema migration, and no world wipe.
- Five pre-boss waves are `WAVE_1` through `WAVE_5`; the old final-add wave is a separate `FINAL_WAVE` only when the chosen boss finale requires it.
- Official eligibility remains unique Survival participants; test commands never change official roster, rewards, End unlock, or durable official phase.
- Every repeating task is generation-owned and every temporary entity/block/effect/music/client binding has idempotent cleanup.
- Boss default is 2500 HP and the configured attack bonus is current value plus exactly 5; debuffs are strong but bounded and never use amplifier 255.
- Client visuals remain UUID-scoped with safe vanilla fallback; server gameplay must work without the client mod.
- Every implementation slice starts with a focused failing regression test and ends with fresh focused verification before commit.

### Task 1: Freeze the local safety boundary and write the requirement matrix

**Files:**
- Create: `docs/superpowers/evidence/2026-08-27-end-rift-survival-requirement-matrix.md`
- Create: `tests/test_end_rift_local_safety_contract.py`
- Modify: `tests/RunEndRiftEventChecks.ps1`

**Interfaces:**
- The safety contract must prove the worktree path, branch, local-only environment, fixed local ports, and absence of production hostnames in the runtime command path.
- The requirement matrix uses `Confirmed`, `Partial`, `Failed`, and `Unverified` rows with an evidence path for every row; a non-confirmed material row prevents a “fully verified” claim.

- [ ] **Step 1: Write the failing safety tests** for branch/path/environment/port guards and for the full required evidence headings.
- [ ] **Step 2: Run `python -m pytest -q tests/test_end_rift_local_safety_contract.py` and record the expected missing-matrix failure.**
- [ ] **Step 3: Implement the matrix headings and a PowerShell preflight that refuses non-local config or paths outside `local-runtime`.**
- [ ] **Step 4: Run the focused test and `git diff --check`; commit `test: freeze End Rift local safety boundary`.**

### Task 2: Extract deterministic five-wave objective policies

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/PortalCapturePolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/TowerDefensePolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/HazardPlanner.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/WaveObjectivePolicy.java`
- Create: `tests/PortalCapturePolicyTest.java`
- Create: `tests/TowerDefensePolicyTest.java`
- Create: `tests/HazardPlannerTest.java`
- Create: `tests/WaveObjectivePolicyTest.java`

**Interfaces:**
- `PortalCapturePolicy.tick(PortalState state, boolean occupied, long nowMillis)` returns a new immutable state with 5-second continuous capture, 450ms grace, and faster decay after grace.
- `TowerDefensePolicy.start(int players, long nowMillis)` returns `CoreState` with base 1200, +300 per player beyond two, max 4200, and a 180-second deadline; `damage(CoreState, String attackId, double amount)` is idempotent by attack ID; `finish` distinguishes success and failure.
- `HazardPlanner.plan(int minX, int maxX, int minZ, int maxZ, Set<Point> protectedPoints, Pattern previous, int maxMutated, double minimumSafeRatio, long seed)` returns a bounded plan with a connected safe area and exact original-block snapshot keys.
- `WaveObjectivePolicy` maps waves 1–5 to `AWAKENING`, `HUNT`, `PORTALS`, `TOWER_DEFENSE`, and `RIFT_STORM`, and exposes caps, titles, and completion predicates without Bukkit types.

- [ ] **Step 1: Add tests** for continuous capture/grace/decay/concurrent portals, tower timer/scaling/idempotent damage/failure, hazard protected-block exclusion/cap/safe ratio/connectivity, and the five objective names.
- [ ] **Step 2: Run the Java tests before implementation and capture RED output.**
- [ ] **Step 3: Implement immutable records and deterministic algorithms with fail-closed bounds.**
- [ ] **Step 4: Run all four focused Java suites plus existing `EndEventDomainTest`; commit `feat: add deterministic End Rift objective policies`.**

### Task 3: Add explicit boss stages, cast states, and damage policy

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossStage.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossCastState.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossStagePolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossDamagePolicy.java`
- Create: `tests/BossStagePolicyTest.java`
- Create: `tests/BossDamagePolicyTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`

**Interfaces:**
- `BossStage` contains `AWAKENING`, `HUNTER`, `DISTORTION`, `ABSORPTION`, and `CATASTROPHE`, with exact thresholds 2500/2000/1500/1000/500 and Russian display names.
- `BossCastState` contains `NONE`, `ABSORPTION_CHANNEL`, `JUDGMENT_CAST`, and `EXHAUSTED`.
- `BossStagePolicy.stageFor(double health, boolean judgmentTriggered)` is deterministic and `transition` reports one-time stage entry; `spellPool(BossStage)` returns stage-specific spells.
- `BossDamagePolicy.damageAllowed(BossStage stage, BossCastState castState, long nowMillis, long deadlineMillis)` is true except during an unexpired bounded cast; expired/cancelled casts resolve to damageable.

- [ ] **Step 1: Write failing tests** for thresholds, large damage crossing, one-shot Judgment marker, absorption timeout/cancel/abort/restart cleanup, and post-cast HP reduction.
- [ ] **Step 2: Run the tests and capture RED.**
- [ ] **Step 3: Implement the pure policies and add `bossStage`, `bossCastState`, `bossCastDeadlineMillis`, `judgmentTriggered`, and `judgmentCompleted` to the durable snapshot/state store with backward-safe defaults.**
- [ ] **Step 4: Replace the mutable invulnerability-only branch in `onBossDamage` with the policy result; add a watchdog that synchronizes `setInvulnerable` only for the bounded cast window and logs a reason when damage is cancelled.**
- [ ] **Step 5: Run focused tests and plugin compilation; commit `fix: make boss cast invulnerability bounded and recoverable`.**

### Task 4: Implement the five-wave controllers and objective lifecycle

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/wave/WaveController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/wave/WaveContext.java`
- Create: `copimine-end-event/src/me/copimine/endevent/wave/WaveRuntimeSnapshot.java`
- Create: `copimine-end-event/src/me/copimine/endevent/wave/PortalWaveController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/wave/TowerDefenseWaveController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/wave/RiftStormWaveController.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventConfig.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/config.yml`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventSnapshot.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventStateStore.java`
- Create/Modify: `tests/test_end_event_wave_objectives_contract.py`

**Interfaces:**
- `WaveController.start(WaveContext)`, `tick(long)`, `onEntityDeath(EntityDeathEvent)`, `onPlayerMove(Player)`, `completed()`, `cleanup(CleanupReason)`, and `diagnosticSnapshot()` are the only controller lifecycle methods.
- `WaveContext` carries event ID, generation, world, core/arena bounds, official roster, entity cap, and controller-owned task registry; it never carries production credentials or external paths.
- Wave III creates 3/4/5/6 portals by roster size, captures for 5 seconds with grace, applies Speed I and controlled knockback, and completes only after portals, mobs, and spawn tasks are clear.
- Wave IV starts only after durable start, tracks 180 seconds and bounded Core HP, applies idempotent attack IDs, supports temporary player aggro, and on failure cleans only Wave IV entities, applies Blindness 5 seconds, restores 100% HP, increments attempt, and retries after 5 seconds.
- Wave V uses a single storm task, bounded hazard/web snapshots, 35% minimum safe floor, no fire spread, and complete restoration before boss cinematic.
- Wave I/II receive their objective pulses/mark controller with short ActionBars and no title spam.

- [ ] **Step 1: Add contract tests** for state transitions, objective types, portal capture, tower success/failure/retry, hazard restoration, speed/knockback, and test-mode no-reward behavior.
- [ ] **Step 2: Run RED against the current three-wave implementation.**
- [ ] **Step 3: Implement the controller interfaces and pure-policy adapters; keep Bukkit block/entity operations inside the corresponding controller.**
- [ ] **Step 4: Integrate one controller instance into `CopiMineEndEvent` and replace the `ownedEntities.isEmpty()`-only completion checks with objective completion plus owned-entity cleanup.**
- [ ] **Step 5: Add durable mutation journal entries before Wave I/V/final-spell block changes and restore journal entries on boot, abort, failure, success, and disable.**
- [ ] **Step 6: Run objective Java/Python tests and local plugin build; commit `feat: run five bounded End Rift objectives`.**

### Task 5: Finish wave rewards as personal, physical, and restart-idempotent

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/WaveRewardPolicy.java`
- Create: `tests/WaveRewardPolicyTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/config.yml`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventSnapshot.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventStateStore.java`
- Modify: `tests/test_end_event_completion_audit_contract.py`

**Interfaces:**
- `WaveRewardPolicy.bundle(int wave, int playerIndex, int participantCount, Map<String,Integer> config)` returns at most 8 merged item stacks per participant and a single shared rare-roll decision per event/wave.
- Durable keys are `end-event:<eventId>:wave:<wave>:participant:<uuid>` and `end-event:<eventId>:wave:<wave>:shared-rare`; the code never grants `rift_core_shard` before official boss victory.
- The physical burst is tagged `WAVE_REWARD`, owner-protected for 30 seconds, capped at 6–8 item entities per participant, and discovered on restart before any retry.

- [ ] **Step 1: Add tests** for generous wave bundles, personal fairness, stack merging/entity cap, shared rare roll once, failed attempt no reward, and restart no duplication.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement the policy, durable marker, owner metadata, and Core burst with bounded particles/sound.**
- [ ] **Step 4: Verify `clear`, abort, core removal, and test cleanup remove reward entities without issuing new rewards; commit `feat: make wave rewards physical and idempotent`.**

### Task 6: Implement the boss movement controller and five named stages

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossMovementPolicy.java`
- Create: `tests/BossMovementPolicyTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/config.yml`
- Modify: `tests/test_end_event_ai_contract.py`

**Interfaces:**
- `BossMovementPolicy.chooseSafeDestination(Anchor anchor, Target target, List<Candidate> candidates, double radius, double verticalRadius, double minCoreDistance, Set<Material> blocked)` scores flank/target/ring candidates, rejects Core/liquid/fire/web/temporary blocks, and returns a deterministic safe candidate.
- `MovementController` maintains an explicit one-operation teleport permit, stuck timer, last movement position/time, phase-aware cooldown, and emits `BOSS_MOVE_TELEPORT` only after a successful real movement.

- [ ] **Step 1: Add pure tests** for candidate scoring, minimum Core distance, vertical radius, blocked materials, stuck fallback, and bounds.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement the policy and replace exact-Y leash with the controller; allow arena vertical movement within configured `arena.vertical-radius`.**
- [ ] **Step 4: Update `EntityTeleportEvent` to accept only the one-operation permit and cancel vanilla Enderman teleports outside the policy.**
- [ ] **Step 5: Exercise four arena sectors in the local bot test and verify boss leaves the Core area without leaving bounds; commit `fix: make Rift Guardian movement target-driven`.**

### Task 7: Strengthen spells, Judgment, and cleanup guarantees

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/JudgmentPolicy.java`
- Create: `tests/JudgmentPolicyTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/EndRiftAiPolicy.java`
- Modify: `copimine-end-event/config.yml`
- Modify: `tests/test_end_event_spell_names_contract.py`

**Interfaces:**
- `JudgmentPolicy.start` creates three scheduled pulses with 4.0/3.5/3.0 second movement windows, 3 safe zones, connected safe area, 20–24 ring points, and one damage application per pulse; `complete` enters `EXHAUSTED` for 6 seconds and then Catastrophe.
- `EndRiftAiPolicy.BossSpell` includes Russian `Пламя Разлома`; every projectile/particle spell has one bounded flight controller, a global active-projectile cap, and a trail interval no faster than every 2 ticks.
- Fire spell uses a journaled visual hazard with at least 30–35% safe floor and no real fire spread; cleanup restores exact block data.

- [ ] **Step 1: Add tests** for spell pools by stage, bounded debuff values, one-shot Judgment, safe-zone reachability, pulse timings, fire cleanup, and no duplicate effects per cast ID.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement bounded spell controllers, per-player particle emission, actionbar/title warnings, and Judgment safe-zone displays/pillars.**
- [ ] **Step 4: Add timeout/exception/boss-death/player-absence cleanup that clears cast state and forces damageable state.**
- [ ] **Step 5: Run focused tests, plugin build, and local RCON spell commands; commit `feat: add staged Rift Guardian spells and Judgment`.**

### Task 8: Build the real boss model, five textures, and phase protocol

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndEventPacket.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/ClientVisualManager.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/EndermanEntityRendererMixin.java`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_awakening.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_hunter.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_distortion.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_absorption.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_catastrophe.png`
- Create: `tests/test_end_rift_client_model_contract.py`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`

**Interfaces:**
- `EndEventPacket` accepts optional `END_BOSS_PHASE` payload fields event ID, generation, boss UUID, binding instance, phase ID, visual/model ID, and transition duration; unknown packet types are ignored safely.
- `EndEventClientState.applyBossPhase` rejects stale generation/event/binding, stores phase/model, and clears on unbind/disconnect/world change.
- `RiftGuardianModel` has heavy torso, broad shoulders, two arms, floating shards/horns, chest rift, idle/walk/attack/absorption/judgment transforms, and one geometry with five texture variants.
- The mixin replaces model/texture only for a bound boss UUID; ordinary Endermen, spiders, shulkers, and clients without the mod retain vanilla rendering.
- Server sends phase packets only on spawn, stage transition, join/reconnect, and recovery; no per-tick binds.

- [ ] **Step 1: Add failing client-state/protocol/model asset tests and a final-JAR ZIP-entry test.**
- [ ] **Step 2: Run client tests RED.**
- [ ] **Step 3: Implement packet parsing/state, model layer, renderer, stage transforms, and five non-empty symmetric textures using the image-generation workflow and saved art prompts.**
- [ ] **Step 4: Build the Fabric client twice; assert the current client JAR contains all mob/boss textures, model classes, mixin config, and no stale third-party JAR is used.**
- [ ] **Step 5: Sync only local/test `thirdparty/client-mods` and `thirdparty/CopiMineMods.zip`; verify the server resource pack remains separate and does not override vanilla textures; commit `feat: render Rift Guardian with staged client model`.**

### Task 9: Add visual/debug diagnostics and performance budgets

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/plugin.yml`
- Create: `tests/test_end_rift_performance_contract.py`
- Create: `docs/superpowers/evidence/2026-08-27-end-rift-performance-baseline.md`
- Modify: `tests/RunEndRiftEventChecks.ps1`

**Interfaces:**
- `/cmend debug packets` reports packet quality mode, plugin messages/sec, estimated particle packets/sec, active projectile count, bound viewers, and recent stage counters.
- `/cmend debug objectives` reports objective type, portal progress, Core HP/timer/attempt, hazard/web snapshot status, and reward markers.
- `/cmend debug hazards` reports mutated/restored block counts and safe-zone ratio.
- `/cmend test visuals mobs` and `/cmend test visuals boss <phase>` print UUID, role, visual ID, bound viewers, and resource path/presence without changing official state.
- BossBar updates at most 5Hz and only when rendered value changes; all arena-only particles use per-player audience filtering; scans are local to the owned sets or precomputed bounded points.

- [ ] **Step 1: Add failing source contracts** for command coverage, no per-block repeating task, bounded trail, no all-world hot-loop scan, throttled BossBar, and per-player particles.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement diagnostics, counters, adaptive `FULL`/`REDUCED`/`MINIMAL_SAFE` quality, and throttled rendering.**
- [ ] **Step 4: Run static tests and instrumented local RCON sampling; commit `perf: bound End Rift visual and packet work`.**

### Task 10: Extend local test bots and run the complete runtime matrix

**Files:**
- Modify: `tests/LocalEndRiftBot.js`
- Modify: `tests/RunEndRiftRuntimeSmoke.ps1`
- Modify: `tests/StartEndRiftLocal.ps1`
- Create: `tests/RunEndRiftTwoPlayerSurvival.ps1`
- Create: `docs/superpowers/evidence/2026-08-27-end-rift-survival-runtime.md`

**Interfaces:**
- The local bot runner creates two distinct local Survival sessions, records ping every 5 seconds, TPS/MSPT, owned living entities, displays, projectiles, particle/plugin-message estimates, GC/CPU samples, resource-pack response size/frequency, and Radmin RTT/loss without changing MTU.
- The runtime script exercises no-event baseline, Wave I, Wave II mark, Wave III portal capture, Wave IV success/failure/retry, Wave V hazard/web restoration, boss cinematic, all five boss stages, absorption damage, every spell, Judgment, exhausted window, death/reward/unlock, and restart recovery.
- Runtime preflight saves the local world/DB state and verifies exact branch, JAR hashes, resource-pack HTTP 200/SHA1, plugin services, and no stale JAR.

- [ ] **Step 1: Add failing runtime-contract assertions** for two UUIDs, Survival mode, no admin phase skipping, full marker order, restart persistence, and no production endpoints.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement bounded RCON/bot orchestration; use normal saves/stops and never delete world/playerdata/DB directories.**
- [ ] **Step 4: Start the local server from the maintained batch entry point, run the two-player scenario, and save raw logs/CSV/JSON evidence.**
- [ ] **Step 5: Run the required bounded Windows checks: `ping -n 30 <Radmin-IP>`, `pathping <Radmin-IP>`, and a non-mutating MTU/fragmentation probe.**
- [ ] **Step 6: Run at least a 15-minute two-client performance window; classify network RTT, server MSPT, client/render, chunks, packet flood, resource download, and VPN latency separately.**
- [ ] **Step 7: Populate the evidence matrix; commit `test: execute two-player End Rift survival matrix`.**

### Task 11: Run all gates, review the branch, and push only the verified branch

**Files:**
- Modify: `docs/superpowers/evidence/2026-08-27-end-rift-survival-requirement-matrix.md`
- Modify: `docs/superpowers/evidence/2026-08-27-end-rift-performance-baseline.md`
- Modify: `docs/superpowers/evidence/2026-08-27-end-rift-survival-runtime.md`

**Interfaces:**
- Build gate is `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1` plus the client, runtime, and full repository test commands listed in the evidence files.
- The final branch report includes every changed file, commit SHA, `git diff --stat`, test PASS/FAIL totals, client/server/resource-pack hashes, logs, network/performance table, visual evidence, and exact local update commands that preserve world/DB.

- [ ] **Step 1: Run `git diff --check`, all focused Python/Java tests, client tests, plugin builds, resource-pack build, and the complete check runner.**
- [ ] **Step 2: Stop and restart only the isolated local server with `save-all`, RCON `stop`, process identity verification, and fresh JAR/hash checks.**
- [ ] **Step 3: Re-run the restart-recovery scenario and verify no permanent invulnerability, duplicate rewards, stale entities, mutated floor, stale bindings, or repeated music outside the event.**
- [ ] **Step 4: Review the diff for launcher/site/production paths and reject any accidental mutation.**
- [ ] **Step 5: Commit the final evidence and push `codex/end-rift-event` to the configured GitHub remote; verify the remote ref and commit without deploying.**
- [ ] **Step 6: Write the final report with `NOT FULLY VERIFIED` and exact remaining gaps whenever any required runtime/client evidence is unavailable.**

## Self-review checklist

- State/config: five waves, objective config, stage/cast markers, journal, and migration defaults are covered by Tasks 2–4.
- Network/performance: diagnostics, per-player particles, throttled BossBar, bounded trail, Radmin measurements, and 15-minute runtime are covered by Tasks 9–10.
- Boss: movement, five stages, bounded invulnerability, all spells, fire, Judgment, exhausted cleanup, damage-after-absorption are covered by Tasks 3, 6, and 7.
- Visuals: UUID binding, five mob textures, real Guardian geometry, phase packet, fallback, JAR sync, and runtime evidence are covered by Task 8 and Task 10.
- Loot/recovery: personal physical bundles, shared rare roll, no pre-victory shard, failure/restart idempotency, and exact cleanup are covered by Task 5 and Task 11.
- No plan step relies on a placeholder or an unbounded world/DB mutation; every step names its files, interfaces, tests, and verification command.
