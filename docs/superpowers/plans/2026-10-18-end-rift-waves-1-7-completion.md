# End Rift Waves 1–7 completion plan

**Branch:** `codex/end-rift-26-3-integrated-final`  
**Starting HEAD for this completion pass:** `02d8d93be3cfd4bb5edcfb8bd9181db9be38c01c`  
**Latest pushed checkpoint:** `2610fe8e` (`origin/codex/end-rift-26-3-integrated-final`)  
**Repository:** `IliaZav/copimine`  
**Environment:** isolated Minecraft 26.3 runtime only; no production worlds or services.

## Goal and acceptance boundary

Complete the approved End Rift Waves 1–7 work and reproduce/fix the defects in the supplied 18 October contract. Preserve existing uncommitted user work. Keep PR #7 in draft. A wave is not called accepted based on policy tests alone: source implementation, automated tests, fresh builds, Paper runtime evidence, and real-client/visual evidence are reported separately. If real Minecraft clients or rendering are unavailable, native and visual acceptance remain explicitly unverified.

Current direct user decisions govern conflicts with older documents: Wave 2 uses glow-only, no shader/particles, plus inverted controls; Wave 7 uses the approved named trials and random distinct trials for 2–3 players, all four in parallel for 4+; generic elites are excluded from Wave 7. The approved Echo carrier/view architecture and per-sender V4 readiness/heartbeat remain fail-closed.

## Initial status matrix (18 October; preliminary until full gates)

| Area | Status | Evidence / unresolved acceptance |
|---|---|---|
| Wave 1 carriers | PARTIAL | Focused carrier regressions pass in the current worktree; transfer/death/restart and two-client visuals still need the final adapter/runtime and native checks. |
| Wave 2 hunt | PARTIAL | Glow-only plus inverted controls are the approved behavior. A fresh audit found production START/STOP argument order and lease ID violated the V4 codec; both are now fixed in the dirty worktree with 69 focused Python checks passing and a signed-envelope Java regression added. Native movement inversion and transitions still need a Minecraft client. |
| Wave 3 portals | PARTIAL | Transactional layer rollback, bounded retries, fail-closed recovery and extracted runtime tests are present. The old 13 October note describing partial portal sets is stale; native model dimensions, orientation, texture and capture/collapse remain unverified. |
| Wave 4 obelisks | PARTIAL | Reflected hit identity/consumption and extracted adapter tests are present per audit. The live runner now permits only the authorized integration branch while preserving local/RCON guards; its regression module passed 21 tests and the fix is pushed as `1e0a9136`. Native projectile timing and F2 visual states remain unverified. |
| Wave 5 fog | PARTIAL | Detached production probes cover three cycles, safe-zone setup retry, block-journal restore failures and checkpoint capture/restart fencing; the boot checkpoint adapter is already in the registered gate. `test_wave5_black_fog_adapter.py` was missing from the gate and has now been added. Native fog, damage, sound and safe-zone readability remain unverified. |
| Wave 6 ritual | PARTIAL | Full event gate and 97 focused Wave 6 checks pass. A reproduced projectile-admission defect was fixed in `2610fe8e`: only a live, current-generation, registered ritual guard in Wave 6 combat can fire; stale/unregistered guards and the prisoner remain denied. Paper runtime and native HUD/sphere/targeting still require verification. |
| Wave 7 named trials | PARTIAL | Named runtime, distinct room selection for 2–3 players, owner Echo duels, snapshot, barrier and recovery probes pass the event gate. Authored Warden, Archmage and Marksman client models/textures compile, but signed bindings, cold restart, live hitboxes/combat, full return and boss spawn still require native verification. |
| Shared squad AI / elite projectiles | PARTIAL | Existing coordinator and large integration diff need source-to-runtime audit against the supplied hive contract; no native grouped-combat or projectile pass-through proof yet. |
| V4 client bridge | PARTIAL | Protocol implementation and tests are in the branch; local signer provisioning and real client/server handshake/heartbeat are not verified. Private signing key must remain external and unread. |
| Cross-plugin item fixes | AUTOMATED CHECKS PASS | Artifact catalog, source and rebuilt JAR checkpoint is included in the pushed branch. The full local gate rebuilt first-party plugins; item-loss and catalog regressions passed. Resource-pack builder produced SHA-1 `db0aecd82add0f7a0c3f93449aab390969416b6a`. GitHub checks for the new head remain pending. |
| Resource pack / build provenance | AUTOMATED LOCAL GATE PASS | The fresh End Rift gate completed with exit 0, including Paper 26.3 plugin builds, Fabric 26.3 client build, modpack packaging, resource-pack packaging, contracts and recovery tests. Remote CI must still be checked on the final pushed SHA. |
| Isolated test server | RUNNING WITH PREVIOUS LOADED JAR | Full locked-plugin Paper 26.3 server started on `127.0.0.1:25566` with AuthMe/AuthEffects and the isolated PostgreSQL service. It reports End Event services ready. Its current process loaded the pre-fix End Event JAR. The original exec session has closed stdin and local RCON is disabled, so a safe stop/restart was not available in this pass; do not claim the new JAR is live until the user stops this process and it is restarted from the verified plugin candidate. |
| Native 2-player, 5–6-player and visual acceptance | UNVERIFIED | No valid current-client F2/video evidence is recorded. Bots/harnesses do not substitute for rendered-client acceptance. |

## Work sequence

1. **Baseline and source-of-truth audit:** verify local/remote SHA, dirty/untracked inventory, other relevant worktrees, PR #7 and CI; finish reading the approved and historical wave/bug documents; record stale versus current findings. Do not reset/clean, overwrite unrelated changes, or touch production.
2. **Plan and owner boundaries:** keep `CopiMineEndEvent.java` single-owner. Wave 6 READY-gating change is complete; pass that file to one next owner at a time. Read-only audits can run in parallel. No concurrent builds that replace shared outputs.
3. **Reproduce and fix current acceptance gaps:** the Wave 2 V4 START/STOP wire-up defect is now reproduced and fixed; include the new codec-wiring test. Keep the Wave 5 cycle adapter in the registered gate. Check Wave 7 actor visuals against the approved spec, confirm that tested selection policy and production selection cannot drift, and resolve any remaining Echo resource/cooldown or barrier gaps only when reproduced with regression proof.
4. **Cross-wave and artifact defects:** validate the resource-pack source map, rebuild first-party plugin candidates from exact sources, make Windows installer tests locale-independent while retaining filesystem-integrity checks, and reproduce/fix only the scoped Signal Bell, Gravedigger Contract/command, Berserker Heart, and relevant guard-projectile findings that still exist.
5. **Tests and fresh builds:** run targeted RED/GREEN tests for each changed boundary; run the complete End Rift event gate, relevant artifact/migration suites, pinned Java 25/Paper 26.3 plugin build, Fabric 26.3 client tests/JAR, resource-pack build and asset/provenance validators. Do not run redundant builds concurrently.
6. **Server and review:** install the verified candidate plugins into the isolated local runtime, start Paper bound to loopback with a 20-player test cap, external signer key path only in the child environment, and confirm plugin receipt/startup/readiness. Run CodeRabbit, Codex Security and available PR CI diagnostics on the final exact diff. Investigate findings against source and tests.
7. **Publish checkpoints:** stage only reviewed End Rift, migration/client, resource-pack and specifically authorized cross-plugin files. Exclude `.serena`, `CopiMineClient/bin`, pytest temp folders, javac argument files, crash logs, worlds and secrets. Commit focused checkpoints and push normally to the already authorized integration branch; never force-push or merge PR #7.
8. **Native acceptance:** use current official live runners where branch-safe; then connect real Minecraft 26.3 clients for two-player and 5–6-player checks and collect F2/video evidence with source/server/client/pack hashes. Keep visual/native states UNVERIFIED until actual evidence exists.

## Current known owners and safety

- The Wave 6 agent completed its code/test changes and has released `CopiMineEndEvent.java`.
- Read-only audits found the Wave 2 production V4 field-order/UUID defect (fixed locally), no reproduced Wave 3/4 mechanics defect but native visuals outstanding, and confirmed Wave 5 cycle plus restart adapters; only the cycle adapter had been omitted from the gate.
- Infrastructure audit is read-only and checking current PR/build/resource provenance.
- Other dirty changes in the shared worktree are preserved. No broad staging, reset, cleanup, world deletion, secret read, production deploy or PR merge is allowed.

## Verified progress checkpoints

- `f24bc8f78795db043ccbf00ab9ac0295fb52d965`: Wave 2 source documentation aligned with the approved glow-only/reversed-control behavior; focused regression test passed (3 passed); pushed to `origin`.
- `1e0a9136`: Wave 4 obelisk live-runner branch allowlist repaired with local-only guards retained; focused runner regression module passed (21 passed); pushed to `origin`.
- `363e30708aa843226c7d2cb9873ed1f5329b0b50`: the Windows installer regression now checks that a locked prior client leaves the profile filesystem unchanged, without matching a localized OS error string; focused test passed (1 passed); pushed to `origin`.
- Focused Waves 1–6 Python regression selection: 241 passed with 4 existing Pillow deprecation warnings.
- `2610fe8e`: Wave 6 ritual guard projectile admission fix with extracted production-adapter regression cases for valid guard, stale generation, foreign event session, unregistered guard, intermission and prisoner. Focused Wave 6 group: 97 passed; lifecycle adapter module: 42 passed.
- Fresh `tests/RunEndRiftEventChecks.ps1` on the final source tree: `GATE_EXIT=0`; the run built all first-party plugin candidates, CopiMineClient, the 26.3 Fabric client, modpack and resource pack, then ran the registered End Rift, migration, artifact, client and recovery checks. See the local gate log under `%TEMP%\end-rift-gate-final-164c9b7171864857b5edd583ee9c4b40.log`.
- The local Paper server is listening on `127.0.0.1:25566`; startup log confirms `Done (39.933s)` and `CopiMineEndEvent services ready`. This is boot evidence for the older installed JAR, not runtime proof of commit `2610fe8e`.
- CodeRabbit, Codex Security and GitHub PR checks have not yet been run against the final implementation commit; record their results before calling review complete.

## Reporting rule

For each defect record: current reproduction, root cause, changed files, regression test and actual result, commit SHA, source status, automated status, Paper/native status, visual status and remaining risk. Final report must be in Russian and distinguish FIXED IN SOURCE, AUTOMATED TEST VERIFIED, NATIVE MINECRAFT VERIFIED, VISUAL VERIFIED and NOT VERIFIED/BLOCKED.
