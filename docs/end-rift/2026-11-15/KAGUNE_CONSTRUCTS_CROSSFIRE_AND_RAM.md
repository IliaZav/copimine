# Final-seal constructs, crossfire ownership and Ram continuation — 2026-11-15

## Scope and status

User requested local implementation and GitHub commits, leaving tests to DeepSeek.
No tests, Paper, Minecraft, Prism replacement or production deployment were run.
Compilation and archive inspection below are not gameplay/visual acceptance.

Start: `2e02e6d70b4ebd2f712dc241b9aed41b682c7287`.
Built implementation: `b049ade2062a17b811c277432f51726cd255cf60`.
Publication target: `origin/codex/rift-guardian-polish-20261110` (normal fast-forward).
Own checkout: `C:/Temp/cm-phase6-runtime-20261112`.
Protected dirty integration/source-of-truth worktrees were not changed.

Both canonical files in D:/Downloads were read and SHA-256 rechecked:
- Waves: `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf`.
- Guardian: `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50`.
Latest explicit overrides remain in force: one open Wave 7 arena, Rift Ram in
Wave 6, permanent defender deaths, one cocoon fall and permanent melee fury.

## Guardian: missing Phase 6 execution paths

The master contract's directed stab and grouped ground eruption had no actual
boss-spell dispatch. Added `KAGUNE_STAB` and `KAGUNE_ERUPTION` through the real
vocabulary, ability admission, LAST_SEAL pool, brain weights and executor.
They are disabled after cocoon destruction; fury retains its existing melee pool.
Grouped punishment favors eruption; spread punishment favors directed thrust.

`LastSealConstructRuntime` owns event/generation/boss scope, admission, frozen
positions/heading, timeline, one-attempt-per-player hit ledger, accepted damage,
launch leases and cancellation/cleanup. It creates one ItemDisplay carrying up to
five instances of the original continuous Kagune mesh. No extra AI mobs, guardian
HP, defender slots, shields, grabs or respawn records are created.

Timeline: 30-tick ground warning, 16-tick emergence, 12-tick strike, 18-tick retraction.
The stab emerges behind the locked target point, bends back and thrusts along the
validated fixed heading; it does not track a dodging player. Eruption raises five
smaller rigs around one frozen ground point. Failed/blocked floor or travel
admission refuses the entire cast before damage or cooldown consumption.

`KaguneSpellFrame` shares the procedural root trajectory, scale and conservative
swept stem/player-AABB geometry between client and server. This is a body-volume
approximation, not per-vertex or full animated-bone collision. Eight temporal
substeps account for relative motion. The first STAB damaging frame clips away
progress before .25, including its corresponding player-motion interval.
Rendering preserves source hierarchy, pivots, skin and UVs. Original rigs are
batched in the existing tentacle render submission; they are not particle stand-ins.
Actual appearance/contact alignment remains unverified in Minecraft.

Initial engineering tuning: stab 8 HP, eruption 7 HP, .65 rig scale. These damage
values are not presented as exact user-mandated numbers and require playtest
balance. Accepted eruption launches use .98 vertical velocity and a single
world/generation-scoped fall lease; a rejected hit gives no launch or knockback.
Owner fences are rechecked during and after synchronous Bukkit damage callbacks.

## Cocoon anti-camping combination

Five continuous seconds of close horizontal pressure prepare an owned radial
repel, then a half-second pause, then the existing three-row ring. The combination
has its own 28-second active-tick cooldown, with a bounded two-second admission
retry when a feasible ring/gap layout cannot be found. It cannot replace another
committed major spell. Continuous pressure observation happens during other casts;
admission happens after cast recovery advances, so an overdue ordinary spell
cannot always steal its first free frame.

The ring runtime owns the warning/repel/pause and normal rows as one scope. Phase
exit, broken cocoon, missing carrier, canceled encounter and rewind stop it.
The shove checks the entire short arena corridor, not just a far endpoint. It adds
no blindness, damage or player hold. Animation, warning and impact audio/geometry
are emitted once at their appropriate stage rather than restarted by heartbeat.
Cooldown and dwell are transient generation-scoped combat state, not a durable
victory/reward receipt. A restart reconstructs no half-executed disposable cast;
full restart/recovery acceptance is still assigned to DeepSeek.

## Wave 7: genuine paired cast ownership

The original crossfire wrote preparation only to its leader. The second skeleton
could continue a solo attack and receive no TRIAL release, while the leader later
forced a second fan from it and overwrote its checkpoint.

Both skeletons now acquire matching cast deadlines, frozen aim and WINDUP frames
before either starts. Both must be out of cooldown. A failed second checkpoint
rolls back the first before any presentation. The follower waits for the matching
leader instead of canceling its own preparation at the release deadline. The
leader alone releases both wings once, validates the second actor's matching
checkpoint, emits RELEASE only for wings that spawned arrows, and gives both the
same recovery deadline. Losing a member or a matching checkpoint cancels safely.
The one-open-arena routing and existing physical arrow/model ownership remain.

## Wave 6: focused AI and one accepted impact cue

`RiftRamEncounter.impact()` entered STUN twice for one collision, replaying its
new audio twice. It now enters once before the durable impact checkpoint.
The final pillar marksman retains a legal target for 60 ticks, prefers visible
players on re-evaluation, and keeps pursuing a covered target when none are
visible. Its corrupted display name was repaired. Endermites reject dead,
disconnected, wrong-world and non-roster targets. Charge contact stops processing
if a synchronous damage callback closes the encounter.

## Audio and packaging

Two original deterministic one-shots, `kagune_stab` and `kagune_eruption`, extend
the existing generator/catalog. Previous 13 OGG payloads stayed byte-identical.
Emergence and impact audio are placed at the actual spell origin. Listening/mix
quality is unverified. All 15 catalog entries and OGGs ship in both the required
26.3 client and resource pack; no Iris/additional audio mod is needed.

`KaguneSpellFrame` and its nested Point class are packaged with EndEvent and client,
excluded from Narcotics, and included in the standalone plugin compiler source list.
The latter script was updated, not independently executed in this continuation.
The legacy 1.21.1 client was not rebuilt: its shared source/resources did not change.

## Build evidence and independent review

Pinned Temurin 25.0.4.1+1 / Gradle 9.8.1:

- `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain`
  — BUILD SUCCESSFUL, final build 6 s.
- `gradle -p tools/minecraft-26.3/client clean jar --console=plain`
  — BUILD SUCCESSFUL, final build 12 s.
- `python -X utf8 resourcepacks/build-resourcepack.py --skip-server-properties`
  — built pack; SHA-1 `c1246a657207a3eb83cbcd31e6b1622e1bc107e7`.
- Candidate archive CRC/entry inspection confirmed protocol/nested classes, runtime,
  existing spell meshes, six preceding release clips and all 15 OGG/catalog entries.
- `git diff --check` passed before commits. No test task was run.

CodeRabbit review against `2e02e6d7` completed on the `d604ad9e` snapshot with
one minor issue: STAB sweep included the preceding safe progress interval.
Confirmed and fixed in `528b6687`. That follow-up also adds owner fences and atomic
carrier creation and fixes post-recovery pressure admission. A separate review
against `1dd7d12d` completed on the `9559bea3` crossfire snapshot with zero issues.
Later standalone packaging and audio additions were built/inspected, not included
in that immutable review scope. OGG quality has not been auditioned.

## Candidate

Directory: `artifacts/20261115-kagune-crossfire-candidate/`.
Manifest pins implementation source, byte sizes, SHA-256/SHA-512/SHA-1 and explicit
`testsRun=false`, `nativeVerified=false`, `productionInstalled=false`.
Build and review logs are copied beside it.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `CopiMineEndEvent.jar` | 2544617 | `0f128ece1616615b0653c72c942ab878d801aab4f453d91f79a69cf2bc4c42ae` |
| `CopiMineNarcotics.jar` | 375086 | `0b44338f0e5f59be1d2691bfc7e62e91008bc7c15e8de4eebe4f9fbb730235a0` |
| `CopiMineClient-0.1.4+26.3.jar` | 30240460 | `2345eb55ed0cf307ccbf3ddbe3c399a1f2700b708dcc7be2548fd48e12db3846` |
| `CopiMineResourcePack.zip` | 27938837 | `c466df569030e045890d2c2bcbffa6ce653ad9ede39996aeb88c2d759345455f` |

## DeepSeek handoff: behavioral work, not source-name assertions

Do not remove the two new spell IDs to satisfy old exhaustive fixtures. Extend
those fixtures with the approved new production paths and shared protocol helper.

Required acceptance:
- Directed stab miss after moving away, contact boundary .25, swept movement,
  blocked floor/path, rotation, target death/quit and no damage before visible contact.
- Eruption's five real rigs, one direct hit across overlapping stems, canceled hit,
  launch height, protected fall, stale generation and complete retraction/removal.
- Pressure observations while busy; five-second continuity; free-frame admission;
  one repel, pause then all rows; 28-second cooldown; invalid escape layout; forced
  replacement/phase end/cocoon break/restart during every prelude stage.
- Paired crossfire with follower-first and leader-first tick order, both warnings,
  both actual release poses, one leader receipt, solo-shot exclusion, same recovery,
  one wing blocked, failed spawn/write, leader death and reload during preparation.
- Final-pillar marksman focus retention, LOS preference and pathing, invalid mite
  targets, exactly one impact sound, all seven pillar/armor receipts unchanged.
- 2/5/10/11/20 players, accepted/canceled damage, cleanup/recovery and current full
  event regression suites. Real client F2/video and listening plus measured load.
- Exact-SHA CI and a complete real Paper run remain NOT VERIFIED.

Installation remains local/staging only. When acceptance and deployment are later
authorized, update matching EndEvent/client/pack together; preserve the old files
and their hashes for rollback. No running world, database or installed client was
changed by preparing this candidate.
