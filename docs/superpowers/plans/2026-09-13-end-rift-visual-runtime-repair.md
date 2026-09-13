# End Rift visual runtime repair implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current End Rift client and resource-pack artifacts render the supplied assets and repair the visible Wave 3, Wave 4, Wave 7, and boss HUD defects without altering the working boss projectile mechanic.

**Architecture:** The repair has two boundaries. First, source assets and client code must be built into one verified client JAR and one verified resource pack, then copied atomically into the local Minecraft profile with obsolete duplicate client JARs quarantined. Second, server-owned visuals use deterministic geometry and journaled physical state: gate models must parse under vanilla JSON rules, obelisks must use a non-repeating visual composition, and Wave 7 walls must make a visible surface over the same cells as their collision barriers. The boss HUD consumes the existing authoritative health packet and renders phase thresholds locally.

**Tech Stack:** Java 21/Paper-Purpur plugin, Fabric 1.21.1 client, vanilla resource-pack JSON, Python pytest contracts, PowerShell local runtime harness.

**Spec:** User-authored End Rift V3 bug list in this Codex task; no separate repository specification exists. This plan is the scoped executable specification for the remaining visual/runtime defects.

## Global Constraints

- Work only in `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` on `codex/end-rift-event`.
- Preserve existing user changes and never delete an existing game artifact; obsolete local client JARs are moved only into a local backup directory after their exact targets are resolved.
- Use the supplied `enderboss` mesh/skin and the two supplied attack clips from `D:\Downloads\Telegram Desktop` when the client model route is active; ordinary supplied assets are limited to Enderman and Spider skins.
- Do not change boss projectile speed, timer, hitbox, reflection, damage, trajectory, or parry rules.
- Every behavioral source change starts with a focused failing test and is verified green before the next change.
- Do not treat a build, a log, or a screenshot supplied by the user as native-client verification. Native Minecraft verification requires a targetable Computer-use window and a freshly deployed JAR/pack hash.
- Update `minecraft/server/server.properties` only to the SHA-1 produced by the current resource-pack build.

---

### Task 1: Verify and deliver the actual client artifacts

**Files:**
- Create: `tests/SyncEndRiftClientArtifacts.ps1`
- Create: `tests/test_end_rift_client_artifact_sync.py`
- Modify: `tests/StartEndRiftLocalUserSession.ps1`
- Modify: `tests/test_local_start_batch_contract.py`

**Interfaces:**
- Consumes: `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` and `resourcepacks/build/CopiMineResourcePack.zip`.
- Produces: exactly one active `CopiMineClient-0.1.1.jar` and one matching manual `CopiMineResourcePack.zip` in the selected local game profile, both SHA-256 verified.

- [ ] **Step 1: Write the failing artifact-sync integration test.** Create an isolated fake game directory containing an old `CopiMineClient-0.1.0.jar`, a fake source client JAR, and a fake resource-pack ZIP. Assert the sync script leaves the new JAR and pack byte-identical to their sources and moves the old JAR below `mods/copimineclient-backups/`.
- [ ] **Step 2: Run the test and observe the missing-script failure.** Run `py -m pytest tests/test_end_rift_client_artifact_sync.py -q` and confirm it fails because `SyncEndRiftClientArtifacts.ps1` does not exist.
- [ ] **Step 3: Implement a hash-checked sync helper.** Require explicit source files and a resolved game directory; reject paths outside the selected profile; reject a running Fabric client before mutation; copy then verify hashes; move only verified obsolete `CopiMineClient-*.jar` files into a timestamped backup directory.
- [ ] **Step 4: Call the helper from the local user-session runner before optional client launch.** Build the Fabric client before synchronization, fail closed if the target profile is stale while Minecraft is running, and keep server startup independent from production resources.
- [ ] **Step 5: Re-run both focused tests.** Run `py -m pytest tests/test_end_rift_client_artifact_sync.py tests/test_local_start_batch_contract.py -q` and record the result.

### Task 2: Make the gate model load instead of falling back to magenta/black

**Files:**
- Modify: `resourcepacks/src/assets/copimine/models/block/end_event_portal_shard.json`
- Modify: `tests/test_end_event_resource_visual_contract.py`

**Interfaces:**
- Consumes: vanilla block-model element rotation constraints.
- Produces: a gate shard model that Paper clients parse with only supported `-45`, `-22.5`, `0`, `22.5`, or `45` degree rotations.

- [ ] **Step 1: Keep the existing failing resource-contract regression.** The test scans every End Rift vanilla model and rejects unsupported element rotations.
- [ ] **Step 2: Verify the historical failure against a copied pre-fix shard.** The test must reject `-28.0`/`28.0` because Minecraft's model loader explicitly rejects them.
- [ ] **Step 3: Retain only nearest supported rotations in the actual shard.** Use `-22.5` and `22.5` so the silhouette remains angled but valid.
- [ ] **Step 4: Rebuild the pack and assert no model parse failure occurs in the fresh local server/client resource logs.**

### Task 3: Repair the obelisk visual composition without changing obelisk combat state

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `resourcepacks/src/assets/copimine/models/item/end_event_rift_obelisk_full.json`
- Modify: `resourcepacks/src/assets/copimine/models/item/end_event_rift_obelisk_damaged.json`
- Modify: `resourcepacks/src/assets/copimine/models/item/end_event_rift_obelisk_critical.json`
- Modify: `tests/test_end_event_resource_visual_contract.py`
- Modify: `tests/EndRiftVisualPolicyTest.java` or add a focused Java policy test

**Interfaces:**
- Consumes: the existing obelisk health-state selection and journaled server ownership.
- Produces: one correctly anchored vertical obelisk visual per state, with the portrait on intended faces only and no stack of repeated whole-obelisk models.

- [ ] **Step 1: Write a failing policy/asset test for a single visual composition.** It must fail when a five-layer server loop uses the full model for each layer.
- [ ] **Step 2: Implement a fixed-height composition or section-specific models.** Preserve current physical blocks, ownership tags, health-state selection, cleanup, and client binding semantics.
- [ ] **Step 3: Run the focused resource and Java tests.**

### Task 4: Make Wave 7 rooms visually obvious and physically identical to their boundary

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/RealitySplitBarrierPolicy.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/RealitySplitBarrierPolicyTest.java`
- Modify: `tests/test_end_event_wave6_wave7_boundaries_contract.py`

**Interfaces:**
- Consumes: `RealitySplitBarrierPolicy.cells(chamberCount)` and existing `HazardMutationJournal` ownership.
- Produces: an End-themed display wall at every physical `Material.BARRIER` cell, high enough to read as a room divider and removed only with its corresponding boundary.

- [ ] **Step 1: Write a failing test that requires the intended visible height and bounded four-chamber geometry.**
- [ ] **Step 2: Implement the smallest geometry/material change.** Update `MAX_CELLS`, render a clearly visible End-themed wall from the same level-one bases, retain journal restore/open-boundary behavior, and do not alter mob chamber logic.
- [ ] **Step 3: Run `RealitySplitBarrierPolicyTest` and the Wave 6/Wave 7 contract test.**

### Task 5: Replace the placeholder boss frame with a compact phase-marked event HUD

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndRiftBossBarHud.java`
- Modify: `CopiMineClient/src/main/resources/assets/copimineclient/textures/gui/end_rift_bossbar_frame.png` or add purpose-specific HUD layers
- Modify: `tests/test_end_rift_client_hud_contract.py`

**Interfaces:**
- Consumes: `EndEventClientState.BossBarState.health()`, `maxHealth()`, current phase, and `progress()`.
- Produces: a compact themed HUD with readable authoritative HP, a segmented fill, explicit phase threshold ticks, no duplicate vanilla End Rift boss bar, and no independent health authority.

- [ ] **Step 1: Amend the focused HUD test to require actual health/max-health text, phase markers, a bounded layout, and marker placement derived from fixed phase fractions.**
- [ ] **Step 2: Run it RED against the old 384x128 flat purple frame.**
- [ ] **Step 3: Implement the HUD rendering and use the existing authoritative packet only.**
- [ ] **Step 4: Build the Fabric client and run the focused HUD test green.**

### Task 6: Assemble and verify the fresh runtime

**Files:**
- Modify: `minecraft/server/server.properties` only if the newly built pack SHA-1 differs.
- Test: `tests/RunEndRiftEventChecks.ps1`, targeted resource/client tests, `tests/RunEndRiftWave6Wave7BoundariesLive.ps1`, `tests/RunEndRiftBossVisualLive.ps1`.

- [ ] **Step 1: Build the resource pack twice and verify reproducible SHA-1/SHA-256 values.**
- [ ] **Step 2: Update the tracked resource-pack SHA-1 to exactly the first build's SHA-1 and rerun validators.**
- [ ] **Step 3: Build server and Fabric client from the current worktree; sync into the local isolated server/profile only when the client is stopped.**
- [ ] **Step 4: Run the full End Rift gate and targeted Paper probes for gates, obelisks, Wave 6/7, boss real health, shield states, model/animation binding, and projectile regression.**
- [ ] **Step 5: Use Computer Use to perform native visual checks only if it exposes the Minecraft window. If it does not, report the precise limitation and do not mark native QA complete.**
- [ ] **Step 6: Review the diff, commit only verified changes, push the branch, then wait for GitHub Actions tied to the final SHA.**
