# Integration audit and boss recovery ownership

Starting upstream: `124fdd7ec5d7738a7c4281e4e60dbc538d34ef92`.
Thirteen upstream commits after `50eaa3fd` were inspected. They add elite spell
atlases, meshes, flight signatures and impact particles, and update obsolete
test harnesses. The final Inferno commit changes a stale test; its title is not
evidence of another runtime implementation or deployment.

## Confirmed integration defects repaired

* The new spell meshes referenced their own atlases, while client
  `processResources` still copied only the earlier two shared textures. All
  three referenced atlases are now embedded in the required client. The built
  JAR was inspected for the complete model-to-texture dependency closure.
* `DUSK` is not a constant in the pinned Paper Particle enum. That pass silently
  vanished. It now uses the verified `ELECTRIC_SPARK` particle.
* The spear was authored along +Z but its spin was applied on X. Flight now rolls
  about Z. The 26.3 item renderer already centres a 0..16 cuboid model by -0.5;
  the added server offset displaced the model from collision. It is removed.
  The native ItemDisplay renderer's Y=PI rotation is compensated, including the
  first frame, so the spear faces its released flight direction.

Bytecode inspection used the exact locked Minecraft 26.3 client:
`ItemTransform.apply`, `DisplayRenderer.ItemDisplayRenderer.submitInner`.
Local evidence: `C:/Temp/cm-native-itemtransform.log` and
`C:/Temp/cm-itemdisplay-transform-bytecode.log`. This proves the transform
convention, not the final rendered appearance.

## Boss shutdown checkpoint

Previously `onDisable` called `cancelSessionTasks`, which cleared the tentacle
controller, guardian deaths and cocoon fall state, before its final save. Boss
combat was also absent from restart-preserved actor ownership. Correct policy
code therefore did not guarantee correct production shutdown.

The official living boss now retains its body UUID. After hazard restoration,
an immutable snapshot is frozen before combat teardown. All subsequent teardown
saves use that checkpoint. This also preserves recovery evidence when the boss
is already in RECOVERY_REQUIRED. If both checkpoint writes fail, the physical actor retains a recovery marker;
bootstrap refuses automatic combat resumption from that ambiguous state. A
confirmed write of the same frozen snapshot at either boundary clears the marker.
CodeRabbit identified the first-success/second-failure case; it is now preserved
as a committed checkpoint rather than falsely quarantined.

## Living small Kagune recovery

`SmallKaguneRosterSnapshot` owns a versioned event/generation roster, with exact
actor/world identity, position, facing, HP and maximum HP. It validates finite
coordinates, positive bounded health, unique identities and the thirty-actor
limit. Invalid records are retained as recovery evidence rather than replaced
with an empty roster. Legacy snapshots without this extension cannot recreate
enemy history that was never recorded.

`SmallKaguneRestoration` owns pending entries, bounded retry, rollback UUIDs and
checkpoint admission. The Paper adapter creates the real model/hitbox pair,
confirms position and HP, and commits the complete roster before boss AI actions resume.
A partial batch rolls back; rollback failures retain ownership. New summons and
boss actions pause while recovery remains pending. Restoration does not heal
enemies, revive defensive guardian slots, refresh the spawn timer, or delete
surviving small enemies when the cocoon breaks. Accepted small-enemy HP changes
queue the normal ordered snapshot writer. A process crash before that queued
write can still recover the last committed checkpoint, not an uncommitted hit.

The shutdown checkpoint captures this roster before nonpersistent displays and
hitboxes are removed, enabling a real server restart to recreate living enemies
with their saved HP and a fresh, visible emergence.

## Evidence and remaining acceptance

Modern server/client builds passed for the elite integration slice; 26 focused
checks passed. The new small-roster owner was compiled and executed against
round-trip HP/position, 0–30 actors, repeated restart, duplicate identity, wrong
scope, partial spawn rollback, bounded retry, failed checkpoint and retained
invalid-record scenarios (`C:/Temp/SmallKaguneRecoveryProbe.java`). Modern server
compilation passed after production integration.

No full current-SHA gate, production install, authenticated client run, native
art acceptance or complete event acceptance is claimed by this document.
`nativeVerified=false`. Earlier candidate manifests describe earlier commits;
newly built artifacts must be repinned before publication or installation.

## Pursuit ownership through real target events

The pursuit lock previously read `Mob.getTarget()` as its authority. A vanilla
retaliation target event could therefore replace the focused player between
coordinator decisions and bypass the lock. The owned boss now keeps a separate
pursuit UUID. Its target event adapter redirects native proposals to that focus;
only the coordinator's guarded write may commit a new pursuit target. Null
targets used by casts remain legal, and normal combat restores the original
focus afterwards without extending its lock. Invalid/disconnected focus is
released promptly. Rejected target writes respect other plugins' cancellation
and use bounded repair/retry instead of forcing a target every tick.

The real target adapter was executed with a late visitor, a second admitted
attacker, native retaliation, cast target clearing, focus restoration and an
already-cancelled target event. The existing roster probe passed. Native
multi-player movement/melee acceptance is still required.
