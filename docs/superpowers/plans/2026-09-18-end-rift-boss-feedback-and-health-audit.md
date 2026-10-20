# End Rift Boss Feedback and Health Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the UUID-scoped End Rift boss health presentation and make accepted, shielded, rejected, and projectile hits visibly and audibly distinct without changing server-authoritative encounter health or hitboxes.

**Architecture:** Keep the Paper plugin as the only gameplay authority. Stream a bounded `END_BOSS_BAR` snapshot beside the existing boss binding so the client can project the intended maximum only for that UUID; intercept the client `LivingEntity#getMaxHealth` return narrowly, leaving raw/native values for ordinary entities untouched. Add a pure server feedback policy, then attach one feedback transaction to each already-deduped damage route; the adapter emits vanilla status, animation, particles, sound, recoil, and structured diagnostics after the real-health commit.

**Tech Stack:** Paper/Purpur 1.21.1 Bukkit API, Fabric Loader/Minecraft 1.21.1 client mod, Java 21, Gradle Loom, PowerShell gates, pytest contract tests, checked-in generated boss pose parity.

**Spec:** `C:/Users/zavod/.codex/attachments/8deccb72-5b25-4f9f-b772-b9369a5241cd/Вставленный текст.txt`

## Global Constraints

- The server remains authoritative for real boss health, phases, damage, shields, and hitbox acceptance.
- Boss health remains the existing 5000–20000 roster-scaled pool; never reduce it to 1024 or multiply damage to hide a display bug.
- Every behavior change follows `REPRODUCE -> RECORD EVIDENCE -> TRACE -> failing test -> red -> minimal fix -> green -> related suite -> full gate -> Paper live -> native Minecraft`.
- Client health projection is UUID-bound to the active End Rift boss and falls back to native values for ordinary entities, ordinary Endermen, stale UUIDs, and missing snapshots.
- Feedback is attached after one accepted/rejected deduped transaction; no path may emit duplicate sound, flash, animation, recoil, or particles for one hit identity.
- Vanilla built-in sounds are used first. No external audio is added unless a native audition proves it is necessary; if that changes, provenance is mandatory.
- Model geometry, texture dimensions, UV validation, hitbox pose generation, Wave 6/7 behavior, and existing tentacle fixes must remain green.
- Work only in `D:/Desktop/Copimine/copimine-main/.worktrees/end-rift-event`; preserve unrelated untracked evidence artifacts.
- Commit and push to `https://github.com/IliaZav/copimine` on `codex/end-rift-event`; do not claim native visual/audio completion from CI alone.

---

### Task 1: Capture the current HP boundary and establish regression fixtures

**Files:**
- Modify: `tests/test_end_event_current_contract.py` only if a source-level contract is needed for the new diagnostics.
- Create: `tests/BossHpPipelineContractTest.java` if the existing pure gate needs a Java-side pipeline fixture.
- Create: `docs/superpowers/evidence/2026-09-18-end-rift-boss-health-boundary.md`

**Interfaces:**
- Consumes: current `BossHealthPolicy`, the server PDC max-health key, `EndEventClientState.BossBarState`, and the local Paper/RCON runtime.
- Produces: a recorded baseline showing configured max, Bukkit physical max, PDC max, current health, current packet behavior, and the exact current client tooltip path.

- [ ] **Step 1: Reproduce the baseline without changing production code.**

  Run the existing local Paper/client harness, spawn the disposable boss, and query `/cmend status`, `/cmend debug bosshitbox status`, and the server log around one damage event. Record the values separately as `configuredMaxHealth`, `boss.getMaxHealth()`, PDC max, and health.

- [ ] **Step 2: Confirm the clamping boundary in the client runtime.**

  Use the current client profile and the existing screenshot path. If the current tooltip cannot be controlled by the repository, record that limitation explicitly and use the client command added in Task 2 as the authoritative CopiMine-controlled display probe.

- [ ] **Step 3: Write the baseline evidence document.**

  Include the starting SHA `73ae8a85ffb1e9841cdd827529c0f967cc56cc5a`, commands, observed values, and a clear first-boundary statement. Do not call the issue fixed in this task.

- [ ] **Step 4: Commit the evidence before implementation.**

  ```powershell
  git add docs/superpowers/evidence/2026-09-18-end-rift-boss-health-boundary.md
  git commit -m "docs(end-rift): record boss health display boundary"
  ```

### Task 2: Implement and test the UUID-scoped boss health projection

**Files:**
- Create: `CopiMineClient/src/main/java/me/copimine/client/EndRiftBossHealthProjection.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityHealthProjectionMixin.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java`
- Modify: `CopiMineClient/src/main/resources/copimineclient.mixins.json`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/CopiMineClient.java`
- Test: `CopiMineClient/src/test/java/me/copimine/client/EndRiftBossHealthProjectionTest.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Test: `tests/EndRiftBossHealthProjectionContractTest.java` if the pure End Rift gate needs a server-side protocol contract.

**Interfaces:**
- Consumes: `EndEventClientState.bossUuid()`, `EndEventClientState.bossBar()`, and the server `END_BOSS_BIND`/`END_BOSS_BAR` protocol.
- Produces: `EndRiftBossHealthProjection.resolveMaxHealth(String entityUuid, double nativeMax, String boundBossUuid, EndEventClientState.BossBarState snapshot)`, `ClientBridgeProtocol.projectedBossMaxHealth(...)`, and `/copimineclient endrift health` diagnostic lines.

- [ ] **Step 1: Write the failing JUnit test.**

  Cover exactly these cases: bound boss with native `1024` and snapshot `5000` resolves to `5000`; ordinary Enderman with native `40` and a snapshot for another UUID remains `40`; stale bound UUID with no usable snapshot remains `1024`; malformed/zero snapshot falls back to native.

- [ ] **Step 2: Run only the new client test and observe RED.**

  ```powershell
  Push-Location CopiMineClient
  .\gradlew.bat test --tests me.copimine.client.EndRiftBossHealthProjectionTest
  Pop-Location
  ```

  Expected: compilation/test failure because the projection helper does not yet exist.

- [ ] **Step 3: Implement the pure projection helper.**

  Normalize only finite positive native values, require UUID equality with both the active binding and visible boss-bar snapshot, require a positive snapshot maximum, and return the native value for every other case.

- [ ] **Step 4: Run the test and observe GREEN.**

  Re-run the focused Gradle test and keep the output as evidence.

- [ ] **Step 5: Add the narrow Mixin and client diagnostics.**

  Inject at the return of `LivingEntity#getMaxHealth`, call the helper with the current entity UUID, and set a new return only for the bound boss. Add `/copimineclient endrift health` output for binding UUID, packet HP/max, entity HP, native max, projected max, and source. Ordinary entities must not enter the projection branch.

- [ ] **Step 6: Stream a real snapshot from the server.**

  Send `END_BOSS_BAR` immediately after `END_BOSS_BIND` and from the existing boss-bar update boundary with the configured/PDC max rather than `boss.getMaxHealth()`. Keep the native Bukkit BossBar unchanged for clients without the mod.

- [ ] **Step 7: Rebuild and test the client mixin.**

  Run the focused JUnit test, `CopiMineClient\build-client.ps1`, and the existing client test suite. Record the client JAR SHA-256.

- [ ] **Step 8: Commit the projection.**

  ```powershell
  git add CopiMineClient copimine-end-event
  git commit -m "fix(end-rift): project boss max health for bound client"
  ```

### Task 3: Add the pure hit-feedback policy and animation priority contract

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossHitFeedbackPolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossAnimationPriorityPolicy.java`
- Create: `tests/BossHitFeedbackPolicyTest.java`
- Create: `tests/BossAnimationPriorityPolicyTest.java`
- Modify: `tests/RunEndRiftEventChecks.ps1`
- Modify: `CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations/hurt.json`
- Modify: `copimine-end-event/tools/GenerateBossAnimationPoses.ps1` only if the existing generator contract needs an explicit shortened HURT invariant.
- Regenerate: `copimine-end-event/src/me/copimine/endevent/domain/GeneratedBossAnimationPoses.java`

**Interfaces:**
- Consumes: an outcome enum and the existing animation IDs.
- Produces: immutable feedback records with red flash, hurt animation, physical recoil, impact/shield particles, primary/attacker sound cues, and duration; `BossAnimationPriorityPolicy.mayApplyHitFlinch(...)`.

- [ ] **Step 1: Write the failing pure tests.**

  Assert that accepted melee has all body-hit flags and the bounded recoil range; accepted projectile has body feedback without melee recoil; shielded melee/projectile have zero damage presentation, shield particles, and `ITEM_SHIELD_BLOCK` without red flash; phase/cinematic outcomes have no flesh feedback; miss emits no hit feedback; HURT cannot replace DYING, FINAL_STRIKE, PHASE_TRANSITION, GROUND_SLAM, or CHEST_STRIKE.

- [ ] **Step 2: Run the new pure tests and observe RED.**

  Run the current pure Java compile path with the test names added to `RunEndRiftEventChecks.ps1`. Expected failure is missing classes/enum behavior.

- [ ] **Step 3: Implement the minimal policy tables.**

  Use vanilla IDs: `minecraft:entity.player.attack.strong` for attacker-local melee impact, `minecraft:entity.wither.hurt` for positional body response, `minecraft:item.shield.block` for shield, and restrained anchor/beacon cues for explicit immunity outcomes. Keep the IDs in the pure policy so the adapter has one source of truth.

- [ ] **Step 4: Strengthen the authored HURT clip.**

  Shorten the existing one-second clip to a fast flinch and add small body/shoulder recoil tracks. Regenerate the server pose copy with `GenerateBossAnimationPoses.ps1 -Check`/generation so client and OBB timelines remain identical.

- [ ] **Step 5: Run policy tests and generator parity GREEN.**

  Run both focused tests and the pose generator check. Do not wire Bukkit behavior until these are green.

- [ ] **Step 6: Commit the policy and authored clip.**

  ```powershell
  git add copimine-end-event/src/me/copimine/endevent/domain tests/BossHitFeedbackPolicyTest.java tests/BossAnimationPriorityPolicyTest.java tests/RunEndRiftEventChecks.ps1 CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations/hurt.json copimine-end-event/tools/GenerateBossAnimationPoses.ps1
  git commit -m "feat(end-rift): define boss hit feedback policy"
  ```

### Task 4: Wire one feedback transaction into every authoritative boss hit path

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/RunEndRiftEventChecks.ps1` if the new policy test names are not already included.
- Create: `tests/test_end_rift_combat_sfx_contract.py`

**Interfaces:**
- Consumes: `BossHitFeedbackPolicy`, `BossAnimationPriorityPolicy`, existing proxy/projectile dedupe identity, real-health transaction result, OBB proxy point/part.
- Produces: one `BOSS_HIT_FEEDBACK` structured diagnostic and one presentation transaction for accepted melee/projectile, shielded melee/projectile, phase immunity, and cinematic immunity; bounded recoil and actual hit-point particles.

- [ ] **Step 1: Write the failing contract test.**

  Parse the plugin source and assert the server calls `playEffect(EntityEffect.HURT)` only from the feedback adapter, contains `Sound.ITEM_SHIELD_BLOCK`, emits `Particle.ELECTRIC_SPARK`/`Particle.CRIT`, contains `BOSS_HIT_FEEDBACK`, and uses the pure policy outcome. Also assert the resource-pack contract documents that no custom sound asset is required when the built-in cues are used.

- [ ] **Step 2: Run the contract test and observe RED.**

  ```powershell
  python -m pytest -q tests/test_end_rift_combat_sfx_contract.py
  ```

  Expected: failure because the feedback adapter and diagnostic do not yet exist.

- [ ] **Step 3: Add the adapter after the real-health commit.**

  For accepted body hits, send hurt status, call the priority-guarded HURT animation, emit restrained CRIT/DUST at the proxy/projectile hit point, play positional Wither hurt plus attacker-local strong attack, and apply a small horizontal recoil. For shield blocks, keep health unchanged, send no hurt status, emit sparks/pulse, and play only the shield-block cue. Map phase/cinematic rejections to their explicit non-flesh cues. Keep misses silent.

- [ ] **Step 4: Add bounded recoil state.**

  Derive direction from attacker/projectile to boss, cap each accepted melee impulse at `0.08–0.16` horizontal and `0.00–0.04` vertical, merge repeated impulses in a two-to-three-tick window, and never add projectile recoil. Use the existing boss position/hitbox update loop so proxy OBBs follow the moved carrier.

- [ ] **Step 5: Route shield checks through the common transaction.**

  Ensure proxy melee, direct melee, proxy projectile, and swept projectile paths all call the same shield feedback outcome before returning. Remove only the duplicate outer shield gate that would otherwise suppress feedback; do not reopen native damage application.

- [ ] **Step 6: Add structured diagnostics.**

  Emit fields `type`, `boss`, `attacker`, `part`, `healthBefore`, `healthAfter`, `damageApplied`, `redFlash`, `hurtAnimation`, `recoil`, and `sound`. Add `physicalMaxHealth` and `configuredMaxHealth` to the boss damage diagnostics so the 1024 boundary remains observable.

- [ ] **Step 7: Run the focused contract and pure suites.**

  Run the new pytest file, both feedback policy Java tests, the existing pure End Rift tests, and the server plugin build. Expected result is GREEN before live testing.

- [ ] **Step 8: Commit the wiring.**

  ```powershell
  git add copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/test_end_rift_combat_sfx_contract.py
  git commit -m "feat(end-rift): restore boss hit feedback"
  ```

### Task 5: Add operator health diagnostics and live feedback coverage

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Create: `tests/RunEndRiftBossHitFeedbackLive.ps1`
- Create: `docs/superpowers/evidence/2026-09-18-end-rift-boss-feedback-live.md`
- Modify: `tests/RunEndRiftEventChecks.ps1` to invoke the focused live script only when the local Paper harness prerequisites are present.

**Interfaces:**
- Consumes: `/cmend debug bosshp`, existing local RCON helpers, hit feedback diagnostics.
- Produces: server-side health pipeline output and a disposable live probe for accepted melee, shield block, projectile, and cleanup.

- [ ] **Step 1: Write the failing command/contract assertions.**

  Extend the contract test to require `/cmend debug bosshp`, `serverHealth`, `configuredMaxHealth`, `physicalMaxHealth`, `pdcMaxHealth`, `packetMaxHealth`, and `projectionSource` markers in source.

- [ ] **Step 2: Run the contract test and observe RED.**

  Confirm it fails before adding the command and markers.

- [ ] **Step 3: Implement `/cmend debug bosshp`.**

  Report boss UUID, real HP, configured/PDC max, physical Bukkit max, phase, and the last streamed packet max without mutating state. Keep it local/staging-only like `bosshitbox`.

- [ ] **Step 4: Implement the focused local live script.**

  Start the isolated Paper server, connect the disposable client, spawn the test/official local boss through supported commands, perform one melee hit, one shielded hit, and one projectile hit, assert the exact health deltas and exactly one `BOSS_HIT_FEEDBACK` record per identity, then clean up and assert zero residue. Never use marker text before state assertions.

- [ ] **Step 5: Run the live script and capture evidence.**

  Save command output, relevant server log excerpts, artifact hashes, and cleanup status in the evidence document. If a native player input step is unavailable, mark the live case `UNVERIFIED` instead of fabricating a pass.

- [ ] **Step 6: Commit the diagnostics/live harness.**

  ```powershell
  git add copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/RunEndRiftBossHitFeedbackLive.ps1 tests/test_end_rift_combat_sfx_contract.py docs/superpowers/evidence/2026-09-18-end-rift-boss-feedback-live.md
  git commit -m "test(end-rift): add boss feedback live probe"
  ```

### Task 6: Rebuild, run the full gate, and perform native visual/audio acceptance

**Files:**
- Create: `docs/superpowers/evidence/2026-09-18-end-rift-boss-native-audit.md`
- Modify: `tests/RunEndRiftEventChecks.ps1` and the authoritative pytest list only for new contract files.
- Modify: `docs/END_RIFT_EVENT_GUIDE_RU.md` only if operator commands or the sound/model ownership contract need documentation.

**Interfaces:**
- Consumes: all source and artifacts from Tasks 1–5, local Paper server, exact `ServerRP_copy_1` profile, existing screenshot/video capture tooling.
- Produces: exact final artifact hashes, model-quality and hitbox-alignment evidence, native audio/visual status, and GitHub CI state.

- [ ] **Step 1: Run mutation checks.**

  Temporarily mutate the projection return, accepted red-flash flag, shield sound, duplicate feedback path, and one UV face in isolated copies; run the corresponding focused test/validator and record RED, then restore immediately and rerun GREEN. Do not commit mutation changes.

- [ ] **Step 2: Run all fresh automated gates.**

  ```powershell
  python -m pytest -q tests
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunCopiMineValidators.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1
  powershell -ExecutionPolicy Bypass -File .\resourcepacks\build-resourcepack.ps1
  git diff --check
  ```

  Also run the authoritative `CopiMineClient\build-client.ps1` and verify the resource-pack ZIP, client JAR, server JAR, and modpack ZIP hashes.

- [ ] **Step 3: Run native model checks with debug OFF.**

  Use the exact rebuilt client/resource pack in `D:\.minecraft\versions\ServerRP_copy_1`; capture boss front/back/left/right/45-degree/close/combat and the Enderman, skeleton, and spider role matrix with F3+B and custom hitbox debug off. Check texture selection, feet, head/arms, holes, z-fighting, seams, and feature layers.

- [ ] **Step 4: Run native hitbox checks with debug ON.**

  Capture the same boss in IDLE, RUN, HURT, MELEE_SWIPE, CHEST_STRIKE, and GROUND_SLAM with `/cmend debug bosshitbox on`, comparing all 11 parts against the rendered bones at the documented tolerance. Turn the debug overlay off afterward.

- [ ] **Step 5: Run one continuous native hit clip with audio.**

  Record boss idle, three accepted sword hits, shield activation, three shielded sword hits, and one projectile. Verify red flash/flinch/recoil/body sound, metallic shield clang with no HP delta, and no duplicate feedback. If audio cannot be captured or auditioned, final evidence must state `NATIVE VISUAL/AUDIO: NOT VERIFIED`.

- [ ] **Step 6: Write the final evidence report.**

  Use the required sections from the spec: root cause, HP pipeline, normal/shield feedback, sound sources, model/UV and hitbox audits, role audits, resource build, automated tests, Paper live, native results, cleanup, and open issues. Separate model-quality screenshots from hitbox-debug screenshots.

- [ ] **Step 7: Commit, push, and verify GitHub.**

  ```powershell
  git status --short
  git rev-parse HEAD
  git diff --check
  git add docs/superpowers/evidence docs/END_RIFT_EVENT_GUIDE_RU.md tests/RunEndRiftEventChecks.ps1
  git commit -m "docs(end-rift): record boss combat and native audit"
  git push origin codex/end-rift-event
  ```

  Poll the exact GitHub Actions runs for the final SHA. Report source checks, Paper live checks, and native visual/audio checks as separate evidence levels.

---

## Coverage Review

- Sections 1–7 of the spec are covered by Tasks 1–2, including the 1024 boundary and required fallback semantics.
- Sections 8–24 are covered by Tasks 3–5, including single-transaction feedback, shield differentiation, recoil cap, diagnostics, and live checks.
- Sections 25–38 are covered by Tasks 3, 4, and 6; the current design intentionally prefers built-in sounds, so no server-pack custom SFX are required unless native audition rejects them.
- Sections 39–44 are covered by Tasks 2, 4, and 5, including operator/client diagnostics and UI agreement.
- Sections 45–54 are covered by Task 6; the report will explicitly mark any unavailable native matrix/audio evidence as unverified rather than treating static tests as proof.
- No plan step uses a placeholder or changes server health authority.
