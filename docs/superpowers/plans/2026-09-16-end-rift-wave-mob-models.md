# End Rift Wave Mob Models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the role-collapsed End Rift wave visuals with clean, assembled, low-noise models for every ordinary, elite, guardian, ritual-guard, and ritual-caster variant, while preserving authoritative server hitboxes and producing inspectable previews for each model.

**Architecture:** The server will publish a distinct visual ID for each gameplay role/type combination. The Fabric client will resolve that ID to a dedicated model instance and a hand-authored opaque UV atlas; render-only parts remain outside the server collision boundary. A deterministic Pillow preview tool will assemble the same named visual roles into front/three-quarter cards and a review board, while native Minecraft capture remains a separate evidence level.

**Tech Stack:** Java 21, Fabric Loom 1.8.13, Minecraft 1.21.1/Yarn `1.21.1+build.3`, Fabric API `0.116.12+1.21.1`, JUnit 5, Python 3, Pillow, Paper/Bukkit server bridge, GitHub branch `codex/end-rift-event`.

**Spec:** `docs/superpowers/specs/2026-09-16-end-rift-visual-fidelity-design.md`

## Global Constraints

- Preserve the supplied boss geometry, imported animations, texture binding, and existing boss hitbox contract; do not replace the boss with a new simplified mesh.
- Keep all decorative model parts client-side and render-only; server collision, damage, targeting, and generation ownership remain authoritative.
- Every entity atlas is opaque at its declared dimensions and uses deliberate connected shapes, not checkerboards, random noise, or guide lines.
- The server must keep ordinary, elite, special guardian, ritual guard, and ritual caster visual IDs distinct.
- `NOT VERIFIED IN GAME` is the only valid native-runtime result when Computer Use has no native Minecraft app surface.
- Do not install or deploy production artifacts; update source/staged repository artifacts only after the client build and parity checks pass.
- Do not stage or delete the pre-existing untracked files under `artifacts/end-rift-v3-evidence/` or `tests/.packet-trace-manifest.mf` unless a new proof file is explicitly added.

---

### Task 1: Make every entity role explicit in the visual contract

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java:270-281,24949-24980,4701-4730`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java:70-90`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndermanRendererSelection.java:40-180`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java:70-160`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/SkeletonEntityRendererMixin.java:20-60`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/SpiderEntityRendererMixin.java:15-35`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndermanRendererSelectionTest.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndEventTextureCatalogTest.java`
- Test: `tests/test_end_event_current_contract.py`

**Interfaces:**
- Produces these stable IDs and texture names for later model tasks:

| Entity role | Visual ID | Texture |
|---|---|---|
| ordinary Enderman | `END_RIFT_ENDERMAN_V1` | `end_rift_user_enderman.png` |
| elite Enderman | `END_RIFT_ELITE_V1` | `end_rift_elite.png` |
| special Wave Guardian Enderman | `END_RIFT_WAVE_GUARDIAN_V1` | `end_rift_wave_guardian.png` |
| Wave 6 ritual caster | `END_RIFT_RITUAL_CASTER_V1` | `end_rift_ritual_caster.png` |
| ordinary Skeleton | `END_RIFT_SKELETON_V1` | `end_rift_skeleton.png` |
| elite Skeleton | `END_RIFT_ELITE_SKELETON_V1` | `end_rift_elite_skeleton.png` |
| special ritual-guard Skeleton | `END_RIFT_RITUAL_GUARD_V1` | `end_rift_ritual_guard.png` |
| ordinary Spider | `END_RIFT_SPIDER_V1` | `end_rift_user_spider.png` |
| elite Spider | `END_RIFT_ELITE_SPIDER_V1` | `end_rift_elite_spider.png` |
| special Wave Guardian Spider | `END_RIFT_WAVE_GUARDIAN_SPIDER_V1` | `end_rift_wave_guardian_spider.png` |

- Consumes the existing server `EVENT_KIND_*` tags; it does not change the
  entity type or collision profile.

- [ ] **Step 1: Add failing selection/catalog assertions.** Extend the JUnit
  tests with exact expectations for the four new IDs and add Python assertions
  that the server maps `EVENT_KIND_WAVE_GUARDIAN`, `EVENT_KIND_RITUAL_GUARD`,
  and elite spiders to those IDs. The test shape is:

```java
@Test
void specialWaveVariantsUseTheirOwnVisualContracts() {
    assertEquals(EndermanRendererSelection.Kind.WAVE_GUARDIAN,
            EndermanRendererSelection.selectVisual(
                    ENTITY_UUID, "END_RIFT_WAVE_GUARDIAN_V1", null,
                    Identifier.of("copimineclient",
                            "textures/entity/end_rift_wave_guardian.png"), true).kind());
    assertEquals("end_rift_wave_guardian_v1",
            EndermanRendererSelection.selectVisual(
                    ENTITY_UUID, "END_RIFT_WAVE_GUARDIAN_V1", null,
                    Identifier.of("copimineclient",
                            "textures/entity/end_rift_wave_guardian.png"), true).geometryId());
}
```

- [ ] **Step 2: Run the new tests and verify the expected RED failure.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test --tests me.copimine.client.EndermanRendererSelectionTest --tests me.copimine.client.EndEventTextureCatalogTest`

Expected: FAIL because the new IDs and selection kind do not exist yet.

- [ ] **Step 3: Implement the smallest explicit mapping.** Add
  `WAVE_GUARDIAN` to `EndermanRendererSelection.Kind`; return explicit
  `Decision` records for the new Enderman ID; add all new catalog entries; and
  choose IDs in the server mapping as follows:

```java
if (entity.getType() == EntityType.SPIDER) {
    if (EVENT_KIND_WAVE_GUARDIAN.equals(kind)) return CLIENT_VISUAL_WAVE_GUARDIAN_SPIDER;
    if (EVENT_KIND_ELITE.equals(kind)) return CLIENT_VISUAL_ELITE_SPIDER;
    return CLIENT_VISUAL_SPIDER;
}
if (entity.getType() == EntityType.SKELETON) {
    if (EVENT_KIND_RITUAL_GUARD.equals(kind)) return CLIENT_VISUAL_RITUAL_GUARD;
    return isSkeletonMiniBoss(entity) ? CLIENT_VISUAL_ELITE_SKELETON : CLIENT_VISUAL_SKELETON;
}
if (entity.getType() == EntityType.ENDERMAN) {
    if (EVENT_KIND_WAVE_GUARDIAN.equals(kind)) return CLIENT_VISUAL_WAVE_GUARDIAN;
    if (EVENT_KIND_RITUAL_CASTER.equals(kind)) return CLIENT_VISUAL_RITUAL_CASTER;
    if (EVENT_KIND_ELITE.equals(kind)) return CLIENT_VISUAL_ELITE;
    return CLIENT_VISUAL_ENDERMAN;
}
```

Update `clientVisualResourcePath`, texture mixins, and renderer diagnostics with
the same IDs. Keep `END_RIFT_GUARDIAN_V1` reserved for the imported boss.

- [ ] **Step 4: Run the focused tests and confirm GREEN.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test --tests me.copimine.client.EndermanRendererSelectionTest --tests me.copimine.client.EndEventTextureCatalogTest` and `python -m pytest -q tests/test_end_event_current_contract.py tests/test_end_event_wave_mob_visual_contract.py tests/test_wave6_ritual_caster_behavior_contract.py`.

Expected: all focused tests pass before model code is changed.

- [ ] **Step 5: Commit the contract boundary.**

```powershell
git add copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java CopiMineClient/src/main/java/me/copimine/client tests/test_end_event_current_contract.py CopiMineClient/src/test/java/me/copimine/client/EndermanRendererSelectionTest.java CopiMineClient/src/test/java/me/copimine/client/EndEventTextureCatalogTest.java
git commit -m "feat: separate End Rift mob visual roles"
git push origin codex/end-rift-event
```

### Task 2: Build dedicated Enderman-role rigs without touching hitboxes

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/RiftEventEndermanModelTest.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndermanRendererSelectionTest.java`

**Interfaces:**
- `RiftEventEndermanModel.Variant` has `ORDINARY`, `ELITE`,
  `WAVE_GUARDIAN`, and `RITUAL_CASTER` values.
- `RiftEventEndermanModel.getTexturedModelData(Variant variant)` returns a
  `TexturedModelData` with a 64×32 atlas.
- `RiftEventEndermanModelRenderer.modelFor(Kind kind)` returns one stable
  model instance per variant and returns `null` for boss/vanilla kinds.

- [ ] **Step 1: Add failing geometry tests for every role.** Assert that each
  model has the named parts required for its silhouette and that the renderer
  returns different instances for all four roles:

```java
@Test
void everyEndermanRoleHasDistinctReadableParts() {
    for (RiftEventEndermanModel.Variant variant : RiftEventEndermanModel.Variant.values()) {
        RiftEventEndermanModel model = new RiftEventEndermanModel(
                RiftEventEndermanModel.getTexturedModelData(variant).createModel(), variant);
        assertTrue(model.getPart().getChild("body_shell") != null);
        assertTrue(model.getPart().getChild("chest_rift") != null);
        assertTrue(model.getPart().getChild("left_forearm") != null);
        assertTrue(model.getPart().getChild("right_forearm") != null);
    }
    assertNotSame(renderer.modelFor(Kind.EVENT_ENDERMAN), renderer.modelFor(Kind.ELITE));
    assertNotSame(renderer.modelFor(Kind.ELITE), renderer.modelFor(Kind.WAVE_GUARDIAN));
    assertNotSame(renderer.modelFor(Kind.WAVE_GUARDIAN), renderer.modelFor(Kind.RITUAL_CASTER));
}
```

- [ ] **Step 2: Run the test and confirm RED.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test --tests me.copimine.client.RiftEventEndermanModelTest`.

Expected: FAIL because the variant API and dedicated parts are not present.

- [ ] **Step 3: Implement the four actual geometries.** Replace the current
  boolean-only builder with a variant builder that keeps the humanoid renderer
  contract but adds connected, overlapping parts:

| Variant | Required geometry |
|---|---|
| `ORDINARY` | narrow shell, compact head, long segmented forearms, two-part shins, single chest crack |
| `ELITE` | ordinary silhouette plus shoulder plates, split horns, jaw/waist bones, larger chest core |
| `WAVE_GUARDIAN` | broader shoulder mantle, crown horns, rib plates, forearm cuffs, rear spine and brighter core |
| `RITUAL_CASTER` | ordinary body, both arms raised toward a `channel_orb`, pale cuffs, no elite shoulder mantle |

Use a separate `ModelPart` for each named part, call
`root.traverse().forEach(ModelPart::resetTransform)` before `super.setAngles`,
and apply the caster raised-arm pose only for `RITUAL_CASTER`. Keep all added
parts under the render model; do not add server-side dimensions or entity data.

- [ ] **Step 4: Wire the renderer instances and scoped swap.** Construct four
  fields in `RiftEventEndermanModelRenderer`, map the new selection kind in
  `LivingEntityRendererMixin`, and leave the guardian path routed only to
  `RiftGuardianModelRenderer`.

- [ ] **Step 5: Run the model and selection tests.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test --tests me.copimine.client.RiftEventEndermanModelTest --tests me.copimine.client.EndermanRendererSelectionTest`.

Expected: PASS with deterministic model construction and no boss regression.

- [ ] **Step 6: Commit the Enderman rigs.**

```powershell
git add CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModelRenderer.java CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java CopiMineClient/src/test/java/me/copimine/client/RiftEventEndermanModelTest.java
git commit -m "feat: add dedicated End Rift Enderman rigs"
git push origin codex/end-rift-event
```

### Task 3: Give Skeleton and Spider roles dedicated geometry

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModel.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftSpiderModel.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftSpiderModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/RiftEventSkeletonModelTest.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/RiftSpiderModelTest.java`

**Interfaces:**
- `RiftEventSkeletonModel.Variant` has `ORDINARY`, `ELITE`,
  `RITUAL_GUARD`; `WAVE_GUARDIAN` is an additional skeleton variant when the
  server binds that role.
- `RiftSpiderModel.Variant` has `ORDINARY`, `ELITE`, `WAVE_GUARDIAN`.
- Each renderer owner exposes `modelFor(String visualId)` or a typed variant
  and returns a stable instance for each ID.

- [ ] **Step 1: Add failing tests for skeleton roles.** Create
  `RiftEventSkeletonModelTest` and assert named jaw/rib/forearm/knee parts for
  ordinary, shoulder/horn parts for elite, guard sigil/crest for ritual guard,
  and distinct renderer instances.

```java
@Test
void skeletonRolesAreNotTextureOnlyVariants() {
    RiftEventSkeletonModel elite = model(RiftEventSkeletonModel.Variant.ELITE);
    RiftEventSkeletonModel guard = model(RiftEventSkeletonModel.Variant.RITUAL_GUARD);
    assertFalse(elite.getPart().getChild("elite_shoulder_left").isEmpty());
    assertFalse(elite.getPart().getChild("elite_horn_left").isEmpty());
    assertFalse(guard.getPart().getChild("guard_crest").isEmpty());
    assertFalse(guard.getPart().getChild("guard_chest_seal").isEmpty());
}
```

- [ ] **Step 2: Add failing tests for spider roles.** Extend
  `RiftSpiderModelTest` to construct each variant, assert eight articulated leg
  roots, a body shell, and role-only parts such as `elite_carapace` and
  `guardian_spine`; assert that `RiftSpiderModelRenderer` owns three distinct
  instances.

- [ ] **Step 3: Run the new tests and confirm RED.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test --tests me.copimine.client.RiftEventSkeletonModelTest --tests me.copimine.client.RiftSpiderModelTest`.

Expected: FAIL on the missing variant builders/parts.

- [ ] **Step 4: Implement skeleton variants.** Keep the current segmented
  anatomy but move it behind `Variant`; add non-overlapping connected shoulder,
  horn, chest-seal, elbow-bone, knee-bone, and shin parts. The ritual guard
  gets a vertical seal and raised weapon-ready shoulders; the elite gets horns
  and layered shoulder plates; the ordinary variant remains the least adorned.
  Reset all transforms before vanilla skeleton arm/leg animation.

- [ ] **Step 5: Implement spider variants.** Replace the single model instance
  with variant-specific geometry: ordinary has a compact shell and eight thin
  legs, elite adds a raised carapace and two front fangs, and the guardian adds
  a central spine/crest and reinforced leg joints. Preserve vanilla spider leg
  animation and keep every added part render-only.

- [ ] **Step 6: Wire selection and render swapping.** Map skeleton visual IDs to
  the correct model variant and spider IDs to the three renderer instances in
  `LivingEntityRendererMixin`; update the texture mixins without touching
  vanilla entities that have no server binding.

- [ ] **Step 7: Run the full client unit suite.**

Run: `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon test`.

Expected: `BUILD SUCCESSFUL` and all existing plus new JUnit tests pass.

- [ ] **Step 8: Commit the Skeleton/Spider rigs.**

```powershell
git add CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModel.java CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModelRenderer.java CopiMineClient/src/main/java/me/copimine/client/RiftSpiderModel.java CopiMineClient/src/main/java/me/copimine/client/RiftSpiderModelRenderer.java CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java CopiMineClient/src/test/java/me/copimine/client/RiftEventSkeletonModelTest.java CopiMineClient/src/test/java/me/copimine/client/RiftSpiderModelTest.java
git commit -m "feat: add dedicated End Rift Skeleton and Spider rigs"
git push origin codex/end-rift-event
```

### Task 4: Paint and audit every UV atlas

**Files:**
- Modify: `CopiMineClient/tools/generate_end_rift_texture_atlases.py`
- Create: `CopiMineClient/tools/validate_end_rift_mob_uv.py`
- Modify: `tests/test_end_event_wave_mob_visual_contract.py`
- Modify: `tests/test_end_event_current_contract.py`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_wave_guardian.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_ritual_guard.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_elite_spider.png`
- Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_wave_guardian_spider.png`

**Interfaces:**
- `generate_end_rift_texture_atlases.py` remains the single deterministic
  generator for all 64×32 wave entity sheets.
- `validate_end_rift_mob_uv.py` accepts `--root` and returns non-zero for a
  missing atlas, wrong size, non-opaque alpha, an uncontrolled colour, or a
  declared palette larger than the role limit.

- [ ] **Step 1: Add failing image/UV tests.** Extend the Python contract with
  the four new filenames and add a role table whose expected dimensions are
  `(64, 32)`, alpha is exactly `{255}`, and palette limits are `14` for normal
  variants and `16` for bone-heavy variants. Add a test that runs:

```python
result = subprocess.run(
    [sys.executable, str(UV_TOOL), "--root", str(ROOT)],
    capture_output=True, text=True,
)
assert result.returncode == 0, result.stderr
```

- [ ] **Step 2: Run the image tests and confirm RED.**

Run: `python -m pytest -q tests/test_end_event_wave_mob_visual_contract.py`.

Expected: FAIL because the four new assets and UV auditor do not exist.

- [ ] **Step 3: Implement deterministic, hand-authored role sheets.** Add
  functions `wave_guardian_sheet`, `ritual_guard_sheet`,
  `elite_spider_sheet`, and `wave_guardian_spider_sheet` using connected
  silhouette islands: dark shell, a small number of mid-tone planes, pale bone
  joints, and one controlled magenta/rift accent. Never draw a full atlas grid,
  random pixels, or transparent holes. Call all functions from `main()` and
  leave existing supplied boss assets unchanged.

- [ ] **Step 4: Implement the UV auditor.** Read PNG headers through Pillow,
  calculate alpha and colour sets, and print one deterministic line per file:

```text
END_RIFT_UV_PASS id=END_RIFT_ELITE_SPIDER_V1 size=64x32 alpha=opaque colors=13
```

Reject a sheet when alpha is not opaque, size is wrong, or any colour violates
the purple/bone palette predicate used by the tests.

- [ ] **Step 5: Regenerate every atlas and run the auditor.**

Run: `python CopiMineClient/tools/generate_end_rift_texture_atlases.py` and `python CopiMineClient/tools/validate_end_rift_mob_uv.py --root .`.

Expected: one `END_RIFT_UV_PASS` per ordinary/elite/special/caster atlas and no
file outside the intended texture directory is changed.

- [ ] **Step 6: Run image and source contracts.**

Run: `python -m pytest -q tests/test_end_event_wave_mob_visual_contract.py tests/test_end_event_current_contract.py`.

Expected: PASS with all role atlases opaque and staged visual IDs present in
the source contract.

- [ ] **Step 7: Commit the atlases and audit tool.**

```powershell
git add CopiMineClient/tools/generate_end_rift_texture_atlases.py CopiMineClient/tools/validate_end_rift_mob_uv.py CopiMineClient/src/main/resources/assets/copimineclient/textures/entity tests/test_end_event_wave_mob_visual_contract.py tests/test_end_event_current_contract.py
git commit -m "art: add clean End Rift role atlases"
git push origin codex/end-rift-event
```

### Task 5: Assemble every model into reviewable previews

**Files:**
- Create: `CopiMineClient/tools/render_end_rift_mob_previews.py`
- Modify: `CopiMineClient/tools/render_wave_mob_visual_proof.py`
- Create: `artifacts/end-rift-v3-evidence/end-rift-mob-model-board-20260916.png`
- Create: one assembled PNG per role under `artifacts/end-rift-v3-evidence/model-previews/`
- Create: `docs/superpowers/reports/2026-09-16-end-rift-mob-model-preview.md`

**Interfaces:**
- `render_end_rift_mob_previews.py --output <path> --texture-root <path>` writes
  one 1600×1000 RGB board and individual 420×620 RGB PNG cards.
- Each card is labelled with the exact visual ID, role, entity type, atlas
  filename, and `static assembled preview`; the board includes the user
  references only as references and never labels a software preview as native.

- [ ] **Step 1: Add the preview contract test.** Assert that the script has a
  `ROLE_SPECS` entry for all ten role/type combinations and that a generated
  card has a non-background foreground bounding box, a visible two-tone body,
  and no transparent output pixels.

- [ ] **Step 2: Run the preview test and confirm RED.**

Run: `python -m pytest -q tests/test_end_event_wave_mob_visual_contract.py -k preview`.

Expected: FAIL because the new preview script and board do not exist.

- [ ] **Step 3: Implement deterministic assembled cards.** Define role specs
  with named silhouette primitives matching the Java parts: head, body shell,
  arms/legs, joints, horns/crest, chest core, caster orb, and spider legs.
  Render a front view plus a small three-quarter inset; beside it render a
  scaled atlas swatch and a compact collision rectangle labelled
  `server hitbox (unchanged)`. Use clean polygons/rounded segments rather than
  rendering the UV sheet directly as the mob.

- [ ] **Step 4: Generate the board and inspect it visually.**

Run: `python CopiMineClient/tools/render_end_rift_mob_previews.py --output artifacts/end-rift-v3-evidence/end-rift-mob-model-board-20260916.png --texture-root CopiMineClient/src/main/resources/assets/copimineclient/textures/entity`.

Open the result with `view_image` and inspect every card at high detail. If a
role is disconnected, noisy, too square, missing its distinguishing part, or
has an empty atlas swatch, fix the generator/model/preview and rerun this
step before proceeding.

- [ ] **Step 5: Update the existing proof board and write the report.** Add the
  new role cards and exact output paths to `render_wave_mob_visual_proof.py`;
  write the report with a table of IDs, model variants, atlas dimensions,
  preview paths, and static/native evidence status.

- [ ] **Step 6: Commit visual proof.**

```powershell
git add CopiMineClient/tools/render_end_rift_mob_previews.py CopiMineClient/tools/render_wave_mob_visual_proof.py artifacts/end-rift-v3-evidence/end-rift-mob-model-board-20260916.png artifacts/end-rift-v3-evidence/model-previews docs/superpowers/reports/2026-09-16-end-rift-mob-model-preview.md
git commit -m "test: add assembled End Rift mob visual proof"
git push origin codex/end-rift-event
```

### Task 6: Build, stage, and verify the repository artifacts

**Files:**
- Modify: `thirdparty/client-mods/CopiMineClient-0.1.1.jar`
- Modify: `thirdparty/CopiMineMods.zip`
- Modify: `thirdparty/checksums.txt`
- Modify: `thirdparty/thirdparty_manifest.json`
- Modify: `thirdparty/modpack_manifest.json`
- Modify: `admin-web/frontend/assets/public-data/modpack_snapshot.json`
- Create: `docs/superpowers/reports/2026-09-16-end-rift-mob-model-final-verification.md`

- [ ] **Step 1: Run the client build.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File CopiMineClient/build-client.ps1`.

Expected: `BUILD SUCCESSFUL` and `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` contains the new model classes, IDs, and all new PNGs.

- [ ] **Step 2: Verify the built JAR assets.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineClientJarContainsAssets.ps1` and `python -m pytest -q tests/test_end_event_current_contract.py`.

Expected: the source JAR contains all role resources and the current staged
artifact test reports parity only after staging is refreshed.

- [ ] **Step 3: Refresh the staged client/modpack through the existing scripts.**

Use the repository packaging path, not an ad-hoc archive:

```powershell
Copy-Item -LiteralPath CopiMineClient/build/libs/CopiMineClient-0.1.1.jar -Destination thirdparty/client-mods/CopiMineClient-0.1.1.jar -Force
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/thirdparty/build_modpack.ps1 -ProjectRoot .
```

Update the manifest/checksum entries with the existing packaging helper if the
script reports a stale client hash; never hand-copy a hash from an older build.

- [ ] **Step 4: Run the complete static gate.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftEventChecks.ps1` and `CopiMineClient/.gradle-dist/gradle-8.10.2/bin/gradle.bat --no-daemon clean test`.

Expected: both commands exit `0`; report the fresh counts and hashes in the
verification report rather than reusing an older report.

- [ ] **Step 5: Commit the built/staged artifacts and verification report.**

```powershell
git add thirdparty/client-mods/CopiMineClient-0.1.1.jar thirdparty/CopiMineMods.zip thirdparty/checksums.txt thirdparty/thirdparty_manifest.json thirdparty/modpack_manifest.json admin-web/frontend/assets/public-data/modpack_snapshot.json docs/superpowers/reports/2026-09-16-end-rift-mob-model-final-verification.md
git commit -m "build: stage End Rift mob model artifacts"
git push origin codex/end-rift-event
```

### Task 7: Attempt native Minecraft evidence and complete review

**Files:**
- Create, only if a native Minecraft window is available:
  `artifacts/end-rift-v3-evidence/native-end-rift-mob-models-20260916.png`
- Create, only if a native Minecraft window is available:
  `artifacts/end-rift-v3-evidence/native-end-rift-mob-flight-20260916.mp4`
- Modify: `docs/superpowers/reports/2026-09-16-end-rift-mob-model-final-verification.md`

- [ ] **Step 1: Query Computer Use once for the current native surface.** Use
  the initialized CUA API with exactly `await cua.getState();`. Do not use
  `capture_screen_context` in this text session and do not automate a terminal
  through CUA.

- [ ] **Step 2: If Minecraft is visible, capture role evidence.** Use the
  existing local test path to show ordinary/elite/special/caster mobs, capture
  front and moving views, and use the in-game hitbox/debug view when available.
  Save only captures tied to this commit and record the Minecraft profile,
  client JAR hash, and screenshot/video paths in the report.

- [ ] **Step 3: If no native app surface exists, record the honest result.**
  Write `native_runtime=NOT VERIFIED IN GAME (Computer Use native surface unavailable)`
  and link the static board/cards as `static assembled preview`; do not claim
  the Java renderer was player-visible.

- [ ] **Step 4: Run final diff and targeted review.**

Run: `git diff HEAD~1 --stat`, `git status --short --branch`,
`git diff --check`, `python -m pytest -q tests/test_end_event_current_contract.py tests/test_end_event_boss_hitbox_contract.py tests/test_end_event_wave_mob_visual_contract.py tests/test_wave6_ritual_caster_behavior_contract.py`, and the client Gradle test command from Task 6.

Confirm that the diff contains no server hitbox mutation, every new visual ID
has a catalog entry and a texture, all preview cards are linked in the report,
and old untracked evidence remains untouched.

- [ ] **Step 5: Request code review before the final completion message.** Use
  `superpowers:requesting-code-review` against the pushed branch/PR, address
  any concrete model, mapping, build, or evidence findings, rerun the affected
  verification commands, and push the correction as a new commit.

- [ ] **Step 6: Finalize only with fresh evidence.** Report the commit list,
  GitHub PR, test/build output, artifact hashes, preview board/cards, and the
  separate native-runtime status. Never call static previews a Minecraft
  screenshot.
