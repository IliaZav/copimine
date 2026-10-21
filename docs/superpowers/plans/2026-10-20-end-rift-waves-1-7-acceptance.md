# Plan: End Rift Waves 1–7 Implementation and Acceptance

## Goal

Repair the confirmed End Rift defects against the current Minecraft 26.3 source of truth, implement the newer master-task mechanics where they supersede older wave documents, and collect reproducible evidence without overstating what source tests prove.

## Architecture

Keep gameplay authority in the Paper plugin and presentation in the Minecraft 26.3 Fabric client. Use bounded pure policies for timing, scaling, eligibility, capacity, cooldowns and targeting; connect them through the existing event adapter with generation-fenced ownership. Preserve the existing arena and Rift Guardian/Kagune encounter. Keep runtime, protocol, asset and native-visual evidence separate.

## Tech Stack

- Java 25.0.4.1 for the pinned Minecraft 26.3 server plugin and client toolchain.
- Gradle 9.8.1 for `tools/minecraft-26.3/client`.
- Python 3.13 for repository pytest suites.
- PowerShell for documented gates and local runtime setup.
- Paper 26.3 plus protocol bots for live acceptance when the isolated PostgreSQL dependency is available.

## Spec

The supplied master task is the highest-priority contract. It replaces the old Wave 2 20-second/inverted-controls design with a 45-second mark and no input/camera inversion, and replaces Gravity Well/Rift Chains with Gravity Anchor/Falling Anvils. It requires fair Wave 5 safe-zone capacity, actual gameplay-path tests, 2/5/10/11/20-player coverage where feasible, exact artifact locks, security/review evidence, and an explicit native F2 checklist. The existing final-26.3 documents remain applicable where they do not conflict with the master task.

## Global Constraints

- Preserve the requested source-of-truth branch and all pre-existing untracked or dirty work. Never reset, clean, blindly overwrite or mass-commit.
- Do not modify the Rift Guardian boss encounter, its model, animations, phases or Kagune/tentacle mechanics.
- Do not redesign the arena or add unrelated systems/migrations.
- Do not use GUI/computer-control tools or an interactive Minecraft client.
- Do not claim native visuals from headless, source, policy, asset or protocol evidence.
- Do not weaken checks or suppress failures to make a gate green.
- Do not claim live server acceptance without a working isolated Paper runtime and protocol-bot evidence.

## Review Focus

- Wave 2 mark timing, fair rotation, two-mark minimum, bounded reinforcement, client cleanup, no input inversion, and stale generation handling.
- Wave 5 safe-zone capacity against current eligible roster and journal rollback/fail-closed behavior.
- Wave 6 spell progression, physical ownership, warnings, movement counterplay, prisoner exclusion, exactly two 5×5 volleys for 11–20 players, and cleanup.
- Wave 7 actor persistence, physical isolation and mutation-journal recovery.
- Artifact hashes and toolchain alignment; preservation of unrelated files.
- Separate source, automated, Paper/bot, protocol, resource-asset, CI, security/review and native-visual claims.

## Tasks

- [x] **1. Establish checkout and evidence baseline.** Verify branch, HEAD, remotes, linked worktrees, tracked/untracked state, existing local artifacts and known runtime blockers. Read the complete supplied master task and applicable End Rift specifications.
  - Paths/evidence: repository Git metadata; `docs/end-rift/final-26.3/`; local runtime and artifact locks.
  - Checks: `git status --short --branch`, `git worktree list`, `git log`, `git diff --check`, SHA-256/size inspection.
  - Result: branch `codex/copimine-source-of-truth-20261020`, HEAD `04a006c1f295650b055cfef1cab22e82f90effb8`; pre-existing untracked `.serena/` and `CopiMineClient/bin/` preserved. Local database/runtime services are unavailable; do not start bots until dependencies are safely available.

- [ ] **2. Replace obsolete Wave 2 behavior.**
  - Paths: `copimine-end-event/src/me/copimine/endevent/domain/WaveTwoHuntPolicy.java` (or equivalent new policy), `CopiMineEndEvent.java`, client control/state lifecycle only if needed, focused Java/Python tests.
  - Implement 45-second marks, fair eligible-player rotation, at least two completed mark periods, first-two-period pressure batches, bounded 1–2 later reinforcements, no input reversal/camera change, and cleanup on every lifecycle edge.
  - Tests: timing boundaries, 2/3/5/10-player rotation, death/quit/reconnect fallback, pressure cap, bridge/client cleanup and stale generation. Keep 36-player/elite composition unchanged unless runtime evidence or a clear contract establishes otherwise.

- [ ] **3. Make Wave 5 safe-zone activation capacity-safe.**
  - Paths: new pure capacity policy and Java tests; `CopiMineEndEvent.java` adapter; `tests/test_wave5_black_fog_adapter.py`.
  - Count distinct usable emerald cells after collision/bounds filtering and require sufficient capacity for every eligible surviving player before journal apply or fog transition. Roll back partial setup and retry/fail closed.
  - Tests: 1/2/5/10/20-player capacity, partial/overlapping/out-of-bounds zones, journal preparation/apply/restore failures, exact original block restoration.

- [ ] **4. Preserve Wave 4 mechanics and reconcile the stale recovery test.**
  - Paths: `EncounterGenerationPolicy.java`, `CopiMineEndEvent.java`, `tests/test_end_rift_wave4_pressure_retries.py`, focused generation/recovery tests.
  - Verify that transient recovery advances the generation with overflow-safe policy, removes only stale owned resources, and fails closed. Update an outdated test assertion only if the current policy is demonstrably equivalent and stronger; do not change correct runtime code to satisfy an obsolete string check.
  - Checks: focused policy, recovery and W4 tests; preserve 3 HP, legitimate reflection ownership and non-homing projectile behavior.

- [ ] **5. Check Wave 1, 3, 6 and 7 against the master-task deltas.**
  - Paths: current event source, `tools/minecraft-26.3/client/`, focused wave tests and policies.
  - Wave 1: assess increased bounded melee pressure and delivery cleanup.
  - Wave 3: verify portal order/capture/emergence and Giant entry only at portal two.
  - Wave 6: inspect startup, guard ownership/AI, targeting, ability progression, Gravity Anchor and Falling Anvils; migrate saved identifiers safely.
  - Wave 7: verify deterministic room assignment, actor checkpoint/recovery and barrier journal invariants.
  - Add a failing behavior test before each confirmed code change; do not treat legacy 1.21.1 client tests as 26.3 evidence.

- [ ] **6. Validate the active 26.3 client and resource pack.**
  - Paths: `tools/minecraft-26.3/client/`, `tools/minecraft-26.3/protocol/`, `resourcepacks/`, candidate profiles and artifact locks.
  - Run pinned client tests/build, validate pack structure and referenced assets, compare actual artifact size/hash against profile locks. Keep legacy build outputs intact.

- [ ] **7. Run applicable automated gates and reviews.**
  - Paths: `tests/RunEndRiftEventChecks.ps1`, relevant Python suites, Java policy/runtime tests, 26.3 Gradle client tests, bridge integration and security/config checks.
  - Audit runner version/path assumptions before invocation; do not let the legacy aggregate runner overwrite 26.3 artifacts or count a 1.21.1 build as 26.3 acceptance.
  - Capture exact commands and exit codes. Inspect all CodeRabbit/Codex Security findings where callable; document unavailable review or CI integrations honestly.

- [ ] **8. Run isolated Paper and protocol-bot acceptance if dependencies permit.**
  - Required matrix: 2/5/10/11/20 participants, wave-specific natural gameplay and restart/failure cases. Use loopback only, pinned server/plugin/client artifacts, and collect logs, telemetry and performance.
  - Current blocker: local PostgreSQL is absent and prior approved runtime acquisition was policy-blocked. Recheck only existing local services/files; do not bypass the blocker or install arbitrary production tooling.

- [ ] **9. Produce evidence and commit only intended changes.**
  - Paths: repository diagnostics/report conventions plus readable Markdown and machine-readable evidence; `docs/end-rift/final-26.3/` only where contract clarification is necessary.
  - Include final source SHA, commands/exit codes, artifact hashes, wave-by-wave status, test/bot matrix, review/security/CI result and exact blockers.
  - Include the requested manual F2 checklist. Verify `git diff --check`, staged paths, and final status; commit only intentional task files. Keep all unverified native visual claims marked pending.
