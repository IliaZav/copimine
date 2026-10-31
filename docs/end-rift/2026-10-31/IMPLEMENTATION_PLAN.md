# End Rift Implementation Plan — 2026-10-31

Companion to `APPROVED_WAVE_CONTRACT.md`. That file says *what* is required; this
file says *where* it is implemented and *how it is proven*.

Branch: `codex/copimine-integration-20261030`
Baseline: `db1b732716915defec87db5169f83927e386f3d7`

## 0. Verified starting state (measured, not assumed)

| Item | Measured value |
|---|---|
| `python -m pytest -q -ra tests` | **5 failed, 2901 passed, 15 skipped, 782.54 s** (basetemp `C:/Temp/cpm-baseline-1334`) |
| Failing modules | `test_ci_release_job_contract`, `test_minecraft_migration_profile`, `test_minecraft_migration_server_plugins` (×2), `test_own_plugin_inventory_contract` |
| Dirty files at start | 23 modified, 5 untracked (backed up, see `VERIFICATION.md`) |
| `CopiMineEndEvent.java` | 2,690,404 bytes |

The audit's "752 selected tests / 2 failed migration tests" figure is not what
this tree produces. The five real failures are release-provenance drift, which
is master defect #6. They are fixed by reconciling provenance, never by relaxing
an assertion.

## 1. Work packages

Each package is one reviewable commit. Order is dependency order.

### WP-A — Provenance reconciliation (master defect #6)

**Files:** `tools/minecraft-26.3/profile.lock.json`,
`tools/minecraft-26.3/server-plugins.lock.json`,
`minecraft/server/plugins/*` installed jars, `local-runtime/end-rift-server-26.3/plugins/*`.

**Cause:** the two lock files are dirty relative to the built artifacts, so the
lock, the build output and the installed jar disagree. The fix is to make the
chain agree in one direction: build the artifact, hash it, record that hash in
the lock, install that exact hash.

**Proof:** `test_minecraft_migration_profile`,
`test_minecraft_migration_server_plugins`,
`test_own_plugin_inventory_contract`, `test_ci_release_job_contract` green with
unchanged assertions.

### WP-B — Wave 2: abolish the rotating 45-second mark

**Files:** `domain/WaveTwoHuntPolicy.java`, `domain/WaveTwoStagePolicy.java`,
`CopiMineEndEvent.java` (`tickCurrentHuntObjective`, `updateMarkedTargetObjective`,
mark lease fields), `tests/WaveTwoHuntPolicyTest.java`,
`tests/WaveTwoStagePolicyTest.java`, `tests/test_end_event_current_contract.py`,
`tests/test_end_event_v4_wiring_contract.py`,
`tests/test_end_rift_waves2_4_mechanics.py`.

**Change:** every eligible player is marked for the whole wave; the wave is
exactly three subwaves; the next subwave starts 3 s after the previous finite
roster is cleared. `MARK_ROTATION_MILLIS`, `markDeadline`, `markExpired`,
`waveTwoCompletedMarkPeriods` and the rotation gates are removed, not left dead.

**Proof:** new behavioural test asserts three subwaves, 3 s gap, all-marked for
the whole wave, and that no completion is possible on one or two subwaves.

### WP-C — Wave 5: five cycles + five-cycle safe-zone profile

**Files:** `domain/BlackFogTimingPolicy.java`,
`domain/BlackFogSafeZonePolicy.java`, `domain/BlackFogRecoverySnapshot.java`,
`domain/EndRiftObjective.java`, `CopiMineEndEvent.java`
(`tickBlackFogObjective`, the `cycles=3` log), `tests/BlackFog*Test.java`,
`tests/test_end_event_current_contract.py`.

**Change:** `CYCLE_COUNT = 5`, a cycle/roster profile that still produces usable
safe volume at cycles 4 and 5, snapshot validation against five cycles, post-fog
reinforcement hook after each fog removal.

**Proof:** policy test over cycles 0–4 × roster 2/5/20 asserts a non-zero safe
side and total capacity ≥ participants at every cycle.

### WP-D — Elite HP = exactly 3 × ordinary

**Files:** `domain/EliteCombatStatsPolicy.java`,
`CopiMineEndEvent.applyEliteCombatStats` + `configureEventMobStats`,
`tests/EliteCombatStatsPolicyTest.java`.

**Change:** the policy takes the ordinary role's *post-scaling* maximum HP and
returns exactly `3 ×` it. Remove `HEALTH_MULTIPLIER` and the `MAX_HEALTH = 256`
clamp from the health path. Delete the double-boost block at the skeleton
call-site so the multiplier is applied once.

**Proof:** test asserts `eliteHealth == 3 * ordinaryHealth` across rosters
2/10/20 and that no value is silently clamped; a restore test asserts
re-applying does not compound.

### WP-E — Wave 1: charge carriers are elites

**Files:** `CopiMineEndEvent.spawnCurrentCarrierReplacement`,
`domain/RiftCarrierPolicy.java`.

**Change:** the carrier is spawned through the elite path so it gets the 3× HP
and elite presentation; picking a carrier only considers live wave-combat
entities and never re-elites an ordinary mob twice.

**Proof:** test asserts the carrier spawn path calls the elite spawner and that
carrier HP equals the elite contract for the roster.

### WP-F — Wave 6 P0 prisoner placement

**Status:** already implemented in the pre-existing dirty tree
(`RitualPrisonerPlacementPolicy`, capture rollback, pending release). This
package adds the missing regression coverage and commits it as its own unit.

**Files:** `domain/RitualPrisonerPlacementPolicy.java` (new),
`tests/test_end_rift_wave6_prisoner_placement.py` (new).

**Proof:** placement accepted → `PLACED`; three consecutive refusals → `ABORT`
and the encounter refuses rather than half-starting.

### WP-G — ClearLag / EntityClearer announcement silence

**Files:** `minecraft/server/plugins/ClearLag/config.yml`,
`local-runtime/end-rift-server-26.3/plugins/ClearLag/config.yml`,
both `EntityClearer/config.yml`, `tests/ValidateCopiMineClearLagConfig.ps1`.

**Change:** `messages.broadcast-enabled: false` in the staging ClearLag copy
(the server copy already is false), EntityClearer player-facing strings empty in
both copies, console diagnostics kept. Cleanup itself keeps running.

**Proof:** validator assertions extended to require the silence flag in **both**
copies; a Python test asserts no countdown string is player-reachable.

### WP-H — Active client TAB: truncate the real styled name

**Files:** `tools/minecraft-26.3/client/src/main/java/me/copimine/client/TabPanelLayout.java`,
`mixin/PlayerTabOverlayMixin.java`, `TabPanelLayoutTest.java`.

**Change:** the truncated `Component` is what gets drawn. Add a layout-owned
`truncate(Component, width)`-style helper that cuts the actual styled sequence
(with an ellipsis) instead of only capping the measured width while passing the
full component to `context.text(...)`.

**Proof:** test asserts the drawn component's rendered width ≤ budget and that
a long styled name loses trailing glyphs rather than overdrawing the ping.

### WP-I — Shared regressions

Five new suites required by the contract:
`test_end_rift_arena_relocation.py`, `test_end_rift_participant_lifecycle.py`,
`test_end_rift_scoped_cleanup.py`, `test_end_rift_release_provenance.py`,
`test_end_rift_final_handoff_once.py`.

They cover wrong world/generation, duplicate outcomes, canceled
damage/teleports, death/quit, corrupt snapshots, DB failure, partial spawn,
restart, full-party wipe and three repeated start/abort runs.

## 2. Explicitly not attempted in this pass

Named so the next pass does not assume they were done:

- Sequential Wave 6 mage encounters replacing concurrent casters.
- Sequential Wave 7 trials and simultaneous personal Echo for every player.
- Exact safe-volume mask rendering (current client uses a visibility radius with
  a documented `shortcut:` comment).
- Wave 5 texture-loss root cause (needs native client observation).
- Native visual capture for any wave.

Those are **BLOCKED** on native verification or on a larger redesign, and are
reported as such rather than claimed.

## 3. Per-defect discipline

Reproduce through the production path → failing behavioural regression → smallest
coherent change → targeted tests → shared regressions → one commit.