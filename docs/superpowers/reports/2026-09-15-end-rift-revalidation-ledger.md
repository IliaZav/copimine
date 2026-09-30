# End Rift v3 Revalidation Ledger

Date opened: 2026-09-15 (Europe/Moscow)

This is a working evidence ledger for the v3 objective. Existing source comments, historical plans, generated evidence folders, and prior reports are claims to revalidate, not proof. `FIXED` is reserved for a fresh test/runtime/native artifact that directly proves the row.

## Baseline

| Field | Evidence | Status |
|---|---|---|
| Repository | `IliaZav/copimine` | recorded |
| Target branch | `codex/end-rift-event` | recorded |
| Published baseline commit | `da25b4f514ae384af63c8f82667e57a8f5a47e01` (`fix(end-rift): make wave seven walls visible`) | recorded |
| Published baseline tree | `773f1f1c63768948176d6d6a5db4b6b3f2df10b5` | recorded |
| GitHub compare at start | remote branch identical to baseline; no ahead commits | recorded |
| Clean baseline gate | `tests/RunEndRiftEventChecks.ps1`: server/client/resource build, 77 Python contracts, policy/persistence tests, diff hygiene: exit 0 | recorded |
| Current target tree | same HEAD with a preserved dirty layer listed by `git status --short` | recorded |

## Fresh native/artifact evidence (2026-09-15)

| Evidence | Result |
|---|---|
| Current source client artifact | `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar`, SHA-256 `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370`, 9,430,955 bytes; this is the vanilla-bossbar follow-up build used for the final local parity check |
| Client copies outside the source build | `thirdparty/client-mods` SHA-256 `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370`; `D:\.minecraft\versions\ServerRP\mods` has the same SHA-256; both are byte-identical to the source build |
| Current resource pack | SHA-256 `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`; source build and both `D:\.minecraft` copies match |
| Current modpack archive | `thirdparty/CopiMineMods.zip`, 21,635,001 bytes, SHA-256 `87b5443cb08c7c91d244c2120d3417988ea4ab86179cb9ddc1b2053ba6037a74`; `thirdparty_manifest.json` SHA-1/SHA-256 entries now match the archive |
| Native boss screenshot | `artifacts/end-rift-v3-evidence/native-end-rift-boss-vanilla-bar-close-20260915.png`, 854x480, SHA-256 `3f7cc4be367a98169100211760a882f0356814c2760e39e63320db0e23af654` |
| Native processed video | `artifacts/end-rift-v3-evidence/native-end-rift-boss-flight-15s-vanilla-bar-20260915.mp4`, 15.000 s, 30 fps, 450 frames, 854x320, SHA-256 `ed3a0f8de41c6ddfde657038bded64eb66bcd489b5e1b5cc38f37825cf08f563`; made from 15 real Minecraft F2 framebuffer PNGs after the vanilla-bar fix, not a continuous GPU/window recording |
| Local server state during boss capture | `cmend status`: disposable `cmend test boss` path with `wave=0`, `event-mobs=1`, boss `hp=5000/5000`, `bossPhase=AWAKENING`, `endUnlocked=true`; this is not an official rewarded boss wave |
| Earlier arena-only media retained | `native-end-rift-final-artifact-screenshot-20260915.png` and `native-end-rift-final-artifact-flight-15s-20260915.mp4` remain as prior arena evidence; they do not show the boss |
| Official three-client Wave 1–7 follow-up | Event `c6927381-a0c1-4a56-bd3f-83c1af67bdd7`, generation `1152`, clients `Rift3A/Rift3B/Rift3C`; `CURRENT_WAVE_PASS` for Wave 1 `RIFT_CARRIERS`, Wave 2 `RIFT_HUNT`, Wave 3 `RIFT_GATES portals=3`, Wave 4 `OBELISK_ASSAULT obelisks=4`, Wave 5 `BLACK_FOG cycles=3`, Wave 6 `COLLAPSE_RINGS rings=3`, and Wave 7 `REALITY_SPLIT chambers=local` |
| Official probe melee recovery | RED contract was added before repair; the test-only helper checks tagged event-mob distance and teleports only for `DistanceSquared > 2.75D * 2.75D`. It uses no `/damage` or `/kill`; focused helper checks passed `2 passed, 91 deselected`. Real Wave 5 `PLAYER_ATTACK` events were observed at distances `0.24` and `1.08` |
| Current post-run server state | Later status after wrapper cleanup: `state=READY_FOR_PLAYERS`, `generation=1157`, `wave=0`, `event-mobs=0`, `boss=none`, `endUnlocked=true`, `victory=NONE` |
| Live boss authority probes | Real health `5000/5000`; single-player damage `before=2000 after=1765 delta=235` with `47` player damage events; three-player damage `297` events and `before=5000 after=4031.2566`; shield probe preserved `shield 950 -> 950`, changed vulnerable health `3950 -> 3943`, and restored shield `950`; all cleanup/scene-restore markers passed |
| Five-client visual/runtime wrapper | `CURRENT_VISUAL_FIVE_PLAYER_PASS`, diagnostics failure/runtime/AI/performance passes; server SHA `3151E711E6749FC455B97F4E36DAC19CB7406E46B1E883DEDDCE1CD442315422`, client SHA `ADE5EF1B66C916F40B66714EB510B44794CA1DEDE6F16C40DF50E68986E26370`, resource-pack SHA `34BBED01D468F5F45821AD82DC571012F6C9C5B581CABCA18FD6D1112FC143C9`; native field remains `NATIVE_CLIENT_SCREENSHOT=NOT_VERIFIED` |
| Native Computer Use discovery | The earlier browser-oriented `cua` check returned no native apps; the current `@oai/sky` service live returned TLauncher, targetable `javaw`, and `Minecraft* 1.21.1`; `sky.get_window_state()` captured the TLauncher window at `1056x783` |
| Persistent native app allow-list | TOML-validated `[computer_use.windows].always_allowed_app_ids` now contains only `C:\\Users\\zavod\\AppData\\Roaming\\.minecraft\\TLauncher.exe` and `process:D:\\.minecraft\\runtime\\java-runtime-delta\\windows\\java-runtime-delta\\bin\\javaw.exe` |
| Native Minecraft activation follow-up | Fresh run launched TLauncher through `@oai/sky`, clicked its native `Войти в игру` action, selected the returned `Minecraft* 1.21.1` window, and captured the visible main menu at `1920x1080`; a later menu click returned an unknown input/refresh outcome, so no in-world boss screenshot is claimed from this follow-up |

| Fresh official five-player run on rebuilt Paper | `CURRENT_OFFICIAL_PASS event=9fe94db9-5748-45c0-b42e-127db1c7187b players=5 waves=1,2,3,4,5,6,7 stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL victory=true`; final `wave=0 event-mobs=0 boss=none victory=VICTORY_COMPLETE` |
| Fresh native renderer selection diagnostic | `/copimineclient endrift selection` reported `23` bindings in the client log; event Enderman, elite, Spider, skeleton, and elite skeleton lines all reported `resourcePresent=true` with concrete model/geometry/texture/animation-set ids |
| Clean double-build reproducibility | Earlier two `gradle clean build --no-daemon` runs: both `BUILD SUCCESSFUL`, both SHA-256 `d94876c7eec13adba28df44eacdadd50e8a0e4a12c591c70cc45201350f973bf`; the later vanilla-bossbar follow-up build is `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370` |

### Follow-up after vanilla bossbar fix (2026-09-15)

| Evidence | Result |
|---|---|
| Final source client artifact | `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar`, SHA-256 `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370`, 9,430,955 bytes; final `gradle clean build --no-daemon`: `BUILD SUCCESSFUL` |
| Focused regression tests | Server alias diagnostic test passed; vanilla bossbar and melee-range recovery contracts passed; staged-client and modpack-manifest parity tests passed; full `tests/test_end_event_current_contract.py`: `93 passed`; current full Python gate: `113 passed` |
| Installed profile client | `D:\.minecraft\versions\ServerRP\mods\CopiMineClient-0.1.1.jar` now matches SHA-256 `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370`; old `525d59753a7a0be1a0c51866ac31b40afb9821f0bc2bd7193c7f0eb66253473` preserved as `CopiMineClient-0.1.1.jar.pre-vanilla-bossbar-20260915.bak` |
| Fresh bossbar/texture screenshot | `artifacts/end-rift-v3-evidence/native-end-rift-boss-vanilla-bar-close-20260915.png`, 854x480, SHA-256 `3f7cc4be367a98169100211760a882f0356814c2760e39e63320db0e23af654`; one ordinary purple `Страж Разлома` bar, supplied boss model visible; old decorative HUD is absent |
| Runtime texture evidence | `CopiMineClient/run/logs/copimineclient.log:60069-60071`: fresh `END_BOSS_BIND`, `END_BOSS_BAR`, and `texture=copimineclient:textures/entity/end_rift_user_boss.png resourcePresent=true` |
| Disposable test cleanup | Final `cmend status`: `state=COLLECTING`, `wave=0`, `event-mobs=0`, `boss=none` |

## Supplied asset verification (2026-09-15)

| Check | Result |
|---|---|
| `end event.rar` versus current client resources | Geometry, seven animation JSON files, and three supplied PNGs all match byte-for-byte by SHA-256 |
| `models.rar` versus `modelsboss.rar` | Exact duplicate; SHA-256 `2a994415246d7f03eb7160414a116427b21a7867347ca27b941ea477441e73cd` |
| Detached `udar_iz_grudi.json` and `udar_po_zemle.animation.json` | Each matches the corresponding copy inside `end event.rar` |
| Supplied model coverage | Full Bedrock geometry is present for the boss; Enderman and Spider contributions are 64x32 skin atlases, with no separate mesh files in the supplied archives |
| Runtime wiring | Boss geometry/animations are loaded by `UserEndBossModelData` and `UserEndBossAnimationPlayer`; event Enderman/elite and Spider select adapted runtime models and the supplied skins |

## Issue ledger

| ID | Requirement / symptom to revalidate | Reproduction / proof needed | Current status | Fix / verification handle |
|---|---|---|---|---|
| E01 | Bedrock geometry conversion must preserve coordinates, pivots, hierarchy, rotations, cubes, UVs, and fail loudly when unsupported | converter unit tests plus supplied-asset golden fixtures | PARTIALLY FIXED | formal coordinate/matrix tests, supplied 16-bone/123-cube/UV fixture, strict validator, and current 96-contract gate pass; native full animation geometry proof remains open |
| E02 | Model selection must distinguish vanilla Enderman, event Enderman, elite, and Rift Guardian | selection tests plus runtime debug metadata and native frames | PARTIALLY FIXED | `EndermanRendererSelectionTest`, scoped `LivingEntityRendererMixin`, native Guardian screenshot, and `/copimineclient endrift selection` with 23 live bindings pass; native full close-up matrix remains open |
| E03 | Bind/rest reset must prevent animation drift | repeated-pose golden test and native clip capture | PARTIALLY FIXED | bind/reset and animation golden tests pass; no native authored animation clip has been captured |
| E04 | Tentacles must be articulated, continuous, and use authored attack clips | chain continuity/animation closure tests plus native capture | PARTIALLY FIXED | continuity/closure and strict parser contracts pass; no native tentacle/attack clip has been captured |
| E05 | Boss look affects head/neck only | channel-isolation test plus native frame sequence | PARTIALLY FIXED | channel-isolation policy/golden checks pass; a native look/animation capture remains open |
| E06 | Obelisk FULL/DAMAGED/CRITICAL, gate, core, and display identity must be correct | model-data/resource tests plus live/native phases | PARTIALLY FIXED | source/resource contracts, live boundary gate, and fresh official Wave 4 `OBELISK_ASSAULT obelisks=4` pass; native phase-by-phase obelisk/gate/core proof remains open |
| E07 | Boss has real HP scaling and reset-safe bossbar lifecycle | pure policy/live two- and ten-player probes plus HUD screenshot | PARTIALLY FIXED | live test boss reports `5000/5000`, fresh official five-player run reached `hp=1681/8500` before the Last Seal shield and completed defeat, and the fresh follow-up frame shows the ordinary vanilla bossbar; native official damage/defeat HUD capture and reset lifecycle remain open |
| E08 | Wave 6 has one RingDefinition for visuals/collision/AI and no boundary bypass | policy tests and live cardinal/diagonal/corner checks | PARTIALLY FIXED | pure policy suite, boundary probe, and fresh official `wave=6 objective=COLLAPSE_RINGS rings=3` pass; a complete native movement matrix remains open |
| E09 | Wave 7 official/dev-solo modes and participant state are separated | live matrix and restart/recovery logs | PARTIALLY FIXED | fresh official five-player run completed Wave 7 with `players=5`, `participants=5`, `helpers=5`, and clean boss victory; restart/recovery and dev-solo separation remain open |
| E10 | Wave 7 physical and visual walls occupy identical cells and remain visible | cell-set contract plus native Wave 7 screenshot/video | PARTIALLY FIXED | cell-set/material/policy changes, live boundary pass (`wall_material=barrier collision=true`), fresh official `wave=7 objective=REALITY_SPLIT`, and native wall frame are present; full native room/collision matrix remains open |
| E11 | Temporary blocks/entities are journaled, restored exactly, and cleanup is idempotent | persistence/recovery tests and repeated live cleanup | PARTIALLY FIXED | persistence suite, live cleanup pass, and fresh official victory cleanup (`event-mobs=0`, `boss=none`) pass; restart/recovery matrix remains open |
| E12 | Renderer/model swaps are scoped and exception-safe | client renderer regression test and client build | PARTIALLY FIXED | renderer now selects the spider/Guardian model through a scoped `@Redirect` and resets per render; current client build, contract gate, and native Guardian frame pass, but native event Enderman/elite/Spider frames remain open |
| E13 | Resource pack and client/server artifacts are complete, hashed, and reproducible | clean double build, manifest/hash parity, CI contracts | PARTIALLY FIXED | earlier clean client builds reproduce SHA-256 `d94876c7eec13adba28df44eacdadd50e8a0e4a12c591c70cc45201350f973bf`; the vanilla-bossbar follow-up build is `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370` and matches both `thirdparty/client-mods` and the `ServerRP` profile; current server artifact `3151e711e6749fc455b97f4e36dac19cb7406e46b1e883deddce1cd442315422`, resource pack `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`, modpack `87b5443cb08c7c91d244c2120d3417988ea4ab86179cb9ddc1b2053ba6037a74`, and manifest hashes are current; deployment/push remains intentionally open |
| E14 | Native Minecraft screenshots and a real 15-second moving flight video are required | current exact artifact installed; frame-difference-checked captures | PARTIALLY FIXED | fresh vanilla-bossbar/texture screenshot, native model-selection screenshot, and current 15.000-second/30-fps/450-frame processed sequence `native-end-rift-boss-flight-15s-vanilla-bar-20260915.mp4` are recorded with hashes from 15 real F2 frames; this is not a continuous GPU recording and not the full event visual matrix |
| E15 | Projectile behavior may not change without new reproduced bug and test | diff audit and projectile-specific regression evidence | PARTIALLY FIXED | current source diff did not introduce a new projectile behavior change; the existing projectile policy tests pass, but no new projectile reproduction was required or added |

## Rules for closing a row

1. Attach the exact command/test or capture path, commit, and artifact hash to the row.
2. A passing static contract cannot close a native-visual row; a screenshot cannot close a server-authority row.
3. Any failed runtime check reopens the corresponding row and creates a new RED test before a repair.

## Open unresolved items after the fresh capture

- Full native Minecraft visual/runtime proof is still incomplete: the official five-player Paper run now proves the authoritative Wave 1–7 and boss lifecycle, but the Minecraft client did not capture every phase, boundary, damage stage, defeat/reward, restart/recovery, and idle/vanilla state as a visual matrix.
- The latest three-client official probe and separate real-health, damage, multiplayer, shield, and recovery probes pass server-side. Native `@oai/sky` is callable, launches the client, and captures a fresh Minecraft main-menu frame; no full native visual matrix is claimed because the in-world boss/wave frames are still missing.
- The current vanilla-bar frame proves the Guardian boss model and ordinary bossbar; the earlier frame/video prove the arena and spectator camera path. The native selection command additionally proves live event Enderman/elite/Spider/skeleton bindings with resources present, but close-up frames and authored attack animations in motion remain open.
- Formal Bedrock conversion, strict validation, supplied texture dimensions, supplied animation parser/resource wiring, and current staged-artifact parity now pass source-side fixtures and the full local gate; native authored animation clips and full visual coverage remain open. The custom bossbar is intentionally disabled pending rework, and the supplied boss PNG is loaded but sparse by design.
- The branch remains a dirty, uncommitted worktree; the repaired client was installed only into the local `ServerRP` profile and no commit or push was made.
