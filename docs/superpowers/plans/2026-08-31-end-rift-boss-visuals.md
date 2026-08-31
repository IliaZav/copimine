# End Rift Boss Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Довести визуал Стража Разлома, его заклинаний, финальных сцен арены, 3D-порталов волны 3 и безопасных зон до согласованного уровня без изменения боевой логики и без повреждения текущей локальной карты.

**Architecture:** Сервер остаётся авторитетным: он выбирает фазу/cue, запускает звук, частицы и bounded-сцену арены. Чистые Java-policy-классы описывают cue, зоны и геометрию, чтобы основные ограничения проверялись без Paper. Клиентский Fabric-мод расширяет существующую `RiftGuardianModel`, а resource pack получает объёмные модели портала; игрок без мода продолжает видеть серверный fallback.

**Tech Stack:** Java 21, Paper/Purpur Bukkit API, Fabric/Loom для Minecraft 1.21.1, JUnit 5, Python/pytest contract tests, PowerShell local Paper harness, JSON resource-pack models.

**Spec:** `docs/superpowers/specs/2026-08-31-end-rift-boss-visuals-design.md`

## Global Constraints

- Работать только в `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` на ветке `codex/end-rift-event`.
- Использовать только локальный Paper/PostgreSQL runtime; боевой сервер, сайт и лаунчер не запускать и не подключать.
- Текущий мир и карту не очищать, не пересоздавать и не заменять.
- Не добавлять `assets/minecraft/textures` переопределения; ванильные текстуры не менять.
- Любая временная блоковая мутация проходит через `HazardMutationJournal` и восстанавливается условно, только если блок всё ещё имеет event-материал.
- Серверные частицы отправлять через `eventAudience()`/viewer-scoped path; не добавлять глобальный `world.spawnParticle` в повторяющиеся boss-циклы.
- Сохранять лимит `MAX_ACTIVE_RIFT_PROJECTILES`, общий лимит мобов и существующие правила здоровья, урона, целей и наград.
- Каждая задача завершается красным тестом, минимальной реализацией, зелёной проверкой и отдельным Git-коммитом.

## Map of Files and Responsibilities

- `copimine-end-event/src/me/copimine/endevent/domain/BossVisualCuePolicy.java` — чистая таблица cue для фаз, заклинаний, звуков, анимаций и бюджетов.
- `copimine-end-event/src/me/copimine/endevent/domain/ZoneVisualPolicy.java` — состояния зон `FREE`, `OCCUPIED`, `DANGER`, `SAFE`, `COMPLETED` и их bounded-параметры.
- `copimine-end-event/src/me/copimine/endevent/domain/BossArenaSetPiecePolicy.java` — чистая bounded-геометрия финальных сцен и портальных маркеров.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` — серверный адаптер: lifecycle cue, sound/particle rendering, portal layers, safe-zone layers, final arena scene и cleanup.
- `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java` — дополнительные ModelPart и фазовые/кастовые позы.
- `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModelRenderer.java` — нормализация фаз и новых animation ids.
- `CopiMineClient/src/main/java/me/copimine/client/mixin/EndermanEntityRendererMixin.java` — сохранить UUID-scoped подмену только для босса.
- `CopiMineClient/src/test/java/me/copimine/client/EndEventClientStateTest.java` — регрессии протокола phase/cue и очистки состояния.
- `CopiMineClient/src/test/java/me/copimine/client/RiftGuardianModelTest.java` — тесты доступных частей модели и animation ids.
- `resourcepacks/src/assets/copimine/models/block/end_event_portal.json` — объёмная арка портала.
- `resourcepacks/src/assets/copimine/models/block/end_event_portal_inner.json` — отдельный глубокий внутренний слой разлома.
- `resourcepacks/src/assets/copimine/models/block/end_event_portal_shard.json` — объёмный осколок для углов портала.
- `resourcepacks/src/assets/copimine/models/item/end_event_portal_inner.json` и `.../end_event_portal_shard.json` — item-display модели тех же слоёв.
- `resourcepacks/build-resourcepack.py` — включить новые модели в архив и сохранить проверку отсутствия vanilla texture override.
- `tests/BossVisualCuePolicyTest.java`, `tests/ZoneVisualPolicyTest.java`, `tests/BossArenaSetPiecePolicyTest.java` — чистые unit-тесты.
- `tests/test_end_event_boss_visual_contract.py`, `tests/test_end_event_portal_visual_contract.py`, `tests/test_end_event_arena_scene_contract.py` — серверные/resource-pack contract tests.
- `tests/RunEndRiftEventChecks.ps1` — подключить новые чистые тесты, контрактные тесты и итоговые хэши.
- `tests/RunEndRiftBossVisualLive.ps1` — локальный полный прогон сцен и диагностических маркеров.

### Task 1: Add testable cue, zone and arena policies

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossVisualCuePolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/ZoneVisualPolicy.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/BossArenaSetPiecePolicy.java`
- Create: `tests/BossVisualCuePolicyTest.java`
- Create: `tests/ZoneVisualPolicyTest.java`
- Create: `tests/BossArenaSetPiecePolicyTest.java`
- Modify: `tests/RunEndRiftEventChecks.ps1`

**Interfaces:**
- `BossVisualCuePolicy.cue(String spellId, CueStage stage)` returns a non-null `Cue(id, animationId, soundId, primaryParticle, accentParticle, particleBudget, cooldownTicks)` for every current boss spell and for `phase_shift`, `final_awaken`, `defeat_collapse`.
- `ZoneVisualPolicy.profile(ZoneState state)` returns `Profile(id, primaryParticle, accentParticle, colorRgb, floorY, ringPoints, particleBudget)`.
- `BossArenaSetPiecePolicy.frame(Scene scene, Bounds bounds, Point core, int elapsedTicks)` returns bounded `Frame(scene, List<Ring>, List<Pillar>, List<Cell>, boolean finished)` and exposes `Frame.allPointsInside(Bounds)`.

- [ ] **Step 1: Write failing policy tests.** Assert all current spell ids have three cue stages, unique animation/cue ids, positive bounded budgets, valid sound ids, and that every zone profile is floor-anchored at `0.04..0.12`.

```java
for (String spell : CURRENT_SPELLS) {
    for (BossVisualCuePolicy.CueStage stage : BossVisualCuePolicy.CueStage.values()) {
        BossVisualCuePolicy.Cue cue = BossVisualCuePolicy.cue(spell, stage);
        check(cue != null && cue.particleBudget() <= 96, spell + " cue budget");
    }
}
check(BossArenaSetPiecePolicy.frame(Scene.FINAL_RITUAL, bounds, core, 0)
        .allPointsInside(bounds), "ritual geometry must stay inside arena");
```

- [ ] **Step 2: Run the new tests and confirm they fail because the policies do not exist.**

Run: `powershell -NoProfile -Command "& .\tests\RunEndRiftEventChecks.ps1"`

Expected: `javac` fails with missing policy classes or the focused tests fail before any server source is changed.

- [ ] **Step 3: Implement the minimal immutable policies.** Use string ids rather than Bukkit classes, keep all returned lists immutable, clamp elapsed ticks and coordinates, and encode the exact palettes from the approved spec.

- [ ] **Step 4: Run focused and full pure-domain tests.**

Run: `powershell -NoProfile -Command "& .\tests\RunEndRiftEventChecks.ps1"`

Expected: the new policy tests and all pre-existing pure-domain tests pass.

- [ ] **Step 5: Commit the policy layer.**

```powershell
git add copimine-end-event/src/me/copimine/endevent/domain tests/BossVisualCuePolicyTest.java tests/ZoneVisualPolicyTest.java tests/BossArenaSetPiecePolicyTest.java tests/RunEndRiftEventChecks.ps1
git commit -m "feat(end-event): define bounded boss visual policies"
```

### Task 2: Connect boss cue lifecycle to server sounds, particles and animations

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Create: `tests/test_end_event_boss_visual_contract.py`
- Modify: `tests/test_end_rift_performance_contract.py`

**Interfaces:**
- Add `playBossVisualCue(LivingEntity boss, String cueId, Location origin, boolean forced)` as the single entry point for boss cue delivery.
- Add `renderBossVisualCue(Player viewer, LivingEntity boss, BossVisualCuePolicy.Cue cue, Location origin, int elapsedTicks)` for viewer-scoped particles.
- Add `resetBossVisualCue(LivingEntity boss, long callbackGeneration)` for one-shot return to `IDLE`.
- Existing `sendBossAnimationVisualUpdate`, `renderSpellTelegraphVisual`, `renderSpellImpactVisual` and `scheduleBossAnimationReset` remain compatible wrappers or call the new entry point.

- [ ] **Step 1: Add failing source contracts.** Check that every spell passes through `TELEGRAPH`, `RELEASE` and `IMPACT`, every stage transition sends `PHASE_SHIFT`, final entry sends `FINAL_AWAKENING`, and defeat sends `DEFEAT_COLLAPSE`.

- [ ] **Step 2: Run the focused contract tests and record the expected failure.**

Run: `python -m pytest -q tests/test_end_event_boss_visual_contract.py tests/test_end_rift_performance_contract.py`

Expected: the new markers are absent from `CopiMineEndEvent.java`.

- [ ] **Step 3: Implement one guarded cue path.** Add a per-boss last-cue id/deadline guard, reject cues from stale generations or invalid bosses, play the selected vanilla sound once to event viewers, send the animation id, and render only the policy-selected particle layers. Do not put sound or large particle calls in the 1-tick boss loop.

- [ ] **Step 4: Wire the path into the existing lifecycle.** Call the cue path from `telegraphBossSpell`, immediately before the flight in `executeBossSpell`, on the impact callback, in `synchronizeBossStage`, `startAbsorptionChannel`, `startJudgment`, `triggerFinalPhase`, and `commitOfficialBossDefeat`. Preserve the old `SPELL_*`, `ABSORPTION_CHANNEL`, `JUDGMENT_CAST` and `EXHAUSTED` wire ids as aliases.

- [ ] **Step 5: Run source contracts and compile the plugin.**

Run: `python -m pytest -q tests/test_end_event_boss_visual_contract.py tests/test_end_rift_performance_contract.py`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1`

Expected: all cue lifecycle and performance contracts pass; the plugin JAR is rebuilt without Java errors.

- [ ] **Step 6: Commit the server cue lifecycle.**

```powershell
git add copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/test_end_event_boss_visual_contract.py tests/test_end_rift_performance_contract.py
git commit -m "feat(end-event): synchronize boss cues with combat effects"
```

### Task 3: Make the client boss model visibly transform between phases

**Files:**
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModelRenderer.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/mixin/EndermanEntityRendererMixin.java`
- Create: `CopiMineClient/src/test/java/me/copimine/client/RiftGuardianModelTest.java`
- Modify: `CopiMineClient/src/test/java/me/copimine/client/EndEventClientStateTest.java`
- Modify: `tests/test_end_rift_client_model_contract.py`

**Interfaces:**
- Add model parts `jaw`, `core_eye`, `crown_left`, `crown_right`, `left_talon`, `right_talon`, and `catastrophe_spine` to the existing `ModelPart` tree.
- Extend `setAnimation` handling for `IDLE_BREATH`, `DAMAGED_FLINCH`, `TELEPORT_RIP`, `CAST_CHARGE`, `CAST_RELEASE`, `CAST_IMPACT`, `PHASE_SHIFT`, `FINAL_AWAKENING`, and `DEFEAT_COLLAPSE`.
- Keep `modelForPhase(String, long, String)` and UUID-scoped fallback behavior unchanged.

- [ ] **Step 1: Write failing client tests.** Assert the new named parts occur in model construction, all approved animation ids have bounded poses, `CATASTROPHE` is the only phase that raises the final silhouette scale, and ordinary Endermen still use the vanilla model.

- [ ] **Step 2: Run the focused client tests before implementation.**

Run: `powershell -NoProfile -Command "Set-Location .\CopiMineClient; .\gradlew.bat test"`; `python -m pytest -q tests/test_end_rift_client_model_contract.py`

Expected: the new model contract fails because the parts and animations are not present.

- [ ] **Step 3: Add the geometry using the existing 128x128 phase textures.** Use UV regions that do not overlap current torso/arms, keep the jaw/eye inside the model silhouette, and apply only model transforms; never change entity hitbox or server location.

- [ ] **Step 4: Add bounded pose helpers.** Implement `applyBreathTransform`, `applyDamagedFlinch`, `applyTeleportRip`, `applyCastTransform`, `applyPhaseShiftTransform` and `applyDefeatCollapse`; call them in a stable order after reset and clamp all scale/rotation values.

- [ ] **Step 5: Build and test the client JAR.**

Run: `powershell -NoProfile -Command "Set-Location .\CopiMineClient; .\gradlew.bat clean test build"`; `python -m pytest -q tests/test_end_rift_client_model_contract.py`

Expected: JUnit, client contract tests and `CopiMineClient-0.1.1.jar` build pass; no ordinary Enderman renderer path changes.

- [ ] **Step 6: Commit the client model update.**

```powershell
git add CopiMineClient/src/main/java CopiMineClient/src/test/java tests/test_end_rift_client_model_contract.py
git commit -m "feat(client): intensify End Rift guardian model animations"
```

### Task 4: Replace flat portal visuals with layered 3D portals and readable floor zones

**Files:**
- Modify: `resourcepacks/src/assets/copimine/models/block/end_event_portal.json`
- Create: `resourcepacks/src/assets/copimine/models/block/end_event_portal_inner.json`
- Create: `resourcepacks/src/assets/copimine/models/block/end_event_portal_shard.json`
- Create: `resourcepacks/src/assets/copimine/models/item/end_event_portal_inner.json`
- Create: `resourcepacks/src/assets/copimine/models/item/end_event_portal_shard.json`
- Modify: `resourcepacks/build-resourcepack.py`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Create: `tests/test_end_event_portal_visual_contract.py`

**Interfaces:**
- `spawnPortalModelVisual` produces at most four tracked layers per portal: frame, inner rift, four corner shards represented by one bounded shard layer set, and particle-only floor/vertical accents.
- `animatePortalModelVisuals(long now)` updates all tracked portal layers in one 5-tick task, with no per-portal scheduler.
- `renderZoneVisual(Player viewer, Location floor, ZoneVisualPolicy.ZoneState state, double radius, long now)` renders every zone from a floor anchor and never uses a floating item as the floor itself.

- [ ] **Step 1: Add failing JSON/source contracts.** Assert the portal frame has volumetric elements with thickness, the inner/shard models exist and are packed, all portal display origins use `x + 0.5`, `y`, `z + 0.5`, and occupied/safe colors are distinct from free/danger colors.

- [ ] **Step 2: Run the focused contracts and confirm failure.**

Run: `python -m pytest -q tests/test_end_event_portal_visual_contract.py`

Expected: missing model files, missing builder entries, or missing server layer markers.

- [ ] **Step 3: Implement the resource-pack models.** Reuse `copimine:item/end_event_portal` texture assets, build a thick outer arch, a recessed inner cavity and angular shard geometry, and keep all geometry within the item-display bounds. Do not add any vanilla texture override.

- [ ] **Step 4: Add layered server displays.** Track every layer in `waveObjectiveVisuals`, clean it with the existing wave-objective cleanup, pulse scale by no more than 3%, rotate only the inner/shard layers, and keep all displays persistent only for the active objective.

- [ ] **Step 5: Upgrade zone rendering.** Use `ZoneVisualPolicy` for rune occupancy, portal capture, `JUDGMENT_CAST` safe zones and danger cells. Render the main ring and fill at `0.04..0.12` above the floor, four low corner markers and a bounded vertical beam; use text only as a secondary label.

- [ ] **Step 6: Build and inspect the archive.**

Run: `python resourcepacks/build-resourcepack.py --skip-server-properties`; `python -m pytest -q tests/test_end_event_portal_visual_contract.py tests/test_end_event_visual_regressions.py`

Expected: all model JSON files are in `resourcepacks/build/CopiMineResourcePack.zip`, archive SHA-1 is printed, and no `assets/minecraft/textures/block` override is present.

- [ ] **Step 7: Commit portal and zone visuals.**

```powershell
git add resourcepacks/src resourcepacks/build-resourcepack.py copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/test_end_event_portal_visual_contract.py
git commit -m "feat(end-event): add layered portal and floor zone visuals"
```

### Task 5: Add final-stage arena set pieces with exact cleanup

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/test_end_event_runtime_invariants_contract.py`
- Create: `tests/test_end_event_arena_scene_contract.py`
- Modify: `tests/BossArenaSetPiecePolicyTest.java`

**Interfaces:**
- Add `startFinalArenaScene(EventPhase scene)` and `renderFinalArenaScene(EventPhase scene, LivingEntity boss, int elapsedTicks)`.
- Add `clearFinalArenaScene(String reason)` that cancels its one task, removes tracked display UUIDs, clears scene state and never touches the persistent victory core.
- `finalRitualVisualTask` remains the only task for `FINAL_DRAIN`/`FINAL_RITUAL`; `FINAL_WAVE` reuses the existing hazard task and bounded wave-front renderer.

- [ ] **Step 1: Add failing arena-scene contracts.** Check that each final phase has a scene start and cleanup path, the final scene uses `BossArenaSetPiecePolicy`, no plan point is outside the configured arena or vertical bounds, and victory/reset/disable paths call cleanup.

- [ ] **Step 2: Run the focused contracts before implementation.**

Run: `python -m pytest -q tests/test_end_event_arena_scene_contract.py tests/test_end_event_runtime_invariants_contract.py`

Expected: the new scene lifecycle markers and cleanup calls are absent.

- [ ] **Step 3: Implement the display-only final drain and ritual scenes.** Create four low obelisks/energy anchors at policy-selected inner-perimeter points, connect them to the core with viewer-scoped lines, pulse them every 5 ticks, and emit one transition sound per scene. Do not replace map blocks in these two scenes.

- [ ] **Step 4: Integrate final wave/inferno visuals.** Keep existing journaled MAGMA mutations as the only real floor mutation, add the policy-driven ring pulses and danger/safe colors, protect core/runes/pads/gates, and refuse an empty or invalid mutation plan.

- [ ] **Step 5: Wire cleanup to every exit.** Call `clearFinalArenaScene` from victory, manual reset, recovery, plugin disable, boss defeat, cancelled ritual and stale-generation branches. Verify tracked display UUIDs are removed from `ownedEntities` and no task survives a generation change.

- [ ] **Step 6: Run contracts and pure tests.**

Run: `python -m pytest -q tests/test_end_event_arena_scene_contract.py tests/test_end_event_runtime_invariants_contract.py`; `powershell -NoProfile -Command "& .\tests\RunEndRiftEventChecks.ps1"`

Expected: all arena bounds, cleanup and rollback checks pass.

- [ ] **Step 7: Commit the final arena scenes.**

```powershell
git add copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java tests/test_end_event_arena_scene_contract.py tests/test_end_event_runtime_invariants_contract.py tests/BossArenaSetPiecePolicyTest.java
git commit -m "feat(end-event): stage bounded final arena set pieces"
```

### Task 6: Add local end-to-end visual diagnostics and five-player verification

**Files:**
- Create: `tests/RunEndRiftBossVisualLive.ps1`
- Modify: `tests/RunEndRiftEventChecks.ps1`
- Modify: `tests/test_end_event_diagnostics_contract.py`
- Create: `docs/superpowers/evidence/2026-08-31-end-rift-boss-visuals.md`

**Interfaces:**
- `RunEndRiftBossVisualLive.ps1` accepts only the existing local runtime paths/ports and refuses non-local `environment`, non-local server address or a wrong branch.
- The live script records `BOSS_VISUAL_CUE`, `PORTAL_VISUAL_LAYER`, `ZONE_VISUAL_STATE`, `FINAL_ARENA_SCENE`, `FINAL_ARENA_CLEANUP`, `RUNTIME_DIAGNOSTICS` and `PERF_PASS` markers.

- [ ] **Step 1: Add a failing diagnostics contract.** Assert the live script is local-only, invokes the existing disposable event driver without wiping the current map, waits for all five boss stages and checks zero event displays/projectiles after cleanup.

- [ ] **Step 2: Run the contract and confirm failure because the script does not exist.**

Run: `python -m pytest -q tests/test_end_event_diagnostics_contract.py`

Expected: missing script/markers.

- [ ] **Step 3: Implement the bounded local probe.** Start from the current local runtime, run the existing official/disposable event harness, exercise all five waves and boss thresholds, run a five-player performance sample, capture `debug ai`, `debug perf`, `latest.log` and the resource-pack/client hashes, and always use the existing cleanup path in `finally`.

- [ ] **Step 4: Run the local probe.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftBossVisualLive.ps1`

Expected: local Paper remains running or is restored according to the existing harness policy, every scene marker appears in order, five test players remain connected during the sample, and cleanup reports zero event-owned temporary displays, projectiles and hazards.

- [ ] **Step 5: Review evidence item by item.** Confirm there are no `ERROR`/uncaught exceptions in the captured local log, no outside-arena entity coordinates, no particle budget violation, no persistent block mismatch and no production endpoint in commands or output.

- [ ] **Step 6: Commit the diagnostics and evidence format.**

```powershell
git add tests/RunEndRiftBossVisualLive.ps1 tests/RunEndRiftEventChecks.ps1 tests/test_end_event_diagnostics_contract.py docs/superpowers/evidence
git commit -m "test(end-event): verify boss visuals in local five-player run"
```

### Task 7: Full build, local restart, visual review and GitHub checkpoint

**Files:**
- Modify only when local restart verification demonstrates a regression: `start-local-copimine.bat`, `tests/StartEndRiftLocal.ps1`, `resourcepacks/build-resourcepack.ps1`
- Create: `docs/superpowers/evidence/2026-08-31-end-rift-boss-visuals-final.md`

- [ ] **Step 1: Run the complete static gate.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1`; `python -m pytest -q`

Expected: all pure tests, contract tests, resource-pack checks and existing regression tests pass.

- [ ] **Step 2: Build all local artifacts from the worktree.**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\CopiMineClient\build-client.ps1`; `python .\resourcepacks\build-resourcepack.py --skip-server-properties`

Expected: plugin JAR, client JAR and resource pack are rebuilt from current source; hashes are recorded.

- [ ] **Step 3: Restart only the local server using the existing local launcher.** Preserve the current world, `server.properties`, whitelist and local PostgreSQL data; verify Paper binds to `25566`, RCON to `25576`, and the pack endpoint remains the local Radmin/VPN endpoint configured by the current launcher.

- [ ] **Step 4: Repeat the five-player live visual probe after the restart.** Verify portal layers, rune/zone floor anchoring, every boss cue, phase model changes, final arena scenes and cleanup from the actual installed local artifacts, not only from source.

- [ ] **Step 5: Inspect the final diff and hashes.**

Run: `git status --short`; `git diff --check`; `git diff HEAD~1 --stat`; `Get-FileHash .\copimine-end-event\CopiMineEndEvent.jar -Algorithm SHA256`; `Get-FileHash .\CopiMineClient\build\libs\CopiMineClient-0.1.1.jar -Algorithm SHA256`; `Get-FileHash .\resourcepacks\build\CopiMineResourcePack.zip -Algorithm SHA256`

Expected: only approved event/client/resource-pack/tests/docs files changed; no launcher/site/production files changed.

- [ ] **Step 6: Commit the verified final checkpoint and push the current branch.**

```powershell
git add docs/superpowers/evidence
git commit -m "chore(end-event): record verified boss visual checkpoint"
git push origin codex/end-rift-event
```

Expected: `origin/codex/end-rift-event` points to the verified local checkpoint. No production deployment is performed.
