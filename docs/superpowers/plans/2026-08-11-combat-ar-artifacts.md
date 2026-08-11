# Combat AR Artifacts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three AR combat projectile artifacts and one hidden streamer artifact with test-backed projectile, trail, explosion, and arc-knockback behavior.

**Architecture:** Keep the existing `CopiMineArtifacts` entry point and catalog. Mark official projectiles at `EntityShootBowEvent` with PDC, process them at `ProjectileHitEvent`, and keep all world mutations on the server thread. Use a bounded main-thread tracker for the crossbow trail and existing authenticity/cooldown gates for melee behavior.

**Tech Stack:** Java 21, Paper/Bukkit 1.21 API, YAML catalog, PowerShell/Python repository validators, deterministic source/contract tests.

## Global Constraints

- Do not modify or wipe worlds, player inventories, elections, accounts, or databases.
- New catalog items have no texture: `custom_model_data: 0`.
- AR items use ordinary purple names and `EPIC` rarity.
- The streamer stick remains `ADMIN_ONLY` and does not appear in the AR shop; combat projectile items use 15-second cooldowns except the explosive crossbow at 30 seconds.
- An accepted explosive-crossbow shot damages its owner by 6 HP exactly once, while Multishot's three projectile events share one shot window.
- Recipe fragments are separate `WRITTEN_BOOK` AR items priced at 300 AR and do not change brewing.
- Do not replace existing blocks while building the trail; route placement through `BlockPlaceEvent`.
- Run tests before committing and verify the exact deployed JAR after upload.

---

### Task 1: Add failing catalog and behavior contracts

**Files:**
- Create: `tests/ValidateCopiMineCombatProjectileArtifacts.ps1`
- Create: `tests/test_combat_ar_artifacts_contract.py`
- Test target: `copimine-artifacts/items.yml`, `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`

**Interfaces:**
- Tests consume catalog markers and source-level behavior contracts.
- Tests produce a red baseline proving the four IDs/effects and required hooks are absent before implementation.

- [ ] **Step 1: Write the failing YAML/source contract**

  Assert the four IDs, prices, materials, source/rarity, purple `&d` names, short lore, `custom_model_data: 0`, projectile event hooks, PDC keys, `Infinity`, `Multishot`, `TNTPrimed`, trail interpolation, and streamer velocity guard.

- [ ] **Step 2: Run the focused tests and confirm expected failure**

  Run:
  `python tests/test_combat_ar_artifacts_contract.py`
  and
  `pwsh -File tests/ValidateCopiMineCombatProjectileArtifacts.ps1`

  Expected: failure because the current catalog and Java source contain none of the new artifact IDs/effects.

- [ ] **Step 3: Commit the red tests and design docs**

  Run:
  `git add docs/superpowers/specs/2026-08-11-combat-ar-artifacts-design.md docs/superpowers/plans/2026-08-11-combat-ar-artifacts.md tests/ValidateCopiMineCombatProjectileArtifacts.ps1 tests/test_combat_ar_artifacts_contract.py`
  `git commit -m "test: define combat projectile artifact contracts"`

### Task 2: Add catalog entries and official item enchantments

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Test: `tests/ValidateCopiMineCombatProjectileArtifacts.ps1`

**Interfaces:**
- `loadCatalogFromConfig` produces `CatalogItem` records for the four IDs.
- `createOfficialItem` adds `INFINITY` only to `cobblestone_trail_bow` and `MULTISHOT` only to `explosive_crossbow`.

- [ ] **Step 1: Add the four catalog records with exact prices and no model data**
- [ ] **Step 2: Add the two enchantments without changing unrelated items**
- [ ] **Step 3: Run catalog-only tests and inspect the diff**

### Task 3: Implement projectile tagging and hit behaviors

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Test: `tests/test_combat_ar_artifacts_contract.py`

**Interfaces:**
- `onCrossbowArtifactShot(EntityShootBowEvent)` authenticates `event.getBow()` and tags each projectile.
- `onProjectileHit(ProjectileHitEvent)` dispatches only tagged projectiles.
- Tracker state is keyed by projectile UUID and is cleared on terminal events/plugin disable.

- [ ] **Step 1: Add PDC keys and a small projectile state record/helper**
- [ ] **Step 2: Tag teleport, trail, and explosive projectiles on the main thread**
- [ ] **Step 3: Add safe teleport resolution and one-shot consumption**
- [ ] **Step 4: Add interpolated cobblestone placement with protection and non-replacement guards**
- [ ] **Step 5: Add primed TNT creation only on explosive projectile hit**
- [ ] **Step 6: Run focused contracts and Java compilation**

### Task 4: Implement streamer stick arc behavior

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Test: `tests/test_combat_ar_artifacts_contract.py`

- [ ] **Step 1: Add `STREAMER_STICK_ARC` to combat effect dispatch**
- [ ] **Step 2: Apply away-from-attacker horizontal velocity plus positive arc velocity**
- [ ] **Step 3: Enforce live target/self-hit/cooldown guards**
- [ ] **Step 4: Run focused tests and existing ability validators**

### Task 4a: Enforce cooldown and explosive-shot self-damage

**Files:**
- Create: `copimine-artifacts/src/me/copimine/artifacts/CombatArtifactShotPolicy.java`
- Modify: `copimine-artifacts/items.yml`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Create: `tests/CombatArtifactShotPolicyTest.java`
- Modify: `tests/test_combat_ar_artifacts_contract.py`
- Modify: `tests/ValidateCopiMineCombatProjectileArtifacts.ps1`

**Interfaces:**
- Vanilla items remain outside the authenticity gate.
- Teleport crossbow, cobblestone crossbow, and streamer stick use 15 seconds; explosive crossbow uses 30 seconds.
- A Multishot event window accepts at most three projectiles from one accepted shot and charges 6 HP once.

- [ ] **Step 1: Add the failing pure policy test for cooldown and three-projectile admission**
- [ ] **Step 2: Implement the bounded Multishot shot window and self-damage gate**
- [ ] **Step 3: Add exact catalog/source contracts and rerun focused tests**

### Task 5: Build and verify the release artifact

**Files:**
- Modify only generated JAR/output files required by the repository build.

- [ ] **Step 1: Run all focused Python/PowerShell validators and syntax checks**
- [ ] **Step 2: Run `copimine-artifacts/build-plugin.ps1` with the repository Paper API**
- [ ] **Step 3: Verify the JAR contains the new classes/strings and the source/catalog hashes match**
- [ ] **Step 4: Review `git diff --check` and `git status --short`**

### Task 6: Publish and verify without world/database changes

**Files:**
- Deployment input: freshly built `CopiMineArtifacts.jar`, synchronized `items.yml`, and the release bundle mechanism already used by the server.

- [ ] **Step 1: Confirm GitHub auth, remote branch, and exact intended file scope**
- [ ] **Step 2: Commit the implementation and push `agent/combat-ar-artifacts`**
- [ ] **Step 3: Upload the verified patch/archive with SHA-256 verification**
- [ ] **Step 4: Restart only the necessary Minecraft/plugin service**
- [ ] **Step 5: Verify service status, live JAR hash, catalog markers, and RCON/plugin self-check**

### Task 7: Generate texture candidates for approval only

**Files:**
- Create: generated candidate PNGs outside the release bundle or in a review-only folder.

- [ ] **Step 1: Generate several simple pixel-art candidates for the three visible items**
- [ ] **Step 2: Display the candidates to the user in chat**
- [ ] **Step 3: Do not copy any candidate into the resource pack until the user explicitly approves one.**

### Task 8: Add narcotic recipe books without changing brewing

**Files:**
- Create: `copimine-artifacts/src/me/copimine/artifacts/NarcoticRecipeBookData.java`
- Modify: `copimine-artifacts/items.yml`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Create: `tests/NarcoticRecipeBookDataTest.java`
- Create: `tests/test_narcotic_recipe_books_contract.py`

**Interfaces:**
- The eight book catalog records are official AR items priced at 300 and use `WRITTEN_BOOK`.
- `BookMeta` receives exactly two pages: centered recipe title and Russian ingredient names.
- The book catalog is checked against the existing narcotics recipe configuration; the `zhuzevo` fallback with no ingredients is excluded.

- [ ] **Step 1: Write failing catalog/data tests for all eight recipes**
- [ ] **Step 2: Add pure recipe-book page data and catalog records**
- [ ] **Step 3: Decorate official written books while preserving existing brewing code**
- [ ] **Step 4: Run focused tests and verify no brewing files changed**
