# End Rift Event Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining End Rift gaps, add a durable top-down portal passage, finish event-specific visuals, and prove the complete event locally without touching production.

**Architecture:** Keep `CopiMineEndEvent` authoritative and reuse its existing durable `EventLayoutState` snapshot and generation/task guards. Add a pure `GateOpeningPlan` for ordering/bounds, then integrate one bounded scheduler into the existing gate command and victory saga. Keep entity textures in the UUID-scoped `CopiMineClient` renderer path and keep Core/rune overlays as Paper-owned `ItemDisplay` models.

**Tech Stack:** Java 21, Paper 1.21.1 API, Bukkit/Purpur local runtime, JUnit-style repository Java tests, Python/Pytest contract tests, Fabric client, PNG/resource-pack pipeline, isolated PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-08-24-end-rift-completion-design.md` plus `docs/superpowers/specs/2026-08-17-end-rift-event-design.md`.

## Global Constraints

- Work only in `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event` on `codex/end-rift-event`.
- Do not modify launcher, website, production server/world/database, deployment scripts, or ordinary production runtime state.
- Wave spiders replace Endermites; no Endermite spawn path remains.
- Boss health is 1200; final-drain health is 200; boss and mobs stay inside the bounded arena.
- Creative is allowed only for the local admin test driver; official pad occupancy continues to exclude Creative/Spectator.
- Every production-code slice starts with a failing focused test and ends with a fresh focused verification.
- Every visual claim must distinguish source/pack evidence from real client observation.

---

### Task 1: Add the gate opening domain plan and regression tests

**Files:**
- Create: `copimine-end-event/src/me/copimine/endevent/domain/GateOpeningPlan.java`
- Create: `tests/GateOpeningPlanTest.java`
- Create: `tests/test_end_event_gate_contract.py`

**Interfaces:**
- `GateOpeningPlan.from(Point first, Point second, int maxVolume)` validates one world, inclusive coordinates, and the volume ceiling.
- `GateOpeningPlan.layersDescending()` returns immutable `List<Layer>` ordered from greatest Y to least Y; each layer contains deterministic block coordinates.
- `GateOpeningPlan.Layer.y()` and `Layer.blocks()` expose only coordinates inside the cuboid.

- [ ] **Step 1: Write failing tests** for cross-world points, zero/oversized volume, inclusive dimensions, top-down Y ordering, deterministic X/Z order, and no coordinate outside the input cuboid.
- [ ] **Step 2: Run the Java test and Python gate contract; confirm the missing class/command contract fails for the intended reason.**
- [ ] **Step 3: Implement the minimal immutable plan and coordinate records.**
- [ ] **Step 4: Run the focused tests and then `EndEventDomainTest`; confirm all pass.**
- [ ] **Step 5: Inspect the diff and commit `test: define bounded top-down gate opening contract`.**

### Task 2: Persist and execute the gradual gate opening

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/EventLayoutState.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventLayoutStore.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/plugin.yml`
- Modify: `tests/EventLayoutStoreTest.java`
- Modify: `tests/test_end_event_gate_contract.py`

**Interfaces:**
- `EventLayoutState.gateStatus()` accepts `UNSET`, `PREVIEW`, `OPENING`, `OPENED`, `RESTORED`, and `RESTORED_ON_BOOT`.
- The layout store round-trips the existing snapshot plus gate status without dropping old layouts.
- Command completion is `/cmend gate open [ticks-per-layer]`; the default interval is bounded and the argument is range-checked.

- [ ] **Step 1: Extend RED tests** for status round-trip, command tab completion/usage, durable snapshot before mutation, top-down layer logs, invalid interval refusal, and restore-on-boot for `OPENING`.
- [ ] **Step 2: Run the focused tests and record RED.**
- [ ] **Step 3: Implement `openGate` with one generation-scoped task.** Snapshot exact `BlockData`, save `OPENING`, process one `GateOpeningPlan.Layer` per interval, set only snapshot-owned blocks to air, emit bounded particles/sound, save progress/status, and finish at `OPENED`.
- [ ] **Step 4: Update boot recovery and `restore confirm`.** An interrupted `OPENING` restores the full snapshot before marking `RESTORED_ON_BOOT`; no world-wide scan is permitted.
- [ ] **Step 5: Wire gate opening into the victory saga idempotently and preserve the existing `preview` behavior.**
- [ ] **Step 6: Run focused Java/Python tests, plugin build, and `git diff --check`.**
- [ ] **Step 7: Commit `feat: open End Rift gate gradually from top to bottom`.**

### Task 3: Close the configuration and wave-visual contract

**Files:**
- Modify: `copimine-end-event/config.yml`
- Modify: `copimine-end-event/src/me/copimine/endevent/EventConfig.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `tests/test_end_event_config_contract.py`
- Modify: `tests/test_end_event_visual_regressions.py`

**Interfaces:**
- All wave composition paths use `SPIDER` for the spider profile and reject `ENDERMITE` as an event spawn type.
- Existing +10 max-health/+2 attack-damage spider tuning remains applied exactly once.
- Boss config remains 1200/600/120/200 and validates the threshold ordering.
- Every event-owned mob receives the appropriate visual ID and a generation-scoped bind; cleanup sends unbinds.

- [ ] **Step 1: Add failing tests** for no Endermite in config/source spawn switches, spider wave composition, exact boss values, and visual IDs for common Enderman, elite, spider, shulker, and guardian.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Change only the wave profile and visual binding seams; keep natural vanilla entities untouched.**
- [ ] **Step 4: Run focused contracts, Java AI tests, and plugin/client builds.**
- [ ] **Step 5: Commit `fix: align End Rift waves and entity visual bindings`.**

### Task 4: Finish original mob/boss texture assets and render contracts

**Files:**
- Modify/Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_guardian.png`
- Modify/Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_enderman.png`
- Modify/Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_elite.png`
- Modify/Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_spider.png`
- Modify/Create: `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_shulker.png`
- Modify/Create: `docs/art-prompts/end-rift/*.md`
- Modify: `tests/test_end_event_resourcepack_contract.py`
- Create/Modify: `tests/test_end_event_client_texture_contract.py`

**Interfaces:**
- Entity PNGs use the correct vanilla UV dimensions, opaque/non-empty pixels where required, no transparent holes or accidental global overrides, and distinct event palettes.
- The renderer maps only the exact server-announced UUID and visual ID; unbind, generation mismatch, disconnect, death, and world change clear it.

- [ ] **Step 1: Write failing tests** for every required visual asset, dimensions, alpha/palette sanity, client-jar packaging, and per-UUID mapping.
- [ ] **Step 2: Run RED for any missing/weak asset contract.**
- [ ] **Step 3: Inspect each current texture visually, regenerate only the assets that fail the visual bar using the image-generation workflow, save reusable art prompts, and nearest-neighbor validate final PNGs.**
- [ ] **Step 4: Build the client and assert all five entity textures are packaged.**
- [ ] **Step 5: Build the resource pack; verify Core/charged Core/idle rune/occupied rune/Rift Shard assets remain 32×32 and do not override vanilla block textures.**
- [ ] **Step 6: Commit `art: finish original End Rift entity textures`.**

### Task 5: Add a Creative full-event test driver and spell evidence markers

**Files:**
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/plugin.yml`
- Modify: `tests/LocalEndRiftBot.js`
- Modify: `tests/RunEndRiftRuntimeSmoke.ps1`
- Create/Modify: `tests/test_end_event_creative_run_contract.py`

**Interfaces:**
- `/cmend test run creative` is permission-gated, local-only, does not freeze official roster or unlock End, and drives a disposable event through every phase.
- Each phase writes a unique log marker with event ID/generation; spells log telegraph, flight, effect, and cleanup markers.
- The driver verifies core/rune display model IDs, occupied model transitions, entity counts/visual IDs, BossBar, containment, cleanup, and gate layer order through RCON-visible status.

- [ ] **Step 1: Write failing contract tests** for local-only permission, Creative exclusion from official pads, full phase marker order, and spell flight markers.
- [ ] **Step 2: Run RED.**
- [ ] **Step 3: Implement the smallest test-only driver using existing `test` commands and generation guards; do not add a production bypass to ordinary ritual rules.**
- [ ] **Step 4: Run focused contracts and compile the plugin.**
- [ ] **Step 5: Commit `test: drive complete End Rift scenario in Creative locally`.**

### Task 6: Execute isolated runtime, inspect visuals, and build the evidence matrix

**Files:**
- Modify only ignored state under `local-runtime/end-rift-completion-*`
- Create: `docs/superpowers/evidence/2026-08-24-end-rift-completion-runtime.md`
- Create: `docs/superpowers/evidence/2026-08-24-end-rift-requirement-matrix.md`

- [ ] **Step 1: Recheck ordinary ports/process identities. Start only isolated PostgreSQL/Paper/resource HTTP/client inputs.**
- [ ] **Step 2: Build plugin, pack, and client twice; record SHA-256 and restore ordinary `minecraft/server/server.properties` if the pack builder touches it.**
- [ ] **Step 3: Run the Creative scenario from fresh state. Capture core/resources/runes, all wave/boss phases, spells, cleanup, and gate open logs/status.**
- [ ] **Step 4: Exercise gate restart rollback during `OPENING`, `restore confirm`, and victory idempotency.**
- [ ] **Step 5: Use an actual local Fabric client if present; capture screenshots of Core, both rune states, each mob, boss beside a vanilla peer, spells, and the gate animation. Mark any unavailable GUI observation explicitly rather than inferring it from source.**
- [ ] **Step 6: Run the full End Rift Python/Java/PowerShell suites, client tests, resource-pack checks, build checks, `git diff --check`, and clean-tree safety audit.**
- [ ] **Step 7: Populate the requirement matrix with `Confirmed`, `Partial`, `Failed`, or `Unverified`; any non-confirmed material row keeps the goal active.**
- [ ] **Step 8: Commit evidence, push `codex/end-rift-event`, verify remote commit and clean tree.**
