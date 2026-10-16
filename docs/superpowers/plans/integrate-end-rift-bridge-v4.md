# Plan: Integrate End Rift 26.3 and Client Bridge V4

## Goal
Integrate accepted End Rift Waves 1–7 and Client Bridge V4 work onto `codex/end-rift-26-3-integrated-final`, preserve source history, fix reproduced defects, and leave verifiable evidence for the exact final SHA.

## Binding inputs and decisions
- End Rift source of truth: `origin/docs/end-rift-26-3-complete-spec-20261013` at `640e906`, including `docs/end-rift/final-26.3/` and linked Echo/return/Kagune acceptance docs.
- Bridge V4 source: `codex/client-bridge-v4` at `fadca2a` plus relevant dirty changes. Ed25519 key ID `copimine-bridge-20261015-v1` and public key are approved. Never open, copy, print, log, or commit the external private key.
- Client trust is CopiMine servers only; fail closed if the signing key is unavailable. Verify exact packet body and all bound session/event/player/generation/stream/counter/time fields.
- The user approved Wave 7 room selection: for 2–3 players, create the same number of rooms and randomly sample distinct trial types from the shared pool without replacement; all selected rooms run in parallel. For 4+ players, run all four distinct trials in parallel and distribute additional players. Persist the selected layout so reconnects or restarts cannot reroll it. The official personal Echo duel remains deferred; the disposable Echo presentation probe is not an official trial or completion receipt. Existing receipt fields or codec-only tests are data-state coverage and do not establish duel acceptance. Unsupported selected trials must fail closed into recovery, so Wave 7 remains incomplete until their official actors and outcomes are implemented and verified.
- Controls Q/W/E/R; no runes after Wave 7; immediate full heal and 30% maximum-durability restoration; exactly one 40-second boss transition; Kagune guardian cooldown 90 seconds.
- Preserve existing dirty worktrees. Do not modify production worlds, player inventories, PostgreSQL, or unrelated plugins/binaries.
- The latest user authorization requires regular local commits and pushes to `origin/codex/end-rift-26-3-integrated-final`. Never force-push, overwrite other branches, merge to `main`, or deploy. Create a draft PR after reviewed integration if branch protections and evidence permit.
- Keep evidence labels distinct: source change, automated test, CI, isolated runtime, native Minecraft visual proof. Native verification stays NOT VERIFIED without real client screenshots at exact artifact SHA.

## Global constraints
- Use the real repo `D:\Desktop\Copimine\copimine-main` and isolated integration worktree `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-26-3-integrated-final`.
- Use selective staging only; exclude `.serena`, local auth files until reviewed, generated binaries without provenance, and unrelated dirty files.
- Preserve both original feature branches and resolve `CopiMineEndEvent.java` conflicts semantically.
- Confirm each reported bug at current SHA and use RED/GREEN for fixes. Existing tests are not proof until run.
- Pin/verify Java 25.0.4.1, Python 3.13, candidate artifact size/hash, resourcepack hashes, and full required gates. Do not bypass hashes or make gates optional.

## Task 1: Inventory, source contract, and checkpoint manifest
Recheck all worktree/branch/remote state; classify every dirty tracked/untracked path by source, tests, docs, assets, generated artifact, local metadata, or unrelated change. Review the canonical specs, Bridge V4 plan/spec, CI run `38703734445`, and exact ancestry. Produce a selective commit manifest; preserve original worktrees untouched. Push only verified commits from the integration branch.

## Task 2: P0 mob AI coordinator lifecycle
Reproduce the stale-generation reconciliation on base `a703c921` with a base-compatible focused test, then verify the current lifecycle fix in the End Rift source worktree. Cover fog clearing, empty/intermission phases, eligible cohort start/resume, and no stale lease/task effects. If the dirty branch already contains the complete fix, commit it selectively after tests and independent review; do not rewrite working code without evidence.

## Task 3: Kagune guardian execution lifecycle
Use the accepted KAGUNE_90S matrix at the executing tick/restoration boundary: independent 90-second cooldowns, exact warning time, 15-second guaranteed vulnerability without shortening cooldown, shield count equal to living slots, WorldGuard cancellation, HIT_RECOVERY and forbidden phases, 1800-tick config validation, generation-safe persistence/restart, and no respawn after boss death. Add/verify RED/GREEN tests at the runtime adapter, not policy-only.

## Task 4: Wave 7 recovery and deferred unsupported trials
Add a regression for legacy schemas that retain WAVE_7 with missing or inconsistent named-trial progress, and fail closed into RECOVERY_REQUIRED without renaming legacy RIFT_REFLECTION/JUGGERNAUT/RIFT_HUNTER snapshot identifiers. Preserve the approved 2–3-player distinct random room sample and all-four-trials layout for rosters of four or more, and persist the selected layout so reconnects or restarts cannot reroll it. The official personal Echo duel remains deferred: do not add duel actors or combat, and do not revive the shared-Warden-plus-two-Echo layout. Existing receipt-only state bookkeeping is not an official duel or accepted completion. Keep unsupported selected trials fail-closed in RECOVERY_REQUIRED and report Wave 7 as incomplete until each selected official trial has an implemented actor and validated outcome. Never interpret a legacy Reflection as Echo or treat the disposable Echo presentation probe as trial completion.

## Task 5: Waves 1–6 and shared mob/elite regressions
Audit and reproduce accepted bugs before edits: stable squad assignment, Void Burst, Arrow Rain telegraphs, Spider Web Shot root/poison, arrow/explosion damage, WorldGuard cancellation, attribute compatibility, portal layer retry/rollback, Wave 2 inversion lifecycle, Wave 4 bounded objective dispatch, Wave 5 fog AI recovery, Wave 6 eligible casters/roles and cleanup. Fix only confirmed gaps; verify generation/phase fencing and resource cleanup.

## Task 6: Client Bridge V4 and deterministic dependency builds
Integrate exact-body Ed25519 envelopes, pinned key/key ID, connection nonce, player UUID, event/generation/stream/counter/timestamp/expiry, replay protection, readiness, server switching, and v3 mutation rejection. Keep signer loading external and fail closed. Run signing, sender, reconnect, mismatch, replay, fallback, and readiness tests. Resolve music position alignment and stabilize direct PowerShell Paper dependency selection without weakening Gradle/pinned-artifact verification.

## Task 7: Pinned migration artifact CI failure
Reproduce the failure from GitHub run `38703734445`, trace candidate build inputs/outputs at component boundaries, and identify actual-versus-pinned size/SHA. Correct source/artifact provenance; keep all hashes and gates required. Report source build output hash and byte count.

## Task 8: Integration and full verification
Checkpoint reviewed changes on feature branches; integrate End Rift, Bridge V4, and canonical docs into this branch with traceable history. Run Python 3.13 focused/full suites, Java 25.0.4.1 client tests/build/JAR, plugin build, resourcepack/asset guards, migration candidate preparation, full End Rift/Bridge gates, GitHub CI, CodeRabbit and Codex Security. Attempt isolated Paper/Fabric 26.3 tests without production data. Capture screenshots only if real native client access exists; otherwise report NOT VERIFIED and provide manual steps.

## Task 9: Final branch, publish, and report
Review every changed/staged file, run final checks against the committed SHA, push verified checkpoints and final commits to the authorized integration branch, create a draft PR if reviewable, and report commit/branch/PR links plus remaining blockers in Russian. Do not force-push or merge to main.

## Task interface/conflict scan
| Tasks | Shared file/interface | Scan and ruling |
|---|---|---|
| 2 + 3 | `CopiMineEndEvent.java`, coordinator/tick lifecycle | Both touch the event coordinator. Keep P0 lifecycle and Kagune state separate in commits; integrate sequentially and review exact hunks. |
| 3 + 4 | Main event tick, entity lifecycle, snapshot | Keep guardian persistence/state separate from Wave 7 trial snapshots; generation and phase fences remain mandatory. |
| 4 + 5 | Official wave dispatch, projectile and AI admission | Wave 7 owns trial identity/routing; shared AI owns common targeting/projectile admission. Legacy three trials cannot remain official. |
| 4 + 6 | End Event client binding/visual recovery | Server remains gameplay authority; bridge readiness/replay must not reset gameplay or resurrect expired Wave 7 state. |
| 6 + 7 | Build locks, candidate JAR, SHA/size gate | Dependency stabilization is not permission to alter the pinned artifact digest without proving the rebuilt bytes. |
| 1 + all | Worktree commits, lockfiles, generated artifacts | Inventory first, selective staging only, no reset/clean. Generated binaries are excluded until source build and hashes are independently verified. |

## Self-consistency scan
| Task | Internal consistency check | Ruling |
|---|---|---|
| 1 | Source paths include 121 dirty tracked and 91 untracked files; staged manifest must be explicit. | Inventory each changed path; do not stage all. |
| 2 | Test must fail semantically on base, not just fail extraction because a helper signature is new. | Treat the current failed RED attempt as inconclusive; construct a base-compatible reproduction before completion. |
| 3 | Runtime tick/restart tests can distinguish policy timer values from actually scheduled respawn. | Policy-only evidence cannot close the task. |
| 4 | The room-selection clarification approves distinct random trials for 2–3 rooms and all four trials for 4+ players; a later decision defers the official personal Echo duel. | Keep no-repeat room sampling and persistence. Remove the unused shared-Warden-plus-two-Echo scaffold. Unsupported selected trials remain fail-closed, and Wave 7 is not complete while selected trials lack implemented actors and outcomes. |
| 5 | Each regression requires current-source reproduction and a covering test. | Unconfirmed items remain open and are never reported fixed. |
| 6 | Security properties are exact; music availability must not depend on V4 readiness. | Preserve fail-closed security while testing independent audio/recovery behavior. |
| 7 | Job failed after successful Gradle build at digest/size comparison. | Instrument the true compared bytes and repair provenance; no allowlist/skip bypass. |
| 8 | Required gates include versions/native evidence that may be unavailable. | Run valid available commands; label unavailable proof as blocked, never substitute weaker evidence. |
| 9 | User explicitly authorized local commits and pushes to this branch only. | Push only after fresh verification and exact staged-diff review; no force push/main merge/deploy. |
