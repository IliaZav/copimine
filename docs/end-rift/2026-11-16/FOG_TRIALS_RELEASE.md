# Fog, trial presentation and reviewed DeepSeek integration

## Source ownership and provenance

Integration checkout: `C:/Temp/cm-phase6-runtime-20261112`.
Published starting point: `3750397c6d9ed5dd3922454f80b5b7a888f5a8c6`.
Source candidate: `5c00a22c` (including all intervening Guardian/Echo repairs).
Publish destination: `origin/codex/rift-guardian-polish-20261110`.

DeepSeek's `42f22c49` diverged from this integration: 33 integration commits
versus five DeepSeek commits. Do not merge its release pins or replace newer
Guardian, Wave 6 or open-arena Wave 7 runtimes with that older baseline.
Reviewed `17c80564` and `948c058c` selectively: retain carrier Slowness II,
repair Ram HUD text, distinguish spell-composition metadata and use Apache's
archive for the unchanged pinned Maven binary. Several server display names
were already correct on the integration branch. DeepSeek's test report is
evidence for its own source, not this candidate.

## Repairs in this slice

| Defect | Cause and implementation | Evidence boundary |
|---|---|---|
| Nearby fog reads as a black screen | Delay local extinction ramp; preserve reconstructed floor texture within roughly 2–3 blocks. Distant opacity remains. | Shader source and client build; native image unverified. |
| Sparse visible world smoke | Most of the old 44-by-44 distribution was hidden behind dense fog. Reuse the same bounded budget in visible distance bands; retain ash, soul and smoke providers. | Client implementation; appearance and FPS unverified. |
| Wrong persistent fog floor | A heuristic emerald block could permanently win before a signed guide arrived. Allow signed guide height to supersede that provisional cache. | Client implementation and compilation. |
| Smoke disappears before fog fade | Stop replenishment on CLEAR but retire owned smoke with volume fade; never clear the global particle engine or reload models. | Client implementation; repeated in-game cycles unverified. |
| Trial actors arrive already attacking | New archmage/marksman actors have one generation-scoped, 20-tick emergence with a real finite gate body and scoped sound. Movement/attacks resume afterward; actors remain damageable. Adoption after restart does not replay arrival. | Server/client compilation; no live trial invoked. |
| Corpse shrinks and separates | Copy actual entity scale to decorative death carrier. Rotate the complete rig around its feet rather than moving sibling torso/head/arms independently. | Model code/client build; native animation unverified. |

The November 11 master contract remains authoritative: uniform Wave 5 optical
fog includes safe cells; shelters protect gameplay rather than disable the
volume. This release does not restore the superseded clear-chamber design.
Wave 7 remains one open arena for all supported party sizes.

## Included Guardian/Echo architecture

`GuardianActorFrame` owns signed, actor-local GACT1 animation clocks. The
server, bridge and client must be installed together. Replicas do not share
the real boss bar or authorize damage through presentation packets.

Delayed copies keep the committed source family and use corresponding swipe,
slam or moving chest-projectile collision. Target/heading lock, LOS, one-hit
receipts, cancellation and generation fences remain server-owned. Explosion
is a separate warned beat with finite 3D geometry. Real chest projectiles use
continuous segment collision and retire their model on interruption.

Persistent 50-HP decoys keep legal focus with hysteresis and bounded path
requests; their native arrows retain the reduced accepted damage route and
finite lifetime. Actor frames present run, attack, hurt and retirement rather
than borrowing the real Guardian's clip clock. Rage combinations use the
existing committed melee runtime. Wave 7 rift shots and Wave 6 directed final
skeleton fans have straight trajectories matching their committed warnings.

## Validation and remaining acceptance

User explicitly assigns tests/gameplay to DeepSeek. No test suite, bot run,
Minecraft playtest or local Paper encounter was launched for this slice.

Build commands (Temurin 25.0.4.1+1 / Gradle 9.8.1):

- `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain`
- `gradle -p tools/minecraft-26.3/client clean jar --console=plain`
- `python -X utf8 resourcepacks/build-resourcepack.py --skip-server-properties`
- `git diff --check`

Server and client builds succeeded; existing deprecation/unchecked warnings
remain. Candidate packaging inspects ZIP CRCs, protocol classes, Wave 7
animator, model/mixin entries, six Guardian spell clips and fifteen audio cues.
These are artifact checks, not gameplay acceptance.

Candidate manifest: `artifacts/20261116-fog-trials-production-candidate/manifest.json`.
Runtime deployment/backup receipts are recorded separately after installation.
Required DeepSeek acceptance: repeat fog cycles without losing bindings;
ground visibility and dense smoke; all trial arrivals/deaths at real scale;
first post-arrival attacks; reconnect/adoption; complete Wave 6 seven impacts;
Guardian phases, Echo damage/cancellation, final cocoon and victory.

Do not label the full encounter, visual quality, performance, recovery matrix,
or latest GitHub Actions as passed from successful compilation/startup.
