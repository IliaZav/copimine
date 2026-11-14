# Guardian spell admission, target ownership and release repair

CODE IMPLEMENTED. Test execution and real-client inspection are delegated to
DeepSeek by the latest user instruction. No test files were added or changed.
No installed runtime, release artifact/pin or Prism profile was replaced.

Canonical inputs re-read for this task:
* WAVES SHA256 6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf
* GUARDIAN SHA256 0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50
The November 11 MASTER_REBUILD_CONTRACT and later explicit decisions supersede
old revival/ritual mechanics. Work starts at bb68ed64 in the isolated checkout.

## Production changes

1. bossActionAvailable is called before brain ranking AND direct dispatch.
   Inferno on cooldown, a full servant roster, exhausted Rift projectile capacity,
   consumed Final Strike and broken-cocoon summons cannot reserve a cast, show a
   fake warning and spend a complete cooldown with no gameplay action. Summon
   capacity uses living owned actors; actual spawn clears stale servant IDs too.
2. BossTargetSelector is the spell selector; rotateBossTarget alone owns pursuit.
   It no longer returns the pursuit focus unconditionally. Perception reads the
   spell-target history rather than pursuit history; accepted general telegraphs
   record cast recipients. Damage threat reads actual recent incoming hits.
   LOS affects target scoring. This does not call setTarget or refresh pursuit.
3. Ordinary Rift projectiles, arrow fans and fireballs receive the exact locked
   warning mark as their aim. They do not reacquire a fleeing player's location
   at release. Existing diagnostic overloads remain compatible. Once released,
   the native projectile continues to own flight, real collision and expiry.
4. Ground/arena/summon actions resolve at their authored release boundary. Removed
   the boss adapter's extra eight-tick fake particle flight to a player before a
   grounded effect. Physical projectiles retain their real separate bodies.
5. Ground impacts now briefly display their authored textured model at the real
   effect anchor. A Rift lance gets its finite authored impact and positional
   amethyst cue at native collision, rather than silently disappearing.
6. Echo decoys get up to twelve validated placement proposals around their chosen
   player, outside player/boss/other-decoy collision space. They spawn directly at
   that accepted location. Previously the selected player was discarded and all
   summons used the real boss as the ring origin. Each decoy initially hunts its
   own admitted anchor player. The renderer-compatible Pillager body, exact 50 HP,
   cap/cooldown, damage scale, generation fence and separate death cleanup remain.
   Test-boss decoys preserve diagnostic ownership rather than official tagging.

## DeepSeek handoff

Update any obsolete test expectations that force spell selection to hold the
navigation focus: only pursuit holds that lock. Verify focused target history,
rejected casts, LOS, actual group pressure and all six phases. Confirm no extra
particle-flight delay and native projectile miss after leaving a warning point.
Check Inferno cooldown, servant death freeing capacity and direct diagnostic
requests. Verify decoy separation/canceled placements and real Guardian rendering.
Verify collision effects do not falsely imply accepted player damage.

Compilation only: compileEndEventJava succeeded (13 s), compileJava for the modern
Fabric client succeeded (16 s). Neither invocation ran a test task.
Logs: C:/Temp/cm-boss-admission-visuals-server-20261114.log and
C:/Temp/cm-boss-admission-visuals-client-20261114.log.
No Paper, Minecraft, FPS/TPS, regression-suite or CI conclusion is asserted.
