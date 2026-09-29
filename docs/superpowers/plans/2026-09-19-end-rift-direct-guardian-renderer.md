# Direct End Rift Guardian Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the bound End Rift guardian from the exact supplied Chameleon/Bedrock mesh and atlas without the lossy vanilla `ModelPart` geometry importer.

**Architecture:** A pure `ChameleonGuardianGeometry` module parses the checked-in source package and evaluates its absolute-pivot bone stack into testable transformed faces. `ChameleonGuardianRenderer` sends those faces to Fabric's `VertexConsumer`. `RiftGuardianModel` remains the Enderman renderer adapter but bypasses its vanilla model geometry only when the scoped guardian model is selected.

**Tech Stack:** Java 21, Fabric 1.21.1/Yarn, Gson, JOML, JUnit 5, existing Fabric mixins, Gradle 8.10.2, PowerShell deployment helper.

**Spec:** `docs/superpowers/specs/2026-09-19-end-rift-direct-guardian-renderer-design.md`

## Global Constraints

- Work only in `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` on `codex/end-rift-event`; preserve unrelated dirty changes.
- Do not alter the exact source `geometry.json` or `end_rift_user_boss.png` bytes, nor recolour or synthesize a boss atlas.
- Do not change End Rift gameplay, boss hitbox, server bindings, resource-pack protocol, or the ordinary Enderman, Skeleton, and Spider renderer routes.
- Use source coordinate conversion `(-x, -y, z)` and Chameleon transform order: parent stack, relative animation translation, absolute pivot, `Z → Y → X` rotation, then pivot return.
- Preserve every declared per-face UV rectangle, including negative `uv_size` endpoints, and draw no missing synthetic faces.
- Every production behavior change begins with a focused test that is observed failing before the implementation is written.
- Do not commit, push, restart the server, or launch/close Minecraft as part of this plan; installing an already-built JAR is allowed only after tests and artifact hashes pass and no Fabric client process is running.
- Completion requires live front and side visual evidence after the installed JAR is launched by the user; builds and static tests alone are insufficient.

## Review Focus

- Degenerate or zero-sized cube faces must preserve the source face set and not emit invalid normals; cover this in Task 2 with the source cube inventory.
- A child bone with a non-zero parent rotation must retain its absolute source pivot before the parent stack transforms it; cover this in Task 1 with `right_hand_low`.
- A rotated cube under a rotated bone must compose both rotations in source `Z → Y → X` order; cover this in Task 1 with the first `head` horn segment.
- Negative U and V sizes must reach emitted vertices without normalisation; cover this in Task 2 with a literal source `down` face.
- Direct guardian rendering must not leak to a non-bound Enderman or replace event Spider/Skeleton routes; cover this in Task 3 via the existing selection classes.

---

### Task 1: Compile the supplied absolute-pivot geometry into a testable rest pose

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/ChameleonGuardianGeometry.java`
- Create: `CopiMineClient/src/test/java/me/copimine/client/ChameleonGuardianGeometryTest.java`

**Interfaces:**
- Consumes: `UserEndBossModelData.RESOURCE`, original source JSON, texture dimensions `16×16` and `128×128`.
- Produces: `ChameleonGuardianGeometry.load()`, `int boneCount()`, `int cubeCount()`, `int faceCount()`, `List<Face> restFaces()`, and `List<Face> faces(GuardianPose pose)`.
- Produces: `ChameleonGuardianGeometry.Face` with four target-space `Vertex` values, one target-space normal, source face name, and signed texture-space UV endpoints.
- Consumed later by: `ChameleonGuardianRenderer` in Task 2 and `RiftGuardianModel` in Task 3.

- [ ] **Step 1: Write the failing geometry-contract tests**

Create `ChameleonGuardianGeometryTest` with independent literal expectations from the archived source data. The test must name the bug it catches: treating an absolute Chameleon pivot as a local vanilla `ModelPart` pivot.

```java
@Test
void compilesEverySuppliedBoneCubeAndFace() {
    ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

    assertEquals(16, geometry.boneCount());
    assertEquals(119, geometry.cubeCount());
    assertEquals(714, geometry.faceCount());
    assertEquals(714, geometry.restFaces().size());
}

@Test
void nestedLowerArmUsesTheParentStackAroundItsAbsoluteSourcePivot() {
    ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

    ChameleonGuardianGeometry.Vertex joint = geometry.transformedBonePivot("right_hand_low");

    // Hand-calculated from the Chameleon sequence. Source child pivot [8,41.25,.75]
    // is first converted with (-x,-y,z), then transformed by its parent's
    // absolute-pivot stack; it must not remain at a ModelPart-local [0.5,21.25,0].
    assertEquals(-12.24544F, joint.x(), 0.0002F);
    assertEquals(-43.31405F, joint.y(), 0.0002F);
    assertEquals(8.57257F, joint.z(), 0.0002F);
}

@Test
void rotatedHornComposesItsOwnRotationUnderTheRotatedHead() {
    ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();

    // Head cube 22 is the first rotated horn cube in the exact source data.
    // Its pivot is affected both by head rotation [7.5,0,0] and by its own
    // source Z rotation [-12.5]. A ModelPart Euler conversion loses that stack.
    ChameleonGuardianGeometry.Vertex pivot = geometry.transformedCubePivot("head", 22);

    assertEquals(6.5F, pivot.x(), 0.0002F);
    assertEquals(-76.19105F, pivot.y(), 0.0002F);
    assertEquals(-4.52798F, pivot.z(), 0.0002F);
}
```

- [ ] **Step 2: Run the focused test and verify the expected RED state**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.ChameleonGuardianGeometryTest --no-daemon
```

Expected: compilation fails because `ChameleonGuardianGeometry` does not exist. Do not proceed if the test passes or fails for an unrelated Gradle environment problem.

- [ ] **Step 3: Implement the immutable source parser and rest-pose evaluator**

Create `ChameleonGuardianGeometry` with no Fabric rendering dependency. Parse the source JSON once, retain the source `Bone`, `Cube`, and `FaceUv` records, connect each bone to its declared parent, and enumerate root bones in document order.

Implement transform evaluation as literal matrices, not Euler values decomposed through `ModelPart`:

```java
private static Matrix4f applyBoneBind(Matrix4f parent, Bone bone) {
    Vector3f pivot = toTargetPoint(bone.pivot());
    return new Matrix4f(parent)
            .translate(pivot)
            .rotateZ((float) Math.toRadians(-bone.rotation().z()))
            .rotateY((float) Math.toRadians(-bone.rotation().y()))
            .rotateX((float) Math.toRadians(bone.rotation().x()))
            .translate(-pivot.x, -pivot.y, -pivot.z);
}

private static Vector3f toTargetPoint(Point source) {
    return new Vector3f(-source.x(), -source.y(), source.z());
}
```

Build faces from source coordinates in Chameleon face order. A cube uses its source origin/size and each source face's `uv` plus signed `uv_size`; do not call `Math.abs`, create a `ModelPart`, or infer an atlas rectangle. Leave the existing `UserEndBossModelData` runtime route untouched in this task so the new pure geometry compiler has a green, isolated test boundary; Task 3 switches the active route after direct rendering is ready.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command again.

Expected: `ChameleonGuardianGeometryTest` passes; output reports the source cardinalities plus the literal lower-arm and horn-stack pivot assertions green.

- [ ] **Step 5: Run the current model tests as a read-only compatibility baseline**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.RiftGuardianModelTest --tests me.copimine.client.ImportedGuardianBindPoseTest --no-daemon
```

Expected: current model tests pass before the runtime route is switched in Task 3. Record their result; do not modify legacy `ModelPart` assertions during this isolated compiler task.

### Task 2: Emit source faces through the Fabric renderer with exact UV endpoints

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/ChameleonGuardianRenderer.java`
- Create: `CopiMineClient/src/test/java/me/copimine/client/ChameleonGuardianRendererTest.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/ChameleonGuardianGeometry.java`

**Interfaces:**
- Consumes: `ChameleonGuardianGeometry.load().faces(GuardianPose)` from Task 1.
- Produces: `ChameleonGuardianRenderer.render(MatrixStack, VertexConsumer, int light, int overlay, int color, GuardianPose pose)`.
- Produces: package-private `emit(Face, VertexSink)` used by a real `VertexConsumer` adapter in production and a recording sink in tests.
- Consumed later by: `RiftGuardianModel.render(...)` in Task 3.

- [ ] **Step 1: Write failing face-emission tests**

Create a recording `VertexSink` in the test and assert real emitted values, not a mock call count:

```java
@Test
void preservesTheSignedDownFaceUvEndpointsAndChameleonVertexOrder() {
    ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();
    ChameleonGuardianGeometry.Face sourceFace = geometry.findFace("head", 0, "down");
    List<ChameleonGuardianRenderer.EmittedVertex> emitted = new ArrayList<>();

    ChameleonGuardianRenderer.emit(sourceFace, emitted::add);

    assertEquals(4, emitted.size());
    assertEquals(38.0F, emitted.get(0).u(), 0.0001F);
    assertEquals(30.0F, emitted.get(0).v(), 0.0001F);
    assertEquals(30.0F, emitted.get(1).u(), 0.0001F);
    assertEquals(30.0F, emitted.get(1).v(), 0.0001F);
    assertEquals(30.0F, emitted.get(2).u(), 0.0001F);
    assertEquals(21.0F, emitted.get(2).v(), 0.0001F);
    assertEquals(38.0F, emitted.get(3).u(), 0.0001F);
    assertEquals(21.0F, emitted.get(3).v(), 0.0001F);
}

@Test
void sourceInventoryEmitsNoSyntheticOrMissingFaces() {
    List<ChameleonGuardianRenderer.EmittedVertex> emitted = new ArrayList<>();

    ChameleonGuardianRenderer.emitAll(ChameleonGuardianGeometry.load().restFaces(), emitted::add);

    assertEquals(714 * 4, emitted.size());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.ChameleonGuardianRendererTest --no-daemon
```

Expected: compilation fails because `ChameleonGuardianRenderer` and its `emit` contract do not exist.

- [ ] **Step 3: Implement direct `VertexConsumer` rendering**

Implement the real adapter once and keep geometry emission renderer-agnostic:

```java
static void emit(Face face, VertexSink sink) {
    for (Vertex vertex : face.vertices()) {
        sink.vertex(new EmittedVertex(vertex.x(), vertex.y(), vertex.z(),
                vertex.u(), vertex.v(), face.normalX(), face.normalY(), face.normalZ()));
    }
}

static void emitAll(List<Face> faces, VertexSink sink) {
    for (Face face : faces) {
        emit(face, sink);
    }
}
```

In `render`, use the incoming `MatrixStack.Entry` to transform positions and normals and forward them to `VertexConsumer` with incoming light, overlay, and colour. The direct renderer must never invoke `ModelPart.render`, `UserEndBossModelData.applyExactFaceUv`, or synthetic vanilla cuboid UV APIs.

- [ ] **Step 4: Run focused renderer tests and verify GREEN**

Run the Step 2 command again.

Expected: both tests pass, including the literal signed face endpoints and `2856` emitted vertices.

- [ ] **Step 5: Run source visual-contract validation**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event'
python -m pytest -q tests/test_end_event_resource_visual_contract.py
```

Expected: all resource visual-contract tests pass and confirm the source geometry/atlas have not changed.

### Task 3: Switch only the bound guardian model to the direct renderer

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java:17-148`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/UserEndBossAnimationPlayer.java:40-81`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/UserEndBossModelData.java`
- Modify: `CopiMineClient/src/test/java/me/copimine/client/RiftGuardianModelTest.java`
- Modify: `CopiMineClient/src/test/java/me/copimine/client/UserEndBossAnimationPlayerTest.java`
- Modify: `CopiMineClient/src/test/java/me/copimine/client/ImportedGuardianBindPoseTest.java`

**Interfaces:**
- Consumes: `ChameleonGuardianRenderer.render(...)` and `ChameleonGuardianGeometry.GuardianPose` from Tasks 1–2.
- Consumes: existing canonical boss animation IDs from `BossAnimationId` and the existing scoped selection in `LivingEntityRendererMixin`.
- Produces: a `RiftGuardianModel` that renders direct guardian vertices and does not mutate source geometry through surrogate Enderman limbs.
- Produces: `UserEndBossAnimationPlayer.sample(String animationId, float ticks)` returning source-bone pose deltas, not `ModelPart` mutations.
- Consumed later by: existing `LivingEntityRendererMixin` render selection, without any server change.

- [ ] **Step 1: Write the failing bound-model integration tests**

Replace obsolete tests that inspect imported `ModelPart` cuboids with behavior tests for the new boundary:

```java
@Test
void guardianRestPoseIsDirectGeometryNotVanillaEndermanCuboids() {
    RiftGuardianModel model = new RiftGuardianModel(
            RiftGuardianModel.getTexturedModelData().createModel());

    assertEquals(119, model.directGeometryCubeCount());
    assertEquals(714, model.directGeometryFaceCount());
    assertFalse(model.usesVanillaCuboidGuardianMesh());
}

@Test
void suppliedIdleAnimationChangesOnlyItsNamedSourceBones() {
    ChameleonGuardianGeometry.GuardianPose pose =
            UserEndBossAnimationPlayer.sample("IDLE_BREATH", 20.0F);

    assertTrue(pose.hasBoneDelta("head"));
    assertTrue(pose.hasBoneDelta("body"));
    assertFalse(pose.hasBoneDelta("right_leg_low"));
}
```

- [ ] **Step 2: Run focused integration tests and verify RED**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.RiftGuardianModelTest --tests me.copimine.client.UserEndBossAnimationPlayerTest --tests me.copimine.client.ImportedGuardianBindPoseTest --no-daemon
```

Expected: tests fail because the direct-geometry inspection and pose-sampling APIs do not exist; failures must not be caused by a missing resource.

- [ ] **Step 3: Replace surrogate pose mutation with source-pose sampling and override rendering**

Add a direct-render call in `RiftGuardianModel.render(...)`. The model must reset and sample a `GuardianPose` in `setAngles`, then pass it to `ChameleonGuardianRenderer` in `render`. Do not call `super.render(...)` for the scoped guardian mesh.

Refactor animation player around a returned pose:

```java
static ChameleonGuardianGeometry.GuardianPose sample(String animationId, float ticks) {
    Clip clip = CLIPS.get(canonical(animationId));
    if (clip == null) {
        return ChameleonGuardianGeometry.GuardianPose.identity();
    }
    return GuardianPose.fromClip(clip, ticks / 20.0F);
}
```

Each sampled source-bone rotation/position delta must be applied by `ChameleonGuardianGeometry.faces(pose)` at the source transform-stack boundary. Remove the guardian-specific old `ModelPart` marker transforms that changed arms, horns, shards, torso, jaw, or head without a supplied source-bone track. Keep only adapter methods necessary for `EndermanEntityModel` construction and existing scoped renderer compatibility.

- [ ] **Step 4: Run focused integration tests and verify GREEN**

Run the Step 2 command again.

Expected: all three suites pass; output confirms direct face/cube counts and source-only idle bone deltas.

- [ ] **Step 5: Run selection regression tests**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.EndermanRendererSelectionTest --tests me.copimine.client.RiftGuardianModelRendererTest --tests me.copimine.client.EndRiftTentacleModelTest --no-daemon
```

Expected: boss selection remains UUID-bound and existing non-guardian renderer tests remain green.

### Task 3b: Verify every supplied mob skin and its scoped model route

**Files:**
- Verify: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_user_boss.png`
- Verify: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_user_enderman.png`
- Verify: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_user_spider.png`
- Verify: `CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java`
- Verify: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java`
- Verify: `CopiMineClient/src/test/java/me/copimine/client/EndermanRendererSelectionTest.java`
- Verify: `CopiMineClient/src/test/java/me/copimine/client/EndEventTextureCatalogTest.java`

**Scope note:** The supplied archive contains one geometry definition (`enderboss.json`) plus three skins: `enderboss.png`, `enderman-1.png`, and `spider.png`. Do not invent geometry for the two 64×32 skins. Their correct implementation is the existing UUID/visual-scoped Enderman and Spider model route, with byte-identical runtime skin resources.

- [ ] **Step 1: Check asset identity and dimensions against the supplied archive extraction**

Run SHA-256 and dimension checks for each exact pair. Expected matches are:

| source skin | client resource | dimensions |
| --- | --- | --- |
| `enderboss.png` | `end_rift_user_boss.png` | 128×128 |
| `enderman-1.png` | `end_rift_user_enderman.png` | 64×32 |
| `spider.png` | `end_rift_user_spider.png` | 64×32 |

- [ ] **Step 2: Run scoped selection regressions**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.EndermanRendererSelectionTest --tests me.copimine.client.EndEventTextureCatalogTest --tests me.copimine.client.RiftSpiderModelTest --tests me.copimine.client.RiftEventEndermanModelTest --no-daemon
```

Expected: the supplied Enderman skin resolves to `EVENT_ENDERMAN`, the supplied Spider skin resolves through the independent Spider route, the boss route remains `GUARDIAN`, and a normal vanilla Enderman retains the vanilla route.

### Task 4: Verify, build, install, and capture live visual acceptance

**Files:**
- Modify only if build outputs require it: `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar`
- Install target: `D:\.minecraft\versions\ServerRP_copy_1\mods\CopiMineClient-0.1.1.jar`
- Verify script: `tests/SyncEndRiftClientArtifacts.ps1`

**Interfaces:**
- Consumes: all production and test changes from Tasks 1–3.
- Produces: a SHA-verified built client JAR installed into the user’s configured Fabric profile.
- Produces: live user screenshots of the direct-rendered guardian after a fresh client launch.

- [ ] **Step 1: Run the complete Fabric client test suite**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --no-daemon
```

Expected: all client tests pass. Any failure, including an unrelated existing one, is named and investigated before build/install.

- [ ] **Step 2: Build the client JAR**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient'
.\build-client.ps1
Get-FileHash '.\build\libs\CopiMineClient-0.1.1.jar' -Algorithm SHA256
```

Expected: `BUILD SUCCESSFUL` and one SHA-256 for the generated JAR.

- [ ] **Step 3: Verify the target client is closed before installation**

Run:

```powershell
Get-Process javaw -ErrorAction SilentlyContinue | Select-Object Id,Path,StartTime
```

Expected: no active Fabric/Minecraft `javaw` process. If one is active, stop here and ask the user to close it; do not overwrite a loaded JAR.

- [ ] **Step 4: Install through the guarded artifact-sync script and compare hashes**

Run:

```powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event'
.\tests\SyncEndRiftClientArtifacts.ps1 `
  -SourceClientJar 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient\build\libs\CopiMineClient-0.1.1.jar' `
  -SourceResourcePack 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\resourcepacks\build\CopiMineResourcePack.zip' `
  -ClientGameDirectory 'D:\.minecraft\versions\ServerRP_copy_1'
Get-FileHash 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\CopiMineClient\build\libs\CopiMineClient-0.1.1.jar','D:\.minecraft\versions\ServerRP_copy_1\mods\CopiMineClient-0.1.1.jar' -Algorithm SHA256
```

Expected: sync script reports success and source/install client SHA-256 values are identical. The resource-pack hash is reported but no pack contents are changed unless its generated archive hash differs from the source archive.

- [ ] **Step 5: Confirm live client binding and obtain visual evidence**

Ask the user to start `ServerRP_copy_1`, join `127.0.0.1:25566`, and send fresh unpaused screenshots of the guardian from front and side at close range. Then inspect `D:\.minecraft\versions\ServerRP_copy_1\logs\copimineclient.log` for a fresh `END_BOSS_BIND` and `End Rift texture lookup ... resourcePresent=true` line.

Expected: screenshots show one connected guardian with readable source texture, attached horns/arms/legs, and no vanilla Enderman body; log confirms the scoped guardian route. Do not mark the task complete until both visual acceptance and log binding evidence are present.
