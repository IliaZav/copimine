# Performance report — End Rift Waves 1–7

Status: **BLOCKED / NOT MEASURED** for real combat performance.

Source commit: `d44c8f51be00f3b6d911135f7722a155cbf20c13`. No source change in this pass altered timing or AI budgets.

## What is known

- No authenticated player/bot run was available, so TPS/MSPT/memory under Waves 1–7 combat at 2/5/10/11/20 players is not measured.
- A historical idle-only sample had 0 owned living mobs and 0 projectiles. It is **not** combat or load acceptance and is intentionally not reused as a result.
- The repository has timing/policy tests for cast cadence, anvil volley limits, fog timing, obelisk cadence, projectile budgets and cleanup. These prove bounded policy constants, not runtime percentiles.
- Native hardware and the actual disposable runtime were not profiled after the latest install.

## Acceptance target

| Metric | Target | Current |
|---|---|---|
| TPS at 10-player combat | 20.0 sustained | NOT MEASURED |
| p95 MSPT at 10-player combat | ≤ 50 ms | NOT MEASURED |
| Memory growth across cleanup | no monotonic growth | NOT MEASURED |
| 20-player targeted load | bounded spawns/AI/projectiles, no crash | NOT MEASURED |
| Anvil stress (11–20) | ≤ 25 simultaneous visuals, one damage receipt per target per volley | policy PASS; runtime NOT MEASURED |
| AI/LOS budgets | bounded retarget/path/reposition | policy PASS; runtime NOT MEASURED |
| Sync disk per hit | none | source/contract reviewed; runtime NOT MEASURED |
| Soak | no leak across waves/repeats | NOT MEASURED |

## Required method

1. Start the disposable Paper runtime with the pinned profile and exact candidate jars.
2. Use real protocol players with the approved signer and normal admission.
3. Record `tps`, `mspt` (mean/p95/max), heap used/committed, entity/projectile/task counts, chunk counts and event-owned resource counts at wave start, peak combat, wave end and after cleanup.
4. Run 2, 5, 10, 11 and 20-player matrices; at 20, record honestly even if hardware cannot sustain it.
5. Repeat the 2- and 10-player runs to expose state leaks.
6. Store raw logs/timestamps and exact jar/hash/SHA; do not label idle data as combat performance.

No PASS is claimed until those measurements exist.
