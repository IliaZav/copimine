# Implementation Prompt: Rebuild the End Rift Boss From Scratch

You are working in the repository `IliaZav/copimine`, using the current End Rift boss implementation only as a source of reusable low-level utilities and existing assets.

## Primary objective

Rebuild the End Rift boss combat runtime from scratch so the fight is readable, visually coherent, progressively smarter from phase to phase, mechanically varied, and maintainable.

Do not keep extending the current high-level boss AI with more conditions. Replace the boss encounter controller with a new modular combat runtime.

The result must feel like a designed Minecraft boss encounter rather than a normal mob with random particles and spell timers.

The intended production fight is for 5-6 players, but every mechanic must remain testable and mechanically solvable with 1-2 players.

---

# Non-negotiable constraints

1. DO NOT modify, replace, or "fix" the working boss texture/UV/model conversion pipeline.
   - The texture/UV converter took a long time to stabilize.
   - Preserve it exactly unless a compile error proves an interface adaptation is unavoidable.
   - Do not rewrite the model importer or UV remapping code.
   - Boss combat, AI, animation state, VFX, and movement may be rewritten around it.

2. Remove `RIFT_OBELISKS` from the boss completely.
   - That mechanic was moved into a wave.
   - Do not reintroduce it as a boss spell.
   - Existing Wave 4 obelisk gameplay remains separate and must not be broken.

3. Remove the expanding wave-front effect that currently travels from the Core across the arena at the start of numbered waves.
   - The existing effect is driven by `startWaveFrontAnimation(...)`, `WaveVisualPolicy`, `MODEL_WAVE_FRONT_OVERLAY`, `waveFrontVisuals`, and related state/tasks.
   - Stop invoking this effect from official wave startup and wave test startup.
   - Remove or retire the old wave-front-only code if it becomes unused.
   - Keep a short local wave-arrival burst at the Core if useful, but it must remain compact and must not sweep across the entire arena.
   - Do not alter the actual wave objectives or wave mechanics when removing this visual.

4. Existing real boss animations must be wired into combat:
   - `swipe.json` -> `MELEE_SWIPE`
   - `udar_iz_grudi.json` -> `CHEST_STRIKE`
   - `udar_po_zemle.animation.json` -> `GROUND_SLAM`
   These clips already exist and must actually be triggered by the server combat runtime.

5. Future spell animations will later be supplied by an animator.
   - For now, every spell without a real JSON animation must receive a good procedural animation/pose generated in code.
   - Design the animation interface so a future JSON clip can replace the procedural fallback without changing gameplay logic or action IDs.

6. Do not use particle spam as the main visual language.
   - Particles are allowed and encouraged, but they must form readable shapes.
   - No random clouds that make it impossible to understand the attack.
   - Telegraph, release, impact, sound, camera shake, and damage timing must be synchronized.

7. Projectiles must not home after launch unless a mechanic explicitly says otherwise.
   - Most attacks should aim, snapshot a direction, and then fly straight so players can dodge.

---

# High-level architecture

Replace the current high-level boss decision flow with a new controller, for example:

- `BossCombatController`
- `BossPerception`
- `BossCombatMemory`
- `BossTacticSelector`
- `BossActionSelector`
- `BossActionExecutor`
- `BossPhaseProfile`
- `BossVfxController`
- `BossProceduralAnimator`

Exact class names may follow repository conventions, but the responsibilities must remain separated.

The main combat state machine should be simple:

`MOVE -> SELECT -> TELEGRAPH -> CAST/ATTACK -> RECOVERY -> MOVE`

With dedicated interrupting modes:

- `HALF_HEALTH_EVENT`
- `LAST_SEAL`
- death / cinematic cleanup

One system must own both movement and attacks. Do not let pathfinding continue issuing independent movement commands while the boss is performing a committed heavy attack.

Once an action reaches TELEGRAPH, the boss normally commits to it until RELEASE/RECOVERY unless the boss dies or the encounter is forcibly reset.

Normal boss movement should use pathfinding/walking. Teleporting is a deliberate tactical tool or emergency recovery, not the default movement system.

---

# AI goal

The boss must visibly become smarter from phase to phase.

Do not implement this as "the same AI with shorter cooldowns". Later phases must unlock more perception, more tactical responses, more action combinations, and better target decisions.

The boss should feel as if it learns how the party is playing.

The AI must stay deterministic enough to debug. Do not build a giant opaque utility-scoring system with hundreds of weights.

Use this decision priority:

1. scripted phase transitions / one-time events
2. hard safety checks and arena recovery
3. immediate tactical reactions with strict conditions
4. phase-allowed tactical attacks
5. phase-allowed spells
6. movement / repositioning

---

# Perception and combat memory

Track the state of every live participant.

For each player, maintain at least:

- current health percentage
- recent health trend
- distance from boss
- distance band: melee / medium / far
- whether the player is isolated from the group
- how long the player has stayed near the boss
- how much recent damage the player has dealt to the boss
- how long the player has been the primary target
- when that player was last targeted
- whether that player was recently hit by hard control
- whether that player was targeted by the previous Edge Volley salvo
- whether the player is moving actively or camping in roughly the same area

Group-level perception should include:

- party center
- spread radius
- number of players close to boss
- whether boss is surrounded
- whether players are stacked
- whether players are widely separated
- currently active hazards
- available safe positions / arena corners

Health must influence target selection, but do not make the boss permanently tunnel the lowest-HP player.

A low-health player can become a strong candidate for a chase, projectile, or finishing melee attack, but recent pressure against that player must temporarily reduce priority so one person is not relentlessly deleted.

Use a sticky primary target for roughly 6-10 seconds unless:
- the target dies/leaves,
- an ability explicitly selects a different target,
- a scripted event overrides targeting,
- a major tactical condition requires a change.

---

# Combat memory and anti-repetition

Maintain history of at least the last two meaningful abilities/tactical actions.

Rules:
- Do not cast the same major ability twice in a row.
- Avoid A -> B -> A repetition for major abilities.
- Tactical attacks have a shared tactical cooldown of about 7-8 seconds.
- Each tactical attack also has its own cooldown.
- Do not immediately repeat the same tactical pattern after recovery.
- Phase changes may clear or partially reset weights, but must not allow instant spam.

Two-step tactical sequences are allowed, but keep them short.

Examples:
- `VOID_MARK -> CHEST_STRIKE`
- `REPULSE -> RIFT_ARROWS`
- `GROUND_SLAM -> CHASE`
- `ARENA_INFERNO -> FLOATING_FIREBALLS`

Do not build long scripted 5-10 move combos.

---

# Boss phases and AI progression

Use the existing phase structure:

## Phase 1 - AWAKENING: 100% to 80%

Purpose: readable introduction.

AI behavior:
- simple sticky target
- direct walking/pathfinding
- little or no flank logic
- longer recovery
- no tactical teleport attack
- no advanced group analysis

Allowed tactical attacks:
- `MELEE_SWIPE`
- `BACKHAND_SWEEP`

Core attacks/spells:
- `VOID_BLAST`
- `RIFT_PROJECTILE`

The player should learn the basic visual language here.

---

## Phase 2 - HUNT: 80% to 60%

AI improvements:
- target rotation starts to matter
- health-aware target selection starts
- boss understands near / medium / far targets
- basic flank behavior
- remembers recent targets
- begins punishing static players

New tactical attacks:
- `RUSH`
- `WALL_SMASH`

New attacks/spells:
- `CHEST_STRIKE`
- `RIFT_ARROWS`
- `VOID_MARK`

Do not unlock Edge Volley yet.

---

## Phase 3 - RIFT: 60% to 45%

This is where the boss first feels tactically advanced.

AI improvements:
- analyze party formation, not only one target
- understand stacked vs spread groups
- detect isolated players
- use short two-step plans
- react intelligently to repeated surrounding

New tactical attacks:
- `EDGE_VOLLEY`
- `ANTI_STACK_REPULSE`

New heavy/special attacks:
- `GROUND_SLAM`
- `SUMMON_SERVANTS`
- `FLOATING_FIREBALLS`
- `ARROW_RING`

Important: `EDGE_VOLLEY` begins only in Phase 3.

---

## One-time 50% HP event

When boss HP crosses 50% for the first time, interrupt normal AI exactly once.

Persist a flag such as `halfHealthEventTriggered` so it can never happen twice after heals/reloads within the same encounter generation.

Sequence:

1. boss stops normal movement and casting
2. boss moves into a clear arms-spread procedural pose
3. short telegraph
4. all current players are forcefully thrown away from the boss toward the arena walls
5. strong but controlled camera shake
6. screen darkens for roughly 3 seconds
7. players receive Slowness for roughly 5 seconds
8. arena changes and temporary pillars appear
9. normal combat resumes

Pillars:
- simple fixed arena anchors
- approximately 2 for solo/duo and around 4 for a 5-6 player party
- one skeleton on each pillar
- skeletons apply pressure with readable projectiles/status variants
- examples: Slowness, Blindness, small explosion
- killing a skeleton causes its matching pillar to visibly collapse/disappear
- pillar collapse should have readable particles/sound
- pillars are OPTIONAL pressure
- boss remains damageable
- pillars are NOT immunity nodes
- do not reuse the old `RIFT_OBELISKS` boss system for this

---

## Phase 4 - OVERLOAD: 45% to 30%

AI improvements:
- starts considering arena hazards when selecting attacks
- deliberately pressures routes through dangerous zones
- reacts faster to formation changes
- uses more ranged pressure
- still leaves dodge windows

Tactical attack availability:
- `RUSH`
- `BACKHAND_SWEEP`
- `EDGE_VOLLEY`
- `ANTI_STACK_REPULSE`
- `MELEE_SWIPE`

Temporarily disable `WALL_SMASH` in this phase because forced wall knockback combined with Inferno hazards would create too much forced movement.

`GROUND_SLAM` may remain available outside the active Inferno window, but do not select it while the arena is already in a dense Inferno/geyser sequence.

Important spell:
- `ARENA_INFERNO`

Other suitable attacks:
- `VOID_MARK`
- `RIFT_ARROWS`
- `FLOATING_FIREBALLS`
- `RIFT_PROJECTILE`

---

## Phase 5 - RAGE: 30% to 20%

This is the smartest normal-combat phase.

AI improvements:
- shorter decision delay
- better target changes
- stronger health awareness
- better short memory
- more frequent use of two-step plans
- smarter flank/reposition decisions
- strict no-repeat rules still apply

Most learned tactical attacks may return:
- `MELEE_SWIPE`
- `BACKHAND_SWEEP`
- `RUSH`
- `WALL_SMASH`
- `ANTI_STACK_REPULSE`
- `EDGE_VOLLEY`
- `GROUND_SLAM`

Do not add tentacles here.

Difficulty should increase through smarter timing and combinations, not by doubling projectile counts.

---

## Phase 6 - LAST_SEAL: 20% to 0%

This is a separate final-mode controller.

Disable normal:
- chase movement
- flank movement
- ordinary reposition teleport
- `RUSH`
- `WALL_SMASH`
- `EDGE_VOLLEY`
- normal anti-stack movement logic

Boss behavior:
- move/lock the boss above the Core
- hover roughly 2.5-3 blocks above the final position
- do not let ordinary pathfinding run
- activate visible shields
- allow a small controlled regeneration while shields are active
- cap regeneration around roughly 24-25% max HP so the phase does not reset
- stop regeneration permanently when the shield condition is broken

The final phase is built around tentacles.

---

# Tactical attacks

These are not "spells". They are physical/positioning actions selected by the AI when conditions make sense.

## MELEE_SWIPE

Use the real `swipe.json` clip.

- ordinary close melee hit
- readable hand swing
- short arc-shaped particle accent
- moderate knockback
- short recovery
- should remain one of the most common simple actions

---

## BACKHAND_SWEEP

Purpose: punish players who camp behind or around the boss.

Condition:
- multiple players are close behind/side of boss, or boss has been surrounded briefly

Behavior:
- short 0.5-0.7 second windup
- boss rotates body and performs wide arm sweep
- hit sector roughly 120-160 degrees
- moderate damage
- pushes hit players away
- local hit shake
- clear curved particle arc

Cooldown: approximately 9-12 seconds.

---

## WALL_SMASH

Purpose: rare positional knockback attack.

Condition:
- target is close
- a valid wall direction exists
- target has not recently suffered Wall Smash pressure

Behavior:
- heavy physical strike
- launch player about 5-7 blocks toward a nearby arena wall
- if player collides with wall shortly after launch:
  - small bonus damage
  - dust/debris particles at collision
  - heavy impact sound
  - brief camera shake

Do not chain-stun.

Personal target protection: roughly 20 seconds.
Ability cooldown: roughly 14-18 seconds.

Disabled in OVERLOAD and LAST_SEAL.

---

## RUSH

Purpose: punish stationary medium-range players.

Condition:
- clear line toward target
- target roughly 5-10 blocks away
- target has been relatively stationary or maintaining comfortable medium range

Behavior:
- boss leans forward for a short telegraph
- a thin readable ground line indicates charge direction
- boss dashes forward
- hit players take damage and side knockback
- boss continues slightly past impact
- on miss, boss gets a short recovery window

Cooldown: roughly 10-14 seconds.

---

## ANTI_STACK_REPULSE

This is a reactive defense, not a normal random spell.

Condition example:
- at least two players remain within roughly 3.5 blocks for around 2-3 seconds
- anti-stack cooldown is ready

Behavior:
- quick radial pulse
- push nearby players about 5-6 blocks
- limited damage or no heavy damage
- readable expanding ring
- short shake

Internal cooldown about 8-10 seconds.

If players immediately surround the boss again shortly after a repulse, AI may choose a short deliberate reposition instead of repeating Repulse.

No spam.

---

## EDGE_VOLLEY

This begins only in Phase 3.

This attack must choose the best corner intelligently.

### Position selection

Before teleport:
1. gather valid arena corner / edge anchor candidates
2. reject blocked, unsafe, or invalid anchors
3. for each candidate, compute the distance to the nearest living player
4. choose the candidate that maximizes the minimum distance to players
5. if tied, prefer the anchor with better line of sight to more players

This should feel like the boss deliberately moves to the farthest corner away from the party.

### Sequence

`TELEPORT -> AIM -> SALVO 1 -> AIM -> SALVO 2 -> AIM -> SALVO 3 -> AIM -> SALVO 4 -> AIM -> SALVO 5 -> RECOVERY`

Exactly 5 salvos. Never increase to 7 or 10 in later phases.

Before every salvo:
- reevaluate current live player positions
- rotate/aim at appropriate targets
- use health, recent pressure, and previous-salvo target history
- do not blindly target the same player five times

Once a projectile is fired:
- its direction is fixed
- no homing after launch

Suggested pattern:
- Salvo 1: fast arrows
- Salvo 2: arrows + one Rift projectile
- Salvo 3: wider fan
- Salvo 4: more precise shots at current player positions
- Salvo 5: most visually dramatic mixed volley, but not necessarily much higher damage

For 5-6 players:
- distribute ordinary arrows across players
- use only 1-2 heavier Rift projectiles per relevant salvo
- do not flood the screen

Between salvos:
- roughly 0.65-0.85 seconds
- boss visibly re-aims

Boss stays vulnerable during the sequence.

After Salvo 5:
- mandatory 1-1.5 second recovery
- resume normal AI

Personal cooldown: approximately 22-28 seconds.
Also obey the shared tactical cooldown/history system.

---

# Heavy and special attacks

## GROUND_SLAM

Use the real `udar_po_zemle.animation.json` clip.

The current clip is long. Map gameplay timing to the meaningful impact keyframe instead of making the player wait for an arbitrary full clip duration. Do not edit the animation asset itself unless absolutely necessary.

Visual sequence:
- boss stops moving
- body clearly winds up
- thin ring appears under boss
- 6-8 readable cracks extend outward
- at the impact frame:
  - heavy ground impact
  - expanding shockwave ring
  - dirt/debris/smoke particles pushed upward
  - strong local sound
  - noticeable camera shake
- damage/knockback happens on the actual impact frame, not before

The shockwave must have a readable radius.

---

## CHEST_STRIKE

Use the real `udar_iz_grudi.json` clip.

Visual:
- energy particles spiral inward toward chest/body
- core/chest point brightens
- boss leans into release
- directional burst exits forward
- narrow structured particle cone/beam/surge, not random clouds
- clear impact effect

Gameplay can be a heavy directional medium-range attack.

---

# Spell and projectile VFX rules

Create reusable particle/VFX geometry primitives such as:

- `RING`
- `ARC`
- `SPIRAL`
- `HELIX`
- `CONE`
- `RUNE`
- `SHOCKWAVE`
- `COLUMN`
- `TRAIL`
- `CRACKS`

Particles should build these shapes mathematically.

Avoid code whose only visual idea is:
"spawn N particles with random XYZ offsets."

Every meaningful attack must have:
1. TELEGRAPH
2. RELEASE
3. IMPACT or clear end state

Sounds should follow the same structure:
- telegraph sound
- release sound
- impact sound

Use existing Minecraft sounds unless a custom asset already exists. Do not stack many unrelated sounds at once.

Camera shake:
- small for normal melee
- medium for impacts/geysers
- strong for Ground Slam and 50% transition
- range-limited where appropriate

---

# Procedural animation fallback

Build a clean procedural animation layer for actions that do not yet have custom JSON clips.

Each action can define:
- windup pose
- hold pose
- release pose
- recovery pose
- durations/easing

Use smooth interpolation.

Examples:

## ARROW_RING
- arms spread outward
- short hold
- ring forms
- sharp outward release gesture

## FLOATING_FIREBALLS
- one arm sweeps
- hand remains extended
- projectiles form around hand/shoulder
- slight body lean on release

## VOID_MARK
- one arm points toward marked area
- other arm remains open
- hand tracks the ground target during telegraph

## SUMMON_SERVANTS
- both arms lift/open
- body holds a channel pose
- summon points activate around the arena

The action ID and timing contract must remain stable so a future JSON animation can replace the procedural animation without rewriting gameplay.

---

# Existing / revised spell behavior

## VOID_BLAST

Keep the concept, but remove any mismatch between telegraph point and damage point.

If a location is telegraphed, the hit must resolve at that same location unless the visual explicitly tracks the target.

Use a readable expanding blast ring and directional body pose.

---

## RIFT_PROJECTILE

Server remains authoritative for collision.

The visible projectile must be easy to see:
- compact particle core
- helix or spiral accent
- short trail
- clear impact

No homing after launch.

---

## RIFT_ARROWS

Keep as a targeted arrow volley distinct from ARROW_RING.

Use visible arrow lines/fans and readable target directions.

Do not make it a generic particle cloud.

---

## ARROW_RING

New ability.

Behavior:
- 8 arrows for solo testing
- approximately 12-16 for 5-6 players
- arrows form a ring roughly 2-3 blocks around boss
- arrows visibly point outward
- hover for about 0.8-1.0 second
- then launch radially in straight lines
- no homing

Some arrows may carry exactly one special effect:
- Slowness
- Blindness
- small explosion

Do not put multiple status effects on every arrow.

Leave safe gaps so the pattern is readable and dodgeable.

---

## FLOATING_FIREBALLS

New ability.

Behavior:
- create several Ghast-like / Rift projectile visuals around the boss
- projectiles hover for about 1 second
- select the farthest valid player at release time
- snapshot target direction at release
- then projectiles fly straight
- no homing
- boss remains stationary during the cast

Scale projectile count by party size without screen spam.

Do not allow terrain grief.

---

## VOID_MARK

Make the ground area extremely readable.

Visual:
- geometric rune, diamond, square, or controlled ring
- clearly defined boundary
- low-intensity build-up
- stronger activation state
- periodic pulse while dangerous

Players must understand exactly where the dangerous zone ends.

---

## SUMMON_SERVANTS

Remove the old generic "spell flight to target" presentation.

Instead:
- boss channels
- summon points/rifts appear at intended spawn locations
- rifts build with spirals/rings
- mobs emerge
- close the rift visual

The effect should make sense spatially.

---

# ARENA_INFERNO redesign

Duration: exactly 20 seconds.

The current short/simple Inferno presentation should be replaced.

## Floor

Convert the intended combat-floor cells to actual magma for the Inferno duration.

Requirements:
- journal every changed block
- restore every changed block on normal completion, boss death, reset, abort, server cleanup, or phase interruption
- protect core/structural blocks that must never be replaced
- no permanent arena damage

Visual telegraph before conversion:
- glowing crack patterns
- rising heat particles
- low rumble / crackling
- clear warning before damage starts

## Player effects on magma

While standing on active Inferno magma:
- periodic moderate damage
- chance / heat pulse that can set some exposed players on fire
- small armor durability damage at controlled intervals
- durability damage must be capped per Inferno cast
- do not destroy a healthy armor set from one cast
- ideally do not let this mechanic alone reduce an armor item below 1 durability

Use sparks/hissing/metal sound so durability damage has feedback.

## Steam geysers

During the 20-second Inferno, spawn temporary steam vent zones on magma.

State:
`IDLE -> WARNING -> ERUPTION -> COOLDOWN`

WARNING:
- about 0.8-1.0 second
- bubbling/steam particles
- a small readable ring on the floor
- rising hiss sound

ERUPTION:
- vertical steam column roughly 4-5 blocks high
- damage players inside
- launch them roughly 3-4 blocks upward
- may ignite exposed players
- medium local camera shake

Scaling:
- solo: about 1-2 simultaneous vents
- 2 players: about 2-3
- 5-6 players: about 4-5

Move vent locations over time. Do not simply spawn every vent directly under a player's exact feet with no warning.

---

# 50% pillars and skeleton pressure

Do not confuse these with removed boss obelisks.

These are a one-time arena event.

Use simple pillar visuals/blocks with robust cleanup.

Skeleton projectiles can have different single roles:
- Slowness
- Blindness
- small explosion

Killing a skeleton causes only its own pillar to collapse.

Boss never becomes immune because of these pillars.

---

# LAST_SEAL tentacles and shield

Tentacles appear only in LAST_SEAL.

Do not allow temporary tentacles in RAGE.

There are two types.

## 1. Guardian tentacles

Stationary around Core/boss.

Scaling guideline:
- solo/duo: 2
- 3-4 players: 3
- 5-6 players: 4

Behavior:
- guard the shielded boss
- attack/throw players who approach
- each guardian has HP
- each guardian is normally protected or difficult to damage
- after its attack, open a short vulnerability window, roughly 1.5-2 seconds
- players bait the attack, then punish it
- all guardian tentacles must die before the boss can be finished

Important:
- guardian tentacles do NOT respawn
- when the final guardian dies, the boss shield breaks permanently
- stop boss regeneration
- play clear shield-break VFX/sound

## 2. Ambush tentacles

Temporary surprise tentacles.

Behavior:
- emerge under a selected player
- visibly erupt upward
- grab the player
- short hold
- strong throw
- retract

They do not need a normal HP loop.

The current temporary `SPAWN_UNDER_PLAYER` behavior must be replaced because the existing implementation only appears/retracts without actually grabbing/throwing.

Throw should be noticeably stronger than the current weak vector.
Use a strong horizontal impulse plus upward velocity.

Fairness comes from:
- target rotation
- post-throw protection
- sensible frequency
- not repeatedly selecting the same player

---

# Shield visual

Do not build a giant noisy particle network.

Use a stable, readable shield presentation:
- orbiting plates/shards/runes, or
- a clean ring/shell
- count/state should visibly correspond to remaining guardian tentacles

The shield must not jitter or snap erratically.

When a guardian dies, visibly remove/break one shield segment.

When all guardians die:
- clear shield visual
- strong but short shield-break effect
- boss becomes finishable

---

# Movement and animation synchronization

Fix the current visual problem where the boss can move while appearing to idle/float.

The server must explicitly synchronize movement state.

Normal states should look like:

`IDLE -> RUN -> ATTACK -> RECOVERY -> RUN`

If boss velocity/path state says it is walking:
- play `RUN`
- do not leave the model on `IDLE_BREATH`

If performing a committed attack:
- stop pathfinding as needed
- play the attack/procedural animation

LAST_SEAL intentionally uses a hover/final-mode animation state.

Do not treat unintended floating during ordinary combat as acceptable.

---

# VFX ownership and networking

Gameplay remains server-authoritative:
- damage
- hit detection
- target selection
- HP
- cooldowns
- hazard cells
- teleports
- knockback decisions

Client owns presentation:
- particle geometry
- procedural bone poses
- world-space beams/rings
- camera shake
- short-lived local visual interpolation

Server should send concise semantic VFX/action events rather than hundreds of individual particle points when possible.

Example:
- action = GROUND_SLAM
- stage = IMPACT
- location
- radius
- intensity
- encounter generation

Client then renders the correct shape.

Every visual instance must be scoped to encounter generation and cleaned on:
- reset
- death
- world change
- phase exit
- disconnect
- aborted encounter

No stale beams, displays, tentacles, magma, pillars, or tasks may survive cleanup.

---

# Selection rules by phase

Do not simply carry every unlocked action forever.

Use explicit `allowedActions` / phase profiles.

Actions may:
- unlock
- disappear for a phase
- return later

This is intentional and should make each phase feel different.

Important examples:
- EDGE_VOLLEY unavailable before Phase 3
- WALL_SMASH disabled in OVERLOAD
- RUSH/WALL_SMASH/EDGE_VOLLEY disabled in LAST_SEAL
- tentacles only in LAST_SEAL
- RIFT_OBELISKS never used by boss
- normal locomotion disabled in LAST_SEAL

Use phase-specific:
- decision cadence
- recovery multiplier
- target stickiness
- flank allowance
- health-awareness weight
- group-awareness level
- allowed actions
- action cooldown multipliers

---

# Scaling

Production target: 5-6 players.

Testing target: 1-2 players.

Every mechanic must scale without changing its identity.

Scale:
- projectile count
- guardian tentacle count
- simultaneous geysers
- summon count
- pillar count

Do NOT scale difficulty primarily by:
- multiplying damage excessively
- flooding the arena with particles
- increasing Edge Volley beyond 5 salvos
- making projectiles home
- removing telegraphs

---

# Code quality requirements

Do not replace the current giant system with a different giant system.

Prefer small testable units.

Examples:
- one class for perception snapshot
- one class for combat memory
- one class for phase profile
- one selector for tactics
- one registry of action executors
- one executor per complex action or a small family of closely related actions
- one VFX geometry helper layer

Do not let one monolithic event class own every detail.

Reuse low-level existing utilities only when they are correct:
- entity lookup
- event participant lookup
- damage helpers
- safe scheduling
- arena boundaries
- cleanup registry
- client bridge
- existing texture/model conversion

Replace old high-level boss behavior when it conflicts with the new controller.

Migrate safely:
1. implement new runtime behind a controlled path/test hook
2. test actions individually
3. switch the encounter to the new runtime
4. remove obsolete old high-level boss AI only after the new runtime works

Do not delete the old working path first and leave the boss broken mid-migration.

---

# Tests and verification

Add deterministic tests wherever possible.

At minimum verify:

## Phase/action tests
- every phase exposes the intended action set
- EDGE_VOLLEY first becomes available in Phase 3
- WALL_SMASH is absent in OVERLOAD
- normal tactical movement actions are absent in LAST_SEAL
- RIFT_OBELISKS is absent from boss action pools

## AI tests
- low HP influences target score
- recent pressure prevents permanent low-HP tunneling
- sticky target behavior works
- last-two-action history prevents direct repeat and A-B-A spam
- surround timer triggers Repulse only after required time
- tactical global cooldown prevents back-to-back special attacks

## Edge Volley tests
- selects the valid corner maximizing distance from the nearest living player
- uses fallback candidate when best corner is blocked
- runs exactly five salvos
- re-aims before each salvo
- projectiles do not home after launch
- does not repeatedly choose the same player without reason

## 50% event tests
- triggers exactly once when crossing 50%
- does not retrigger after boss regeneration
- pillars do not make boss immune
- each skeleton death removes only its matching pillar

## LAST_SEAL tests
- normal pathfinding is disabled
- boss remains anchored/hovering over Core
- guardian count scales
- shield blocks final boss damage while required guardians remain
- guardians do not respawn
- final guardian death permanently breaks shield
- regen stops after shield break
- ambush tentacle actually grabs, holds, throws, and retracts

## Inferno tests
- lasts 20 seconds
- magma cells restore correctly
- abort/reset also restores all blocks
- geysers telegraph before eruption
- geyser damages and launches
- armor durability damage is bounded
- no permanent arena mutation remains

## Visual state tests
- moving boss uses RUN
- committed attack stops conflicting path commands
- real MELEE_SWIPE / CHEST_STRIKE / GROUND_SLAM IDs are actually sent
- procedural fallback is used only where no real clip exists

## Wave cleanup tests
- numbered waves no longer start Wave Front
- no expanding 20-block ring/display is spawned from the Core
- compact arrival effect can still play
- wave objectives remain unchanged

---

# Manual acceptance criteria

The implementation is not complete until the following is visibly true in-game:

1. The boss walks instead of visually skating/hovering during normal combat.
2. Phase 1 feels simple and readable.
3. Each later phase visibly changes decision-making, not only cooldown speed.
4. The boss uses player HP intelligently without permanently tunneling the weakest player.
5. The boss stops spamming the same two actions.
6. WALL_SMASH feels rare and deliberate.
7. RUSH is clearly telegraphed and dodgeable.
8. BACKHAND_SWEEP punishes players crowding behind the boss.
9. EDGE_VOLLEY starts only in Phase 3, chooses the farthest safe corner, performs exactly five re-aimed salvos, and never homes after launch.
10. The 50% transition happens once and is visually obvious.
11. Pillar skeletons create optional pressure but never gate boss damage.
12. ARENA_INFERNO lasts 20 seconds, visibly converts the floor to magma, creates readable steam geysers, ignites some exposed players, and slightly damages armor durability.
13. Geysers warn before eruption, then damage and launch players.
14. Ground Slam, Chest Strike, and Melee Swipe use the existing real animation clips.
15. Other attacks have coherent procedural animations ready to be replaced later.
16. Particle effects form readable rings, arcs, spirals, runes, shockwaves, columns, and trails instead of random clutter.
17. Sound, animation, particles, hit timing, and camera shake line up.
18. LAST_SEAL is a clearly different encounter mode.
19. Tentacles appear only in LAST_SEAL.
20. Guardian tentacles must all be killed before the boss can be finished.
21. Ambush tentacles actually grab and strongly throw players.
22. The final shield visually breaks segment-by-segment and disappears permanently.
23. No stale displays, tasks, magma, pillars, VFX, or entities remain after reset/death.
24. The old Core Wave Front is gone from numbered waves.
25. The existing working texture/UV/model conversion pipeline remains untouched and working.

---

# Implementation discipline

Before changing code:
- inspect the current boss path, phase policy, animation bridge, model renderer, VFX bridge, wave-start flow, hazard cleanup, and tests
- identify what can safely be reused
- write tests for the new controller contracts
- keep gameplay values centralized and configurable
- do not silently change unrelated wave mechanics

During implementation:
- work feature-by-feature
- verify each action in isolation
- keep logs useful but not spammy
- prefer explicit state over timer spaghetti
- make cleanup idempotent
- keep every scheduled task generation-scoped

At the end:
- run the complete test suite
- run targeted boss tests
- run wave regression tests
- manually test solo and duo
- manually test with production scaling values for 5-6 players
- verify reset/restart cleanup
- verify the texture/UV pipeline is unchanged

The goal is not to preserve the old boss internals. The goal is to preserve the working assets and encounter integration while replacing the combat runtime with a cleaner, smarter, more readable, and more cinematic boss fight.
