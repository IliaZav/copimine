# End Rift visual regression repair

## Scope

Repair only the supplied End Rift boss/mob visuals, normal-phase boss grounding,
Last Seal shield presentation, and the checks needed to prove those paths.
Preserve the supplied `enderboss.png` bytes and signed UV rectangles. Do not
rewrite boss AI, wave behavior, combat balance, tentacle gameplay, or the model
importer unless a focused failing visual test proves it is necessary.

## Evidence-first diagnosis

- Reconfirm the exact boss, mob, and animation resources in the supplied
  archives and compare them with client resources and installed artifacts.
- Trace server UUID visual bindings through the client model-selection path for
  Endermen, Skeletons, Spiders, boss, tentacle carriers, and shield carriers.
- Record the local server's current state before any restart; preserve its
  world and event state.
- Treat a passing unit/asset test as separate from an in-game visual pass.

## Focused implementation

- Add regression coverage for grounded, airborne-upward, airborne-falling, and
  scripted boss velocity; preserve natural gravity during ordinary pursuit.
- Keep the Last Seal shield count and gameplay authority unchanged while
  tightening only the visual orbit if evidence confirms excessive spacing.
- Change only the minimal visual/model dispatch code if a targeted test exposes
  a broken UUID or resource-selection branch.

## Verification and handoff

- Run focused Java tests, the End Rift contract suite, client build, resource
  pack validation, and exact installed-artifact hash checks.
- Gracefully restart only the isolated local server when a rebuilt plugin must
  be loaded; retain the arena/world and current event state.
- Recheck server/RCON readiness and visual bindings. Do not call the result
  player-visible until the client is opened with the exact profile and current
  front/side/back, locomotion, and shield evidence is reviewed.
