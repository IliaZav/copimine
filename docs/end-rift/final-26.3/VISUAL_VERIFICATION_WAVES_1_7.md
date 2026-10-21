# End Rift Waves 1–7: native visual verification checklist

**Status:** pending. No graphical Minecraft session or native F2 capture was used during the repair. Complete this checklist in a real 26.3 Fabric client connected to the pinned local Paper server. Headless protocol tests and static asset checks do not satisfy these visual gates.

For every item, capture an F2 screenshot or short video with the wave, player POV, event generation, and timestamp visible where practical. Record any discrepancy with exact reproduction steps and retain the original capture.

## Wave 1 — carrier and core

- [ ] Carrier item is visible in hand and has the correct model and texture.
- [ ] Carrier-to-core beam is visible and terminates at the correct core.
- [ ] Core charging animation and charging state are readable.
- [ ] Inventory indicator updates while carrying and after depositing the charge.
- [ ] Dropped-charge timer is visible and counts down accurately.
- [ ] Capture the item, beam, core, inventory indicator, and dropped-charge timer separately.

## Wave 2 — mark and phantom Endermen

- [ ] Marked player's first-person view shows the intended mark effect for the full 45-second period.
- [ ] Grey phantom Endermen render at the server-selected positions and remain distinguishable from real Endermen.
- [ ] Phantom aggression transition shows the open-mouth and raised-arm cues.
- [ ] Phantom activation materializes the corresponding real, colored Enderman with a readable transition.
- [ ] Unmarked player POV shows the intended shared fight without the marked-only shader.
- [ ] Shader and hidden-player presentation clear immediately on mark expiry, death, disconnect, wave clear, and reconnect.
- [ ] Allies become visible again after the mark ends.
- [ ] Capture marked and unmarked POVs, activation, and cleanup.

## Wave 3 — portals

- [ ] For each of the three portals, capture front, back, left, right, and top views.
- [ ] Close-up confirms frame geometry and inner surface have no gaps or z-fighting.
- [ ] Capture active, capturing, interrupted-capture, and closing states.
- [ ] Regular mobs visibly emerge from each active portal.
- [ ] Rift Giant visibly emerges from portal two specifically.
- [ ] Closed portals stop emitting mobs and leave no visual residue.

## Wave 4 — obelisks and reflected fireballs

- [ ] Capture each obelisk undamaged, damaged, critical, and fully destroyed from multiple sides.
- [ ] Fireball telegraph and trajectory point toward the selected target and remain dodgeable.
- [ ] Capture a genuine reflected fireball striking the correct obelisk.
- [ ] Destruction visuals and temporary collapse state clean up after the objective.
- [ ] Confirm unrelated projectiles produce no objective visual feedback.

## Wave 5 — fog and safe zones

- [ ] Capture fog from arena center and outer boundary at each of the three cycles.
- [ ] Emerald safe cells are in the expected positions and large enough for the supported player count.
- [ ] Green safety beams are visible and align with the safe cells.
- [ ] Capture each shrinking-zone phase and players occupying safe cells.
- [ ] Capture danger damage and safe-zone protection feedback.
- [ ] Capture AI pause/resume and confirm temporary floor blocks restore after each cycle and cleanup.

## Wave 6 — Sphere, guards, spells, and anvils

- [ ] Capture Sphere from outside: front, side, and top; then from the prisoner's POV.
- [ ] Capture caster hand/channel poses and the full guard roster: zombie, skeleton, Enderman, and spider.
- [ ] Capture Rift Giant aura and bounded summon visuals.
- [ ] Capture Gravity Anchor warning, pull, explosion, and recovery; include a successful dodge.
- [ ] Capture first and second anvil volleys, their warnings, falling anvils, impacts/misses, and cleanup.
- [ ] At 11+ players, capture each 5×5 volley and show exactly 25 active anvil visuals per volley, two volleys total.
- [ ] Capture prisoner Q/W/E/R UI progression and blue/violet target outlines.
- [ ] Capture fifth-caster-death release and ensure the Sphere and all effects disappear.

## Wave 7 — four trials and handoff

- [ ] Capture all four chambers from front, back, left, right, and top.
- [ ] Show physical wall collision from a moving player POV and confirm attacks do not cross walls.
- [ ] Capture each actor model and its actual attack telegraph/impact.
- [ ] Capture each separator opening independently as its assigned trial completes.
- [ ] Capture Echo appearance, duel, accepted damage, and valid player ownership.
- [ ] Capture natural Wave 7 completion, post-completion cleanup, player repair/heal, and the single 40-second handoff.
- [ ] Confirm the handoff reaches the existing boss transition without changing the boss encounter or its Kagune/tentacle mechanics.

## Evidence log

For each capture, record: filename, wave, POV/player, event generation, in-game time, reproduction command or action, expected result, observed result, and pass/fail. Attach original screenshots or videos without editing away HUD or context.
