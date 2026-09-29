# End Rift Tentacle Forensics and Regression Plan

> **For agentic workers:** Execute the checks in order and keep the change limited to tentacle client/server behavior. Do not commit or push.

**Goal:** Verify the supplied Kagune assets against the current client package, then fix only tentacle idle/throw behavior reproduced by automated or local combat evidence.

**Architecture:** Use the exact `D:\Downloads\Telegram Desktop\kagune.rar` source as the asset authority and compare its entries to the built client JAR. Characterize idle sampling with real imported animation data. Use the local Last Seal harness to measure player health and post-release movement; if that test fails, trace the exact policy path and add a failing policy regression before changing server code.

**Tech Stack:** Java 21, Fabric client JUnit tests, Paper/Purpur local server, Mineflayer bot, PowerShell/RCON.

**Spec:** Parent instructions in the current task: `kagune.rar`/current package audit; tentacle-only scope; no shield or ordinary mob edits; no commit/push; no live-visual completion without a fresh client screenshot and real player-damage verification.

## Global Constraints

- Preserve all existing dirty/untracked worktree state; no revert/reset or cleanup.
- No code changes to shield or ordinary mob models.
- No production code before a failing behavior test.
- Use the already-running local server only if the parent-approved combat harness can safely restore it; do not start/restart Paper in this task.
- The old 04:03 probe predates the 04:42 source and 04:51 plugin JAR; do not treat it as evidence for the newer server build.
- No commit or push.

## Review Focus

- Idle curve at the authored 2.0-second turn: compare incoming/outgoing angular velocity from the real imported clip; assert a bounded velocity change at the key.
- Current client package fidelity: compare the RAR model/texture entries and built/thirdparty client JAR entries by SHA-256 before attributing color or animation to code.
- Player damage masking: the active harness must not grant Resistance or Regeneration and must observe a real player health decrease around the throw release.
- Throw impulse: the active harness must capture player movement/velocity after release and reject vertical-only fallback or sub-threshold horizontal motion.
- Encounter state: perform damage/throw checks only in official `BOSS_ACTIVE`/`LAST_SEAL`, never in static `COLLECTING` showroom state.

### Task 1: Establish supplied Kagune asset and package identity

**Files:** Read-only: `D:\Downloads\Telegram Desktop\kagune.rar`, `CopiMineClient/src/main/asset-source/end-rift-tentacle/kagune.bbmodel`, `CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json`, client JAR and texture resources.

- [x] Compare source archive model, texture, and animation entries with extracted and packaged assets by SHA-256.
- [x] Record whether renderer color/tint code applies any red overlay.

### Task 2: Characterize imported idle temporal continuity

**Files:** Test `CopiMineClient/src/test/java/me/copimine/client/KaguneModelImporterTest.java`; production `CopiMineClient/src/main/java/me/copimine/client/KaguneModelImporter.java` only if the test exposes a real defect.

- [ ] Add a focused test using the loaded `idle` clip that estimates the same rotation channel's velocity immediately before and after the 2.0s key; assert the bounded discontinuity implied by a smooth authored turn.
- [ ] Run the focused client test and record RED/GREEN. If current sampling already satisfies the contract, leave production untouched and report the user-visible claim as unconfirmed pending fresh client capture.
- [ ] If RED, change only the idle interpolation path, then rerun the focused importer and tentacle model tests.

### Task 3: Measure player-facing throw and damage in official Last Seal

**Files:** Live harness `tests/RunEndRiftTentacleLive.ps1`, Mineflayer probe `tests/LocalEndRiftMobCombatBot.js`, existing pure test `tests/TentacleThrowPolicyTest.java`; server production path only if the live regression fails.

- [ ] Remove the harness's Resistance/Regeneration masking and add assertions for player health delta and post-release horizontal movement.
- [ ] Run the probe against the already-running official Last Seal process/JAR; record exact loaded artifact hash, encounter phase, damage before/after, and measured movement. Do not infer combat behavior from `COLLECTING` showroom output.
- [ ] If the throw assertion fails, add a pure policy test that reproduces the actual unsafe-landing/fallback condition and watch it fail before changing the policy/release path.
- [ ] Rerun the policy suite and combat harness. A passing server probe does not complete the visual requirement; a fresh client screenshot remains required.
