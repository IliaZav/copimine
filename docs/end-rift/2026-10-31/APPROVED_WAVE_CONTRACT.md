# Approved End Rift Wave Contract — 2026-10-31

Status: **authoritative for implementation on `codex/copimine-integration-20261030`.**

This document is the entry point for the 31 October 2026 End Rift assignment. It
records which rules are user requirements, which are configurable engineering
defaults, and which historical rules are now **superseded**. Historical
documents stay in the repository for traceability and are not deleted.

## 1. Source authority order

Applied top-down; a lower entry never overrides a higher one.

1. The three 30–31 October 2026 prompts (master contract + Waves 1–5 + Waves 6–7).
2. `D:\Downloads\COPIMINE_END_RIFT_WAVES_CANONICAL.md`
   (`CM-END-RIFT-WAVES-CANONICAL`, revision 2026-10-27.1,
   SHA-256 `6E1245B44A449A0EF1BAB9193606398660C68F6BB2DE7D7BB6082CC8CDE50CAF`,
   47126 bytes).
3. `D:\Downloads\COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md`
   (`CM-END-RIFT-RIFT-GUARDIAN-CANONICAL`, revision 2026-10-27.1,
   SHA-256 `0C781E42E274A3F27762E0B568CC1D8FFD7DFD8623E5A0F1DEB65EA73A94AE50`,
   51711 bytes).
4. Compatible 25 October 2026 prompts, the 22 October master goal, then the
   per-wave documents under `docs/end-rift/`.
5. Old v5 ritual/End proposals. **Never** import their mechanics.

Both canonical files are historical baselines. Where they conflict with the
three prompts they lose. Both hashes must be re-recorded after any compaction
and before each task or subagent dispatch; see `AGENTS.md`.

## 2. User requirements (not negotiable, not configurable)

### 2.1 World, arena and event scope

- The **entire** event, Rift Guardian included, happens in the configured
  **Overworld** arena. Official victory opens access to the End.
- No hardcoded world name, no End-dimension requirement, no historical arena
  coordinates.
- The arena may move to different coordinates and to a **second configured
  Overworld world**. Relocation requires a clean inactive state; an active
  attempt freezes its arena context.
- Never silently fall back to `the_end` when Core is unavailable.

### 2.2 Wave identity

| Wave | Win condition |
|---|---|
| 1 Rift Carriers | 3 distinct genuine elite-borne charges delivered to Core |
| 2 Rift Gaze | exactly 3 subwaves cleared; all players marked for the whole wave |
| 3 Rift Gates | 3 sequential portals captured/closed |
| 4 Obelisk Assault | 3 valid reflected fireballs per roster-scaled obelisk (≤6) |
| 5 Black Fog | exactly 5 fog cycles, exact safe-volume blackout, post-fog reinforcements |
| 6 Ritual Sphere | 5 sequential mage encounters cleared, prisoner released |
| 7 Reality Split | sequential trials, every admitted player fights their own Echo |
| Boss | Rift Guardian |

### 2.3 Cross-wave invariants

- Transition after Waves 1–6: exactly **10 continuous seconds** of valid rune
  occupancy.
- After Wave 7: full eligible-player heal, repair **30 % of each** damaged
  item's own maximum durability, then exactly **one 40-second** boss
  transition, guarded by a persisted receipt so a restart cannot repair or
  summon twice.
- KeepInventory stays **personal to registered official participants** during
  waves and boss. Do not change the world gamerule. Preserve item metadata and
  prevent duplicate drops. XP policy unchanged.
- One individual may return while another admitted teammate still lives. A
  true full-party death ends the attempt; the Wave-7-only all-dead grace
  exception is removed.
- Party survival is evaluated across **both** Echo realms. Spectators and stale
  disconnected actors are not living participants.
- A valid bed return from another world routes through the configured return
  entry. Relogging never refreshes a death or return deadline.

### 2.4 Shared elite contract

- Elite maximum HP = **exactly 3 ×** the corresponding ordinary role's maximum
  HP **under the same frozen party scaling**. Never multiply an already-boosted
  value, never re-apply on restore, and no hidden cap may silently break it.
- Visual size ≈1.5–2× ordinary with matching gameplay collision.
- Skeleton: three successive fans of six arrows (18 per burst); burst cadence
  2–3× the ordinary skeleton baseline; arrows pass allied mobs without stopping
  or detonating; terrain still blocks them; bounded knockback and explosive
  variants; at most one accepted combined direct/blast hit per target per fan.
- Zombie/Giant: strong tank, immune to knockback and slowing while its owned
  elite role is active; accepted melee applies Slowness II; nearby ordinary
  combat allies gain Strength I + Speed I + Resistance I; **no** regeneration,
  self-buff, stacking, mage/boss buff or recursive summons; bounded ordinary
  zombie summoning.
- Enderman: strong caster, damaging dragon-breath-like clouds, credible close
  retaliation, rare safe repositioning; no idle casting, no permanent teleport
  evasion, no invisible hits.
- Spider: web projectiles, short stuns/slows, Poison and Nausea III, shared
  per-target protection against permanent control chains; never root a player
  into an unavoidable major sphere impact.

### 2.5 Wave-specific supersessions (mandatory)

- **Wave 1:** charge carriers **are elites**.
- **Wave 2:** all players marked throughout the wave; exactly three subwaves;
  no rotating 45-second target; no minimum mark-period gate.
- **Wave 3:** continuous replenishment while the current portal is open, with a
  bounded simultaneous population.
- **Wave 5:** five fog cycles with exact safe-volume blackout and post-fog
  reinforcements.
- **Wave 6:** **sequential** mage encounters whose guards are ordinary combat
  elites — not a tiny-post formation AI.
- **Wave 7:** **sequential** trials, not four concurrent rooms.
- **Echo:** every admitted player fights their own copy simultaneously; large
  groups split between ordinary presentation and limbo. No queue, no waiting
  minigame, no phantom activity.

### 2.6 Wave 6 prisoner (P0)

- Validate signed readiness before activation; immediately capture one eligible
  player.
- Confirm safe teleport **before** committing capture (`teleportRitualPrisoner`
  returns a boolean that the capture path must honour); bounded rollback/retry;
  no half-start.
- Hold the player inside the raised translucent sphere above Core; reconcile
  gravity, velocity, arena height, containment, teleport handlers and Grim; no
  falling/escape/correction loop.
- Preserve camera, F1, F2, F5. Restore controls and safe placement on death,
  quit, bridge loss, abort, restart. Never disable authentication or global
  anti-cheat.
- Four LOCKED Q/W/E/R icons above the hotbar immediately; unique mage deaths
  1/2/3/4 unlock them; the 5th releases the prisoner. HUD packets are not
  rendering proof.
- Deaths 1–4 accumulate Barrage, Gravity Anchor, Soul Brand and Heavenly
  Judgment. The sphere owns casting and origin; surviving mages switching to
  `AWAKENED_ATTACKING` must not silence it. One major cast at a time through
  cleanup.
- Guards actively fight free players with melee/ranged/caster/support roles with
  per-mage guard and shield identity; summons never refill a guard slot.

### 2.7 Cross-cutting defects

1. Active client TAB: neutral dark top-centred panel; clip/truncate the
   **actual styled name**, not only its measured width; preserve ping, ranks and
   glyph styling.
2. Silence routine ClearLag **and** EntityClearer announcements at their
   configuration sources, including installed staging configs. Keep cleanup and
   console/admin diagnostics.
3. Preserve event actors, charges and required projectiles from unintended
   cleaner removal.
4. Investigate Wave 5 texture loss independently of fog density: resource
   selection, resource reload, atlas/model references, signed entity bindings,
   shader transitions.
5. Never switch or disable the user's shaderpack to make event graphics work.
6. Trace data flow source → build → packaged artifact → installed file →
   actually loaded process.

### 2.8 Acceptance

Actual native visual verification is required for visual acceptance. Historical
"automated checks only" wording cannot make a visual requirement pass. An
unavailable native check is **BLOCKED**, never silently skipped as success.

## 3. Configurable engineering defaults (tunable, documented, not user rules)

These are tuning values. Changing one is a config change, not a contract change.

- Wave 5 combat-segment seconds `40 / 50 / 60` (baseline), safe-zone warning
  8 s, fog duration 3 s.
- Wave 2 subwave cadence: next subwave starts 3 s after the previous finite
  roster is cleared.
- Pressure band ≈3–5 concurrent ordinary enemies per participant.
- Elite stat multipliers for attack/speed/armor/toughness/knockback, the
  bounded `MAX_ATTACK_DAMAGE`, `MAX_MOVEMENT_SPEED`, `MAX_ARMOR`,
  `MAX_ARMOR_TOUGHNESS`, `MAX_KNOCKBACK_RESISTANCE`.
- Boss focus window 10–12 s, Edge Volley cooldown 75 s (60–90 s band),
  Inferno 400 ticks, guardian respawn 90 active-combat seconds.
- Prisoner support tuning: Heal ~6 HP / ~20 s, Battle Surge ~7–8 s / ~28–30 s,
  Guardian Link ~6 s / ~30 s, Conversion ~20–25 s / ~50–60 s.
- Rune hold 10 s and boss handoff 40 s are **user requirements**, not defaults.
- Wave 4 obelisk count is roster-scaled to a maximum of 6; three objective HP
  each is a user requirement.

## 4. Superseded rules — recorded explicitly

| Superseded rule | Where it lived | Replaced by |
|---|---|---|
| Event staged in the End dimension; Boss in the End | historical arena docs, `portalWorld = CopiMine_the_end` assumptions | whole event in configured Overworld arena; victory unlocks the End |
| Historical arena coordinates `(8,68,-39)`, `(111,99,-312)`, `(0,100,0)` | dated configs | configured `arena.world` + validated geometry, frozen per attempt |
| Wave 1 carriers are ordinary escorts | canonical Wave 1 | carriers **are elites** |
| Wave 2: one rotating marked player for 45 s, ≥2 periods, 3×20 s hunt | `WaveTwoHuntPolicy`, `WaveTwoStagePolicy` | all players marked all wave, exactly 3 subwaves, no rotation |
| Wave 3: fixed portal layer with no replenishment | canonical Wave 3 | continuous replenishment while the current portal is open, bounded population |
| Wave 5: exactly three fog cycles | `BlackFogTimingPolicy.CYCLE_COUNT = 3`, `BlackFogRecoverySnapshot` | exactly **five** cycles + post-fog reinforcements |
| Wave 5: blindness/vignette/4-block visibility fog | `EndEventBlackFogManager` `8-4*intensity` | whole arena opaque black except clear safe volumes |
| Wave 6: five concurrent casters with tiny-post guard AI | current ritual runtime | sequential mage encounters with ordinary combat-elite guards |
| Wave 6: capture commits before teleport is confirmed | capture path ignoring `teleportRitualPrisoner` result | placement-confirmed commit with bounded rollback |
| Wave 7: four concurrent rooms for every roster | `RealitySplitChamberController` | sequential trials; personal Echo for every admitted player |
| Wave 7: Echo queue / waiting minigame / phantom activity | earlier Echo plans | all copies fight simultaneously; large groups split into ordinary presentation + limbo |
| Wave 7: all-dead grace exception only at Wave 7 | attempt lifecycle | true full-party death ends the attempt at any wave |
| Elite HP = 1.5 × configured health, hidden 256 HP cap | `EliteCombatStatsPolicy` | exactly 3 × ordinary role max HP under frozen party scaling |
| Elite zombie aura grants Regeneration I + Resistance I | canonical shared-enemies section | Strength I + Speed I + Resistance I, no regeneration |
| `GRAVITY_WELL` / `RIFT_CHAINS` mechanics | legacy enum ids | Gravity Anchor + falling anvils (ids kept only as compat aliases) |
| Visual acceptance via automated checks only | historical verification docs | native visual verification required; otherwise BLOCKED |

## 5. Architecture rules

- Do not grow `CopiMineEndEvent.java` (~2.69 MB, ~50 k lines) with a new
  subsystem. Extract only the wave responsibilities being changed and preserve
  existing APIs.
- Use pure Java state/policy classes for decisions; one production runtime
  controller per wave/stage; Paper adapters for entity/damage/teleport/item/
  block work; signed client presentation adapters; existing persistence
  extended through versioned schemas.
- Reuse existing abstractions. The proposed shared units
  `domain/EncounterArenaContext.java`, `domain/CombatScope.java` and
  `runtime/waves/WaveActionOwner.java` are **proposals**, not existing files.
  Almost every other unit named by the prompts already exists under
  `copimine-end-event/src/me/copimine/endevent/`.
- Lifecycle: `prepare -> admit -> warn -> commit -> execute -> hit/miss ->
  recover -> cleanup -> complete`.
- Separate a legitimate defeat from despawn, ClearLag removal, administrative
  cleanup, unload and spawn failure. Never grant progression for an unexplained
  missing actor.
- Every delayed callback and client update is fenced by its scope. Every
  transition, reinforcement pack, unlock and reward carries an idempotency
  receipt.
- Never synchronously write a full event snapshot per NPC attack or sampled
  movement. Capture immutable data on the server thread, persist through
  ordered bounded workers, apply results only after checking the current
  generation. Keep durability at real progression and item-consumption
  boundaries.

## 6. Preserved user work

The tree carried 23 modified and 5 untracked files before this assignment.
They were backed up outside tracked source (see `VERIFICATION.md`). They are not
reset, discarded or overwritten. `.serena/` and `CopiMineClient/bin/` are never
staged blindly. One integration writer owns `CopiMineEndEvent.java`.
