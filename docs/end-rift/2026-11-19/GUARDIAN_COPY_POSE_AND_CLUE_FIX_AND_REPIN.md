# Guardian copies: per-submission pose and a learnable decoy clue

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`) rendering of the Echo
decoy / Echo replica bodies, and the contract 15.3 identification clue.
Spec: `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` sections 15.3 and
15.5; companion `COPIMINE_END_RIFT_WAVES_CANONICAL.md`.
Supersedes the client pin in
`docs/end-rift/2026-11-19/GUARDIAN_CARRIER_RENDER_FIX_AND_REPIN.md`; that record
stays as written, this file carries the current artifact.

Status vocabulary: IMPLEMENTED / AUTOMATED-TESTED / LIVE-PAPER-VERIFIED /
VISUAL-VERIFIED / NOT VERIFIED.

## 1. Defect A - every decoy was drawn with the last body's pose

`SubmitNodeCollection.submitCustomGeometry` copies only the `PoseStack` pose and
stores the `CustomGeometryRenderer` lambda in a `CustomFeatureRenderer$Submit`
node; the lambda runs at draw time, not at submit time.

`ChameleonGuardianRenderer.submit` read `guardian.currentPose()` *inside* that
deferred lambda. One `RiftGuardianModel` instance is owned by one
`LivingEntityRenderer` mixin instance, and every Echo decoy is a `PILLAGER`, so
all decoys share the single `PillagerRenderer` model. The last body submitted in
a frame set the shared pose, and every queued decoy drew with it - the
"obviously broken animations" contract 15.3 forbids as an accidental giveaway.

Repair: capture the immutable `GuardianPose` (a `record` with a copied map) once
per submission, before handing the lambda to the collector. Each deferred draw
now closes over its own pose.

## 2. Defect B - the "learnable clue" had no implementation

Contract 15.3: "A careful player should still be able to identify the real boss
through subtle, learnable clues", using e.g. "a consistent difference in Rift
core energy". The real boss, the decoy and the replica share one mesh and one
skin (`end_rift_user_boss.png`), so a decoy was pixel-identical to the boss and
the clause could not be satisfied at all, even though a source comment claimed a
"spectral skin" carried it.

Repair: the shared `getModelTint` hook now returns a fixed, defined cool-violet
tint (`COPIER_TINT = 0xFFD6C4F0`) for `END_RIFT_ECHO_DECOY_V1` and
`END_RIFT_ECHO_REPLICA_V1`. The real boss (`END_RIFT_GUARDIAN_V1`) keeps the
identity tint. The tint flows into `ChameleonGuardianRenderer.submit`'s `color`,
so the authored skin stays readable while the copy reads colder. It is a colour,
never a "FAKE" label, and it is a constant, so the server cannot flip it per
viewer.

## 3. Evidence

| Claim | Status |
|---|---|
| The pose is captured per submission, before the deferred lambda | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py::test_the_submitted_pose_is_captured_before_the_deferred_lambda`) |
| A decoy/replica carries a fixed learnable tint and the boss does not | AUTOMATED-TESTED (`test_a_copy_is_visibly_learnable_without_a_fake_label`) |
| The client compiles against pinned JDK 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL` |
| Two or more decoys animate independently on screen | NOT VERIFIED - needs a real client |
| The tint is subtle enough and readable on the authored skin | NOT VERIFIED - needs a real client |
| Live decoy summon, damage and phase-exit cleanup on Paper | LIVE-PAPER-VERIFIED only for the server side; the client body and tint are NOT VERIFIED |

## 4. Re-pin

Only client sources moved; no `copimine-end-event` source changed.

* previous: `30250712` bytes,
  `7e5b40eb8d41142d9f23315566de4c19761b01663a51e2f05be9320608483c8b0a6c937934983fbbe85a2ef363669feda22622103fc7eee9dc217de9a62a688e`;
* current: `30250859` bytes,
  `0698329c0d7349217b6364a27ccbfdd5870cfa8746d4ac9ad0e87fe1dce978c1c2de0b9fc5a0ae76270d75b7783309e51556bfb0ac5847288abd3a86961dfed7`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

Local evidence: End Rift gate exit 0 (`127 passed, 2 skipped`); targeted client
tests `197 passed`.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.
