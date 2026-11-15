# Wave 7 integration and code review — 2026-11-15

Latest explicit user decision: Wave 7 uses ONE OPEN ARENA for the whole party.
The old `chamber` fields remain serialized stage identifiers; they do not authorize new partitions.
Tests and native Minecraft acceptance were delegated to DeepSeek by the user.

## Provenance and review scope

Start: `04d8be93ec859f3ca39dd6fda8fe13808903494d`.
Reviewed and merged DeepSeek branch tip: `b0e9eb965f209752f9339273adc062e3bf5a0321`.
This delta contains 27 commits and 81 changed files. This is a bounded review of
that delta, not a claim of reviewing the repository's entire historical implementation.
Protected dirty worktrees were not edited. Source uses the current actor rigs and retains
Archmage UV fitting, original texture inputs and the existing server damage authorities.

## Confirmed defects repaired

| Defect | Cause | Correction | Evidence boundary |
|---|---|---|---|
| Groups >=4 returned to physical partitions | `beginWave7` chose the obsolete four-room branch; objective and repair paths also assigned sectors | All new starts/repair paths use the open route; objective expects its single completion | Implemented, compiled |
| One-arena player admission had a hidden angular seam/Core exclusion | `wave7PlayerRoom` still used sector geometry for slot 0 | One-slot route admits roster members anywhere inside the arena | Implemented, compiled |
| Restored old partitions could reappear | Multi-room snapshots remained accepted | Refuse explicit migration before changing controller state; retain original checkpoint | Implemented, compiled; no migration/restart playtest |
| Generic poses did not identify or time attacks | Vanilla AI flags never reflected scripted actions | Signed capability-gated action/stage/cast/elapsed/duration frames; immutable render snapshots and local-space clips | Implemented; no visual acceptance |
| Two animation implementations could stack | Both branches posed the same body | Timed clips override generic cast poses; hit/death own their interruption | Implemented, client compiled |
| Moving warning/release could reacquire a target's new position | Rendering, beam, arrows and fangs read live positions independently | Frozen per-cast points and fang marks; interrupted/restarted cast cancels without blind replay | Implemented, not behavior-tested |
| Delayed fans could use the next cast | Callback fenced actor/generation but not cast context | Capture the same aim-lock identity and cancel if replaced | Implemented |
| Pair lookup could select another trial's skeleton | Cached/search partners lacked stage/session checks | Validate stage slot and generation for both paths | Implemented |
| Cancelled attacks could fabricate RELEASE | WINDUP->RECOVER was interpreted as a hit | Explicit release from actual accepted execution only | Implemented |
| Hit/release poses lost their lease on the next actor tick | Pose map had no hold deadline | Preserve visual lease without changing combat timing; reject zero damage reactions | Implemented |
| Beam created two effects | Both beam resolver and dispatch rendered release | One dispatch owns release | Implemented |
| Fang group members had no corresponding warning positions | Group targets chosen on release, warning only primary | Freeze all group marks at warning and spawn each fang line on the same marks | Implemented |
| Spell models were detached static objects | Lease validated caster only and never advanced presentation | Facing locked; bounded lifetime, wave/role/session ownership, local bloom/gate motion and native-shot muzzle bodies | Implemented |
| Spear used a generic trident item | Snowball had no private mesh selector | Use existing velocity-aligned rift-lance item data | Implemented |
| Release particles could show a distant impact before arrow arrival | Full static trail/burst emitted at launch | Restrict physical-shot release to muzzle geometry; actual projectile retains collision | Implemented |
| Dust data sent for non-dust particles | Accent colour was treated as a particle data type | Typed data only for DUST; explicit fallback colour | Implemented |
| Purported 3D burst was a helix | Burst sampled a single ring with rising Y | Bounded golden-angle shell; orthonormal directed lance | Implemented |
| Death pose could vanish with authoritative actor | Real entity cleanup owned both combat and model lifetime | Bounded invulnerable cosmetic carrier, same model family, 1.5s animated collapse; no objective/loot ownership | Implemented, no native verification |
| Standalone plugin missed new frame class | Gradle copied only three selected protocol helpers | Include frame and nested enum classes; retain bridge copy for signed codec validation | JAR entries inspected |

## Build and artifact inspection

Executed with pinned Temurin 25.0.4.1+1 / Gradle 9.8.1:

- `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain` — SUCCESS.
- `gradle -p tools/minecraft-26.3/client clean jar --console=plain` — SUCCESS.
- `python -X utf8 resourcepacks/build-resourcepack.py --skip-server-properties` — SUCCESS.
- ZIP CRC/entry inspection confirmed all three JARs contain `Wave7AnimationFrame`, the
  client contains `Wave7ActorAnimator` and the four imported spell meshes.
- Earlier compilation and archive inspection failures were repaired; they were not passes.
- No Java/Python test suite, Minecraft, bot combat or live encounter was launched.
- CodeRabbit result is recorded separately when returned; no unreturned review is a pass.

## Artifacts

Source implementation: 1ef49f937bc772b55cea872a60a5a4962f6d6a03.
Local folder: `artifacts/20261115-wave7-reviewed-candidate/`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| CopiMineEndEvent.jar | 2515792 | `9b6f980b3c14b71f3f4144d750be9b292e66ffb3fb8853a7a3d3beb6e8e86f85` |
| CopiMineNarcotics.jar | 375086 | `0b44338f0e5f59be1d2691bfc7e62e91008bc7c15e8de4eebe4f9fbb730235a0` |
| CopiMineClient-0.1.4+26.3.jar | 30107971 | `dbf122de4510b1feaf8db0edba9b2e0735ae516160726d0c2a2fa7e12ccb3dfe` |
| CopiMineResourcePack.zip | 27806472 | `a2e25fcc276ff9be9ad361ac3402648b667ed595752edbd37f19f8343309f6d9` |

Profile/server candidate pins and client-size documentation match these bytes.
`nativeVerified=false`; testsRun=false; productionInstalled=false.

## Deployment and outstanding acceptance

SSH to both `90.188.117.117:2222` and `92.126.27.114:2222` closed before authentication.
No new production runtime, public download or Prism profile was replaced in this pass.
Once connectivity is restored, verify the actual installed hashes (DeepSeek may have
installed a newer release), create fresh restorable backups, stage all coordinated
JARs/pack, atomically replace the verified target and check fresh startup logs.
Do not blindly use the previous release's allowed hashes or replace unrelated plugins.
Rollback uses the new deployment's own backups; never overwrite newer world/DB data
with an older archive automatically.

DeepSeek acceptance: 2/5/10/11/20 players, sequential route and return after each
miniboss, cancellation/restart during casts, every arrow fan and locked beam dodge,
all fang marks, texture/pivot/UV inspection from all sides, hit/death carriers,
late viewer/rejoin, signed old-client capability fallback, zero orphan displays,
actual FPS/MSPT, clean restoration and final handoff. Old four-room checkpoints
require an explicit migration decision rather than automatic progress rewriting.
