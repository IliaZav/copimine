# Task 2 brief — deterministic five-wave objective policies

Read this file first. It is the exact requirements for this task.

Repository: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`, branch `codex/end-rift-event`.

Implement only the pure Java policy layer and its Java tests. Do not edit the Bukkit plugin main class, YAML config, persistence classes, client, runtime files, website, launcher, or unrelated tests. Do not dispatch agents. Preserve all existing uncommitted changes if any. Commit only the files listed below and write the report to the path supplied by the parent.

Create:

- `copimine-end-event/src/me/copimine/endevent/domain/PortalCapturePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/TowerDefensePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/HazardPlanner.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WaveObjectivePolicy.java`
- `tests/PortalCapturePolicyTest.java`
- `tests/TowerDefensePolicyTest.java`
- `tests/HazardPlannerTest.java`
- `tests/WaveObjectivePolicyTest.java`

No class may import Bukkit or touch files/worlds.

Required APIs and behavior:

1. `PortalCapturePolicy` exposes immutable `PortalState` and `tick(PortalState state, boolean occupied, long nowMillis)`. A portal completes after 5,000 ms of continuous occupation. A gap up to 450 ms is grace and does not reset progress; after grace, progress decays faster than normal. Repeated calls at the same timestamp are deterministic. Provide enough state fields for multi-portal callers to distinguish completed, progress, last occupied time and last update time.

2. `TowerDefensePolicy.start(int players, long nowMillis)` returns immutable `CoreState`: base 1200 HP, +300 HP for each player beyond 2, maximum 4200 HP, deadline exactly 180,000 ms after start. `damage(CoreState, String attackId, double amount)` is idempotent by nonblank attack ID and fail-closed for invalid/negative amounts. `finish(CoreState, long nowMillis)` distinguishes success and failure; failure is timeout or core HP depleted. Include attempt/retry state helpers with a clean retry that restores full HP and starts a new 180-second deadline.

3. `HazardPlanner.plan(int minX, int maxX, int minZ, int maxZ, Set<Point> protectedPoints, Pattern previous, int maxMutated, double minimumSafeRatio, long seed)` returns a deterministic bounded plan with no protected point mutated, no more than `maxMutated` hazard cells, at least the requested safe-floor ratio, a connected safe area, and exact original-block snapshot keys. Define immutable `Point`, `Pattern`, `Cell`, and `Plan` records/enums as needed. Invalid bounds/caps fail closed. The planner must not select a cell outside the inclusive rectangle.

4. `WaveObjectivePolicy` maps waves 1–5 to `AWAKENING`, `HUNT`, `PORTALS`, `TOWER_DEFENSE`, and `RIFT_STORM`; exposes objective titles, mob caps, and completion predicates without Bukkit types. Ensure an unknown wave is rejected or returns a fail-closed result.

Tests must cover continuous portal capture, grace, decay, concurrent independent states; tower timer/scaling, idempotent attack IDs, failure/retry; hazard protected exclusion/cap/safe ratio/connectivity/seed determinism; and all five objective names/caps/completion predicates. Tests use the repository's executable-main style (no new dependency). Run the focused Java test classes using the existing test harness conventions and report the exact command/output.

Report path: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\.superpowers\sdd\2026-08-27-end-rift-survival-architecture\task-2-report.md`.
