# Wave 6, Kagune, fog and Last Seal: seven defect repairs

Date: 2026-11-18
Scope: Wave 6 Rift Ram (stomp, rain volley, pillar collider, skeleton fall),
Kagune animation broadcast, Wave 5 fog-pack receipts, Last Seal contact order.
Spec: `COPIMINE_END_RIFT_WAVES_CANONICAL.md` (Wave 6 section 8 is superseded by
`docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` sections 7.1-7.5) and
`COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md`.

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a gate test drives the real code path.
* **LIVE-PAPER-VERIFIED** - exercised on a live Paper server.
* **VISUAL-VERIFIED** - confirmed on a real client.
* **NOT VERIFIED** - neither of the above.

## 1. What the governing text requires

The master rebuild replaces the abandoned Ritual Sphere Wave 6 with one Rift Ram
and four ordinary pillars plus a final pillar (seven accepted impacts). It asks
for a real travelling stomp with a clear damage and Slowness outcome, an elite
marksman on the final pillar with two distinguishable volleys that keeps
fighting after the pillar collapses, and pillars the party can actually use as
cover. Wave 5 requires one finite reinforcement pack after every fog, and the
Last Seal construct is a finite spell with one contact per cast.

## 2. The seven defects and their repairs

### 2.1 The stomp applied Slowness but never damage

`RiftRamEncounter.stomp` added the slow and nothing else, so a party that never
jumped took no health loss from the wave at all: a visible hit had no
consequence. `RiftRamPolicy.STOMP_DAMAGE` (6.0) is now applied through
`host.damage` on the **same accepted hit** that applies Slowness, inside the same
per-cast hit set. The damage radius is therefore exactly the shown wave front
and one cast can never damage a player twice.

### 2.2 "Bone rain" was a second horizontal fan

The heavy volley reused the arrow spread helper, so it was the triple shot with
a different angle and speed. It now spawns `BONE_RAIN_ARROWS` (3) arrows
`BONE_RAIN_HEIGHT` (11) blocks above the target, pushed straight down at
`BONE_RAIN_SPEED` (0.55) with gravity **on**; the landing offsets bracket the
victim inside `BONE_RAIN_SPREAD` (1.7). The triple shot stays the authored
straight fan and always asks the host for gravity **off**, so the fragile
`Double.compare(speed, BONE_RAIN_SPEED)` heuristic is gone.

### 2.3 The skeleton "fall" was a teleport that declared itself landed

The shipped drop wrote the actor to the floor and recorded the landing in the
same call, so nothing could render a fall and a checkpoint could claim a landing
before physics ran. `dropPillarSkeleton` now releases the actor with gravity
enabled and `tickSkeletonFall` commits `landPillarSkeleton` only after the
actor's own `isOnGround()` reports ground contact near the floor
(`hasLanded`). `FALL_TIMEOUT_TICKS` (60) provides a bounded, vetted fallback; a
refused fallback fails the wave into recovery instead of leaving an orphan on a
pillar that no longer exists.

### 2.4 Pillars were display-only geometry

The party could walk through a solid obsidian tower and only the Ram's distance
test "collided". The pillar now has a real barrier volume owned by the host
(`placeRiftRamPillarCollider` / `clearRiftRamPillarCollider`) and journaled as
`RIFT_PILLAR` at the true block height. It is applied only after the journal
commits, rolled back on a failed commit, restored on collapse and cleanup, and
recovered at boot when a crash left it standing. Boot recovery and clearing only
restore a cell that still holds the barrier this plugin wrote, so a foreign edit
is never overwritten. Because the pillar is now solid, `charge` evaluates
`segmentHitsPillar` **before** `safeVolume(to)`: otherwise the Ram would be
refused as "moving into a wall" before its nose reached the objective.

A first version of the collider kept one fixed height while `crackPillar()`
shrank the visible final pillar, and it also reached the block the marksman
stands in. The pillar geometry now lives once in `RiftRamPolicy`
(`pillarSilhouetteHeight`, `pillarPlatformHeight`, `pillarFootprintShrink`) and
feeds the model, the elite's platform and the barrier together:
`pillarColliderHeight` is `7`, `6`, `5` across the two crack stages of the final
pillar (four for an ordinary one), which always stops below the marksman's feet.
`crackPillar()` re-places the collider after it re-seats the model, so the party
never keeps a full-height invisible wall where the model already lost a section;
a refused re-placement throws `damaged-pillar-collider-refused` and fails the
wave into recovery instead of leaving the two out of step.

### 2.5 Fog packs were credited by a global live-count delta

A wave-5 mob dying in the same tick as a reinforcement spawn shrank the delta,
so a fully delivered pack looked short and was rolled back while the debt stayed
unpaid. Packs (post-fog and pre-fog refill) are now credited per entity:
`countNewlyOwnedWaveEntities` counts only actors that this spawn call newly
registered, are still live, and belong to wave 5. A partial pack is still rolled
back and stays owed.

### 2.6 The Kagune animation broadcast was O(V squared)

Binding an entity ran per viewer and each of those broadcasts re-looped every
other viewer, so a full party paid a quadratic packet storm for one state
change. `sendTentacleAnimationVisualUpdate(Entity, ...)` now broadcasts exactly
once per state change, and a new per-viewer overload sends a single targeted
packet when a late viewer binds.

### 2.7 The Last Seal marked a player hit before the hit succeeded

`LastSealConstructRuntime` recorded the receipt (`hits.add`) before calling
`host.hit`. A refused hit (dead, out of the event, immunity window) therefore
consumed the one allowed contact and the spell silently did nothing for the rest
of its cast. The receipt is now written only after `host.hit` returns true.

## 3. Evidence

| Claim | Status |
|---|---|
| Stomp damages once inside the shown wave | AUTOMATED-TESTED (`tests/test_rift_ram_stomp_runtime.py`, `tests/test_end_rift_wave_six_seismic_stomp.py`) |
| Rain volley falls from above; triple shot stays gravity-off | AUTOMATED-TESTED (`tests/test_rift_ram_bone_rain_runtime.py`) |
| Landing commits only after real ground contact; bounded fallback | AUTOMATED-TESTED (`tests/test_rift_ram_skeleton_landing_runtime.py`, `tests/WaveSixPillarSkeletonPolicyTest.java`) |
| Pillar collider is solid, journaled, crash-safe and rollback-safe | AUTOMATED-TESTED (`tests/test_rift_ram_pillar_collider_runtime.py`, `tests/test_end_rift_wave_six_runtime_contract.py`) |
| Collider height follows both crack stages and stops below the marksman | AUTOMATED-TESTED (`tests/test_rift_ram_pillar_collider_runtime.py::final-pillar-collider-follows-cracks`) |
| Charge decides the pillar impact before destination validity | AUTOMATED-TESTED (`tests/test_end_rift_wave_six_runtime_contract.py`) |
| Fog pack is not shortened by an unrelated death | AUTOMATED-TESTED (`tests/test_wave5_black_fog_adapter.py`) |
| Kagune state change costs one packet per viewer | AUTOMATED-TESTED (`tests/test_small_kagune_presentation_packet.py`) |
| A refused Last Seal hit does not consume the contact | AUTOMATED-TESTED (`tests/test_last_seal_construct_contact_runtime.py`) |
| Plugin builds with the pinned JDK | AUTOMATED-TESTED: clean build, no errors |
| End Rift event gate | AUTOMATED-TESTED: exit 0, `127 passed, 2 skipped`, wave-hit harness `27 passed` |
| Stomp, volley, fall, collider and spell read correctly on screen | NOT VERIFIED - needs a real client |
| 2/5/10/20-player behaviour | NOT VERIFIED - no players online, `/cmend test wave 6` refuses an empty sandbox roster |
| Obelisk texture repaint | NOT DONE - still deferred, outside this repair batch |

## 4. Re-pin

The End Event sources changed, so the compiled candidate moved and was re-pinned
to `2582610` /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.

It moved twice more while the pillar geometry was corrected (`2582423` /
`6188d78c04df1c10`, then `2582609` / `e4ba96d991fb5084`). Every replaced digest was prepended
to `replaceSha256`, following the prepend convention in
`docs/end-rift/CANONICAL_SPEC_PINS.md` section 5, so an already-staged older
candidate is still accepted, newest first:
`e4ba96d991fb5084573e3a999c25edd49fdf20b0a9505a8c2d32482f722ea93b`,
then `6188d78c04df1c1064cbb7ca5fd824796a9275c791c2d04f582123e38b9da0ab`,
then `637a08f3372cfe103e78fcf5ed18a2910e54627ef74931f236751a98767142ac`.

`docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json` carries the same new
`candidateSha256` and `sha256`. Historical records under `docs/end-rift/<date>/`
keep the digests that were true on their date and are not rewritten.
