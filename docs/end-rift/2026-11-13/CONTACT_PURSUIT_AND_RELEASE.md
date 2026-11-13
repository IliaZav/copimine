# Grounded pressure and a single projectile flight

Status: IMPLEMENTED, focused AUTOMATED-TESTED; native Minecraft NOT VERIFIED.

The existing tactical selector ignored its target-distance argument. An orbit
beat could abandon a valid hand-strike opportunity. `MELEE_PRESSURE` now keeps
the 3.5-block contact band in every grounded phase, closes directly during the
approach beat, and stays active throughout the post-cocoon melee finale. The
runtime passes the generation-fenced cocoon state explicitly. Core exclusion,
safe floor validation, cast ownership and target hysteresis remain stronger
owners. The approach destination is relative to the target, not arena center;
it does not path into the player's collision box.

User-approved animation tuning: Swipe2 and Ground Slam 2x; Chest Strike 1.75x.
Original clips, bones and pivots remain intact. Shared `BossAnimationPlayback`
drives server release/contact ticks, animated hitboxes and the active Fabric
pose sampler. Contact rounds forward, never before the matching pose. Death
and unrelated animations are unchanged.

Previously every ranged cast ran an eight-tick cosmetic flight to its target,
showed an impact there, and only then spawned the actual arrow/fireball/bolt
back at the Guardian. Physical spells now release immediately at the committed
marker. Their real projectile owns flight and collision presentation. Other
spell deliveries retain their existing lifecycle. Do not restore the generic
flight wrapper around physical spells when merging later DeepSeek changes.

Validation: Python 3.13 `pytest` over contact pursuit, projectile release owner,
cast navigation, actual damage admission, telegraph timing, pose contract,
Edge Volley and current encounter contract: **247 passed, 2 skipped**.
Log: `C:/Temp/cm-20261113-contact-release-tests.log`.
Modern `endEventJar`: BUILD SUCCESSFUL, log
`C:/Temp/cm-20261113-release-owner-build.log`.
The earlier run without an isolated pytest temp directory hit Windows cleanup
permissions and is not used as acceptance evidence.

Remaining: real-client combat, path quality around the actual arena, audiovisual
quality, all six phases, full CI and release pin refresh. Production and the
working Prism client were not replaced.
