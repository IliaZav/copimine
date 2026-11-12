# End Rift master rebuild — implementation and acceptance ledger

Authority: [verbatim user request](MASTER_REBUILD_CONTRACT.md), received
11 November 2026. SHA-256:
`4a99a2fe93d925d663f7673c6b988169bea27d13aa9a464b2881e4cfced0a654`.
Latest explicit decisions in that request override conflicting older designs.
Historical contracts and evidence remain intact.

## Baseline and ownership

- Checkout: `C:/Users/zavod/.codex/worktrees/rift-guardian-polish-20261110/integration-20261030`.
- Branch: `codex/rift-guardian-polish-20261110`.
- Starting HEAD and fetched GitHub branch: `3f2249402d3926f06a96ac1e8de02bc4d35c3fbb`.
- Tracked worktree clean at entry. Existing untracked artifacts, `.serena/`
  and `CopiMineClient/bin/` retained. No reset, cleanup or branch merge.
- Both Downloads canonicals re-read and hashes rechecked:
  waves `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf`;
  guardian `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50`.
- GitHub Actions run [42987075340](https://github.com/IliaZav/copimine/actions/runs/42987075340)
  on `36d34400`: `static-and-contract`, `migration-postgres-contract`,
  `java-plugins` all observed `completed/success`. `3f224940` records that result;
  it is not a claim of CI for new rebuild code.
- Earlier locally observed playtest repairs: pytest 3231 passed, 13 skipped;
  End Rift local gate passed; 369 client tests; 112 server bridge tests with one
  skip. These establish the previous implementation baseline, not acceptance
  of the new master contract.

## Explicit design supersessions

| Area | New controlling requirement |
| --- | --- |
| Wave 2 | Three finite groups, roughly 20-second mark rotation, roughly 5-second group gap; compatible ~70-observer field |
| Wave 5 | Uniform dark brown/violet fog inside and outside refuge; near-ground readability; bright guide fades 3–5 seconds after activation; roster/path capacity and Wither II/III |
| Wave 6 | Four one-impact pillars, then one three-impact pillar; seven impacts, final elite skeleton and full-squad completion |
| Boss movement | Mobile casts allow legal locomotion; stationary locks limited to authored heavy segments |
| Boss melee | Shorter basic cadence and combos with synchronized contact frames; final fury explicitly permits melee |
| Boss phases | Reversible real fractures/geysers, authored Inferno, two distinct Rage Echo systems; no boss skeleton towers |
| Last Seal | Six-to-eight large defenders die permanently; protected regen 0.2%/s, 5% total; separate up-to-30 killable small Kagune; transient spell constructs |
| Cocoon break | One durable animated fall/landing, permanent melee fury, 3x accepted incoming damage, no cocoon return |
| Release | Independently verifiable candidate; fresh explicit release authorization required for this rebuilt scope before production/client replacement |

## Milestones

| Milestone | Scope and proof | Status |
| --- | --- | --- |
| M0 | Preserve master request; audit repository/remote, earlier commits/assets/F2/logs and current acceptance gaps; retain exact baseline provenance | Baseline recorded; source reproduction continues per milestone |
| M1 | Projectile roster/admission consistency, finite atomic groups/cleanup, charge custody and signed burst reconciliation | Source repair and focused tests passed; isolated Paper/native acceptance pending |
| M2 | Waves 1–5, updated Wave 2 groups/layout, Wave 3/4 transitions, uniform fog/capacity/effect ownership | In progress: M2a–d source repairs and focused automated proof recorded below; Paper/native and remaining wave audit pending |
| M3 | Five Ram pillars/seven impacts/final skeleton, custom UI/model/audio, collision and completion/recovery | Pending; current three-impact encounter is superseded |
| M4 | Wave 7 topology ownership, 2/3 Marksmen, models/UV/animation, differentiated elite abilities | Existing repairs retained; master acceptance pending |
| M5 | Real accepted damage/hurt feedback/composite hitboxes, mobile/stationary cast ownership, responsive melee and group AI | Pending |
| M6 | Phases 1–5, authored spell/projectile models, reversible fractures/Inferno, delayed Echo attacks and 50-HP decoys | Pending |
| M7 | Cocoon/defenders/small enemies/transient spells, three-row gap ring, permanent fall/fury/death | Pending |
| M8 | Isolated Paper 2/5/10/11/20 scenarios, interruptions/restarts, native visual/audio/performance captures, review, full CI and candidate manifests | Pending; native acceptance incomplete |

Each milestone receives focused tests and a separate commit. An implementation
commit does not mark the milestone accepted: source, automated, isolated Paper,
Minecraft visual/audio, performance and official progression evidence are
separate fields. No unspecified native evidence is counted as a pass.

## Current reproducible M1 defect

`reserveWaveProjectileGroup` uses `max(1, activeLivingPlayers().size())`, while
`PressureBudgetController.configuredProjectileCap(1)` is zero and both ordinary
and elite group admission reject a one-player profile. A test wave can also use
the arena roster while `activeLivingPlayers` applies a stale official reward
roster. Fix the roster boundary; do not widen the global official admission
minimum or make a zero-player arena allocate projectiles.

## Safety and verification boundary

New gameplay and destructive/recovery scenarios run on an isolated local Paper
copy, never by resetting the production arena or live database. No credentials
or AuthMe passwords are copied to documentation. Native visual review follows
the master contract's real-frame requirements; missing access/evidence is
explicitly incomplete. No new production install or Prism replacement is part
of the M0/M1 implementation commits.

## M1 implementation evidence

The actual reservation adapter now selects the disposable wave roster or active
boss roster, then normalizes that roster through `WaveProjectileRosterPolicy`.
A one-player diagnostic and a legitimately admitted last survivor get the bounded
two-player projectile profile. Empty arenas and invalid one-player official
admission get zero. Global official admission and pressure caps remain strict.
Diagnostics and the scripted elite fan use the same normalized budget.

- Reproduced before repair: the real extracted reservation adapter rejected a
  three-arrow diagnostic volley with `cap=0`.
- New executable adapter regression: 9 passed, including stale official roster,
  boss precedence, last survivor, empty arena, cancellation and parties 2/5/10/11/20.
- Adjacent projectile/fog lifecycle tests: 120 passed (14 existing subprocess
  encoding warnings). The final two boundary cases also passed in the nine-case run.
- Gradle `bridgeServerTest endEventJar`: BUILD SUCCESSFUL.
- Inventory provenance: 142 passed, 3 host/environment skips. The early staging
  command used an incorrect script path; actual staging was performed and
  verified during M2c below, not claimed for this older artifact.
- Isolated Paper, live model survival, native visuals and new-commit CI: NOT RUN.
  M1 is not accepted as a complete gameplay/visual milestone.

## M2a — Wave 2 hunt and observer compatibility

11-November requirements supersede the 31-October collective mark and three-second
group gap. One eligible frozen-roster owner now rotates on the persisted hunt
origin every 20 seconds; each new group updates its target and field. A committed
phantom warning holds its valid owner until its callback resolves. Owner death,
quit and leaving the arena release owned glow and private packets; outsiders
never expand the roster. Every assignment has a newer fenced packet identity.

Server and client now support the same 70-observer target and 50-position safe
fallback. Resolved positions are checked again against the field radius after
block-centre adjustment; fallback layouts remain cached within that identity.
No physical phantom entity population was added. Group gap is five seconds.

Evidence:
- Actual codec/cache integration failed before repair: server emitted 35.
- Extracted production adapter failed before repair: all players were marked.
- Wave 2-specific suites: 67 passed.
- Wave 2/4 adapters plus wire/scatter: 67 passed after final fixes.
- Three executable Java mains passed: hunt policy (1/2/5/10/11/20 parties),
  stage policy and server placement policy.
- Gradle `bridgeServerTest endEventJar`: BUILD SUCCESSFUL.
- Native phantom/materialisation/music appearance and isolated Paper: NOT RUN.
  This is source/automated progress within M2, not complete M2 acceptance.

M2a plugin candidate: SHA-256 `77918e4b438c4a6865553c849b54adf8ebb730e0a313bc4698d02c35402d32c7`, 2284050 bytes.

## M2b — Uniform depth-based fog presentation

The 11-November visual contract supersedes shader-free clear refuge chambers.
The active 26.3 runtime now applies the same world-depth volume everywhere in
its signed arena. It no longer samples an occupied emerald box, skips the pass
inside it, or submits opaque chamber-wall geometry. The retired renderer's
compatibility API returns no chamber; model, resource and user-shader state are
untouched. The uniform-buffer layout stays compatible.

The GLSL distance envelope preserves nearby silhouettes and a 2–3-block ground
patch, with dense brown/violet extinction farther away. The actual extracted
shader envelope is executed by a numerical optics regression; this is not native
visual proof. Owned world particles now mix ash, smoke, soul wisps, sparse violet
sparks and slower clusters under the existing 256-particle cap. Refuge cells
use the same particle rules. Large smoke sprites avoid the near-camera patch.
Activation admits the effect immediately with a 300 ms onset; repeated and late
leases retain their original clock. The existing clear/suspend fences remain.

Evidence:
- New uniform-volume and onset regressions failed against the old runtime.
- Focused lifecycle/optics/smoke tests: 17 passed.
- Fog-related suites: 106 passed, one historical clear-chamber assertion found
  and explicitly superseded; adapter/profile/CI-contract rerun: 116 passed,
  one environment skip.
- Active client: 369 tests, zero skips/failures; `test build` passed.
- A clean rebuild reproduced the identical candidate SHA-512.
- Client candidate: 15,011,288 B; SHA-256
  `3fceed08546b15c4d41edce23619662ed62ce9fb8f41210114f988a463711430`;
  SHA-512 `86c5dd5d372a121781e8cfbfb8474f4c753a04e0016c7e651f1f7001e4e6b324ef796c10cd4dc298d01f5427e983a70b6dc91c6f789f668fe2f44d0fe02e6837`.
- Native Minecraft image/audio/FPS verification: NOT PERFORMED.
- Remaining M2 fog work includes timed luminous guidance through dense fog,
  roster-capacity/walkable-path admission, Wither II/III exposure and acceptance.
  This commit is not a declaration that Wave 5 or M2 is complete.

## M2c — Refuge admission, walking time and owned Wither

Reproduced the old two-player late cycle: one zone of one cell for two owners.
Footprints now have a frozen-party capacity floor, so side and zone count still
shrink monotonically without leaving fewer cells than players. An eligible-roster
increase during SAFE refreshes insufficient capacity before the hazard starts.

`BlackFogRefugeAccessPolicy` samples a bounded walkable ground grid once, finds
real routes around obstacles and uses minimax matching to allocate distinct
reachable cells. The resulting lead includes slowed travel at a conservative
2.8 blocks/s and 1.2 s reaction margin, capped at 20 s. Disconnected routes,
insufficient reachable capacity and excessive paths refuse activation before
journal preparation or block mutation. The adapter rechecks routes after the
initial telegraph and extends the SAFE deadline when the route needs more time.
This conservative flat-floor graph does not promise access across unverified
vertical steps; actual arena gameplay acceptance remains pending.

Exposure now grants short owned Wither II (up to ten players) or III (11–20),
plus Slowness II. The old direct-damage path is removed. Refuge entry releases
both owned leases before the next one-second cadence; stronger/unrelated effects
retain the existing lease and restoration protections.

Evidence:
- Actual capacity and exposure regressions failed before repair.
- Actual adapter, walking/matching, exposure and lease-boundary selection: 32 passed.
- Full fog/Wave-5 Python selection after superseding old exclusion assertions:
  115 passed, zero failures.
- Three Java mains passed: footprint, capacity and effect-lease policies.
- Gradle `bridgeServerTest endEventJar`: BUILD SUCCESSFUL.
- Correct staging command: `python scripts/minecraft/install_migration_server_plugins.py --stage-only`:
  `Verified 30 pinned plugin candidates`, exit 0. Only repository candidate staging.
- Inventory/provenance after staging: 142 passed, 3 skips (Windows symlink privilege;
  isolated 26.3 runtime absent; ProtocolLib runtime candidate absent).
- Plugin candidate: 2,290,935 B; SHA-256
  `9cf214801ad156c797eb97b9a3e2cd5fdbbf2f9e804eb2232f9e7f696faecad2`.
- Real Paper survivability, native visuals, FPS/TPS and player acceptance: NOT RUN.

GitHub CI for M2b commit `cd3b81ca`: run `43023667606` observed in progress;
PostgreSQL contract job succeeded, other required jobs have not yet passed.
M2c is not deployed; M2 is still incomplete, including timed luminous guidance.

## M2d — Timed luminous refuge guides

Guides remain bright for 3.5 seconds after activation and fade smoothly to zero
at five seconds. Server beam/perimeter emission follows this finite window.
The client depth-volume pass adds bounded world-space luminous guide columns:
up to eight nearest signed, current-event/current-generation markers in the
arena. True scene depth still occludes them; fog extinction does not erase the
guide before players can react. The optical integration remains bounded at
7.5 blocks, while guide visibility uses a separate 128-block scene-depth cap.
Ordinary world beams and particles remain real arena presentation.

An existing lookup used `startsWith("wave5-safe-zone-")` on full scoped instance
IDs such as `event:2:world:wave5-safe-zone-0`. Corrected key recognition is now
exercised through the actual world-VFX manager; old events/generations cannot
supply floor or guide coordinates to a new fog volume. All eight guide uniforms
are included in the parsed post-chain config and checked against buffer size.

Evidence:
- Timing/server-client compatibility, optics and lifecycle selection: 18 passed.
- Integrated fog/profile/plugin/inventory/CI-contract selection: 353 passed,
  4 environment skips.
- After the guide-range adjustment, profile/CI-contract/timing rerun:
  96 passed, 1 environment skip.
- Active client: 370 tests, zero failures/skips; GLSL compiled with shaderc;
  Minecraft post-chain codec accepted the 22-vector uniform block.
- Client `test build` and subsequent `clean build` produced identical SHA-512.
- Server `bridgeServerTest endEventJar`: BUILD SUCCESSFUL.
- Candidate installer: 30 pinned plugin candidates verified in repository staging.
- Current plugin: 2,291,276 B / SHA-256
  `ca84f018a5c6629cf2956f5b9e39226d5e1310b0f27c7e944259a921c89fe4d5`.
- Current client: 15,013,727 B / SHA-256
  `ac0a8e81961ba76c9f25216d8329b4addc6e5218bff461bb3dd483a30b75c829`;
  SHA-512 `10b49e7b4f7cfc677d9d7e643676a78be5a5cd0a86250a456cbf204debbe5c63fcbec8d74c15d991ac04fdc7f6dddc415a736c6b5ab72941a307c545eeed6862`.
- Native image/audio/performance and real Paper gameplay acceptance: NOT RUN.
  M2 remains in progress; Waves 1/3/4 and cross-wave acceptance still need review.

GitHub run `43023667606` on M2b `cd3b81ca`: `static-and-contract` and
`migration-postgres-contract` observed completed/success; `java-plugins` still
in progress at this entry. This does not establish CI for subsequent commits.

## Integrated M1/M2 source verification at `2ade9718`

No gameplay source changed while these checks ran.

| Check | Observed result |
| --- | --- |
| Full Python suite | 3252 passed, 14 skipped, 124 warnings, 2 subtests passed; 629.99 s, exit 0 |
| End Rift common gate with pinned Java 25 / legacy Java 21 and `-SkipBuilds` | `End Rift current local checks passed.`, exit 0 |
| Independent CodeRabbit CLI 0.8.2, base `3f224940` to `2ade9718` | completed, 0 issues, exit 0; receipt in `M2_REVIEW_RECEIPT.json` |
| Exact-head GitHub CI | run `43028501404` started for `2ade9718`, incomplete at this entry |
| Earlier M2b CI | run `43023667606`: static and PostgreSQL success, Java job still in progress |
| Isolated Paper / real Minecraft / FPS/TPS | NOT PERFORMED; no visual/gameplay acceptance claimed |

Full-suite skips are environment/platform limitations, including missing local
isolated runtime and ProtocolLib candidate, pinned Java-21 Grim toolchain not
selected for that Python process, Windows symlink privilege and POSIX permissions.
No skip is counted as runtime or native success.

Local Paper preparation audit: the older reference runtime has an `Arena` world
folder but its current EndEvent config names `CopiMine`; do not blindly copy it
and claim it represents the production arena. PostgreSQL listens only on loopback
55434; no Minecraft client/server was active at this observation. Existing DB,
world and private environment files remain unchanged.
## M1 tail — Wave 1 delivery durability (11 November, second pass)

The delivery path advanced the score and owed a reinforcement pack before the
state reached disk. A failed write therefore consumed the charge and moved the
objective with nothing durable behind it. The accepted delivery now retires its
inventory token, commits the owed pack, saves synchronously, and only then
consumes the physical item; a failed checkpoint rolls holder, counters, token
identity and owed debt back and fences recovery. Ending the objective retires
the specific accepted token instead of whatever token is current, so a
concurrent rebind cannot be purged by another holder's transition.

Evidence: the detached inventory probe gained a `delivery-save-failure`
scenario that fails when the rollback is removed. 31 passed. The stale probe
stubs for the two new helpers were repaired in the same commit.
Commit `9230e5a9`.

## M3 — Wave 6 pillar roster and the final-pillar marksman

The encounter was one pillar hit three times. It is now four ordinary pillars,
each spent by a single Ram collision, followed by one oversized final pillar
that absorbs three and cracks at each one: seven accepted impacts in total.

- `MINOR_PILLARS` (4) + `FINAL_PILLAR_IMPACTS` (3) derive `REQUIRED_IMPACTS`
  (7), `ARMOUR_PLATES`, `PILLAR_COUNT` and `ELITE_SKELETON_PILLAR`, so the
  pillar order, plates, boss bar and damage gate cannot drift apart.
- The runtime retires a pillar only when that pillar is spent. `pillarImpacts`
  tracks what the standing pillar absorbed, separately from the encounter-wide
  count, resets when a pillar rises, and round-trips through the checkpoint, so
  a restart resumes the finale at its real damage stage.
- A surviving collision is now visible: the pillar sinks, narrows, sheds rubble
  and exposes a magma core from the second collision. The final pillar is built
  at roughly double footprint and height with a glowing core band.
- `WaveSixPillarSkeletonPolicy` is the only skeleton-on-a-pillar mechanic in the
  event: two distinguishable volleys on independent cooldowns, normalised and
  symmetric fan directions, no fire without a live target, a bounded patrol so
  it never becomes a static turret, a floor drop that happens exactly once for a
  survivor, and a refusal to complete while a skeleton is still up on a
  destroyed pillar.

Retired single-pillar assertions in `test_rift_ram_five_pillars.py`,
`test_rift_ram_runtime.py` and `test_rift_ram_wave_six_rift_ram.py` are marked
SUPERSEDED in place with the reasoning.

Evidence: `test_rift_ram_pillar_roster.py` and `test_wave6_pillar_skeleton.py`
are new; the Rift Ram family is 10 passed. Ten mutation checks across both
guarantees were caught with no survivors (final pillar made one-hit, three
ordinary pillars, pillar retired on every impact, counter never reset, firing
without a target, dropped cooldown, never repositioning, early drop, firing from
a degenerate aim, orphan guard ignored). Wave 6 selection: 337 passed.
Commits `d5e5767a`, `4dec80ad`.

Not verified: the runtime half is exercised through extracted production
methods, because no local Paper API jar is present, so a full javac of
`RiftRamEncounter` did not run here. No native Minecraft capture was taken.
## M5-M7 — Rift Guardian combat, phases and the Kagune finale

### The freeze, and why nothing caught it

`holdBossCastPosition()` stopped pathfinding and cleared AI and awareness for the
whole of every cast, and `reconcileBossCastProjection()` closed casts without
restoring them. One cast was survivable; a fight was not — the Guardian ended up
permanently immobile, which is precisely the "boss just stands there" complaint.
Mobile casts now never suppress locomotion, stationary casts lock only their own
authored window, and `releaseBossCastMobility()` runs on BOTH terminal branches of
reconciliation, so a dropped scheduler callback cannot strand the body. Melee
cadence moved from a frozen 8 s with a blanket LAST_SEAL ban to 2.4 s tightening
to 1.8 s, permitted in every real phase, with a 600 ms retry instead of forfeiting
a full cadence. `BossHitClassificationPolicy` is now the single authority on
damage acceptance: an accepted hit is the only source of the red flash and the
only thing that reaches real HP; a blocked hit shows a shield impact with zero
damage and no flesh flash.

### The compile gap, closed

The python probes compile EXTRACTED method bodies against hand-written stubs. They
are blind to structure: adding two methods to the RiftRam Host interface left its
implementation unable to compile while every probe stayed green. That is not
hypothetical — it happened on this branch and M5 found it, not a gate.

`scripts/minecraft/RunFullTreeCompile.ps1` now compiles every source the plugin
needs against the real pinned Paper 1.21.1 API with JDK 25: 384 sources, 1057
classes, exit 0. It judges javac by exit status and `error:` lines only, so a
deprecation warning cannot masquerade as a pass or a failure. The earlier belief
that only extracted methods were checkable was wrong; JDK 25 and the pinned jar
are both present locally.

### Policy layers delivered, and the gap they leave

Delivered with real behavioural tests and mutation evidence:
`RiftFractureEruptionPolicy` (14/14 mutants killed), `InfernoArenaLifecyclePolicy`
(13 killed, 1 honestly reported equivalent), `EchoAttackReplicaPolicy` (13/13),
`EchoDecoyPolicy` (16/16), `BossSummonAdmissionPolicy` (4 killed, 1 equivalent),
`KaguneRingGapPolicy`/`KaguneRingCastPolicy` (2 mutants caught),
`KaguneCocoonGuardianPolicy`, `KaguneGuardianFightPolicy`, `GuardianDeathLedger`,
`CocoonFallPolicy`, `SmallKaguneSpawnPolicy`.

**Only GuardianRegenerationPolicy of the cocoon set is actually called by the
monolith.** The rest are pure policy with nothing invoking them, so their
guarantees are not live in play. `test_end_rift_cocoon_contract` reports this
rather than hiding it: it xfails with the exact list of unwired policies, and
`STRICT_COCOON_WIRING=1` turns it into a hard failure. Verified both ways.

### Probes corrected rather than accommodated

Four probes broke on the unfreeze. Three needed a `releaseBossCastMobility` stub —
the documented "new monolith method needs a stub in every probe" trap. The fourth
is the interesting one: `test_boss_cast_position_adapter` asserted that every cast
stops native pursuit and clears AI, which WAS the freeze. It now asserts the new
split, and two mutations (all-spells-mobile, all-spells-stationary) fail it.

`test_small_kagune_strike_adapter` pinned the retired six-tentacle summon cap that
contract 18 replaced. Restoring it would have re-imposed removed behaviour, so it
now checks what still holds: a valid LAST_SEAL summon produces actors, never
exceeds thirty, and survives grab trimming.

### Not verified

Policies are not wired for phases 3-5 or the cocoon. Real BlockData round-trips,
geyser collision against the rendered column, Bukkit entity lifecycle for Echoes
and decoys, client-rendered red flash, melee feel, and freeze recovery under a
lost scheduler tick all need a live server or a real client. None is claimed.
### Full-suite state at this point

`python -m pytest tests/ -q` — 3273 passed, 18 skipped, 1 xfailed, 4 failed.

All four failures were resolved as follows rather than left as noise:

- Two Wave 1 probes extracted a method that had become a delegate, and asserted
  the pre-checkpoint call signature. Both are repaired: the probe now extracts
  both overloads, and the delivery assertion is token-scoped, which is a
  strictly tighter guarantee than the one it replaced.
- Two packager tests fail ONLY because this machine has
  `Get-ExecutionPolicy = Restricted`: they shell out to
  `powershell -NoProfile -File <temp script>`, which the local policy blocks.
  The test file is unmodified from HEAD, CI invokes PowerShell with
  `-ExecutionPolicy Bypass`, and running the same tests with
  `PSExecutionPolicyPreference=Bypass` gives 2 passed. This is an environment
  limitation, not a defect, and it is recorded here rather than silently
  tolerated.

Full-tree compile gate: 384 sources, 1057 classes, exit 0.
### Wiring the cocoon policies into play

The previous ledger entry was blunt about twelve policies with zero call sites.
Five commits now connect them, each with mutation evidence, and the process
turned up a real gameplay defect that no policy test could have found.

**Small Kagune (contract 18.1).** The batch was
`min(policy.admission(...), MAX_TEMPORARY - temporaryCount())`. A real enemy
spawn was being clamped by the shared six-slot budget that governs transient
tentacle visuals, so a "real enemy type" could never exceed a cast visual's
budget. The call site now asks `SmallKaguneSpawnPolicy.admission` directly.
Two things were already right and are now pinned rather than assumed:
`trimTentacles` excludes SMALL from the temporary prune, and
`SmallKagunePolicy.expired` is hard-false so nothing retires on a timer.

**Guardian death ledger (contract 17.1).** A killed defender still received a
90-second respawn deadline, so the cocoon refilled and the final fight could
never become permanent. Three call sites now use `GuardianDeathLedger`: a real
kill records the slot durably; the kill drops that slot's deadline instead of
scheduling a new one; and `ensurePermanentTentacles` refuses any slot the
ledger reports destroyed - which is what makes "no respawn" a property of the
arena rather than of a deadline that merely happens to be far away.

**Cocoon break (contract 17.3).** Nothing recorded the break itself, so a
restarted generation could rebuild defenders and re-shield a boss the party had
already opened. `commitTentacleGuardianDeathWindow` marks it durably, and
`isLivingTentacleGuardian` refuses a destroyed slot - the subtle one, because
without it a stale display adopted after a restart counts as a living defender
and the shield silently returns.

**Regeneration (contract 17.4).** The gate asked only whether defenders exist
RIGHT NOW. The contract asks a historical question - was the cocoon ever broken?
`guardianDeaths.cocoonBroken()` answers that and is durable.

**Cocoon count (contract 17.1) - a real defect found while wiring.** The live
count came from the retired ladder: 2 defenders for a duo, 3 for four, 4 for
seven, 5 for ten. The contract asks for six to eight. For every party below
eleven players the cocoon simply did not exist, and a duo broke the seal by
killing one pair. The count now comes from `KaguneCocoonGuardianPolicy.countFor`.

One assertion had to change rather than be accommodated:
`test_guardian_respawn_recovery_adapter` asserted
`permanentTentacleTarget() == 5` for ten players. It was checking the right
thing - the count follows the ADMITTED roster, not the two players still online -
so it now asserts seven, with the reason recorded instead of the number quietly
edited.

Three more probes needed stubs and/or sources for the new field. That trap has
now cost time four times on this branch; every probe enumerates its own sources
by hand.

### Every remaining policy now has a live call site

Seven more policies were connected after the cocoon work. Each one had been
complete, tested, and referenced by nothing - the contract described a mechanic
that the game never performed.

| Policy | What it makes happen | Where |
| --- | --- | --- |
| `RiftFractureEruptionPolicy` | Phase 3 fractures mutate real floor blocks through the hazard journal, erupt an 8-12 block geyser that IS the damage volume, then restore the exact original BlockData | `startCurrentRiftFractures` |
| `InfernoArenaLifecyclePolicy` | Phase 4 becomes a real inferno: heating wave outward, cooling patches, exact restore | `arenaInferno` |
| `EchoDecoyPolicy` | Phase 5 RAGE summons 50 HP boss decoys instead of two spiders | `summonServants` -> `summonEchoDecoys` |
| `EchoAttackReplicaPolicy` | Phase 5 delayed spectral attack copies with a locked aim and two separate damage ledgers | `startEchoAttackReplicas` |
| `KaguneRingGapPolicy` / `KaguneRingCastPolicy` | The three-row Kagune ring with real two-block escape gaps and one damage event per player per cast | `startKaguneRingCast` |
| `CocoonFallPolicy` | The Guardian actually descends, lands once, and stays in melee fury | `beginCocoonFall` / `tickCocoonFall` |
| `BossSummonAdmissionPolicy` | No skeleton tower is ever admitted in a boss phase | `summonServants` |

### Two defects that only wiring could reveal

**The fall was invisible.** The boss is pinned to a hover anchor above the Core
every tick. A correctly computed descent running alongside that pin is
overwritten immediately, so the player never sees the boss come down. The fall
now runs BEFORE the anchor is applied and owns the position until melee fury.

**The inferno cooled down early.** The stage was built as
`duration - prepare - heat - cooling`, and `Stage.phase()` starts COOLING at
`burningEnd()`, so a 120-tick cast began cooling at tick 81. The window now
fills the requested duration.

### Two tests that passed for the wrong reason

Both were found by mutation, not by review, and both are recorded here because
"the test exists" and "the test bites" are different claims:

- The Kagune ring test asserted that `kaguneRingCast.claim(...)` appeared. A
  mutant that called it and ignored the boolean - letting three rows stack onto
  one player - passed. The assertion now requires an if-gate with `continue`.
- The attack replica test asserted that `strikeConnects(...)` appeared. A mutant
  replacing it with `true` - invisible homing, the exact failure contract 15.2
  forbids - passed. The assertion now requires the damage to live inside the
  branch.

### Known limitation carried forward

The client has still not been run. Nothing in this ledger claims a visual
verification. The geyser's fire, the inferno's readability, the ring's wall of
Kagune, the decoy's readability against the real boss, and the descent's timing
are all unverified by eye.

## The installed client is not the build this branch produces

The one visual claim this session can make is negative, and it is a real
finding rather than a caveat.

The user's playtest screenshots of the Wave 5 fog were measured instead of
described. `2026-11-10_23.31.34_3.png` samples `rgb=(16,24,31)` on the upper
field: blue leads red by **+15**. The fog source in this branch renders violet,
whose soul highlight `(0.055,0.018,0.075)` gives **+5**. The installed jar
contains `soul * vec3(0.015,0.080,0.090)` - **+19**, a cold teal. The screenshot
matches the installed jar, not the source.

So the fog the user photographed is not the fog this branch draws, and the
difference is exactly the 15 entries of the Wave 5 fog system:

```
DIFFERS  me/copimine/client/BlackFogVolumePolicy.class
DIFFERS  me/copimine/client/BlackFogVolumeRuntime.class (+ \$Chamber)
DIFFERS  me/copimine/client/BlackFogChamberRuntime.class
DIFFERS  me/copimine/client/BlackFogSmokeRuntime.class
DIFFERS  me/copimine/client/EndEventBlackFogManager.class
DIFFERS  me/copimine/client/EndEventWorldVfxManager.class (+ \$1, \$Beam, \$BeamSnapshot)
DIFFERS  me/copimine/client/mixin/BlackFogPostPassMixin.class
DIFFERS  me/copimine/client/CopiMineClient.class
DIFFERS  assets/copimineclient/shaders/post/wave5_volume.fsh
DIFFERS  assets/copimineclient/post_effect/wave5_volume.json
MISSING  me/copimine/client/BlackFogGuidePolicy.class
```

`BlackFogGuidePolicy` - the 3-5 second luminous refuge guide of contract 6.4 -
does not exist in the installed client at all. That is not a rendering
regression the user misread; the class is absent.

### The build itself is sound

Rebuilding reproduces the pin exactly, so the release candidate is internally
consistent and only the installed copy is stale:

| | bytes | sha512 |
|---|---|---|
| pinned, and rebuilt | 15013727 | `10b49e7b...eeed6862` |
| installed in Prism | 15013152 | `4810de65...8fb8466e0` |

`preserveFileTimestamps = false` and `reproducibleFileOrder = true` are both set,
and two independent builds produced the same digest. The artifacts directory
`guardian-polish-final` is older still: its manifest names `a60b758b`, five
commits behind HEAD, and pins the 15013152 client.

Replacing the installed jar was **not** done. It is the user's client, and the
assignment forbids overwriting it without explicit authorisation. The exact
command is in the report.

### Why no test caught the drift

The suite pins the fog's *structure* - uniform density, no safe-cell exemption,
depth reconstruction - but never its *hue*. Two palettes that are both "dark"
were therefore both acceptable, and a build carrying the wrong one shipped.
`test_the_fog_is_violet_black_not_a_cold_teal_screen` now reads the real ink
palette out of the shader and refuses one where blue leads red. Restoring the
stale palette makes it fail with `assert (0.09 - 0.015) <= 0.06`.

The general lesson, and the third instance this session: a test that constrains
shape without constraining appearance cannot tell a correct build from a
plausible wrong one.

## A test that was red for a reason unrelated to its subject

`test_direct_packager_preserves_gradle_candidate` launched `powershell.exe`
without `-ExecutionPolicy Bypass`. Windows PowerShell defaults to Restricted, so
the child refused to load the generated script and the assertion surfaced
"scripts are disabled on this system" as a packaging failure. The guard was
never broken; it was never executed.

The bypass is now requested only when the launcher is Windows PowerShell, since
`pwsh` has no such policy. Reintroducing the regression the guard exists to
prevent - a direct packager copying over the verified Java 25 candidate - fails
both parametrisations, so the test now genuinely runs and genuinely bites.

## A build that failed and a shell that said it worked

CI failed twice on the same assertion, at `2ade9718` and again at the tip:

```
ValueError: Local plugin artifact SHA-256 differs from its lock: CopiMineEndEvent
expected=ca84f018a5c6629cf2956f5b9e39226d5e1310b0f27c7e944259a921c89fe4d5
actual  =b1e95954e30c38f7e4dbee4590337fb9325b3a1c76adb19e9d5a7b95a115d891
```

The lock was last written at `2ade9718`. Everything after it — the Wave 6/7
domain, the Guardian rebuild, five thousand lines across twenty-two files —
changed what the plugin compiles to, and nobody re-pinned. The lock was stale.
CI was right.

What makes this worth a section is how nearly I concluded the opposite.

My first local check ran `gradle :plugins:jar`. That task does not exist in this
project; the real one is `endEventJar`. Gradle printed

```
* What went wrong:
  Cannot locate tasks that match ':endEvent:jar' as project 'endEvent' not found
BUILD FAILED in 818ms
```

and my loop read the jar already sitting in `build/minecraft-26.3/plugins/jars/`
— a leftover from an earlier build — computed `ca84f018`, and compared it to a
lock that also said `ca84f018`. Match. "The lock is fine; CI's environment must
differ." That conclusion was wrong, and it was comfortable.

The evidence that killed it was simple: I listed the classes inside the jar I
had just "verified" and asked whether it contained the code under test.

```
KaguneRingGapPolicy         : False
InfernoArenaLifecyclePolicy : False
EchoDecoyPolicy             : False
CocoonFallPolicy            : False
SmallKaguneSpawnPolicy      : False
GuardianDeathLedger         : False
RiftFractureEruptionPolicy  : False
```

A jar missing every policy it is supposed to ship is not a candidate. It is a
leftover. With the correct task the build produced exactly `b1e95954` and
2382357 bytes — byte for byte what CI had computed, from a completely different
machine. The build was reproducible all along; my check was not.

Two rules come out of this, and both are the same rule twice:

1. A hash comparison proves nothing unless you know the artifact was built from
   the source you are testing. Assert presence of something only the new code
   produces before trusting the digest.
2. A failed subprocess in a shell loop does not stop the loop. Check the exit
   code, or better, don't reason from files a failed command was supposed to
   rewrite.

There is a third, smaller one. Two jars here turned out to be *both* correct:
the `build-plugin.ps1` output (2119437 bytes, 860 entries) and the Gradle
candidate (2382357, 874). They differed only by fourteen directory entries —
`me/`, `me/copimine/`, and so on — with identical class sets. Directory records
are not content. The RC now carries the Gradle jar, because that is the one the
lock pins and the one CI reproduces, but the earlier jar was never broken; it
was a different packager's idea of the same bytes.

## What the repin touched

```
tools/minecraft-26.3/server-plugins.lock.json   candidate sha256, sha512, size
tests/test_minecraft_migration_server_plugins.py replaceSha256, candidate table
artifacts/rc-0f9b69c8/manifest.json             plugin entry + sourceCommit
artifacts/rc-0f9b69c8/README.md                 the hash table
```

The old `ca84f018` stays in `replaceSha256`. That list is how the installer
recognises a legitimate predecessor rather than a foreign file dropped into the
plugins directory, and the previous candidate is exactly that.

`tests/test_minecraft_migration_server_plugins.py`: 137 passed, 4 skipped.

## A red run that was not our fault

Run `43092554243` at `97b8e43f` finished red with two failing steps:

```
[failure] Publish Python test report
[failure] Upload CI diagnostics
```

Neither is a test. Both are `actions/upload-artifact`, and both failed the same
way:

```
Attempt 1 of 5 failed with error: Unexpected token '<', "<!DOCTYPE "... is not valid JSON.
...
##[error]Failed to CreateArtifact: Failed to make request after 5 attempts
```

The artifact API answered with an HTML error page five times running. That is
GitHub being unavailable, not a defect in this branch.

The tests in the same job passed:

```
3361 passed, 10 skipped, 124 warnings, 2 subtests passed in 521.15s
```

Recording it because a red X on a commit is the kind of thing that gets read as
"the build is broken" a week later, and the difference between "our test failed"
and "their upload endpoint was down" is invisible unless someone writes it down.
The run was re-triggered with `gh run rerun --failed`, which re-executes only the
two upload steps.

Worth pairing with the earlier entry: the first two red runs on this branch were
genuinely ours - a stale plugin lock - and this third one is not. Both looked
identical from the run list. The way to tell them apart is to open the job and
read which step failed, not to look at the badge.

## Phase 5 Echo: the audit is right, and checked

PHASE5_RUNTIME_AUDIT lists seven findings. I re-derived the load-bearing ones
from the source rather than taking them on trust, and three of them are real.

**The decoys cannot be summoned.** `BossPhasePolicy.spellPool(RAGE)` reads:

```
case RAGE -> List.of(VOID_BLAST, RIFT_PROJECTILE, RIFT_ARROWS, ARENA_INFERNO,
                     BOSS_FIREBALL, MELEE_SWIPE, CHEST_STRIKE, GROUND_SLAM);
```

`SUMMON_SERVANTS` appears in `RIFT` and `OVERLOAD` and nowhere else. The new
decoy summon hangs off `summonServants`, which is dispatched by that spell. So
`summonEchoDecoys` is unreachable for the whole of Rage - the phase it was
written for. Eight spells, none of them the one that matters.

**The attack replicas are invisible.** `startEchoAttackReplicas` puts a UUID, a
target lock and a timeline into three maps:

```
echoReplicaTimelines.put(copyId, timeline);
echoReplicaLocks.put(copyId, new TargetLock(...));
echoReplicaDamageLedger.put(copyId, new LinkedHashSet<>());
```

No entity, no display, no model binding. Nothing is ever made visible. The
ticking method then deals damage against those timings. That is invisible
damage, which is the exact failure the contract names in 23.3.

**And it cannot tick anyway.** The only caller of `tickEchoAttackReplicas` sits
inside `pinBossToFinalSealCore`, below a LAST_SEAL gate. Replicas start at entry
to RAGE and would never advance during it.

**The entry call is refused before it begins.** Line 45857 passes `VOID_BLAST`;
`EchoAttackReplicaPolicy` line 47 accepts only

```java
"MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM"
```

so `timeline(...)` returns null and the replica is discarded on the first
iteration. Three separate reasons the feature does nothing, any one sufficient.

This is worth stating plainly because the tests are green. There are policy
tests for the replica timings, the decoy cap, the 50 HP and the cooldown, and
they pass - as they should, the policies are correct. What no test asks is
whether anything calls them from a phase that runs, or whether an actor exists
to be seen. A correct policy behind an unreachable call site is indistinguishable
from a working feature to a test suite that only reads the policy.

The audit's own conclusion is the right one, and I am adopting it: this needs a
real slice, not a one-line unblock. Moving the tick call alone would start
invisible damage on live players, which is worse than the current nothing.

Recorded as unfinished. It is not claimed working.

## What the screenshots actually contain, measured

The only Minecraft screenshots in the Prism instance are eight PNGs from
10 November, 23:28 to 23:35 — the playtest that prompted this work. Nothing
captured since. So every visual claim in this ledger about waves, fog, spells or
the boss stands on code and automated tests, not on a single pixel anyone has
looked at.

I sampled those eight numerically rather than describing them. Method: load the
PNG, convert to RGB, average each of three horizontal bands on a 7-pixel stride,
report the mean per channel and the blue-minus-red difference.

```
2026-11-10_23.31.34_3.png   1920x1080
  top     rgb=(17, 25, 32)   blue-red  +15
  mid     rgb=(56, 49, 46)   blue-red  -10
  bottom  rgb=(100, 82, 66)  blue-red  -34

2026-11-10_23.35.27.png     1920x1080
  top     rgb=(16, 22, 27)   blue-red  +11
  mid     rgb=(94, 77, 65)   blue-red  -29
  bottom  rgb=(124, 104, 81) blue-red  -43
```

A positive blue-red at the top of frame means the fog reads blue-teal. The
corrected palette in `wave5_volume.fsh` is a dark violet:

```glsl
vec3 ink = mix(vec3(0.015,0.009,0.017), vec3(0.052,0.032,0.046), eddy*eddy);
ink += soul * vec3(0.055, 0.018, 0.075);
```

That soul term scores blue-red `0.075 - 0.055 = +0.020`, and the base sits at
`0.046 - 0.052 = -0.006`. The stale teal it replaced was
`soul * vec3(0.015, 0.080, 0.090)`, scoring `0.090 - 0.015 = +0.075` — nearly
four times bluer. The shots measure +15/255 ≈ +0.059, which is the stale
palette, not the corrected one.

That is consistent with the independent finding that the installed Prism client
is a different build from the pinned one: 564 entries against 565, fifteen
entries differing, all Wave 5 fog, and `BlackFogGuidePolicy.class` absent
entirely. The player was looking at an older client.

Two conclusions worth keeping separate:

1. The source now holds the violet palette, verified by reading the shader.
2. No screenshot anywhere shows the violet palette running. Whether the fix
   actually looks right is unverified, and this ledger does not claim otherwise.

The measurement also rules out a specific failure the contract forbids: the fog
is not "a black screen". The bottom band reads `(100, 82, 66)` — warm, bright,
and roughly 4.4x the luminance of the top band. Near-floor geometry is plainly
visible. That much, the screenshots do establish.

## The Echo fix was only half a fix

The Phase 5 Echo mechanics were inert for four independent reasons, each enough
on its own. They are now repaired:

- `spellPool(RAGE)` did not offer `SUMMON_SERVANTS`, the only spell that
  dispatches `summonServants`, so the decoys were unreachable in the phase that
  owns them.
- `tickEchoAttackReplicas` was called only inside `pinBossToFinalSealCore`,
  below a LAST_SEAL gate, so copies started at Rage entry never advanced.
- `startEchoAttackReplicas` recorded a UUID, a target lock and a timeline but
  spawned no entity and published no model - while the tick dealt real damage.
  Invisible damage, which contract 23.3 forbids outright.
- The Rage entry passed `VOID_BLAST`, a family the policy refuses, so every
  copy was discarded on the first iteration.

The repairs are structural rather than cosmetic: separate event kinds
(`ECHO_DECOY`, `ECHO_REPLICA`), a real Enderman actor spawned before the copy
is registered, `damageAllowed` consulted in both the strike and the explosion
branch, and explicit Rage and generation fences that clear rather than skip.

**Then the client turned out not to know the new visual ids.** The server binds
`END_RIFT_ECHO_REPLICA_V1` and `END_RIFT_ECHO_DECOY_V1`. The client decided what
a visual id renders as in three separate places, and none of them had an entry:

```
EndEventTextureCatalog.textureForVisual    -> returned null
EndermanRendererSelection.selectVisual     -> exhaustive switch, default -> vanilla()
EndermanEntityRendererMixin                -> guardian flag false
```

So a decoy would have rendered as a plain vanilla Enderman. For a mechanic whose
entire purpose is that the player cannot tell the copies from the real Guardian,
that is not a cosmetic slip - it advertises the trick and hides the authored
model at the same time.

This is the shape of failure the master contract keeps warning about. The server
side was wired, logged, mutation-tested and green. The client side had no test at
all, because every existing client test asked whether a class was present in the
jar rather than whether any id resolved to anything. A model that is bound,
published, logged and never drawn passes all of that.

Three files now carry the arms, and `test_end_rift_echo_renders_as_guardian.py`
holds all three with mutations: removing the switch arms fails
`test_the_renderer_selection_switch_has_an_arm_for_both_echo_ids`; dropping
`echoFamily` from the guardian predicate fails
`test_the_enderman_mixin_treats_echo_ids_as_the_guardian_family`. Both restored,
both re-run green.

What this still does not establish: whether the result looks right. No client was
run. The claim is that the id now resolves to the authored Guardian texture,
geometry and animation family - not that anyone has seen it.

## The plugin build is reproducible, and I nearly concluded otherwise

Two CI runs came back red, both on the same step, both with the same line:

```
ValueError: Local plugin artifact SHA-256 differs from its lock:
CopiMineEndEvent expected=af45b9a2... actual=52d1448e...
```

My first reading was "stale lock, repin again" - the same diagnosis as the
earlier failure, and a comfortable one because I had already fixed that once.
It was wrong. The lock was fine. The two CI runs had simply been triggered
against commits that predated the repin.

What mattered was the number `52d1448e`. That is not the old lock value
(`af45b9a2`), and it is not what my machine builds either. It is a third
digest, produced by CI's own build. Two machines disagreeing about a "pinned"
artifact is a much more serious claim than a stale pin, so I stopped guessing
and reproduced CI's path locally.

The local build used Gradle `endEventJar`. CI does not: it runs
`scripts/minecraft/PrepareMigrationCandidates.ps1`, which drives
`copimine-end-event/build-plugin.ps1`. Running that exact script here produced

```
jar sha256: 7128fc9557a389a8f4813fb1ea5e8130b0b936ce92d003073fe5f194dfcab544
Verified 30 pinned plugin candidates
EXIT=0
```

which is precisely the committed lock, and which reproduces the earlier
`endEventJar` result byte for byte. So the artifact is reproducible across the
two build entry points after all, and `52d1448e` was the earlier commit's
source against the earlier lock - a genuine mismatch, at a commit that was
already superseded when I looked.

The lesson is narrow and worth keeping: a red CI on a commit that is not your
HEAD tells you about that commit. Read the `actual` digest before believing the
`expected` one is wrong. Had I repinned to `52d1448e` on the strength of the
CI message alone, I would have committed a lock pinning an artifact that no
build on this machine produces, and turned a transient into a permanent defect.

Reproduced on my side, so it is evidenced rather than argued: the plugin digest
is identical under `endEventJar` and under CI's `PrepareMigrationCandidates.ps1`.
