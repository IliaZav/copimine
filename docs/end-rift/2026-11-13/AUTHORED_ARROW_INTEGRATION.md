# Authored arrow models: reviewed integration of the external draft

IMPLEMENTED and focused AUTOMATED-TESTED; Minecraft appearance NOT VERIFIED.

The original shared checkout remains untouched. Its uncommitted DeepSeek arrow
draft was imported as a bounded patch into the isolated runtime branch.

Confirmed defects corrected during integration:

* `cleanupEventArrow` used `candidate` after removing its declaration: compilation failure.
* Side lanes did not inherit the primary arrow's mesh. Each lane now keeps the
  same authored identity, with rain/explosive identity taking precedence.
* Active Fabric packaging omitted every new arrow definition/model/texture.
* Chunk-load reconciliation recognized only elite Snowball visuals and would
  reject a valid arrow display. It now checks the appropriate runtime owner.
* Boundary cleanup did not clear the new visual map. Removal, missing display,
  failed teleport and failed tagging now release it and restore a visible
  vanilla collision body as fallback. A successful mesh hides the vanilla arrow.
* Displays lacked teleport/interpolation settings; they now follow the same
  smooth tracking convention as the existing elite projectile meshes.

The three arrow meshes have separate geometry and UV atlases: bone fan bolt,
violet falling shaft and orange explosive head. Boss Rift Arrows/Edge Volley
use the fan mesh too. The physical Arrow remains the sole hit/collision owner;
an ItemDisplay never authorizes damage or progression. No extra projectile
leases, independent timers or AI mobs were introduced.

The reviewed web-bundle fallback is packaged too; the spider's primary thrown
COBWEB BlockDisplay is preserved. Existing rift-bolt, breath and void-burst assets
remain intact. New JSON resources have explicit LF rules for reproducibility.

Validation:

* Combined arrow/runtime/migration checks: **364 passed, 7 skipped**;
  `C:/Temp/cm-20261113-arrow-all-tests.log`.
* Production visual-owner probe includes spawn failure, tag failure, invalid
  transform, model replacement, follow refusal, visible fallback and cleanup.
* Modern server and Fabric client builds succeeded. Updated candidate pins are
  in the migration profile and server plugin lock. Both resource packs rebuilt.
* The earlier converted-arrow probe failed because its extracted-method fixture
  lacked the newly added presentation adapter hooks; the fixture was updated,
  and its original real-arrow lifecycle/damage assertions remain enabled.

Full regression/review evidence is recorded separately after completion.
Production, Prism, real Paper gameplay, native visuals and performance remain
unverified for this candidate. These meshes are not a claim that every spell's
art or the entire six-phase encounter has passed visual acceptance.
