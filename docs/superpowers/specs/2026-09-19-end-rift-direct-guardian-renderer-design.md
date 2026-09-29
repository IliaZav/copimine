# Direct End Rift Guardian Renderer

## Status

Approved architectural direction: replace the lossy vanilla `ModelPart` importer
for the supplied End Rift boss with a self-contained, Chameleon-compatible
geometry renderer. This specification covers only the client visual path for
the End Rift guardian. It neither changes server combat state nor replaces the
separate event Enderman, Skeleton, or Spider renderers.

## Problem

The supplied assets are a Chameleon/Bedrock geometry package:

- `geometry.json` contains 16 bones, 119 cubes, and 714 explicitly mapped
  faces;
- `end_rift_user_boss.png` is the original 128 by 128 atlas; and
- the source uses absolute bone/cube pivots plus rotations at multiple levels.

`UserEndBossModelData` currently tries to translate this package into the
vanilla Enderman `ModelPart` tree. `ModelPart` stores child pivots in parent
local coordinates. Chameleon instead visits each bone with an absolute pivot,
then applies the parent transform stack. The conversion cannot preserve both
semantics when it folds the model into a vanilla skeleton. The visible result
is disconnected or distorted limbs, horns, and incorrectly oriented face
textures.

## Goal and acceptance criteria

The boss rendered for a bound End Rift guardian must be a complete, connected
dark humanoid based on the exact supplied geometry and atlas. It must retain
the long arms, lower legs, head, and paired horn structure shown in the target
reference rather than fall back to a vanilla Enderman.

The implementation is accepted only when all of the following hold:

1. The production JAR contains source-equivalent geometry and atlas bytes.
2. The direct renderer visits all 16 bones, 119 cubes, and all declared faces.
3. Every face reads its original UV rectangle, including negative UV sizes.
4. Rest-pose matrix results for a nested lower arm and a rotated horn match the
   source Chameleon transform sequence.
5. A bound in-game guardian has a connected head, horns, arms, torso, and legs
   from front and side views, with no vanilla Enderman body visible.
6. Existing event-mob texture routes remain selected only for their own entity
   types; the guardian renderer is scoped by the existing boss UUID binding.

## Selected design

### 1. Keep the existing network and entity boundary

The Paper server continues to create an Enderman for collision, combat, AI,
and UUID binding. The Fabric client continues to select the guardian only when
`ClientBridgeProtocol` identifies that Enderman as the current End Rift boss.
No resource-pack, server-side model, hitbox, or gameplay change is part of
this design.

`RiftGuardianModel` remains the Enderman-renderer adapter so the scoped
renderer mixin does not change its entity-selection contract. Its normal
vanilla `ModelPart` children become compatibility carriers rather than the
source of the guardian mesh.

### 2. Add a direct source-geometry renderer

Create a focused client-side geometry module that reads the supplied JSON once
and holds immutable source bones, cubes, face UVs, and a parent-to-child bone
map. It writes vertices directly to the entity `VertexConsumer`; it does not
rebuild `ModelPart.Cuboid` or `ModelPart.Quad` objects.

The renderer reproduces the source Chameleon transform procedure for each
bone:

1. start from the parent matrix;
2. apply animation translation relative to that bone's bind pivot;
3. move to the bone's absolute source pivot;
4. apply static and animation rotations in source `Z`, `Y`, `X` order;
5. move back from the same pivot;
6. render each cube after its own pivot/rotation transform; and
7. recursively process child bones with the resulting matrix.

The Fabric vertex coordinate conversion is explicit: Chameleon mirrors source
X; Fabric entity model coordinates have Y down. Therefore the direct renderer
converts source points with `(-x, -y, z)`. This has positive determinant and
does not require the old importer's fragile face-winding repair.

### 3. Preserve the six source faces directly

For each source cube, generate the six vertices in the source renderer's face
order (`north`, `east`, `south`, `west`, `up`, `down`). Each emitted vertex
carries the UV from the corresponding source face. `uv_size` signs are
preserved; no absolute value, recoloring, synthetic atlas, or one-rectangle
vanilla cuboid UV is permitted.

The runtime normal is transformed by the same matrix entry as the face
position. Lighting and overlay values come from the renderer invocation so
the model participates in normal Minecraft lighting.

### 4. Move animations onto source bones

The supplied source clips are sampled into an immutable-or-resettable
`GuardianPose` keyed by original bone name. Animation rotations and position
deltas are applied within the direct renderer's source transform stack, not by
mutating a surrogate Enderman body/arm/head part. Unsupported legacy cosmetic
motions that reference non-source marker bones are not applied to the direct
mesh.

This keeps idle, running, hurt, swipe, chest-strike, ground-slam, and dying
clips scoped to the bones the artist actually supplied. The rest pose remains
correct when no clip is active.

### 5. Restrict the old importer

`UserEndBossModelData` is reduced to source validation and minimal Enderman
adapter-carrier construction. It must no longer import the guardian's cubes,
pivots, rotations, or UVs into `ModelPart`.

`RiftGuardianModel.render(...)` delegates only the bound guardian mesh to the
new direct geometry renderer. Vanilla model rendering is not allowed to run
for that mesh. Existing non-boss Endermen continue through the unmodified
vanilla model route.

## Tests and verification

The implementation will use tests first and will retain these regression
boundaries:

- a parse/contract test proves source asset cardinality and every cube face is
  available;
- an independently calculated matrix test proves nested lower-arm and horn
  pivots use Chameleon absolute-pivot stack semantics;
- a face-emission test proves a hand-picked north/down face retains its signed
  UV endpoints and expected vertex order;
- a renderer-scope test proves the UUID-bound guardian selects the direct mesh
  without changing other event-mob renderer selections;
- focused tests, the full `CopiMineClient` Gradle test suite, client JAR build,
  resource visual-contract tests, and installed-artifact SHA checks must pass;
- a final live check requires fresh front and side Minecraft screenshots after
  the new JAR is installed. Static tests and build logs alone are not visual
  acceptance.

## Rejected alternatives

1. **Another `ModelPart` pivot/rotation patch:** rejected because several
   variants already failed and the source and destination hierarchy semantics
   differ fundamentally.
2. **Recolouring, replacing, or redrawing the atlas:** rejected because the
   original JSON plus PNG are the source of truth and their bytes already
   match the supplied archive.
3. **Adding GeckoLib or the old Chameleon dependency:** rejected because this
   Fabric 1.21.1 client needs a controlled, self-contained renderer; importing
   a legacy Forge-era rendering stack adds broad runtime risk without solving
   the local entity renderer boundary.
