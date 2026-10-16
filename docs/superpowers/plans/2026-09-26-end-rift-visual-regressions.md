# End Rift Visual Regression Repair Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the supplied Kagune without a permanent hit tint, make each event mob's custom geometry selection explicit, and show four distinct translucent violet shields close around the boss.

**Architecture:** Keep authoritative entity IDs, shield positions, and gameplay on the server. The client uses UUID-bound mob models and its existing custom Kagune/shield meshes; only visual presentation changes. Move Kagune import data outside Minecraft's vanilla `models` discovery path.

**Tech Stack:** Java 21, Fabric 1.21.1, Paper/Purpur plugin, Gradle, JUnit 5, Python/Pillow resource generators, pytest.

**Spec:** Current user request in this task; existing implementation references `EndRiftTentacleRenderer`, `LivingEntityRendererMixin`, `ShieldOrbitPolicy`, and the visual-contract tests.

## Global Constraints

- Keep supplied source skins and Kagune UV/animation data unchanged.
- Do not touch or restart the running Minecraft client/server during this implementation; stage verified artifacts for a safe apply window.
- Do not claim player-visible completion until the rebuilt client and server are running and a fresh screenshot confirms it.

## Review Focus

- `DAMAGED`/`CRITICAL` are persistent health levels, not hit-flash events; they must not recolor the Kagune.
- All ordinary/elite/guardian/ritual spider and skeleton visual IDs must resolve to the matching custom geometry, not just a custom texture.
- Four shield plates must remain visually distinct while their centers stay within one block of the boss.
- The shield texture must read violet under full-bright rendering and remain translucent.
- The custom Kagune JSON must be readable by its importer but not parsed as a vanilla item/block model.

---

### Task 1: Stop persistent Kagune recoloring and remove vanilla model-loader errors

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/KaguneModelImporter.java`
- Modify: `CopiMineClient/tools/import_kagune_model.py`
- Modify: `CopiMineClient/build.gradle`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndRiftTentacleModelTest.java`
- Test: `tests/test_end_event_resource_visual_contract.py`

- [x] Add tests proving `FULL`, `DAMAGED`, `CRITICAL`, and `DEAD` keep the authored texture multiplier white, and proving the importer data is emitted outside `assets/copimineclient/models/**`.
- [x] Run the focused JUnit and pytest cases; confirm they fail on the current health tint/path.
- [x] Use white vertex tint for every persistent health level; place imported Kagune data at `assets/copimineclient/geometry/end_rift_tentacle.json` and update all loader/output references.
- [x] Re-run both focused tests and verify Kagune source hash/UV/animation coverage remains unchanged.

### Task 2: Make event mob geometry routing fail closed and observable

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftSpiderModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModelRenderer.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/RiftEventSpiderRoleModelTest.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/RiftEventSkeletonModelTest.java`

- [x] Add behavior tests covering every event spider/skeleton visual ID and rejecting unknown IDs.
- [x] Run a source-contract test and confirm the duplicated mixin allowlists fail before implementation.
- [x] Use each renderer's model lookup as the single allowlist in the shared render mixin; log the first geometry selection per visual ID so runtime logs distinguish a model swap from a texture-only bind.
- [x] Re-run focused tests and verify all ordinary, elite, wave-guardian, and ritual roles select a non-null custom model.

### Task 3: Separate and recolor the boss shield orbit

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/ShieldOrbitPolicy.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndRiftGuardianShieldModel.java`
- Modify: `CopiMineClient/tools/generate_end_rift_texture_atlases.py`
- Test: `tests/ShieldOrbitPolicyTest.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndRiftGuardianShieldModelTest.java`
- Test: `tests/test_end_event_resource_visual_contract.py`

- [x] Add tests that four plates are within one block of the boss but adjacent centers are farther apart than one plate width; add a texture-palette assertion for a blue-violet rather than magenta-red face.
- [x] Run focused tests and confirm the existing 0.55-block orbit and magenta palette fail those assertions.
- [x] Set a close, non-overlapping 0.78-block orbit and shift the shield palette toward deep violet/blue while preserving alpha blending and the 9-point shield mesh.
- [x] Regenerate client/server shield atlases; run focused tests and verify identical generated bytes are used by both artifacts.

### Task 4: Build/stage and report runtime gate

**Files:**
- Build: `CopiMineClient` and the End Rift server plugin.
- Build: resource pack without changing public server properties.

- [x] Run focused tests, the End Rift regression suite, client build, plugin build, and resource-pack visual contracts.
- [x] Inspect built JAR/ZIP contents and hashes, confirming the model JSON is outside vanilla model discovery and both shield textures match.
- [x] Do not install over files used by the running game or restart its server. Report the exact safe apply/restart step and request a fresh in-game screenshot for visual acceptance.
