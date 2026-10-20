# END RIFT OBSERVABILITY AND BUG AUDIT

## Run Identity

- repository: `IliaZav/copimine`
- branch: `codex/end-rift-event`
- baseline/reference SHA: `79781d8c8be3827078a77972ccba851df4eb819f`
- actual tested SHA: `8a01421b5921f7663c659b917bdfd14553a12632`
- dirty: `True`
- Paper: `Purpur local-runtime`
- Java: `21.0.10.0`
- diagnostic mode: `VERBOSE`
- automated gate: `PASS`
- diagnostic run directory: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\artifacts\end-rift-diagnostics\20260918-045615-8a01421b5921`

## Result Summary

**PASS**

- timestamps: `2026-09-18T01:56:16.623562Z` → `2026-09-18T01:59:03.964375500Z`
- server ticks: `0` → `1620`
- generations: `1162`

## 1. Diagnostic System Changes

Central structured events, bounded asynchronous JSONL output, sequence IDs, correlation IDs, burst capture, invariant monitoring, strict report parsing, and artifact identity are enabled for this run.

## 2. Bugs Found

No additional runtime bug is promoted to FIXED from this report without a structured reproduction bundle and regression evidence.

| ID | Severity | Subsystem | Symptom | Root cause | Regression test | Fix commit | Automated result | Live result | Native-client result | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| BUG-ER-AUDIT | INFO | observability | audit-only run | see machine-readable lifecycle fields | diagnostic report tests | current tested SHA | see Result Summary | see Live Paper Results | NOT VERIFIED IN NATIVE MINECRAFT | `summary.json`, `end-rift-events.jsonl` |

## 3. Root Causes

The machine-readable lifecycle and invariant results below are the source of truth; no success status is inferred from a console summary.

## 4. Regression Tests

- event records analyzed: `4381`
- event counts: `{"ENTITY/REMOVE": 292, "ENTITY/SPAWN": 317, "EVENT/COMPLETE": 2, "EVENT/READY": 1, "EVENT/SNAPSHOT": 122, "EVENT/SNAPSHOT_DUMP": 1, "OBJECTIVE/CANCEL": 14, "OBJECTIVE/ENTITY_PLACED": 16, "OBJECTIVE/READY": 1, "OBJECTIVE/START": 1, "PHASE/UPDATE": 2, "RECOVERY/ENTITY_REHYDRATE": 25, "RITUAL_CASTER/CHANNEL": 4, "RITUAL_PRISONER/CAPTURED": 1, "RITUAL_PRISONER/DRAIN": 1, "RITUAL_PROJECTILE/REMOVE": 6, "RITUAL_PROJECTILE/TIMEOUT": 6, "RITUAL_ZONE/CLEAR": 14, "RITUAL_ZONE/SPHERE_VISUAL_READY": 1, "TASK/CANCEL": 86, "TASK/COMPLETE": 6, "TASK/CREATE": 92, "WAVE7_BARRIER/CLEANUP": 18, "WAVE7_BARRIER/MUTATE": 608, "WAVE7_BARRIER/PLAN": 4, "WAVE7_BARRIER/READY": 4, "WAVE7_BARRIER/RESTORE": 2736}`

## 5. Runtime Invariant Failures

- none

## 6. Exceptions

- none

## 7. Wave 6 Ritual Audit

Correlated ritual lifecycle records are included in the central JSONL stream and are analyzed for prisoner, projectile, zone, and control leaks.

## 8. Boss/Hitbox Audit

- proxy lifecycle leaks: `[]`

## 9. Wave 7 Audit

- restore mismatches: `[]`

## 10. Recovery Audit

Recovery START/COMPLETE records and canonical snapshots are retained in the central stream when a live restart is run.

## 11. Cleanup Audit

- entity leaks: `[]`
- task leaks: `[]`
- control leaks: `[]`
- projectile leaks: `[]`

## 12. Client/Visual Audit

- native Minecraft verification: `NOT VERIFIED`
- static previews are not native Minecraft proof.

## 13. Performance and Diagnostic Integrity

- diagnostic events dropped: `0`
- write failures: `0`
- max queue depth: `309`
- log rotations: `1`

## 14. CI Results

- CI: `NOT RECORDED`

## 15. Live Paper Results

- live Paper: `PASS`
- diagnostic report: `PASS`

## 16. Native Minecraft Results

- native Minecraft: `NOT VERIFIED`

## 17. Artifact Hashes

- `CopiMineClient.jar`: `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce`
- `CopiMineEndEvent.jar`: `2607d993bcba3771705300f8f14ac28c30847866da3605ddc8da298bea4833a0`
- `CopiMineResourcePack.zip`: `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`
- `Purpur server jar`: `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c`


## 18. Commits

- tested SHA: `8a01421b5921f7663c659b917bdfd14553a12632`

## 19. Open Issues

- native Minecraft visual/client behavior remains NOT VERIFIED unless native evidence is indexed for this exact SHA.

## Verification Matrix

Each layer is reported independently. `NOT RUN IN THIS BUNDLE` and `NOT VERIFIED` are intentional evidence states, not implicit passes.

| Area | Automated/contract | Paper live | Native Minecraft |
|---|---|---|---|
| Seal capture | PASS (RitualPrisonerCapturePolicyTest) | PASS | NOT VERIFIED |
| 20-second drain cadence | PASS (RitualPrisonerHealthPolicyTest) | PASS | NOT VERIFIED |
| External-damage immunity | PASS (RitualPrisonerHealthPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Target exclusion | PASS (RitualTargetPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Sphere projectile origin | PASS (RitualSphereProjectilePolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Zone effects | PASS (RitualZoneEffectPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Control swap | PASS (RitualControlPairPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Amplifier roles | PASS (RitualCasterTacticsPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Boss oriented OBB | PASS (BossOrientedHitboxPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Animated hitbox pose | PASS (BossAnimationPosePolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Finite projectile sweep | PASS (BossProjectileSweepPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Proxy self-heal / dedupe | PASS (BossHitboxProxyReconciliationPolicyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| UV and skeleton parity | PASS (client/resourcepack gate) | NOT RUN IN THIS BUNDLE | NOT APPLICABLE (static/automated) |
| Evidence portability and hashes | PASS (test_end_rift_evidence_portability.py) | NOT RUN IN THIS BUNDLE | NOT APPLICABLE (static/automated) |
| Wave 7 command cleanup | PASS (RealitySplitBarrierRecoveryTest) | PASS | NOT VERIFIED |
| Wave 7 natural cleanup | PASS (RealitySplitBarrierRecoveryTest) | PASS | NOT VERIFIED |
| Wave 7 restart recovery | PASS (RealitySplitBarrierRecoveryTest) | PASS | NOT VERIFIED |
| Wave 6 restart recovery | PASS (RitualSphereEncounterSnapshotTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |
| Second-run idempotency | PASS (TransitionIdempotencyTest) | NOT RUN IN THIS BUNDLE | NOT VERIFIED |

## 20. Evidence Index

- `summary.json` is generated from the parsed JSONL records.
- `server-events.jsonl`/`end-rift-events.jsonl` is the primary structured evidence stream.
- `evidence-index.json` is authoritative for native screenshot/video evidence; unavailable native evidence is explicitly NOT VERIFIED.
