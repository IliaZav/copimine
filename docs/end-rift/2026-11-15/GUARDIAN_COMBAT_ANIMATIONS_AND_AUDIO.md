# Guardian combat, authored spell releases and Wave 6/7 continuation — 2026-11-15

## Scope and evidence boundary

Latest user instruction: implement locally and push to GitHub; DeepSeek owns tests.
No Minecraft, Paper, gameplay, unit-test or integration-test execution occurred in
this continuation. Compilation and ZIP payload inspection are build evidence only.
Production and the installed Prism profile were not changed.

Implementation baseline: `482c6a690d13459654c73c28ce9564c83c2d59d4`.
Built source: `e21fe17aed5a2e82480ea6375478739a1d691898`.
Commits: `da26737a`, `a56473a6`, `28c7f86c`, `24dd6e2e`, `37a5053b`, `e21fe17a`.
Fetched publication branch was `124fdd7ec5d7738a7c4281e4e60dbc538d34ef92`, an
ancestor of this checkout, not a divergent new edit. Publish by normal fast-forward,
never force-push. The protected integration/source-of-truth checkouts were untouched.

Canonical files were read from D:/Downloads. SHA-256:
- Waves: `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf`.
- Guardian: `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50`.
Latest approved overrides remain: Wave 6 Rift Ram, Wave 7 one open arena, permanently
destroyed final defenders, one fall and permanent final melee fury.

## Implementation

### Grounded Guardian AI

`CopiMineEndEvent.maintainBossPath` now keeps a legal close melee opening instead
of continually pushing the boss out to its preferred range. Replanning follows
bounded phase cadence (200–500 ms). Tactical orbit/standoff destinations are
relative to the selected player; only Core exclusion uses the outer arena ring.
RUN presentation depends on actual motion. `BossCloseCombatPolicy.canStrike`
no longer blocks every melee opportunity while a future major-spell deadline is
pending: runtime cast ownership, pause, cooldown, sight and control fences still
apply. A canceled telegraph releases its cast state, deadline, mobility and hazard.
This requires behavioral acceptance for scheduling fairness and cancel paths.

### Six authored spell-release clips with matching server geometry

Original Guardian hierarchy, pivots and UVs are preserved. The new generator
`CopiMineClient/tools/generate_guardian_spell_animations.py` authors void blast,
lance, arrow fan, ground mark, summon and inferno release poses. Actual releases
now select `BossVisualCuePolicy.animationFor(..., RELEASE)`, not the generic
CAST_RELEASE token. Client clip bindings and server generated pose tracks include
all six clips and the existing summon/arrow aliases.

`copimine-end-event/tools/GenerateBossAnimationPoses.ps1` regenerates the matching
server hitbox tracks; generated source digest:
`eaf2f24e13f861fe0045eab2d5d24272887aa635d7df62e710a0c0eba4366248`.
The generator was run as an asset build, not reported as a regression-test pass.
Swipe playback is 2.25x, slam 3x, chest remains 1.75x. Shared protocol time mapping
retimes client poses, authoritative contact markers and follow-through together.
Server and client must be updated together; a plugin-only install is unsuitable.

### Wave 7 projectiles and Wave 6 action presentation

Named-trial arrows now attach the existing three-dimensional triple-shot/rain
carrier to the real owned Arrow. Carrier synchronization does not register a second
damage controller. Native projectile physics retain collision and damage ownership.
The prior signed Wave 7 cast timelines and single open-arena route remain intact.

Ram charge uses the running action; stomp uses one authored compression/contact
pulse instead of looping run motion. The server refreshes bounded action leases
once per second for late-bound viewers. Sound is emitted on state entry separately,
so lease refresh does not repeat it. This is not a claim of exact late-join frame
synchronization or observed animation quality. All seven impact receipts and the
final four-element pillar invariant remain unchanged.

### Original ability audio and packaging

`resourcepacks/generate_encounter_ability_audio.py` authors 13 original one-shots:
three Ram, Warden swing/sonic, Marksman, Archmage, six Guardian spell families.
Cues use scoped proximity delivery on accepted release/state entry, not tick loops.
They supplement existing spatial fallback sounds. In-game mix, perceived quality
and loudness are NOT VERIFIED.

Payload inspection caught missing nested ability OGGs in the 26.3 client. The
Gradle include now recursively bundles end_rift audio; all 13 catalog entries and
OGGs are present in both client and resource pack. sounds.json is LF-pinned and its
generator writes LF so checkout line endings do not change packaged catalog bytes.

## Builds and inspection

With the repository-pinned toolchains:

- Temurin 25.0.4.1+1, Gradle 9.8.1:
  `gradle -p tools/minecraft-26.3 endEventJar narcoticsJar --console=plain`
  — BUILD SUCCESSFUL, final server build 6 s.
- Same tools: `gradle -p tools/minecraft-26.3/client clean jar --console=plain`
  — BUILD SUCCESSFUL, 12 s.
- `python -X utf8 resourcepacks/build-resourcepack.py --skip-server-properties`
  — built pack, SHA-1 `7a994cb718d590fa7f9bca3a2c73ffb09910f49e`.
- Temurin 21.0.12.1+1, Gradle 8.10.2:
  `gradle -p CopiMineClient clean remapJar -x test --console=plain --max-workers=1`
  — BUILD SUCCESSFUL, 58 s. Gradle compiled test sources as a dependency;
  no test task was executed.
- `scripts/thirdparty/build_modpack.ps1 -SyncBuiltClient` rebuilt the pinned legacy
  client/modpack because shared resources changed. Prior staged artifacts and
  metadata are preserved under the candidate's `prior-legacy/` directory.
- Candidate ZIPs were CRC-inspected; required classes, mixin registration, existing
  spell meshes, six new clips and 13 audio files/catalog mappings were inspected.

CodeRabbit `review --agent --base-commit 482c6a69 -c AGENTS.md` completed with zero
findings on the `da26737a`/`a56473a6` core snapshot. It excluded the binary OGGs.
Later packaging, stomp, generated hitbox and pose-heartbeat fixes were manually
reviewed and compiled, not covered by that immutable CodeRabbit snapshot.

## Candidate

Local directory: `artifacts/20261115-guardian-animation-audio-candidate/`.
The JSON manifest pins the implementation SHA, sizes and SHA-256/SHA-512/SHA-1.
Logs and raw CodeRabbit output are stored alongside it.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `CopiMineEndEvent.jar` | 2522243 | `4252ed47a39c3d9877ff67cf5de815da482a78ab64f53dbf1b09488a7818c30b` |
| `CopiMineNarcotics.jar` | 375086 | `0b44338f0e5f59be1d2691bfc7e62e91008bc7c15e8de4eebe4f9fbb730235a0` |
| `CopiMineClient-0.1.4+26.3.jar` | 30219143 | `cb313147004650081aac44dd319f19e642b4f0130de774754079f16dea1c0fe5` |
| `CopiMineResourcePack.zip` | 27921111 | `d50d1d2d3a8325b4fc9681759c205513264b5303baa4625a18ff4278caab8620` |

The Narcotics bridge was rebuilt/preserved for coordinated packaging; its source
was not otherwise changed here. Legacy 1.21.1 CopiMineClient SHA-256 is
`42a98436f1546f37335bd07f3193351390879d9a5cb0cdb19a5306cffde36b4f`.
Legacy modpack SHA-256:
`4c496060ac5cddbeee203796cc3a726039bb96fa4fefa05a5bf565fcc4ae2ab4`.
These are distinct from the active 26.3 candidate above.

## DeepSeek acceptance still required

- Actual 2/5/10/11/20-player boss scheduling: melee cannot starve spells, casts cannot
  freeze pursuit, targets cannot oscillate, and final fury remains permanently legal.
- Accepted/canceled sword, bow and critical hits; shared accelerated contact timing,
  moving targets, LOS, vertical boundaries and no repeated damage.
- Six spell clips, server arm OBB alignment and aliases, all phase transitions,
  cocoon fall/landing, interruption, restart and cleanup.
- Wave 7 frozen-aim shots with real travel, carrier alignment, one hit, expiration,
  paired crossfire and all miniboss locomotion/animation/audio in the open arena.
- Wave 6 all seven impacts, armor transitions, final skeleton restore, charge/stomp
  distinction, late binding, no repeated audio from action lease heartbeat.
- Actual listening, F2/video inspection, real performance and exact-SHA CI.

Status: IMPLEMENTED + BUILT + PAYLOAD-INSPECTED. Behavioral tests, live Paper,
native visuals/audio, CI and official encounter completion remain NOT VERIFIED.
Do not promote this candidate to a claimed fully accepted release from these builds.
