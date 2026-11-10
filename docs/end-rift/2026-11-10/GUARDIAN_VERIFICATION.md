# Rift Guardian implementation and verification, 2026-11-10

Scope: boss only, in managed branch `codex/rift-guardian-polish-20261110`, based
on `f8ef41af7ef89a22fe807b62b3532644dfa39603`. The external DeepSeek checkout
and its uncommitted work were not modified.

## Canonical inputs

Both complete documents were read from the user's actual Downloads directory.
Latest explicit user instructions take precedence.

- Waves SHA-256: `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf`.
- Boss SHA-256: `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50`.

## Implemented behavior

| Requirement | Change | Evidence |
| --- | --- | --- |
| Manual boss spawn | `/cmend spawn boss` normalizes to the existing `/cmend boss spawn` path, preserving confirmation arguments | Compiled command-policy and actual-runtime adapter probes |
| Stable party AI | Full focus lock under simultaneous hits; invalid target still replaced immediately; own maximum HP used for player health perception | Group aggro and incoming-damage simulations |
| Ability diversity | Ambush and small Kagune registered in the real brain; bounded history advances only after accepted casts; invalid geometry has no unsafe fallback | Long Last Seal simulation, actual damage admission tests |
| Manual encounter parity | Small Kagune attacks, Rift fractures, fireballs and recovery use owned live boss runtime rather than the durable official phase | Actual small-Kagune RED/GREEN probe and command runtime fences |
| Manual encounter damage | A previous official roster cannot block an owned diagnostic boss; official roster/protection rules remain required for an official boss | All three incoming hit paths, including old-roster RED/GREEN cases |
| Manual encounter defeat | Player lethal hit plays DYING and finite defeat geometry, cancels hazards, then removes the actor once; no official rewards or End unlock | Actual diagnostic death adapter: duplicate, stale generation, official rejection |
| Model and animation | Bare V4 animation IDs decoded correctly; binding heartbeat reasserts current clip only to that viewer and does not broadcast idle/reset clip clocks | Client state JUnit and extracted server binding probe |
| Phase entry | New authored 2.2 s grounded roar/open-arm clip; short action pause without extra damage immunity; phase-owned coils and roar | Active client clip test, generated pose parity, callback ownership test |
| Impact | Bounded frontal swipe, slam footprint/spokes, travelling chest cross-section and phase/death coils/rings; heavy sound once per cast | Pure geometry and real damage cancellation/miss probes |
| Camera | Optional bounded pitch/yaw/roll envelope from signed animation clock; distance culling and opt-out; render quaternion only | Pure envelope probes, actual 26.3 Camera bytecode and client compilation |
| Bossbar | New original skeletal-mouth RGBA frame; true HP, phases/cast state, damage chip and proportional narrow-screen scaling | Actual asset alpha/UV test, client chip/state tests, packaged JAR inspection |
| Music | Six distinct 45 s arrangements, increasing pulse 76/88/100/112/124/136 BPM; measured levels and identical independent renders; wall-clock boss loop timing | OGG granules/hashes manifest and actual manual/official music adapter probes |
| Official victory | Added bounded collapse geometry/sound to existing terminal presentation; kept durable rewards and WorldCore ownership | Existing defeat durability, incoming roster and phase damage regressions |

Camera feedback is derived from server-selected animation markers. It is not an
acknowledgement that a particular victim accepted damage; a legitimate miss can
still have a ground impact. Joining mid-clip has no wire-level elapsed-time
receipt and is not claimed to be frame-exact. No new protocol type was added.

## Evidence boundaries

- Source, automated behavior, active 26.3 client and plugin/bridge builds: measured locally.
- Original seven supplied boss clips, model geometry and UV were preserved.
- New music has technical level/duration/reproducibility checks, not native listening acceptance.
- `nativeVerified=false`: no authenticated Minecraft visual/play session was operated.
- Production installation and native acceptance are separate release steps.
- This report does not claim that this branch contains any later uncommitted DeepSeek wave changes.

## Player configuration

The player's CopiMine client contains these visuals and the camera effect.
Iris, a shader pack, GeckoLib and an external effect mod are not required.
`/copimineclient camera-shake off` disables the camera effect;
`/copimineclient camera-shake on` restores its default strength of 0.65.
The local configuration property `boss_camera_shake_strength` accepts 0..1.

## Measured final focused checks

- Boss/Guardian/Kagune Python adapter and policy suite: **302 passed, 2 skipped**.
- Migration/client/plugin release-contract group: **239 passed, 4 skipped**.
- Active client JUnit: **353 tests, zero failures/errors**; target client build passed.
- Server bridge JUnit: **94 tests, zero failures/errors**; target plugin build passed.
- Source pose generator `-Check`: passed; `git diff --check`: passed.
- CodeRabbit completed review of tracked and newly added code: **0 issues**.
- Independent candidate rebuild observed from the other executor produced the
  same pinned EndEvent and client bytes.

The initial repository-wide Python run had **3171 passed, 18 failed, 11 errors,
15 skipped**. Missing staged inputs were prepared, changed exact pins and the
new clip's checkout check were updated, and the extracted edge-volley harness
received the new runtime helper. Those failing groups were rechecked; no
assertion was disabled. The above focused checks are fresh green runs, not a
claim that the initial repository-wide run itself was green.

## Prepared bundle (not installed)

`artifacts/20261110-guardian-polish-release-candidate/manifest.json` records
complete sizes and SHA-256/SHA-512 for all four exported files. The EndEvent
and client rows were re-measured after the flying-cobweb projectile, the web
impact feedback and the semi-transparent TAB panel were added, so both differ
from the first candidates this report originally listed :

| Artifact | Bytes | SHA-256 prefix |
| --- | ---: | --- |
| CopiMineEndEvent.jar | 2262141 | `f826ff9fc4b208a4` |
| CopiMineNarcotics.jar (server bridge carrier) | 370499 | `026787808eac784f` |
| CopiMineClient-0.1.3+26.3.jar | 14513956 | `428329f06cd58169` |
| CopiMineResourcePack-26.3.zip | 33371080 | `17aa6d3b2edf626f` |

`tools/minecraft-26.3/profile.lock.json` and
`tools/minecraft-26.3/server-plugins.lock.json` were repinned to exactly these
bytes, and `PrepareMigrationCandidates.ps1` then reproduced them from source and
exited 0. The superseded candidate digests remain in each lock's
`replaceSha256` history so an installed predecessor is still recognised.

Production was not changed. During final checks an OpenCode process launched
`PrepareMigrationCandidates.ps1` in this same managed worktree, recreating
build outputs. A single final build/deployment owner was requested from the
user; no approval is inferred from elapsed time or from that process ending.
Native acceptance and exact-final-SHA hosted CI remain separate, unclaimed gates.

## Final event gate result

`tests/RunEndRiftEventChecks.ps1 -JavaHome <pinned Temurin 25.0.4.1> -SkipBuilds`
finished with **exit 0** and `End Rift current local checks passed` after actual
plugin/client/resource builds and verified staging. Its final boss accepted-hit,
cast-ownership and Kagune-recovery group passed **125 tests, 2 skipped**. Full
log: `artifacts/20261110-guardian-polish-release-candidate/full-event-gate.txt`.
