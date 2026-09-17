# Task 6 report: corrupted-zone effect semantics

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

Branch: `codex/end-rift-event`

Working HEAD before this task: `bd997cfb7a1f26ef816edb044aed21f795018830`
(`fix: harden ritual control pair validation`)

## Outcome

Wave 6 corrupted zones now route their gameplay decision through the pure
`RitualZoneEffectPolicy`. A free player inside the existing active 4x4 zone
receives refreshed Wither and Slowness and reverse movement only when that
player is not in an active control swap. Prisoners and players outside the
active zone receive no zone effects.

The old zone Poison effect and once-per-second raw `player.damage(1.0D)` call
were removed. Zone-created reverse recipients are tracked separately and
reconciled each tick. Exits and expiry clear only the event-owned reverse
state through the existing `ritualControlInstances`, `ritualReverseUntil`,
and `sendEndControlPacket` lifecycle; no potion effects are removed during
zone cleanup.

The existing 4x4 geometry, telegraph, expiry, visual rendering, and free
target filtering remain unchanged. No Task 5 pair policy/cleanup, Task 7
projectile origin, Task 8 caster roles, or Task 9 encounter state was changed.

## Changed files

- `copimine-end-event/src/me/copimine/endevent/domain/RitualZoneEffectPolicy.java`
  - Adds the pure `effect(boolean prisoner, boolean insideActiveZone,
    boolean controlSwapActive)` API and immutable `Result` record.
  - Suppresses all three zone outputs for prisoners/outside players and
    suppresses only reverse movement during control swap.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - Routes `tickRitualZones` through the pure policy.
  - Applies only short refreshed Wither and Slowness effects.
  - Removes zone Poison and raw player damage.
  - Tracks, refreshes, and clears zone-owned reverse recipients through the
    existing server-authoritative control state and packet lifecycle.
  - Keeps unrelated potion effects intact by not removing potion effects.
- `tests/RitualZoneEffectPolicyTest.java`
  - Covers free, swapped, prisoner, and outside decision-table cases.
- `tests/test_end_event_ritual_zone_effect_contract.py`
  - Adds the narrow source contract for policy routing, Wither/Slowness,
    prisoner/target filtering, no Poison/raw damage, reverse-state ownership,
    and non-destructive potion cleanup.
- `tests/RunEndRiftEventChecks.ps1`
  - Registers the pure Java test and Python source contract in the current
    gate invocations.
- `.superpowers/sdd/2026-09-17-copimine-end-rift-master-repair/task-6-report.md`
  - Records the exact RED/GREEN, contract, build, and diff evidence.

## TDD evidence

### Focused RED

The pure test was compiled before adding `RitualZoneEffectPolicy.java`:

```powershell
$out='tests\\build\\task6-red'
New-Item -ItemType Directory -Force $out | Out-Null
& javac -encoding UTF-8 -d $out 'tests\\RitualZoneEffectPolicyTest.java'
$code=$LASTEXITCODE
Write-Host "JAVAC_EXIT=$code"
```

Observed result:

```text
tests\\RitualZoneEffectPolicyTest.java:1: error: package me.copimine.endevent.domain does not exist
import me.copimine.endevent.domain.RitualZoneEffectPolicy;
                                  ^
tests\\RitualZoneEffectPolicyTest.java:5: error: package RitualZoneEffectPolicy does not exist
        RitualZoneEffectPolicy.Result free = RitualZoneEffectPolicy.effect(false, true, false);
                              ^
tests\\RitualZoneEffectPolicyTest.java:5: error: cannot find symbol
        RitualZoneEffectPolicy.Result free = RitualZoneEffectPolicy.effect(false, true, false);
                                             ^
  symbol:   variable RitualZoneEffectPolicy
  location: class RitualZoneEffectPolicyTest
tests\\RitualZoneEffectPolicyTest.java:11: error: package RitualZoneEffectPolicy does not exist
        RitualZoneEffectPolicy.Result swapped = RitualZoneEffectPolicy.effect(false, true, true);
                              ^
tests\\RitualZoneEffectPolicyTest.java:11: error: cannot find symbol
        RitualZoneEffectPolicy.Result swapped = RitualZoneEffectPolicy.effect(false, true, true);
                                                ^
  symbol:   variable RitualZoneEffectPolicy
  location: class RitualZoneEffectPolicyTest
tests\\RitualZoneEffectPolicyTest.java:17: error: package RitualZoneEffectPolicy does not exist
        RitualZoneEffectPolicy.Result prisoner = RitualZoneEffectPolicy.effect(true, true, false);
                              ^
tests\\RitualZoneEffectPolicyTest.java:17: error: cannot find symbol
        RitualZoneEffectPolicy.Result prisoner = RitualZoneEffectPolicy.effect(true, true, false);
                                                 ^
  symbol:   variable RitualZoneEffectPolicy
  location: class RitualZoneEffectPolicyTest
tests\\RitualZoneEffectPolicyTest.java:21: error: package RitualZoneEffectPolicy does not exist
        RitualZoneEffectPolicy.Result outside = RitualZoneEffectPolicy.effect(false, false, false);
                              ^
tests\\RitualZoneEffectPolicyTest.java:21: error: cannot find symbol
        RitualZoneEffectPolicy.Result outside = RitualZoneEffectPolicy.effect(false, false, false);
                                                ^
  symbol:   variable RitualZoneEffectPolicy
  location: class RitualZoneEffectPolicyTest
9 errors
JAVAC_EXIT=1
```

The exact focused RED compiler result above is the required
production-boundary RED evidence. The source contract was then run against
the completed adapter and passed after its assertions were aligned with the
existing two-sided reverse packet cleanup helper.

### Focused GREEN

Command:

```powershell
$out='tests\\build\\task6-focused-final'
New-Item -ItemType Directory -Force $out | Out-Null
& javac -encoding UTF-8 -d $out `
  'copimine-end-event\\src\\me\\copimine\\endevent\\domain\\RitualZoneEffectPolicy.java' `
  'tests\\RitualZoneEffectPolicyTest.java'
& java -cp $out RitualZoneEffectPolicyTest
```

Observed result:

```text
RitualZoneEffectPolicyTest OK
```

## Focused and relevant Python contracts

Command:

```powershell
python -m pytest -q `
  tests/test_end_event_ritual_zone_effect_contract.py `
  tests/test_end_event_wave6_wave7_boundaries_contract.py `
  tests/test_wave6_ritual_caster_behavior_contract.py `
  tests/test_end_event_ritual_prisoner_health_contract.py `
  tests/test_end_event_ritual_control_pair_contract.py
```

Observed result:

```text
............................................                             [100%]
44 passed in 0.70s
```

The focused source contract alone also passed earlier as:

```text
....                                                                     [100%]
4 passed in 0.15s
```

The gate script syntax check passed:

```text
RunEndRiftEventChecks.ps1 parse OK
```

## End Event plugin build

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File '.\\copimine-end-event\\build-plugin.ps1' -SyncServerConfig
```

Observed result:

```text
5 warnings
Built D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\copimine-end-event\CopiMineEndEvent.jar
Copied D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\minecraft\server\plugins\CopiMineEndEvent.jar
```

Build exit: `0`.

The five warnings are the existing Bukkit removal/deprecation warnings for
`EntityKnockbackEvent` and `EntityRemoveEvent`; no Task 6 compile errors were
reported.

## Diff and scope checks

Command:

```powershell
git diff --check
```

Observed result: exit `0`. Git printed only the existing LF-to-CRLF
working-copy warnings for the modified tracked Java/PowerShell files; no
whitespace error was reported.

Pre-existing untracked End Rift evidence directories/files, the master-repair
plan, and `tests/.packet-trace-manifest.mf` were left untouched and are not
part of this Task 6 commit.

## Verification boundary

No Paper/Purpur live server, Bukkit harness, native Minecraft run, upload,
deployment, or player-visible runtime claim was made for Task 6. The runtime
adapter behavior is covered by the narrow source contract and plugin
compilation; live/native verification remains a separate evidence layer.
