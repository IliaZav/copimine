# End Rift Wave 7 Named Trials Implementation Plan

## Goal

Implement and safely enable the four official Wave 7 named trials described in `docs/end-rift/final-26.3/WAVE_07.md` and the approved design `docs/superpowers/specs/2026-10-16-end-rift-wave7-named-trials-design.md`. Keep the Wave 7 room assignment deterministic after selection, make each trial generation-scoped and restart-safe, and preserve the existing fail-closed launch gate until every selected trial has a production runtime.

## Invariants

- For 2–3 owners, create one room per owner and select distinct trials without replacement from the four-trial pool. For 4+ owners, run all four trials in parallel; distribute extra owners evenly. Preserve the persisted selection across reconnect/restart.
- Echo uses the approved server carrier plus vanilla client player view. Every owner in an Echo room has a private duel cell and separate owner-bound Echo state. Only that owner may damage or be damaged by that Echo. Legacy Reflection is not an Echo completion.
- A personal Echo receipt requires server confirmation that the exact owner-bound actor for the current event, runtime generation, attempt generation, and duel died. A stale actor, stale callback, or prior receipt cannot complete a duel.
- Never silently substitute unsupported actors, items, attacks, or trial types. Reject an unsafe active item or corrupt/missing required checkpoint and enter the existing recovery path.
- Trial boundaries stay transparent, physically impassable while closed, and owned by the event journal. Opening one passage must not leave opaque collision or an uncommitted partial opening; full cleanup restores the exact pre-event block state.
- Keep this work scoped to Wave 7 runtime and its explicitly required shared lifecycle/barrier paths. Preserve pre-existing dirty files and generated artifacts outside the scoped source/doc/test paths.

## Work sequence

### 1. Establish runtime seams and baseline

- Map the official Wave 7 start, tick, damage, target, completion, persistence, restore, cleanup, and recovery routes for each named trial.
- Reconcile production room assignment with the tested layout policy so there is one source of truth.
- Record the current branch/HEAD and verify the protected dirty-file list before edits.
- Run focused existing Wave 7 and boundary tests to capture baseline behavior, including the pre-existing separator contract test. Do not weaken failing acceptance contracts to obtain a green baseline.

### 2. Make barrier ownership and opening transactional

- Capture every planned separator cell, including pre-existing full blocks, in the event-owned mutation journal before replacing it with the transparent impassable barrier representation.
- Preserve original block data across passage opening, rebuild, final-seal reclose, restart, and full cleanup. A successfully opened passage becomes physically clear while retaining the original block snapshot until cleanup.
- Make passage opening all-or-rollback so a failed mid-operation cannot leave a physical gap without a committed logical passage.
- Add regressions for pre-existing walls, shared boundary cells, open/rebuild/reclose, restart, exact restore, and injected failure partway through opening.

### 3. Implement named-trial production runtimes

- Implement and connect Warden, Mobile Marksman, and Rift Archmage adapters to the named trial controller. Each adapter must have generation-fenced spawn/tick/target/damage/completion/cleanup/restore behavior and visible telegraphs that correspond to server-side attacks.
- Keep attacks within the assigned room and route damage/projectiles through existing owner and chamber separation policies.
- Implement Echo using the approved carrier/view design, bounded profile-derived behavior, an allowlisted finite loadout, per-owner duel cells, owner-only combat admission, persisted duel state, and death-verified owner receipts.
- Handle Echo appearance and actor replacement after restart without rerolling the persisted trial, owner behavior, or remaining finite resources. Rebind the new actor UUID only to the current duel attempt and runtime generation.
- Keep `RECOVERY_REQUIRED` as the gate for any selected trial without a complete runtime; remove no guard until all four production routes are registered and verified.

### 4. Persist and recover the whole Wave 7 attempt

- Validate event ID, roster, room/trial map, owner-to-duel map, runtime/attempt generation, actor ownership, actor state, finite inventory/cooldowns, completion receipts, and open-passage state before resuming.
- On a missing, malformed, stale, or unsupported snapshot, cleanly enter recovery and do not partially spawn or issue completion receipts.
- Preserve the existing return/death deadline behavior and ensure owner disconnect/reconnect does not grant a new window or another owner's duel authority.

### 5. Automated verification and review

- Add focused unit tests for selection/no-repeat layout, each runtime adapter, Echo owner isolation/completion, finite inventory, profile fallback, generation fencing, snapshot validation, and transaction rollback.
- Add plugin-level integration probes for 2, 3, 4, 5, 6, and 10 owners; include multiple Echo owners in one room, reconnect, server restart, corrupt state, simultaneous trial completion, and shutdown cleanup.
- Run the Wave 7 and event boundary suites, the full End Rift event check runner, relevant Paper/client builds, and the repository-required CI checks. Record exact commands, exit codes, and artifact revisions.
- Run CodeRabbit, Codex Security, and Code Review on the completed diff. Validate each actionable report against source and add a regression for each confirmed fix. Do not report an unchanged V4 scan as coverage of Wave 7 changes.

### 6. Native Minecraft 26.3 acceptance and publish checkpoints

- Verify on the actual 26.3 client and Paper server with 2 owners, then with 5–6 owners. Confirm each named trial is visible, separated, completable, and recoverable; check the exact Echo appearance, owner-only attacks, finite item use, wall collision/opening, death/reconnect, and restart behavior.
- Save native screenshots/log evidence and record the exact server plugin, client profile, resource pack, and source commit SHA used. Keep `nativeVerified=false` until this evidence exists.
- Commit only reviewed scoped files in small checkpoints after the corresponding checks pass; push each verified checkpoint to `origin/codex/end-rift-26-3-integrated-final`. Never force-push, change other branches, publish secrets, or include generated runtime worlds/artifacts/unrelated dirty files.
- Create a draft PR only after the integration diff and target branch are deliberate and the published branch is reviewable.

## Completion criteria

- All four official named trials launch through the same persisted production selection and work for the required roster sizes without falling back to legacy actors.
- Echo receipts are owner-, duel-, attempt-, and generation-bound and are granted only after server-confirmed death of the correct actor.
- Closed boundaries are transparent and collision-safe; opened passages are clear; cleanup/restart restore or rebuild exact owned state without unjournaled gaps.
- Focused suites, full event checks, relevant builds, and requested reviews pass or have a precise documented blocker.
- Native 26.3 evidence is attached to the exact published commit; otherwise the report clearly marks native verification incomplete.
