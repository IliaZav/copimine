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
| M2 | Waves 1–5, updated Wave 2 groups/layout, Wave 3/4 transitions, uniform fog/capacity/effect ownership | Pending contract-by-contract audit and implementation |
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
- Candidate staging and inventory provenance: 142 passed, 3 host/environment skips.
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
