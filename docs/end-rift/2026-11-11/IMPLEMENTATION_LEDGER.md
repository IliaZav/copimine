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
