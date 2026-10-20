# Task 3 report: central free-player ritual target eligibility

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

Branch: `codex/end-rift-event`

Working BASE supplied by the controller: `f89668f81543c3e91651d72003ba40b9874f57e8`

The brief also names historical baseline `79781d8c8be3827078a77972ccba851df4eb819f`; it was not used as the working HEAD because the controller supplied `f89668f81543c3e91651d72003ba40b9874f57e8` as the current BASE.

## Changed files

- `copimine-end-event/src/me/copimine/endevent/domain/RitualTargetPolicy.java`
  - Added the exact `freeTargets(List<Candidate>, UUID)` API and `Candidate(UUID, boolean)` record.
  - Null candidate lists return `List.of()`; null candidate UUIDs are rejected; eligible candidates are filtered, the non-null prisoner is excluded, duplicate UUIDs are removed, and caller order is preserved.
- `tests/RitualTargetPolicyTest.java`
  - Added the required prisoner/freeA/freeB core case plus null prisoner, ineligible, duplicate, null list, and invalid candidate boundaries.
- `tests/RunEndRiftEventChecks.ps1`
  - Registered `RitualTargetPolicyTest` in the pure-test list.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - Added the Bukkit adapter that resolves player recipients through `RitualTargetPolicy`.
  - Routed current Wave 6 caster/guard AI target validation and awakened-caster aggro through the policy.
  - Routed sphere nearest-target selection, barrage/void-lance/spike validation, ritual projectile retargeting, corrupted-zone recipients, reverse recipients, control-swap candidate construction/input validation, and ritual skeleton/projectile/explosive recipients through the policy.
  - Corrected `ritualNearestTarget` and `ritualNearestGuardTarget` to select by distance first with deterministic UUID tie-breaking.
  - Left Task 2 waiting/capture lifecycle, Wave 7/cleanup, and Task 4-8 mechanics outside this change.

## TDD evidence

### Focused RED

Command, run before creating the policy implementation:

```powershell
$redBuild = Join-Path (Resolve-Path '.').Path 'tests\build\task-3-ritual-target-red'
New-Item -ItemType Directory -Force $redBuild | Out-Null
javac -encoding UTF-8 -d $redBuild tests\RitualTargetPolicyTest.java
```

Result: `RED_EXIT=1`; focused compilation failed with the intended missing-policy errors, including `package me.copimine.endevent.domain does not exist` and `cannot find symbol RitualTargetPolicy`.

### Focused GREEN

Command:

```powershell
$greenBuild = Join-Path (Resolve-Path '.').Path 'tests\build\task-3-ritual-target-green-focused-2'
New-Item -ItemType Directory -Force $greenBuild | Out-Null
javac -encoding UTF-8 -d $greenBuild copimine-end-event\src\me\copimine\endevent\domain\RitualTargetPolicy.java tests\RitualTargetPolicyTest.java
java -cp $greenBuild RitualTargetPolicyTest
```

Result:

```text
RitualTargetPolicyTest OK
FOCUSED_GREEN_EXIT=0
```

## Relevant subsystem results

- Wave 6 policy/subsystem pure suite: compiled the domain/runtime support sources and ran the 13 relevant tests; `WAVE6_POLICY_TESTS_PASS count=13`.
- Wave 6 source contracts:

  ```text
  python -m pytest -q tests\test_wave6_ritual_caster_behavior_contract.py tests\test_end_event_wave6_wave7_boundaries_contract.py
  24 passed in 0.39s
  ```

- Plugin build:

  ```text
  powershell -NoProfile -ExecutionPolicy Bypass -File .\build-plugin.ps1
  Built ...\copimine-end-event\CopiMineEndEvent.jar
  Copied ...\minecraft\server\plugins\CopiMineEndEvent.jar
  BUILD_EXIT=0
  ```

- `git diff --check`: no whitespace errors. Git reported only the existing LF-to-CRLF working-copy warnings.

## Commit

`af69bf3a34a7f7a6904ac32a580dd33d6443f778` — `fix: route Wave 6 targets through ritual policy`

## Concerns

- No live Paper/Purpur gameplay run was performed in this task; the evidence is focused pure tests, source contracts, and a successful plugin build.
- The build emits five existing deprecation warnings for Bukkit removal-marked APIs (`EntityKnockbackEvent` and `EntityRemoveEvent`).
- Task 4 damage immunity, Task 5 pair API/atomic cleanup, Task 6 effect semantics, Task 7 projectile origin, and Task 8 caster roles remain intentionally unimplemented.
- Pre-existing untracked evidence artifacts, the pre-existing plan, and `tests/.packet-trace-manifest.mf` were left untouched and were not included in the scoped commit.

## Fix round 1: immutable ritual projectile provenance

Reviewer finding addressed: a Wave 6 ritual caster/guard arrow could outlive
its shooter.  After `onOwnedEntityDeath` removed live ritual-role membership,
the direct arrow damage path could stop recognizing the shooter and leave the
already-fired arrow to generic vanilla damage; explosive area damage had the
same provenance risk.

### Changed files

- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - Persisted `end_event_ritual_projectile` as an immutable byte PDC marker on
    ritual caster/guard Wave 6 arrows from both the skeleton shoot listener and
    `riftArrowVolley`.
  - Kept ordinary arrows on the existing shooter-based path.
  - Made direct skeleton-arrow handling admit a marked arrow without requiring a
    live/current skeleton shooter, then route player validation through the
    marker-based ritual free-target policy with null-safe room checks.
  - Made the custom event-arrow listener delegate marked legacy skeleton
    payloads through the same authoritative direct path, regardless of listener
    ordering.
  - Kept custom hit validation, projectile-hit explosive detonation, and the
    explosive area recipient loop marker-based; a marker never falls back to a
    generic `activeLivingPlayers()` recipient list.
  - Clears the marker during event-arrow cleanup, including the Bukkit lookup
    fallback when the owned-entity map no longer contains the arrow.
- `tests/test_end_event_ritual_projectile_provenance_contract.py`
  - Added a source regression proving marked direct arrow damage does not
    require a live skeleton shooter and cannot bypass the marker policy.
- `tests/RunEndRiftEventChecks.ps1`
  - Registered the provenance contract in the explicit current Python gate.

### TDD and verification evidence

Focused RED, run before the direct-handler change:

```text
python -m pytest -q tests/test_end_event_ritual_projectile_provenance_contract.py
1 failed, 1 passed
```

The failure was the intended missing marker-aware direct damage admission.

Focused GREEN and final focused contracts:

```text
python -m pytest -q tests/test_end_event_ritual_projectile_provenance_contract.py tests/test_wave6_ritual_caster_behavior_contract.py tests/test_end_event_wave6_wave7_boundaries_contract.py tests/test_end_event_current_contract.py tests/test_end_event_boss_hitbox_contract.py
129 passed in 1.10s
```

Plugin build:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-plugin.ps1
Built ...\copimine-end-event\CopiMineEndEvent.jar
Copied ...\minecraft\server\plugins\CopiMineEndEvent.jar
BUILD_EXIT=0
```

The build emitted the existing five Bukkit removal/deprecation warnings for
`EntityKnockbackEvent` and `EntityRemoveEvent`; no new compile error occurred.
`git diff --check` completed without whitespace errors, with only the existing
LF-to-CRLF working-copy warnings.  No live server was started, and the
protected evidence artifacts, plan, and `tests/.packet-trace-manifest.mf` were
not changed.

Task 3 fix round 1 is limited to projectile provenance and fail-closed target
routing.  Task 4+ mechanics remain outside this change.
