# Echo decoy and replica: the Guardian body on a Pillager carrier

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`) rendering of Contract
15.2/15.3 bodies - the Rift Guardian, the Echo decoy and the Echo replica - when
the server carrier is a Pillager rather than an Enderman.
Spec: `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` (Contracts 15.2 and 15.3) and
`docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md`.
Supersedes the client pin recorded in
`docs/end-rift/2026-11-19/ECHO_CLIENT_TICK_AND_EYE_FIXES_AND_REPIN.md`; that
record stays as written, this file carries the current artifact.

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a test drives the real decision code.
* **LIVE-PAPER-VERIFIED** - exercised on a live Paper server.
* **VISUAL-VERIFIED** - confirmed on a real client.
* **NOT VERIFIED** - neither of the above.

## 1. Defect

The server spawns an Echo decoy as a real `PILLAGER` on purpose (Contract 15.3:
it must never share the boss entity type, HP, boss bar or defeat effects) and
binds `END_RIFT_ECHO_DECOY_V1`. The shared `LivingEntityRendererMixin` reached
the Guardian model from an `IllagerRenderState` branch, but the model itself was
declared as:

```java
public final class RiftGuardianModel extends EndermanModel<EndermanRenderState>
```

`Model.setupAnim` is dispatched through the erased `setupAnim(Object)` bridge,
and the `EndermanModel` bridge casts its argument to `EndermanRenderState`. A
Pillager extracts an `IllagerRenderState`, so once per decoy per frame:

* `LivingEntityRenderer.submit` called `setupAnim(IllagerRenderState)` on the
  Guardian model and the bridge threw `ClassCastException`;
* the illager layers then cast `getParentModel()` to `IllagerModel`, which the
  Guardian model is not, and threw again;
* the authored body submission was additionally gated on
  `state instanceof EndermanRenderState`, so a Pillager could never have emitted
  the Chameleon geometry even without the cast;
* the carrier's own texture was used for any geometry that did reach the
  collector, which would have drawn the Guardian mesh with Pillager UVs.

## 2. Repair

* `RiftGuardianModel` now extends `EntityModel<LivingEntityRenderState>` and
  overrides `setupAnim(LivingEntityRenderState)`. The bridge admits an
  `IllagerRenderState`, a player view state and an `EndermanRenderState`; the
  pose still comes from the authored animation player, not from the carrier.
* `copimine$submitImportedGuardian` is keyed on `model instanceof RiftGuardianModel`
  plus `state instanceof LivingEntityRenderState`, never on the carrier class.
  The Enderman boss keeps its renderer-supplied texture; every other carrier is
  drawn with `RiftGuardianModelRenderer.bossTexture()` through
  `RenderTypes.entityCutout`, so one authored skin is used everywhere.
* `copimine$suppressGuardianCarrierLayers` returns `false` from
  `shouldRenderLayers` for a non-Enderman state that wears a Guardian body
  (`END_RIFT_GUARDIAN_V1`, `END_RIFT_ECHO_DECOY_V1`,
  `END_RIFT_ECHO_REPLICA_V1`). The illager layers cannot cast a Guardian model,
  and the whole visible decoy is the authored body.
* Because those layers no longer run, `LivingEntityRenderer` no longer advances
  the model pose, so the submission path calls `guardian.setupAnim(state)` right
  before emitting the geometry. The deferred collector then reads a current
  pose, exactly as the Enderman route did through the layer setup call.

The Enderman boss route is deliberately unchanged: same model selection, same
texture from `EndermanEntityRendererMixin`, same scale injection, same layers.

## 3. Evidence

| Claim | Status |
|---|---|
| The Guardian body accepts an `IllagerRenderState` instead of casting to `EndermanRenderState` | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py::test_the_guardian_body_accepts_a_pillager_carrier` pins the declared base state and rejects a narrowed `setupAnim`) |
| The authored mesh is submitted for every living carrier, not only an Enderman | AUTOMATED-TESTED (`test_the_guardian_mesh_is_submitted_for_every_living_carrier` pins the model key, the living-state gate, the skin supply and the pose advance) |
| Illager layers cannot cast a Guardian model | AUTOMATED-TESTED (`test_carrier_layers_are_suppressed_for_a_pillager_guardian_body`) |
| The client compiles against the pinned JDK 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL` |
| A decoy visibly reads as the Guardian on screen (pose, skin, scale) | NOT VERIFIED - needs a real client |
| The decoy's animation cadence matches the boss at 60 and 144 FPS | NOT VERIFIED - needs a real client and an FPS measurement |
| Decoy damage, boss bar exclusion and cleanup on a live server | LIVE-PAPER-VERIFIED only for the server side; the client body is NOT VERIFIED |

## 4. Re-pin

Only client sources moved; no `copimine-end-event` source changed.

* previous: `30250423` bytes,
  `96947fa52c0faaa6b41a894e2fc04c3ccf6442c0e69be624589f07e4d501004638c5af337c8617f4309f648046f5aaecc5d10277b26a535caae56db51344c3ad`;
* current: `30250709` bytes,
  `31eafee05d1330c726546c93c57ed647ede4ac2033c2e44818d12ef56fc566f9955cdcf9c6b3987cc1de6521df4133a555a027c95028efbbdfb87eb194e5baf6`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.
