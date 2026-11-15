# Wave 7 continuation and Wave 6 first repair pass — 2026-11-15

User instruction: continue locally, commit and push; production is unavailable.
Start: `558cc0bc`. Implementation: `fca030af`, `ee9f85c0`.
No tests or Minecraft were run; those acceptance tasks remain assigned to DeepSeek.

## Wave 7

- Locomotion retains a legal focus for 60 ticks. Attack-target fairness remains
  independent in the existing cast director; frozen cast aim is unchanged.
- The final steered movement vector is collision/arena checked before applying
  velocity. A blocked heading relinquishes commitment instead of grinding a wall.
- Ground movement preserves vertical physics rather than repeatedly micro-hopping.
- Marksman pair positioning uses the same locomotion focus, a rear ranged lane
  and the common validated steering path instead of directly overwriting velocity.
- Covered targets provoke Warden pursuit while cast admission still requires sight.
- RECOVERY begins at the release clip's final draw weight, avoiding a second windup.
- FEET/LOCKED_FLOOR spell bodies are anchored to the floor; chest models retain
  chest origin. Bounded non-projectile bodies emit four sparse geometry frames,
  max 16 sampled points each; physical shots retain native flight/collision.
- Warden release geometry follows its committed direction after a player's dodge.

## Wave 6

The pillar-emergence bug was a production integration defect, not a style setting:
`buildPillar` authored ordinary/final geometry correctly, then `updateDisplays`
replaced every part's transform with three hard-coded ordinary-pillar offsets.
The fourth core piece overlapped a cap; final-pillar footprint/height were lost.
Each display now has an authoritative rest position; emergence eases from below
that position, and crack stages update the same rest-position ownership map.

- Ram locomotion retains its target for 60 ticks instead of per-tick nearest-player
  rotation. Charge aim freezes for the final eight warning ticks before release.
- Existing signed WAVE pose vocabulary drives preparation, charge/stomp release,
  stagger and recovery. No client pose changes HP, collision or cast admission.
- Render extraction copies bounded pose progress into an immutable render snapshot.
- `RiftRamModelAnimationMixin` layers guarded local neck/body motion over native
  gait, resetting the event model before vanilla animation to prevent accumulation.
  Natural Ravagers are outside the visual-ID guard.
- The final skeleton faces the target position. Ground navigation is updated every
  ten ticks and stops at ranged distance with sight; custom volleys require sight.

## Build evidence

Pinned Temurin 25.0.4.1+1 / Gradle 9.8.1:

- `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain`: SUCCESS.
- `gradle -p tools/minecraft-26.3/client clean jar --console=plain`: SUCCESS.
- CRC/entry inspection: all three coordinated JARs contain the frame class; the
  client contains the new Ram mixin and its registration. This is packaging evidence,
  not proof of live Mixin application or visual quality.
- `git diff --check`: clean before commits.

## Candidate artifacts

Local folder: `artifacts/20261115-wave76-ai-candidate/`.
Source implementation: `4c05fb7e6fd29610dfdb5abab06fdb0e745c7dc8`.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| CopiMineEndEvent.jar | 2520015 | `363860cec8ac548ec13c3dcd9eba007cf77d92ce091394fd2aca06dec5edc110` |
| CopiMineNarcotics.jar | 375086 | `0b44338f0e5f59be1d2691bfc7e62e91008bc7c15e8de4eebe4f9fbb730235a0` |
| CopiMineClient-0.1.4+26.3.jar | 30110413 | `549a7eccfffff041999396cde6075e6635fa00d49ed9666fbff1379e3a721d73` |
| CopiMineResourcePack.zip | 27806472 | `a2e25fcc276ff9be9ad361ac3402648b667ed595752edbd37f19f8343309f6d9` |

Profile/server candidate pins and client-size documentation match these bytes.
No installed client, public downloads, production files, database or world were changed.
Both WAN hosts and server-rpgrp (ports 22/2222) closed SSH before authentication;
the user confirmed a server problem and requested local-only continuation.

## Remaining acceptance

IMPLEMENTED and BUILT: changes above. TESTED/NATIVE/PRODUCTION: NOT VERIFIED.
Required manual/DeepSeek checks include two crossing players during pursuit,
blocked corners and covered targets, cast cancellation, collision/front alignment,
all seven pillar impacts, each final-pillar crack stage, early skeleton death and
surviving skeleton landing, model pose reset between two rendered Ravagers,
actual Mixin application, reconnect/late-bind timing and complete cleanup.
This is a Wave 6 repair slice, not a declaration that every Wave 6 mechanic and
visual or the complete seven-wave event has passed acceptance.

## Independent review and final follow-up

CodeRabbit `review --agent --base-commit 558cc0bc -c AGENTS.md` completed on the
`fca030af` snapshot: 9 files, 1 issue. The issue concerned chargeTell following
the passed target point instead of locked movement; corrected in `4c05fb7e`.
The later Warden-axis and tower-skeleton corrections were manually reviewed and
compiled, not claimed as covered by this immutable review snapshot.

Final-pillar volleys now enter the existing bounded event-arrow controller after
shooter attribution. They select the original triple-shot/bone-rain 3D item mesh,
expire through the common controller, cannot be picked up, and retain their actual
Arrow collision/damage owner. If visual creation fails, the native arrow remains
visible; failure to admit the projectile removes it rather than leaving an orphan.

The server was rebuilt after the final follow-up. Raw review output is local in
the candidate folder. No CI pass, gameplay or visual acceptance is claimed.
