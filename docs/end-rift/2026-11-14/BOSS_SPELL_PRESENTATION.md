# Guardian spell presentation repair — 2026-11-14

Scope: boss first, according to the latest user instruction. No production or Prism installation.
Start commit: `341a0df51af822fe8edded19e97f3ffc18e3a653`.

## Architecture and ownership

- `BossSpellGeometryPolicy` produces bounded, deterministic local-space curves for sixteen spell/impact families. It never applies damage. Each frame has at most 96 points. The Bukkit adapter transforms one frame once, then shares it with up to 32 eligible viewers; distance/party-size LOD reduces emissions. There is no full-world scan.
- `BossSpellPresentationEffects` owns only finite impact presentation. Every delayed frame rechecks event, generation, boss and cast ownership. A physical projectile owns its actual flight/contact; generic cast effects cannot manufacture a target impact before launch.
- `playBossSpellModel` creates at most two short-lived cosmetic ItemDisplays. They have no AI or combat hitbox, are tagged into the existing entity registry, and expire within 80 ticks. Movement uses the existing combat teleport permit. Cancellation, generation change, owner loss, failed movement and explicit cleanup remove both the actor and its ownership record. A failed tag also rolls back the inserted record.
- `GuardianSpellMesh` loads eight bounded cuboid meshes once. Each has at most 32 cuboids. Geometry, UVs, pivots and normals are validated independently. The material sheet is a standalone entity texture, not a block/item-atlas sprite: its non-power-of-two dimensions cannot change terrain-atlas mipmaps.
- `GuardianSpellRenderer` consumes immutable deferred render-state snapshots. ItemDisplays use private selectors 830200–830207. Actual rift Snowballs (830031) and rift Fireballs (830013) use velocity-aligned meshes instead of camera-facing item icons. Normal projectiles retain vanilla rendering. Position, collision, damage and removal remain server-owned; there are no client-created projectile actors or per-frame world queries.
- The existing signed bridge, generation fences, original Guardian/Kagune geometry, UVs and animation catalogue remain authoritative. This change adds no packet type and does not loosen the decoder.

## Repairs

| Defect | Reproduction / root cause | Repair / automated evidence |
|---|---|---|
| Void Blast and Void Mark followed a player after warning | Actual production adapters damaged a target moved from x=1 to x=20; the warned point was ignored | Both accept and clone the original locked point. The fleeing target is untouched; a player still at the warning point is hit. Both regression scenarios failed before the fix. |
| All spells used generic vortex/ring silhouettes | Composition colours differed, but the renderer shared two shapes | Sphere, double helix/lance, three arrow lanes, polygonal rune, arches, flame crown, diamond cage, seal, iris, cracks and other bounded families. Impact uses the same spell identity. |
| Duplicate warning audio | Telegraph adapter played its signature before the cue controller played another warning | The deduplicating cue controller owns the initial sound. Final Strike has one additional midpoint charge cue instead of recurring audio and per-tick beam packets. |
| Cue controller replaced cast animations | Inferno/fireball cues were outside the spell-driven animation guard | Ordinary spell clips and the dedicated Final Strike controller retain their animation ownership. Phase transition/death cues keep their separate lifecycle. |
| Every cast beat restarted chest strike | Active client mapped charge/release/recovery to the full chest clip | Four authored clips on the existing source bones: charge 1s, release/recovery 1s, independent recovery .6s, Final Strike 2.3s. Charge→release and release→recovery key poses match. Final Strike reaches its release at tick 40. Server hitboxes are regenerated from the same assets. |
| Long-range shield rendering | Shield display view_range used 64 as if it were blocks; it is a multiplier | Set to 1, representing 64 blocks. |

## Automated evidence so far

- Boss-focused Python suite: **332 passed, 2 skipped**, 162.78s. The two existing cover cases use physical collision rather than caster line-of-sight.
- Modern Fabric client build: **376 JUnit tests, 0 failures, 0 errors, 0 skipped**.
- Modern Paper plugin: `gradle -p tools/minecraft-26.3 endEventJar` passed.
- Authored mesh and animation generators have reproducible `--check` modes; server pose generator has `-Check`.
- The model lifetime adapter executes spawn, bounded concurrency, expiry, cast/generation/boss invalidation, failed movement, explicit cleanup and failed-tag rollback. The failed-tag case exposed and then fixed a real ownership leak during this implementation.
- The previous exact-SHA CI run 43509875552 failed one stale source-string assertion that required the buggy RAGE decoy fallback. Its production execution regressions passed; the obsolete assertion is corrected without restoring spider fallback. Full CI on the new final source still must run.
- The older long local validation transaction was interrupted, not passed. Its generated outputs were preserved separately; all 50 original snapshot paths were restored and verified before continuing source work. Snapshot: `C:/Temp/cm-boss-coherent-backup-341a0df5-20261114/manifest.json`; preserved interrupted outputs: `C:/Temp/cm-20261114-interrupted-validation-preserved/manifest.json`.

## Evidence boundaries / acceptance still required

IMPLEMENTED and focused AUTOMATED-TESTED are not native acceptance. The current build has not been connected to a real Minecraft client and inspected in motion. No new F2/video, all-phase visual acceptance, official seven-wave→boss→victory run, recovery matrix, or measured 2/5/10/11/20-player TPS/MSPT/FPS results exist for this source. `nativeVerified=false`.

Remaining work includes exact-final-SHA full CI, independent review, full Paper/client acceptance of every spell and phase, model positions/scales, audio balance, telegraph readability, real melee/ranged hit/miss reactions, cocoon fall/landing, and victory. The new material artwork and geometry are implementation assets, not Minecraft screenshots. Do not describe the whole boss as complete based on these checks.

No deployment authorization is inferred from code completion. Production and installed Prism artifacts remain untouched.
