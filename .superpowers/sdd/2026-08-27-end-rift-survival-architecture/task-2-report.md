# Task 2 report — deterministic five-wave objective policies

## Scope

Implemented only the four pure domain policies and their four executable-main Java tests listed in the brief. No Bukkit imports, filesystem access, world access, plugin-main changes, configuration changes, persistence changes, client changes, runtime changes, or unrelated test changes were made.

## TDD evidence

Tests were added before production classes. The RED command was:

```powershell
$sources=(Get-ChildItem copimine-end-event\src\me\copimine\endevent\domain\*.java).FullName
javac -encoding UTF-8 -d tests\build\task-2-red $sources tests\PortalCapturePolicyTest.java tests\TowerDefensePolicyTest.java tests\HazardPlannerTest.java tests\WaveObjectivePolicyTest.java
```

It failed because the four required policy classes were not yet present (`cannot find symbol` for `PortalCapturePolicy`, `TowerDefensePolicy`, `HazardPlanner`, and `WaveObjectivePolicy`).

## Verification

Focused command, run from the worktree:

```powershell
$out='tests\build\task-2-focused-final'
$sources=(Get-ChildItem copimine-end-event\src\me\copimine\endevent\domain\*.java).FullName
javac -encoding UTF-8 -d $out $sources tests\PortalCapturePolicyTest.java tests\TowerDefensePolicyTest.java tests\HazardPlannerTest.java tests\WaveObjectivePolicyTest.java tests\EndEventDomainTest.java
java -cp $out PortalCapturePolicyTest
java -cp $out TowerDefensePolicyTest
java -cp $out HazardPlannerTest
java -cp $out WaveObjectivePolicyTest
java -cp $out EndEventDomainTest
```

Output:

```text
PortalCapturePolicyTest OK
TowerDefensePolicyTest OK
HazardPlannerTest OK
WaveObjectivePolicyTest OK
EndEventDomainTest OK
```

`git diff --check` also passed.

## Concerns

- The Java compiler emitted the existing `EndEventStateMachine.java uses or overrides a deprecated API` note; it is unrelated to this task.
- The repository-wide End Rift script was not run because it builds and copies unrelated runtime artifacts, while the brief restricts this task to pure domain files and focused Java tests.
- Existing unrelated untracked files (Boss policy files, `.superpowers/`, and the architecture plan) were preserved and excluded from the commit.

## Round 1 review fixes

1. `PortalCapturePolicy.tick` now treats an occupied call at the current timestamp as a re-entry event and records `lastOccupiedMillis` without adding progress. The regression checks resumption exactly at the 450 ms grace boundary and verifies that the boundary does not decay progress.
2. `HazardPlanner` filters previous snapshot entries to the inclusive planning rectangle, selects hazards only from cells with validated originals, and returns `originalBlocks` with exactly the hazard-cell keys. `Pattern` rejects malformed entries, `Plan` rejects snapshot/hazard mismatches and hazard/safe overlap, and candidate ordering is canonicalized before seeded shuffling.
3. The mob-cap oracle is documented in `WaveObjectivePolicy`: current config base totals are `[13, 16, 17, 20, 30]`, runtime maximum player scale is `2.0`, and `waves.hard-cap` is `48`; therefore caps are `min(baseTotal * 2, 48)` = `[26, 32, 34, 40, 48]`. The test derives these values independently from the config composition literals and asserts the hard-cap ceiling.
4. `TowerDefensePolicy.retry` now retries only `FAILURE` states; active and successful states are unchanged. Negative timestamps, deadline overflow, invalid player counts, NaN/infinite damage, negative damage, and malformed snapshot/planner inputs are covered by fail-closed tests.

The review regressions were run before the corresponding fixes. The pre-fix focused run failed with:

```text
PortalCapturePolicyTest: same-timestamp re-entry must record the occupied time
TowerDefensePolicyTest: active cores must not be retried
HazardPlannerTest: each hazard must have exactly one original snapshot entry
WaveObjectivePolicyTest: objective mob cap must match config composition at max scale and hard cap
EndEventDomainTest OK
```

The additional public-`Plan` invariant test was also run before its hardening and failed with `plans must reject missing original snapshots for hazards`.

## Round 1 verification

Exact focused command, run from `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`:

```powershell
$out='tests\build\task-2-round1-final-20260827'
New-Item -ItemType Directory -Force $out | Out-Null
$sources=(Get-ChildItem copimine-end-event\src\me\copimine\endevent\domain\*.java).FullName
javac -encoding UTF-8 -d $out $sources tests\PortalCapturePolicyTest.java tests\TowerDefensePolicyTest.java tests\HazardPlannerTest.java tests\WaveObjectivePolicyTest.java tests\EndEventDomainTest.java
java -cp $out PortalCapturePolicyTest
java -cp $out TowerDefensePolicyTest
java -cp $out HazardPlannerTest
java -cp $out WaveObjectivePolicyTest
java -cp $out EndEventDomainTest
```

Output:

```text
PortalCapturePolicyTest OK
TowerDefensePolicyTest OK
HazardPlannerTest OK
WaveObjectivePolicyTest OK
EndEventDomainTest OK
```

The compiler emitted only the pre-existing deprecation note for `EndEventStateMachine.java`. The modified policy/test files contain no Bukkit imports or filesystem/world access. The working tree's pre-existing config, plugin-main, Boss*, plan, and `.superpowers` changes were not edited or staged.
