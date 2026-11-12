# Runtime repair checkpoint — 12 November 2026

This is a development checkpoint, not full encounter or production acceptance.
The November 11 master and the user's later animation-speed decision apply.
The October 27 canonical files were read and their recorded SHA-256 values
remain unchanged.

## Implemented production changes

| Defect | Runtime repair | Principal owner |
|---|---|---|
| Spell fairness rewrote pursuit aggro | Independent generation-scoped cast target history | BossSpellTargetSelector |
| Ring rows were particle circles following the boss | Fixed cast origin, three sequential moving rows, original instanced Kagune rigs, frozen gaps, cast hit ledger, fall lease | LastSealRingRuntime / KaguneRingFrame |
| Small summons spent the ambush cap | Separate 30-small / 6-temporary / 8-guardian budgets | TentacleController |
| Small summon clock reset on restart | Remaining active time and permanent stop receipt in the real boss snapshot | SmallKaguneCadence |
| Small attacks used grab and false hurt recovery | Locked-direction windup, contact frame, miss and recovery; living actors accept real damage | tickSmallKagune / active client animator |
| Echo existed only on phase entry | Copies originate from committed contact attack execution, with mapped source family and warning-point lock | executeContactBossAbility / EchoReplicaPresentationPolicy |
| Boss projectile had particle-only presentation | Real projectile carries a closed textured model | riftProjectile |
| Three elite effects were flat sprites | Distinct closed meshes, explicit item definitions, embedded client assets | resourcepacks/src/assets/copimine / client processResources |
| Large-party fog footprints never shrank | Joint final footprint/count capacity planning | BlackFogTimingPolicy |
| Pillars extended below the arena floor | Floor-based bodies; absolute stage dimensions on restore; marksman platform/patrol follows damage | RiftRamEncounter |
| Long contact animations plus idle padding made melee sluggish | Shared 1.5x swipe and 1.25x chest/slam playback, contact and animated-hitbox time mapping; redundant padding removed | BossAnimationPlayback |

## Independent review

CodeRabbit CLI 0.8.2 reviewed committed changes against `56e70e54` and reported
2 issues: 1 major, 1 minor. Raw local output:
`C:/Temp/cm-cacc-coderabbit-review.ndjson`.

* Major, ring renderer: degrees were passed to a radians API. Confirmed against
  the adjacent facing-yaw path; corrected to `-angle + PI/2` radians.
* Minor, final-pillar marksman name: a Cyrillic string was corrupted. Confirmed
  and restored to `Стрелок Разлома`.

The direct plugin builder now compiles all three required encounter protocol
helpers with the main source set. Adding presentation classes no longer changes
the unrelated Narcotics runtime JAR. Both direct compilation and packaging ran.

## Executed evidence

* Modern server compilation and JAR: passed.
* Modern clean client build: passed; 370 tests, 0 failures/errors/skips.
* Two clean pre-review client builds matched exactly. Review fixes then changed
  the pinned client and server bytes; their pins were refreshed from new builds.
* Full-tree Paper 26.3 compilation: 391 sources, 1074 classes, exit 0.
* Candidate installer `--stage-only`: verified 30 pinned candidates; no install.
* Existing cocoon/ambush checks: 12 passed.
* Existing Echo presentation checks: 8 passed.
* Existing small-enemy adapter/config/wiring checks: 14 passed.
* Existing fog adapter checks: 21 passed.
* Existing Ram geometry/restart/roster checks: 20 passed.
* Existing retimed release scheduler checks: 4 passed.
* Accepted-damage adapter after updating its dependency boundary: 93 passed,
  2 skipped. It retains protection, absorption, stale-target and cover cases.
* Strengthened actual pillar-builder geometry plus client-pin check: 17 passed.

## Still not acceptance

The full Python run and End Rift gate are recorded separately when complete;
focused checks are not a substitute. Exact-final-SHA CI must also pass.
No new release candidate was installed on production or in the user's working
Prism instance. The separately prepared local Paper server still requires a
configured fixture arena and authenticated client run on these exact artifacts.

All required native screenshots, full official wave/boss progression, party-size
matrix, measured performance, individual small-enemy cold recovery and the entire
spell-art catalog remain NOT VERIFIED or unfinished. The three mesh families do
not constitute a completed overhaul of every spell. `nativeVerified=false`.
