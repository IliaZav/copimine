# Task 5 report: control-swap exclusion and atomic cleanup

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

Branch: `codex/end-rift-event`

Working HEAD before this task: `daef7f6f4637e8ebb12a4910689482835996b8dd` (`fix: make Wave 6 ritual prisoner damage immune`)

## Outcome

Wave 6 control-swap pairing now has an explicit prisoner-exclusion API and
the Bukkit start path passes the current ritual prisoner UUID to it. The
policy preserves caller order, trims and de-duplicates valid IDs, rejects
blank/null IDs by omission, and caps the requested pair count at
`RitualControlPairPolicy.MAX_PAIRS`.

Paired control state now tears down as one operation. Disconnect, death, world
change, invalid encounter participation, and expiry clear both members'
instance, partner, and expiry maps and send `STOP` to both known active
instances. The pair teardown helper does not remove `ritualReverseUntil`, so a
reverse effect for an unrelated player is not cleared by paired cleanup. The
existing reverse/control mutual-exclusion policy remains in place.

## Changed files

- `copimine-end-event/src/me/copimine/endevent/domain/RitualControlPairPolicy.java`
  - Added `pair(List<String>, String excludedId, int)`.
  - Kept the two-argument overload as a compatibility delegate.
  - Filters the exact excluded ID before deterministic pairing and retains
    validation, de-duplication, caller order, and `MAX_PAIRS` bounding.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - Passes `ritualPrisonerUuid.toString()` through the explicit excluded-ID
    API at control-swap start.
  - Validates both pair members when creating and ticking control state.
  - Adds atomic bidirectional pair cleanup with two STOP packets.
  - Routes world changes and invalid input participants through lifecycle
    cleanup; disconnect and death already use the same lifecycle adapter.
- `tests/RitualControlPairPolicyTest.java`
  - Adds the exact three-argument prisoner-exclusion case while retaining the
    reverse/control mutual-exclusion assertion.
- `tests/test_end_event_ritual_control_pair_contract.py`
  - Adds source-contract coverage for the policy API, runtime prisoner
    exclusion, paired map consistency, two-sided STOP cleanup, reverse-state
    isolation, and all lifecycle teardown triggers.
  - Documents that these runtime assertions are source coverage because no
    Bukkit harness is available in the focused gate.
- `tests/RunEndRiftEventChecks.ps1`
  - Registers the new control-pair source contract in the current Python gate.
- `.superpowers/sdd/2026-09-17-copimine-end-rift-master-repair/task-5-report.md`
  - Records exact task evidence and scope boundaries.

## TDD evidence

### Focused RED

The test was already changed first with the required three-argument call. The
focused compile was run before adding the production overload:

```powershell
$redBuild=Join-Path $PWD 'tests\build\task5-red'
New-Item -ItemType Directory -Path $redBuild -Force | Out-Null
& javac -encoding UTF-8 -d $redBuild 'copimine-end-event/src/me/copimine/endevent/domain/RitualControlPairPolicy.java' 'tests/RitualControlPairPolicyTest.java'
$exit=$LASTEXITCODE
Write-Host "FOCUSED_RED_EXIT=$exit"
exit $exit
```

Observed output:

```text
tests\RitualControlPairPolicyTest.java:16: error: method pair in class RitualControlPairPolicy cannot be applied to given types;
        List<RitualControlPairPolicy.Pair> withoutPrisoner = RitualControlPairPolicy.pair(
                                                                                    ^
  required: List<String>,int
  found:    List<String>,String,int
  reason: actual and formal argument lists differ in length
1 error
FOCUSED_RED_EXIT=1
```

The new source contract was also run before the production edit:

```text
python -m pytest -q .\tests\test_end_event_ritual_control_pair_contract.py
```

Observed result:

```text
FFFF                                                                     [100%]
4 failed in 0.35s
CONTRACT_RED_EXIT=1
```

The failures were the expected missing policy overload, missing explicit
prisoner argument, missing atomic pair helper, and missing world-change
cleanup route.

### Focused GREEN

```text
RitualControlPairPolicyTest OK
```

The final focused source contract passed:

```text
python -m pytest -q .\tests\test_end_event_ritual_control_pair_contract.py
```

```text
....                                                                     [100%]
4 passed in 0.14s
```

## Relevant Python contracts

Command:

```powershell
python -m pytest -q .\tests\test_end_event_current_contract.py .\tests\test_wave6_ritual_caster_behavior_contract.py .\tests\test_end_event_ritual_projectile_provenance_contract.py .\tests\test_end_event_ritual_prisoner_health_contract.py .\tests\test_end_event_ritual_control_pair_contract.py
```

Observed result:

```text
........................................................................ [ 60%]
...............................................                          [100%]
119 passed in 1.20s
```

## End Event plugin build

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File '.\copimine-end-event\build-plugin.ps1' -SyncServerConfig
```

Observed result:

```text
5 warnings
Built D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\copimine-end-event\CopiMineEndEvent.jar
Copied D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\minecraft\server\plugins\CopiMineEndEvent.jar
END_EVENT_PLUGIN_BUILD_EXIT=0
```

The five warnings are the existing Bukkit removal/deprecation warnings for
`EntityKnockbackEvent` and `EntityRemoveEvent`; there were no compile errors.

## Diff and scope checks

Command:

```powershell
git diff --check
Write-Host "DIFF_CHECK_EXIT=$LASTEXITCODE"
```

Observed result:

```text
DIFF_CHECK_EXIT=0
```

Git also printed the existing LF-to-CRLF working-copy warnings for the four
already tracked text files. No whitespace error was reported.

Pre-existing untracked End Rift evidence directories/files, the master-repair
plan, and `tests/.packet-trace-manifest.mf` were left untouched and will not
be staged.

## Verification boundary

No Paper/Purpur live server, Bukkit harness, native Minecraft run, upload, or
deployment was performed for Task 5. The runtime lifecycle behavior is covered
by the narrow source contract and plugin compilation only; this report makes
no live-run claim.

## Commit

The scoped changes will be committed locally on `codex/end-rift-event`. No
GitHub push will be performed.
