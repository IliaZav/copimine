# Task 8 Report - Client boss model, phase protocol, and mob texture packaging

## Status

Implemented the optional Fabric client changes only:

- `EndEventPacket` accepts `END_BOSS_PHASE` on the existing bridge-v2 field mapping, with the phase/model id carried through the existing bounded boss-id slot.
- `EndEventClientState` tracks boss phase/model state by UUID and binding instance, rejects stale generation updates, rejects wrong event/binding/UUID phase updates, and clears phase state through the existing clear/unbind/disconnect/world-change paths.
- The Enderman renderer mixin keeps ordinary Endermen and UUID-bound elite/event Endermen on their existing texture path, while only the bound boss UUID swaps to the `RiftGuardianModel` and phase texture.
- Added a concrete `ModelPart`-backed `RiftGuardianModel` with torso, shoulders, arms, horns/shards, chest rift, and bounded idle/walk/attack/absorption/judgment transforms.
- Extended the existing texture generation workflow to produce five real 128x128 PNG phase textures.
- Added the focused Python client contract test requested by the brief.

No server/config/runtime/website/launcher/resource-pack files were edited by this task.

## TDD red run

Command:

```powershell
python -m pytest -q 'tests\test_end_rift_client_model_contract.py'
```

Output:

```text
FFFFFF                                                                   [100%]
================================== FAILURES ===================================
____ test_client_jar_packages_guardian_model_and_all_end_rift_mob_textures ____

E           AssertionError: Client JAR is missing required End Rift entries: assets/copimineclient/textures/entity/rift_guardian_absorption.png, assets/copimineclient/textures/entity/rift_guardian_awakening.png, assets/copimineclient/textures/entity/rift_guardian_catastrophe.png, assets/copimineclient/textures/entity/rift_guardian_distortion.png, assets/copimineclient/textures/entity/rift_guardian_hunter.png, me/copimine/client/RiftGuardianModel.class, me/copimine/client/RiftGuardianModelRenderer$Phase.class, me/copimine/client/RiftGuardianModelRenderer.class

______ test_phase_textures_are_real_bounded_png_assets_and_not_identical ______

E           AssertionError: Missing source texture: rift_guardian_awakening.png

_____ test_phase_protocol_uses_bridge_v2_fields_and_rejects_unknown_types _____

E       assert '"END_BOSS_PHASE"' in 'package me.copimine.client;...

_________ test_state_tracks_boss_phase_by_uuid_and_binding_generation _________

E       AssertionError: assert 'applyBossPhase' in 'package me.copimine.client;...

___ test_guardian_model_uses_modelpart_geometry_and_bounded_phase_animation ___

E       FileNotFoundError: [Errno 2] No such file or directory: 'D:\\Desktop\\Copimine\\copimine-main\\.worktrees\\end-rift-event\\CopiMineClient\\src\\main\\java\\me\\copimine\\client\\RiftGuardianModel.java'

_ test_enderman_renderer_scopes_custom_model_to_bound_boss_uuid_with_fallback _

E       AssertionError: assert 'RiftGuardianModelRenderer' in 'package me.copimine.client.mixin;...

=========================== short test summary info ===========================
FAILED tests/test_end_rift_client_model_contract.py::test_client_jar_packages_guardian_model_and_all_end_rift_mob_textures
FAILED tests/test_end_rift_client_model_contract.py::test_phase_textures_are_real_bounded_png_assets_and_not_identical
FAILED tests/test_end_rift_client_model_contract.py::test_phase_protocol_uses_bridge_v2_fields_and_rejects_unknown_types
FAILED tests/test_end_rift_client_model_contract.py::test_state_tracks_boss_phase_by_uuid_and_binding_generation
FAILED tests/test_end_rift_client_model_contract.py::test_guardian_model_uses_modelpart_geometry_and_bounded_phase_animation
FAILED tests/test_end_rift_client_model_contract.py::test_enderman_renderer_scopes_custom_model_to_bound_boss_uuid_with_fallback
6 failed in 0.39s
```

## Focused contract test final run

Command:

```powershell
python -m pytest -q 'tests\test_end_rift_client_model_contract.py'
```

Output:

```text
......                                                                   [100%]
6 passed in 0.13s
```

## Fabric client build 1

Command:

```powershell
& 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient\build-client.ps1'
```

Output:

```text
> Configure project :
Fabric Loom: 1.8.13

> Task :compileJava
Note: D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient\src\main\java\me\copimine\client\mixin\ArmorFeatureRendererMixin.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :validateShaderpacks
validateShaderpacks: acid_shaders.zip -> zip-shaderpack-compatible
validateShaderpacks: crucify.zip -> zip-shaderpack-compatible
validateShaderpacks: ctr_vcr.zip -> zip-shaderpack-compatible
validateShaderpacks: cursed_metamorphopsia.zip -> zip-shaderpack-compatible
validateShaderpacks: lsd_shader.zip -> zip-shaderpack-compatible
validateShaderpacks: nms_1_6.zip -> zip-shaderpack-compatible
validateShaderpacks: trippy_shaderpack.zip -> zip-shaderpack-compatible
validateShaderpacks: white_sharp_1_2.zip -> zip-shaderpack-compatible

> Task :processResources
> Task :classes
> Task :jar
> Task :compileTestJava
> Task :processIncludeJars UP-TO-DATE
> Task :sourcesJar
> Task :processTestResources NO-SOURCE
> Task :testClasses
> Task :test
> Task :validateAccessWidener NO-SOURCE
> Task :check
> Task :remapJar
> Task :remapSourcesJar
> Task :assemble
> Task :build

BUILD SUCCESSFUL in 12s
10 actionable tasks: 9 executed, 1 up-to-date
```

## Fabric client build 2

Command:

```powershell
& 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient\build-client.ps1'
```

Output:

```text
> Configure project :
Fabric Loom: 1.8.13

> Task :compileJava UP-TO-DATE

> Task :validateShaderpacks
validateShaderpacks: acid_shaders.zip -> zip-shaderpack-compatible
validateShaderpacks: crucify.zip -> zip-shaderpack-compatible
validateShaderpacks: ctr_vcr.zip -> zip-shaderpack-compatible
validateShaderpacks: cursed_metamorphopsia.zip -> zip-shaderpack-compatible
validateShaderpacks: lsd_shader.zip -> zip-shaderpack-compatible
validateShaderpacks: nms_1_6.zip -> zip-shaderpack-compatible
validateShaderpacks: trippy_shaderpack.zip -> zip-shaderpack-compatible
validateShaderpacks: white_sharp_1_2.zip -> zip-shaderpack-compatible

> Task :processResources UP-TO-DATE
> Task :classes UP-TO-DATE
> Task :jar UP-TO-DATE
> Task :compileTestJava UP-TO-DATE
> Task :processIncludeJars UP-TO-DATE
> Task :remapJar UP-TO-DATE
> Task :sourcesJar UP-TO-DATE
> Task :remapSourcesJar UP-TO-DATE
> Task :assemble UP-TO-DATE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test UP-TO-DATE
> Task :validateAccessWidener NO-SOURCE
> Task :check
> Task :build

BUILD SUCCESSFUL in 2s
10 actionable tasks: 1 executed, 9 up-to-date
```

## Final JAR inspection

Command:

```powershell
python -c "import struct, zipfile; from pathlib import Path; jar=Path('CopiMineClient/build/libs/CopiMineClient-0.1.0.jar'); required=['me/copimine/client/RiftGuardianModel.class','me/copimine/client/RiftGuardianModelRenderer.class','me/copimine/client/RiftGuardianModelRenderer$Phase.class','copimineclient.mixins.json','assets/copimineclient/textures/entity/rift_guardian_awakening.png','assets/copimineclient/textures/entity/rift_guardian_hunter.png','assets/copimineclient/textures/entity/rift_guardian_distortion.png','assets/copimineclient/textures/entity/rift_guardian_absorption.png','assets/copimineclient/textures/entity/rift_guardian_catastrophe.png','assets/copimineclient/textures/entity/end_rift_enderman.png','assets/copimineclient/textures/entity/end_rift_elite.png','assets/copimineclient/textures/entity/end_rift_spider.png','assets/copimineclient/textures/entity/end_rift_shulker.png']; z=zipfile.ZipFile(jar); names=set(z.namelist()); missing=[x for x in required if x not in names]; print('missing=' + ','.join(missing)); print('entries=' + str(len(required)-len(missing)) + '/' + str(len(required))); blobs=[]; dimensions=[]; textures=[x for x in required if x.endswith('.png') and 'rift_guardian_' in x]; [blobs.append(z.read(x)) for x in textures if x in names]; [dimensions.append((x, struct.unpack('>II', z.read(x)[16:24]))) for x in textures if x in names]; print('phase_texture_dimensions=' + ';'.join(f'{n}:{w}x{h}' for n,(w,h) in dimensions)); print('unique_phase_texture_blobs=' + str(len(set(blobs))))"
```

Output:

```text
missing=
entries=13/13
phase_texture_dimensions=assets/copimineclient/textures/entity/rift_guardian_awakening.png:128x128;assets/copimineclient/textures/entity/rift_guardian_hunter.png:128x128;assets/copimineclient/textures/entity/rift_guardian_distortion.png:128x128;assets/copimineclient/textures/entity/rift_guardian_absorption.png:128x128;assets/copimineclient/textures/entity/rift_guardian_catastrophe.png:128x128
unique_phase_texture_blobs=5
```

SHA-256:

```text
B8621FE61F0DC0DA6F3B58FCE5F62C41B1ABEA8E1EEF02E2AC80B218D62E5E59
```

## Concerns

- Verification covered the focused client contract, Gradle/JUnit build, and final JAR contents. I did not launch a Minecraft client runtime session.
- Parent-owned server/plugin/test edits remained uncommitted and were not staged by this task.
