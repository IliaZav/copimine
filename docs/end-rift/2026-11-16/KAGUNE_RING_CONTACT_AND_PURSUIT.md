# Moving Kagune ring and grounded pursuit

Source: `c9a76d49`, based on `3030b24f`. Tests/gameplay remain assigned to
DeepSeek by the user. This document is implementation/build evidence, not an
official encounter or visual acceptance report.

## Defects and fixes

1. **Ring pose contradicts collision.** During the damaging travel interval the
   renderer played the imported THROW clip, folding the model sideways while
   server contact remained a moving upright column. Ring stems now stay upright
   with small counter-rotating distal-joint ripples. Emergence/retraction moves
   the complete original rig through the floor using shared height and timing;
   frame interpolation smooths that vertical motion. One pose is still sampled
   per batched row, not per tentacle or full AI mob.
2. **Endpoint-only vertical and gap checks.** The old server rejected/accepted a
   whole movement segment from the player's final Y and final gap angle. Shared
   `KaguneRingFrame.contacts` now intersects the moving player's vertical
   interval and horizontal swept-cylinder interval against the actual visible
   stems. Gap and boundary-stem visibility are evaluated at contact time. The
   calculation uses player width/height and the shared rig height. It does not
   allocate per-stem collections or change frozen gap layout, row cadence,
   server ownership or the existing one-accepted-hit-per-cast ledger.
3. **Raised-player pursuit stall in grounded phases.** Navigation considered
   horizontal melee distance sufficient to stop, while attack admission checks
   full distance. Hold-position now requires the same full reach, preserving
   movement toward players on modest elevation changes.
4. **Final fury retreats from Core campers.** The old Core-specific CREATE_SPACE
   branch preceded the melee-fury branch, selecting an eight-block retreat in
   the melee-only finale. Core players no longer override fury's close-pressure
   intent. Destinations still use existing safe arena/path validation.
5. **Throw callback lifecycle.** Tentacle release now checks current ownership
   and active combat before any launch. After accepted damage it rechecks event,
   generation, actor, participant/world, death and active boss state. A listener
   ending the encounter or transferring/killing the target cannot attach the
   old action's velocity and landing lease to the new state. Canceled damage
   retains the existing early return.

## Build and artifact evidence

Pinned Temurin 25.0.4.1+1 / Gradle 9.8.1:

- `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain` — succeeded.
- `gradle -p tools/minecraft-26.3/client clean jar --console=plain` — succeeded.
- `git diff --check` — clean.

Candidate: `artifacts/20261116-kagune-ring-contact-candidate/manifest.json`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| EndEvent | 2560547 | 883ccfb5304391033b1ad8c3492ee64fe48449daf4a97d936a0192a1492ffb79 |
| Bridge | 378217 | 4609582cdc08db60e64122b510e7a264a001baeef88fd10f905bee054be4f9a0 |
| Client | 30247430 | 99f56df57ef57b263da93d3a28d532838fc313c0a4d7d76280b628669a12e2c5 |
| Migrated 26.3 pack | 34373458 | 98d7950558e7da38e37b641d340b7fe89dc0310cbce6ad0671be62432d627176 |

## Exact CI handoff for DeepSeek

Previous source/pin run `43871297327` (`2eaf2183`) completed red:
58 failed, 3302 passed, 8 skipped, 303 setup errors, 2 subtests passed.
PostgreSQL migration job passed; Java plugin job was skipped. Resource-pack
preparation progressed past the repaired pin gate. Examples in failed logs:
extracted compile harnesses omit new protocol classes referenced by the codec;
Wave 7 stage test still expects four rooms for a large party. Do not restore
obsolete rooms or old mechanics to satisfy superseded expectations. Triage all
remaining results against actual source; these examples do not prove every
failure is a stale test. No local suite was run or edited here.

Required behavior acceptance: each ring row; moving/jumping/falling players;
gap boundaries and lateral crossing; 2/5/10/11/20 participants; one-hit ledger;
cancel, stale owner, carrier removal; retreat/move on elevated targets; Core
camping in fury; throw damage listeners that end combat, kill or transfer the
target. Check real rig continuity and root retraction in Minecraft. New and
existing all-phase abilities, native visuals, performance and full victory are
not declared verified by this slice.

## Cocoon cancellation follow-up

`c8ac0f3e` also fixes an integration gap found while following the actual
collapse chain: `cancelBossCastForPhaseChange()` did not own temporary
`TentacleController` grab states. A held ambush could therefore continue
through the fall. Cocoon break now retires only UNDER_PLAYER/TEMPORARY spell
actors, releases their controller ownership and protects the first landing of
an actually held participant. SMALL living enemies are preserved. THROW up to
its release marker now counts as controlled, including target arbitration.
The final plugin was rebuilt successfully after this correction.

Final candidate: `artifacts/20261116-cocoon-cancel-final-candidate/manifest.json`.
EndEvent is now 2560796 bytes, SHA-256
`5253e668b09cf31d72d60c5f3f61c56d4c23baf52140950df367f674c32cc267`.
Client, bridge and converted pack are unchanged from the table above.

CI run `43878808596` for the preceding source/pin commit also completed red:
60 failed, 3278 passed, 8 skipped, 325 setup errors, 2 subtests passed. Migration
PostgreSQL passed; Java plugin CI was skipped. Those suites were run by GitHub
Actions, not locally. DeepSeek must update missing extracted-harness
dependencies/stubs and independently triage the remaining failures; this
release does not claim green CI.

## Independent review correction and final candidate

The first CLI review was interrupted without a result. The retry completed
against `3030b24f..34e78aa9`, reviewed eleven source/pin/documentation files and
raised one minor issue: the first TRAVEL frame skipped the final EMERGE rise
sample (0.95). `70392cb5` restores that interpolation and defers damage until
the next travel sample, when the upright row is fully presented. Both plugin
and clean client were rebuilt successfully. The cocoon cancellation follow-up
was manually traced and compiled; it was outside the retry's immutable scope.

Final reviewed-correction candidate:
`artifacts/20261116-ring-reviewed-final-candidate/manifest.json`.
EndEvent: 2560821 bytes, SHA-256
`751209eddd2813832798e903298d8d4becda5f81e9b18b928ecd96e2eb67b1f4`.
Client: 30247451 bytes, SHA-256
`81a5e1d052002b1b0ef8d84afd63aecc1530b1ff7880d4fe42d0416e51978cfb`.
Bridge and migrated resource pack remain unchanged from the table.
No claim of a zero-issue review or completed native/gameplay acceptance is made.
