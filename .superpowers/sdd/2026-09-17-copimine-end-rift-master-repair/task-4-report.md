# Task 4 report: ritual prisoner health authority

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

Branch: `codex/end-rift-event`

Working HEAD before this task: `8bb547ee` (`fix: fail-closed ritual projectile provenance`)

## Outcome

The captured Wave 6 prisoner now ignores every non-ritual external damage event. `RitualPrisonerHealthPolicy.safeExternalDamage` returns exactly `0.0D`, and `CopiMineEndEvent.onRitualPrisonerDamage` cancels the generic `EntityDamageEvent` without calling `setHealth` or manually subtracting health. The existing authoritative ritual drain remains responsible for the 20-second, 2 HP drain, the 1 HP floor, and the no-intensity-gain-at-floor behavior.

## Changed files

- `copimine-end-event/src/me/copimine/endevent/domain/RitualPrisonerHealthPolicy.java`
  - Replaced the external-damage remainder calculation with the required constant `0.0D` policy.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - Removed the external-damage cap and handler-side health mutation.
  - Kept prisoner matching, event cancellation, and a non-mutating guard log.
- `tests/RitualPrisonerHealthPolicyTest.java`
  - Uses the exact three required zero-damage assertions.
- `tests/test_end_event_ritual_prisoner_health_contract.py`
  - Adds source/contract coverage for melee, projectile, fall, zone, explosion, fire, poison, wither, and generic entity damage through the generic Bukkit handler.
  - Asserts cancellation-only behavior and rejects handler health mutation.
  - Documents that this is source coverage because no Bukkit harness is available in the focused gate.
- `tests/RunEndRiftEventChecks.ps1`
  - Registers the new prisoner-health source contract alongside the current Python contracts.
- `.superpowers/sdd/2026-09-17-copimine-end-rift-master-repair/task-4-report.md`
  - Records the exact TDD, contract, build, diff, and scope evidence for this task.

## TDD evidence

### Focused RED

Command, run before production edits:

```powershell
$build = Join-Path (Get-Location) 'tests\build\task-4-focused'
New-Item -ItemType Directory -Path $build -Force | Out-Null
javac -encoding UTF-8 -d $build copimine-end-event\src\me\copimine\endevent\domain\RitualPrisonerHealthPolicy.java tests\RitualPrisonerHealthPolicyTest.java
java -cp $build RitualPrisonerHealthPolicyTest
```

Result:

```text
javac exit 0
java exit 1
java.lang.AssertionError: captured prisoner must ignore all non-ritual external damage
```

The failure was the expected first exact zero-damage assertion against the old one-HP-preserving external-damage calculation.

The new source contract was also run before production edits:

```text
python -m pytest -q .\tests\test_end_event_ritual_prisoner_health_contract.py
3 failed in 0.26s
```

The failures were the expected policy `Math.min` implementation and handler-side `safeExternalDamage`/`setHealth` mutation.

### Focused GREEN

Command:

```powershell
$build = Join-Path (Get-Location) 'tests\build\task-4-focused'
New-Item -ItemType Directory -Path $build -Force | Out-Null
javac -encoding UTF-8 -d $build copimine-end-event\src\me\copimine\endevent\domain\RitualPrisonerHealthPolicy.java tests\RitualPrisonerHealthPolicyTest.java
java -cp $build RitualPrisonerHealthPolicyTest
```

Result:

```text
RitualPrisonerHealthPolicyTest OK
```

The final focused source contract command was:

```text
python -m pytest -q .\tests\test_end_event_ritual_prisoner_health_contract.py
11 passed in 0.17s
PRISONER_CONTRACT_EXIT=0
```

The parametrized contract covers all nine required damage paths through the same generic `EntityDamageEvent` cancellation-only handler. It does not claim live Bukkit behavior.

## Relevant Wave 6 contract results

Command:

```powershell
python -m pytest -q .\tests\test_end_event_current_contract.py .\tests\test_end_event_wave6_wave7_boundaries_contract.py .\tests\test_wave6_ritual_caster_behavior_contract.py .\tests\test_end_event_ritual_projectile_provenance_contract.py .\tests\test_end_event_ritual_prisoner_health_contract.py
```

Result:

```text
133 passed in 1.25s
WAVE6_PYTHON_EXIT=0
```

## Plugin build

Command, run from `copimine-end-event`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File '.\build-plugin.ps1'
```

Result:

```text
Built D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\copimine-end-event\CopiMineEndEvent.jar
Copied D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\minecraft\server\plugins\CopiMineEndEvent.jar
BUILD_EXIT=0
```

The build emitted the existing five Bukkit removal/deprecation warnings for `EntityKnockbackEvent` and `EntityRemoveEvent`; no new compile error occurred.

## Diff and scope checks

```text
git diff --check
DIFF_CHECK_EXIT=0
```

Only the six files listed above are in the scoped change. Pre-existing untracked evidence artifacts, the pre-existing master-repair plan, and `tests/.packet-trace-manifest.mf` were left untouched and are not included.

## Concerns

- No live Paper/Purpur Bukkit harness or gameplay run was performed. The handler coverage is explicitly a source contract, while the Java policy test proves the pure policy and drain rules.
- The plugin build copied the locally built JAR into the local server plugin directory as part of the repository build script; no server was started and no production deployment or upload was performed.
- Task 5 and later scope remains untouched: control swap, zone effects, projectile origin, caster roles, encounter state, and live/native verification were not changed.

## Commit

The scoped local commit contains this report; no GitHub push was performed.
