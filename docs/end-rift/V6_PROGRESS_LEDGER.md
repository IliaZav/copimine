# CopiMine End Rift — V6 persistent progress ledger

> Append-only ledger. Newest section wins. Read this first after any context
> reset instead of re-auditing. Final owner-facing report is written in Russian.

## 0. Session identity and authority

- Objective (owner, latest): finish the entire End Rift event + dungeon to the
  V6 specification, with authored 3D VFX/audio for every spell, then run real
  builds, tests and (when safe) production deployment. No stubs, no fabricated
  evidence. This supersedes the earlier "no build / no test" restriction.
- Canonical specs (AGENTS.md gate). Re-verified 2026-11-25:
  - `D:\Downloads\COPIMINE_END_RIFT_WAVES_CANONICAL.md` — SHA-256
    `6E1245B44A449A0EF1BAB9193606398660C68F6BB2DE7D7BB6082CC8CDE50CAF` (matches).
  - `D:\Downloads\COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` — SHA-256
    `0C781E42E274A3F27762E0B568CC1D8FFD7DFD8623E5A0F1DEB65EA73A94AE50` (matches).
- New spec documents (V6, 2026-11-24): `CopiMine_End_Rift_Master_V6_2026-11-24.md`,
  `CopiMine_End_Rift_Audit_Architecture_V6_2026-11-24.md`.
- Source safety: no force push; no destructive checkout; no invented production
  coordinates; production-world block mutation stays BLOCKED without an explicit
  location-bound approved site plan.

## 1. Worktrees (ground truth 2026-11-25)

| Worktree | Branch | HEAD | State |
|---|---|---|---|
| `end-rift-v6-integration` | `codex/end-rift-v6-integration` | `e0322ff2` | clean except untracked `.serena/`, `CopiMineClient/bin/` |
| `end-rift-live-playtest-repair` | `codex/end-rift-live-playtest-repair` | `681c7cb2` | 5282-line tracked diff + 15 untracked files (V5.1 rework) |

- Integration target = `end-rift-v6-integration` (per V6 instruction).
- Backups written to `D:\Downloads\_endrift_v6_backup_20261125_155322\`
  (`live/tracked.patch`, `live/<untracked files>`; `v6/` empty by design).
- v6 branch adds 15 commits / 8694 insertions over `681c7cb2`, including the real
  client VFX stack: `SignedSpellVisuals`, `SpellContactMesh`, `GuardianSpellAccents`,
  `SpellVisualRenderer` (+582), `LastSealSpellEffects`, `EndRiftTentacleRenderer`,
  shader `wave5_volume.fsh`, `geyser.ogg`.

## 2. Binding integration decisions (this session)

1. Base is `end-rift-v6-integration`. v6 already has permanent Kagune strike AND
   capture, arena-derived ring timing, `ArenaTransformationPolicy`,
   `LastSealArenaReveal`, and the cooperative SKY_HAIL.
2. **SKY_HAIL stays the v6 cooperative version** (two marksmen converge → airborne
   collision → falling arrows). The V5.1 individual ceiling arrow-rain implementation
   in the live worktree is **NOT** ported (V6 explicitly forbids replacing the
   cooperative version with individual ceiling arrow rain).
3. Port from the V5.1 rework only the non-conflicting mechanisms V6 still requires:
   - `GuardianEscalationPolicy` (§3 destroyed-fraction cadence) — v6 has none.
   - `ArenaTransformationPlanPolicy` / `ArenaTransformationRuntime` (§1 fail-closed
     bounded reveal + durable recovery) vs `LastSealArenaReveal` — dedupe to one owner.
   - `Wave7MarksmanInvisibilityPolicy` clock fix + paired-crossfire damage admission
     fix + `RIFT_PROJECTILE` LAST_SEAL pool admission — verify against v6 first.
4. No blind merge; each port is compile- and test-verified on v6 before proceeding.

## 3. Verifiable baseline established this session

- **Java compile**: full `copimine-end-event` sources + 26.3 protocol sources
  compile against the pinned Paper `26.3.build.205-stable` classpath:
  `V6 TYPECHECK EXIT=0` (only pre-existing deprecation warnings).
- **Python broad End Rift sweep** (`-k` on end_rift/wave/guardian/kagune/boss/
  last_seal/tentacle/archmage/marksman/warden/fog/gravity/…), first run:
  `10 failed, 2578 passed, 1 skipped, 147 errors`. The 147 errors were one
  test-probe drift defect, now fixed (see 4.1).

## 4. Completed work (newest first)

### 4.8 Phase 4 fire-arena expansion: decay + two signature spells (2026-11-25)

Owner request (latest): make the Phase 4 fire arena genuinely detailed, add the
rotating fire ring and the "Infernal Forge"/«Адская кузня» signature spells, make
cold patches decay with the fight, gate the new spells once per HP threshold, and
keep safe zones reachable. Implemented as pure, tested owners plus a thin adapter:

- **NEW `domain/InfernoDecayPolicy.java`** — cold patches decay with the
  Guardian's health fraction: fewer (never below `MIN_PATCHES = 3`) and no larger
  than the full-health radius as the fight drags; relocation cadence shortens
  from 160 ticks to the five-second floor; and `selectCovering(...)` chooses the
  patches for *reachability* (every walkable cell within
  `MAX_SAFE_SPACING_BLOCKS = 7` Chebyshev blocks of one), distinct from the
  existing minimum-spacing `safeCells`.
- **NEW `domain/RiftFireRingPolicy.java`** — the new rotating fire ring:
  `GATHER → FLIGHT → BURST → ENDED`, 14 segments on a spinning 2.8-block ring,
  locked (non-homing) heading, detonates on a real player/floor contact, real
  hit/burst radii, damage + ignite, and a 1000-tick cooldown.
- **NEW `domain/InfernalForgePolicy.java`** — «Адская кузня»:
  `GATHER → OVERHEAT (3 strikes, each a directed fire impulse) → SLAM (two-limb)
  → VULNERABLE`; the broken shell takes `2.0×` damage in an 80-tick window;
  `spawnsExtraMobs()` is `false` (medium difficulty).
- **NEW `domain/BossSignatureCastGatePolicy.java`** — the shared once-per-HP-
  threshold gate: a signature fires at each configured threshold exactly once,
  spending the earliest-crossed one first so a fast drop cannot skip a threshold.
- **Adapter wiring** in `CopiMineEndEvent`: `arenaInferno` now sizes and places
  its cold patches from `InfernoDecayPolicy` and `tickArenaInferno` relocates
  them on the decay cadence (`relocateInfernoSafeCells`, `bossHealthFraction`);
  `tickOverloadSignatureCasts` runs from the main boss loop and starts
  `startRiftFireRing`/`startInfernalForge` on staggered threshold sets
  (`{0.40, 0.30}` ring, `{0.36, 0.26}` forge); the forge's vulnerable window
  feeds `currentBossIncomingDamageMultiplier`; leaving OVERLOAD retires both
  ledgers.
- Evidence: `pytest tests/test_phase4_infernal_expansion.py
  tests/test_phase4_infernal_wiring.py tests/test_end_rift_cocoon_contract.py
  tests/test_tentacle_guardian_role_separation.py
  tests/test_end_rift_last_seal_arena_reveal.py
  tests/test_last_seal_structural_geometry.py
  tests/test_minecraft_migration_profile.py → 76 passed, 1 skipped`;
  `PLUGIN BUILD EXIT=0`.
- Remaining: Phase 5 echo-copy upgrades, Phase 6 boss-bar animation + tentacle
  coil, Phase 4 music track, the transition cinematic and native VFX/audio
  capture (still `NATIVE_BLOCKED`).

### 4.7 Build-and-verify pass: migration pack, gate, VFX reel (2026-11-25)

- **26.3 migration resource-pack candidate rebuilt and digest-verified offline.**
  The pinned 26.3 vanilla client (`sha1 e877b6a0…`, 41 483 720 B) was fetched and
  `resourcepacks/build-migration-pack.py` was run; it verified the legacy pack
  (`c466df56…`) and produced
  `build/minecraft-26.3/CopiMineResourcePack-26.3.zip` matching the pinned
  `candidateSha256` (`98d79505…`) — 20 migrated block-atlas textures, 36
  normalized model UV faces, 34 item definitions, 85 registered models. This
  removes the gate's former blocker `test_minecraft_26_3_pack.py`.
- **Gate `tests/RunEndRiftEventChecks.ps1`** (`-SkipBuilds`, pinned Temurin
  25.0.4.1): "Prebuilt artifact presence", the AuthMe probe, Wave 7 Echo (180),
  the V5.1 Wave 7 runtime probes (41) and the wave-navigation/passage step
  (29 + 1 + 156) all pass. A later step and the broad sweep failed **only** on
  host commit-limit exhaustion (`Native memory allocation (mmap) failed … G1
  virtual space`, Windows errno 1455) caused by running two Java-compiling
  suites concurrently; not a code regression. Policy: run these suites serially.
- **Owner-facing VFX evidence**: the committed `docs/end-rift/2026-11-23/spell_previews`
  set (12 authored casts × `svg` + animated `gif`, plus `playtest_reel.html`) is
  `--check`-pinned to the shipped `SpellVisualGeometry`; a copy was placed at
  `C:\Users\zavod\copimine-spell-previews` and opened in the browser so the owner
  can watch every authored cast arc.

### 4.6 Directed arena-merge scene, frozen roster, hold release (2026-11-25)

Three genuine V6 gaps were found by re-reading the master against v6 and are
now implemented, compiled and tested on the v6 worktree.

1. **§13.2 directed RAGE → LAST SEAL arena-merge scene (12–16 s, five shots).**
   - NEW `domain/ArenaMergeCinematicPolicy.java`: five contiguous shots —
     `BOSS_FRACTURE` 0–2 s, `WALL_COLLAPSE` 2–5 s, `TWO_WORLDS` 5–8 s,
     `GUARDIAN_ASCENDS` 8–12 s, `LAST_SEAL_IGNITES` 12–16 s — total 16 000 ms,
     unique signature/colour/sound per shot, reveal commit at 2 000 ms,
     ascension at 8 000 ms, seal ignite at 12 000 ms, a static self-check that
     fails at class load on a bad edit, and `grantsInvulnerability()` always
     false.
   - `domain/BossImpactGeometryPolicy.arenaMerge(signature, radius, progress)`
     draws one authored rig per shot; an unknown signature draws nothing.
   - `BossPresentationEffects.playArenaMerge(...)` + `mergeSound(...)` are the
     presentation-only rig (no `setType`/`setBlockData`/`damage`).
   - `CopiMineEndEvent`: `startArenaMergeScene`, `onArenaMergeBeat`,
     `playArenaMergeCue`; LAST_SEAL entry calls the scene and holds the body
     for `ArenaMergeCinematicPolicy.totalMillis()`; the retired
     `LAST_STAND_INTRO_MILLIS` 5.2 s fallback constant was removed, so no path
     can claim the transformation finished before the scene closes.
   - The approved physical reveal commits exactly once, at the wall-collapse
     shot, through `startLastSealArenaReveal` (fail-closed); if no approved
     site plan exists the honest fallback is `startCurrentLastSealVisuals`.
2. **§3 frozen guardian roster.** `guardianRosterOriginal` is frozen once, at
   the first tick where the permanent Kagune target is non-zero, and read by
   `lastSealSpellIntervalMillis` as the escalation denominator; it is only
   cleared by `resetTentacleGuardianLedger` on a new generation. A player
   leaving can no longer slide the band back.
3. **§6/§11 hold release.** `tentacleHeldPlayers` records
   display→player after a successful grab; the normal throw clears it before
   any guard; `removeTentacle`, `clearTentacles`, death and logout all release
   the body (live release lands on a validated arena floor; a corpse/offline
   body is only forgotten, never teleported).

Evidence:
- `pytest tests/test_end_rift_arena_merge_cinematic.py
  tests/test_end_rift_last_seal_cadence.py
  tests/test_end_rift_tentacle_hold_release.py
  tests/test_end_rift_sky_hail_and_guardian_permanence.py
  tests/test_end_rift_last_seal_arena_reveal.py
  tests/test_last_seal_arena_ritual_wiring.py → 19 passed`.
- `V6 TYPECHECK EXIT=0` (408 sources); `PLUGIN BUILD EXIT=0`
  (`CopiMineEndEvent.jar` copied into `minecraft/server/plugins/`).

### 4.5 Cooperative SKY_HAIL verified as the superseded individual rain guard

`test_end_rift_sky_hail_and_guardian_permanence.py` pins V6's cooperative
version: squad ≥ 2 or no cast, second wave only with a third live marksman,
exactly 12 real `spawnArrow` projectiles per wave carrying
`MARKSMAN_SKY_HAIL`, and `Wave7MarksmanSkyHailPolicy` absent from production.
It also pins permanent guardian destruction (`guardianDeaths.isDestroyed` gate
before spawn) and hold release on every removal path.

### 4.4 Cleared all broad-sweep drift failures (2026-11-25)
The broad V6 sweep surfaced 10 failures + 50 errors; every one was test-harness
drift caused by the VFX/mechanics commits, not a production defect. Fixed without
weakening any assertion:
- `test_boss_cast_locomotion_runtime.py` — probe now models the `BossAbilityState`
  enum the melee-opening branch reads.
- `test_end_rift_boss_spell_dispatch_contract.py` — accepted-cast cadence now
  asserts both sanctioned sources (`lastSealSpellIntervalMillis` while the cocoon
  is intact, `scaledBossDelayMillis` otherwise).
- `test_end_rift_spell_presentation.py` — contact cues (`*_contact`) are excluded
  from the cast-token roster (they are pinned against `SpellContactMesh`).
- `test_end_rift_wave6_visual_mechanics.py` — prisoner fixture models the new
  `LastSealArenaReveal` collaborator `isArenaLocation` consults.
- `test_rift_ram_trample_runtime.py` — probe gained `Particle.DUST/ASH`, `Color`,
  `Location.add`, `host.inArena` and the Object-less `spawnParticle` overload.
- `test_spell_visual_authoring.py` — boss-lease assertion now reads the real
  6-arg `playBossSpellModel` body (the 5-arg overload only delegates).
- `test_tentacle_throw_protection_adapter.py` — probe stubs `tentacleController.state`.
- `test_wave2_return_bootstrap.py` / `test_wave7_return_bootstrap.py` — probes model
  the Last Seal arena journal restore (`LastSealArenaReveal`, `getDataFolder`,
  `restoreLastSealArena`) that `tryBootstrap` now performs.
- `test_wave7_named_trial_warning_audio_runtime.py` — probe enum gained
  `MARKSMAN_SKY_HAIL` and `Location.subtract`.
- `test_end_rift_miniboss_counterplay.py` — probe stubs `Vector`, `getUniqueId`
  and `publishSpellContact`.
- `test_wave6_converted_arrow_runtime.py` — probe stubs
  `namedTrialProjectileAttackKey` and `renderMarksmanLanceFlight`.
- Evidence: focused set `114 passed` (`PYTEST EXIT=0`).

### 4.3 Repaired the two audit-flagged superseded assertions (V6 §3/§6)
- `tests/test_tentacle_guardian_role_separation.py` — rewritten for the V6 contract
  "permanent Kagune MUST strike AND grab". The retired strike-only assertions
  (`} else if (!state.temporary()) {`, "no capture states") were replaced with
  behavioural assertions: the strike is a real dodgeable damage event (reach +
  facing + LOS before `applyBossActorAbilityDamage`, no `setHealth`); the capture
  is admitted only behind real guards (same reach/facing/LOS, one live hold/throw,
  legal safe socket, `applyTentacleReleaseHit`); the melee apparition / small
  Kagune never capture.
- `tests/test_end_rift_last_seal_ring_runtime.py::test_rows_advance_in_order_and_the_cast_is_bounded`
  — the fixed `elapsed / ROW_TICKS` assertion was replaced with the real
  arena-derived cadence (`travelBlocks = outerRadius - innerRadius`,
  `rowTicks = EMERGE_TICKS + travelTicks + RETRACT_TICKS + GAP_TICKS`,
  `elapsed / rowTicks`, bounded by `rowTicks * KaguneRingGapPolicy.ROW_COUNT`).
- Evidence: `pytest tests/test_tentacle_guardian_role_separation.py
  tests/test_end_rift_last_seal_ring_runtime.py tests/test_end_rift_kagune_ring_contract.py
  → 17 passed`.

### 4.2 Probe drift fixed (restores 147 errored tests)
- `tests/test_boss_attack_damage_admission.py`: the generated
  `BossAttackAdmissionProbe` called `publishSpellContact(...)` introduced by the
  VFX commits but never stubbed it. Added the same 8-arg stub the sibling probes
  use: `void publishSpellContact(String token,LivingEntity actor,UUID contact,
  Location origin,Vector direction,double radius,double reach,int ticks){}`.
- Evidence: `pytest tests/test_boss_attack_damage_admission.py → 95 passed, 2 skipped`.

### 4.1 Baseline recon
- Canonical hashes re-verified; both worktrees backed up; v6 compile baseline
  (`EXIT=0`); broad pytest sweep run to enumerate real failures.

## 5. Requirement → code → test → status matrix (in progress)

Status legend: `SRC` implemented in source, `TEST` covered by automated test,
`NATIVE` verified in real Minecraft (BLOCKED unless stated), `PROD` deployed.

| Req (V6) | Owner code | Test | Status |
|---|---|---|---|
| §6 permanent Kagune strike AND grab, real 4.5–5-block reach | `CopiMineEndEvent.strikeTentacleGuardianTarget` / `acceptGuardianCapture` / `guardianStrikeRangeSquared` | `test_tentacle_guardian_role_separation.py` (rewritten) | SRC+TEST |
| §6 destroy is permanent, release on every death/logout/removal | `ensurePermanentTentacles` destroyed-slot gate; `releaseTentacleHolder` / `releaseAllTentacleHolders` / `dropTentacleHoldsForPlayer` | `test_end_rift_tentacle_hold_release.py`, `test_end_rift_sky_hail_and_guardian_permanence.py` | SRC+TEST |
| §6 spell cadence by destroyed fraction 7–9/5–7/4–5.5/3–4.5 | `lastSealSpellIntervalMillis` bands + `guardianRosterOriginal` frozen denominator | `test_end_rift_last_seal_cadence.py` | SRC+TEST |
| §6 ring rows to real usable radius, 2-block gaps | `LastSealRingRuntime` + `KaguneRingGapPolicy` | `test_end_rift_last_seal_ring_runtime.py` | SRC+TEST |
| §6 cooperative SKY_HAIL (12 arrows, 3-mark wave, lone survivor refused) | `beginMarksmanSkyHail` / `releaseMarksmanHail` | `test_end_rift_sky_hail_and_guardian_permanence.py` | SRC+TEST |
| §6 safe zone / beds / lighting untouchable; only approved corridor demolished | `LastSealArenaReveal` protected cell predicate + approved digest | `test_end_rift_last_seal_arena_reveal.py` | SRC+TEST |
| §6 remove unsafe official fallback; bounded reveal batches; durable recovery | `LastSealArenaReveal` (MAX_CELLS 1536, batches of 26, 32-chunk span guard, journal recovery); `LAST_STAND_INTRO_MILLIS` removed | `test_end_rift_last_seal_arena_reveal.py` | SRC+TEST |
| §2 12–16 s arena-merge cinematic (cracks/shake/collapse/ascent/cocoon/6–8 Kagune) | `ArenaMergeCinematicPolicy` + `BossPresentationEffects.playArenaMerge` + `startArenaMergeScene` | `test_end_rift_arena_merge_cinematic.py` | SRC+TEST |
| §4 Warden native + 5×4 sweep + R6 through-wall echo + sculk | v6 | `test_warden_v5_runtime.py` | SRC+TEST |
| §4 Archmage vex/fangs/crown/spear/beam/repulse | v6 | `test_wave7_archmage_*` | SRC+TEST |
| §7 3D meshes/materials/UV/animation/audio | v6 client stack | `SpellVisualGeometryTest`, `GuardianSpellMeshTest` | SRC |
| §7 fog depth-aware volume + safe shelters | v6 | `test_wave_coordinator_fog_lifecycle.py`, `test_wave5_uniform_volume.py` | SRC+TEST |
| CI: artifact digest / reproducible build | `tools/minecraft-26.3/*.lock.json`; 26.3 migration pack rebuilt + digest-verified offline; server-plugin candidate staging still environment-bound | `test_minecraft_migration_profile.py`, `test_minecraft_26_3_pack.py` | SRC+TEST |
| NATIVE visual acceptance on matched Paper 26.3 + Fabric 26.3 | — | F2 capture | NATIVE_BLOCKED |
| PROD deploy `copimine-game.service` | — | — | NOT PERFORMED |

## 6. Blockers

- `NATIVE_BLOCKED`: no matched Minecraft 26.3 client/server graphics session has
  been performed by this agent. Native F2/video evidence must come from a real run.
- `PROD` not performed; requires backup + rollback candidate + health checks.
- §1 production-world block mutation stays BLOCKED (no approved site plan).

## 7. Next steps (ordered)

1. Re-run the broad End Rift pytest sweep **alone** (no other Java-compiling
   suite concurrently) and record the final pass count.
2. Re-run `tests/RunEndRiftEventChecks.ps1` alone to completion, so the Java
   pure-policy and persistence suites actually execute, and record every step.
3. Native F2/video capture on a matched Paper 26.3 + Fabric 26.3 session remains
   `NATIVE_BLOCKED`; publish exact manual capture steps for the owner.
4. Production-world mutation and deployment stay `PROD`/`BLOCKED` without a
   location-bound approved site plan.
