# End Rift V3 release hardening plan

## Baseline and release boundary

- Repository: `IliaZav/copimine`
- Branch: `codex/end-rift-event`
- Baseline: `e0fd48cba1efa4026ef5c02027eda469f8a4f4c2`
- Release evidence must distinguish source/contracts, Paper runtime, GitHub Actions, and native Minecraft client observation.
- No production server or production resource-pack endpoint will be changed as part of this local hardening pass.

## Current audit findings

The baseline has a passing local End Rift gate and a successful two-player accelerated Paper run, but it is not release-proven. The following items are the first implementation targets:

1. `EndEventStateMachine` accepts any fresh idempotency key and does not remember keys. Duplicate transitions can therefore repeat adapter side effects.
2. `EndRiftEncounterCoordinator` changes the graph before starting an encounter and completes the encounter before committing the graph transition. A failure between those operations can leave graph and encounter state inconsistent.
3. `EncounterResourceScope.close()` swallows closer failures; `EndRiftSession.close()` swallows the scope failure. Cleanup failure cannot reach the lifecycle owner.
4. `AttemptLifecycleController.abortWipe()` clears the frozen flag. A failed wipe can resume the old attempt instead of remaining frozen for retry/recovery.
5. `EventTaskRegistry` accepts non-positive generations and has no explicit execution-time guarded callback helper.
6. `DepositJournal` is still a six-field attempt-unscoped ledger. Current schema-4 requirements need event id and generation ownership plus explicit legacy handling.
7. The main Bukkit class is still 20k+ lines. Extraction must be incremental and correctness-driven; no rewrite-only refactor is planned.
8. Native client visual verification and complete 3/10/20-player full Paper runs are unavailable in this environment and must remain `NOT VERIFIED` unless a real client/runtime is observed.

## Phase 1 — contracts and failing tests (TDD)

Add focused regression tests before each implementation change:

- transition idempotency and conflicting-key rejection;
- coordinator start/complete atomicity and rollback on injected failure;
- cleanup failure aggregation and propagation through session/lifecycle;
- failed-wipe freeze and retry;
- positive task generation and stale callback fence;
- ordered persistence saves;
- legacy combat snapshot migration to recovery;
- generation-scoped deposit journal;
- hazard duplicate-cell ownership/conflict.

Each test must be demonstrated RED against the baseline, then made GREEN by the smallest compatible implementation change.

## Phase 2 — lifecycle/recovery hardening

- Make state transition identity durable within a state-machine instance and reject conflicting reuse.
- Add prepare/commit boundaries to the encounter coordinator so graph and encounter lifecycle cannot diverge.
- Return structured cleanup results/failures and propagate them to the owning lifecycle.
- Keep old generation frozen after partial wipe failure; retry cleanup before committing a new generation.
- Enforce task generation invariants and execution-time ownership checks.
- Add monotonic save sequencing to `EventStateStore` without changing its public recovery semantics.
- Scope deposit/hazard records to event id + generation and reject conflicting mutations.

## Phase 3 — wave and combat audit

Trace each W1–W7 adapter entry and completion for exactly-once encounter start, objective progress, scope, music, and cleanup. Then run targeted Paper tests for:

- W1 holder death/quit/delivery provenance;
- W2 fair target rotation;
- W3 three sequential portals and journal restoration;
- W4 obelisk integrity, official reflected projectile provenance, collision exactly-once, and bounded fireballs;
- W5 fog cycles and safe-zone restoration;
- W6 one pair state machine and deadline boundary;
- W7 chamber ownership and pre-completion boundary rejection;
- real boss HP, same-tick multiplayer damage, shield windows, phase transitions, and Final Strike.

Do not turn a contract pass into a Paper pass. Save structured runtime traces with event id and generation.

## Phase 4 — client/assets/audio and visual evidence

- Validate current model/texture/animation catalogs and artist aliases against runtime mappings.
- Keep boss binding UUID-scoped and generation-reset safe; ordinary Endermen remain vanilla.
- Validate tentacle hierarchy/socket and obelisk muzzle/collision ownership.
- Build the resource pack twice and compare hashes/metadata.
- Check audio provenance/license manifest and runtime catalog.
- Run a real native Minecraft client matrix if a client is available. Otherwise mark every native visual requirement `NOT VERIFIED`.

## Phase 5 — CI and runtime release gate

- Run the full repository validators and `RunEndRiftEventChecks.ps1` from a clean checkout.
- Push logical commits, then wait for a GitHub Actions run whose `head_sha` is the final SHA. A prior or skipped run is not evidence for the final SHA.
- Run official Paper scenarios for 2, 3, 5, 10, and 20 players where the local harness can support them; record unavailable cases explicitly.
- Execute restart/failure-injection cases at each durable boundary and verify cleanup, generation fences, journals, and exactly-once rewards.
- Run a bounded 20-player performance/soak scenario and record leaks/counts before and after cleanup.

## Final evidence gate

The final report must include an item-by-item matrix with `FIXED`, `ALREADY FIXED`, `FALSE POSITIVE`, `DEFERRED`, or `NOT VERIFIED`. If any mandatory CI, Paper, failure-injection, or native-client gate is unavailable, the verdict remains:

`RELEASE VERDICT: NOT READY`
