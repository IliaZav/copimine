# Last Seal: articulated spells, collapse and fury handoff

Source change: `f0474fd4`, based on `4472aaa6`.
User scope: update the selected Prism profile, continue Guardian implementation,
especially final-phase logic, Kagune animation and spell presentation. Tests
remain assigned to DeepSeek; no Minecraft/Paper playtest was launched here.

## Implemented

- The durable, generation-scoped `GuardianDeathLedger.cocoonBroken()` now owns
  open-shield eligibility. The obsolete timed vulnerability window and a stale
  decorative actor count cannot re-close or re-pin a destroyed cocoon.
- Landing-to-fury admission explicitly enables awareness/AI and gravity,
  invalidates old navigation scheduling and releases old close/ranged deadlines.
  Existing survivors remain: this does not delete small Kagune or spawn new ones.
- Descent accelerates from suspension while retaining the exact 40-tick fall
  and 20-tick landing boundaries and existing persistent checkpoints.
- Authored fall/landing JSONs now use six key poses each: suspension release,
  opposing torso/head twists, asymmetric arm recoil, impact compression and
  damped recovery. End/start brace poses match. Original model bones, pivots,
  hierarchy and artist clips are preserved. The generator remains source-owned.
- Spell Kagune receive a new articulated anticipation/extension/withdrawal
  sequence in code. Extra coil deformation finishes by the first permitted stab
  contact (progress 0.25); damaging root kinematics remain shared with Paper.
- Two narrow energy filaments wrap each temporary spell stem in the same locked
  heading/pitch frame. At most 40 points every four ticks for five stems, using
  the existing event particle budget. No extra AI entities, new damage paths or
  target homing were introduced.
- Landing uses existing bounded 3D fault geometry, staged impact/camera
  presentation, a scoped Kagune eruption cue and sparse dust. Removed the
  unrelated lava shower and unscoped whole-world explosion sounds.

## Resource-pack release repair

The earlier installation accidentally published the **legacy source ZIP**
`c466df56...`, not the converted 26.3 candidate. This also exposed stale
`resourcePackMigration` pins in GitHub Actions run `43867192483`: candidate
preparation failed before Python tests with
`Legacy resource pack does not match the pinned migration source`.

Rebuilt with `resourcepacks/build-migration-pack.py`, preserving its checksum
enforcement. The new migrated artifact is 34373458 bytes, SHA-256
`98d7950558e7da38e37b641d340b7fe89dc0310cbce6ad0671be62432d627176`.
It declares exact format `[97,1]`, contains 34 modern item definitions and 85
registered models. Candidate packaging now takes
`build/minecraft-26.3/CopiMineResourcePack-26.3.zip`, never the legacy ZIP.
Both legacy-source and converted-candidate hashes/sizes are updated in the lock.

## Evidence boundary

Server/bridge and clean client JAR builds succeeded on pinned JDK 25 / Gradle
9.8.1. Resource-pack conversion completed with strict pinned hash verification.
Packaging inspects ZIP CRCs, protocol classes, model/mixin entries, spell clips
and audio entries. `git diff --check` was run.

The initial Prism update installed the production `10d068b9...` mod, backing up
the old 30061084-byte JAR. The new candidate client is 30246359 bytes, SHA-256
`934526b84c0d07988e57cc9734c4da2b697140ff7062890a34afef61f7039102`;
its final installation is recorded with the runtime receipt.

Do not interpret compiled source as a completed visual/gameplay acceptance.
DeepSeek still needs to exercise shield break, stale actor removal, exact
landing timing, restart during descent, permanent fury, projectile retirement,
Kagune contact/miss/cancel, original rig continuity and final victory. No new
claim is made here that all six phases or every recovery path are verified.
