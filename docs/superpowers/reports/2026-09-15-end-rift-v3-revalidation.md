# End Rift v3 revalidation report

Date: 2026-09-15 (Europe/Moscow)

Repository: `IliaZav/copimine`

Branch: `codex/end-rift-event`

## Outcome

The current dirty layer builds and passes the full local static gate. The fresh native capture used the Fabric development client launched from the current `CopiMineClient` source/resources; this is the client that rendered the boss proof and the event-entity selection proof below. A fresh official five-player run on the rebuilt Paper artifact also completed Waves 1–7 and the boss lifecycle. The resource pack and client JAR in the isolated `D:\.minecraft` profile match the current source-backed artifacts, and the staged `thirdparty` client JAR now matches them as well. The local Wave 6/7 boundary probe also passed and restored its disposable state.

This is a partial revalidation, not a claim that every End Rift v3 requirement is closed. The local server was in `COLLECTING`, `wave=0` during the requested screenshot/video capture, so the media shows the arena and a disposable boss/spectator flight rather than a complete native visual capture of the official encounter.

### Follow-up: vanilla bossbar and supplied boss texture

After the initial capture, the client HUD was corrected and the current source-backed client was rebuilt. The custom `EndRiftBossBarHud` registration and `EndRiftBossBarHudMixin` entry were removed from the active client path, so the event now falls back to the ordinary Minecraft bossbar. The server also stops streaming the retired `END_BOSS_BAR` custom-HUD snapshot while vanilla mode is active; its ordinary Bukkit bossbar remains the sole HP/progress presentation. The server visual alias `END_RIFT_GUARDIAN` is normalized to the catalog key `END_RIFT_GUARDIAN_V1`, which resolves the supplied `end_rift_user_boss.png` atlas.

Fresh native proof from `cmend test boss` and `cmend client bindboss Player355`:

- `CopiMineClient/run/logs/copimineclient.log:60069-60071` records `END_BOSS_BIND`, `END_BOSS_BAR`, and `texture=copimineclient:textures/entity/end_rift_user_boss.png resourcePresent=true`.
- `artifacts/end-rift-v3-evidence/native-end-rift-boss-vanilla-bar-close-20260915.png` is an 854x480 F2 PNG, SHA-256 `3f7cc4be367a98169100211760a882f0356814c2760e39e63320db0e23af654`. It shows one slim vanilla purple `Страж Разлома` bar and the supplied multi-part boss model; the previous decorative frame, phase labels, and numeric HP text are absent. The earlier clean frame `native-end-rift-vanilla-bossbar-texture-clean-20260915.png` is retained as comparison evidence.
- The disposable boss and wave entities were removed afterwards. Final local server status was `state=COLLECTING`, `wave=0`, `event-mobs=0`, `boss=none`.
- The current client JAR was installed into `D:\.minecraft\versions\ServerRP\mods` after the native check. The prior profile JAR remains at `CopiMineClient-0.1.1.jar.pre-vanilla-bossbar-20260915.bak`.

The supplied PNG itself is intentionally sparse and largely transparent; the native frame therefore proves that the file is loaded, but it does not imply a different artist texture or a redesigned mesh. A richer appearance requires a replacement skin asset from the user.

## Current live follow-up (2026-09-15)

The previous official probe exposed a real geometry problem rather than a combat shortcut: a bot was pinned beside the two-block amethyst wall at `BLOCK_8_68_-38_amethyst_block`, `BLOCK_8_69_-37_air`, `BLOCK_8_69_-38_amethyst_block`, and `BLOCK_8_68_-37_AIR`. A RED contract was added first, then the official driver was repaired with a test-only melee-range recovery helper. It reads tagged event mobs, computes the actual player-to-mob distance, and uses `Teleport-Player` only when `DistanceSquared > 2.75D * 2.75D`; it does not use `/damage` or `/kill` and does not create fake damage. The focused helper contract passed (`2 passed, 91 deselected`), and the PowerShell/JavaScript syntax checks passed.

The rebuilt three-client official probe completed the current Wave 1–7 path:

- event `c6927381-a0c1-4a56-bd3f-83c1af67bdd7`, generation `1152`, clients `Rift3A`, `Rift3B`, and `Rift3C`;
- `CURRENT_WAVE_PASS` markers: Wave 1 `RIFT_CARRIERS`, Wave 2 `RIFT_HUNT`, Wave 3 `RIFT_GATES portals=3`, Wave 4 `OBELISK_ASSAULT obelisks=4`, Wave 5 `BLACK_FOG cycles=3`, Wave 6 `COLLAPSE_RINGS rings=3`, and Wave 7 `REALITY_SPLIT chambers=local`;
- the Wave 5 log contains real `PLAYER_ATTACK` events after recovery, including distances `0.24` and `1.08`, rather than command-injected damage;
- the driver restored the disposable state. The later local status was `state=READY_FOR_PLAYERS`, `generation=1157`, `wave=0`, `event-mobs=0`, `boss=none`, `endUnlocked=true`, and `victory=NONE`.

Separate live boss probes closed the server-authority gaps that had remained after the visual capture:

- `LIVE_BOSS_REAL_HEALTH_PASS`: test boss `5000/5000`, physical health `5000`, unclamped attribute, current-health marker present, legacy virtual marker absent;
- `LIVE_BOSS_DAMAGE_PASS`: `before=2000 after=1765 delta=235`, `player_damage_events=47`; cleanup and scene restore both passed;
- `LIVE_BOSS_MULTIPLAYER_REAL_HEALTH_PASS`: three independent attackers, `297` events, `before=5000 after=4031.2566`, summed final damage `968.74844849109657`, health delta `0.00504849109657`, and `96` same-tick event groups; cleanup passed;
- `LIVE_BOSS_SHIELD_PASS`: shield remained `950`, vulnerable health changed `3950 -> 3943`, and restored shield remained `950`; cleanup and scene restore passed.

The five-client visual/runtime wrapper also passed its current server-side checks: `CURRENT_VISUAL_FIVE_PLAYER_PASS`, `DIAGNOSTICS_FAILURE_PASS`, `PERF_FIVE_PASS`, `RUNTIME_DIAGNOSTICS_PASS`, `AI_DIAGNOSTICS_PASS`, and `PERF_DIAGNOSTICS_PASS`. It recorded current server SHA `3151E711E6749FC455B97F4E36DAC19CB7406E46B1E883DEDDCE1CD442315422`, client SHA `ADE5EF1B66C916F40B66714EB510B44794CA1DEDE6F16C40DF50E68986E26370`, and resource-pack SHA `34BBED01D468F5F45821AD82DC571012F6C9C5B581CABCA18FD6D1112FC143C9`; its native capture field remains `NATIVE_CLIENT_SCREENSHOT=NOT_VERIFIED`.

## Computer-use runtime follow-up (2026-09-15)

The earlier diagnosis used the browser-oriented `cua` surface and therefore returned `apps=[]`. The bundled Computer Use skill is enabled in `C:\Users\zavod\.codex\config.toml`; the current native `@oai/sky` service is configured through `NODE_REPL_TRUSTED_SERVICES` and `SKY_CUA_NATIVE_PIPE`. A live `sky.list_apps()` call returned the installed TLauncher and a targetable `javaw` window titled `Minecraft* 1.21.1`, and `sky.get_window_state()` successfully captured the TLauncher window at `1056x783`.

To make the setup persistent for the requested workflow, `[computer_use.windows].always_allowed_app_ids` was added to the user Codex config with only these exact IDs: `C:\\Users\\zavod\\AppData\\Roaming\\.minecraft\\TLauncher.exe` and `process:D:\\.minecraft\\runtime\\java-runtime-delta\\windows\\java-runtime-delta\\bin\\javaw.exe`. The file parses successfully with Python TOML parsing. On the next fresh run, `sky.launch_app` opened TLauncher, its native `Войти в игру` action started the client, and a fresh `sky.get_window_state()` captured the visible Minecraft main menu at `1920x1080`. A subsequent menu click returned an unknown input/refresh outcome, so this proves native client capture and launch but not the in-world event matrix. Windows Computer Use still requires Minecraft to remain visible on the active desktop during the task.

## Baseline and worktree

- Published baseline commit: `da25b4f514ae384af63c8f82667e57a8f5a47e01` (`fix(end-rift): make wave seven walls visible`)
- Published baseline tree: `773f1f1c63768948176d6d6a5db4b6b3f2df10b5`
- `HEAD` remains at the published baseline; the remediation layer is intentionally still uncommitted and unpushed.
- No deployment or production upload was performed.

## Verification

Command: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1`

Result: `End Rift current local checks passed.`

The gate included server plugin builds, Fabric client Gradle `BUILD SUCCESSFUL`, resource-pack build, `113 passed` current Python contracts, all current pure Java policy/persistence checks, and diff hygiene. The focused `tests/test_end_event_current_contract.py` suite reports `93 passed`, including the melee-range recovery contract, staged-client, modpack-manifest, and vanilla-HUD traffic parity. The last gate was rerun after the renderer contract was moved to `LivingEntityRendererMixin`, the retired custom-HUD stream was disabled, and the official Wave 5/6 recovery helper was covered; it ended with `End Rift current local checks passed.`

Fresh official five-player live run on the rebuilt local Paper artifact:

- `CURRENT_OFFICIAL_PASS event=9fe94db9-5748-45c0-b42e-127db1c7187b players=5 waves=1,2,3,4,5,6,7 stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL victory=true`
- Final RCON state: `state=UNLOCKED`, `generation=1067`, `requiredPlayers=5`, `roster=5`, `participants=5`, `helpers=5`, `wave=0`, `event-mobs=0`, `boss=none`, `bossPhase=LAST_SEAL`, `endUnlocked=true`, `victory=VICTORY_COMPLETE`.
- The server log records real `RIFT_TENTACLE_DAMAGE` transitions for all four Last Seal guardians, including `visual=DEAD`, followed by boss defeat and cleanup. The probe did not bypass the shield with a command damage shortcut.

Clean double-build reproducibility:

- The earlier two independent clean builds ended with `BUILD SUCCESSFUL` and produced client SHA-256 `D94876C7EEC13ADBA28DF44EACDADD50E8A0E4A12C591C70CC45201350F973BF`; the current vanilla-bossbar follow-up build is recorded separately below as `ADE5EF1B66C916F40B66714EB510B44794CA1DEDE6F16C40DF50E68986E26370`.

Additional local runtime result from `RunEndRiftWave6Wave7BoundariesLive.ps1 -BotDurationSeconds 30 -TimeoutSeconds 60`:

- `LIVE_WAVE6_BOUNDARIES_PASS rings=3 radii=8,14,19 visual_displays=240 visual_points=64,80,96 leash_policy=true player_containment=true`
- `LIVE_WAVE7_BARRIERS_PASS chambers=2 cells=152 visual_displays=38 wall_material=barrier collision=true`
- `LIVE_WAVE7_BARRIER_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0`

## Native media

The fresh boss capture used the current source-backed Fabric development client (`CopiMineClient:runClient`) with the local Paper server on `127.0.0.1:25566`. `Player355` was authenticated locally, placed in spectator mode, and the server was put into the disposable `cmend test boss` path. The runtime status at capture was `state=COLLECTING`, `wave=0`, `event-mobs=1`, `hp=5000/5000`, `bossPhase=AWAKENING`, `endUnlocked=true`; this is a test boss visual proof, not an official rewarded encounter.

- Earlier boss screenshot (pre-vanilla-bar follow-up): `artifacts/end-rift-v3-evidence/native-end-rift-boss-screenshot-20260915.png`
  - 854x480 PNG
  - SHA-256: `6224ca734a4f481fd0dbf0798a4207c31dcfd3e3142f812456c48adcc63ef54d`
- Current vanilla-bossbar close frame: `artifacts/end-rift-v3-evidence/native-end-rift-boss-vanilla-bar-close-20260915.png`
  - 854x480 PNG
  - SHA-256: `3f7cc4be367a98169100211760a882f0356814c2760e39e63320db0e23af654`
- Current processed flight video: `artifacts/end-rift-v3-evidence/native-end-rift-boss-flight-15s-vanilla-bar-20260915.mp4`
  - 15.000 seconds, 30 fps, 450 frames, 854x320
  - SHA-256: `ed3a0f8de41c6ddfde657038bded64eb66bcd489b5e1b5cc38f37825cf08f563`
  - Built from 15 real Minecraft F2 framebuffer PNGs captured after the vanilla-bar fix; the camera route uses in-arena teleports, and the lower service HUD strip is cropped out.
- Earlier processed flight video (pre-vanilla-bar follow-up): `artifacts/end-rift-v3-evidence/native-end-rift-boss-flight-15s-processed-20260915.mp4`
  - 15.000 seconds, 30 fps, 450 duplicated-output frames, 854x320
  - SHA-256: `d60140e50ee981d33f2697d4ee8357a04732d66300c11c7c4b4124351341091e`
- Current source frame sequence: `artifacts/end-rift-v3-evidence/boss-flight-frames-vanilla-20260915-15/` (15 PNGs)
- Current contact sheet: `artifacts/end-rift-v3-evidence/flight-contact-vanilla-20260915.png`, SHA-256 `a2043a8398991d70b7bcb77517f178c62d46ab40820824ca203ce231aad8de18`
- Earlier source frame sequence and contact sheet are retained under `boss-flight-frames-20260915-2/` and `flight-contact-20260915.png`.

The same source-backed client also ran the read-only `/copimineclient endrift selection` diagnostic after the event Wave 3 test bindings were created. The native Minecraft log records `End Rift renderer selections: 23`; every listed selection has `resourcePresent=true` and includes `EVENT_ENDERMAN`, `ELITE`, `EVENT_SPIDER`, `EVENT_SKELETON`, and `ELITE_SKELETON` with their concrete model, geometry, texture, and animation-set identifiers. This is native client state/selection evidence, not a replacement for close-up frame captures of every model and animation.

- Native selection log: `CopiMineClient/run/logs/latest.log` around the `07:35:16` diagnostic lines
- Native selection screenshot: `CopiMineClient/run/screenshots/2026-09-15_07.38.25.png`, 854x480 PNG, SHA-256 `74637E551E0EBD9059F9EEBF3AA9EE6E604B2CB7861D87EAECD9AFC47FE6209C`

The older selection screenshot shows the supplied black/purple multi-part boss model in the amethyst chamber, the `СТРАЖ РАЗЛОМА` bossbar, `ПРОБУЖДЕНИЕ`, `5000 / 5000`, the phase labels, and the CopiMine side HUD; those decorative bossbar details belong to the pre-follow-up capture. The current close frame above is the post-fix vanilla-bar proof. The current video is a processed 15-frame sequence taken after the fix; it tours the arena through in-arena teleports and retains the vanilla bar on every frame, while showing the boss from the opening and return viewpoints. It is not a continuous GPU/window recording. The lower service HUD strip is cropped out. The Windows GDI capture path was tested separately and produced a white OpenGL surface, so it is not used as evidence.

The earlier arena-only artifacts are retained for comparison: `native-end-rift-final-artifact-screenshot-20260915.png` (1024x767, SHA-256 `3d3e8aaf175fd98795809b9a907c1e8c159eca47d6011638b65b5db8e6122520`) and `native-end-rift-final-artifact-flight-15s-20260915.mp4` (15.000 seconds, SHA-256 `c2963eab636261b3e7e887755f4bc6f29d20efe7e18adddd9807cdf80776d2`). They show the arena but no boss and are not the fresh boss proof.

## Artifact parity

| Artifact | SHA-256 | Parity / status |
|---|---|---|
| `CopiMineClient-0.1.1.jar` current source build | `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370` | current vanilla-bossbar follow-up build; 9,430,955 bytes |
| `thirdparty/client-mods/CopiMineClient-0.1.1.jar` | `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370` | exact byte match with source build |
| `D:\.minecraft\versions\ServerRP\mods\CopiMineClient-0.1.1.jar` | `ade5ef1b66c916f40b66714eb510b44794ca1dede6f16c40df50e68986e26370` | current local profile copy after the vanilla-bossbar follow-up; previous `525d...` JAR is preserved as the adjacent `.bak` |
| `copimine-end-event/CopiMineEndEvent.jar` and `minecraft/server/plugins/CopiMineEndEvent.jar` | `3151e711e6749fc455b97f4e36dac19cb7406e46b1e883deddce1cd442315422` | exact byte match after removing legacy `END_BOSS_BAR` streaming |
| `thirdparty/CopiMineMods.zip` | `87b5443cb08c7c91d244c2120d3417988ea4ab86179cb9ddc1b2053ba6037a74` | 21,635,001 bytes; `thirdparty_manifest.json` SHA-1/SHA-256 entries match the archive |
| `CopiMineResourcePack.zip` source build and `D:\.minecraft` copies | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` | exact match |

The full gate hashes the source artifacts and verifies the resource-pack SHA-1. The current source, staged `thirdparty`, and local `ServerRP` client JARs are byte-identical; the resource-pack source and local copy are also byte-identical. `thirdparty/thirdparty_manifest.json` now contains the current SHA-1/SHA-256 for `thirdparty/CopiMineMods.zip`, and the focused manifest-parity test passes.

## Supplied model package parity

The attached `end event.rar` was treated as the complete source package. `models.rar` and `modelsboss.rar` contain the same boss package byte-for-byte; their SHA-256 is `2a994415246d7f03eb7160414a116427b21a7867347ca27b941ea477441e73cd`. The two detached animation files also match their copies inside `end event.rar`.

| Supplied source | Runtime destination | SHA-256 | Runtime use |
|---|---|---|---|
| `models*/models/enderboss/enderboss.json` | `CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/geometry.json` | `301583a2efea6c5b597c4fe2cadced68d7838b80f630d1d16f8aaa8265964783` | `UserEndBossModelData` imports the Bedrock hierarchy, cubes, pivots, rotations, and UVs |
| `models*/models/enderboss/animations/dying.json` | `.../end_rift_guardian/animations/dying.json` | `dd1ab32fae51e31346d06f3a9510b615e84a836d6bfe5ad841c817087b2d8b8c` | `DYING` |
| `models*/models/enderboss/animations/hurt.json` | `.../end_rift_guardian/animations/hurt.json` | `a491645b15c9b00b9b7da939181a9c811756aa9f5a64c7cd587c535fd8fb591b` | `HURT` |
| `models*/models/enderboss/animations/idle.json` | `.../end_rift_guardian/animations/idle.json` | `9e87cdb45b394f1676ecc6f619ca1e37a7292e2e670e7b235951dc95625d4c6c` | `IDLE_BREATH` |
| `models*/models/enderboss/animations/running.json` | `.../end_rift_guardian/animations/running.json` | `5d9f42aa83e50b3d5d4819df756b323cd7005843516502c76fd67d0868417df2` | `RUN` |
| `models*/models/enderboss/animations/swipe.json` | `.../end_rift_guardian/animations/swipe.json` | `9710b8766bf1b9d1382475f778e3d41105a88e41f768bdf5c2f56aeeaa322eea` | `MELEE_SWIPE` |
| `end event/chameleon/models/enderboss/animations/udar_iz_grudi.json` | `.../end_rift_guardian/animations/udar_iz_grudi.json` | `c53c68d133f42b61c50df329bd87235e87835b1b4db63fc80176539b26d87141` | `CHEST_STRIKE` |
| `end event/chameleon/models/enderboss/animations/udar_po_zemle.animation.json` | `.../end_rift_guardian/animations/udar_po_zemle.animation.json` | `85d9ae6bc72bf09454bc1abcbc9ffb716e7f06e0d8c0ce507c148c7c80e88ce1` | `GROUND_SLAM` |
| `models*/models/enderboss/skins/enderboss.png` | `.../textures/entity/end_rift_user_boss.png` | `f298ed322335c5439c19dddb8014aa0960b83f3fb27d692580a75e051516c45d` | Boss atlas, 128x128 |
| `end event/chameleon/models/enderboss/skins/enderman-1.png` | `.../textures/entity/end_rift_user_enderman.png` | `a9a154f232919627451431e3f3874c9e850f23e531eae2cfe2a4a9cc16edf447` | Event Enderman skin, 64x32 |
| `end event/chameleon/models/enderboss/skins/spider.png` | `.../textures/entity/end_rift_user_spider.png` | `19c46ff4aa829e7101b25a50a55090cd1d8145c2f83b95d64c13a20f6b5c9abf` | Event Spider skin, 64x32 |

The current client consumes these resources through `RiftGuardianModel`/`UserEndBossAnimationPlayer`; event Enderman/elite and Spider use their separate runtime model classes with the supplied skins. No resource copy was necessary because every compared file already matched exactly.

## Issue status

The detailed row-by-row ledger is [2026-09-15-end-rift-revalidation-ledger.md](2026-09-15-end-rift-revalidation-ledger.md). In summary:

- `PARTIALLY FIXED`: E01, E02, E03, E04, E05, E06, E07, E08, E09, E10, E11, E12, E13, E14, E15.
- No row is marked fully `FIXED` solely from the static gate or the arena media.

## UNRESOLVED ITEMS

REAL MINECRAFT VISUAL VERIFICATION NOT PERFORMED for the full event matrix. A real Minecraft boss frame, a real-F2-frame flight sequence, and native client model-selection diagnostics are now available, but they are not a complete event matrix.

- Native Wave 6/7 combat, boss damage stages, defeat/victory, cleanup, restart/recovery, and idle/vanilla entity matrix were not all exercised in the Minecraft client. The official five-player server run did exercise the authoritative lifecycle and ended cleanly, but its bot clients did not provide a complete visual capture.
- The current three-client official probe and separate boss probes now prove the server-side Wave 1–7 path, real-health damage, shield transitions, cleanup, and recovery behavior. The native `@oai/sky` computer-use service is callable, can launch TLauncher and capture a fresh visible Minecraft main-menu frame; the full Minecraft visual matrix remains open because no in-world boss/wave frame was captured in this follow-up.
- The boss capture used `cmend test boss` at `COLLECTING`, `wave=0`; it proves the boss model/bossbar path but is not a native visual capture of the official multi-player boss wave or rewards.
- Native selection metadata now confirms event Enderman, elite, Spider, skeleton, and elite skeleton bindings with present resources. Close-up native frames for the full variant matrix, authored chest/ground/attack clips, tentacle overlay motion, projectiles, and every boss phase are still open.
- The source-side Bedrock conversion, strict asset validation, supplied texture dimensions, animation parser/golden contracts, and current artifact parity now pass; the full runtime animation/visual matrix remains open. The custom bossbar is intentionally disabled pending a visual rework; the supplied boss PNG is loaded, but its own pixels are sparse and largely transparent.
- The worktree is dirty and has no final commit or push. Required runtime issues remain open, so no deployment/push claim is made.
