# Cocoon runtime repair — 11 November 2026

Baseline reviewed: `97b8e43f69e7bd30e2372517a40555d9783429aa`.
Authority: sections 17, 22 and 28 of `MASTER_REBUILD_CONTRACT.md`.

## Reproduced defects in the actual adapter

The earlier pure-policy and source-wiring tests did not exercise the movement
owner and therefore missed these failures:

1. `tentacleVulnerabilityOpen()` returned before `tickCocoonFall()`. The last
   defender's death enabled ordinary AI without running the descent.
2. The fall hook returned false to the caller, allowing the ordinary leash,
   cast hold and pursuit to overwrite its position in the same tick.
3. `heightDuringFall` received absolute world Y instead of height above the
   landing surface. Positive-height arenas dropped almost immediately;
   negative-height arenas behaved differently.
4. The landing passed total descent time into a stage-local 20-tick clock.
   Its recovery consequently lasted one tick.
5. Raw `Entity.teleport` bypassed the event's internal movement permit, and its
   return value was ignored. A rejected movement could still emit an impact.
6. The permanent-death ledger never registered successful guardian spawns and
   was not reconstructed from saved HP. Recording a death could therefore do
   nothing, and restore could re-enable the old respawn branch.
7. The fall stage and elapsed sample were absent from the saved objective map.
8. The 3x incoming-damage policy and melee-fury spell profile had no callers in
   the three real boss damage adapters or either spell-selection entry point.
   The purported melee-only profile also contained the ranged Chest Strike.
9. The last-defender predicate still demanded a respawn deadline, although the
   new death handler removed that deadline. Genuine permanent kills therefore
   never started the fall. The predicate now requires actual death-ledger receipts.
10. Individual guardian kills did not checkpoint before publishing destruction.
    Each permanent kill now commits its HP/death state before visual/sound/death
    presentation; a failed write enters recovery without publishing a kill.
11. Already running cocoon casts, rings and projectiles were not cancelled at
    fall start. Their owners now end before descent begins.

## Repair

The descent owns all movement through 40 fall ticks and 20 landing ticks.
Internal movement permission and cancellation are honoured. Three consecutive
relocation refusals enter explicit recovery, without advancing the clock or
publishing a landing. The real hitboxes update while ordinary combat is held.

The versioned `boss-cocoon-fall-v1` checkpoint records the next sample to
execute. Stage boundaries are committed before publishing impact/fury; intermediate
samples use the existing ordered asynchronous writer every five ticks.
Restoring landing/fury does not replay the impact. Invalid or unprovable old
post-break snapshots require recovery rather than recreating a cocoon.

Existing persisted guardian health reconstructs living and permanently destroyed
slots. Successful materialization registers a living slot before combat. The
new damage multiplier acts on genuine HP only in current-generation melee fury;
the ordinary event, proxy impact and projectile sweep each apply it once.
Both automatic and explicit spell entry points use the restricted fury pool.

## Evidence and limits

- New production-adapter regression: initially **4 failed**, then passed for
  arena floor Y=69, 101 and -20 plus cancellation/bounded recovery.
- Focused cocoon/damage suites: **78 passed**.
- Guardian recovery/profile integration: **12 passed** before adding the
  dedicated fall-restart matrix.
- `endEventJar`: **BUILD SUCCESSFUL**, exit 0.
- Candidate: **2,385,635 bytes**,
  SHA-256 `af45b9a27937a6c275b0a2f0612b4b64fd222fe8ed56a3ba3557cb1746aa1b04`.
- Full recompilation (`endEventJar --rerun-tasks`): identical candidate SHA-256.
- Stage-only installer: **Verified 30 pinned plugin candidates**, exit 0.
- Latest death/fall/restart/wiring regressions: **41 passed**; actual guardian
  damage-window/durable-kill adapter: **10 passed**.
- CodeRabbit first review: **0 issues**. Follow-up review: **1 minor issue**,
  an accepted snapshot boundary one tick too early. Confirmed and fixed; **40
  focused tests passed**, including both boundary rejections.
- Pure Java policy list: all passed. Two stale Java tests were corrected to
  master requirements (5% regeneration cap; independent 30-enemy small-Kagune
  cap and no forced expiry), retaining invalid-input, HP and capacity checks.
- `RunEndRiftEventChecks.ps1 -SkipBuilds`: **exit 0**, final message
  `End Rift current local checks passed.` Log: `C:/Temp/cm-fury-endrift-gate-complete.log`.
- Frozen-source full regression: **3392 passed, 14 skipped, 124 warnings,
  2 subtests passed**, exit 0, 678.90 seconds. Log:
  `C:/Temp/cm-fury-frozen-full.log`. Skips include absent isolated/native
  runtime and database fixtures and platform-specific filesystem checks;
  none is counted as native acceptance.
- Final CodeRabbit review of the complete repair: **0 issues**, exit 0.
  Receipt: `C:/Temp/cm-fury-frozen-coderabbit.ndjson`.
- Earlier exploratory runs exposed stale-client-output/source-selector tests
  and pin expectations loaded before a candidate rebuild. Those runs are not
  reported as full passes; the frozen run above is the full-suite evidence.

## Integration ownership

An external executor committed the staged repair as `1ecd90b1` and published
the identical tree as `171580fe` while these checks were running. Both tree
IDs were verified as `97bf98946236e1c0a57d4bcbc96432a515752042`.
The user then confirmed stopping that executor and assigned continuation here.
The two histories were merged without changing source or discarding either
commit. This record distinguishes the actual implementation and verification
from the external commit operation.

The local reviewable bundle is `artifacts/cocoon-runtime-af45b9a2/manifest.json`.
It contains the pinned plugin, unchanged bridge, client and resource pack, with
`productionInstalled=false`, `prismInstalled=false`, `nativeVerified=false`.

This establishes adapter behavior and artifact provenance. It does not verify
the rendered fall pose, landing animation, camera response, final melee combos,
or any native Minecraft screenshot. Those remain open acceptance work.
Production and the installed Prism client have not been modified by this repair.
