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
| V7-16 | Echo AI: 4 distinct + hybrid profiles, independent tactics, finite resources | `runtime/wave7/EchoDuelCombatRuntimeAdapter`, `domain/wave7/EchoBehaviorStrategy`, `EchoDuelCombatPolicy`, `EventCombatProfileService` | `EchoDuelCombatRuntimeAdapterTest` (110 assertions), `EchoCombatDiagnosticThrottleTest` (15 assertions), `EchoWoundedRetreatTest` (actor-phase lockstep divergence), `EchoBehaviorPersonalisationTest` (115 assertions: personalised heal threshold, axe-vs-sword preference, melee + ranged draw cadence), `EchoBehaviorStrategyTest` (schema-2→3 migration), `EchoDuelCapabilityPolicyTest` | IMPLEMENTED_UNVERIFIED (E-02 navigation dead zones + E-03 actor-local phase + E-04 confidence-blended weapon/cadence/sustain evidence + E-05 ranged draw/charge cadence + E-05 draw→release/cancel lifecycle + E-06 throttled structured diagnostics/bounded recovery 2026-11-26; native BLOCKED) |
| V7-17 | Two Echo modes: Wave 7 duel + rare Phase 5 summon, no teleport-to-room | Wave 7 `EchoDuelCombatRuntimeAdapter`; Phase 5 `RAGE_ECHO` mode on the same engine (`spawnRageEcho`, `tickRageEchoes`, `RageEchoSummonPolicy`, `RageEchoSession`) | `test_end_rift_echo_runtime_wiring.py` (rare Echo tick ownership, phase/generation fences, own kind, common engine, accepted damage, policy budget, cleanup, range gate, no teleport), `RageEchoSummonPolicyTest` | IMPLEMENTED_UNVERIFIED (E-01 monolith wiring 2026-11-27; `spawnEchoClone` remains a diagnostic only; native BLOCKED) |
| V7-18 | Guardian phases 1–5: real HP/damage/hurt, grounded, 4 physical attacks | EndEvent boss runtime | boss phase probes | SRC+TEST (native BLOCKED) |
| V7-19 | Phases 3–4: geysers + temporary blocks, rare ring/forge, no spam | EndEvent hazard runtime; `BossSignatureCastGatePolicy`; `ArenaInfernoPolicy.allowsConcurrent` | `test_phase4_infernal_wiring.py` (signature thresholds live inside OVERLOAD; signature ticker refuses while magma is live), `test_phase4_infernal_expansion.py` (five-stage fracture, once-per-eruption damage, journal) | IMPLEMENTED_UNVERIFIED (2026-11-27: ring/forge can no longer start mid-Inferno; the former 0.30/0.26 thresholds were unreachable and are now 0.33/0.31, so the second authored set-piece really exists; native BLOCKED) |
| V7-20 | Phase 5: transparent coordinated decoys, up to 5, independent death, rare Echo | Phase 5 Rage runtime | `test_event_combat_profile.py`, `test_end_rift_echo_runtime_wiring.py` | IMPLEMENTED_UNVERIFIED (rare Echo wired 2026-11-27; coordinated-skill repertoire still pending owner approval) |
| V7-21 | Cooperative actions: enough members, prepare, loss, miss, cancel | `domain/BossCopyCooperationPolicy` + Phase 5 runtime `tickBossCopyCooperations` | `BossCopyCooperationPolicyTest` (admission sufficiency, prepare→fix, copy loss before/after fixation, miss, cancellation, cooldown band, beam concurrency, no dead-source shot) | IMPLEMENTED_UNVERIFIED (2026-11-27: three provisional cooperative techniques wired to the RAGE phase; owner has not ratified the repertoire, so the profiles stay windowed 25–35/20–30/40–60 s) |
| V7-22 | Transition 5→6: corridor + exact panels, safe zone/roof/portal intact | `LastSealArenaReveal`, `ArenaMergeCinematicPolicy` | `test_end_rift_arena_merge_cinematic.py` | SRC+TEST (native BLOCKED) |
| V7-23 | Last Seal: 6–8 defenders, shield per living, ring gap, real grab/throw | Kagune runtime | `test_tentacle_guardian_role_separation.py`, `test_end_rift_last_seal_ring_runtime.py` | SRC+TEST (native BLOCKED) |
| V7-24 | Victory/defeat: square restored, no stuck damage/tasks, no rewards on defeat | `EndRiftVictoryCoordinatorPolicy` | victory probes | SRC+TEST (native BLOCKED) |
| V7-25 | Post-boss: legal hit, permanent flag, names, rewards, WorldCore End access | `CoreMemorial*`, WorldCore integration | memorial/reward probes | SRC+TEST (native BLOCKED) |
| V7-26 | Restart/races at phase/gate/reward/WorldCore boundaries | journal + receipts | `test_end_rift_cold_restart_boundary_matrix.py` (15 boundary assertions: bootstrap rehydration, per-wave restore dispatch, gate snapshot recovery, reward idempotency, WorldCore unlock, stale-callback rejection, recovery-required fail-closed) | IMPLEMENTED_UNVERIFIED (cold-restart matrix automated 2026-11-27; native cold-restart timing BLOCKED) |
| V7-27 | Load: 2/5/10/11/20 + dungeon 32+, MSPT/client/packets, no leaks | `scripts/minecraft/EndRiftSyntheticLoadHarness.py` + `EndRiftSpellLoadReport.py` | `test_end_rift_synthetic_load_harness.py` (8 tests: reads real `stride()`/`MAX_VIEWERS`/`MAX_POINTS`, O(casts) candidate vs O(points·viewers) baseline, complete 2/5/10/11/20 ladder, honest `SYNTHETIC_MODEL`/native `BLOCKED` labeling, report integration) | SYNTHETIC_MODEL (deterministic 99.2–99.96% packet reduction at 2/5/10/11/20; native MSPT/bytes/GPU profiling BLOCKED) |
| V7-28 | Assets/release: active sources, real hashes, CI at final SHA, no old sphere/camera/Echo bar | build + CI | validators, `RunEndRiftEventChecks.ps1` | IMPLEMENTED_UNVERIFIED (CI P0 encoding resolved; native visual BLOCKED) |

## Deployment (2026-11-27, commit `269506a9`)

The V7-19/V7-21 build was promoted to both runtimes after a full green gate:

| Target | Path | CopiMineEndEvent.jar SHA-256 | Service |
|---|---|---|---|
| Production | `/opt/copimine-game/minecraft/server/plugins/` | `deba588753f9c0992141f069e01896f6a182412af54e56092e12a4b0e9b33dce` | `copimine-game.service` active, phase `COLLECTING` |
| Isolated site | `/opt/copimine-game/playtest-end-rift-site-20261125/plugins/` | `deba588753f9c0992141f069e01896f6a182412af54e56092e12a4b0e9b33dce` | `copimine-end-rift-site-paper-20261125.service` active, phase `UNCONFIGURED` |

Rollback backup: `/opt/copimine-game/minecraft/server/plugins-backup-20261126T225937Z/`
(previous EndEvent `37fea8ac…`, Dungeon `d400cf63…`). Resource pack
`CopiMineResourcePack-26.3-c1246a657207a3eb.zip` serves HTTP 200,
27,938,837 bytes. Both plugins enabled with no load errors; the deployed jar
contains `BossCopyCooperationPolicy`, `RageEchoSummonPolicy` and
`RageEchoSession`. Owner playtest is the native/visual acceptance path.

## Defects fixed this session (2026-11-26)

### E-04 — Rich weapon/cadence/sustain evidence was compiled away (`domain/wave7/EchoBehaviorStrategy`, `runtime/EventCombatProfileService`)
- Root cause: the profile service measured accepted hits by weapon, attack
  intervals, bow/crossbow charge and the health ratio at which each owner heals,
  but `echoBehaviorStrategy()` compiled only coarse hit/sample counts and the
  adapter used fixed `HEAL_THRESHOLD_PERMILLE=550`, a fixed melee cooldown and the
  first melee slot. Rich evidence and per-feature confidence were discarded.
- Fix: `EchoBehaviorStrategy` is now schema 3 and persists five more bounded
  features — healing threshold, swing cadence, axe-vs-sword preference, bow draw
  and crossbow charge — with a schema-2 migration that restores the neutral
  baselines. `EventCombatProfileService.blendMean` blends each feature's sample
  mean towards a competent neutral baseline by its OWN per-feature confidence, so
  one accidental sample cannot lock a style. The adapter now derives the `wounded`
  threshold from `healThresholdPermille`, the melee cooldown from
  `swingCadenceTicks` (clamped 10..40), and the held melee weapon from
  `weaponPreferencePermille` (via `EchoLoadoutState.findPreferredMelee`).
- Regression: `EchoBehaviorPersonalisationTest` (`healThresholdIsPersonalised`,
  `weaponPreferencePicksTheRecordedBlade`, `swingCadencePersonalisesMeleeCooldown`),
  `EchoBehaviorStrategyTest` (schema-2 migration + truncated-record rejection).
  Result: 113 assertions OK; `EchoBehaviorStrategyTest OK`.

### E-05 (part) — Ranged cadence ignored the recorded draw/charge time (`runtime/wave7/EchoDuelCombatRuntimeAdapter`)
- Root cause: `tryStartRanged` always used the shared fixed tuple
  (`rangedTicks=40`) for bows and crossbows, so every copy fired on the same team
  beat. The E-04 `bowDrawTicks`/`crossbowChargeTicks` evidence reached the profile
  record but never the fight, and a bow and a crossbow shared one cadence even
  though they are different charged-item lifecycles.
- Fix: `rangedCooldownsFor(strategy, rangedSlot, replica)` picks the owner's own
  recorded bow draw or crossbow charge for the weapon the copy actually holds and
  clamps it to a fair 10..60 tick band, then stages it only on the real ranged
  branch. Wind charge and splash keep their own base cadence. A neutral profile
  carries the vanilla 20-tick full draw, so its ranged cooldown is 20 — matching
  the melee neutral (20↔20) rather than the former fixed 40.
- Regression: `EchoBehaviorPersonalisationTest.rangedCadencePersonalisesRangedCooldown`
  (12-tick snap-shooter's draw completes on tick 112, 55-tick heavy drawer's on
  tick 155 under one identical observation) plus the updated exact checkpoints in
  `EchoDuelCombatRuntimeAdapterTest.acceptedRangedActionsAreScopedAndDurablySpendSupplies`.
  Result: `EchoBehaviorPersonalisationTest OK assertions=115`.

### E-05 (foundation) — Persisted ranged-charge deadline + schema-6 migration (`runtime/RealitySplitEchoDuelState`, `runtime/RealitySplitTrialSnapshot`)
- Root cause: the full E-05 bow-draw / crossbow-load lifecycle needs a durable
  "charge completes at" deadline on `ActorState`; without it a restart mid-draw
  cannot be distinguished from a ready shot and the copy would either fire a
  free instant arrow or lose the draw.
- Fix: `ActorState` now carries `rangedChargeUntilTick` (0 = no draw/load in
  flight). `RealitySplitTrialSnapshot` is schema 6; `upgradeKnownNamedSchema`
  upgrades BOTH previously supported named layouts (schema 4 with 10-field
  actors and schema 5 with 13-field actors) to the 14-field actor tuple by
  appending neutral zero deadlines, so a live Wave 7 snapshot from either older
  build still loads with no invented cooldown. Unknown layouts still fail closed.
- Regression: `EchoDuelCombatRuntimeAdapterTest.schemaFourEchoStateUpgradesWithoutInventingActionCooldowns`
  now asserts 14-field encoding and a zeroed `rangedChargeUntilTick` after the
  legacy upgrade; `EchoDuelCombatPolicyTest` round-trips all 14 fields.
  Result: `EchoDuelCombatPolicyTest OK assertions=45`,
  `EchoDuelCombatRuntimeAdapterTest OK assertions=91`, focused suite green.
- Open (now closed by the E-05 lifecycle entry below): the decision/executor
  layer used to fire a single-tick RANGED release; the draw→release state
  machine is now wired through `EchoDuelCombatPolicy` and the server executor.

### E-05 (lifecycle) — Instant max-power shot on the decision tick (`domain/wave7/EchoDuelCombatPolicy`, `runtime/wave7/EchoDuelCombatRuntimeAdapter`, `domain/wave7/EchoPresentationProbeState`)
- Root cause: `tryStartRanged` spent the arrow, opened the ranged cadence and
  fired the projectile on the very tick the copy decided to shoot. A bow can
  never release before it has been drawn, so every Echo archer had a
  frame-perfect, zero-charge max-power shot the owner could not match — and an
  interrupted draw (target left the band, line of sight broke, bow broke, quiver
  emptied) had already eaten the arrow.
- Fix: the ranged path is now a real lifecycle.
  - `tryStartRangedDraw` opens the draw: it records `rangedChargeUntilTick`
    (tick + the owner's recorded draw/charge) and spends **nothing**.
  - While the draw is in flight the adapter holds the aim (`Action.NONE`, no
    durable churn) unless the shot stopped being legal.
  - `rangedReadyToRelease` gates the release on the persisted deadline;
    `tryReleaseRanged` then spends exactly one arrow, applies the bow wear and
    clears the charge. The draw already consumed the recorded charge, so the
    release opens no second lockout — the shared action gate is the only
    recovery, which keeps the archer's interval exactly the recorded charge
    instead of silently doubling it.
  - `cancelRangedDraw` abandons an illegal draw without spending the arrow or
    opening the cadence; the monolith lowers the bow immediately.
  - A new `Action.RANGED_DRAW` carries the aim pose through the executor and
    `driveNamedEchoGait`. `EchoPresentationProbeState.begin` gained a bounded
    item-use window so a heavy draw (up to 60 ticks) keeps the bow visibly
    pulled for its whole charge instead of lowering it at the fixed 32/25-tick
    default.
  - The persisted charge deadline is rebased on restart with the 60-tick
    personalisation ceiling, so a cold restart can neither invent an extra-long
    draw nor fire before the operator's own cadence.
- Regression: `EchoDuelCombatPolicyTest.finiteResourcesWearAndRebindPersistence`
  (draw spends no arrow, early release is refused, release at the exact
  deadline spends one), `EchoDuelCombatPolicyTest.rangedDrawLifecycleCancelsAndCharges`
  (cancel spends nothing, double-cancel fails closed, draw-in-flight blocks a
  second draw, nonzero charge survives the named snapshot),
  `EchoDuelCombatPolicyTest.actionCooldownsGateAndRestore` (the shared gate paces
  the redraw), `EchoDuelCombatRuntimeAdapterTest.acceptedRangedActionsAreScopedAndDurablySpendSupplies`
  (draw → hold → release → cooldown across a durable snapshot),
  `EchoBehaviorPersonalisationTest.rangedCadencePersonalisesRangedCooldown`
  (draw completion 112/155 for the snap-shooter/heavy drawer),
  `test_wave7_echo_presentation_probe.py[draw-window]` (55-tick draw window and
  the 72 000-tick wire ceiling).
- Result: `EchoDuelCombatPolicyTest OK assertions=65`,
  `EchoDuelCombatRuntimeAdapterTest OK assertions=100`,
  `EchoBehaviorPersonalisationTest OK assertions=115`, presentation probe 11/11
  scenarios, focused suite green. Native draw/shoot capture remains BLOCKED (no
  graphical Fabric client in this environment).

### E-06 (allocation) — Duplicate private-loadout serialisation on the decision hot path (`runtime/wave7/EchoDuelCombatRuntimeAdapter`)
- Root cause: `tick` serialised the 42-slot private replica twice per decision —
  once for the identity guard (`replica.encode().equals(duel.replicaState())`)
  and again to freeze the `baselineReplica` — which is avoidable allocation in
  the per-tick path E-06 names.
- Fix: encode once into `replicaSnapshot`, compare that to the persisted state,
  and restore the baseline from the same immutable snapshot. Revision and
  rollback guarantees are unchanged; only the redundant serialisation is gone.

### E-06 (observability) — A broken actor hid behind a silent idle tick (`runtime/wave7/EchoDuelCombatRuntimeAdapter`, `runtime/wave7/EchoCombatDiagnosticThrottle`, `CopiMineEndEvent`)
- Root cause: the adapter's `catch (RuntimeException) { return Optional.empty(); }`
  treated every failure — including a permanently corrupt snapshot — as "no
  action this tick". A broken Echo therefore froze forever with no boundable
  diagnostic, while a partial supply mutation could only be recovered by the
  caller noticing the empty decision.
- Fix: the adapter now has a `Diagnostics` seam (`Diagnostics.NONE` for the
  four-argument overload, so every existing caller is untouched).
  - An expected invalid snapshot (`IllegalArgumentException`) is classified as
    `REASON_INVALID_SNAPSHOT`, reported, and fails the actor closed; the
    monolith restores the last durable receipt.
  - Any other `RuntimeException` is classified as `REASON_UNEXPECTED_FAILURE`,
    reported, and rethrown to the runtime boundary instead of being swallowed.
  - `EchoCombatDiagnosticThrottle` bounds the channel to one report per actor per
    200-tick window (a restarted counter is always a fresh window), and its
    entries are dropped with `removeNamedEchoRuntime`/teardown, so the map is
    bounded by the roster, not by uptime.
  - The monolith `emitNamedEchoCombatDiagnostic` logs only `eventGeneration`,
    `runtimeGeneration`, `room`, `duelId`, `action=TICK` and the exception
    type — never the private 42-slot inventory.
  - Recovery is bounded: `restoreNamedEchoReplica` restores from
    `current.replicaState()` and, if even that receipt is corrupt, keeps failing
    the tick closed rather than inventing state.
- Regression: `EchoDuelCombatRuntimeAdapterTest.diagnosticsClassifyAndReportBoundedReasons`
  (expected vs unexpected classification, exact failure handed through, null/`NONE`
  sinks are inert, the unobserved overload never fires), `EchoCombatDiagnosticThrottleTest`
  (first report, in-window suppression, window boundary, clock reset, forget/clear,
  null actor, invalid interval).
- Result: `EchoDuelCombatRuntimeAdapterTest OK assertions=110`,
  `EchoCombatDiagnosticThrottleTest OK assertions=15`, focused suite green.

### E-01 — The rare Phase 5 Personal Echo never existed in the live call graph (`domain/RageEchoSummonPolicy`, `runtime/wave7/RageEchoSession`, `CopiMineEndEvent`)
- Root cause: §10.4.3 requires a rare fully real hostile copy of one
  participant, driven by the common combat engine but with no room lock, no
  teleport of the owner, no boss bar, no loot and no Wave 7 progression. The only
  Phase 5 copy in the runtime was `spawnEchoClone`, an 18-second diagnostic that
  neither ran the common AI nor presented the owner's skin/equipment, so the mode
  was absent from the live call graph.
- Fix:
  - Added `RageEchoSummonPolicy` (one alive, 105 s cooldown, ×1.75 owner
    max-health, ×0.9 damage, 52.5 s lifetime, `OWNER_ONLY` incoming-damage default,
    no bar/loot/boss model) and `RageEchoSession`, a synthetic `DuelRecord`
    wrapper that owns the committed duel + private replica state.
  - Added `EVENT_KIND_RAGE_ECHO` and the monolith runtime (`tickRageEchoes`,
    `trySummonRageEcho`, `spawnRageEcho`, `tickOneRageEcho`,
    `executeRageEchoCombatAction`, `applyRageEchoDamage`,
    `driveRageEchoMovement`, `driveRageEchoGait`, `sendRageEchoView`,
    `retireRageEcho`, `clearRageEchoes`). The copy is a real Pillager hitbox
    whose client view is the owner's vanilla player model, so the critical strike,
    arrow, drink and shield are all real server actions rather than a scripted
    replay.
  - Wired the tick into the general boss loop before `pinBossToFinalSealCore`,
    and cleaned it on phase transition, last-seal entry and stage release.
  - Damage goes through the accepted-damage entry point, so a cancelled hit
    reports zero and the committed supply never persists after a rejected action.
- Regression: `tests/test_end_rift_echo_runtime_wiring.py` (tick ownership,
  phase/generation cleanup, own kind, common engine, accepted damage, policy
  budget, cleanup, range/capability gate, no teleport of the owner),
  `RageEchoSummonPolicyTest` (admission, health/damage multipliers, lifetime,
  incoming-damage modes).
- Result: `test_end_rift_echo_runtime_wiring.py` 24/24 green,
  `RageEchoSummonPolicyTest OK`. Native visual (skin, equipment, eyes, formation)
  remains BLOCKED; owner playtest is the native acceptance path.

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

### V7-19 — Overload set-pieces could stack on the Inferno; two thresholds were dead (`CopiMineEndEvent`)

- Root cause: `tickOverloadSignatureCasts` refused a ring/forge only while another
  *boss ability* owned the cast slot. The Inferno is not a `BossAbilityState` —
  it is a live block hazard — so a Rift Fire Ring or Infernal Forge could begin
  while the magma floor and steam vents were still burning, producing exactly the
  unavoidable combo V7-19 forbids. Separately, the configured thresholds ended
  at `0.30`/`0.26`, but the OVERLOAD band is `(0.30, 0.45]`; both were already
  in RAGE when crossed, so the second authored cast of each set-piece was
  silently deleted.
- Fix: the signature ticker now bails on `!arenaInfernoBlocks.isEmpty()` before
  it spends a threshold, so the set-piece is deferred to a real gap instead of
  being lost. The thresholds are now `{0.40, 0.33}` (ring) and `{0.36, 0.31}`
  (forge) — strictly inside OVERLOAD, still staggered and still paced by the
  existing 50 s / 80 s cooldowns, so both casts are reachable without spam.
- Regression: `tests/test_phase4_infernal_wiring.py`
  (`test_signature_thresholds_are_inside_the_overload_band`,
  `test_signature_cast_cannot_start_mid_inferno`); both files are now part of
  the canonical gate step.

### V7-21 — Cooperative Guardian + copy techniques did not exist in the runtime

- Root cause: §10.4.2 requires the Rift Guardian and its Phase 5 copies to
  perform coordinated techniques (mirror substitution, coordinated salvo,
  reflection chain) with preparation, a committed fix, honest copy-loss handling,
  a real miss, cancellation and cleanup. The runtime had no cooperative
  sequence at all, so the provisional repertoire was absent from the live call
  graph.
- Fix: added `domain/BossCopyCooperationPolicy`, a pure policy that owns every
  admission, timeline, copy-loss, cooldown, beam-concurrency and friendly-fire
  rule for the three windowed profiles. The Phase 5 runtime
  (`tickBossCopyCooperations`, called next to `tickEchoAttackReplicas`) owns only
  the real actors: it reserves one `BossHazardBudget` slot, captures each
  caster's eye origin and fixed aim at the fix tick, and executes real damage
  through `applyEchoReplicaDamage` on the scheduled execute ticks.
  - A copy that dies before fixation cancels the whole technique; after fixation
    mirror/chain still cancel (no invisible shot from a dead source) while the
    salvo shortens to its surviving casters.
  - `clearEchoAttackReplicas`, `clearBossOnly` and every phase/generation fence
    cancel the technique so no remote sequence survives a copy wipe.
  - `MIRROR_SUBSTITUTION` reserves `REPOSITION` (not `MAJOR_GRAB`, which needs a
    target and would always be refused) and `REFLECTION_CHAIN` reserves
    `CHEST_STRIKE`; cooperative techniques never complete a phase and never drop
    loot.
- Regression: `tests/BossCopyCooperationPolicyTest.java` (admission sufficiency,
  prepare→fix ordering, damage only on execute ticks, copy loss before/after
  fixation, miss, cancellation matrix, cooldown band, beam concurrency, no
  dead-source shot, cleanup-required).
- Status: IMPLEMENTED_UNVERIFIED — owner ratification of the provisional
  repertoire is still pending; native visual remains BLOCKED.

## Blocked (never a pass)

- `NATIVE_BLOCKED`: no graphical Minecraft 26.3 client session in this agent
  environment; the owner playtest is the native/visual acceptance path.
- Underground site plan / production-world mutation: BLOCKED without an approved
  location-bound plan.
- V7-15 Wave7 V4 asset integration: BLOCKED (no native client; the
  `CopiMine_Wave7_V4.zip` content is not present anywhere on this workstation and
  cannot be extracted to author/verify the models, textures or bars).
- V7-27 native load profiling: BLOCKED (no real 2/5/10/11/20-player Paper +
  Fabric session). The deterministic model lives at
  `scripts/minecraft/EndRiftSyntheticLoadHarness.py`, emits
  `docs/end-rift/final-26.3/SYNTHETIC_LOAD_PROFILE.json` and shows a 99.2–99.96%
  packet reduction, but **is not** a native benchmark. Run the commands in
  `native_upgrade_commands` to upgrade it to MEASURED.
- V7-26 native cold-restart timing: BLOCKED (no live server with RCON). The
  automated boundary matrix is `tests/test_end_rift_cold_restart_boundary_matrix.py`.

## Production acceptance — 2026-11-28 (merged release `c7195be7`)

The single coherent V7 release that reconciles main (`f48aac70`) with the deployed
physical-site branch (`ec03dff0`) is commit `c7195be7` on
`codex/end-rift-v7-release`. It was built deterministically with
`build-plugin.ps1` (JDK 25) into `CopiMineEndEvent.jar` (2,695,900 bytes,
SHA-256 `5BDD63E61A72D1CB2D50191E14BDB26EDABE9481013CF6DA51D487149ED6BD90`)
and promoted to both runtimes.

### Deployment

| Target | Path | JAR SHA-256 | Service |
|---|---|---|---|
| Production | `/opt/copimine-game/minecraft/server/plugins/` | `5BDD63E6…` | `copimine-game.service` active, phase `COLLECTING`, invariants=0 |
| Isolated site | `/opt/copimine-game/playtest-end-rift-site-20261125/plugins/` | `5BDD63E6…` | `copimine-end-rift-site-paper-20261125.service` active (`UNCONFIGURED` — that site has no arena) |

Both runtimes enabled the plugin with no load errors. The production event state was
preserved across the restart: `COLLECTING`, event `42ae3f02…`, generation 1, core
`CopiMine 2048,72,2048`, pads 0/2, runes 2/2, `portal=unset`.

### Arena destruction and restoration (real prod blocks, via RCON)

`/cmend arena reveal-preview` on the promoted JAR returns
`Карта совпадает с блоками. Радиус 57 · SHA-256 9ba622c8245a78853c5a74a8c3231441cfbfac56b7824621889ea52270a4e5e9`,
equal to `last-seal-reveal.approved-plan-sha256` (the 16,690-cell plan; 0 safe-zone
overlap).

`/cmend arena reveal-test` then, at t+18 s (during the 5→6 reveal), measured on the
live blocks:

| Cell | Before | During reveal | After auto-restore |
|---|---|---|---|
| `(2014,77,2014)` inner panel | `deepslate_bricks` | **`air`** | `deepslate_bricks` |
| `(2014,72,2014)` outer wall | `deepslate_bricks` | `deepslate_bricks` | `deepslate_bricks` |
| `(2085,71,2033)` far wall | `deepslate_tiles` | `deepslate_tiles` | `deepslate_tiles` |

The demo log shows `LAST_SEAL_REVEAL_BEGIN generation=1 plan=9ba622c8…`; the test boss
reached `bossPhase=LAST_SEAL`, the demo ended on its own (`bossPhase` LAST_SEAL →
AWAKENING, `boss=none`, `event-mobs=0`). After restoration `reveal-preview` again
returns `9ba622c8…`, `/cmend debug invariants` reports `failures=0`, and the service
log has no End Rift errors. This proves real block destruction **and** the reverse
square restoration on the production world, including that the safe zone, outer wall
and far tiled wall are untouched.

### Physical site structure (prod, measured)

The site chunks away from the arena were not resident, so a first block probe returned
"no match" for every id. They were loaded with
`execute in minecraft:overworld run forceload add 1888 2000 2096 2128`, probed, and the
forceload was removed again (the pre-existing 28 force-loaded chunks were left untouched).
Measured live blocks:

| Element | Probe | Block |
|---|---|---|
| Corridor floor | `(1992,71,2048)` | `polished_deepslate` |
| Corridor light strip | `(1992,72,2048)` | `light` |
| Corridor interior | `(1992,74,2048)` | `air` |
| Corridor ceiling | `(1992,77,2048)` | `deepslate_tiles` |
| Corridor walls | `(1992,74,2044)` / `(1992,74,2052)` | `deepslate_bricks` / `polished_basalt` |
| Safe-zone floor | `(1980,71,2048)` | `spruce_planks` |
| Safe-zone ceiling | `(1980,81,2048)` | `deepslate_tiles` |
| Safe-zone interior | `(1980,73,2048)` | `air` |
| Dungeon wall | `(1918,74,2060)` | `deepslate_bricks` |
| Dungeon hall interior | `(1930,74,2060)` | `air` |
| Arena core floor | `(2048,71,2048)` | `deepslate_tiles` |
| Arena interior | `(2048,73,2048)` | `air` |

Combined with `reveal-preview` validating all 16,690 authored plan cells (374 corridor +
16,295 wall + 21 fissure) against live block data, this proves the arena, corridor,
safe zone and dungeon shell are physically present on production as authored.

The dungeon plugin itself reports a clean runtime site (`/cmdungeon status`):
`Схема: v1`, `Комнат: 6, шлюзов: 1, секретов: 0, спавнов: 10`, `Проблем загрузки: 0`.
The six authored halls are:

| Room | Bounds (world `CopiMine`) | Size | Enabled |
|---|---|---|---|
| `arena_playtest_dungeon` | `[1933,71,2036]..[1957,84,2060]` | 25×14×25 | yes |
| `rift_elite_hall` | `[1905,71,2036]..[1929,84,2060]` | 25×14×25 | yes |
| `rift_group_hall` | `[1933,71,2012]..[1957,84,2034]` | 25×14×23 | yes |
| `rift_final_hall` | `[1933,71,2062]..[1957,84,2086]` | 25×14×25 | yes |
| `rift_admin_wing` | `[1901,71,2062]..[1929,84,2086]` | 29×14×25 | no (test wing) |
| `rift_spell_range` | `[1901,71,2092]..[1929,84,2116]` | 29×14×25 | no (test range) |

### Portal room — BLOCKED-OWNER

`/cmend portalroom info` = `CopiMine_the_end 0.5,80.0,0.5 (source=config)`. The world
`CopiMine_the_end` exists (`CopiMine/dimensions/minecraft/the_end`; WorldGuard loaded
it), but the End is closed through `CopiMineWorldCore` (`/cmworld end status` =
`закрыт`), so the Rift Core Shard teleport is already gated. The configured point is
not a standable destination: a scoreboard block probe at `(0,y,0)` in the End shows
the island surface at y=60 (solid at 60, air 61+), so the floor beneath y=80 is air;
and no `end_portal`/`end_portal_frame` block exists near the arena in the overworld
region files. Per V7/OV4.7 the pre-existing behind-arena portal room must not be
guessed, so its coordinates remain **BLOCKED-OWNER** pending the owner's real survey.

### Code upload

The reviewed source and the exact deployed jar are on the server at
`/opt/copimine-game/code-uploads/20261128-end-rift-v7-c7195be7/`:
- `end-rift-v7-src.tgz` — all plugin `src`, `docs/end-rift` (ledger +
  `V7_OWNER_ACCEPTANCE_GUIDE_RU.md`), `tests`, the built jar, plus `MANIFEST.txt`,
  `git-log.txt`, `git-diff-stat.txt`.
- `CopiMineEndEvent-5BDD63E6.jar` (2,695,900 bytes,
  SHA-256 `5BDD63E61A72D1CB2D50191E14BDB26EDABE9481013CF6DA51D487149ED6BD90`).

The branch `codex/end-rift-v7-release` is pushed to `github.com/IliaZav/copimine`; the
End Rift production source is unchanged from `c7195be7` (later commits are docs and one
test-probe fix).

### Backups / rollback

- Previous jar: `/opt/copimine-game/deploy-backups/20261127T200539Z/CopiMineEndEvent.jar.pre-5BDD63E6`
  (SHA `cd2f7765…`).
- Pre-destruction overworld region: `/opt/copimine-game/backups/arena-region-20261127T200539Z/overworld-region.tgz`
  (130,523,099 bytes).
- Exact destructive-cell snapshot: `plugins/CopiMineEndEvent/last-seal-arena.tsv`
  (761,176 bytes); `/cmend arena reveal-stop` restores from it idempotently.

### Automated suite status

A full `pytest tests` run over the merged tree was executed with JDK 25 on `PATH`
(the Java probe harnesses need `javac`/`java`). Final totals:

```
16 failed, 3936 passed, 15 skipped, 154 warnings, 2 subtests passed in 2061.61s (0:34:21)
```

The only End Rift-caused failure was
`test_boss_edge_volley_runtime.py`, whose hand-built Java probe did not declare the
`stopCocoonBeam(String)` seam that the merged `cancelBossCastForPhaseChange` now calls;
the probe stub was added and all 12 cases pass (`12 passed in 11.15s`). This is a
harness fixture, not production code — the production method signature is authoritative.

The remaining 16 failures are **environment/packaging pre-conditions, not End Rift
defects**, and are reproducible on the untouched deployment branch too:

| Test | Cause |
|---|---|
| `test_copimine_client_mob_artifact.py` (4) | Gradle client build absent: `CopiMineClient/build/{libs,devlibs}/CopiMineClient-0.1.1*.jar` not built in this checkout |
| `test_end_event_current_contract.py::test_staged_client_artifact_matches_current_source_build` | same missing source-built client jar |
| `test_minecraft_26_3_pack.py::test_built_26_3_candidate_matches_locked_source_and_archive_digests` | pinned Minecraft 26.3 client not downloaded (`build/minecraft-26.3/toolchain/minecraft-26.3-client.jar`) |
| `test_minecraft_26_3_paper.py::test_paper_server_candidate_matches_locked_build_and_download_object` | `PrepareMigrationCandidates.ps1` did not stage `paper-26.3-205.jar` |
| `test_minecraft_26_3_paper.py::test_copimine_client_candidate_matches_locked_build_artifact` | on-disk `CopiMineClient-0.1.5+26.3.jar` size 30,706,420 ≠ lock 30,705,791 |
| `test_minecraft_migration_server_plugins.py` (7) | stale first-party 26.3 candidates (Dungeon lock `0020ee86…` vs disk `f447404b…`; EndEvent lock size 3,001,381 vs disk 2,958,162) and un-staged `CoreProtect-CE-…-26.3-upstream.jar`, `ClearLag …-26.3.jar`, `Chunky-Bukkit-1.5.3.jar` |
| `test_minecraft_26_3_custom_plugin_database_contracts.py` | same Dungeon candidate lock drift |

Closing these requires running the full 26.3 migration pipeline
(`PrepareMigrationCandidates.ps1` + downloads + Gradle client build), which is outside
the End Rift runtime and does not change any deployed End Rift behaviour.

### GitHub Actions

Pushing `codex/end-rift-v7-release` triggered `CopiMine verification`. The run fails in
the step **Build and verify pinned Minecraft 26.3 candidates** with:

```
CopiMineClient build output differs from its pinned artifact digest and size: pinned 307…
throw 'Pinned Minecraft 26.3 candidate preparation failed.'
```

Because that step aborts the job, `Run Python contract tests` never executes and
`Publish Python test report` then fails on the missing `test-results.xml`. This is the
same non-reproducible client-JAR pin that also fails the local
`test_copimine_client_mob_artifact.py` group, and it is **pre-existing**: the previously
deployed branch `codex/end-rift-v7-acceptance` (`ec03dff0`, run `46035755195`) fails with
the identical message. It is a Minecraft-26.3 client packaging problem, not an End Rift
source defect; the End Rift contract tests were therefore run directly against this tree
(see above) instead of through CI.
