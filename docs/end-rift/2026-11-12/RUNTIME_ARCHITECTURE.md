# End Rift runtime architecture — implementation handoff

Authority: the November 11 master contract and the user's November 12 decisions.
The boss is completed first, followed by wave 6, wave 5, wave 7, and the remaining
event spell presentation. The user permits faster boss/Kagune playback; a playback
change must move the server's contact/release markers by the same factor.

## Ownership boundaries

`CopiMineEndEvent` remains the Paper composition root and API adapter. Encounter
state, movement/collision timelines and spell lifetime belong in runtime owners,
not additional tick-local flags or delayed lambdas spread through the plugin.

| Owner | Responsibility | Must not own |
| --- | --- | --- |
| `EndRiftEncounterCoordinator` / `runtime/encounter` | Wave admission, objective result, transition ownership | Rendering, speculative kills, duplicate spawn loops |
| `RiftRamEncounter` | Real Ram/marksman identities, pillar/armour state, charges, durable receipts | Obsolete ritual sphere |
| Existing boss brain/domain policies | Perception, intent, eligible ability and target choice | Changing HP, writing terrain, replacing the navigation focus for a single ranged cast |
| Existing cocoon/fall owner | Real defender deaths, one collapse/descent/landing, permanent melee fury | Respawning destroyed defenders or restoring protection after collapse |
| `LastSealRingRuntime` | One complete moving three-row cast and accepted launch leases | Guardian HP/shields, per-segment AI mobs, rewards |
| `KaguneRingFrame` | Bounded shared spatial frame, instance positions and two-block gap geometry | Progression, damage or authorization from client data |
| Fabric renderers | Authored mesh/UV/pose sampling, batching and interpolation | Server collision, objective completion or entity health |

New runtime owners receive narrow `Host` ports. A port performs Paper IO and
reports accepted outcomes. The owner decides the next state. Failed IO cannot
advance the gameplay state merely because a timer expired.

## Implemented ring replacement

The old ring used monolith particle loops and damage against a moving radius.
It had no authored emergence/retraction actors, followed the boss's current
position, and directly damaged/launched players without the accepted-hit adapter.

`LastSealRingRuntime` now captures an immutable event/generation/boss scope and
a fixed floor origin at commitment. It plans frozen non-aligned gaps against
player directions and traversable arena samples. The origin never follows a
later boss movement. A refused geometry or carrier spawn refuses the cast.

Each row runs:

`EMERGE -> TRAVEL -> RETRACT -> STAGGER -> next row`

Initial implementation tuning is 20/40/18/8 server ticks, respectively. These
values are engineering tuning, not newly invented canonical requirements.
Rows emerge at the existing 7.5/11/14.5 radii and travel the existing five blocks.
Only TRAVEL accepts hits, after full visible emergence. An accepted contact uses
the normal boss damage adapter, then launches upward and grants one bounded
generation/world-scoped fall receipt. A canceled hit has no launch or hit claim.
All rows share the one-hit-per-player ledger.

Collision sweeps player-relative segment motion between the previous/current
row radius. The same frame supplies the client instance positions and gap
boundaries. Segment count is frozen by row, so rings expand without adding and
renumbering meshes every tick. Gross player teleports do not become long invisible
collision sweeps. Vertical eligibility is bounded to the rendered near-floor row.

The server creates **one non-AI ItemDisplay for the entire cast**. Its custom-model
component carries the model selector, a distinct ring marker and the bounded
frame. There is no living entity or pathfinder per visible tentacle. The client
samples the existing authored Kagune rig once and submits all instances through
the existing single geometry pass and original skin. Emergence, throw/upstroke
and retraction use the existing clips with blended boundaries. Radius motion is
interpolated between authoritative samples.

The component format is local presentation data. It cannot authorize damage or
client-controlled movement. Invalid components render no ring. The shared class
is packaged in both the event JAR and the 26.3 client, avoiding a mandatory
Narcotics classloader dependency for the frame codec.

The renderer admission budget is 48 carriers, covering eight defenders, thirty
small enemies, up to six transient actors and one batched ring carrier. The ring
itself has a bounded maximum of 192 rig instances. This is a capacity bound, not
a measured FPS claim; native profiling remains required.

The serialized `SMALL_KAGUNE` spell ID dispatches the major ring for compatibility.
It no longer injects extra small-enemy batches between independent 15–20 second
cadence windows or relies on a tick-modulo lottery to make a ring reachable.

## Combat arbitration and cancellation

While the ring owns a major cast, ordinary selection cannot reserve another.
An explicit forced spell first cancels the ring. Phase changes, cocoon collapse,
boss cleanup, generation changes, clock rewind and missing carrier cancel it.
The general boss tick advances the ring once; the hover helper is no longer its
only ticker. Cancellation removes the carrier and releases the matching scope's
reservation. Previously accepted launch protection survives visual retirement
until its one landing or bounded timeout.

Cold restart cancels transient ring casts rather than replaying old contacts.
Durable defender deaths/cocoon collapse remain owned by the existing generation
ledger and fall checkpoint. Ring visuals must never create a new shield or
defender slot during recovery.

## Next implementation boundaries

1. `BossSpellTargetSelector` now owns generation-scoped cast fairness/history.
   The production spell selector no longer calls `Mob.setTarget`, increments the
   pursuit cursor or refreshes its lock. Navigation remains owned by
   `rotateBossTarget`; each committed cast retains its own target. Remaining AI
   work is movement/cast recovery, faster authored contact timing and native group
   acceptance across all grounded phases.
2. `SmallKaguneCadence` now owns remaining active nanoseconds, step and permanent
   stop receipt, scoped by event/generation and saved through the actual boss
   snapshot. Pauses/offline time do not consume it. A full arena advances the
   cadence without a burst of catch-up waves. The Paper adapter counts only
   valid living owned hitboxes/displays and confirms each spawn. Obsolete lifetime
   maps/expiry branches are removed. Small enemies have a separate 30-actor
   controller budget; temporary ambushes retain their own six-actor budget.
   Their directed melee owns 12-tick windup, 10-tick strike (contact at tick 4)
   and 18-tick recovery. Direction freezes at commitment, range/cone/LOS are
   checked at contact, and the cast cannot become a grab or a fake hurt pose.
   The active client retimes/blends sections of the original continuous rig;
   it uses the carrier's locked yaw rather than following the fleeing target.
   Living small enemies accept damage throughout their attack cycle; only
   terminal poses reject it. Remaining small-enemy work is the authored
   directed melee/contact controller and native pressure/visual acceptance.
3. Keep wave 6 progress/death receipts in `RiftRamEncounter`; finish final-pillar
   geometry/update/restoration and route its six armour textures from receipts.
4. Give wave 5 one authoritative safe-volume description used by placement,
   capacity, mob exclusion, HUD/guides, client volume rendering and cleanup.
5. Keep wave 7 room admission, owned actors, trial AI and renderer IDs separate;
   completion comes from legal actor results, not child effect expiry.
6. After boss gameplay, rebuild spell presentation by ability family: original
   textured meshes, consistent warning/release/contact geometry, bounded trails,
   impacts and audio. Repeated generic particle circles are not a substitute.

## Evidence boundary

This document describes real code and the remaining implementation work. Modern
server and client builds have been executed for the ring slice; final validation
and native arena footage are still pending. No production or installed working
Prism client has been replaced. The shared dirty integration checkout was
preserved; this slice is developed in an isolated checkout based on `65a73688`.

## Executed build evidence for this slice

- Server: 2,420,822 bytes, SHA-256 `0ca4f6373025a84adefc1c37711ea31265d4a82a3637226fa9e170a0950ae339`.
- Client: 15,018,286 bytes, SHA-256 `0bcc354bc524c9d11f429872328362285583aa8e26f729a5062a3cef513f35ec`.
- Modern server compilation/JAR: BUILD SUCCESSFUL; `C:/Temp/cm-phase6-ring-server-verified.log`.
- Modern client build: BUILD SUCCESSFUL; `C:/Temp/cm-phase6-ring-client-final.log`.
- Existing client JUnit: 370 tests, zero failures/errors/skips. No new source-string
  tests were substituted for the implementation.
- Release locks/staging are intentionally not promoted until this source slice
  and the following gameplay fixes have been integrated and rebuilt together.
- New ring behavior, collision, visual quality and performance in actual
  Minecraft: NOT VERIFIED. These builds are not a production deployment.
