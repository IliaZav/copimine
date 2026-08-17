# CopiMine End Rift Event Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement and locally verify the complete CopiMine End Rift Event v5 contract as an isolated first-party Paper plugin with typed WorldCore/Artifacts/client integrations, original assets, idempotent rewards, restart recovery, and no launcher/site/production mutation.

**Architecture:** `copimine-end-event` owns authoritative event state, phase transitions, tagged entities, deposits, waves, boss, final drain, arena/gate, and the victory saga. WorldCore, Artifacts, and Narcotics expose narrow `ServicesManager` contracts; the existing `copimine:client_bridge` is extended once for optional boss binding/control packets. Pure policy classes are test-first and Paper adapters are kept on the main thread.

**Tech Stack:** Java 21, Paper API `1.21.1-R0.1-20250328.161643-128`, Bukkit/Paper events and PDC, Fabric Loom/Minecraft 1.21.1, Python pytest contract tests, PowerShell module builds, PostgreSQL only through the existing Artifacts service, deterministic resource-pack builder.

## Global Constraints

- Work only in `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` on branch `codex/end-rift-event`.
- `CopiMineWorldCore` remains the sole source of truth for `world_access.end.enabled`; do not create `end_locked` or use console dispatch as an API.
- `CopiMineArtifacts` remains the sole creator of authentic catalog instances and pending deliveries; End Event must not fabricate reward `ItemStack` objects.
- `copimine-end-event` has no direct `CopiMineEconomyCore` dependency and contains no donation-credit path.
- End Event has no HELLO/version/capability admission, heartbeat gate, kick path, `ClientGateService`, or `/cmend clientgate` command.
- Main-thread only: world/block/player/entity/inventory/PDC/health/teleport/display/BossBar/phase mutations. Async only: PostgreSQL, file/hash work, and pure parsing.
- Every event-owned task/entity/display/effect is scoped by `eventId` and `generation` and is idempotently cleaned.
- No launcher source, website source, production server, production world, production database, production upload, or destructive wipe is touched.
- Every production-code slice starts with a focused failing test and is verified narrow-to-broad before commit.

---

## Task 1: Freeze repository boundaries and add the RED test harness

**Files:**
- Create: `tests/EndEventDomainTest.java`
- Create: `tests/test_end_event_contract.py`
- Create: `tests/test_end_event_resourcepack_contract.py`

**Interfaces:**
- The Java harness will compile against the pure domain package
  `me.copimine.endevent.domain` and call the exact policy methods introduced by
  later tasks.
- Python tests read source/manifests without importing the web backend, so they
  run in the current environment even when FastAPI is unavailable.

- [ ] **Step 1: Write failing domain assertions.** Cover legal/illegal phase
  transitions, transient recovery, N=1..20 pad geometry, deposit cap and
  wrong/custom/offhand rejection, 60% current-health drain, minimum 1 HP,
  immutable N-sized roster, UUID-specific shard keys, and duplicate victory
  keys. Reference classes that do not yet exist.
- [ ] **Step 2: Write failing source contracts.** Assert plugin name/main/
  permissions/commands, no `end_locked`, no `/cmend clientgate`, no console
  dispatch, no direct Economy dependency, WorldCore/Artifacts service names,
  exact resource defaults, and required asset/manifest paths.
- [ ] **Step 3: Run the RED checks.**

  ```powershell
  javac -encoding UTF-8 -d tests\build\end-event-red copimine-end-event\src\me\copimine\endevent\domain\*.java tests\EndEventDomainTest.java
  java -cp tests\build\end-event-red EndEventDomainTest
  python -m pytest -q tests\test_end_event_contract.py tests\test_end_event_resourcepack_contract.py
  ```

  Expected: compilation/source-contract failure because the plugin and domain
  files are absent; fix test typos until the failure is specifically missing
  End Event behavior.
- [ ] **Step 4: Commit the RED harness.**

  ```powershell
  git add tests/EndEventDomainTest.java tests/test_end_event_contract.py tests/test_end_event_resourcepack_contract.py
  git commit -m "test: define End Rift Event contracts"
  git push
  ```

## Task 2: Implement pure event policy and persistence models

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/EndEventState.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/PadLayout.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/CoreDepositMath.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/FinalDrainMath.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/RewardRoster.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/EndEventConfigValues.java`
- Modify: `tests/EndEventDomainTest.java`

**Interfaces:**
- `EndEventStateMachine.transition(EventPhase expected, EventPhase next, String reason, String idempotencyKey)` returns a structured result and never mutates on illegal transitions.
- `EndEventStateMachine.recoverAfterRestart(EventPhase persisted)` maps transient fight phases to `READY_FOR_PLAYERS`, preserves `UNLOCKED`, and maps invalid data to `RECOVERY_REQUIRED`.
- `PadLayout.compute(int count, double baseAngle, List<Double> candidateRadii)` returns exactly N unique integer block locations or an invalid reason without partial output.
- `CoreDepositMath.acceptedAmount(int held, int required, int progress)` returns `min(held, max(0, required-progress))`.
- `FinalDrainMath.healthAfterDrain(double before, double max, double fraction, double minimum)` returns a finite clamped value and never zero.
- `RewardRoster.commitExactly(Set<UUID> occupants, int requiredPlayers)` succeeds only for exactly N distinct UUIDs and exposes an immutable set.

- [ ] **Step 1: Run the failing domain harness from Task 1.** Capture the
  missing-symbol errors as the RED evidence.
- [ ] **Step 2: Implement only the pure value objects and algorithms.** Keep
  Bukkit types out of these files so the harness has no Paper dependency.
- [ ] **Step 3: Run the focused harness and Python contracts.**

  ```powershell
  javac -encoding UTF-8 -d tests\build\end-event-domain (Get-ChildItem copimine-end-event\src\me\copimine\endevent\domain\*.java).FullName tests\EndEventDomainTest.java
  java -cp tests\build\end-event-domain EndEventDomainTest
  python -m pytest -q tests\test_end_event_contract.py tests\test_end_event_resourcepack_contract.py
  ```

- [ ] **Step 4: Commit the pure policy slice.**

  ```powershell
  git add copimine-end-event tests/EndEventDomainTest.java
  git commit -m "feat: add End Event state and invariant policies"
  git push
  ```

## Task 3: Add typed WorldCore End access service

**Files:**
- Create: `copimine-world-core/src/me/copimine/worldcore/api/WorldAccessService.java`
- Create: `copimine-world-core/src/me/copimine/worldcore/api/WorldAccessResult.java`
- Modify: `copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java`
- Create: `tests/WorldAccessServiceContractTest.java`
- Modify: `tests/test_end_event_contract.py`

**Interfaces:**
- `WorldAccessService.setEndEnabled(boolean enabled, String actor, String idempotencyKey)` delegates to the existing persisted `setWorldState(false, enabled)` path.
- `WorldAccessService.isEndEnabled()` reads the current `endAccess` model.
- `WorldAccessService.isEndWorld(World world)` uses the existing configured names/environment matching.
- `WorldAccessResult` exposes `success`, `changed`, `code`, and `message`.

- [ ] **Step 1: Write a failing service/command contract.** Assert that the
  service is registered and `/cmworld end open` uses that implementation's
  result rather than a second persistence path.
- [ ] **Step 2: Verify RED** by running the contract against the current
  WorldCore source.
- [ ] **Step 3: Add the API and a private implementation field.** Register it
  with `ServicePriority.Normal` in `onEnable`, unregister it in `onDisable`,
  and route only the End command branch through it. Preserve Nether and all
  existing evacuation behavior.
- [ ] **Step 4: Run the WorldCore build and focused contracts.**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-world-core\build-plugin.ps1
  javac -encoding UTF-8 -cp "$env:PAPER_API_JAR" -d tests\build\world-api tests\WorldAccessServiceContractTest.java
  python -m pytest -q tests\test_end_event_contract.py
  ```

- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-world-core tests
  git commit -m "feat: expose typed WorldCore End access"
  git push
  ```

## Task 4: Add typed authentic Artifact rewards and the shard catalog item

**Files:**
- Create: `copimine-artifacts/src/me/copimine/artifacts/api/EventArtifactRewardService.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/api/EventArtifactRewardRequest.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/api/RewardIssueResult.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/api/RewardIssueStatus.java`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `copimine-artifacts/items.yml`
- Modify: `tests/test_end_event_contract.py`
- Create: `tests/ArtifactRewardServiceContractTest.java`

**Interfaces:**
- `EventArtifactRewardService.issueToPlayer` and `.issueWorldDrop` return
  `CompletionStage<RewardIssueResult>` and accept only the typed request.
- The service validates catalog identity, uses the existing official item
  creation and DB instance/pending-delivery path, and checks the exact
  idempotency key inside the same durable transaction before creating a new
  instance.
- The `rift_core_shard` catalog row is hidden/event-only, base
  `ECHO_SHARD`, max stack 1, CMD `830004`, and is not AR or donation stock.

- [ ] **Step 1: Write failing reward API and catalog contracts.** Assert the
  public service, registration after Artifacts PostgreSQL readiness, UUID in
  every shard key, no fake-item helper in End Event, and exact hidden catalog
  flags.
- [ ] **Step 2: Run RED.** Confirm the symbols/row are absent.
- [ ] **Step 3: Implement the API adapter using existing Artifacts helpers.**
  Keep JDBC on the existing bounded executor, use transaction/idempotency
  checks, create official unique IDs, queue offline/full-inventory delivery,
  and marshal physical delivery back to the Bukkit thread. For a world drop,
  persist the official instance first and spawn the returned official item only
  after the durable reservation; never duplicate on retry.
- [ ] **Step 4: Build Artifacts and run focused contracts.**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-artifacts\build-plugin.ps1
  python -m pytest -q tests\test_end_event_contract.py
  ```

- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-artifacts tests
  git commit -m "feat: expose idempotent End Event artifact rewards"
  git push
  ```

## Task 5: Scaffold the plugin, validated config, repository, task/entity registries

**Files:**
- Create: `copimine-end-event/plugin.yml`
- Create: `copimine-end-event/config.yml`
- Create: `copimine-end-event/build-plugin.ps1`
- Create: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Create: `copimine-end-event/src/me/copimine/endevent/EndEventConfig.java`
- Create: `copimine-end-event/src/me/copimine/endevent/EndEventStateRepository.java`
- Create: `copimine-end-event/src/me/copimine/endevent/EndEventTaskRegistry.java`
- Create: `copimine-end-event/src/me/copimine/endevent/EndEventEntityRegistry.java`
- Create: `tests/test_end_event_plugin_contract.py`

**Interfaces:**
- `plugin.yml` names `CopiMineEndEvent`, main
  `me.copimine.endevent.CopiMineEndEvent`, API 1.21, `depend:
  [CopiMineWorldCore, CopiMineArtifacts]`, `softdepend: [CopiMineNarcotics]`,
  commands `cmend`, and only the two v5 permissions.
- `EndEventStateRepository.load()` returns a validated state or
  `RECOVERY_REQUIRED`; `.save(EndEventState)` uses temp/backup atomic writes.
- `EndEventTaskRegistry.track(BukkitTask)`, `.cancelGeneration(long)`, and
  `.cancelAll()` prevent stale callbacks.
- `EndEventEntityRegistry.isOwned(Entity, eventId, generation)` and
  `.cleanup(eventId, generation)` inspect only PDC-tagged entities/displays.

- [ ] **Step 1: Add failing plugin/config contracts** for metadata, no client
  gate strings, exact defaults, atomic persistence markers, and dependency
  boundaries.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Add the minimal plugin scaffold and pure repository/registry
  wiring.** Load/validate config before enabling event operations; fail closed
  on invalid ranges.
- [ ] **Step 4: Build the new plugin in the isolated worktree.**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1
  python -m pytest -q tests\test_end_event_plugin_contract.py
  ```

- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-end-event tests
  git commit -m "feat: scaffold End Rift Event plugin"
  git push
  ```

## Task 6: Implement core deposits, visuals, pads, and countdown roster

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/CoreService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/CoreDepositService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/CoreVisualService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/RitualPadService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/ParticipantRegistry.java`
- Create: `copimine-end-event/src/me/copimine/endevent/CountdownService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/DepositJournal.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/EndEventDomainTest.java`
- Modify: `tests/test_end_event_plugin_contract.py`

**Interfaces:**
- `CoreDepositService.handle(PlayerInteractEvent)` accepts only main-hand
  right-clicks on the exact core, returns an accepted count, and records the
  PREPARED → ITEM_REMOVED → COMMITTED/refund journal.
- `CoreVisualService.ensureCoreVisuals` and `.removeProgressVisual` use owned
  ItemDisplay/TextDisplay PDC and never remove unrelated displays.
- `RitualPadService.calculateAndPlacePads` is all-or-nothing and
  `.occupants()` enforces one player/one pad every five ticks.
- `CountdownService.startIfReady` commits an immutable exactly-N roster before
  `COUNTDOWN -> WAVE_1`; any departure resets to READY_FOR_PLAYERS.

- [ ] **Step 1: Extend RED tests** for offhand, custom PDC, duplicate tick,
  remainder, save-failure refund, visual completion, pad collisions, exact N,
  countdown reset, and late-helper exclusion.
- [ ] **Step 2: Run focused RED tests.**
- [ ] **Step 3: Implement the main-thread services and listeners.** Use
  `PlayerInteractEvent` with `EquipmentSlot.HAND`; reject custom/official
  artifacts; cancel recognized interaction; tag every display/pad; journal
  before removal and refund exact stacks on failure.
- [ ] **Step 4: Verify locally with the pure harness and plugin build.**

  ```powershell
  javac -encoding UTF-8 -d tests\build\end-event-domain (Get-ChildItem copimine-end-event\src\me\copimine\endevent\domain\*.java).FullName tests\EndEventDomainTest.java
  java -cp tests\build\end-event-domain EndEventDomainTest
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1
  python -m pytest -q tests\test_end_event_plugin_contract.py
  ```

- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-end-event tests
  git commit -m "feat: add End Rift core deposits and countdown"
  git push
  ```

## Task 7: Implement arena/gate/portal-room protection and admin command surface

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/ArenaProtectionService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/GateService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/EndEventCommand.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/config.yml`
- Create: `tests/test_end_event_commands_contract.py`

**Interfaces:**
- `ArenaProtectionService.isProtected(Location, Block)` scopes every block/
  explosion/piston/fluid/fire/hopper/display check to configured bounds and
  current phase.
- `GateService.preview`, `.open`, and `.restore` operate only on bounded
  snapshots and return structured results.
- `EndEventCommand` implements every v5 `/cmend` command listed in the spec,
  with no GUI and no clientgate/admission subcommand.

- [ ] **Step 1: Write and run RED command/protection contracts.**
- [ ] **Step 2: Implement bounded region selection, snapshot validation, safe
  gate mutation, portal room metadata, and command/tab completion.** `unlock`
  is test/admin-only and cannot bypass the official saga without explicit
  confirmation; test boss/waves never advance official phase.
- [ ] **Step 3: Run build and focused command contracts.**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1
  python -m pytest -q tests\test_end_event_commands_contract.py
  ```

- [ ] **Step 4: Commit and push.**

  ```powershell
  git add copimine-end-event tests
  git commit -m "feat: add bounded End Event controls and protection"
  git push
  ```

## Task 8: Implement waves, loot, boss, containment, target rotation, and spells

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/WaveService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/WaveLootService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/BossController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/BossSpellController.java`
- Create: `copimine-end-event/src/me/copimine/endevent/BossTargetController.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/EndEventDomainTest.java`
- Modify: `tests/test_end_event_plugin_contract.py`

**Interfaces:**
- `WaveService.spawnOfficialWave(int wave, long generation)` and
  `.spawnTestWave(int wave)` apply the exact v5 compositions, N scaling, cap
  48, PDC markers, loot profile, and official/test distinction.
- `BossController.spawnOfficial`, `.spawnTest`, `.applyDamage`, `.handleDeath`,
  `.isCurrentBoss`, and `.healthSnapshot` enforce 1000 HP, +3 exact melee,
  15-block containment, BossBar, and one official death.
- `BossSpellController.tick` is the sole scheduler for four base spells and
  generation/phase/boss checks.
- `BossTargetController.tick` rotates eligible targets every 5–8 seconds and
  excludes outside/dead/spectator/creative players.

- [ ] **Step 1: Add RED pure tests** for scaling cap, deterministic loot,
  target rotation, radii, no-repeat spell selection, generation guards, exact
  +3 non-stacking, and official/test progression.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement bounded Paper services and listeners.** Use
  `AttributeInstance` base/modifier safely, cancel outside teleports, disable
  block pickup/grief, use one spell controller, and stop spell creation at
  half pause/final drain/final wave/victory.
- [ ] **Step 4: Run domain tests, plugin build, and source contracts.**
- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-end-event tests
  git commit -m "feat: add End Rift waves boss and spells"
  git push
  ```

## Task 9: Implement 50% control phase and existing client bridge extension

**Files:**
- Create: `copimine-narcotics/src/me/copimine/narcotics/api/EndEventClientService.java`
- Create: `copimine-narcotics/src/me/copimine/narcotics/api/ControlEffectRequest.java`
- Create: `copimine-narcotics/src/me/copimine/clientbridge/EndEventClientServiceImpl.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientBridgePayloads.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/EndEventVisualState.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/ControlDistortionState.java`
- Create: `CopiMineClient/src/main/java/me/copimine/client/mixin/EndEventMovementInputMixin.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/BridgePayload.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java`
- Modify: `CopiMineClient/src/main/java/me/copimine/client/CopiMineClient.java`
- Modify: `CopiMineClient/src/main/resources/copimineclient.mixins.json`
- Create: `CopiMineClient/src/test/java/me/copimine/client/EndEventControlStateTest.java`
- Create: `CopiMineClient/src/test/java/me/copimine/client/EndEventVisualStateTest.java`
- Create: `tests/test_end_event_client_contract.py`
- Create: `copimine-end-event/src/me/copimine/endevent/ControlInversionSpellService.java`

**Interfaces:**
- Server messages are `end_boss_bind`, `end_boss_unbind`,
  `end_control_reverse_start`, `end_control_reverse_stop`, and diagnostic
  `end_effect_ack` on the existing channel/codec.
- `ControlDistortionState.start(effectId, generation, serverEndsAtMillis)`
  uses a monotonic local deadline, ignores stale generation/effect IDs, and
  transforms only forward/sideways axes.
- `EndEventVisualState.bind/unbind` accepts only exact entity UUID and current
  generation; unknown/stale UUIDs render vanilla.
- `EndEventClientServiceImpl` never exposes admission/capability/version/kick
  methods and routes ACKs only to diagnostics.

- [ ] **Step 1: Write failing client tests** for exact UUID/generation binding,
  axis-only inversion, duplicate START absolute deadline, stale STOP, local
  expiry, disconnect/respawn/world-change/shutdown cleanup, and no kick path.
- [ ] **Step 2: Run the Fabric test task and observe RED** for missing classes.
- [ ] **Step 3: Extend the one codec on both server/client sides.** Append
  bounded optional fields so existing narcotics messages remain compatible;
  register one incoming listener only; add the renderer binding and verified
  Yarn 1.21.1 movement-input mixin without global key mutation.
- [ ] **Step 4: Implement server half-health trigger and control service.**
  Persist the trigger before heal/unlock, heal living eligible players, send
  best-effort packets, stop on all server cleanup paths, and treat missing ACK
  as diagnostics only.
- [ ] **Step 5: Run client tests/build and server build.**

  ```powershell
  .\gradlew.bat :test
  .\gradlew.bat build
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-narcotics\build-plugin.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-end-event\build-plugin.ps1
  python -m pytest -q tests\test_end_event_client_contract.py
  ```

- [ ] **Step 6: Commit and push.**

  ```powershell
  git add CopiMineClient copimine-narcotics copimine-end-event tests
  git commit -m "feat: add optional End Event client effects"
  git push
  ```

## Task 10: Implement final drain, official rewards, and victory saga

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/FinalDrainService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/VictoryService.java`
- Create: `copimine-end-event/src/me/copimine/endevent/WorldCoreBridge.java`
- Create: `copimine-end-event/src/me/copimine/endevent/ArtifactsRewardBridge.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/EndEventDomainTest.java`
- Modify: `tests/test_end_event_plugin_contract.py`

**Interfaces:**
- `FinalDrainService.triggerAtProjectedHealth(double projectedHealth)` clamps
  the boss to 100, persists once, stops spells/control, and enters
  `FINAL_RITUAL`.
- `FinalDrainService.applyIfEligible()` snapshots current eligible players,
  sets each to `max(1.0, before*0.40)` once, and then sets boss health exactly
  200. It never calls `damage`, creates death events, changes absorption,
  drops items, or touches EconomyCore.
- `VictoryService.runUntilComplete()` advances durable step keys and issues
  one Return Stone plus one UUID-keyed shard per official roster member before
  the final unlock commit.
- `WorldCoreBridge.openEndOnce()` calls `WorldAccessService` with the stable
  saga key and does not dispatch a command.

- [ ] **Step 1: Extend RED tests** for first threshold crossing, 60% current
  health examples, minimum 1, outside/dead/creative/spectator exclusion,
  no-death/no-donation source paths, exact 200 HP, one drain, N shard keys,
  full-inventory/offline pending delivery, no last-hit/late-helper reward,
  Return Stone idempotency, saga retry, and one announcement.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement the final drain and saga with stable step markers.**
  Persist before side effects; run async reward calls; marshal results to main
  thread; preserve incomplete steps across restart; remove only owned core/
  pads; open gate/arena and WorldCore in order; title/chat unique names once.
- [ ] **Step 4: Run focused tests and both affected plugin builds.**
- [ ] **Step 5: Commit and push.**

  ```powershell
  git add copimine-end-event tests
  git commit -m "feat: add End Rift final drain rewards and victory saga"
  git push
  ```

## Task 11: Add original resource-pack and client texture assets

**Files:**
- Create: `resourcepacks/src/assets/copimine/textures/block/end_event_core.png`
- Create: `resourcepacks/src/assets/copimine/textures/block/end_event_pad.png`
- Create: `resourcepacks/src/assets/copimine/textures/item/end_event_boss.png`
- Create: `resourcepacks/src/assets/copimine/textures/item/artifacts/rift_core_shard.png`
- Create: `resourcepacks/src/assets/copimine/models/item/block/end_event_core.json`
- Create: `resourcepacks/src/assets/copimine/models/item/block/end_event_pad.json`
- Create: `resourcepacks/src/assets/copimine/models/item/end_event_boss.json`
- Create: `resourcepacks/src/assets/copimine/models/item/artifacts/rift_core_shard.json`
- Create: `CopiMineClient/src/main/resources/assets/copimine/textures/entity/end_rift_guardian.png`
- Modify: `resourcepacks/models_manifest.json`
- Modify: `resourcepacks/src/assets/copimine/manifests/block_visuals_manifest.json`
- Modify: `resourcepacks/item_texture_sources.json`
- Modify: `tests/test_end_event_resourcepack_contract.py`

**Interfaces:**
- Reserve CMD `830001` core, `830002` pad, `830003` boss emblem/display, and
  `830004` `rift_core_shard`; reject collisions with every existing manifest.
- Core/pad models are owned ItemDisplay assets; the shard maps to the hidden
  Artifacts catalog row; the boss entity skin is client-owned and binds only to
  the exact UUID.

- [ ] **Step 1: Add failing asset/manifest tests** for paths, dimensions,
  RGBA/non-empty/distinct PNGs, model references, CMD uniqueness, catalog
  alignment, and client-jar packaging.
- [ ] **Step 2: Run RED** because all five End assets are absent.
- [ ] **Step 3: Generate five original bitmap assets using the built-in
  image-generation workflow, inspect them, and copy final files into the
  source-controlled paths.** Convert to the repository's expected 16×16/32×32
  item/block dimensions while preserving alpha; do not copy internet art or
  leave synthetic placeholder art.
- [ ] **Step 4: Add models/manifests and run focused asset tests plus the
  deterministic resource-pack builder.**

  ```powershell
  python -m pytest -q tests\test_end_event_resourcepack_contract.py
  python .\resourcepacks\build-resourcepack.py
  python -m pytest -q tests\test_end_event_resourcepack_contract.py
  ```

- [ ] **Step 5: Build the client and assert the entity texture is inside the
  JAR.**
- [ ] **Step 6: Commit and push.**

  ```powershell
  git add resourcepacks CopiMineClient tests
  git commit -m "feat: add original End Rift visual assets"
  git push
  ```

## Task 12: Wire build, CI, validators, and isolated local runtime

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `scripts/verify_independent_build.ps1`
- Modify: `scripts/package_full_release.ps1`
- Create: `tests/ValidateCopiMineEndEvent.ps1`
- Modify: `tests/RunCopiMineValidators.ps1` only to include the new validator
- Create: `scripts/local-end-event-runtime.ps1`
- Create: `docs/superpowers/plans/2026-08-17-end-rift-event.md` only for verified command corrections

**Interfaces:**
- All build arrays compile `copimine-end-event` after WorldCore/Artifacts/
  Narcotics and include `CopiMineEndEvent.jar` in the local/release payload.
- The new validator checks plugin metadata, service registration, no gate/
  console dispatch/fake reward paths, resource manifests, and no production
  path references.
- `scripts/local-end-event-runtime.ps1` starts/stops only a distinct local
  Paper/PostgreSQL stack under `local-runtime/end-event`, uses a separate local
  port/database, and refuses paths outside the worktree/local-runtime roots.

- [ ] **Step 1: Write the failing build/validator contracts.**
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Add the new build script/list entries and validator.** Do not
  modify launcher/site source or invoke deploy scripts.
- [ ] **Step 4: Build all first-party server plugins twice and compare hashes.**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_independent_build.ps1 -ProjectRoot (Resolve-Path .).Path
  ```

- [ ] **Step 5: Build client/resource pack/modpack inputs without uploading.**
  Record each artifact SHA-256 and keep generated outputs local.
- [ ] **Step 6: Commit and push build/runtime wiring.**

  ```powershell
  git add .github scripts tests
  git commit -m "build: include End Rift Event in local verification"
  git push
  ```

## Task 13: Run the local Paper/PostgreSQL canonical scenario

**Files:**
- Modify only ignored local runtime state under `local-runtime/end-event`
- Create: `docs/superpowers/evidence/2026-08-17-end-rift-event-runtime.md`

**Interfaces:**
- The local server loads the built event alongside the exact local WorldCore,
  Artifacts, EconomyCore, Narcotics, and client-mod test inputs.
- The evidence file records command, timestamp, server SHA/JAR hash, local DB
  identity, phase transitions, reward keys/unique IDs, and log excerpts; no
  production credentials or player data are copied into it.

- [ ] **Step 1: Start isolated local PostgreSQL/Paper.** Verify ports,
  database name, `level-name`, plugin hashes, and that the target world is
  local before mutating any state.
- [ ] **Step 2: Execute the core matrix.** Set N=5, configure core/arena/gate/
  portal room, deposit all four resources with main-hand stacks, test wrong/
  custom/offhand/spam cases, restart at progress and verify journal/remainder.
- [ ] **Step 3: Execute countdown/waves.** Use five local test players,
  verify exactly five pads/roster, reset on leave/death/quit/world-change,
  verify compositions/caps/loot/containment/target rotation.
- [ ] **Step 4: Execute boss matrix.** Verify 1000 HP/+3/radius/four spells,
  50% heal/control, exact client axis inversion/cleanup, projected 10% clamp,
  final wave, all eligible players' 60% current-health drain, no death/no
  donation, boss 200/invulnerability, and final death.
- [ ] **Step 5: Execute victory/restart matrix.** Verify one authentic Return
  Stone, XP/resources, five unique UUID-keyed shards including offline/full
  inventory pending delivery, core/pads/gate/arena, WorldCore End open,
  announcement names, and restart idempotency.
- [ ] **Step 6: Stop the local stack and write evidence.** Production server,
  launcher, website, production DB, and deploy scripts remain untouched.

## Task 14: Independent read-only verification and completion audit

**Files:**
- Create: `docs/superpowers/evidence/2026-08-17-end-rift-event-requirement-matrix.md`
- Modify: no production/source files unless a failed requirement identifies a
  concrete implementation defect; fix through a new RED-GREEN cycle

**Interfaces:**
- The matrix has one row per v5 requirement with code evidence, focused-test
  evidence, local runtime evidence, and `PASS`, `FAIL`, or `UNVERIFIED`.
- Mandatory rows include direct right-click/no GUI, duplicate guards, N pads,
  waves/loot, boss HP/+3/radii/spells, half heal/control, client cleanup,
  final drain/no death/no donation/boss 200, Return Stone, N shards, End
  unlock, announcement, no admission path, assets, and restart recovery.

- [ ] **Step 1: Run independent read-only source searches and all focused tests.**
- [ ] **Step 2: Run the full Python suite with the repository's documented
  dependencies, full PowerShell validators, independent two-pass build, client
  tests/build, resource-pack hash/reproducibility checks, and local runtime
  checks.**
- [ ] **Step 3: Run `git diff --check`, inspect `git status`, and verify the
  diff contains no launcher/site/production runtime changes or secrets.**
- [ ] **Step 4: Populate the requirement matrix from fresh command output, not
  intent or an agent report.** Any missing/failed/skipped requirement keeps the
  result `NOT DEPLOYED — RELEASE GATE FAILED` and the goal active.
- [ ] **Step 5: Commit the final evidence and push the branch.** Create a draft
  GitHub PR only after all source/runtime evidence is present; do not merge or
  deploy.
