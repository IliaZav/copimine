# CopiMine End Rift Master Repair Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task with a fresh implementer and a spec/quality review after every task.

Goal: Repair and freshly verify the End Rift event against the attached master repair prompt, covering Wave 6 server authority, Wave 7 recovery, exact boss hitboxes and projectile collision, authored animation poses, model UV safety, distributed artifact parity, and native Minecraft evidence when the native client surface is available.

Architecture: Keep encounter rules in pure Java domain policies and one authoritative RitualSphereEncounter state/controller. Keep Bukkit entity handles, effects, displays, packets, block journals, and cleanup in the runtime adapter. Keep model conversion, UV checks, renderer selection, and animation playback in CopiMineClient; generated artifacts and evidence are rebuilt from the exact final source tree.

Tech Stack: Java 21, Paper/Bukkit, Fabric Minecraft 1.21.1, Gradle, PowerShell, Python/pytest, JUnit, local PostgreSQL/Paper fixtures, GitHub branch codex/end-rift-event.

Spec: C:\Users\zavod\.codex\attachments\f88ba2bb-7412-4ef9-95bf-dc7f2781a85f\pasted-text.txt

## Global Constraints

- The required historical baseline is 79781d8c8be3827078a77972ccba851df4eb819f, with message chore: make model evidence portable; it must remain in history and be explicitly recorded.
- The current isolated branch already contains later commits through cd2e6c6f9a70da7e2713c4fc8629e1dadfc6f48e; preserve those changes and compare all new work to the baseline instead of resetting the worktree.
- Do not touch the dirty outer checkout D:\Desktop\Copimine\copimine-main; all source changes belong in D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event.
- Every behavior change requires a focused RED test, the smallest GREEN implementation, relevant subsystem tests, and a review before the next task.
- Tests must exercise real behavior or pure math/policy; source-string checks are secondary guards only.
- Static previews, source/build checks, hashes, local Paper runtime, and native Minecraft visuals are separate evidence layers.
- Do not call native Minecraft visuals verified unless a fresh controllable native client inspected the exact final SHA.
- Keep all temporary entities, blocks, tags, controls, beams, zones, displays, and journals owned by event/generation and cleanup idempotently.
- Do not deploy to production. Local/staging live scripts must refuse production endpoints.
- Commit each coherent task locally; push the final verified branch to https://github.com/IliaZav/copimine only after the required runtime gates and report are regenerated.

## Pre-flight ruling

The requested branch is not currently at the historical baseline: 79781d8c... is an ancestor and cd2e6c6... is the current checked-out tip. Resetting would destroy already-created End Rift work and untracked visual evidence. The execution therefore treats 79781d8c... as the audit baseline and cd2e6c6... as the starting working tree, with every new diff and final report naming both SHAs.

---

### Task 1: Portable model-evidence artifact gate

Files:

- Create tests/test_end_rift_model_evidence_portability.py
- Modify tests/test_end_event_wave_mob_visual_contract.py only if the repository's existing fixture belongs there
- Test artifacts: artifacts/end-rift-v3-evidence/end-rift-mob-model-preview-manifest.json, end-rift-mob-model-board-20260916.png, and end-rift-mob-model-verification-20260916.md

Interfaces:

- Consumes the existing generated manifest and report.
- Produces artifact-level assertions that every preview path is repository-relative, forward-slash based, resolvable from the repository root, and free of C:\Users, D:\Desktop, .worktrees, and username prefixes.

- [ ] Add the manifest path and board hash tests from the spec, plus the machine-prefix assertion.
- [ ] Run python -m pytest -q tests/test_end_rift_model_evidence_portability.py; expected RED if the generated artifact is invalid.
- [ ] Fix only the generator or generated report/manifest owner after the RED is observed.
- [ ] Re-run the focused test and then the existing model visual contract.
- [ ] Commit test: lock portable End Rift model evidence.

---

### Task 2: Seal capture policy and WAITING_FOR_PRISONER

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/RitualSealCapturePolicy.java
- Create tests/RitualSealCapturePolicyTest.java
- Modify copimine-end-event/src/me/copimine/endevent/runtime/encounter/RitualSphereEncounter.java
- Modify copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java
- Modify tests/RunEndRiftEventChecks.ps1

Interfaces:

- RitualSealCapturePolicy.select(List<Candidate>, double sealX, double sealZ): UUID
- Candidate(UUID playerId, boolean eligible, double x, double z)
- Wave 6 starts with an explicit waiting state and does not initialize prisoner UUID, tag, drain timer, or freeze anchor before physical seal entry.

- [ ] Add and register the exact Java test from the spec, including an eligible inside player whose UUID sorts after an outside player.
- [ ] Compile and run only RitualSealCapturePolicyTest; record the intended missing-policy RED failure.
- [ ] Implement the radius policy and caller-order selection.
- [ ] Run the focused test GREEN.
- [ ] Wire the live tick to spawn the visible seal and capture the first eligible participant inside 1.25 blocks; set first drain due to capture time plus 20,000 ms.
- [ ] Add or adjust a pure encounter-state test proving no capture outside the seal.
- [ ] Run the seal and encounter policy tests and commit fix: wait for physical Wave 6 seal capture.

---

### Task 3: Central free-player target eligibility

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/RitualTargetPolicy.java
- Create tests/RitualTargetPolicyTest.java
- Modify all Wave 6 targeting call sites in CopiMineEndEvent.java, RitualSphereEncounter.java, and runtime encounter helpers
- Modify tests/RunEndRiftEventChecks.ps1

Interfaces:

- RitualTargetPolicy.freeTargets(List<Candidate>, UUID prisoner): List<UUID>
- Candidate(UUID playerId, boolean eligible)

- [ ] Add the exact prisoner-exclusion test and register it.
- [ ] Run the focused test RED before adding the policy.
- [ ] Implement the policy preserving caller order and de-duplicating UUIDs.
- [ ] Replace generic active-player pools for barrage, zones, reverse, swap, guards, awakened casters, and projectile retargeting.
- [ ] Rename any method named nearest that does not sort by distance, or implement distance-first ordering with UUID tie-break.
- [ ] Run the focused and relevant Wave 6 policy tests and commit fix: exclude ritual prisoner from hostile targets.

---

### Task 4: Ritual prisoner health authority

Files:

- Modify copimine-end-event/src/me/copimine/endevent/domain/RitualPrisonerHealthPolicy.java
- Modify copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java
- Modify tests/RitualPrisonerHealthPolicyTest.java
- Modify local integration/live test coverage and tests/RunEndRiftEventChecks.ps1

- [ ] Replace external-damage expectations with the three exact zero-damage assertions from the spec.
- [ ] Run the focused test and record RED against the current implementation.
- [ ] Make safeExternalDamage return 0.0D for captured external damage, or replace it with a clearly tested blocking predicate.
- [ ] In onRitualPrisonerDamage, cancel the event without manually subtracting health.
- [ ] Add behavior coverage for melee, projectile, fall, zone, explosion, fire, poison, wither, and generic entity damage between drain ticks.
- [ ] Run focused and subsystem tests, then commit fix: make ritual prisoner immune to external damage.

---

### Task 5: Control-swap exclusion and atomic cleanup

Files:

- Modify copimine-end-event/src/me/copimine/endevent/domain/RitualControlPairPolicy.java
- Modify tests/RitualControlPairPolicyTest.java
- Modify copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java and control-state runtime helpers
- Modify tests/RunEndRiftEventChecks.ps1

- [ ] Add the exact excluded-prisoner pair assertions while retaining the reverse/swap mutual exclusion assertion.
- [ ] Run the focused test RED.
- [ ] Implement pair(List<String> participantIds, String excludedId, int requestedPairs) and remove the excluded ID before pairing.
- [ ] Build runtime pairs from the free-target policy, not generic active players.
- [ ] Add atomic clearing for disconnect, death, world change, invalid participant, and expiry; both sides must clear together.
- [ ] Run focused/control tests and commit fix: keep ritual prisoner out of control swap.

---

### Task 6: Corrupted-zone effects

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/RitualZoneEffectPolicy.java
- Create tests/RitualZoneEffectPolicyTest.java
- Modify Bukkit zone tick/application code in CopiMineEndEvent.java and encounter runtime helpers
- Modify tests/RunEndRiftEventChecks.ps1

- [ ] Add the exact pure policy test and run it RED.
- [ ] Implement Result(wither, slowness, reverseMovement) with prisoner/outside suppression and swap/reverse mutual exclusion.
- [ ] Apply refreshed short Wither and Slowness effects only in active 4x4 zones; remove Poison and raw player.damage(1.0D).
- [ ] Clear only ritual-owned reverse state when a player exits; preserve unrelated potion effects.
- [ ] Run focused and relevant runtime contract tests, then commit fix: align corrupted zone with ritual design.

---

### Task 7: Sphere-origin projectiles

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/RitualSphereProjectilePolicy.java
- Create tests/RitualSphereProjectilePolicyTest.java
- Modify the server-owned Ritual Sphere projectile path and cleanup tags
- Modify tests/RunEndRiftEventChecks.ps1 and the Wave 6 live script

- [ ] Add the exact direction test and run RED.
- [ ] Implement finite normalized direction with zero-vector handling and MAX_INITIAL_SPEED = 0.85D.
- [ ] Separate encounter owner, sphere visual origin, free-player target, server damage authority, lifetime, block/player collision, cleanup, and event/generation tags.
- [ ] Remove the Wave 6 call to caster-origin riftArrowVolley(caster, target, ...).
- [ ] Add a local Paper marker pair sphere_origin=x,y,z and projectile_spawn=x,y,z, requiring distance at most 0.25.
- [ ] Run focused and subsystem tests, then commit fix: launch ritual projectiles from sphere center.

---

### Task 8: Caster role mapping and bounded amplifiers

Files:

- Modify copimine-end-event/src/me/copimine/endevent/domain/RitualCasterTacticsPolicy.java
- Modify tests/RitualCasterTacticsPolicyTest.java
- Modify tests/test_wave6_ritual_caster_behavior_contract.py
- Modify runtime caster rotation in CopiMineEndEvent.java

- [ ] Replace the six-distinct-attack test with role assertions for slots 0 through 5.
- [ ] Run the focused Java and Python tests RED.
- [ ] Implement Role.PROJECTILE_CASTER, ZONE_CASTER, REVERSE_CASTER, CONTROL_SWAP_CASTER, and AMPLIFIER; slots 4 and 5 must be amplifiers.
- [ ] Remove VOID_LANCE, RIFT_SPIKES, spawnRitualVoidLance, and spawnRitualRiftSpikes from required core behavior.
- [ ] Keep amplification caps at projectile +20%, duration +30%, cooldown multiplier floor 0.75, and no gain at the 1 HP floor.
- [ ] Run focused role tests and commit fix: make extra ritual casters amplifiers.

---

### Task 9: One authoritative Ritual Sphere state

Files:

- Modify copimine-end-event/src/me/copimine/endevent/runtime/encounter/RitualSphereEncounter.java
- Modify copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java
- Modify copimine-end-event/src/me/copimine/endevent/domain/RitualSphereEncounterPolicy.java
- Modify or create tests/RitualSphereEncounterPolicyTest.java, snapshot tests, and registered gate entries

- [ ] Add the pure timestamp test: start without prisoner has no due drain; capture at 100,000 first drains at 120,000, not earlier.
- [ ] Run it RED.
- [ ] Make the encounter/controller the single owner of state transitions and timers; Bukkit code owns only handles and adapters.
- [ ] Derive persisted snapshots from authoritative state and rebuild runtime entity maps from snapshots.
- [ ] Ensure no live encounter path uses a hardcoded 0L first-drain timestamp.
- [ ] Run encounter policy, snapshot, and recovery tests and commit refactor: centralize ritual sphere state.

---

### Task 10: Exact boss OBB ray testing

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/BossOrientedHitboxPolicy.java
- Create tests/BossOrientedHitboxPolicyTest.java
- Modify copimine-end-event/src/me/copimine/endevent/runtime/BossHitboxController.java
- Modify tests/RunEndRiftEventChecks.ps1

- [ ] Add the 45-degree thin-box empty-corner MISS and center HIT tests; run RED against the enclosing-AABB authority.
- [ ] Implement immutable Vec3, Ray, and OrientedBox with local center/half-extents and Euler/matrix transforms.
- [ ] Transform world rays to local space, run the slab test, apply max distance in the world-equivalent parameter, and return nearest hit distance.
- [ ] Keep Bukkit Interaction entities as broad selectors only; final acceptance must use OBB math.
- [ ] Run focused boss hitbox tests and commit fix: use oriented boss hitboxes.

---

### Task 11: Authored boss animation pose sampling

Files:

- Create or modify a deterministic animation extractor and generated server pose resource under copimine-end-event
- Modify boss animation JSON source/manifest only where the generator owns it
- Modify BossHitboxController.java and relevant domain pose classes
- Create pure pose interpolation tests and generator parity contract

- [ ] Add a RED pure test proving an animated arm changes position between bind and a non-zero attack frame while an unanimated bone stays at bind pose.
- [ ] Implement a deterministic generator that reads the checked-in JSON used by UserEndBossAnimationPlayer, exports hitbox bones, and uses the same interpolation semantics for elapsed ticks.
- [ ] Add head, chest, pelvis, arms, forearms, and legs to the generated table and reject malformed or unsupported animation data loudly.
- [ ] Sample generated pose data in authoritative hitbox updates; remove guessed per-state offsets.
- [ ] Add a regenerate-and-compare test and run the pose subsystem gate.
- [ ] Commit fix: derive boss hitboxes from authored poses.

---

### Task 12: Hitbox proxy reconciliation and self-heal

Files:

- Create copimine-end-event/src/me/copimine/endevent/domain/BossHitboxProxyReconciliationPolicy.java
- Create tests/BossHitboxProxyReconciliationPolicyTest.java
- Modify BossHitboxController.java
- Modify tests/RunEndRiftEventChecks.ps1

- [ ] Add a pure expected/live key test where RIGHT_FOREARM:0 is missing and valid survivors are not stale; run RED.
- [ ] Implement missing = expected - live and stale = live - expected.
- [ ] Recreate missing proxies or atomically rebuild the rig before accepting more boss damage; keep generation/event tags.
- [ ] Emit BOSS_HITBOX_PROXY_RECREATED event=... boss=... part=... segment=... generation=....
- [ ] Run focused/controller tests and commit fix: self-heal missing boss hitbox proxies.

---

### Task 13: True swept projectile segments

Files:

- Modify BossOrientedHitboxPolicy.java and its tests
- Modify owned projectile tracking/collision code in BossHitboxController.java or the owning runtime adapter
- Modify tests/RunEndRiftEventChecks.ps1

- [ ] Add RED tests for a segment stopping before the box, a segment already past the box, one crossing segment, and a zero-length segment.
- [ ] Implement finite previousPosition to currentPosition segment intersection against OBBs.
- [ ] Remove arbitrary forward/backward 8-block rays and ensure a projectile hit is accepted at most once per crossing/owned projectile.
- [ ] Run focused projectile tests and live collision contract tests.
- [ ] Commit fix: use finite swept segments for boss projectiles.

---

### Task 14: Runtime Java-model UV footprint validation

Files:

- Create CopiMineClient/src/main/java/me/copimine/client/ModelUvBounds.java
- Create CopiMineClient/src/test/java/me/copimine/client/ModelUvBoundsTest.java
- Modify RiftEventEndermanModel.java, RiftEventSkeletonModel.java, and RiftSpiderModel.java
- Modify construction tests for ordinary, elite, guardian, and ritual roles

- [ ] Add the exact ModelUvBoundsTest and run the client test RED.
- [ ] Implement float-safe standard box footprint checks: maxU = u + 2*w + 2*d and maxV = v + d + h, rejecting values outside declared atlas dimensions.
- [ ] Route every custom cuboid through the helper in the three runtime models.
- [ ] Add construction tests for every role variant and fail loudly on invalid UV requests.
- [ ] Do not enlarge atlases as a shortcut.
- [ ] Run the focused Gradle test and commit fix: validate runtime model UV footprints.

---

### Task 15: Skeleton look rotation parity

Files:

- Modify CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModel.java
- Modify or create a client test or Python secondary contract

- [ ] Add the regression guard for super.setAngles and absence of duplicate head.yaw += MathHelper.clamp(headYaw) and head.pitch += MathHelper.clamp(headPitch).
- [ ] Run the focused client/source contract RED if the duplicate transform exists.
- [ ] Remove only the second look application; preserve event-specific offsets independent of vanilla look control.
- [ ] Run skeleton model tests and commit fix: avoid double skeleton head rotation.

---

### Task 16: Fresh static, client, and build gates

Files:

- Modify generated artifacts only through their owner scripts.
- Modify docs/end-rift-validation.md with fresh command output and exact SHA.

- [ ] Run powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1 from the final worktree.
- [ ] Run powershell -NoProfile -ExecutionPolicy Bypass -File .\CopiMineClient\build-client.ps1.
- [ ] Run git diff --check.
- [ ] Run the full Python suite with the declared project Python environment and record the exit result.
- [ ] Rebuild client, server, and resource-pack outputs before claiming parity.
- [ ] Commit only fresh report updates with docs: record final End Rift static gates.

---

### Task 17: Fresh Wave 6 local Paper live verification

Files:

- Create or modify tests/RunEndRiftWave6RitualLive.ps1
- Create fresh evidence under artifacts/end-rift-v3-evidence/<timestamp>/
- Modify runtime code only when a live failure is reproduced by a new RED test

- [ ] Ensure the script rejects production endpoints and uses at least two bots, with three free-capable participants for swap coverage.
- [ ] Start Wave 6, keep bots outside the seal, assert no capture, then move the lexicographically larger-UUID Bot B into the seal and assert B is captured.
- [ ] Verify capture timestamp, unchanged health at 19.5 seconds, exactly 2 HP drain at 20 seconds, and 1 HP floor with no intensity gain at the floor.
- [ ] Apply representative external damage and verify prisoner health is unchanged between drains.
- [ ] Force each core ability and verify targets exclude prisoner; log sphere/projectile origins within 0.25.
- [ ] Verify 4x4 zone Wither/Slowness/reverse, no Poison/raw damage, prisoner immunity, swap exclusion, and local guard wake.
- [ ] Verify caster damage blocked with 3, 2, and 1 guards and accepted with 0; verify completion clears all transient state.
- [ ] Store logs and markers, then commit evidence/report if fresh source changes require it.

---

### Task 18: Fresh Wave 7 boundary, restart, and cleanup verification

Files:

- Modify or use tests/RunEndRiftWave6Wave7BoundariesLive.ps1
- Create fresh boundary/restart evidence directory

- [ ] Run the boundary live test against the exact current source and artifacts.
- [ ] Verify one-block physical separators, BARRIER collision, separate visual displays, cardinal/diagonal/corner containment, boundary opening isolation, restart rehydration, and open-boundary persistence.
- [ ] Verify natural completion removes barriers/displays, restores original blocks, and leaves no transient entities.
- [ ] If the two-client probe still times out or accepts damage for only one UUID, reproduce with packet/log markers before changing code; add a failing integration regression rather than weakening the timeout.
- [ ] Record unresolved behavior explicitly if the environment prevents a deterministic natural-completion proof.

---

### Task 19: Rebuild and artifact parity

Files:

- CopiMineClient/build/libs/CopiMineClient-0.1.1.jar
- thirdparty/client-mods/CopiMineClient-0.1.1.jar
- copimine-end-event/CopiMineEndEvent.jar
- minecraft/server/plugins/CopiMineEndEvent.jar
- thirdparty/CopiMineMods.zip
- checksum files, public modpack metadata, and resource-pack artifact

- [ ] Rebuild all source-owned outputs from the exact final tree.
- [ ] Compare byte size and SHA-256 for every required pair; recompute, never hand-edit, recorded hashes.
- [ ] Verify checksum files, public snapshot metadata, and resource-pack identity.
- [ ] Run a second clean rebuild where feasible and document intentionally variable metadata.
- [ ] Commit generated parity updates with build: refresh End Rift release artifacts.

---

### Task 20: Native Minecraft acceptance

Files:

- Fresh screenshots/videos only under artifacts/end-rift-v3-evidence/<timestamp>/
- Native evidence manifest/report

- [ ] Confirm the Computer Use/native app surface exposes the actual Minecraft javaw window. If it does not, do not fabricate or recycle old evidence; record REAL MINECRAFT VISUAL VERIFICATION NOT PERFORMED FOR <SHA>.
- [ ] When available, capture boss bind front/side, idle/run, melee/chest/slam/hurt/death/phase poses, debug hitboxes, and rotated-limb projectile hit/miss.
- [ ] Capture all ordinary, elite, guardian, and ritual mob roles, Wave 6 seal/capture/freeze/beams/projectile/zone/reverse/swap/guard shield-break/cleanup, and Wave 7 wall/boundary/restart/cleanup.
- [ ] Check UV stretching, face mapping, mirrored/floating/missing geometry, scale, texture role, head rotation, and vanilla-mob leakage.
- [ ] Process the requested 15-second flight video only if the native window is controllable; describe the screenshot and video in the report.

---

### Task 21: Final review, report, commit, and GitHub handoff

Files:

- docs/end-rift-validation.md
- fresh evidence manifests and reports
- .github or PR metadata only through the normal Git/GitHub workflow

- [ ] Run git rev-parse HEAD, git status --short, git log -1 --oneline, the full End Rift gate, client build, live gates, artifact parity, and git diff --check against the exact final tree.
- [ ] Review the whole branch against the attached checklist; every item must be PASS with fresh evidence or explicitly NOT VERIFIED with a concrete reason.
- [ ] Request whole-branch code review, fix Critical and Important findings, and perform one scoped re-review.
- [ ] Commit the final report and coherent fixes; push codex/end-rift-event to https://github.com/IliaZav/copimine only after local required gates are read.
- [ ] Inspect remote SHA, PR #3, and CI status and attach links to commit, PR, and evidence. Screenshots and videos belong in repository evidence and PR description/comment when GitHub accepts the asset format.
- [ ] Final report must contain exact starting/final SHA, commits, changed files by subsystem, RED to GREEN evidence, command exit results, live markers, artifact hashes, native evidence paths or the literal NOT VERIFIED statement, and every remaining objective risk.
