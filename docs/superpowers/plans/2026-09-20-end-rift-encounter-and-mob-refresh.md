# End Rift Encounter and Mob Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Make the End Rift guardian walk and fight as a readable group boss, add visible shield and hit feedback, rebuild the tentacle lifecycle/art, and install coherent requested mob visuals without mutating supplied source assets.

**Architecture:** Pure server-domain policies decide grounded movement, shield orbit positions and tentacle launches; \`CopiMineEndEvent\` adapts their decisions to Paper entities and packets. Fabric renders only server-owned tentacle/shield carriers. Supplied enderman/spider art is synchronised as immutable input; new skeleton, elite and tentacle art is generated as deterministic pixel UV atlases.

**Tech Stack:** Java 21, Paper, Fabric 1.21.1/Yarn, JUnit 5, plain Java domain tests, Python/Pillow visual contracts, Gradle 8.10.2, PowerShell.

**Spec:** \`docs/superpowers/specs/2026-09-20-end-rift-encounter-and-mob-refresh-design.md\`

## Global Constraints

- Work only in \`D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event\` on \`codex/end-rift-event\`; preserve unrelated dirty changes.
- Never change source boss geometry or source boss atlas: SHA-256 \`301583a2efea6c5b597c4fe2cadced68d7838b80f630d1d16f8aaa8265964783\` and \`f298ed322335c5439c19dddb8014aa0960b83f3fb27d692580a75e051516c45d\`.
- Copy archived \`enderman-1.png\` and \`spider.png\` byte-for-byte. No generator may overwrite their client destinations.
- Gameplay authority stays server-side: the client never decides hits, shield blocking, health, target, grab, throw, or phase.
- Ordinary guardian pursuit has no teleport or vertical fallback impulse.
- Shield blocks use metal feedback, never body-hit feedback.
- The new 512×512 tentacle atlas is opaque black/violet block pixel art: no cyan, glass, gems, laser lines, or anti-aliasing.
- Every behavior change starts with a focused test that is observed failing first.
- Do not launch or close Minecraft. Completion needs user-provided live screenshots after verified artifacts are installed.

## Review Focus

- Stale positive Y velocity must be cleared only during ordinary on-ground pursuit.
- A target at the tentacle grab socket must still get a finite, non-zero long throw.
- Shield orbit positions must stay finite at large server ticks.
- Shielded projectiles must never produce body-hurt feedback.
- Temporary tentacles must retain \`TEMPORARY\`, not \`UNDER_PLAYER\`.
- Unbound vanilla mobs/displays must never receive End Rift custom models.
- The asset generator must never alter exact archived enderman/spider targets.

---

### Task 1: Ground the guardian and publish real walk state

**Files:**
- Create: \`copimine-end-event/src/me/copimine/endevent/domain/GroundedBossMotionPolicy.java\`
- Create: \`tests/GroundedBossMotionPolicyTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/domain/CombatTacticsPolicy.java\`
- Modify: \`tests/CombatTacticsPolicyTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java\`
- Modify: \`tests/RunEndRiftEventChecks.ps1\`

**Interfaces:**
- Produce \`GroundedBossMotionPolicy.resolve(boolean ordinaryPursuit, boolean onGround, double horizontalSpeed, double currentVerticalVelocity)\`.
- \`Resolution\` returns \`boolean allowTeleport\`, \`double appliedVerticalVelocity\`, and \`BossAnimationId animation\`.
- \`maintainBossPath\` consumes the resolution after each movement request.

- [ ] **Step 1: Write the failing policy test**

~~~java
var walking = GroundedBossMotionPolicy.resolve(true, true, 0.16D, 0.18D);
check(!walking.allowTeleport(), "ordinary pursuit cannot teleport");
check(walking.appliedVerticalVelocity() == 0.0D, "grounded pursuit clears stale Y velocity");
check(walking.animation() == BossAnimationId.RUN, "horizontal motion selects RUN");

var idle = GroundedBossMotionPolicy.resolve(true, true, 0.01D, 0.0D);
check(idle.animation() == BossAnimationId.IDLE_BREATH, "settled guardian selects idle");

var scripted = GroundedBossMotionPolicy.resolve(false, false, 0.0D, 0.35D);
check(scripted.appliedVerticalVelocity() == 0.35D, "scripted motion retains its own Y state");
~~~

Add this test to \`$pureTests\`.

- [ ] **Step 2: Verify RED**

Run: \`.\tests\RunEndRiftEventChecks.ps1\`

Expected: Java compilation fails because \`GroundedBossMotionPolicy\` is absent.

- [ ] **Step 3: Implement the smallest policy and adapt movement**

~~~java
public static Resolution resolve(boolean ordinaryPursuit, boolean onGround,
                                 double horizontalSpeed, double currentVerticalVelocity) {
    double safeY = Double.isFinite(currentVerticalVelocity) ? currentVerticalVelocity : 0.0D;
    double appliedY = ordinaryPursuit && onGround ? 0.0D : safeY;
    BossAnimationId animation = ordinaryPursuit && horizontalSpeed >= 0.06D
            ? BossAnimationId.RUN : BossAnimationId.IDLE_BREATH;
    return new Resolution(false, appliedY, animation);
}
~~~

Remove \`PHANTOM_FEINT\` from ordinary HUNT/RIFT plans. In \`requestBoundedCombatMovement\`, use \`appliedY\` rather than preserving velocity Y. In \`maintainBossPath\`, dispatch the returned \`RUN\`/ \`IDLE_BREATH\` only on change. Leave any future named relocation outside this path.

- [ ] **Step 4: Verify GREEN**

Run: \`.\tests\RunEndRiftEventChecks.ps1\`

Expected: \`GroundedBossMotionPolicyTest\`, \`CombatMovementPolicyTest\`, \`CombatTacticsPolicyTest\`, \`BossAnimationIdTest\` and the full harness pass.

- [ ] **Step 5: Commit**

~~~powershell
git add -- copimine-end-event/src/me/copimine/endevent/domain/GroundedBossMotionPolicy.java tests/GroundedBossMotionPolicyTest.java copimine-end-event/src/me/copimine/endevent/domain/CombatTacticsPolicy.java tests/CombatTacticsPolicyTest.java copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/RunEndRiftEventChecks.ps1
git commit -m "fix(end-rift): ground guardian movement and run state"
~~~

### Task 2: Restore the tentacle attack chain and compute long throws before grab lock

**Files:**
- Create: \`copimine-end-event/src/me/copimine/endevent/domain/TentacleThrowPolicy.java\`
- Create: \`tests/TentacleThrowPolicyTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/runtime/TentacleController.java\`
- Modify: \`tests/TentacleControllerTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java\`
- Modify: \`tests/RunEndRiftEventChecks.ps1\`

**Interfaces:**
- Produce \`TentacleThrowPolicy.launch(rootX, rootZ, targetX, targetZ)\` returning \`Launch(double x, double y, double z)\`.
- \`TentacleController.register(..., boolean temporary, ...)\` maps \`true\` to \`TentacleAnimationPolicy.Kind.TEMPORARY\`.
- Capture \`Launch\` before \`lockTentaclePlayer\`; consume it at \`THROW_RELEASE\`.

- [ ] **Step 1: Write failing tests**

~~~java
var away = TentacleThrowPolicy.launch(0.0D, 0.0D, 6.0D, 0.0D);
check(away.x() > 0.0D && away.horizontalLength() >= 0.95D,
        "throw travels away with long horizontal reach");
check(away.y() >= 0.40D && away.y() <= 0.60D, "throw lift is bounded");

var coincident = TentacleThrowPolicy.launch(0.0D, 0.0D, 0.0D, 0.0D);
check(coincident.horizontalLength() >= 0.95D, "socket coincidence cannot shorten throw");
check(Double.isFinite(coincident.x()) && Double.isFinite(coincident.z()),
        "fallback direction is finite");
~~~

Replace the temporary-kind assertion in \`TentacleControllerTest\` with:

~~~java
check(controller.state(temporary).kind() == TentacleAnimationPolicy.Kind.TEMPORARY,
        "temporary attacks retain the grab lifecycle kind");
~~~

- [ ] **Step 2: Verify RED**

Add \`TentacleThrowPolicyTest\` to \`$pureTests\`, run \`.\tests\RunEndRiftEventChecks.ps1\`.

Expected: missing-policy compilation failure; after policy exists but before controller change, temporary-kind assertion fails.

- [ ] **Step 3: Implement lifecycle and finite launch**

~~~java
double dx = targetX - rootX;
double dz = targetZ - rootZ;
double length = Math.hypot(dx, dz);
if (!Double.isFinite(length) || length < 0.001D) {
    dx = 0.0D; dz = 1.0D; length = 1.0D;
}
return new Launch(dx / length * 1.10D, 0.50D, dz / length * 1.10D);
~~~

Use \`Kind.TEMPORARY\` in the compatibility registration overload. Spawn temporary attacks in \`TELEGRAPH_GRAB\`, not \`SPAWN_UNDER_PLAYER\`. Store launch in a generation-scoped map at contact, validate its projected landing direction inside the arena before applying it, then remove it after \`THROW_RELEASE\`.

- [ ] **Step 4: Verify GREEN**

Run: \`.\tests\RunEndRiftEventChecks.ps1\`

Expected: \`TentacleThrowPolicyTest\`, \`TentacleControllerTest\`, \`TentacleAnimationPolicyTest\`, \`TentacleGuardianPolicyTest\`, and the full harness pass.

- [ ] **Step 5: Commit**

~~~powershell
git add -- copimine-end-event/src/me/copimine/endevent/domain/TentacleThrowPolicy.java tests/TentacleThrowPolicyTest.java copimine-end-event/src/me/copimine/endevent/runtime/TentacleController.java tests/TentacleControllerTest.java copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/RunEndRiftEventChecks.ps1
git commit -m "fix(end-rift): restore tentacle grab and throw cycle"
~~~

### Task 3: Derive visible shield orbit from living guardian state

**Files:**
- Create: \`copimine-end-event/src/me/copimine/endevent/domain/ShieldOrbitPolicy.java\`
- Create: \`tests/ShieldOrbitPolicyTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java\`
- Modify: \`tests/RunEndRiftEventChecks.ps1\`

**Interfaces:**
- Produce \`ShieldOrbitPolicy.positions(bossX, bossY, bossZ, tick, livingGuardians)\`.
- \`Segment(int slot, double x, double y, double z, float yaw)\` has \`isFinite()\`.
- Server owns \`Map<Integer, UUID> shieldDisplayIds\` and reconciles carrier entities from this policy.

- [ ] **Step 1: Write failing orbit test**

~~~java
List<ShieldOrbitPolicy.Segment> segments = ShieldOrbitPolicy.positions(10, 64, -5, 40L, 3);
check(segments.size() == 3, "one orbit segment per guardian");
check(segments.stream().allMatch(ShieldOrbitPolicy.Segment::isFinite), "finite orbit");
check(segments.stream().allMatch(s -> Math.abs(s.y() - 66.15D) < 0.001D),
        "orbit is readable around torso");
check(!segments.equals(ShieldOrbitPolicy.positions(10, 64, -5, 60L, 3)),
        "orbit advances with server tick");
~~~

- [ ] **Step 2: Verify RED**

Add the test to \`$pureTests\`; run \`.\tests\RunEndRiftEventChecks.ps1\`.

Expected: missing \`ShieldOrbitPolicy\` compilation failure.

- [ ] **Step 3: Implement policy and carrier reconciliation**

~~~java
double base = (tick % 240L) * (Math.PI * 2.0D / 240.0D);
double angle = base + Math.PI * 2.0D * slot / count;
return new Segment(slot, bossX + Math.cos(angle) * 2.35D,
        bossY + 2.15D + Math.sin(angle * 2.0D) * 0.18D,
        bossZ + Math.sin(angle) * 2.35D, (float) angle);
~~~

Call \`reconcileGuardianShieldOrbit(boss)\` from \`tickCurrentTentacles\`. Spawn tagged \`ItemDisplay\` carriers with unique custom model data \`830020\`; update them from the policy and remove them on shield break, boss cleanup, generation change or disabled guardians. Resolve blocked-hit contact to nearest active segment, not boss centre.

- [ ] **Step 4: Verify GREEN**

Run:

~~~powershell
.\tests\RunEndRiftEventChecks.ps1
python -m pytest -q .\tests\test_end_rift_combat_sfx_contract.py .\tests\test_end_event_current_contract.py
~~~

Expected: domain/current contracts and full harness pass.

- [ ] **Step 5: Commit**

~~~powershell
git add -- copimine-end-event/src/me/copimine/endevent/domain/ShieldOrbitPolicy.java tests/ShieldOrbitPolicyTest.java copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/RunEndRiftEventChecks.ps1
git commit -m "feat(end-rift): show guardian shield orbit"
~~~

### Task 4: Differentiate body impact from metallic shield impact

**Files:**
- Modify: \`copimine-end-event/src/me/copimine/endevent/domain/BossHitFeedbackPolicy.java\`
- Modify: \`tests/BossHitFeedbackPolicyTest.java\`
- Modify: \`copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java\`
- Modify: \`tests/test_end_rift_combat_sfx_contract.py\`

**Interfaces:**
- Extend \`Feedback\` with \`boolean attackerLocalCue\`.
- \`forShieldBlockedHit(boolean projectile)\` returns sound cue \`BLOCK_ANVIL_HIT\`.
- Add \`applyBossHitFeedbackAt(LivingEntity boss, Entity source, Location contact, Feedback feedback)\`.

- [ ] **Step 1: Write failing feedback expectations**

~~~java
require(shield.attackerLocalCue(), "shield block confirms locally to attacker");
require("BLOCK_ANVIL_HIT".equals(shield.soundCue()), "shield uses metal cue");
require(melee.attackerLocalCue(), "accepted melee confirms locally to attacker");
~~~

Update static contract to require \`BLOCK_ANVIL_HIT\`, \`applyBossHitFeedbackAt\`, and attacker \`playSound\`.

- [ ] **Step 2: Verify RED**

~~~powershell
.\tests\RunEndRiftEventChecks.ps1
python -m pytest -q .\tests\test_end_rift_combat_sfx_contract.py
~~~

Expected: new assertions fail because the cue, local field and contact method do not exist.

- [ ] **Step 3: Implement location-aware bounded feedback**

Map the new cue to \`Sound.BLOCK_ANVIL_HIT\`. Shield feedback only emits metal sound/shield particles at selected segment location. Body feedback retains hurt animation, adds contact particles and an attacker-local audible cue. Keep recoil horizontal and grounded.

- [ ] **Step 4: Verify GREEN and commit**

~~~powershell
.\tests\RunEndRiftEventChecks.ps1
python -m pytest -q .\tests\test_end_rift_combat_sfx_contract.py .\tests\test_end_event_current_contract.py
git add -- copimine-end-event/src/me/copimine/endevent/domain/BossHitFeedbackPolicy.java tests/BossHitFeedbackPolicyTest.java copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/test_end_rift_combat_sfx_contract.py
git commit -m "feat(end-rift): add body and shield impact feedback"
~~~

### Task 5: Rebuild the heavy client tentacle and render shield carriers

**Files:**
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRig.java\`
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleAnimator.java\`
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleWorldScalePolicy.java\`
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRenderer.java\`
- Create: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftGuardianShieldModel.java\`
- Create: \`CopiMineClient/src/main/java/me/copimine/client/EndRiftGuardianShieldRenderer.java\`
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/CopiMineClient.java\`
- Modify: \`CopiMineClient/src/main/java/me/copimine/client/mixin/DisplayEntityRendererMixin.java\`
- Create: \`CopiMineClient/src/test/java/me/copimine/client/EndRiftGuardianShieldModelTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/EndRiftTentacleRigTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/EndRiftTentacleWorldScalePolicyTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/EndRiftTentacleModelTest.java\`

**Interfaces:**
- Tentacle rig has \`seg_06\`, an enlarged base, and authored body height 6.0–6.5 blocks before scale.
- \`EndRiftGuardianShieldModel.SERVER_CUSTOM_MODEL_DATA == 830020\`.
- Shield renderer draws only bridge/model-data matched \`ItemDisplay\` carriers and does not suppress unknown displays.

- [ ] **Step 1: Write failing Fabric tests**

~~~java
assertTrue(EndRiftTentacleRig.REQUIRED_BONES.contains("seg_06"));
assertTrue(height >= 6.0F && height <= 6.5F, "height=" + height);
assertTrue(base.width() >= 1.45F && base.width() <= 1.70F);
assertEquals(830020, EndRiftGuardianShieldModel.SERVER_CUSTOM_MODEL_DATA);
assertTrue(EndRiftGuardianShieldModel.pose(0.5F).isFinite());
assertFalse(EndRiftGuardianShieldModel.isServerCustomModelData(830017));
~~~

Remove \`SPAWN_UNDER_PLAYER\` from required attack lifecycle assertions; require \`TELEGRAPH_GRAB\` for temporary attack playback.

- [ ] **Step 2: Verify RED**

~~~powershell
Set-Location .\CopiMineClient
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --tests me.copimine.client.EndRiftTentacleRigTest --tests me.copimine.client.EndRiftTentacleModelTest --tests me.copimine.client.EndRiftGuardianShieldModelTest --no-daemon
~~~

Expected: missing segment/model and old dimensions fail.

- [ ] **Step 3: Implement render-only rigs**

Add six heavy tapering segments and distributed poses for \`EMERGING\`, \`TELEGRAPH_GRAB\`, \`GRAB_SUCCESS\`, \`HOLD\`, \`THROW\`, \`RECOVERY\`, \`RETRACT\`, and \`SHIELD_CHANNEL\`. Calibrate client height to server \`TentacleScalingPolicy.DEFAULT_LOGICAL_LENGTH\`/hitbox. Add a two-plate violet shield renderer reading server carrier positions; never recompute orbit client-side.

- [ ] **Step 4: Verify GREEN and commit**

~~~powershell
.\.gradle-dist\gradle-8.10.2\bin\gradle.bat test --no-daemon
.\build-client.ps1
git add -- CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRig.java CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleAnimator.java CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleWorldScalePolicy.java CopiMineClient/src/main/java/me/copimine/client/EndRiftTentacleRenderer.java CopiMineClient/src/main/java/me/copimine/client/EndRiftGuardianShieldModel.java CopiMineClient/src/main/java/me/copimine/client/EndRiftGuardianShieldRenderer.java CopiMineClient/src/main/java/me/copimine/client/CopiMineClient.java CopiMineClient/src/main/java/me/copimine/client/mixin/DisplayEntityRendererMixin.java CopiMineClient/src/test/java/me/copimine/client
git commit -m "feat(end-rift-client): render shield orbit and heavy tentacle"
~~~

### Task 6: Produce UV-safe new art and preserve exact archived textures

**Files:**
- Modify: \`CopiMineClient/tools/generate_end_rift_texture_atlases.py\`
- Modify: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_tentacle_hd.png\`
- Create: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_guardian_shield_hd.png\`
- Modify: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_skeleton.png\`
- Modify: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_elite_skeleton.png\`
- Modify: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_elite.png\`
- Modify: \`CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_elite_spider.png\`
- Create: \`resourcepacks/src/assets/copimine/models/item/end_event_rift_guardian_shield.json\`
- Create: \`resourcepacks/src/assets/copimine/textures/item/end_event_rift_guardian_shield_hd.png\`
- Modify: \`resourcepacks/src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png\`
- Modify: \`resourcepacks/models_manifest.json\`
- Modify: \`tests/test_end_event_resource_visual_contract.py\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/SuppliedEndRiftAssetsTest.java\`

**Interfaces:**
- Generator produces only new tentacle/shield/skeleton/elite sheets.
- It copies source \`artifacts/source-inspect/end-event/chameleon/models/enderboss/skins/enderman-1.png\` and \`spider.png\` exactly after generated output.
- Manifest maps custom model data \`830020\` exactly once.

- [ ] **Step 1: Write the failing texture contract**

~~~python
tentacle = Image.open(client_entity / "end_rift_tentacle_hd.png").convert("RGBA")
assert tentacle.size == (512, 512)
pixels = list(tentacle.getdata())
assert all(alpha == 255 for _, _, _, alpha in pixels)
assert not any(red < 130 and green > 150 and blue > 170
               for red, green, blue, _ in pixels)
assert sum(red > 55 and blue > red + 20 and green < blue * 0.65
           for red, green, blue, _ in pixels) > 12_000
~~~

Also use \`hashlib.sha256\` to require archived enderman/spider bytes equal their client targets and require one \`830020\` manifest entry.

- [ ] **Step 2: Verify RED**

Run: \`python -m pytest -q .\tests\test_end_event_resource_visual_contract.py\`

Expected: old cyan/glass tentacle fails and shield manifest is absent.

- [ ] **Step 3: Generate art and synchronise exact inputs**

Use the user-approved generated tentacle draft only as colour/material direction, never raw UV input. Draw opaque black/violet segment strips, dark joints and sparse violet fissures for base, six segments, tip and claws with nearest-neighbour pixels. Generate client/resource-pack tentacle and shield sheets from that one palette. Repaint only new skeleton/elite UV islands with dark shell, pale bone joints and elite-only shoulders/horns/carapace. Copy the archived enderman/spider PNGs to \`end_rift_user_*.png\` after generation.

- [ ] **Step 4: Verify GREEN and commit**

~~~powershell
python .\CopiMineClient\tools\generate_end_rift_texture_atlases.py
Copy-Item -LiteralPath .\artifacts\source-inspect\end-event\chameleon\models\enderboss\skins\enderman-1.png -Destination .\CopiMineClient\src\main\resources\assets\copimineclient\textures\entity\end_rift_user_enderman.png -Force
Copy-Item -LiteralPath .\artifacts\source-inspect\end-event\chameleon\models\enderboss\skins\spider.png -Destination .\CopiMineClient\src\main\resources\assets\copimineclient\textures\entity\end_rift_user_spider.png -Force
python -m pytest -q .\tests\test_end_event_resource_visual_contract.py .\tests\test_end_event_wave_mob_visual_contract.py
.\resourcepacks\build-resourcepack.ps1 -SkipServerProperties
git add -- CopiMineClient resourcepacks tests/test_end_event_resource_visual_contract.py
git commit -m "feat(end-rift): refresh tentacle and mob visuals"
~~~

### Task 7: Verify renderer routing, artifacts, and player-visible evidence

**Files:**
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/EndermanRendererSelectionTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/RiftEventSkeletonModelTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/RiftSpiderModelTest.java\`
- Modify: \`CopiMineClient/src/test/java/me/copimine/client/EndEventTextureCatalogTest.java\`
- Modify: \`tests/test_end_event_wave_mob_visual_contract.py\`
- Modify: \`tests/RunEndRiftTentacleLive.ps1\`
- Modify: \`docs/END_RIFT_EVENT_GUIDE_RU.md\`

**Interfaces:**
- Every requested End Rift visual ID has a present texture and entity-type scoped model.
- Live script reports body impact, shield impact, shield break, and non-zero tentacle launch diagnostics without launching Minecraft.

- [ ] **Step 1: Write failing route/live assertions**

~~~java
assertTrue(EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual("END_RIFT_ENDERMAN_V1")));
assertTrue(EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual("END_RIFT_SPIDER_V1")));
assertTrue(EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual("END_RIFT_SKELETON_V1")));
assertTrue(EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual("END_RIFT_ELITE_V1")));
assertTrue(EndEventTextureCatalog.isAvailable(EndEventTextureCatalog.textureForVisual("END_RIFT_ELITE_SPIDER_V1")));
~~~

Add negative tests for unbound vanilla mobs. Extend the live harness static contract to require \`RIFT_GUARDIAN_SHIELD_BROKEN\`, \`RIFT_TENTACLE_STATE\`, \`BOSS_HIT_FEEDBACK\`, and throw diagnostics.

- [ ] **Step 2: Verify RED**

Run the changed Gradle tests and \`python -m pytest -q .\tests\test_end_rift_native_visual_harness_contract.py\`. If an assertion passes because it only tests an old mapping, tighten it to assert the newly added shield/asset relationship.

- [ ] **Step 3: Complete mappings and evidence guide**

Keep custom model selection explicitly scoped by entity/bridge ID. Document screenshots required after the user launches: walking boss, body impact, shield impact, shield break, grab/throw, exact enderman/spider, normal skeleton and all elite variants.

- [ ] **Step 4: Fresh full verification**

~~~powershell
.\tests\RunEndRiftEventChecks.ps1
python -m pytest -q .\tests\test_end_event_current_contract.py .\tests\test_end_event_resource_visual_contract.py .\tests\test_end_event_wave_mob_visual_contract.py .\tests\test_end_rift_combat_sfx_contract.py .\tests\test_end_rift_native_visual_harness_contract.py
Get-FileHash -Algorithm SHA256 .\thirdparty\client-mods\CopiMineClient-0.1.1.jar
Get-FileHash -Algorithm SHA256 .\resourcepacks\CopiMineResourcePack.zip
~~~

Expected: all automated gates pass. Start only the server-side local session if the client is closed; never claim visual completion before the user’s fresh screenshots.

- [ ] **Step 5: Commit**

~~~powershell
git add -- CopiMineClient/src/test/java/me/copimine/client tests/test_end_event_wave_mob_visual_contract.py tests/RunEndRiftTentacleLive.ps1 docs/END_RIFT_EVENT_GUIDE_RU.md
git commit -m "test(end-rift): verify encounter and mob refresh"
~~~

## Plan Self-Review

- Tasks 1–4 cover grounded movement, tactical feedback and server authority.
- Tasks 2 and 5–6 cover the full tentacle lifecycle, scale, animation and user-reference material.
- Tasks 6–7 cover exact archived enderman/spider assets plus original skeleton/elite visuals.
- Every review-focus failure has an explicit owning test.
