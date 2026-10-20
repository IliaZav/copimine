# End Rift Encounter and Mob Refresh

## Status

Approved by the user on 2026-09-20. This specification extends the earlier
direct-guardian-renderer design with grounded boss locomotion, readable
party-combat mechanics, shield feedback, a new tentacle presentation and
cycle, and consistent ordinary/elite mob visuals.

## Problem

The guardian geometry now uses the supplied source model, but its encounter
presentation still has several player-visible failures:

- ordinary pursuit can use Enderman feints or recovery teleports and the
  fallback preserves residual vertical velocity;
- the server does not publish `RUN` while it is moving, so the exact client
  mesh can appear to float with an idle lower body;
- accepted boss hits use a quiet, generic world sound and have no
  attacker-local confirmation or contact-specific visual;
- a Last Seal shield blocks damage but has no visible orbiting shield parts;
- temporary tentacles are registered as `UNDER_PLAYER` and immediately enter
  the recovery path instead of the grab/hold/throw path;
- release is calculated while the target is already at the grab socket, so it
  degenerates to a short fallback impulse;
- the current cyan, glass-like tentacle art conflicts with the supplied
  black-and-violet End Rift references; and
- supplied enderman/spider images must stay exact, while skeleton and elite
  variants need a coherent new style rather than vanilla recolours.

## Goals

1. Make ordinary guardian movement visibly grounded, walking, and consistent
   with the supplied `running.json` animation.
2. Make a group encounter tactical and readable without a single player being
   repeatedly hard-controlled or damage arriving without telegraphing.
3. Make body hits and shield hits distinct in both sound and visuals.
4. Make the Last Seal shield visible as orbiting, server-synchronised segments
   tied to the living guardian-tentacle state.
5. Replace the tentacle visual and lifecycle with a large, segmented,
   black-and-violet implementation matching the user reference, including a
   reliable, safe long throw.
6. Install exact archived enderman/spider texture bytes and give skeleton and
   elite variants a deliberate End Rift visual family.

## Non-goals

- Do not recolour, normalise, re-export, or replace the supplied guardian
  atlas or geometry.
- Do not give client rendering authority over damage, shield blocking, target
  selection, grab, throw, health, or phase progression.
- Do not launch or close the user’s Minecraft client.
- Do not silently treat a build or unit test as player-visible confirmation.

## Asset Rules

- The boss geometry SHA-256 is
  `301583a2efea6c5b597c4fe2cadced68d7838b80f630d1d16f8aaa8265964783`.
- The original boss atlas SHA-256 is
  `f298ed322335c5439c19dddb8014aa0960b83f3fb27d692580a75e051516c45d`.
- The archived enderman and spider images are copied byte-for-byte and their
  existing source-hash test remains authoritative.
- The new tentacle atlas is a newly authored, explicit 512 by 512 cuboid UV
  atlas. It uses opaque block-pixel segments, deep black-purple seams and
  restrained violet highlights. Cyan glass, gems, laser lines and smooth
  anti-aliasing are prohibited.
- Skeleton and elite texture/model art is newly authored. It must not claim
  to be an exact archived source asset.

## Selected Architecture

### 1. Grounded guardian controller

The carrier remains an Enderman so the existing direct guardian renderer and
network binding stay scoped to its UUID. Ordinary target pursuit has no
teleport branch. It uses the Paper pathfinder first and collision-checked,
strictly-horizontal fallback movement only when the pathfinder refuses a
request. A fallback clears residual vertical velocity when the guardian is on
ground; it never injects upward velocity.

`PHANTOM_FEINT`, distance recovery and stuck recovery become explicit
telegraphed abilities rather than ordinary navigation. The initial release
removes ordinary feints and automatic position jumps; any later scripted
relocation must have its own named cast, visual and cooldown.

The server emits `RUN` only while actual horizontal motion is above the
walking threshold, and emits `IDLE_BREATH` after the motion settles. The
client stays a pure consumer of that event state.

### 2. Group-combat director

Existing `BossBrain`, target rotation, hazard budgets and intent selection are
retained. A small policy extension turns the selected intent into a bounded
combat beat: position pressure, stack punishment, spread punishment, a
short recovery window, or a guarded Last Seal action. It cannot select an
ability that exceeds the hazard budget, immediately repeats its prior high
impact action, or hard-controls a recently hard-controlled player.

All major attacks retain an advance visual/sound cue. The boss should create
positions and timing for a group to solve, not constantly teleport or merely
absorb click damage.

### 3. Contact feedback and shield ring

Server damage remains authoritative. An accepted body hit creates a bounded
body-impact event with hurt animation, contact particles, an audible hostile
impact for nearby viewers and a clearer local cue for the attacking player.
Its recoil is ground-clamped.

When Last Seal guardians protect the boss, a new `ShieldOrbitPolicy` derives
one or more positions from the boss position, current server tick and living
guardian count. Server-owned display carriers hold the visible shield parts;
the Fabric client draws their dedicated model. A blocked attack chooses its
nearest active shield segment as the visual contact point, emits metallic
particles and a metallic sound, and never plays body-hurt feedback. When the
last guardian falls, the orbit carriers dissolve before the damage window
opens; restoring guardians recreates them.

### 4. Tentacle rig and lifecycle

The client rig becomes a heavy six-segment vertical body with a larger base,
tapered upper segments and a clear grab tip. Server logical height, guardian
interaction hitbox, fallback display and Fabric render scale use one shared
dimension contract.

Temporary tentacles register as `TEMPORARY`, begin at `TELEGRAPH_GRAB`, then
advance through `GRAB_SUCCESS`, `HOLD`, `THROW`, `RECOVERY` and `RETRACT`.
Permanent guardians use `SHIELD_CHANNEL` while guarding and enter the same
attack sequence on their staggered attack schedule. The obsolete
`SPAWN_UNDER_PLAYER` shortcut is reserved only for a future non-grabbing
hazard and is not used by either attack type.

`TentacleThrowPolicy` computes a finite, horizontal launch direction before
the player is moved to the grab socket. It returns a bounded target-away
velocity and vertical lift, with a deliberate safe horizontal range. The
adapter applies that result only when the player remains a valid event
participant and the landing direction remains inside the arena boundary.

### 5. Mob visual family

The exact archived enderman and spider images stay wired to their existing
visual IDs. Skeleton and elite models retain their correct entity-type
renderers but gain a shared End Rift material language: dark purple armour or
bone plates, limited violet rift lines, high-contrast readable joints, and
elite-only silhouette accents. Their model and texture choice is explicit by
visual ID; no variant may leak into ordinary vanilla mobs.

## State Relationships

```text
living Last Seal guardians
          │
          ├─> shield orbit carriers visible ─> intercepted hit: metal feedback
          │
          └─> guardian tentacle attack cadence

no living Last Seal guardians
          │
          └─> orbit dissolve ─> finite boss damage window ─> guardians restore

ordinary guardian pursuit
          │
          └─> grounded path / horizontal fallback ─> RUN or IDLE animation
```

## Acceptance Criteria

1. An ordinary guardian movement simulation contains no automatic teleport or
   vertical fallback impulse, and a moving state produces `RUN`.
2. A stopped grounded guardian produces `IDLE_BREATH`.
3. Repeated player hits yield bounded body feedback and do not create a
   vertical boss launch.
4. Shielded hits are blocked, resolve against an orbit segment and use a
   metallic cue with shield particles instead of body feedback.
5. An active temporary or permanent attacking tentacle reaches each grab and
   throw state in order; the throw has a non-zero bounded horizontal range.
6. Client tentacle dimensions and server hitbox/fallback dimensions agree.
7. New tentacle art is violet/black segmented pixel art and no cyan-glass
   motifs remain in its asset or generated preview contract.
8. The boss, archived enderman and archived spider assets satisfy their exact
   hashes; skeleton and elite selection remains entity-type scoped.
9. Focused tests, the complete End Rift test harness, both client/server
   builds and artifact hash inspection succeed.
10. After the user launches the updated client, live evidence shows walking,
    body impact, blocked shield impact, shield dissolve, tentacle grab/throw,
    and all requested mob variants from more than one camera angle.

## Rejected Alternatives

- Replacing the boss atlas with a recoloured or AI-generated boss atlas is
  rejected because the supplied image is source of truth.
- Keeping invisible shield invulnerability is rejected because it does not
  communicate the mechanic to players.
- Making the client decide whether a hit struck a shield is rejected because
  that would desynchronise multiplayer damage authority.
- Solving bad movement by making every recovery a teleport is rejected
  because it reinforces the reported flying/warping impression.
- Keeping the one-shot `SPAWN_UNDER_PLAYER` temporary tentacle route is
  rejected because it bypasses the intended attack lifecycle.
