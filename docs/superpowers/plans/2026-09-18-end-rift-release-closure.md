# End Rift latest-head release closure

## Goal

Reopen the End Rift blockers from the latest remediation prompt, make the
runtime contracts behavior-based, and publish only evidence that is tied to
the exact source head and built artifacts. The native Minecraft visual gate
stays explicitly `NOT VERIFIED` until the exact tested head is rendered in a
controllable Minecraft window.

## Evidence boundary

- Authoritative checkout: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`.
- Branch: `codex/end-rift-event`.
- Every live artifact report must record separate `sourceImplementationSha`,
  `reportCommitSha`, `testedArtifactBuildSha`, `githubActionsHeadSha`, and
  `nativeMinecraftTestedSha` values.
- Static validators, Java tests, and local Paper runs are separate evidence
  levels. They cannot be used as native visual proof.
- No release-ready claim is allowed while any mandatory gate is `FAIL`,
  `UNVERIFIED`, or `NOT VERIFIED`.

## Work sequence

### 1. Wave 6 caster AI ownership and structured diagnostics

Files:

- `copimine-end-event/src/me/copimine/endevent/domain/RitualCasterTacticsPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/RitualCasterDiagnosticsPolicy.java`
  (new pure policy/records if needed)
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- `copimine-end-event/plugin.yml`
- `tests/test_wave6_ritual_caster_behavior_contract.py`
- Java domain tests under `tests/`

TDD:

1. Add a pure regression test proving that the generic combat-AI restore
   decision keeps `GUARDED_CASTING` and `EXPOSED_CASTING` server-controlled,
   restores AI for ordinary wave mobs, and enables it only for
   `AWAKENED_ATTACKING` casters.
2. Run the focused Java test and observe RED against the current unconditional
   restore path.
3. Implement the smallest role-aware restore decision and call the ritual
   guard/caster reassertion before the pre-capture return path.
4. Add behavior-oriented ownership validation for exactly three guards per
   caster, unique caster ownership, no orphan guards, and no cross-caster
   guard assignment.
5. Add `cmend debug ai --json` (local diagnostic command; no production bypass)
   with aggregate counts plus one record per caster: id, slot, role, state,
   guard IDs/count, target, AI/aware flags, and ability state.
6. Run focused Java/Python tests, build the plugin, and reproduce the original
   live RED probe until the initial Wave 6 state reports all casters guarded,
   passive, un-targeted, and three guards owned by each caster.

### 2. Wave 6 live lifecycle and role matrix

Files:

- `tests/RunEndRiftAiPhasesLive.ps1`
- `tests/RunEndRiftWave6RitualLive.ps1`
- `tests/test_end_event_wave6_ritual_live_contract.py`
- `tests/test_wave6_ritual_caster_behavior_contract.py`

Replace source-string-only assertions with executable JSON/state checks where
possible. The live probe must conditionally wait for spawn/capture/state
markers rather than sleeping for a guessed duration. It must prove:

- initial `WAITING_FOR_PRISONER` guarded state;
- `GUARDED_CASTING -> EXPOSED_CASTING` after the last guard is removed;
- `EXPOSED_CASTING -> AWAKENED_ATTACKING` only after accepted caster damage;
- mixed-state AI expectation: normal mobile mobs plus only awakened casters;
- exact caster/guard ownership and all five role dispatches;
- prisoner seal/health/targeting, projectile origin, zone/control effects,
  amplifier bounds, cleanup, and idempotent cleanup;
- no swallowed cleanup exceptions and a final queried zero-residue state.

### 3. Boss phases and AI proof

Files:

- `tests/RunEndRiftAiPhasesLive.ps1`
- `tests/test_end_event_current_contract.py`
- `tests/test_end_rift_test_quality_contract.py`

The phase probe must invoke every supported local phase command and verify the
resulting `AI_PROFILE`, target/movement/ability state, and phase transition
marker before emitting a phase PASS marker. It must never print the complete
phase list after checking only AWAKENING. Use the existing local boss phase
hook and movement freeze only in the isolated local environment. Any phase
that cannot be observed remains failed, not assumed.

### 4. Wave 7 two-player boundary and cleanup

Files:

- `tests/RunEndRiftWave6Wave7BoundariesLive.ps1`
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- `tests/test_end_event_wave6_wave7_boundaries_contract.py`

Capture a per-player accepted-damage ledger and chamber assignment. Prove
both players receive positive damage, role-aware targeting remains in the
assigned chamber, natural completion emits the server-owned completion marker,
and command cleanup leaves zero mobs, tasks, controls, barriers, projectiles,
and persisted transient state. Do not increase timeouts or force-kill to turn
an incomplete run into PASS.

### 5. Reports and release gate

Files:

- `docs/end-rift-validation.md`
- `docs/superpowers/evidence/end-rift-live-verification-2026-09-18.md`
- generated `artifacts/end-rift-diagnostics/<run>/` reports

Reclassify open reports as `INTERIM — RELEASE BLOCKED` and enumerate every
unverified gate, especially native exact-head visual proof. Archive only
reports whose hashes, head metadata, test result, and CI result agree. Keep
native evidence empty/`NOT VERIFIED` when no Minecraft window is controllable.

### 6. Verification and publication

Run, in order:

```powershell
pytest -q tests/test_wave6_ritual_caster_behavior_contract.py tests/test_end_event_wave6_ritual_live_contract.py tests/test_end_event_wave6_wave7_boundaries_contract.py tests/test_end_rift_test_quality_contract.py
& .\tests\BuildEndRift.ps1
& .\tests\RunEndRiftAiPhasesLive.ps1 -BotName EndRiftAiClosure
& .\tests\RunEndRiftWave6RitualLive.ps1
& .\tests\RunEndRiftWave6Wave7BoundariesLive.ps1
pytest -q
git diff --check
```

Then run the repository validator and inspect GitHub Actions for the exact
published head. Commit coherent changes, push `codex/end-rift-event`, and
report the exact commit/artifact hashes. If native Minecraft is still absent,
the final status remains `NOT READY FOR FINAL RELEASE`.

## Current first RED

The first implementation RED is the Wave 6 role-aware AI restore decision:
the current live output reports four casters with `aiEnabled=16` and
`ritualCastersPassive=0` while guards are alive. The production fix must not
be started until that failing regression is observed and its caller path is
covered by the test.
