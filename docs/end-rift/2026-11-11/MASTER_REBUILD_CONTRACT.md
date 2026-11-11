# COPIMINE END RIFT — COMPLETE WAVES 1–7 AND RIFT GUARDIAN REBUILD

## ROLE AND MISSION

You are the lead software architect, senior Java/Fabric/Paper engineer, technical artist, AI combat engineer, and release-quality reviewer responsible for completing the CopiMine End Rift event.

Your task is to implement, debug, integrate, and verify the entire End Rift experience:

1. Waves 1–7.
2. All elite mobs and their AI.
3. All special projectiles, models, textures, animations, sounds, and spell effects.
4. The Wave 6 Rift Ram encounter.
5. The Wave 7 miniboss encounters.
6. All six Rift Guardian boss phases.
7. A substantially improved, responsive, aggressive boss combat AI.
8. The final Kagune encounter.
9. Recovery, persistence, synchronization, performance, regression tests, and actual in-game visual verification.

This is NOT a request to simply add particles or make isolated cosmetic adjustments.

The user has repeatedly experienced builds that compile successfully while the actual Minecraft gameplay remains broken, visually weak, or inconsistent with the design.

Your objective is a cohesive, production-quality experience where every attack works, every visual corresponds to real gameplay, and every phase feels distinct.

**Do not declare success merely because the code compiles or CI passes.**

---

# PART 0 — REPOSITORY, SOURCE OF TRUTH, SAFETY, AND EXECUTION RULES

## 0.1 Repository

Canonical GitHub repository:

`https://github.com/IliaZav/copimine`

Known local source-of-truth checkout:

`D:\Desktop\Copimine\copimine-source-of-truth`

Known integration worktree:

`D:\Desktop\Copimine\copimine-source-of-truth\.worktrees\integration-20261030`

Known boss-polish worktree:

`C:\Users\zavod\.codex\worktrees\rift-guardian-polish-20261110\integration-20261030`

Known working branch at the time this task was prepared:

`codex/rift-guardian-polish-20261110`

Last verified snapshot commit:

`3f2249402d3926f06a96ac1e8de02bc4d35c3fbb`

This commit is a reference point, NOT an instruction to reset or discard more recent changes.

Before modifying anything:

- Identify the actual active worktree and branch.
- Run `git status`, `git branch -vv`, `git worktree list`, and `git log`.
- Fetch current GitHub state.
- Compare local and remote HEADs.
- Inspect all uncommitted changes.
- Determine which fixes from this specification may already exist.
- Preserve all existing worktrees, untracked artifacts, logs, screenshots, databases, world files, and user data.
- Never force-reset, clean, or overwrite another agent's changes.
- Do not accidentally work in an outdated integration branch.
- Do not silently merge unrelated branches.

If another coding agent is actively editing the same files, avoid concurrent conflicting edits. Coordinate through separate commits or isolated worktrees.

## 0.2 Existing architecture

The project includes:

- Paper-compatible Minecraft 26.3 server.
- `copimine-end-event` server plugin.
- Fabric-based `CopiMineClient`.
- Resource-pack assets and client shaders.
- The existing event packet/bridge protocol.
- Existing arena state, PostgreSQL-backed systems, migration and recovery tooling.
- Automated Java, Python, Node.js, and PowerShell test infrastructure.

Relevant areas include:

`copimine-end-event/src/me/copimine/endevent/`

`copimine-end-event/src/me/copimine/endevent/domain/`

`copimine-end-event/src/me/copimine/endevent/runtime/`

`CopiMineClient/src/main/`

`tools/minecraft-26.3/client/`

`resourcepacks/`

`tests/`

`docs/end-rift/`

`scripts/minecraft/`

Do not assume that a particular source tree is the only compiled or packaged client source. Trace the actual build pipeline, source synchronization, asset packaging, release manifests, and installed JAR hashes.

## 0.3 Essential existing documents and evidence

Inspect the relevant current versions of:

- `docs/end-rift/final-26.3/`
- `docs/end-rift/2026-11-10/PLAYTEST_REPAIR_CONTRACT.md`
- `docs/end-rift/2026-11-10/PLAYTEST_REPAIR_VERIFICATION_20261111.md`
- `docs/end-rift/2026-11-10/WAVES_1_5_REPAIR.md`
- `docs/end-rift/2026-11-10/GUARDIAN_VERIFICATION.md`
- `docs/end-rift/2026-11-10/GUARDIAN_ART_DIRECTION.md`
- `docs/end-rift/WAVES_REPAIR_LEDGER.md`
- Other relevant wave and boss specifications.

Review all recent commits by DeepSeek, Codex, and other agents.

**Their reports are evidence of attempted work, not proof that a feature works.**

Inspect actual changed code, tests, logs, assets, and Minecraft output.

The user's newest gameplay decisions in this task override contradictory older documents.

Do not silently follow obsolete Ritual Sphere, Kagune respawn, generic boss towers, or old Wave 6 designs.

## 0.4 F2 screenshots and visual sources

Inspect the actual Minecraft F2 screenshots from the user's playtest, including the November 10, 2026 sequence.

Known screenshot directory:

`C:\Users\zavod\AppData\Roaming\ElyPrismLauncher\instances\Copimine\minecraft\screenshots`

Inspect screenshot timestamps, game stages, model positioning, broken geometry, fog opacity, bosses, particles, and UI.

Also inventory all existing model and animation archives.

Known Kagune source model:

`CopiMineClient/src/main/asset-source/end-rift-tentacle/kagune.bbmodel`

Existing animation names include:

- `idle`
- `emerge`
- `telegraph_grab`
- `grab_success`
- `hold`
- `trow`
- `grab_miss`
- `hurt`
- `death`
- `retract`
- `spawn_under_player`
- `shield_channel`

Do not recreate animations that already work.

Do not assume an animation is usable merely because its name exists. Inspect its keyframes, pivots, bone names, transformations, total duration, rendering compatibility, and actual client playback.

Search existing archives for additional original animation and model sources before creating replacements.

## 0.5 Non-negotiable architecture rules

1. The server is authoritative for combat, damage, collision, health, movement constraints, event ownership, rewards, and progression.
2. The client is responsible for complex visual rendering, animation playback, camera effects, shaders, and presentation.
3. The resource pack owns reusable model and texture assets where appropriate.
4. Every visible projectile must correspond to a real server-authoritative projectile or an explicitly synchronized deterministic collision simulation.
5. Every temporary object must have an owner, event generation, unique identity, cleanup path, and maximum lifetime.
6. Every major cast must follow a controlled lifecycle: prepare, telegraph, select/lock target, execute, hit/miss, recover, cleanup.
7. Do not create gameplay behavior by adding particles without fixing the underlying mechanics.
8. Do not replace Minecraft's vanilla art style with generic high-poly models or visually incompatible effects.
9. Avoid excessive HUD text, chat spam, full-screen labels, and visual clutter.
10. Do not create unbounded scheduled tasks or per-tick database operations.
11. Preserve the real End Rift arena, rune locations, world data, backups, and legitimate player inventories.
12. No accidental world destruction outside the controlled arena mutation system.
13. The event must work after restart, abandonment, player death, disconnect, and repeated attempts.
14. Keep already working features intact and add regression coverage.

## 0.6 Implementation workflow

This is one master assignment but should NOT be implemented as one unreviewable giant code change.

Create a concise implementation ledger and proceed through independently testable milestones:

- M0: source audit and reproduction.
- M1: shared correctness and projectile/custody fixes.
- M2: Waves 1–5.
- M3: Wave 6.
- M4: Wave 7 and elites.
- M5: boss damage, visuals, AI foundation.
- M6: boss phases 1–5.
- M7: boss phase 6 and Kagune.
- M8: integrated visual acceptance, performance, recovery, CI, and release candidate.

Each milestone needs its own tests and commit.

Do not mark a milestone complete until its actual acceptance criteria are satisfied.

---

# PART I — SHARED FIXES REQUIRED BEFORE WAVE POLISH

## 1.1 Restore reliable physical projectiles

A real playtest showed skeletons and elites attempting ranged attacks without any visible projectile.

Production logs contained events such as:

`WAVE_PROJECTILE_ADMISSION_REJECT`

`requested_children=1 active_children=0 cap=0`

`SKELETON_ARROW_ADMISSION_BLOCKED`

Root cause identified during review:

The projectile budget and admission rules disagree about participant counts during certain live/test scenarios. One layer considers a single active test player valid, while another computes a projectile capacity of zero.

Inspect and reconcile:

- `WaveProjectileAdmissionController`
- `PressureBudgetController`
- Skeleton and elite projectile handlers.
- Current event roster and actual living-player count.
- Official encounters versus test encounters.
- Projectile group reservations and leases.

Requirements:

- A valid skeleton must actually fire a physical arrow.
- The arrow must be visible from launch until impact or expiration.
- Each accepted cast must have the correct projectile budget.
- A reserved volley must either spawn completely or fail atomically.
- Failed reservations must not permanently consume capacity.
- Projectile death, collision, despawn, chunk unload, and cancellation must release leases.
- Fix minimum-player test compatibility without accidentally making production caps unlimited.
- Elite projectile groups may have a separate bounded budget.
- A 2-player event must not silently disable elite volleys.
- Test 1-player diagnostic scenarios, plus official 2/5/10/11/20-player rosters.
- Add diagnostic output distinguishing invalid shooter, cooldown, budget denial, spawn failure, and actual successful shot.

Do not claim this is fixed based only on unit tests. Verify arrows in Minecraft.

## 1.2 Authoritative visual projectile standard

All important ranged attacks must have:

- A real physical collision body or deterministic collision geometry.
- A distinct 3D model.
- A proper texture.
- Flight orientation following the velocity vector.
- Optional spin, body animation, and local particle trail.
- Launch, travel, collision, and expiration effects.
- Proper sound effects.
- A maximum lifetime.
- A bounded per-player rendering cost.

Avoid relying exclusively on invisible Snowballs, arrows, or flat particle trails.

Use server `ItemDisplay`/`BlockDisplay` entities for suitable simple models, and Fabric rendering for advanced animated geometry.

Do not duplicate the same projectile visually through both the server and client.

## 1.3 Packet and client synchronization

Inspect the current client bridge protocol and event state updates.

Previous playtests exposed delayed or missing model state during message bursts.

Requirements:

- No dropped permanent model bindings under normal bursts.
- Deterministic event generation IDs.
- Ordered and idempotent state transitions.
- Client rejoining during an active wave receives the authoritative current scene.
- Stale packets from previous event generations are ignored.
- Large layouts and multiple model events must not exhaust transport queues.
- No permanent invisible models because a single update was missed.
- Critical events must be recoverable through snapshots or reconciliation.

Test burst traffic with representative 2-player and 20-player scenes.

---

# PART II — WAVES 1–7

# WAVE 1 — THE ELITE CHARGE CARRIER

## 2.1 Intended experience

Wave 1 is an objective-based battle, not merely a mob kill counter.

A special elite enemy carries the charge. The players must kill the correct enemy, recover the charge, deliver it to the Core, and defend the process.

The scenario requires three successful charge deliveries.

Preserve the current enemy scaling and ensure early combat remains active enough for larger parties.

## 2.2 One charge, one visible model

The elite carrier currently may show two charge models:

- One above its head.
- Another attached near its body.

This is incorrect.

Requirements:

- Exactly one authoritative attached charge visual, positioned clearly over the elite's head.
- No duplicate charge on its chest, torso, hands, or body.
- Correct animation and interpolation while the elite moves.
- No clipping through the elite's model.
- No second charge when the elite respawns or changes animation.
- No physical charge dropped before the elite dies.
- No independent random charge drops.

On eligible elite death:

1. Detach and remove the carrier visual.
2. Create exactly one legitimate charge item.
3. Record its unique event-owned identity.
4. Allow only an eligible player to acquire it.
5. Start or resume the approved delivery gameplay.

No duplicate actors, no orphan visual entities.

## 2.3 Fix charge duplication and delivery failure

The player reproduced a serious bug:

Moving the charge around the inventory creates additional copies, eventually a large pile, while the charge cannot be delivered correctly.

Treat this as a high-priority data-integrity defect.

Inspect:

- `RiftCarrierCustodyPolicy`
- `InventoryClickEvent`
- `InventoryDragEvent`
- Item pickup/drop.
- Offhand movement.
- Cursor items.
- Player containers and inventories.
- Slot reconciliation.
- Token generation and ownership metadata.
- Recovery after disconnect and death.

The fix must guarantee:

- One legal current token per event charge generation.
- Exactly one authoritative custodian.
- Invalid duplicates are rejected/quarantined without destroying the legitimate charge.
- No accidental cloning through cursor swaps.
- No duplication through Shift-click, number keys, drag-splitting, Q/drop, double-click collection, offhand swap, or containers.
- The player cannot load or submit a counterfeit or stale token.
- A valid token can always be delivered using the intended interaction.
- Completing delivery consumes exactly one authoritative token.
- Rapid repeated input never yields multiple deliveries.
- Disconnect, death, event cancel, and server restart preserve consistent ownership.
- If the original token is genuinely lost, recovery follows a controlled, logged rule; do not silently mint multiple items.

Write real inventory-event integration tests and a Minecraft/manual matrix of all inventory interactions.

## 2.4 Charge presentation

Preserve/improve:

- A visible shrinking charge ring, changing green to red.
- The charge's 15-second hold/charging behavior if required by the current objective contract.
- Carrier Slowness II when carrying.
- A clear carrier-only beam between the Core and the current charge carrier.
- The beam must NOT be visible to unrelated players.
- Clear feedback while the delivery is processing.
- Three to four visibly different Core texture states during progression.
- No unwanted global Core pulse.
- No intrusive text overlays.

Verify every transition visually and in server logs.

---

# WAVE 2 — THE HUNT / SPECTRAL ENDERMEN

## 3.1 Scene and group progression

Preserve the approved hunt concept:

- Three limited enemy groups.
- A target player is marked during the active group.
- The mark transfers approximately every 20 seconds while the current group is still alive.
- After a group is defeated, pause approximately 5 seconds before spawning the next group.
- The next group's targets and observers update correctly.
- Group three includes one or two elite enemies.
- Marked-player controls are inverted where required by the approved gameplay contract.
- Only the marked player receives the relevant target effect.
- Ordinary enemies must not be accidentally assigned as phantom-activation actors.
- All three groups must complete; no stale state after the first group.

Avoid changing the gameplay to an unrelated 45-second marker or endlessly spawning new groups.

## 3.2 Critical phantom layout mismatch

Verified source mismatch:

Server:

`WaveTwoPhantomLayoutPolicy.TARGET_PHANTOM_COUNT = 35`

`WaveTwoPhantomLayoutPolicy.MIN_PHANTOM_COUNT = 25`

Client:

`WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT = 70`

`WaveTwoPhantomLayout.MIN_PHANTOM_COUNT = 50`

The client may reject the server's entire layout as incomplete.

Fix this end-to-end.

The intended presentation is approximately 70 client-only spectral Endermen when the arena can support them.

Requirements:

- Define one compatible authoritative count contract.
- Target approximately 70 observers.
- Permit a safe fallback only when explicitly supported by both server and client.
- Avoid rendering none because 35 is below a hardcoded client minimum of 50.
- Validate safe spawn positions.
- Ensure observers do not stand inside blocks or overlap visibly.
- Avoid spawning 70 unnecessary physical server mobs.
- Deliver all necessary layout information reliably.
- Show phantom observers staring at the marked player.
- On activation, appropriate figures materialize and become real enemies.
- Show the characteristic raised arms, scream, and threatening movement.
- Newly materialized enemies target the marked player.
- Validate activation in every group, not only group one.

## 3.3 Intro, fading, and music

Improve the existing introduction:

- A deliberate initial appearance.
- Short, controlled darkening/fade.
- Spectral silhouettes emerging across the arena.
- A coordinated gaze toward the target.
- A clear audio transition into the hunt.

Do not use chaotic full-screen shader effects during the entire wave.

Preserve the clean glow-focused mark presentation.

Fix music repeatedly restarting or overlapping when targets, mobs, or group states change.

A musical track should start/stop according to the encounter state, not once per spectator.

Test death, relog, target rotation, and group transitions.

---

# WAVE 3 — FUNCTIONAL PORTALS AND DESTRUCTION

## 4.1 Portal gameplay

Fix all portal activation and spawn failures.

Requirements:

- Every portal visibly opens.
- Every portal creates the intended enemies.
- Enemy and elite spawning is not blocked by stale counters.
- Preserve the approved rule that the elite enemy appears starting at the second portal, not the first.
- Portal capture/closure logic actually progresses with eligible players.
- A portal cannot permanently remain open after its objective is completed.
- Players cannot count twice for the same closure event.
- Portals cannot accidentally advance the whole wave before all required encounters are complete.

## 4.2 Portal visual overhaul

The portal should be a 3D animated structure rather than a weak flat effect.

Required stages:

1. **Dormant:** barely visible dormant structure.
2. **Opening:** frame segments rotate and separate, depth appears in the center, particles move inward/outward.
3. **Active:** stable animated center, visible distortion, periodic sounds, and distinct enemy spawn cues.
4. **Capture:** structure becomes unstable, movement accelerates and geometry contracts.
5. **Collapse:** sections fold inward and visibly implode.
6. **Explosion:** a short strong energy burst with sound, debris, and controlled knockback.
7. **Cleanup:** all owned visual and physical entities are removed.

The player specifically reported that closing portals had weak/no explosion and poor animations.

Do not merely add more generic explosion particles.

Use animated geometry and a clear collapse sequence.

Do not damage the permanent arena blocks unless the encounter's reversible mutation rules explicitly permit it.

## 4.3 Portal tests

Verify:

- Each portal actually spawns enemies.
- The second portal's elite works and casts its spells.
- Capture progress cannot freeze.
- Early player death/leave is handled.
- Closing animation plays once.
- Explosion and knockback occur once.
- Stale delayed tasks do not reopen closed portals.
- All temporary displays and particles expire.

---

# WAVE 4 — OBELISKS

## 5.1 Gameplay fixes

The obelisks now appear, but the user reported:

- No effective pushback.
- No convincing explosion/destruction when killed.
- Far too many projectiles for a small party.

Preserve the six-obelisk encounter and its real arena geometry.

Each obelisk must:

- Visibly show health/damage stages.
- Aim using the current valid target's location.
- Fire real visible projectiles.
- Deal actual damage or force movement.
- Push away players standing dangerously close.
- Play an obvious destruction animation.
- Stop firing immediately when destroyed.
- Never remain as an invisible active hazard.

## 5.2 Party-scaled firing budget

Do not independently increase every obelisk's attack rate without a global cap.

Implement a fair shared attack director:

- 2–3 players: only a limited number of obelisks fire simultaneously.
- 4–5 players: moderate overlapping attacks.
- 6–10 players: more simultaneous pressure.
- 11–20 players: suitably increased but bounded attack pressure.

Keep attack timing staggered.

Prevent simultaneous unavoidable projectile carpets.

Use target tracking during telegraph but commit direction when the projectile is launched.

## 5.3 Knockback and destruction

Close-range pushback:

- Brief warning.
- Clear visual pulse.
- Measurable displacement.
- Collision-safe direction.
- Cooldown so the player is not permanently stunlocked.

Destruction:

- New cracks appear and expand.
- Stone segments visibly fracture.
- Model breaks from bottom to top or collapses convincingly.
- A strong final energy burst occurs.
- Dust, debris, and audio correspond to the impact.
- Physical obelisk/hazard state terminates atomically.

Preserve the existing `PREPARED`/`APPLIED` hazard-journal handling and recovery safeguards.

---

# WAVE 5 — UNIFORM DARK WITHERING FOG

## 6.1 Non-negotiable visual direction

The existing black fog is too opaque and often looks like a black screen.

The intended look is:

**Extremely dense, dark, frightening black-brown-purple volumetric fog.**

It is NOT vanilla Soul Sand Valley fog.

It is NOT a full-screen black overlay.

It is NOT a collection of visually separate fog zones.

The fog must be visually uniform across the entire arena, including safe areas.

Recommended art palette:

- Almost-black charcoal.
- Muddy dark brown.
- Deep desaturated violet.
- Sparse brighter violet or soul-tinted highlights.

The visible environment must remain genuinely three-dimensional.

The player must still distinguish the ground immediately under their feet, approximately 2–3 blocks away.

More distant geometry should fade aggressively into the volumetric darkness.

Near-floor terrain and silhouettes must not be completely erased.

## 6.2 Shader improvements

Inspect:

`tools/minecraft-26.3/client/src/main/resources/assets/copimineclient/shaders/post/wave5_volume.fsh`

Retain/improve the existing depth-based volumetric effect rather than replacing it with a cheap screen tint.

Requirements:

- Depth-aware fog that preserves nearby geometry.
- Consistent overall density across the arena.
- Smooth fade-in and fade-out.
- Dark brown/violet-black spectral coloration.
- No uniform pitch-black screen.
- Correct behavior around the player's hand, held items, HUD, and world geometry.
- No camera-dependent discontinuities.
- No harsh banding, broken near-plane artifacts, or flickering.
- Stable frame rate on the intended client hardware.

## 6.3 Atmospheric particles and sounds

Add a layered atmospheric particle system, with performance limits:

- Dark ash and soot.
- Drifting black smoke.
- Low-density purple sparks.
- Withering/soul-like wisps.
- Occasional slow-moving dark particle clusters.
- Brief low-frequency audio pulses.
- Distant eerie ambient sounds.
- Appropriately muffled attacks.

The atmosphere should feel contaminated and supernatural.

Avoid filling the entire camera with opaque sprites.

All particles should have varied speed, scale, lifetime, and movement.

Do not use a single repeated particle type everywhere.

## 6.4 Safe-zone guidance

The user no longer requires a beacon beam.

Do NOT introduce a real vanilla beacon structure merely to render a light beam.

Instead:

- Clearly mark safe-zone locations before the fog becomes fully active.
- After fog activation, keep the safe-zone guide particles strongly visible for another 3–5 seconds.
- Use bright luminous turquoise, green, or another high-contrast appropriate accent.
- Guide particles fade slowly rather than disappearing instantly.
- Render these cues correctly through the fog using the client visual system.
- Ensure their brightness does not become a permanent visibility exploit.
- Never hide safe zones before players have had a fair opportunity to reach them.

Keep the fog itself visually uniform in safe and unsafe cells. Safety is a gameplay property, not a different fog-density region.

## 6.5 Wither and Slowness

Apply damaging effects only outside genuine safe zones.

Suggested baseline:

- 2–10 players: Wither II.
- 11–20 players: Wither III.
- Short, controlled Slowness effects to increase urgency.
- No permanent blindness or complete loss of movement control.

The final values must be tested for survivability and adjusted if necessary.

At safe-zone entry:

- Stop the fog's damaging exposure.
- Remove or expire only potion effects owned by the fog system.
- Do not indiscriminately remove unrelated potion effects from other mechanics.

Do not let a player die from an unavoidable combination of darkness, Slowness, and Wither before they could physically reach the indicated refuge.

## 6.6 Real safe zones

Fix the reported bug where mobs occupy safe zones.

Requirements:

- Hostile mobs cannot spawn inside refuge zones.
- Mobs already inside are removed or safely relocated.
- Mobs cannot immediately walk back into an active refuge.
- Mobs cannot attack through the safe boundary if that violates refuge immunity.
- The player may enter and leave freely.
- No blocked or unusable refuge positions.
- All refuge collision and ownership rules are server-authoritative.

Keep the previously developed multi-cycle design, approximately 5–10 fog cycles scaled to the party, unless the latest authoritative requirements indicate a more recent explicitly approved count.

Do not blindly shrink a refuge to 1×1 when the party cannot physically fit.

Refuge capacity and warning time must account for roster size and walkable paths, not just Euclidean distance.

## 6.7 Pressure and waves

Preserve enemy pressure throughout the encounter.

If all mobs are killed before the first fog event, the wave must not become empty and idle.

Add or retain controlled reinforcement groups between fog cycles.

Ensure each cycle's spawn, warning, active fog, safe state, ending, and cleanup work reliably.

### Mandatory visual proof

Capture:

- Before fog.
- Fog entering.
- Fully active fog looking toward the ground.
- Fully active fog looking across the arena.
- Safe-zone guide immediately after activation.
- Guide fading at 3 and 5 seconds.
- Player inside and outside a refuge.
- Enemy attempts to enter refuge.
- Wither/Slowness application.
- Fog removal after the cycle.

---

# WAVE 6 — RIFT RAM AND THE FINAL SKELETON PILLAR

## 7.1 Replace obsolete mechanics

The old Wave 6 Ritual Sphere/prisoner/mage encounter is obsolete for the current design.

Do NOT restore the Ritual Sphere or its legacy combat flow.

The intended Wave 6 boss is the oversized Ravager called **Rift Ram**.

## 7.2 New pillar structure

Implement:

**Four ordinary pillars followed by one very large final pillar.**

Ordinary pillars:

- Each requires one successful Rift Ram collision.
- Each accepted impact visibly damages the Ram's armor.
- Each destruction changes the model and armor HUD.
- The next pillar is introduced correctly.
- Pillars should have good custom textures, silhouettes, cracks, and destruction effects.

Final pillar:

- Significantly larger and more impressive.
- Custom 3D geometry and textures.
- Glowing core, visible layers, and progressively growing fractures.
- Requires **three distinct successful Ram collisions**.
- On the first collision, its outer shell cracks.
- On the second, a large section breaks and the core is exposed.
- On the third, it collapses completely and the Ram loses the last of its armor.

That is a total of seven accepted pillar collisions across the wave.

Each collision must be counted exactly once.

A charge that glances against the same pillar must not produce multiple armor-stage transitions.

## 7.3 Elite skeleton on the final pillar

Spawn a special elite skeleton on top of the final pillar.

This is the **only skeleton tower/pillar mechanic** in the event.

Do not add skeleton towers to any phase of Rift Guardian.

The skeleton:

- Uses a custom model and meaningful animations.
- Moves across the upper platform instead of standing motionless.
- Fires visible 3D bone-themed projectiles.
- Uses triple-shot and another distinct elite ranged ability.
- Threatens players while they attempt to line up the Rift Ram charge.
- Can be killed by players before the pillar collapses.

If still alive when the pillar is destroyed:

- Trigger a fall/leap/drop transition.
- Land safely.
- Continue combat on the arena floor.

The elite must never become an unreachable orphan actor.

Define explicit completion handling for the skeleton and Ram.

## 7.4 Rift Ram visuals

Improve the existing Ravager encounter:

- Scale it to approximately 1.7× normal size, subject to actual collision clearance.
- Red eyes during charge preparation.
- Dense smoke emitted from the nostrils.
- Heavy breathing and threatening roar.
- Dust and block-debris effects at each footstep.
- Stronger running animation.
- More convincing movement speed.
- Longer straight charge distance.
- Properly locked direction after telegraph.
- Significant collision impact audio.
- Different sounds for successful and failed charges.
- Visible damage to armor after each pillar collision.
- Large stagger animation when hitting a pillar.

The Ram should feel heavy and dangerous, not like an ordinary enlarged Ravager.

Avoid making its direction change abruptly after the player has already dodged.

## 7.5 Seismic Stomp

Make the stomp much more obvious.

Requirements:

- Heavy full-body warning animation.
- Ground vibration.
- Expanding visible 3D shockwave.
- Ground debris and dust.
- Strong directional audio.
- Clear damage and Slowness outcome.
- No invisible damage radius extending far beyond the shown wave.
- An achievable dodge/jump/counterplay window.

Preserve bounded damage and cooldown rules.

## 7.6 Endermites

The summoned Endermites need actual distinctive custom models.

Implement:

- A coherent Rift-themed shell.
- Glowing cracks or internal color accents.
- Articulated movement where justified.
- Proper scaling and collision.
- Spawn and death effects.
- Controlled AI and target selection.
- No invisible vanilla model accidentally left under a custom overlay.

Do not use a static floating cube as the final asset.

## 7.7 Armor boss bar

The Rift Ram needs a custom boss health/armor UI.

The bar must show:

- Current Ram health.
- Current armor strength.
- Seven meaningful damage milestones corresponding to the four minor and three final collisions.
- A distinct exposed state when armor is completely broken.

A full custom texture is preferable to drawing repeated unrelated vanilla icons.

All state comes from the server.

After the final collision, the Ram takes the intended full damage.

Increase combat pressure with stronger behavior, smarter targeting, and limited Endermite spawns; avoid artificially inflating HP or making the fight endlessly repetitive.

---

# WAVE 7 — FIX ALL MINIBOSSES AND TRIALS

## 8.1 General problems

Wave 7 now starts, but the user observed:

- Broken miniboss models.
- Detached or incorrect geometry.
- Poor UV mapping.
- Actors standing still.
- Weak spell effects.
- Inconsistent enemy counts.
- Only one skeleton where two or three were required.
- Minibosses without meaningful combat AI.

This wave needs a full gameplay and presentation pass.

## 8.2 Preserve the current authoritative encounter structure

Inspect the current Wave 7 route and all approved design documents before changing chamber topology.

Some historic commits used one sequential arena for everyone; other states used multiple chambers for larger parties.

Do not silently change the routing based on an older document.

Resolve the final user-approved routing contract from recent source evidence, preserve player transfer/return invariants, and document the chosen behavior.

Regardless of routing:

- Every encounter must start.
- Every miniboss must move.
- All required participants must be admitted.
- No room may complete prematurely.
- No actor may remain after completion.
- No player may be stranded after death, disconnect, or restart.

## 8.3 Miniboss presentation

Every miniboss requires:

- Proper original model geometry.
- Correct UV coordinates.
- Correct pivots and rotations.
- Animated idle, locomotion, windup, attack, hit, and death.
- Actual AI movement.
- Hitboxes matching visible models.
- Distinct spells and audio.
- Correct owner and session generation.
- No static model while the underlying entity walks away.
- No model rooted to the ground while its hitbox moves.

Inspect the user F2 screenshots and correct the broken presentation based on actual evidence.

## 8.4 Mobile Marksman skeletons

The intended group count is:

- Two skeletons for a small party.
- Three skeletons from five participants upward.

The current code contains a dedicated squad-size policy; preserve and verify it.

The skeletons must:

- Spawn as distinct actors.
- Move to different valid positions.
- Create recognizable crossfire.
- Fire visible real projectiles.
- Reposition when players close the distance.
- Avoid permanently standing in one spot.
- Remain addressable individually for recovery.
- Complete the trial only after all required marksmen are defeated.

Their attacks must not unintentionally harm friendly event mobs.

## 8.5 Distinct encounter identities

Review the existing trial types, including guardian/bruiser, Echo, marksmen, and archmage variants.

Make them meaningfully distinct.

Heavy guardian:

- Approaches and applies close-range pressure.
- Uses a powerful but clearly telegraphed impact.

Echo opponent:

- Has distinctive responsive movement.
- Uses convincing attack animations and appropriate clone behavior.
- Does not become a static mannequin.

Marksmen:

- Use mobile coordinated ranged combat.

Archmage:

- Keeps distance intelligently.
- Uses several visually distinct 3D spells.
- Relocates when threatened.

Do not simply increase damage or mob HP.

## 8.6 Verification

Record separate gameplay captures for every miniboss:

- Initial spawn.
- Running animation.
- Pathfinding.
- Each major cast.
- Projectile travel.
- Actual hit.
- Miss.
- Taking damage.
- Death.
- Encounter completion.
- Return to the common event flow.

---

# PART III — ELITE MOB AI AND SPELL LIBRARY

## 9.1 Shared elite architecture

Elite mobs across relevant waves must have real, differentiated combat roles.

Do not create variations consisting only of recolored arrows, particle rings, or increased HP.

All elite spells require:

- An actual gameplay role.
- Readable telegraph.
- Distinct 3D projectile or moving geometry where appropriate.
- Proper model/texture.
- Hit/miss collision logic.
- Animation and audio.
- Recovery and cooldown.
- Cleanup.
- Unit and live tests.

## 9.2 Elite Skeleton

Role: mobile ranged pressure.

Required attacks:

- Triple arrow shot.
- A distinctly designed bone-arrow rain or explosive arrow attack.
- Physical, visible, properly aimed arrows.
- Proper 3D projectile model/texture.
- Movement to maintain firing distance.
- Retreat/reposition when attacked up close.
- No permanently stationary shooting animation.
- No invisible arrows or no-op bow attacks.

Different spells must have different silhouettes, sounds, timing, and on-impact effects.

## 9.3 Elite Spider

Role: crowd control and support.

Required abilities:

- Throws a **visible flying cobweb block/model** toward a player.
- Cobweb flight has actual orientation, speed, and collision.
- The projectile visibly impacts or misses.
- On impact, apply the appropriate bounded web/crowd-control effect.
- Use Poison/Nausea according to the approved spell design.
- Preserve a meaningful Weaving/support mechanic.
- Do not spam permanent cobweb blocks into the world.
- Cleanup temporary blocks/displays correctly.
- The spider must actively choose targets and cast spells.

Do not accept a particle-only snowball as the finished cobweb spell.

## 9.4 Elite Enderman

Role: intelligent mobile caster.

Required behavior:

- Mostly casts at medium distance.
- Uses a few distinct, modeled spells.
- When rushed, performs one or two melee strikes.
- Teleports or relocates to a safe position.
- Does not teleport too frequently.
- Never becomes impossible to hit.
- No random teleport through protected walls.
- No endless stationary casting.

## 9.5 Elite Zombie

Role: heavy frontline tank.

Required behavior:

- Approximately double-sized where appropriate.
- Distinct heavy zombie model.
- Strong knockback resistance.
- A visible aura following the zombie correctly.
- Spawns a bounded group of helper zombies, approximately four with safe player scaling.
- Helpers have real AI and targets.
- No obsolete shield mechanic.
- No obsolete "dead hand" drop.
- Proper audio and hit feedback.

## 9.6 Ordinary mob combat

Review normal zombies, skeletons, spiders, Endermen, and other event mobs.

Ensure:

- AI is not incorrectly disabled during official or test waves.
- Ranged mobs actually release projectiles.
- Collision and attack callbacks run.
- Melee mobs pursue players.
- No target selection of dead/spectator/nonparticipants.
- No enemy gets stuck indefinitely in scenery.
- Boss/player protection rules are not accidentally applied to all event mobs.

---

# PART IV — RIFT GUARDIAN: COMBAT FOUNDATION

# 10. THE BOSS MUST ACTUALLY FIGHT

## 10.1 Global boss philosophy

Rift Guardian must be a dangerous hybrid between:

- Aggressive melee fighter.
- Powerful magical caster.
- Tactical target selector.
- Reactive battlefield controller.

The user must feel that they are fighting one intelligent, imposing creature, not waiting between independent timer-driven spells.

Every phase changes not just spell availability but also the boss's preferred engagement style.

Do not rewrite everything from scratch without first inspecting the current AI framework.

Relevant existing concepts include:

- `BossBrain`
- `BossIntentSelector`
- `BossPhasePolicy`
- `BossMovementPolicy`
- `BossCloseCombatPolicy`
- `CombatTacticsPolicy`
- Existing boss animation, hitbox, and damage controllers.

Reuse correct systems and replace only incorrect ownership, transitions, and decisions.

## 10.2 Diagnose and fix boss freezing

A verified problematic pattern in the current code:

`holdBossCastPosition()` may call:

`mob.getPathfinder().stopPathfinding()`

`mob.setAI(false)`

`mob.setAware(false)`

This can immobilize the boss through repeated casts.

The boss's melee system also uses an eight-second auxiliary cooldown and excludes `LAST_SEAL`.

Required changes:

- Distinguish mobile casts from stationary casts.
- Small projectiles should not freeze movement.
- Target evaluation should continue during compatible casts.
- Locomotion, spell casting, and melee decisions need coordinated but independent schedules.
- Hard movement locking should be reserved for exceptional authored animation moments.
- Recovery must always restore proper navigation and gravity.
- No stale cast flag may leave the boss stationary forever.
- No teleport or pathing loop may trap the boss in place.
- An animation end must not silently leave the underlying entity with AI disabled.

## 10.3 Hybrid combat architecture

Implement or refine a focused separation of responsibilities:

**Movement controller**

Responsible for:

- Pursuit.
- Strafing.
- Closing distance.
- Safe retreat.
- Repositioning.
- Target approach.
- Obstacle avoidance.
- Recovery from being stuck.
- Arena-leash enforcement.

**Melee controller**

Responsible for:

- Choosing contact attacks.
- Direction and range.
- Combo state.
- Anti-spam cooldowns.
- Animation contact markers.
- Real damage.
- Knockback.
- Reactions to shields and misses.

**Spell controller**

Responsible for:

- Ability selection.
- Cast ownership.
- Telegraph.
- Projectile release.
- Hazard reservation.
- Damage/collision.
- Recovery.
- Cleanup.

**Decision coordinator**

Responsible for:

- Assessing the current player arrangement.
- Selecting the next intent.
- Preventing incompatible attacks from running simultaneously.
- Avoiding long inactive gaps.
- Choosing target changes fairly.
- Handling phase transitions and cancellations.

Do not create multiple authorities that fight over `setAI`, movement velocity, target, and cast state every tick.

## 10.4 Tactical perception

The boss should understand:

- Nearest reachable player.
- Visible player.
- Player outside melee range.
- Grouped players.
- Isolated players.
- Players surrounding the boss.
- Players repeatedly attacking from behind.
- Players escaping toward arena edges.
- Whether the boss is stuck or unable to reach a target.
- Recently attacked players.
- Players under other hard crowd-control effects.
- Whether a major arena hazard is currently active.

Expected reactions:

| Situation | Boss behavior |
|---|---|
| Player enters melee range | Immediate suitable melee action |
| Player repeatedly retreats | Pursuit, gap close, or ranged pressure |
| Players surround boss | AOE push/slam with readable windup |
| Players cluster together | Group punishment ability |
| Players spread out | Hunt a vulnerable isolated player |
| Boss cannot path | Select alternate safe destination |
| Heavy cast just ended | Resume movement immediately |
| Previous attack missed | Recover, retarget, or change approach |
| Boss is in a mobile cast | Continue legal movement |
| Boss is in a heavy grounded cast | Lock only for the necessary animation segment |

Never allow the boss to simply stand idle while a valid nearby player attacks it.

## 10.5 Responsive melee

Improve:

- Normal claw/hand strike.
- Cross-body swipe.
- Short two-hit combo.
- A three-hit combination in later phases.
- Ground slam.
- Directed chest strike where phase-appropriate.
- A short-range circular knockback attack.

Normal close-range attack cadence should be much faster than the current eight-second restriction.

Use approximately 1.8–2.4 seconds as an initial basic-hit tuning range where appropriate, then tune through actual gameplay.

Do not allow animation spam or unavoidable continuous stagger.

Each melee attack must use real authored animation frames and real contact timing.

Existing clips may have long durations, such as approximately 3 seconds for a swipe and 9 seconds for a ground slam.

Do not naively cancel the clip midway while the server's damage keyframe remains unchanged.

Retarget, retime, or create dedicated shorter gameplay clips while preserving the actual animation identity.

Hitboxes and real damage frames must match the displayed movement.

## 10.6 Boss health, accepted damage, and red hit feedback

The user reports that hitting the boss often produces no red damage reaction and may fail to decrease its health.

Treat this as a critical gameplay defect.

Trace:

- `EntityDamageByEntityEvent`.
- Composite hitbox proxies.
- Boss carrier entity.
- Damage cancellation by the event plugin.
- Other plugin listeners.
- Eligibility and participation checks.
- Real entity health.
- Server damage routing.
- Phase-specific immunity.
- Guardian shield immunity.
- Boss bar updates.
- Client model renderer and hurt overlay.

Requirements:

**Accepted hit**

- Real server HP decreases.
- Boss health bar updates.
- Client receives an authoritative accepted-hit event.
- The entire visible boss model flashes red briefly.
- Proper hurt animation or physical reaction plays.
- Hit sound and particles occur at the correct location.

**Blocked hit**

- No false HP loss.
- Clearly visible shield impact.
- Correct blocked-hit sound.
- No misleading red flesh damage flash.

**Missed hit**

- No false damage or hurt animation.

A hit against the actual visible body must not disappear merely because its proxy hitbox is outdated.

Test sword, axe, bow, reflected fireball, ranged projectile, multiple simultaneous players, every boss phase, and the cocoon shield.

Create automated damage-routing tests plus actual client verification of 10 consecutive accepted hits.

## 10.7 Boss hit-stun and motion

The boss should react when damaged without becoming permanently stunlocked.

Separate:

- Visual hurt reaction.
- Real HP loss.
- Knockback resistance.
- Ability interruption.

Ordinary player hits should not always cancel a boss spell or reset its movement controller.

Explicit special interrupts may do so, but only when deliberately designed.

---

# PART V — BOSS PHASES

# PHASE 1 — AWAKENING, 100–80% HP

## 11.1 Intended identity

This phase introduces the boss's hybrid fighting style.

It must already be dangerous.

Do not make this phase an idle tutorial where the boss stands still and launches occasional particles.

## 11.2 Required behavior

- Actively chase players.
- Use proper melee range handling.
- Perform real hand swipes.
- Launch 3D Rift projectiles.
- Cast a visually significant Void Blast.
- Move during compatible magic.
- Recover rapidly after casts.
- Change target when the original target becomes invalid.

## 11.3 Visual spell improvements

**Void Blast**

Create an animated volumetric distortion with a clear charge, expanding energy geometry, explosive release, hit reaction, and decay.

**Rift Projectile**

Replace particle-only Snowball visuals with an authored 3D projectile, a dark/violet texture, velocity-aligned rotation, and controlled trailing effects.

**Melee Swipe**

Synchronize the original boss hand animation with a shaped impact arc and actual contact detection.

---

# PHASE 2 — HUNT, 80–60% HP

## 12.1 Intended identity

This is a pursuit phase.

The boss should behave like an active hunter rather than a stationary archer.

## 12.2 Behavior

- More frequent target assessment.
- Direct pursuit when the target is far away.
- Flanking and side attacks when possible.
- Avoid repeatedly targeting only the same person.
- Close combat and magic combinations.
- No meaningless teleport spam.
- No prolonged stationary casting.

## 12.3 Abilities

Preserve and improve:

- Rift projectiles.
- Three-arrow Rift volley.
- Void Mark.
- Chest strike.
- Void Blast.
- Hand swipes.

Make every ability visually distinct.

**Chest Strike**

The energy must visibly emerge from the animated chest and travel forward as a real directional attack.

**Rift Arrow Volley**

Use actual, visible modeled arrows.

**Void Mark**

Use animated 3D marking geometry and a clear active/expired state.

## 12.4 Distinctive phase behavior

Make the boss chain a pursuit into a meaningful attack.

Example:

Approach → side-step → swipe → short ranged follow-up.

The boss must not repeatedly stop moving between each step.

---

# PHASE 3 — RIFT, 60–45% HP

## 13.1 Completely upgrade the Rift fractures

The current implementation mainly draws particle rings over the floor.

This is not sufficient.

The user wants **real arena blocks visibly transforming into fissures that erupt into enormous fiery geysers.**

## 13.2 Fracture lifecycle

**Stage 1 — Preparation**

- Select valid arena floor cells.
- Replace or overlay the surface with cracked Rift-themed blocks.
- Create a visible widening fracture.
- Show internal orange/violet heat.
- Emit dust, ash, and pressure smoke.
- Play stone-cracking sounds.

**Stage 2 — Warning**

- Approximately 1.5–2 seconds of clear warning.
- The fracture grows and the surface begins glowing.
- The player can clearly identify the eruption zone.
- No hidden instant damage.

**Stage 3 — Eruption**

- A huge fiery geyser rises approximately 8–12 blocks.
- Use a volumetric model, animated geometry, and complementary particles.
- Strong eruption sound.
- Real collision/damage corresponding to the visible geyser.
- Hit players are burned, launched, or pushed appropriately.

**Stage 4 — Collapse**

- The fire column contracts.
- Ash and embers fall.
- The floor remains briefly scorched.

**Stage 5 — Restoration**

- Restore the exact original blocks, including their block data.
- Remove all owned displays and hazards.

## 13.3 Frequency and scaling

Initial target:

- Approximately one new fracture batch every 8–12 seconds.
- 2–4 simultaneous fractures for a small party.
- Up to 6 for larger parties.
- Avoid overlapping every escape path.
- No duplicate damage for one eruption.
- Recalculate safe cells from real arena geometry.

The frequency should feel threatening, but must remain physically dodgeable.

## 13.4 Preserve the real arena

Use the existing hazard mutation journal and its recovery lifecycle.

Handle:

- Save-before-mutate.
- `PREPARED` versus `APPLIED`.
- Exact block-state restoration.
- Plugin disable.
- Crash/restart.
- Wave/phase cancellation.
- Protected Core and rune blocks.
- Player-placed or protected structures.
- Stale generation callbacks.
- Multiple fracture batches.
- Overlap with Phase 4.

Do not leave permanent magma or cracked blocks after the phase.

---

# PHASE 4 — OVERLOAD, 45–30% HP

## 14.1 Intended identity

This is the full inferno phase.

**The whole arena should appear to be on fire.**

Not merely a handful of randomly placed magma blocks.

## 14.2 Transition

On entering the phase:

1. The boss performs an authored heavy ground-impact animation.
2. A visible heating wave spreads from the center toward the edges.
3. The arena floor changes to fiery/magma-themed textures.
4. Flames, black smoke, embers, and heat distortion rise.
5. Large geysers begin erupting in selected positions.
6. The soundtrack transitions into a distinct inferno theme.

The arena should feel like a catastrophic environment transformation.

## 14.3 Full arena fire

Preserve the existing reversible magma mutation system.

However, create an actual authored scene with:

- Consistent hot material palette.
- Cracked lava-like floor geometry.
- Animated lava seams.
- Tall flame structures.
- Large but bounded geysers.
- Glowing embers.
- Low drifting smoke.
- Differentiated eruption audio.

The entire arena should look dangerous.

For fairness, there must still be temporary survivable movement routes or cooling surfaces.

Safe regions must be visibly recognizable; do not create invisible safe cells inside identical burning textures.

Avoid global unavoidable damage every tick.

## 14.4 Duration

Baseline: approximately 20 seconds, using the existing configurable duration contract.

The phase as a whole continues according to HP thresholds; the inferno is an important phase ability/environmental event rather than a permanent map corruption.

Ensure it can restart only according to sensible cooldown rules.

## 14.5 Other boss actions

The boss must continue fighting during the inferno when compatible:

- Pursuing or repositioning.
- Using permitted melee attacks.
- Casting compatible projectile abilities.
- Avoiding endless stationary state.

Do not stack a fully unavoidable immobilization spell on top of an arena-wide damaging environment.

## 14.6 Skeleton towers

There must be **NO skeleton towers or skeleton archer turrets in any boss phase**.

The only approved skeleton-on-pillar mechanic is in Wave 6.

---

# PHASE 5 — RAGE / ECHO OF FURY, 30–20% HP

## 15.1 Unique identity

Do not implement Rage as merely Phase 4 with more speed and more fireballs.

This phase introduces **two fundamentally different Echo mechanics**, combined with the boss's aggressive hybrid combat.

The player should temporarily struggle to determine which enemy is the real Rift Guardian.

However, the mechanics must remain readable and fair.

## 15.2 Echo Type A — delayed attack replicas

During certain melee or directional attacks, the real boss creates one or more temporary spectral echoes.

Each echo:

1. Appears at a valid position with a short distinctive arrival effect.
2. Locks onto one specific eligible player.
3. Uses the same authored attack family as the originating boss action.
4. Repeats the attack after a visible delay.
5. Applies real damage only at the matching hit keyframe.
6. Triggers a delayed explosion after the repeated strike.
7. Disappears with a short collapse animation.

Requirements:

- Echoes must have proper copies of the boss model and attack animations.
- Use translucent/darker visual treatment.
- Distinct sound separating real and echoed strikes.
- The echo attack must visibly target the selected player.
- Target choice must be valid and server-owned.
- The final attack direction/position must lock at a reasonable telegraph point.
- Do not invisibly home at a moving player during the final attack frames.
- The echo's explosion must have a visible warning and real radius.
- Explosion damage must not accidentally occur multiple times.
- Echoes cannot produce loot or participate in phase progression.
- Echoes clean up on death, phase transition, restart, and disconnect.
- A dead/disconnected target cancels or safely retargets the specific echo.
- A player must be able to dodge the copied attack and explosion.

This is a delayed combat action, not a persistent miniboss.

## 15.3 Echo Type B — persistent boss decoys

The real boss also has a rare ability to summon combat-capable replicas of itself.

Rules:

- Each replica has exactly **50 HP**.
- Replica attacks deal less damage than the real boss.
- Maximum **five living decoys** simultaneously.
- Summoning cooldown approximately **150 seconds** (within the requested 2–3 minute interval).
- No forced repeated summoning if the phase ends quickly.
- Scale initial decoy count reasonably for the number of players while respecting the maximum of five.
- Each decoy has real AI, movement, attacks, and collision.
- Decoys use the same visual model family as the real boss.
- Decoys should not immediately reveal themselves through obviously broken animations.
- The real boss remains visually plausible among them.
- A careful player should still be able to identify the real boss through subtle, learnable clues.
- Decoys must not share the real boss's authoritative HP state or boss bar.
- Their deaths never trigger the real boss's defeat.
- They drop no inappropriate items or rewards.
- They cannot damage other event mobs.
- They must not become permanent orphan entities.

Use subtle identification clues such as a consistent difference in Rift core energy, a slightly delayed motion detail, or a unique reaction after taking damage.

Do not place an obvious "FAKE" label above the decoys.

The goal is tactical uncertainty, not an arbitrary guessing game.

## 15.4 Boss combat during Echo phase

The real boss remains aggressive.

It should:

- Move among replicas.
- Use rapid pursuit and flanking.
- Initiate two- or three-hit melee combinations.
- Mix short ranged pressure with echo strikes.
- Punish clustered players appropriately.
- Avoid standing still behind its clones.
- Never become immune simply because a decoy exists.

## 15.5 Phase end

On entering the final seal:

- Stop new echo generation.
- Cancel any incompatible delayed echoes.
- Cleanly dissolve or remove persistent decoys through a short visual transition.
- Prevent abandoned decoys from becoming real boss instances.
- Clear all decoy targets, tasks, damage listeners, and boss-bar references.

---

# PHASE 6 — LAST SEAL, 20–0% HP

## 16.1 This phase has two separate combat states

**A. Cocoon / protected magical battle.**

**B. Cocoon destroyed / final melee fury.**

These are NOT interchangeable.

The second state must permanently replace the first during that encounter.

---

# 17. FINAL SEAL STATE A — THE KAGUNE COCOON

## 17.1 Large protective Kagune

Spawn **six to eight large defensive Kagune** near the boss.

They form the cocoon and guard the floating Guardian.

Use approximately:

- 6 for small parties.
- 7 for medium parties.
- 8 for large parties.

These must be closer to the boss than the current widely spaced guardians.

All large guardians:

- Have real HP.
- Can be killed.
- Play proper damage reactions.
- Flash/redden when accepting hits.
- Animate their body segments.
- Attack nearby players actively.
- Use a heavy directed strike with knockback.
- Push players approximately 5–6 blocks on a successful heavy strike.
- Deal meaningful party-scaled damage.
- Are resistant to repeated hit interruption.
- Do not permanently freeze when several players attack together.
- Play proper destruction/death animation.
- Never respawn after being genuinely killed.

**Remove the existing 90-second guardian respawn behavior.**

Do not merely set an enormous respawn timer.

The death ledger must persist the fact that each guardian is permanently destroyed within the current encounter generation.

On restart, only restore guardians that were still alive before the interruption.

Never resurrect a legitimately destroyed guardian.

## 17.2 Defensive Kagune combat logic

They should not simply stand around the boss as target dummies.

Give them:

- Independent target selection.
- Telegraph and directed stabbing/striking.
- 5–6-block knockback on heavy contact.
- Cooldowns preventing repeated instant knockback.
- Anti-stunlock behavior.
- Real damage and clear impact animation.
- Shared pressure controls so all eight do not attack one player at precisely the same moment.

Their defense should be challenging but not artificially endless.

## 17.3 Cocoon vulnerability

While at least one required large guardian remains alive, the boss's core protection remains active.

Attacks against the shielded boss must create correct shield-hit feedback rather than silently disappearing.

Once all large guardians are destroyed:

- The cocoon permanently fails.
- The boss can never return to the invulnerable cocoon state.
- No guardian respawn.
- No new guardian creation.
- No regeneration.
- Begin the authored falling transition.

Do not implement a short vulnerability window followed by renewed invulnerability.

The user explicitly wants a permanent final fight after the cocoon breaks.

## 17.4 Regeneration reduced by five times

Current values are too high:

- 1% max HP restored per second.
- 25% max HP total regeneration cap.

Replace with:

- **0.2% max HP per second.**
- **5% max HP total cap per encounter generation.**

Regeneration is permitted only while the cocoon has living defensive guardians and the corresponding state is valid.

It must stop permanently after the cocoon is destroyed.

Ensure the health bar never displays false or delayed increases.

---

# 18. SMALL ATTACKING KAGUNE

## 18.1 Separate from defensive guardians

Small Kagune are NOT the 6–8 large cocoon defenders.

They are an independent aggressive enemy type.

Requirements:

- Spawn approximately every 15–20 seconds while the cocoon battle is active.
- Up to **30 living small Kagune** on the arena.
- Scale spawn batch size to the admitted party.
- Do not always force the cap of 30.
- Each survives until players actually kill it.
- No short forced lifetime that makes small Kagune disappear before combat.
- Small Kagune may be killed by players.
- Proper server HP and collision.
- Proper independent target selection.
- Clear quick directed melee attacks.
- Visible damage feedback.
- Death animation and cleanup.
- Smaller and visually distinct from the large guardian Kagune.
- No overlapping roots or inescapable dense clumps.
- No excessive server entity count: share or optimize rendering where possible.

The existing `SmallKagunePolicy` and temporary tentacle count limits do not implement this specification and must be revised.

Do not allow the limit of six temporary tentacles to silently cap these permanent small combat enemies.

After cocoon destruction:

- Stop spawning new small Kagune.
- Already living small Kagune remain killable and follow their normal lifecycle.
- Do not let them restart the cocoon or prevent the boss from entering final melee fury.

---

# 19. NON-DESTRUCTIBLE SPELL KAGUNE

These are not ordinary killable enemies.

They are temporary animated spell constructs.

They appear, execute a specific ability, and disappear.

Do not give them permanent guardian HP or respawn bookkeeping.

## 19.1 Grab and throw

A smaller Kagune emerges under a chosen player.

Stages:

1. Ground warning.
2. Emergence.
3. Grab attempt.
4. Successful capture OR miss.
5. Hold.
6. Throw.
7. Retract.
8. Cleanup.

Use the original `kagune.bbmodel` animations where appropriate.

A miss must actually miss and finish normally.

A successful grab must not leave the player permanently restrained after disconnect, death, restart, cancellation, or phase change.

## 19.2 Directed stab

A Kagune emerges, bends backward, then thrusts sharply toward the chosen target.

Use a newly authored stab animation if the existing clips do not provide a good result.

The hitbox follows the animated strike rather than a disconnected circular radius.

Include a clear windup and miss behavior.

## 19.3 Group ground eruption

The boss can summon several temporary Kagune under a cluster of players.

Targeted area approximately 4×4 or 5×5 blocks.

Stages:

- Mark the selected ground area.
- Play emergence warning.
- Multiple Kagune rise in a coordinated attack.
- Hit players are launched approximately five blocks upward.
- Apply bounded direct damage.
- Animate downward withdrawal.
- Clear all displays and hazards.

Do not hit the same player repeatedly from overlapping models within one cast.

Use real server collision logic.

Prevent unexpected extra lethal fall damage from stacking with the launch unless intentionally balanced.

---

# 20. THREE-ROW EXPANDING KAGUNE RING

## 20.1 Signature ability

The boss releases a massive expanding ring consisting of three sequential rows of Kagune.

The rows move outward from the boss.

Each row:

- Smoothly emerges from the ground.
- Rises into an attacking pose.
- Strikes upward.
- Launches hit players approximately five blocks.
- Deals real damage.
- Smoothly descends/retracts.

This is a large animated arena spell.

It must not look like an ordinary circle of particles.

## 20.2 Random gaps

Each row contains multiple **two-block-wide gaps** through which players can avoid the attack.

Rules:

- Gap positions are random.
- They are NOT centered on individual player positions.
- The rows must NOT share one automatically aligned straight escape lane.
- Players need to move around the circle to find the next opening.
- Later rows provide enough staggered time for that movement.
- More players means more total valid gaps.
- Gaps must remain well separated.
- Do not create a mathematically impossible no-escape pattern.
- Do not place passages through solid arena obstacles.
- Freeze the random gap layout at cast start.
- Do not change the layout mid-cast.

Suggested starting counts:

- 2–3 players: 2 gaps per row.
- 4–5 players: 3 gaps per row.
- 6–10 players: 4 gaps per row.
- 11–20 players: 5–7 gaps per row.

Validate these counts against the real arena circumference and player movement speed.

## 20.3 Timing and hit rules

Each row needs:

- Visible preparation.
- Predictable outward movement.
- Real time to reach the next gap.
- One direct damage event per player per overall ring cast.
- No instant stacking of all three rows on the same launched player.
- Controlled knock-up.
- Correct knockback/fall interaction.
- Clear end and cleanup.

The huge number of visible tentacles must NOT mean creating one full pathfinding mob per visible segment.

Keep one server-authoritative spell state and render the repeated tentacle rig instances efficiently on the client.

---

# 21. COCOON BOSS PROJECTILES AND ANTI-CAMPING COMBINATION

## 21.1 Boss is not passive inside the cocoon

While suspended above the Core, the Guardian must actively cast its own ranged attacks.

Use custom 3D:

- Rift bolts.
- Larger cocoon energy projectiles.
- Appropriate short volleys.
- Proper hit/miss and collision.

All projectiles should originate from the actual boss model, not an invisible unrelated position.

The boss must not constantly teleport while pinned.

## 21.2 Anti-camping reaction

If one or more players stay very close to the boss for approximately 5 seconds, and the combination is available:

1. The boss clearly prepares a heavy radial repel.
2. A strong animated shockwave pushes close players away.
3. The displacement remains inside safe arena boundaries.
4. After a visible short delay, the boss begins the three-row Kagune ring.
5. The combination enters a separate cooldown.

Initial cooldown: approximately 25–30 seconds.

Do not continuously restart the combo when a large group keeps someone near the boss.

The combo must have an understandable tell and be avoidable/counterable through movement and gap selection.

---

# 22. FINAL SEAL STATE B — COCOON FALL AND MELEE FURY

## 22.1 Transition

After the last large defender dies:

1. All cocoon shield visual anchors break.
2. The boss stops hovering.
3. Its protective energy collapses.
4. A proper falling animation starts.
5. The boss descends visibly toward the ground.
6. A strong landing animation plays.
7. Dust, fragments, shockwave, camera response, and sound synchronize with ground impact.
8. The boss's active combat AI resumes.
9. The final melee-fury state begins permanently.

Do not teleport it instantaneously from the Core into a standing pose.

Do not let the body visually float while the hitbox stands on the floor.

No repeated fall after restart or repeated checks.

## 22.2 Triple incoming damage

The boss now takes **3× accepted player damage until its death**.

Apply the multiplier only in this specific final-fury state.

Preserve correct source damage, resistance, custom hitboxes, and event accounting.

Do not use cosmetic boss-bar health loss to imitate this multiplier.

## 22.3 Maximum melee aggression

The final state should be dominated by extremely aggressive close combat.

No long-range spellcasting loop.

No return to stationary cocoon channeling.

No renewed protective guardians.

No new small-Kagune spawns.

The boss should:

- Sprint or rapidly approach players.
- Use fast hand/claw combinations.
- Execute two- and three-hit sequences.
- Use powerful close-range ground impacts.
- Use short, controlled gap-closing moves.
- Perform directional or circular melee pressure.
- Punish players remaining too close behind it.
- Change target intelligently.
- Resume movement after every completed attack.

The fight should feel like a desperate final confrontation.

Do not use the current `LAST_SEAL` melee prohibition.

Replace the current spell selection after cocoon break with an explicit legal melee-fury ability profile.

Keep every attack readable.

Do not make the boss permanently invulnerable, permanently stationary, or impossible to hit during the fury.

## 22.4 Final death

The real boss death requires a complete cinematic:

- Death begins only after real HP reaches zero through an accepted official hit.
- The boss plays an actual death animation.
- Rift energy collapses around its body.
- Remaining cocoon artifacts dissipate.
- Existing combat music stops correctly.
- A short unique defeat/victory soundscape or music cue plays.
- The model visibly collapses/dissolves.
- The End Rift event completes once.
- The End is unlocked according to the existing official progression rules.
- Rewards are issued once to eligible participants.
- No inappropriate vanilla Ender Pearl drop from the boss.
- No duplicate experience, rewards, or completion events.
- No abandoned boss models, projectiles, Kagune, clone actors, or scheduled tasks.

Manual/test boss deletion must NOT trigger official event completion or reward issuance.

---

# PART VI — 3D MODELS, TEXTURES, ANIMATIONS, PARTICLES, AUDIO

## 23.1 All spells must look intentionally authored

The user's central complaint is that existing spell effects are weak, repetitive, or limited to ordinary particles.

For every important boss, miniboss, or elite ability, provide a complete presentation package:

- Distinct silhouette.
- Dedicated 3D model where suitable.
- Distinct texture.
- Source model asset.
- Animated movement or geometric transformation.
- Distinct telegraph.
- Distinct projectile path/attack movement.
- Hit effect.
- Miss effect.
- Expiration effect.
- Appropriate sounds.
- Proper cleanup.

Do not call a static particle ring a complete 3D spell.

## 23.2 Use the established art pipeline

Prefer:

- Existing original boss models.
- Existing Kagune model and animation source.
- Original Blockbench/Chameleon/Fabric animation sources.
- Server ItemDisplay/BlockDisplay for simple authored objects.
- Fabric renderer for advanced multi-bone animations.
- Resource-pack models and textures for physical spell projectiles.

Generate new assets only when the existing originals are insufficient.

Preserve bone names, pivots, skeleton hierarchies, UV coordinates, and original texture identity unless an intentional documented revision is necessary.

Do not replace original user-supplied models with generic approximate substitutes.

## 23.3 Visual quality standards

Avoid:

- Featureless particle circles.
- Invisible projectiles.
- Untextured cubes posing as final art.
- Detached model segments.
- Flat models that never rotate with flight.
- Excessive glowing trails.
- Particle spam hiding targets.
- Randomly mixing unrelated vanilla sounds.
- Every spell sharing the exact same purple effect.
- Camera effects that prevent players from controlling combat.
- Giant unreadable HUD decorations.
- Black-screen fog.

Prefer:

- Controlled motion.
- Clear windups.
- Visually distinct attack types.
- Strong short impact events.
- Detailed but readable textures.
- Dedicated sound layers.
- Proper animation timing.
- Visual density concentrated near the important action.

## 23.4 Boss animations

Inventory all source animations, including:

- Running.
- Melee swipe.
- Chest strike.
- Ground slam.
- Phase transition.
- Hurt.
- Death.
- Kagune emergence.
- Kagune grab.
- Kagune hold.
- Kagune throw.
- Kagune retract.
- Kagune shield channel.

Check actual server/client playback.

Where missing, create new authored animations, particularly:

- Boss rapid melee combinations.
- Heavy close-range repel.
- Cocoon collapse.
- Falling/landing.
- Three-row Kagune ring emergence/retraction.
- Directed Kagune stab.
- Echo replica delayed attack.
- Echo replica explosion/death.
- Final-fury locomotion.
- Final boss death.

Animations must correspond to actual server hit frames.

## 23.5 Audio direction

Every phase needs its own musical identity.

Avoid restarting an entire phase track for every ability.

Required audio categories:

- Phase transitions.
- Combat ambient loop.
- Melee windup/contact.
- Projectile launch/travel/impact.
- Large hazard warning.
- Major eruption/explosion.
- Portal collapse.
- Rift Ram charge/stomp.
- Kagune emergence/grab/strike/death.
- Echo summon/replicated hit/explosion.
- Cocoon break/fall.
- Boss death and victory.

Layer sounds intentionally, with distance attenuation and bounded simultaneous voice counts.

Ensure music stops correctly on disconnect, defeat, abandonment, phase transition, and restart.

## 23.6 Performance budgets

Design for up to 20 players.

The worst-case scene may include:

- Six to eight large Kagune.
- Up to 30 small Kagune.
- Multiple transient spell-Kagune.
- Up to five Echo decoys.
- Ranged projectiles.
- Volumetric fog.
- Animated arenas.
- Large boss geometry.

Do not render every effect at full detail for every viewer regardless of distance.

Use:

- Client-side instancing or efficient batched rendering where available.
- Frustum and distance culling.
- LOD.
- Per-viewer particle budgets.
- Bounded temporary display entities.
- Bounded active projectile groups.
- Reused model assets.
- Shared immutable geometry.
- Low-overhead state updates.

Avoid O(players × tentacles × particles × ticks) unbounded behavior.

Measure actual TPS/MSPT and client FPS.

Aim for a healthy stable 20 TPS under expected player counts; report measured MSPT rather than assuming it.

---

# PART VII — EVENT PROGRESSION AND GLOBAL INTEGRITY

## 24.1 Wave transitions

Preserve the seven-wave event lifecycle.

After Waves 1–6:

- Require the correct participants on the runes.
- Maintain the 10-second transition condition.
- Prevent double starts and invalid player admission.
- Correctly clear the previous wave's owned entities.

After Wave 7:

- Fully heal eligible players.
- Restore 30% of maximum durability to damaged eligible equipment, bounded by its maximum durability.
- Start one unified 40-second transition before the boss.
- Avoid duplicate healing or repeated durability restoration.
- Do not start the boss before the scheduled transition is legitimately complete.

## 24.2 Recovery and persistence

Every combat system must handle:

- Player death.
- Player disconnect.
- Player rejoin.
- Last player leaving.
- Event abandonment.
- Event restart.
- Server restart.
- Plugin reload/disable.
- Entity deletion.
- Chunk unload/reload.
- Interrupted animation.
- Stale scheduled callbacks.
- Generation mismatch.
- Double-start attempts.
- Duplicate completion callbacks.
- Accidental recovery of dead enemies.
- Missing client binding.
- Invalid or stale boss health.

A restarted server must never leave:

- Permanent modified arena blocks.
- Orphan boss clones.
- Unowned large or small Kagune.
- Infinite Wither effects.
- Stuck players in an old grab.
- Running music from a finished phase.
- Projectile reservation leaks.
- Duplicate charges.
- A permanently invulnerable boss.

Use event generation ownership and durable checkpoints consistently.

## 24.3 Resource-pack and client release correctness

Previous CI issues involved mismatched client JAR hashes and pinned artifact manifests.

The final release candidate must contain:

- Correct server plugin JAR.
- Correct Fabric client JAR.
- Correct resource pack.
- Correct source/manifest relationship.
- Reproducible build metadata.
- Matching artifact SHA-256 values.
- No stale client duplicated in another build tree.
- No missing model or texture references.

Verify actual contents of built JARs and resource-pack ZIPs, not merely source filenames.

Do not silently copy an outdated prebuilt JAR over a newly compiled artifact.

---

# PART VIII — REQUIRED TEST STRATEGY

## 25.0 Test-driven progression

For every bug:

1. Reproduce the original failure.
2. Record the actual cause.
3. Add a failing regression test when automatable.
4. Fix the smallest responsible subsystem.
5. Run the focused test.
6. Run adjacent integration tests.
7. Build.
8. Test in a running Paper server.
9. Verify with a real Minecraft client when visuals are involved.
10. Record results and evidence.

Do not replace the original bug with a different hidden workaround.

## 25.1 Relevant existing test runners

Inspect and reuse actual test commands, including:

`tests/RunEndRiftEventChecks.ps1`

`tests/RunEndRiftAiPhasesLive.ps1`

`tests/RunEndRiftBossDamageLive.ps1`

`tests/RunEndRiftBossRealHealthLive.ps1`

`tests/RunEndRiftBossHitboxLive.ps1`

`tests/RunEndRiftBossShieldLive.ps1`

`tests/RunEndRiftBossVisualLive.ps1`

`tests/RunEndRiftBossMultiPlayerDamageLive.ps1`

`tests/RunEndRiftSpellMatrixLive.ps1`

`tests/RunEndRiftTentacleLive.ps1`

`tests/RunEndRiftWave6RiftRamLive.ps1`

`tests/RunEndRiftWave6Wave7BoundariesLive.ps1`

`tests/RunEndRiftObeliskLive.ps1`

`tests/RunEndRiftRecoverySmoke.ps1`

`tests/RunEndRiftOfficialTwoPlayerLive.ps1`

`tests/RunEndRiftOfficialFivePlayerLive.ps1`

`tests/RunEndRiftOfficialTenPlayerLive.ps1`

`tests/RunEndRiftPerformanceLive.ps1`

`scripts/minecraft/RunJavaPluginCi.ps1`

Run appropriate pytest, Java, Gradle, Node.js, and PowerShell checks based on changed components.

Do not assume all scripts can run in a particular shell without verifying their prerequisites.

## 25.2 New unit tests

Add focused tests for:

**Wave 1**

- One charge per carrier.
- No early charge drops.
- No duplicate inventory custody.
- All cursor/drag/offhand/container transitions.
- Exact-once delivery.
- Death and reconnect recovery.

**Wave 2**

- Server/client phantom layout compatibility.
- 70-target and safe-fallback behavior.
- Group transitions.
- Target rotation.
- Materialization.
- No stale activated phantom state.
- Music lifecycle.

**Wave 3**

- Portal spawns.
- Elite-from-second-portal rule.
- Capture and close.
- One-shot collapse/explosion.
- Safe cleanup.

**Wave 4**

- Projectile rates for 2/5/10/11/20 players.
- Global firing budget.
- Obelisk knockback.
- Destruction.
- Journal recovery.

**Wave 5**

- Uniform fog state.
- Safe-zone ownership.
- Enemy exclusion.
- Wither II/III rules.
- Slowness.
- Guide fade timing.
- Reachability.
- Damage cleanup.
- All cycles.

**Wave 6**

- Four single-hit pillars.
- Final three-hit pillar.
- Seven total accepted impacts.
- Correct armor progression.
- No double impact.
- Elite skeleton on top.
- Elite skeleton fall after collapse.
- Accurate custom armor bar.
- Endermite models/spawns.
- Stomp timing.
- Charge direction lock.

**Wave 7**

- Two- and three-marksman counts.
- Full-squad completion.
- Per-miniboss real AI.
- Distinct model bindings.
- No stale physical/miniboss actors.
- Room/admission/return logic.

**Boss AI**

- Movement during mobile casts.
- Proper freezing during stationary casts.
- Reliable AI restoration.
- Close-range attack selection.
- Chase when target flees.
- Surround punishment.
- Spread/cluster decision behavior.
- No permanent idle state after spell failure.
- Correct phase-specific ability eligibility.
- Combo cancellation and contact frames.

**Boss damage**

- Real accepted HP loss.
- Shielded hit rejection.
- Red-hit event only on accepted hit.
- Correct composite hitbox routing.
- Three-times final-fury damage.
- Health bar consistency.

**Boss phases**

- Real fracture block mutation/restoration.
- Geyser damage and duration.
- Arena inferno lifecycle.
- Phase 5 delayed echo attacks.
- Echo explosion.
- Persistent clones: 50 HP, max 5, reduced damage, 150-second cooldown.
- Clone death does not kill boss.
- Clone cleanup on phase transition.
- Permanent large guardian deaths.
- Six-to-eight guardians.
- Small Kagune spawning/cap of 30.
- Non-destructible spell-Kagune lifecycle.
- Three-row random gap layout.
- Valid evasive movement between gaps.
- No stacked damage from neighboring rows.
- Cocoon fall occurs exactly once.
- No post-fall cocoon recovery.
- Regeneration 0.2%/second, 5% cap.
- Final boss death exact-once behavior.

## 25.3 Live server tests

Start a real local Paper 26.3 server against an isolated copy of the required environment.

Do not test destructively on the live production server.

Use RCON and existing probes where appropriate.

Verify:

- Every wave starts.
- Every wave completes.
- Wave 7 actually returns to the correct flow.
- The boss starts at the correct time.
- All six phases activate at correct HP thresholds.
- Every named spell performs real work.
- Real arrows/projectiles spawn, travel, and hit.
- All temporary entities clean up.
- Boss physical HP updates correctly.
- Block mutations restore.
- Recovery after restart succeeds.

Run for:

**2, 5, 10, 11, and 20 players.**

Test the worst-case 20-player scene with active fog, multiple projectiles, Echo clones, large Kagune, and small Kagune separately before combining full loads.

Also run a one-player diagnostic scene where the test harness supports it, without changing official admission rules.

## 25.4 Interrupted-state tests

For each major ability, interrupt at multiple lifecycle points:

- Before telegraph.
- During telegraph.
- After target lock.
- During execution.
- Immediately after first hit.
- During recovery.
- Before visual cleanup.

Then simulate:

- Player death.
- Target disconnect.
- Boss death.
- Event abandonment.
- Event restart.
- Plugin shutdown.
- Server restart.
- Entity disappearance.

Verify no stuck task, wrong state, permanent arena mutation, or phantom actor remains.

## 25.5 Git and CI

At each milestone:

- Review code and tests.
- Run `git diff --check`.
- Run focused checks.
- Build all affected modules.
- Commit the milestone with a specific meaningful message.

At final integration:

- Run complete supported test suites.
- Rebuild server plugin.
- Rebuild Fabric client.
- Validate resource pack.
- Validate artifact pins and SHA-256.
- Push commits to the correct GitHub branch.
- Check actual GitHub Actions results.
- Investigate any failures rather than explaining them away.
- Fix and rerun until required CI jobs pass.
- Request independent code review or CodeRabbit review if available.
- Inspect critical cross-module assumptions that automated review may miss.

Do not claim CI passed until actual run IDs and successful conclusions have been observed.

---

# PART IX — MANDATORY VISUAL VERIFICATION

## 26.1 General rule

**Compilation and automated tests are NOT sufficient evidence for graphics, animation, models, or combat presentation.**

Use a real Minecraft 26.3 client with the actual CopiMineClient and resource pack.

If the coding agent can access the desktop/client, it must perform visual checks.

If desktop control is unavailable, provide a detailed manual acceptance procedure and explicitly mark visual acceptance incomplete.

Do not fabricate screenshots or claim to have seen scenes that were not opened.

## 26.2 Model review shots

For every important new or revised model:

- Front.
- Back.
- Left.
- Right.
- Top.
- Three-quarter angle.
- Close-up of the most complex section.
- Idle pose.
- Movement pose.
- Attack pose.
- Hurt pose.
- Death or retract pose.

Compare against original model archives, texture atlases, and screenshots.

Check:

- Bone/pivot alignment.
- UVs.
- Missing faces.
- Duplicated body sections.
- Texture bleeding.
- Geometry interpolation.
- Actual rotation in world space.
- Collision alignment.

## 26.3 Spell review shots

For every important attack:

1. Before cast.
2. Telegraph start.
3. Telegraph peak.
4. Projectile/geometry launch.
5. Mid-flight/mid-animation.
6. Contact or eruption.
7. Successful hit.
8. Miss.
9. Recovery.
10. Final cleanup.

Capture both wide arena views and close-up views.

For boss attacks, include frontal, side, and rear views when relevant.

## 26.4 Required scene captures

**Wave 1**

Elite with exactly one overhead charge; death; pickup; inventory movements; charging ring; Core delivery.

**Wave 2**

Spectral observer field; marked player; materialization; group transitions; elite group; music behavior.

**Wave 3**

Portal opening; enemy spawn; capture; animated closure; explosion.

**Wave 4**

Obelisk firing; close knockback; damage stages; destruction; small-party and large-party firing density.

**Wave 5**

Thick fog; visible near-floor ground; safe-zone cues; their 3–5 second fade; Wither/Slowness; refuge without mobs.

**Wave 6**

Rift Ram head/armor; red eyes; nostril smoke; charge; stomp; four minor pillars; final enormous pillar; skeleton on top; all three final collisions; armor bar; Endermites.

**Wave 7**

Every miniboss from multiple angles; running movement; each special ability; marksman crossfire; death; correct return.

**Boss**

- Full model and customized health bar.
- Red flash on accepted damage.
- Blocked-hit shield effect.
- Normal melee sequence.
- Chest strike.
- 3D Rift projectile.
- Phase 3 real fracture.
- Phase 3 huge fiery geyser.
- Phase 4 full burning arena.
- Phase 5 delayed strike Echo.
- Echo delayed explosion.
- All five possible 50-HP decoys in a large-party test.
- Phase 6 cocoon and six-to-eight guardians.
- Small-Kagune pressure.
- Grab.
- Directed stab.
- Group ground eruption.
- Three-row expanding Kagune ring from above.
- Random two-block gaps from player perspective.
- Heavy repel into ring combination.
- Destruction of the last large guardian.
- Boss falling.
- Final melee-fury attacks.
- Boss death animation and end-of-event scene.

## 26.5 Visual acceptance

Every visual feature should have:

- Actual captured evidence.
- Build/version hashes.
- Scene and phase.
- Player count.
- Camera angle.
- Result.
- Any identified problem.
- A before/after comparison where available.

Do not mark visual verification PASS if only static asset renders or unit tests were inspected.

---

# PART X — FINAL ACCEPTANCE CRITERIA

The complete assignment is acceptable only when:

1. Wave 1 charge ownership is correct, with no duplicate drops or inventory duplication.
2. Wave 2 spectral observers visibly appear, materialize, and support all three hunt groups.
3. Wave 3 portals spawn enemies and perform convincing animated closure/explosion.
4. Wave 4 obelisks fire correctly, use roster-scaled pressure, knock players back, and visibly break.
5. Wave 5 shows uniform dense dark brown/violet-black fog without becoming a pure black screen.
6. Wave 5 refuge guidance remains luminous for the required 3–5 seconds and refuges remain free of mobs.
7. Wave 5 Wither/Slowness is damaging but physically survivable.
8. Wave 6 has four minor pillars, one huge three-impact final pillar, and an elite skeleton at its top.
9. Wave 6 Rift Ram, armor, stomp, sounds, Endermite models, and boss bar are finished.
10. Wave 7 minibosses have correct geometry, animations, movement, and real attacks.
11. Wave 7 marksmen appear as two or three real shooters.
12. Skeleton, elite, and boss projectiles are physically real and visibly modeled.
13. Rift Guardian moves and attacks aggressively in every ordinary phase.
14. Boss melee and spells do not constantly freeze its AI.
15. Every boss phase has a distinct combat identity.
16. Phase 3 uses reversible real blocks and huge fiery eruptions.
17. Phase 4 transforms the arena into a convincing inferno.
18. Phase 5 has delayed attack Echoes and actual 50-HP decoys, max five, with rare summon cooldown.
19. Phase 6 has permanent large Kagune destruction and active small-Kagune combat.
20. Phase 6 includes grapples, stabs, group eruptions, and three-row moving Kagune rings with valid random gaps.
21. Boss regeneration is reduced to 0.2%/second and 5% total while protected.
22. The cocoon collapses and the boss physically falls after the last guardian dies.
23. Final fury has real aggressive close combat and persistent 3× incoming damage.
24. Damage to the boss correctly reduces HP and displays a red hurt flash.
25. Boss death has a full cinematic, correct music, and no unintended Ender Pearl drop.
26. Every encounter is fully cleaned up after completion or interruption.
27. The event works with 2/5/10/11/20 players.
28. All relevant tests and GitHub CI jobs pass.
29. Actual client-side visuals are verified, or explicitly documented as not yet verified.
30. No regressions to existing arena, rune progression, database, rewards, or live player data.

---

# PART XI — REQUIRED FINAL REPORT

Return an evidence-based report containing:

## A. Repository and commits

- Final branch.
- Starting and ending commit.
- All new commits.
- GitHub URLs.
- Push status.
- GitHub Actions run IDs and outcomes.

## B. Changes per wave

For Waves 1–7, provide:

- Original bug.
- Root cause.
- Files changed.
- Behavior implemented.
- Tests added.
- Focused test results.
- Live server test results.
- Minecraft visual verification status.
- Remaining known risks.

## C. Changes per boss phase

For every one of the six phases:

- Combat identity.
- Movement/AI changes.
- Melee attacks.
- Spells.
- New models/textures/animations.
- Actual damage behavior.
- Visual verification evidence.
- Remaining gaps.

## D. Global systems

Report separately on:

- Projectile admission.
- Client/server synchronization.
- Boss damage and composite hitboxes.
- Red hurt feedback.
- Boss AI.
- Kagune lifecycle.
- Arena block mutation and recovery.
- Music.
- Performance.
- Resource-pack and client JAR hashes.

## E. Test evidence

List exact commands, exit codes, relevant test counts, artifact locations, log files, and observed results.

Clearly distinguish:

- Implemented in code.
- Confirmed by automated tests.
- Confirmed on a real Paper server.
- Confirmed in the Minecraft client.
- Not yet verified.

## F. Outstanding work

If any acceptance item is incomplete, say so explicitly.

Do not replace proof with phrases like "looks fine", "should work", or "fully fixed".

Do not conceal known failures, visual problems, skipped tests, or missing gameplay verification.

## G. Production safety

Prepare an independently verifiable release candidate with manifests and hashes.

**Do not deploy to the live production server, overwrite the user's installed Minecraft client, reset the live arena, or replace live world/database files without separate explicit authorization.**

Preserve backups and prepare a reversible deployment/rollback procedure.

---

# FINAL INSTRUCTION

This task is about finishing a coherent, high-quality Minecraft encounter, not maximizing the number of individual features.

The user expects aggressive, intelligent combat; distinctive and properly animated 3D attacks; strong atmospheric visual effects; correct physics and damage; and reliable progression.

A strong implementation makes the player understand an attack visually, react to it, survive through skill, and clearly see its consequences.

Do not stop at successful compilation.

Proceed milestone by milestone, test the real behavior, review the actual assets, and deliver an evidence-backed result.