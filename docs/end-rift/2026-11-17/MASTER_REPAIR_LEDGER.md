# Master repair — verified baseline and acceptance ledger

## Source authority / Gate A

Task: `D:/Downloads/CopiMine_End_Rift_Master_Fix_Prompt_2026-11-17.md`, SHA-256
`026875e2cfdd902086049808280270d5fefaecbc0ac3455c16f0bbf2655112fb`.
The Nov 11 master contract and newer explicit decisions remain authoritative
except where the Nov 17 task supersedes them (notably physical Wave 6 pillars
and the unique tower skeleton). The two Oct 27 canonical files were reread;
their hashes remain `6e1245b4...` / `0c781e42...` as recorded in AGENTS.md.

Implementation branch: `codex/end-rift-master-repair-20261117`.
Clean managed worktree base: `b2d4f7623a0a2d25cb1dde85ec392b83bec56e64`.
Merge base includes published polish `644df9af` and Wave 7 branch `2c8ba6bb`.
Verified origin polish HEAD at baseline: `644df9af`; origin has no merge branch.
Source integration `C:/Temp/cm-merge-20261117` is left untouched: dirty gameplay,
generated poses, tests, binaries, modpack manifests and archives are preserved.
The earlier polish worktree remains dirty and is also untouched.

The merge worktree's uncommitted RealitySplit restore change admits its
disposable two-room dev probe while preserving official one-arena recovery.
It is recorded for selective integration with its behavioral regression, not
silently discarded or treated as permission to restore official chambers.

Decisions:

- Nov 17 explicitly enables automated/live testing, superseding the earlier
  instruction assigning tests only to DeepSeek.
- Official Wave 7 stays one open arena. Dev-only fixtures must not redefine it.
- Wave 6 stays Rift Ram: four one-hit pillars + three-hit final physical tower.
- Dead large defenders never respawn; final cocoon collapse stays permanent.
- Test on isolated Paper/client. Production worlds/DB and installed profile
  are not test fixtures. Previous deployment authorization is not evidence that
  a new candidate satisfies the new test/live release gates.

## Requirement -> current owner -> evidence

| Requirement | Code owner | Baseline / status | Required proof |
|---|---|---|---|
| One-viewer model/pose bind | `CopiMineEndEvent.bindEventEntityClient`, `sendTentacleAnimationVisualUpdate` | CONFIRMED call chain invokes broadcast from per-viewer binding; behavioral reproduction next | 2/5/10/11/20-viewer fanout, 38 actors, late/repeated binding |
| Bounded, ordered V4 presentation | `bridge/EndEventBridgeV4Sender` | FIFO 256 applications; budgets 24/s; replaceable body coalescing absent in inspected path | Signed queue tests, overflow, lifecycle fences, metrics |
| Accepted-hit ledger | `runtime/LastSealConstructRuntime.tick` | Investigate receipt before rejected hit | Rejected/accepted/reentrant generation tests |
| Physical Wave 6 | `runtime/RiftRamEncounter`, hazard journal | Audit display-only geometry and top support before redesign | Real collision + durable mutation/recovery |
| Unique tower skeleton | Ram runtime, client model catalog/renderer | New Nov 17 dedicated actor/asset requirement | Editable source, three physical shot families, falling/grounded AI |
| Stomp damage | `RiftRamEncounter.stomp` | Suspected integration gap; not yet reproduced | Expanding front/contact/miss/once-only damage |
| Fog/refill receipts | Wave 5 adapters, volume shader | Preserve merged depth fog; inspect spawn accounting | Behavioral failures/retries + real client |
| Echo potions | Wave 7 personal Echo cast pipeline | New merged toolkit retained; inspect ballistic feasibility | Gravity/elevation/terrain probes + client |
| Guardian decoy typing | client Illager/Enderman render adapters | Audit actual generic state routing | Adapter integration + real Pillager model scene |
| Phase 3 modelled geysers | fracture runtime/client scene | Audit original/model pipeline; no visual PASS | Exact-cell damage + original block restore + F2/video |
| Boss 1–6 and Kagune | boss coordinator/runtime/animator | Recent fixes preserved; not complete encounter evidence | All-phase mechanics, interruptions and visuals |
| Tests/CI | pytest, event/bridge runners, Gradle | Prior red CI and stale fixtures are not waived | Fix harness drift, behavioral tests, actual required jobs |
| Native/performance | isolated Paper/Fabric + PostgreSQL | NOT VERIFIED | Scenarios and measurements at all required player counts |

Gate A source reconciliation is recorded; the entire assignment is NOT DONE.
Rows are promoted only with actual test/runtime/visual evidence. No production
or installed-client replacement has been performed by this new branch.
