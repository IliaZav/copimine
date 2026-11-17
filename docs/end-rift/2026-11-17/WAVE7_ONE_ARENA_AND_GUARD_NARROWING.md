# Wave 7 topology: one open arena, and the narrowed legacy-partition guard

**Date:** 2026-11-17
**Scope:** `copimine-end-event` Wave 7 (Reality Split) runtime, its snapshots and the
End Rift gate (`tests/RunEndRiftEventChecks.ps1`).
**Status:** IMPLEMENTED; pure-policy AUTOMATED-TESTED through the End Rift gate.
LIVE-PAPER and VISUAL verification remain BLOCKED (no live roster / no client capture).

## 1. Authoritative decision

The approved Wave 7 topology is **one open, sequential arena** shared by the whole
party. The guardian refactor that turned the isolated-chamber model into a single
sequential route is authoritative, and the legacy-partition guard that protects
durable checkpoints stays in place. Stale tests were rewritten to equal-or-stronger
claims rather than deleted.

Evidence in source:

- `runtime/RealitySplitChamberController.beginWave7(long, List<UUID>)` now always
  calls `beginOpenArena(...)`. The previous `count <= 3 ? beginOpenArena : begin`
  branch is gone, so no 2-3 player roster is sectored into physical partitions on
  the official path.
- `runtime/encounter/RealitySplitEncounter.requiredFor(...)` now sources its
  assignment from `ChamberIsolationPolicy.assignOpenArena(...)`, which returns
  `chamberCount() == 1` for any non-empty roster. The encounter therefore reports the
  same single arena the controller builds.
- `domain/ChamberIsolationPolicy.assignOpenArena(...)` places every roster member in
  chamber `0`; `ChamberIsolationPolicy.assign(...)` (the multi-chamber policy) still
  exists and is still unit-tested, but it is no longer on the Wave 7 event path.

## 2. Canonical divergence (documented, not silent)

The canonical Waves contract, section 9.1, calls for two-to-three eligible players to
receive **that many distinct trials without replacement** and four-plus players to
receive **all four approved trials** with partitions between them. The implemented,
approved topology instead runs the whole party in **one open sequential arena**.

This divergence is deliberate and reviewed:

- The one-arena route keeps the four trials but sequences them for the whole party
  instead of sectoring the group into isolated chambers. A group that must fight the
  stage together is not split.
- All Wave 7 passage/checkpoint/line-of-sight/archmage tests were rewritten to the
  single-arena contract (see section 5). None were merely deleted to make the gate
  green.
- The multi-chamber policy and the disposable probe remain so that a durable
  two/four-sector checkpoint is refused explicitly (section 3) rather than silently
  collapsed.

## 3. The narrowed guard (strengthened, not loosened)

`runtime/RealitySplitChamberController.restore(...)` (the official path) hard-refuses
a durable checkpoint whose `chamberCount()` is not `1`:

```
if (!allowSinglePlayer && restoredAssignment.chamberCount() != 1)
    throw new IllegalArgumentException("WAVE7_LEGACY_PARTITIONS_REQUIRE_EXPLICIT_MIGRATION");
```

The guard was **narrowed**, not removed: the official restore still refuses a legacy
two/four-sector checkpoint with a named error, and it publishes nothing (generation
and owned actor index untouched) when it refuses. `allowSinglePlayer` is the
"disposable" flag: the official `restore(...)` passes `false`; `restoreDisposable(...)`
passes `true`.

The **disposable local probe** (`beginDevSolo(...)` -> two physical rooms ->
`restoreDisposable(...)`) is the single documented exemption. It builds two rooms so
the passage/OPENING transaction can be inspected and round-tripped, and its own
snapshot round-trip would otherwise be unreadable. Exempting exactly that probe keeps
the boundary mechanics under test while the official path stays one-arena only.

## 4. Consequences checked by the gate

- `Acceptance evidence matrices and plugin inventory`: 21 passed.
- `Current pure Java policies`: the pure bundle compiles and every pure policy test
  runs. `RealitySplitChamberSnapshotTest` proves the official refusal names the
  migration requirement and that the refused data stays restorable through the probe.
- `EndRiftEncounterCoordinatorTest.testCanonicalSevenWaveLifecycle` proves a
  two-player roster is assigned **one** open arena, that a phantom second chamber is
  rejected, that the arena route completes once, and that it cannot complete twice.

## 5. Tests realigned to the single-arena contract

- `tests/EndRiftEncounterCoordinatorTest.java` (wave 7 section).
- `tests/RealitySplitChamberSnapshotTest.java` (disposable round-trip + official
  refusal + one-arena restore).
- `tests/test_wave7_passage_commit.py`, `tests/test_wave7_checkpoint_recovery.py`,
  `tests/test_wave7_line_of_sight.py`, `tests/test_wave7_archmage_step_permit.py`
  (sequential-route contract, explicit codec/refusal claims).

## 6. Not claimed

- LIVE-PAPER verification of the one-arena flow with a real roster: NOT VERIFIED
  (0 players online; the wave-7 test start refuses on an empty sandbox roster).
- VISUAL verification of arena geometry/partitions: BLOCKED pending a real client
  capture (F2).
