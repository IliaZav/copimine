# CopiMine End Rift — V7 acceptance ledger

Source of truth: `D:\Downloads\CopiMine_End_Rift_Master_V7_2026-11-26.md`
(SHA-256 `1c9c1ae7c9c65575bffb1bf98d1e7e09de6ddc507c0cc332500e004224a96172`,
780785 bytes) and `D:\Downloads\CopiMine_V7_Full_Repair_Echo_AI_Codex_2026-11-26.md`
(SHA-256 `91018fc64f8d345848b0a6eb24cf54d956cee34f6090890989708571fc365ef8`,
60107 bytes). Both canonical V5.1 files were re-read and re-hashed this session:

| File | Revision | Bytes | SHA-256 |
|---|---|---|---|
| `COPIMINE_END_RIFT_WAVES_CANONICAL.md` | `2026-10-27.1` | 47126 | `6E1245B44A449A0EF1BAB9193606398660C68F6BB2DE7D7BB6082CC8CDE50CAF` |
| `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` | `2026-10-27.1` | 51711 | `0C781E42E274A3F27762E0B568CC1D8FFD7DFD8623E5A0F1DEB65EA73A94AE50` |

Where V7, the 2026-10-31 assignment and the canonical files disagree, V7 §1 wins.
Status vocabulary (V7 §18.5): `NOT_IMPLEMENTED`, `IMPLEMENTED_UNVERIFIED`,
`UNIT_PASS`, `NATIVE_PASS`, `VISUAL_PASS`, `BLOCKED`. A native/visual item is
never a pass without real Minecraft 26.3 evidence.

## V7-01 … V7-28 minimum acceptance matrix

| ID | Requirement | Implementation / owner code | Automated evidence | Status |
|---|---|---|---|---|
| V7-01 | Relocated/rotated underground site; published plan, ceiling intact | `LastSealArenaReveal.preview` + `domain/LastSealStructuralGeometryPolicy.cellKindApproved` | `test_last_seal_structural_geometry.py` (combined corridor+wall panel approved, interior wall refused, out-of-mask refused) | IMPLEMENTED_UNVERIFIED (A-01 fixed 2026-11-26; native reveal BLOCKED: no approved site plan + graphical client) |
| V7-02 | Two floors, negative coords, chunk borders; no adjacent-floor/room hits | `LastSealArenaReveal.restore` + `LastSealStructuralGeometryPolicy.recoveryEvacuation` | `test_last_seal_structural_geometry.py::test_recovery_never_teleports_a_surface_or_stacked_floor_body` | IMPLEMENTED_UNVERIFIED (A-02 fixed 2026-11-26; native stacked-floor fixture BLOCKED) |
| V7-03 | Safe zone untouched in combat/fog/phase 6 | protected-cell predicate + `Plan.protectedSafeZone` | `test_end_rift_last_seal_arena_reveal.py`, `test_end_rift_last_seal_ring_runtime.py` | UNIT_PASS (native BLOCKED) |
| V7-04 | `/dun`, GUI and wand | `copimine-dungeon` controllers/commands | `test_dungeon_command.py`, `test_dungeon_domain.py`, `test_dungeon_controllers.py`, `test_dungeon_store.py` | UNIT_PASS (0 published rooms live; native editor BLOCKED) |
| V7-05 | Dungeon + six physical gates; real collisions, no trap framework | `copimine-dungeon` runtime | dungeon contract suites | UNIT_PASS (native BLOCKED) |
| V7-06 | Wave 1 real charges, 3 deliveries, personal beam, 10 s runes | EndEvent W1 runtime | W1 runtime probes | SRC+TEST (native BLOCKED) |
| V7-07 | Wave 2 intro, 20 s mark, three finite groups, observers not Phantoms | EndEvent W2 runtime | W2 probes | SRC+TEST (native BLOCKED) |
| V7-08 | Waves 3→4: 3 portals, capture, Giant, honest close, 3-stage obelisks | EndEvent W3/W4 runtime | W3/W4 probes | SRC+TEST (native BLOCKED) |
| V7-09 | Wave 5: 5–10 cycles, Wither II/III, real shelters, no mobs inside | EndEvent W5 runtime | W5 probes | SRC+TEST (native BLOCKED) |
| V7-10 | Wave 6: one Rift Ram, 7 contacts, 5 swarms, moving quakes, tower fall | EndEvent W6 runtime | W6 probes | SRC+TEST (native BLOCKED) |
| V7-11 | Sky Hail: two charge stages, real arrow rain, ceiling-bounded, cancel | `beginMarksmanSkyHail` / `releaseMarksmanHail` | `test_end_rift_sky_hail_and_guardian_permanence.py` | SRC+TEST (native BLOCKED) |
| V7-12 | Wave 7: 4 sequential trials on one arena, 2/3 marksmen, no extra elites | Wave 7 runtime | W7 probes | SRC+TEST (native BLOCKED) |
| V7-13 | Warden: real base, 5×4, 20t/60t, R6 through-wall, resonance counterplay | W5.1 Warden runtime | `test_warden_v5_runtime.py` | SRC+TEST (native BLOCKED) |
| V7-14 | Archmage: frequent bounded Vexes, 3-block fang line, crown gap, staff | Archmage runtime | `test_wave7_archmage_*` | SRC+TEST (native BLOCKED; low-HP cadence pending owner approval) |
| V7-15 | Distinct skeleton models/arrows, textured bars, no Echo bar | active 26.3 client | `SpellVisualGeometryTest`, `GuardianSpellMeshTest` | IMPLEMENTED_UNVERIFIED (Wave7 V4 ZIP content not extractable; native BLOCKED) |
| V7-16 | Echo AI: 4 distinct + hybrid profiles, independent tactics, finite resources | `runtime/wave7/EchoDuelCombatRuntimeAdapter`, `domain/wave7/EchoBehaviorStrategy`, `EchoDuelCombatPolicy` | `EchoDuelCombatRuntimeAdapterTest` (91 assertions), `EchoWoundedRetreatTest` (actor-phase lockstep divergence), `EchoDuelCapabilityPolicyTest` | IMPLEMENTED_UNVERIFIED (E-02 navigation dead zones + E-03 actor-local phase fixed 2026-11-26; E-04/E-05 open; native BLOCKED) |
| V7-17 | Two Echo modes: Wave 7 duel + rare Phase 5 summon, no teleport-to-room | Wave 7 `EchoDuelCombatRuntimeAdapter`; Phase 5 `spawnEchoClone` | — | NOT_IMPLEMENTED (Phase 5 still uses simplified `spawnEchoClone`; E-01 call-graph replacement open) |
| V7-18 | Guardian phases 1–5: real HP/damage/hurt, grounded, 4 physical attacks | EndEvent boss runtime | boss phase probes | SRC+TEST (native BLOCKED) |
| V7-19 | Phases 3–4: geysers + temporary blocks, rare ring/forge, no spam | EndEvent hazard runtime | wave timing probes | IMPLEMENTED_UNVERIFIED (overlapping hazard batches remain open per V6 ledger) |
| V7-20 | Phase 5: transparent coordinated decoys, up to 5, independent death, rare Echo | Phase 5 Rage runtime | `test_event_combat_profile.py` | IMPLEMENTED_UNVERIFIED (coordinated roles / rare Echo pending) |
| V7-21 | Cooperative actions: enough members, prepare, loss, miss, cancel | cooperative admission | `test_end_rift_sky_hail_and_guardian_permanence.py` | IMPLEMENTED_UNVERIFIED (candidate skills not ratified) |
| V7-22 | Transition 5→6: corridor + exact panels, safe zone/roof/portal intact | `LastSealArenaReveal`, `ArenaMergeCinematicPolicy` | `test_end_rift_arena_merge_cinematic.py` | SRC+TEST (native BLOCKED) |
| V7-23 | Last Seal: 6–8 defenders, shield per living, ring gap, real grab/throw | Kagune runtime | `test_tentacle_guardian_role_separation.py`, `test_end_rift_last_seal_ring_runtime.py` | SRC+TEST (native BLOCKED) |
| V7-24 | Victory/defeat: square restored, no stuck damage/tasks, no rewards on defeat | `EndRiftVictoryCoordinatorPolicy` | victory probes | SRC+TEST (native BLOCKED) |
| V7-25 | Post-boss: legal hit, permanent flag, names, rewards, WorldCore End access | `CoreMemorial*`, WorldCore integration | memorial/reward probes | SRC+TEST (native BLOCKED) |
| V7-26 | Restart/races at phase/gate/reward/WorldCore boundaries | journal + receipts | recovery probes | IMPLEMENTED_UNVERIFIED (cold-restart matrix partial) |
| V7-27 | Load: 2/5/10/11/20 + dungeon 32+, MSPT/client/packets, no leaks | — | — | NOT_MEASURED (native profiling BLOCKED) |
| V7-28 | Assets/release: active sources, real hashes, CI at final SHA, no old sphere/camera/Echo bar | build + CI | validators, `RunEndRiftEventChecks.ps1` | IMPLEMENTED_UNVERIFIED (CI P0 encoding resolved; native visual BLOCKED) |

## Defects fixed this session (2026-11-26)

### E-03 — Shared global clock made every same-profile copy strafe in lockstep (`runtime/wave7/EchoDuelCombatRuntimeAdapter`)
- Root cause: `lateralIntent` derived the strafe phase from `observation.tick() / 12`
  and the profile windows (`inProfileWindow`) from `tick % 100` with no per-actor
  offset. Two copies summoned from the same profile therefore strafed, hopped
  and raised shields on exactly the same ticks, and the owner could time one
  counter around the whole squad.
- Fix: added `actorPhase(duelId, actorUuid)`, a pure 64-bit mix in `[0, 100)`
  derived from persisted identity. `movementIntent`, `lateralIntent`,
  `swapsWeaponThisTick` and every `inProfileWindow` call (shield, jump, mobility)
  now shift by that phase while staying a deterministic function of durable
  state, so replays reproduce every decision. Existing overloads delegate with
  a zero offset; each burst still commits for twelve ticks, so there is no
  per-tick oscillation.
- Regression: `tests/harness/me/copimine/endevent/runtime/wave7/EchoWoundedRetreatTest.java`
  (`actorPhaseIsBoundedAndDeterministic`, `sameProfileCopiesDoNotShareOneGlobalRhythm`).
  Result: `EchoWoundedRetreatTest OK`.

### E-02 — Echo navigation dead zones (`runtime/wave7/EchoDuelCombatRuntimeAdapter`)
- Root cause: `movementIntent` derived `wantsRange` from the profile alone and the
  ranged APPROACH branch also required `distance > RANGED_RANGE_BLOCKS` (30). A
  ranged copy at 20 blocks with a ~5-block preference strafed forever instead of
  closing, and a copy whose quiver was empty kept kiting from a band it could no
  longer fire from.
- Fix: `tick` now passes `rangedAvailable = rangedSlot >= 0 && ammoSlot >= 0`; the
  ranged `APPROACH` branch no longer requires the 30-block envelope. A copy with
  no usable ranged weapon/ammo falls back to the melee hold band and closes.
- Regression: `tests/EchoDuelCombatRuntimeAdapterTest.java`
  (`rangedCopiesCloseIntoTheirOwnHoldBand`, `exhaustedQuiverSwitchesRangedCopyToMelee`).
  Result: `EchoDuelCombatRuntimeAdapterTest OK assertions=91`.

### A-01 — Combined corridor + inner-wall manifest rejected (`domain/LastSealStructuralGeometryPolicy`)
- Root cause: the `wall` case required `corridor == null`, so a legitimate plan
  carrying both a concealed corridor and separate approved inner-partition panels
  could never validate; the round arena could never open through its panels.
- Fix: a `wall` cell is approved on the outer-hall boundary ring above the floor
  and no higher than the hall, whether or not a corridor is configured. Interior
  wall and out-of-mask cells are still refused.
- Regression: `tests/test_last_seal_structural_geometry.py::test_cell_geometry_keeps_demolition_inside_the_approved_corridor`.

### A-02 — Recovery could teleport a surface/stacked-floor player (`LastSealArenaReveal.restore`)
- Root cause: the recovery journal stored only horizontal bounds; the broad
  evacuation branch used a horizontal radius test with no vertical check, so a
  player on the surface or a stacked neighbouring floor sharing the arena X/Z was
  teleported into the rescue landing. Legacy journals also reconstructed the
  square with world min/max Y.
- Fix: the journal is now `last-seal-v2` (17/23 fields) and persists the arena
  floor and exact `minY..maxY`. The displacement decision moved to the pure
  `LastSealStructuralGeometryPolicy.recoveryEvacuation`, which always displaces a
  body intersecting a restored cell but runs the broad sweep only for a V2 journal
  and only inside the arena's vertical band. Legacy V1 journals recover safely and
  skip the broad sweep.
- Regression: `tests/test_last_seal_structural_geometry.py::test_recovery_never_teleports_a_surface_or_stacked_floor_body`.

## Blocked (never a pass)

- `NATIVE_BLOCKED`: no graphical Minecraft 26.3 client session in this agent
  environment; the owner playtest is the native/visual acceptance path.
- Underground site plan / production-world mutation: BLOCKED without an approved
  location-bound plan.
- V7-27 load profiling and V7-15 Wave7 V4 asset integration: BLOCKED (no native
  client; ZIP content not extractable in this environment).
