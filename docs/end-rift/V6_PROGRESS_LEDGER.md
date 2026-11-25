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

### 4.12 Production deployment + local client install (2026-11-26)

- **Deployed to `copimine-game.service`** (Paper 26.3-205, Java 25.0.5). Staged
  `CopiMineEndEvent.jar` (2 598 592 B, sha256 `2571a230…`) and the previously
  absent `CopiMineDungeon.jar` (254 956 B, sha256 `d400cf63…`), stopped the
  service (never `/reload`), backed up the two jars + the `CopiMineEndEvent` data
  dir + narcotics config + `server.properties` to
  `/opt/copimine-game/deploy-backups/20261125T213124Z`, installed atomically,
  restarted and compared bytes. Cold start reached `Done (46.3s)`, the 33rd
  plugin `CopiMineDungeon v1.0.0` enabled, `cmend status` rehydrated the same
  mid-`COLLECTING` event (generation 422), `cmend debug invariants failures=0`,
  `cmdungeon status` clean, and the log contains **0 ERROR/SEVERE** lines.
- **Client pin.** `plugins/CopiMineNarcotics/config.yml`
  `required_client_mod_version: 0.1.5+26.3` (was `0.1.4+26.3`), matching
  `profile.lock.json` `clientArtifact.version`; no jar in the running set is
  touched outside EndEvent + the additive Dungeon.
- **Resource pack unchanged.** A fresh `build-migration-pack.py` run reproduced
  the deployed `CopiMineResourcePack-26.3.zip` byte for byte
  (sha256 `98d79505…`, 34 373 458 B), so no pack redeploy was needed; the vanilla
  migration candidate zip is a separate, larger hand-off artifact.
- **Client published + installed.** The reproducible
  `CopiMineClient-0.1.5+26.3.jar` (30 769 867 B, sha256 `f9f87f78…`, which now
  bundles fabric-api `0.164.0+26.3` as a nested `META-INF/jars/*` jar) was
  published to `https://copimine.ru/assets/` (HTTP 200, exact byte count
  verified) and installed into the owner's Prism `Copimine` instance; the prior
  `CopiMineClient-0.1.4+26.3.jar` was moved to `copimine-mod-backups/`.
- **Dungeon permission repair.** The first cold start raised
  `AccessDeniedException` on `plugins/CopiMineDungeon/*` because the data dir was
  root-owned; re-chowned to `copimine-game:copimine` and restarted clean.
- Verification: authoritative gate `GATE_EXIT=0` on the tree that became
  `dc0549f8` (EndEvent + Dungeon + client all built and tested); prod RCON probes
  as above.

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
- Remaining: the transition cinematic and native VFX/audio capture (still
  `NATIVE_BLOCKED`).

### 4.9 Phase 4 cinematic/music, hit flavours, Phase 6 bar, Phase 5 echoes (2026-11-25)

Owner directives after the §4.8 increment. Implemented as pure, tested owners plus
thin adapters; native capture stays `NATIVE_BLOCKED`.

- **Phase 4 transition cinematic** — NEW `domain/InfernoTransitionCinematicPolicy`
  owns the 3.2 s staged RIFT→OVERLOAD scene (GATHER → LEVITATE → VORTEX →
  RELEASE), inside the V6 §947/§8 3–6 s window. The monolith drives it from a
  generation-fenced ticker (`startInfernoTransitionCinematic`), rendering shards,
  the overhead flame column and exactly one pressure release. Presentation only:
  no invulnerability, no control hold.
- **Phase 4 music enabled + Phase 6 music bug fixed** — NEW
  `domain/BossPhaseMusicPolicy` makes every phase→`music.phase` key explicit. The
  old `"boss-" + bossPhase.name().toLowerCase()` derivation produced
  `boss-last_seal` and silently stranded LAST_SEAL on the previous track;
  `boss-overload` (Phase 4) and `boss-last-seal` (Phase 6) now both resolve, and
  enum/kebab/snake spellings are accepted.
- **Slightly fiery Phase 4 body** — `renderOverloadBossEmbers` emits a low-cadence
  ember aura anchored to the real boss body.
- **Splash/critical damage feedback** — NEW `domain/BossHitFlavorPolicy`
  classifies a real melee/projectile hit; `BossHitFeedbackPolicy` now carries
  `critical`/`splash` and selects a louder cue (`ENTITY_PLAYER_ATTACK_CRIT`), extra
  CRIT/ENCHANT sparks and a `SWEEP_ATTACK` ring. Presentation only: the
  authoritative damage and the red flash are unchanged, and blocked/missed hits
  stay silent.
- **Phase 6 dramatic boss bar** — `EndRiftBossBarHud` layers a breathing accent
  and a two-pass coiling tentacle ribbon over the authored frame; both wall-clock
  driven, bounded and fully suppressed by reduced-motion.
- **Phase 5 echoes reproduce the victim's own action** — NEW
  `domain/PlayerEchoCopyPolicy` maps the target's recorded melee/ranged/area
  action to a copyable family and rolls a bounded, deterministic chest-beam chance
  (solo 0.50 → raid 0.20). Melee/projectile feedback sites record the real action.
- Evidence: `pytest tests/test_phase5_6_owner_directives.py → 7 passed`;
  `PLUGIN BUILD EXIT=0` (30 tasks).

### 4.10 Echo wire P0, Phase 5 marker redesign, 20-TPS bands, probe/compile repair (2026-11-25)

- **P0: every Wave 7 Echo packet was silently dropped at encode.** A real
  `EndEventBridgeBodyCodec.encode` probe (outside the repo, pinned JDK 25) proved
  `END_ECHO_STATE` with `durationMillis=2000` throws
  `IllegalArgumentException: Invalid Echo presentation fields` (codec line 590),
  while `durationMillis=0` encodes and round-trips byte-for-byte. Both producers
  — `sendEchoCloneView` (monolith 10370) and `sendNamedEchoView` (27335) — passed
  `2_000L`, so no Echo avatar ever rendered. Fixed to `0L`: the signed Echo body
  is lease-free and the client owns freshness (client lease 2000 ms + broadcast
  keepalive). No test was weakened.
- **Phase 5 copy marker moved into the frame epoch band.** The planned
  `reason`/`source` string marker is impossible: `EndEventBridgeBodyCodec`
  refuses any non-empty reserved string on an `END_ECHO_*` body
  ("End Event reserved strings must be empty"), proven by the same probe. The
  copy contract now rides the 12-field frame's own `epoch`:
  `Phase5EchoCopyPresentationPolicy.isCopyEpoch(epoch)` (`epoch >= EPOCH_BASE`
  = 1 000 000), `epochFor(generation)`. The client decoder derives `copyAlpha`
  from the epoch, never from a metadata string; Wave 7 duel epochs stay below the
  band and keep the opaque drained body. No wire-schema change.
- **20-TPS transcription repaired, not the policy.** The stale V5.1 test bands
  assumed 50 ms ticks; V6 §W7-ARCHMAGE-03 (0.8–1.1 s Fang warning) and the Warden
  sweep prep (~0.7–1.0 s) are correct in policy (Archmage 18/24/12, Warden 18).
  Fixed the stale test bands/comments only (`ArchmageV5CombatPolicyTest`,
  `WardenV5CombatPolicyTest`, `Wave7SkeletonElusivenessPolicy`,
  `Wave7SkeletonElusivenessPolicyTest`, `Wave7AbilityEffectPolicy`,
  `Wave7AbilityEffectPolicyTest`). `VANISH_TICKS` stays aliased to the single
  live hide owner (`Wave7MarksmanInvisibilityPolicy`).
- **Probe-stub maintenance (no assertion weakened).** `tests/test_boss_edge_volley_runtime.py`
  and `tests/test_inferno_runtime_cadence.py` gained the stub declarations the new
  production code requires (`overloadFireRingSpentThresholds`,
  `infernalForgeSpentThresholds` sets, `infernalForgeVulnerableUntilTick`,
  `arenaInfernoRelocateIntervalTicks`, `arenaInfernoLastRelocateTick`, no-op
  `relocateInfernoSafeCells`).
- **26.3 client compile defect repaired.** `EndRiftBossBarHud.java:337`
  `possible lossy conversion from long to int` (`Math.min(0xFF, …)` with a long)
  → `(int) Math.min(0xFFL, …)`. The Phase 5 translucent-copy client path
  (`EchoEyesFeature` BODY-pass skip for copies, `EchoVanillaRenderer` copy-alpha
  map, `LivingEntityRendererMixin` translucent render type + tint,
  `EchoPresentationPacket` epoch decode) is committed inert until the server marks
  copies.
- **New regression test registered in the gate.**
  `tests/test_end_rift_echo_wire_contract.py` compiles and runs the real codec
  against the duration literals the production producers actually pass, asserts
  `0` for both, and re-proves that `2000` is refused. Added to the gate step
  "Wave 7 Echo presentation and no-loot probe".
- Evidence: full gate `tests\RunEndRiftEventChecks.ps1` → `GATE_EXIT=0`,
  "End Rift current local checks passed" (the previously red
  "Boss accepted hits, cast ownership and Kagune recovery" step now 145 passed,
  2 skipped; 270 pure policies passed). New wire test `→ 3 passed`;
  `Phase5EchoCopyPresentationPolicyTest → passed=1 failed=0`;
  26.3 client `compileJava → BUILD SUCCESSFUL`, `CLIENT_COMPILE_EXIT=0`.
  Native visual verification stays `NATIVE_BLOCKED`.

### 4.11 Dungeon in the authoritative gate, Phase 5 Echo producer, reproducible re-pin (2026-11-25)

- **Phase 5 translucent player-copy Echo producer (`85519c40`).** A RAGE copy
  carrier is bound to an `EchoPresentationState` view so the existing Wave 7
  Echo machinery renders the victim's armour/hands as a detached, semi-
  transparent `RemotePlayer`. Deduped `END_ECHO_BIND/STATE/REMOVE` frames keep
  the wire bounded; `retireEchoReplicaCopyView` always sends `REMOVE`, including
  on death/clear. `test_end_rift_echo_wire_contract.py` covers the producer as a
  third duration/epoch probe.
- **`copimine-dungeon` was entirely outside the authoritative gate.** The plugin
  has a real Bukkit-free core with 14 pytest contracts
  (`test_dungeon_command/domain/controllers/store.py`, driven by
  `tests/dungeon_probe.py`) and its own `build-plugin.ps1`, but
  `tests/RunEndRiftEventChecks.ps1` neither built nor tested it. Added the
  `Dungeon` first-party build and a `Dungeon builder, room, gate and store
  contract` pytest step. Local run: 14 passed; the plugin builds and stages
  `minecraft/server/plugins/CopiMineDungeon.jar`.
- **Client artifact re-pin after the Echo/P5 client work.** Two clean
  `clean build` runs of HEAD `85519c40` (pinned Temurin 25.0.4.1 / Gradle 9.8.1)
  produced byte-identical archives: **30 769 867 B, SHA-512
  `4be5f5e0…46b5ae01d4cf8`**. The previous pin (`30 766 985 B`, `b139c487…`)
  predated the Phase 5 copy client path, which fully explains the size/digest
  delta; no unrelated plugin checksum was touched. `profile.lock.json`, the
  `test_minecraft_migration_profile.py` literals and
  `docs/minecraft-26.3/README.md` are re-pinned together from that reproducible
  digest.
- Verification: `pytest test_minecraft_migration_profile.py + dungeon_*` →
  65 passed, 1 skipped.



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
| NATIVE visual acceptance on matched Paper 26.3 + Fabric 26.3 | — | owner playtest (F2/video from the deployed server) | NATIVE_BLOCKED (agent); owner capture pending |
| PROD deploy `copimine-game.service` | EndEvent + Dungeon jars; client pin `0.1.5+26.3` | prod RCON probes (`cmend status`, `inspect`, `cmdungeon status`) | DONE (2026-11-26) |

## 6. Blockers

- `NATIVE_BLOCKED`: no matched Minecraft 26.3 client/server graphics session has
  been performed by this agent. The production Paper 26.3 server and the owner's
  Prism client now run the matching artifacts, so the owner playtest is the native
  acceptance path; native F2/video evidence must come from that real run.
- `PROD` **performed 2026-11-26** (see 4.12): backup
  `20261125T213124Z`, atomic install, cold start, byte + health verification.
- §1 production-world block mutation stays BLOCKED (no approved site plan).

## 7. Next steps (ordered)

1. ~~Re-run the broad End Rift pytest sweep alone~~ **done**: each step executed
   serially and the authoritative gate finished `GATE_EXIT=0`.
2. ~~Re-run `tests/RunEndRiftEventChecks.ps1` alone to completion~~ **done**:
   Java pure-policy and persistence suites executed; EndEvent + Dungeon + client
   all green.
3. Native F2/video capture: the deployed Paper 26.3 server + the owner's Prism
   client now match; the owner playtest records the native visual acceptance
   (agent-side native capture remains `NATIVE_BLOCKED`).
4. Production-world block mutation stays `BLOCKED` without a location-bound
   approved site plan.
5. Owner playtest entry points: `cmend boss damage <n>`,
   `cmend boss spell <name>`, `cmend arena reveal-preview|reveal-test|reveal-stop`,
   `/cmdungeon` (editor wand + room definitions).
