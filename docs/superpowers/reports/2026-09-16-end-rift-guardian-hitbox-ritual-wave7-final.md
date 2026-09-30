# CopiMine End Rift — guardian, hitbox, Ritual Sphere, and Wave 7 verification record

Date: 2026-09-16 (Europe/Moscow)

Repository: `IliaZav/copimine`

Branch: `codex/end-rift-event`

## Outcome

The current source layer contains the End Rift guardian model import, authored animation wiring, scoped renderer selection, server-authoritative boss hitboxes and damage, the Ritual Sphere Wave 6 path, and the one-block physical/visual Wave 7 barrier path. The disposable Wave 6/7 runtime contract now completes through restart, natural cleanup, and command cleanup. The guardian follow-up also aligns hitboxes with the rotated bind-pose geometry, normalizes the client texture palette, and refreshes the staged client/modpack artifacts so the distributed client is the tested client.

This record deliberately separates source/build evidence, local live-server evidence, and native visual evidence. A passing contract or a historical screenshot is not treated as proof of a current full Minecraft visual acceptance run.

## Implemented scope

### Guardian model and client rendering

- Imported the supplied Bedrock guardian geometry, preserving hierarchy, cubes, pivots, rotations, and per-face UVs through `UserEndBossModelData` and strict asset validation.
- Wired the supplied idle, running, hurt, dying, chest-strike, ground-slam, and swipe animation JSON files through `UserEndBossAnimationPlayer`, with bind/reset protection against pose drift.
- Kept look control isolated to the head/neck channel and added explicit model/texture/animation metadata for runtime selection diagnostics.
- Added separate event Enderman and Rift Spider model/renderer paths and scoped renderer mixins so the guardian model is not applied to unrelated vanilla entities.
- Normalized the server visual alias to the `END_RIFT_GUARDIAN_V1` client catalog key and kept the ordinary Minecraft bossbar as the active HUD presentation.

### Server authority and hitboxes

- Added a composite guardian hitbox controller based on persistent server-side `Interaction` proxies, with a single authoritative damage route, same-tick deduplication, real health/shield accounting, and fallback handling for projectile/melee paths.
- Added explicit boss hitbox profile/transform policies and pure tests for part mapping, offsets, rotation, overlap deduplication, and damage ownership.
- Added `/cmend debug bosshitbox on|off|status` for controlled local inspection without weakening normal event admission or anti-counterfeit checks.
- Added Wave 7 same-room knockback pairing so only the accepted follow-up knockback from an owned chamber mob is suppressed; natural mob, cross-room, expired-window, and pre-Wave-7 knockback remain untouched.

### Ritual Sphere Wave 6 and Reality Split Wave 7

- Wave 6 uses one `RitualSphereEncounter`/definition for visual anchors, collision, AI, caster/guard roles, prisoner health, control-pair behavior, scaling, and drain timing.
- Wave 7 uses one journaled cell set for the physical `BARRIER` wall and `AMETHYST` visual layer. The live boundary reported 152 physical cells and 38 connected columns across two chambers.
- Restart recovery rehydrates the barrier with collision and visibility intact; cleanup restores blocks, removes displays, and leaves no transient event entities.
- Participant containment and mob destination checks now use occupant-specific full-footprint clearance, not only a center-point check. The player recovery path rejects wall intersections and searches for a safe combat location before applying a correction.
- The local disposable-wave completion path no longer requests an official reward roster. Official Wave 7 reward behavior remains behind the official-attempt branch; disposable validation now performs cleanup immediately after natural completion.

## Verification performed

### Static and unit/contract gate

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1
```

Result: `End Rift current local checks passed.`

The gate completed the server plugin builds, Fabric client Gradle build (`BUILD SUCCESSFUL`), resource-pack build, current Python contracts (`135 passed in 2.05s`), Java policy/persistence checks, artifact parity checks, and `git diff --check`. The build emitted five existing API deprecation warnings; no compilation or test failures occurred. The first run after the texture fix correctly failed on a stale staged client JAR; the staged JAR, modpack, checksums, and public snapshot were then regenerated and the complete gate passed.

The focused disposable-wave regression was intentionally red before the repair and green after it. The final focused contract set reported `17 passed` for the current Wave 6/7 boundary and disposable-completion checks.

### Fresh local Paper boundary run

Command:

```powershell
.\tests\RunEndRiftWave6Wave7BoundariesLive.ps1 -FirstBotName DiagC -SecondBotName DiagD -BotDurationSeconds 180 -TimeoutSeconds 60
```

The isolated Paper run exited successfully and produced these acceptance markers:

```text
LIVE_WAVE6_RITUAL_SPHERE_PASS casters=4 guards=12 prisoner=df52ad19-9df1-3b60-987a-e464ec56cfdd drain_interval_ms=20000 drain_hp=2 health_floor=1 visual_displays=1 legacy_rings=false
LIVE_WAVE7_ONE_BLOCK_WALL_PASS chambers=2 cells=152 columns=38 visual_displays=38 wall_material=barrier barrier=13,69,-39 collision=true connected=raster
LIVE_WAVE7_RESTART_RECOVERY_PASS rehydrated=true collision=true visible=true barrier=13,69,-39 journal_replayed=true
LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0 phase_unchanged=true
LIVE_WAVE7_COMMAND_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0
```

The test server was stopped after the run. This is isolated local runtime evidence, not a production deployment or a claim about another server installation.

## Artifact hashes

All hashes below are SHA-256 unless stated otherwise. The current build gate reported byte parity for the source build and the staged third-party copy where both are present.

| Artifact | Size | SHA-256 |
|---|---:|---|
| `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` | 9,431,136 | `9ffc0628975fc86aafe3286eebd6f1a508d6355c4fa800c067670dadd5c84920` |
| `thirdparty/client-mods/CopiMineClient-0.1.1.jar` | 9,431,136 | `9ffc0628975fc86aafe3286eebd6f1a508d6355c4fa800c067670dadd5c84920` |
| `copimine-end-event/CopiMineEndEvent.jar` | 780,743 | `700033bad3e75fc5899b026d75d2babe05424aca16da31fb43828a0cfd303e08` |
| `minecraft/server/plugins/CopiMineEndEvent.jar` | 780,743 | `700033bad3e75fc5899b026d75d2babe05424aca16da31fb43828a0cfd303e08` |
| `thirdparty/CopiMineMods.zip` | 21,635,094 | `f2a6beb19d37918d93daf99ba0ab61146937b08580830a8ec75d42caf543f5f1` |
| resource-pack build output | — | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| `artifacts/end-rift-v3-evidence/boss-hitbox-profile-bind-pose-20260916.png` | 56,131 | `a7f0c9d4150625447268f3ebff8366b8cbbaa812a360bb3aefdd95ca05c8abb5` |
| `artifacts/end-rift-v3-evidence/boss-texture-reference-atlas-20260916.png` | 49,694 | `8d82673b7794aa8e348a1cfc4f6cb4c96b323c9e563cffd1860da083b160210a` |

The resource-pack hash is also recorded by the resource-pack build and manifest checks.

The user-supplied reference image is committed as `artifacts/end-rift-v3-evidence/reference-boss-user.png` and was copied without modification. The latest attached reference has the same bytes and hash:

- source: `C:\Users\zavod\AppData\Local\Temp\codex-clipboard-d5622bb1-1365-49ed-9004-3e3a6ef7a28d.png`;
- dimensions: 331x751;
- size: 24,472 bytes;
- SHA-256: `af6f06e069b5942d748fca69c2001a19dff6150016fb5dc00c2982c9bcc10ca7`.

## Native Minecraft media retained for review

These files are historical local captures from 2026-09-15, made during the earlier source-backed local Minecraft run. They are useful review artifacts, but they are not a new native acceptance run for this final commit.

| Evidence | Details | SHA-256 |
|---|---|---|
| `artifacts/end-rift-v3-evidence/native-end-rift-boss-vanilla-bar-close-20260915.png` | 854x480 screenshot; ordinary vanilla purple bossbar; supplied guardian model visible | `3f7cc4be367a98169100211760a882f0356814c2760e39e63320db0e23af654` |
| `artifacts/end-rift-v3-evidence/native-end-rift-boss-flight-15s-vanilla-bar-20260915.mp4` | 15.000 seconds, 30 fps, 450 frames, 854x320; processed sequence from real Minecraft F2 frames | `ed3a0f8de41c6ddfde657038bded64eb66bcd489b5e1b5cc38f37825cf08f563` |
| `artifacts/end-rift-v3-evidence/native-end-rift-boss-flight-15s-processed-20260915.mp4` | 15.000 seconds, 30 fps, 450 output frames; earlier processed comparison | `d60140e50ee981d33f2697d4ee8357a04732d66300c11c7c4b4124351341091e` |
| `artifacts/end-rift-v3-evidence/native-end-rift-final-artifact-screenshot-20260915.png` | 1024x767 arena-only comparison frame; no boss | `3d3e8aaf175fd98795809b9a907c1e8c159eca47d6011638b65b5db8e6122520` |

The vanilla bossbar frame confirms the HUD correction and model resource resolution for that historical run. It does not prove pixel-exact visual equality with the supplied reference, every authored animation clip, or every official encounter phase.

## Explicit visual-verification boundary

`REAL MINECRAFT VISUAL VERIFICATION NOT PERFORMED` for the current final change set: the Computer Use surface available in this turn reported no native application/window (`apps=[]`). Plugin Management also reported that `computer-use@openai-bundled` is not installed in this session; its native app-control methods are therefore not repairable from this repository. A TLauncher process was started as a permitted launch attempt, but the Computer Use runtime still exposed no controllable Minecraft window, so a new in-world screenshot and a continuous 15-second flight recording could not be captured through the requested interface.

`NOT VERIFIED IN GAME` for the full matrix of exact reference fidelity, front/side silhouette, authored attack animations, all guardian hitbox parts, Wave 6 cardinal/diagonal/corner movement, Wave 7 restart visibility, damage stages, defeat/reward HUD, and official multiplayer visual flow.

The source-side Bedrock importer, animation parser, model-selection contracts, live server boundary contracts, and historical local captures provide evidence for the implementation, but native visual acceptance still needs one fresh visible Minecraft run after the Computer plugin/window surface is available again.

## Publication boundary

No production server upload or installation was performed. The source, static visual evidence, and refreshed distribution artifacts were published to `https://github.com/IliaZav/copimine`:

- commit: `5e3d2cd61af075c126715562389c814b6c1c8e69` (`fix: close End Rift guardian and wave boundary gaps`);
- client artifact parity/build normalization: `1407ad84907d4a9da71f9d248942b3905a664cd6` (`fix: normalize client resource artifact`);
- bind-pose hitboxes and static hitbox proof: `8ba68131` (`fix: align guardian hitboxes with bind pose`);
- guardian texture palette and static atlas proof: `70830faa` (`fix: normalize guardian texture palette`);
- the staged client/modpack refresh and this report are included in the follow-up distribution commit on this branch;
- branch: `codex/end-rift-event`;
- pull request: [#3 — fix: close End Rift guardian and wave boundary gaps](https://github.com/IliaZav/copimine/pull/3).

GitHub Actions verification for `1407ad84907d4a9da71f9d248942b3905a664cd6` completed successfully in [run #645](https://github.com/IliaZav/copimine/actions/runs/35110552028): both `static-and-contract` and `java-plugins` passed, including the clean-runner client build, artifact validators, the 133 Python contracts, and the End Rift event gate.

The pull request is open for review and has not been merged.
