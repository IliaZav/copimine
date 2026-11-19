# Guardian copies: per-body clip and blend state

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`) Guardian model used by
the Enderman boss and by the Pillager carriers (Echo decoys and Echo replicas).
Spec: `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` sections 15.2,
15.3 and 15.5; companion `COPIMINE_END_RIFT_WAVES_CANONICAL.md`.
Supersedes the client pin in
`docs/end-rift/2026-11-19/GUARDIAN_COPY_POSE_AND_CLUE_FIX_AND_REPIN.md`; that
record stays as written, this file carries the current artifact.

Status vocabulary: IMPLEMENTED / AUTOMATED-TESTED / LIVE-PAPER-VERIFIED /
VISUAL-VERIFIED / NOT VERIFIED.

## 1. Defect - one decoy's clip rewrote another decoy's animation

`RiftGuardianModel` kept the active clip, the blend source pose and the blend
duration as plain fields on the model. One `RiftGuardianModel` is owned by one
`LivingEntityRenderer` instance, and **every echo decoy is a `PILLAGER` on the
single `PillagerRenderer`**. All decoys therefore share one model and one set of
those fields.

`modelForPhase` is called per submission, so the last submitted decoy decided

* which clip the shared model was playing,
* which pose the next `setAnimation` blended *from* (`blendFromPose`).

Concretely, with decoy A flinching (`HURT`) and decoy B idle:

1. A is submitted; the model starts blending from A's own last pose into
   `HURT`.
2. B is submitted; `setAnimation` blends **from A's pose** into B's clip, and
   B's elapsed ticks set the blend weight.
3. A is submitted again; its remembered source pose is now B's pose.

So a flinching copy could be drawn mostly in another copy's idle pose, and two
copies with different clips contaminated each other every frame. Contract 15.3
explicitly forbids a decoy "immediately reveal[ing] itself through obviously
broken animations"; this was exactly that, on the client only.

## 2. Repair

`RiftGuardianModel` now keeps a `ClipState` (clip id, blend source pose, blend
duration, last pose) **per body key** in a bounded `LinkedHashMap`
(`MAX_CLIP_KEYS = 64`, cleared at the cap so a long session cannot grow it).

* `modelForPhase(clipKey, phaseId, transitionMillis, animationId, elapsed)`
  selects the body before switching its clip.
* `setAnimation(clipKey, animationId)` blends from *that body's* last pose.
* `setupAnim(state)` samples the clip of the body currently selected and stores
  the result as that body's last pose.
* The old single-body overloads keep the `@default` key, so tests and tooling
  that own no entity behave exactly as before.
* The living renderer mixin passes the owning entity's UUID as the key on all
  three Guardian routes (decoy, boss by flag, boss by visual id).

The deferred-lambda pose capture from the previous repair is unchanged and
still required: the collector stores the geometry lambda and runs it at draw
time.

## 3. Evidence

| Claim | Status |
|---|---|
| A new body does not inherit another body's blend source pose | AUTOMATED-TESTED (`RiftGuardianModelClipIsolationTest::aBodyDoesNotInheritAnotherBodysBlend`) |
| The single-body/default-key API still samples the requested clip | AUTOMATED-TESTED (`theDefaultKeyKeepsSingleBodyCallersWorking`) |
| Every Guardian route passes the body key | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py::test_each_guardian_route_keys_the_clip_to_its_own_body`) |
| The client compiles and its unit tests pass on pinned JDK 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL`, new suite `tests=2 failures=0` |
| Two decoys visibly animate independently on screen | NOT VERIFIED - needs a real client |
| The boss and the decoys never share a frame-time blend in the live game | NOT VERIFIED - needs a real client |

## 4. Re-pin

Only client sources and one client unit test moved; no `copimine-end-event`
source changed.

* previous: `30250859` bytes,
  `0698329c0d7349217b6364a27ccbfdd5870cfa8746d4ac9ad0e87fe1dce978c1c2de0b9fc5a0ae76270d75b7783309e51556bfb0ac5847288abd3a86961dfed7`;
* current: `30252055` bytes,
  `63db43fcd4a8503658f3a14f03f9c45d76c2aed7b62cc8b301b820ac620bbc4b8605e02c1ffefb46f1be229aa319015814e165edce75e9686279b63da2247ed9`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.
