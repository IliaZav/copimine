# End Rift Waves, Boss, and Tentacles — Architecture Design

Date: 2026-09-24

## Scope

This design covers only the staged gameplay architecture for:
1. Waves 1-7.
2. Rift Guardian boss combat.
3. Last Seal tentacles.

Model/UV/importer work is intentionally deferred and must be repaired separately before these stages are implemented. These stages must not opportunistically redesign the model importer pipeline.

## Shared architecture

The event uses a contract-first layered architecture.

### Global encounter lifecycle

The existing End Rift facade remains the compatibility entry point, while a central encounter controller owns global transitions:

```text
IDLE
 -> RUNE_WAIT
 -> WAVE_ACTIVE
 -> WAVE_CLEANUP
 -> RUNE_WAIT
 -> ...
 -> WAVE_7_CLEANUP
 -> PRE_BOSS
 -> BOSS_ACTIVE
 -> FINISHED
```

Individual waves report completion but never start the next wave directly.

### Generation fencing

Every run has a monotonically increasing generation. Delayed work, projectiles, hazards, client sessions, temporary entities, spell runtimes, and transition timers carry generation identity. Stale work is a no-op.

### Ownership and cleanup

Every temporary object has one owner. Wave, boss, and tentacle subsystems expose idempotent cleanup. An encounter cleanup registry is a safety net, not a second gameplay engine.

### Participant roster

One authoritative participant roster distinguishes registered, active, online, alive, and objective-eligible players. Individual mechanics must not redefine participation through arbitrary nearby-player queries.

### Server/client authority

Server owns gameplay: health, damage, targeting validation, cooldowns, collision, movement effects, projectile trajectories, objective completion, room completion, boss/tentacle states.

Client owns presentation: HUD, shaders, fog, local target outline, particles, beams, animation, camera shake, visual interpolation.

The existing End Rift client bridge is extended rather than replaced.

## Stage 1 — Waves

Between Waves 1-6, transition runes require all required active/alive/online participants to stand in the rune area continuously for 10 seconds. Countdown is numeric only and resets when a required player leaves. The old expanding Wave Front is removed from the live flow. After Wave 7, runes never return.

### Wave 1
Preserve the simple 3 Carrier -> Charge -> Core deliveries. No added projectile minigame. Core visually progresses 1/3, 2/3, 3/3. Delivery state is authoritative and deadlock-safe.

### Wave 2
Exactly 3 mark/hunt cycles. Server selects a valid marked player; the marked player receives the intended local shader and mobs prioritize them. Target repetition is avoided when alternatives exist. Effects clear on all exit paths.

### Wave 3
Three large portals captured sequentially, about 5 seconds each with existing grace/decay behavior. Only one portal is active at a time. Client visually collapses the portal as capture progresses.

### Wave 4
Obelisks remain a Wave 4 objective. Server-authoritative visual states are FULL, DAMAGED, CRITICAL, DESTROYED. Model/UV importer work is not touched.

### Wave 5
Exactly three black-fog cycles. Fog is a real client rendering session, safe zones stay readable, and server owns safe-zone gameplay/damage. Fog must restore on completion, reset, disconnect, and world change.

### Wave 6
Wave 6 is decomposed into ritual roster, Sphere runtime, Caster controller, guard controller, major spell controller, prisoner controller, prisoner ability controller, drain policy, and client session.

Exactly five Casters drive progression:
- 1st death disables Rift Barrage and unlocks A.
- 2nd disables Gravity Well and unlocks S.
- 3rd disables Soul Brand and unlocks D.
- 4th disables Rift Chains and unlocks F.
- 5th breaks the prison and releases the prisoner.

There is no Resonance/spell-strength scaling system.

Only one major Sphere spell can be active at a time.

Rift Barrage uses charged straight projectiles with no homing after release. Gravity Well uses a fixed ground center and server-authoritative bounded pull. Soul Brand follows the marked player until detonation at the player's current position. Rift Chains telegraphs then applies strong slow plus bounded pull, not a full stun.

Prisoner controls use A/S/D/F. The custom Fabric client renders four 32x32 pixel-art icons with LOCKED, READY, COOLDOWN, and NO_TARGET states. Cooldown timing is server-authoritative. Target selection is local to the prisoner; valid allies receive a cyan local-only outline and valid conversion targets a violet/red local-only outline. The server revalidates every cast.

Abilities:
- A: ally heal, roughly 3 hearts, ~20s cooldown.
- S: ally Speed I + modest outgoing damage bonus for ~7-8s, ~28-30s cooldown.
- D: ally protective field for ~6s, ~30s cooldown.
- F: convert an eligible ordinary hostile for ~20-25s, then dissolve/remove, ~50-60s cooldown. Converted mobs are removed from hostile objective accounting immediately.

Old REVERSE_MOVEMENT and CONTROL_SWAP mechanics are removed from the redesigned live Wave 6.

### Wave 7
Wave 7 owns room assignment, Rift barriers, four isolated trial controllers, and regrouping.

Trials:
1. Warden mini-boss: frontal strike, sweep, short charge.
2. Rift Reflection: invulnerable Eye + three sequential seals; reflected slow projectile must hit active seal three times.
3. Juggernaut: bait three straight charges into sequential active anchors, then short exposed finish.
4. Rift Hunter: mobile special Hunter with disappear/trace/reappear and telegraphed jump/charge.

Rift Walls are server-authoritative barriers with client visual rendering. Completing a room opens its barrier and lets completed-room players help unfinished rooms. Orphaned rooms have a simple recovery path so disconnects cannot permanently deadlock the wave.

### Post-Wave-7
After Wave 7:
- fully heal active players;
- repair damageable armor/weapons by 30% of each item's maximum durability;
- begin one total 40-second pre-boss transition;
- no manual rune;
- boss starts exactly once through BossStartGateway.

## Stage 2 — Rift Guardian Boss

The boss subsystem is split into encounter control, combat control, perception, memory, target selection, tactic selection, action selection/registry, movement control, phase control, damage policy, hazards, presentation bridge, one-time half-health event, Last Seal controller, and TentacleGateway.

Normal combat has one authoritative major-action lifecycle:

```text
MOVE -> SELECT -> TELEGRAPH -> EXECUTE -> RECOVERY -> MOVE
```

Only one committed major action is active at a time.

### Perception and memory
A periodic immutable perception snapshot holds player and group information. Combat memory tracks sticky target, recent pressure, action history, and one-time event flags.

Target stickiness is roughly 6-10 seconds. Low HP creates interest but recent pressure reduces repeat focus so the boss does not tunnel one player indefinitely.

### Phases

```text
AWAKENING 100-80
HUNT       80-60
RIFT       60-45
OVERLOAD   45-30
RAGE       30-20
LAST_SEAL  20-0
```

Phase profiles define allowed actions and behavior rather than scattering HP checks across the codebase.

### Movement ownership
BossMovementController is the only owner of normal pathing/teleport/reposition. Combat grants FREE, ACTION_CONTROLLED, LOCKED, or LAST_SEAL movement mode. Generic movement cannot reposition the boss during committed locked attacks.

### Important actions
Melee Swipe and other authored attacks use explicit semantic timeline markers. Rush telegraphs, then commits to a straight charge with no homing. Wall Smash uses a short-lived wall-impact context and is disabled in OVERLOAD. Anti-stack Repulse is a reactive tactic.

Edge Volley is available from RIFT onward. The boss chooses the valid arena corner maximizing minimum distance from living players, then fires exactly five salvos. Each salvo may re-aim before release; each projectile travels straight after release. Suggested cooldown is ~22-28 seconds. Edge Volley is disabled in LAST_SEAL.

### 50% event
A dedicated controller triggers exactly once when HP first crosses <=50%:
- interrupt safely;
- throw players toward walls;
- camera shake;
- darkness ~3s;
- slowness ~5s;
- spawn skeleton pillars;
- return to combat.

Pillars do not gate boss damage.

### Inferno
Arena Inferno belongs to OVERLOAD and lasts exactly 400 ticks / 20 seconds. It owns a block mutation journal, temporary magma state, capped armor durability pressure, steam geysers, and deterministic restoration.

### Last Seal
At <=20%, normal combat action selection pauses. Boss anchors/hovers above Core, shield activates, and small regeneration is capped around 24-25% max HP.

Tentacles are accessed only through TentacleGateway. Tentacle subsystem reports Guardian defeats; BossLastSealController owns shield, regen, and boss damage admission.

While shield is active, boss damage is blocked. After all Guardians are permanently defeated, shield is permanently broken and regen permanently disabled for the encounter generation.

## Stage 3 — Last Seal Tentacles

Tentacles remain a separate segmented entity system. They are not world blocks and not part of the Guardian body model.

The runtime chain roles remain:

```text
root
segment_1
segment_2
segment_3
tip
```

The current tentacle texture contract remains 64x64 unless the prior dedicated art-repair stage intentionally updated all dependent contracts.

A logical tentacle owns five carrier/segment runtimes. A shared geometry profile defines segment lengths, thickness, joint overlap, interaction geometry, grab offsets, hold offsets, and scale. A pose solver composes parent-child transforms so segments form one continuous chain.

Gameplay grab geometry is derived from the authoritative TIP transform; gameplay does not require a new decorative grab_socket bone.

### Guardian
Guardian FSM:

```text
EMERGING
 -> CHANNELING
 -> TARGETING
 -> TELEGRAPH
 -> ATTACK
 -> HOLD or MISS
 -> THROW if held
 -> VULNERABLE
 -> RECOVERY
 -> CHANNELING
```

The Guardian is protected outside the explicit VULNERABLE window. Vulnerability is roughly 1.5-2 seconds. A successful contact creates a short HeldPlayerState, then a strong throw.

Throw math normalizes XZ separately and sets Y independently. Initial tuning target: horizontal ~1.10-1.25, vertical ~0.65-0.80. A short 2-3 second post-throw protection prevents immediate re-grab chains.

Guardian count:
- 1-2 players: 2 Guardians.
- 3-4 players: 3 Guardians.
- 5-6 players: 4 Guardians.

Defeated Guardians are tracked in a generation-scoped defeat ledger by logical slot and never respawn during that encounter generation.

### Ambush
Ambush is a temporary hazard with no objective HP:

```text
HIDDEN
 -> TELEGRAPH
 -> EMERGING
 -> GRAB_ATTEMPT
 -> HOLD/MISS
 -> THROW if held
 -> RECOVERY
 -> RETRACT
 -> DONE
```

It warns before emerging, can miss if the player dodges, and uses the same authoritative TIP-based grab geometry. One central AmbushScheduler controls spawn frequency. Max active Ambush target is about 1 for solo/duo and 2 for a 5-6 player group on different targets.

### Shield integration
Each Guardian death emits GUARDIAN_DEFEATED. The final death emits ALL_GUARDIANS_DEFEATED exactly once. The boss subsystem permanently breaks shield and disables regen. Tentacle code does not directly toggle boss invulnerability.

### Client
Client receives semantic tentacle bind/state/target/health/unbind updates, uses existing carrier/entity transforms plus state timelines for interpolation, and does not receive per-bone transforms every render frame.

### Cleanup
Tentacle clear releases held players first, then cancels states, removes proxies/carriers/displays, clears client bindings and scheduled work, and removes temporary target protections. Cleanup is idempotent and generation-safe.

## Verification

All three stages require automated architecture/gameplay tests plus real Minecraft live verification. A passing build alone is not enough to claim success.

The existing project test/build gates should be used or their current equivalents located if paths changed.

## Deferred work

Model/UV/importer redesign is explicitly excluded from these three stages. It is a separate future project and must not be mixed into Waves, Boss, or Tentacle implementation.
