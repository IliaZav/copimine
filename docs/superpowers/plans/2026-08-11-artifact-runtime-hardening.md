# Artifact Runtime Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair return-stone use, make the streamer stick launch a target roughly 10 blocks upward and backward, and add a dupe-safe server-side Angel Seal to the existing AR artifact system without touching brewing, shops, world data, or unrelated artifacts.

**Architecture:** Keep the existing catalog, PDC authenticity, durable instance-binding, AR purchase, and pending-delivery paths. Add small pure policy/math seams for death preservation and knockback so most behavior is testable without a live server, then connect those seams to the existing Bukkit listeners in `CopiMineArtifacts`.

**Tech Stack:** Java 21, Bukkit/Paper API, PostgreSQL-backed CopiMineArtifacts catalog/delivery bridge, Python contract tests, PowerShell plugin build and local Paper smoke test.

## Global Constraints

- Do not modify the production server, production world, or production database.
- Do not change brewing, ATM, existing shop/payment flow, or unrelated artifact behavior.
- Angel Seal uses the explicit catalog parameters: `FEATHER`, `custom_model_data: 0`, `custom-texture-mode-allowed: false`, `EPIC`, `1000 AR`, max stack `1`, effect `ANGEL_SEAL`.
- Angel Seal must preserve exact item instances and consume exactly one authentic seal only after a real, non-prevented death; XP/levels remain vanilla.
- No client mod, new Minecraft item ID, or resource-pack dependency is required for Angel Seal.
- Every production change gets a failing test first and a focused passing test immediately after implementation.

---

### Task 1: Capture current state and read-only findings

**Files:**
- Create: `docs/superpowers/plans/2026-08-11-artifact-runtime-hardening.md`
- Backup: `D:/Desktop/Copimine/backups/combat-ar-artifacts-pre-angel-2026-08-11/`

**Interfaces:**
- Consumes: current `agent/combat-ar-artifacts` checkout and completed read-only agent reports.
- Produces: an isolated local backup and a file-scoped implementation plan.

- [x] **Step 1: Record the worktree boundary**

Run:

```powershell
git status --short --branch
```

Preserve all pre-existing dirty files outside the artifact task. Do not stage them.

- [x] **Step 2: Create a recoverable local backup**

Copy the current artifact source, `items.yml`, `plugin.yml`, and active local `CopiMineArtifacts.jar` to `D:/Desktop/Copimine/backups/combat-ar-artifacts-pre-angel-2026-08-11/`.

- [x] **Step 3: Incorporate read-only agent evidence**

Use the reports to validate these suspected boundaries before coding: `onArtifactInteract`/`beginReturnStoneChannel`, `findReturnStoneDestination`, `authenticCatalogItem`/binding reload, `onDeath`, and `CombatArtifactMath.awayArcVelocity`.

### Task 2: Add failing regression contracts

**Files:**
- Create: `tests/test_return_stone_runtime_contract.py`
- Create: `tests/test_angel_seal_contract.py`
- Modify: `tests/test_combat_ar_artifacts_contract.py`
- Modify: `tests/test_utility_ar_artifacts_contract.py`

**Interfaces:**
- Consumes: source text and `copimine-artifacts/items.yml`.
- Produces: failing red tests that name the required catalog values, event guards, pure policy seams, and anti-dupe invariants.

- [ ] **Step 1: Write the return-stone failing tests**

Assert that the return-stone path cancels the interaction synchronously at the highest priority, starts only from an authentic held instance, does not rely on a later mutable cancellation check, preserves the 300-second UUID/item cooldown, and resolves a valid respawn location before world spawn. Add a regression assertion that catalog authentication has a durable binding fallback after restart rather than treating a still-owned delivered item as pending.

- [ ] **Step 2: Write the Angel Seal catalog and death-policy failing tests**

Assert the exact item row (`angel_seal`, `FEATHER`, `TOOL`, `AR_SHOP`, `EPIC`, `1000`, `ANGEL_SEAL`, `0`, stack 1, lore), then require named policy seams for selecting one authentic seal across storage/armor/offhand, preserving main inventory/hotbar/armor/offhand exact instances, removing only the selected seal, leaving XP untouched, and refusing consumption when `keepInventory`, totem resurrection, or cancelled death already prevents loss. Assert no drops retain preserved copies.

- [ ] **Step 3: Write streamer-stick failing tests**

Assert that `STREAMER_STICK_ARC` no longer uses the current `1.65D`/`1.25D` impulse and that the pure calculation reaches the specified upward/backward target envelope while keeping self/dead/non-melee guards and cooldown unchanged.

- [ ] **Step 4: Run the focused tests and capture the expected failures**

Run:

```powershell
python -m unittest tests/test_return_stone_runtime_contract.py tests/test_angel_seal_contract.py tests/test_combat_ar_artifacts_contract.py tests/test_utility_ar_artifacts_contract.py -v
```

Expected: the new tests fail because the catalog row, policy seams, and event guards do not yet exist; existing unrelated tests must not be edited to hide failures.

### Task 3: Fix return-stone activation and restart binding

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Test: `tests/test_return_stone_runtime_contract.py`
- Test: `tests/test_utility_ar_artifacts_contract.py`

**Interfaces:**
- Consumes: `authenticCatalogItem(ItemStack, Player, String)`, `beginReturnStoneChannel`, `findReturnStoneDestination`, existing binding/instance caches.
- Produces: a synchronous, cancellable return-stone channel and a restart-safe durable authentication path.

- [ ] **Step 1: Make interaction admission final at the event boundary**

In the existing `RETURN_STONE` branch, after authenticating the item and checking right-click use, call `event.setCancelled(true)` and deny both item/block use before scheduling any channel. Start the channel on the current server tick with the exact held hand/item identity; do not defer a check against a mutable `PlayerInteractEvent` object.

- [ ] **Step 2: Make channel completion identity-safe**

Retain UUID + hand + unique item ID. At completion, re-read the same hand, authenticate it, compare the stored unique ID, require online/alive, and clear the channel on every failure. Keep damage, slot, swap, drop, inventory-click/drag, death, quit, and world-change cancellation behavior.

- [ ] **Step 3: Harden destination resolution**

Use the player’s Bukkit respawn location when valid, search nearby loaded safe positions around bed/anchor spawn, and only then use the resolved main-world spawn. Never load chunks as a side effect. Reject liquid, suffocation, border, and occupied positions consistently.

- [ ] **Step 4: Close the post-restart binding race**

Use the existing durable instance/purchase lookup or a completed binding refresh before rejecting an owned delivered item. A row that is still genuinely `PENDING_DELIVERY` must remain unavailable; a physical item already bound to the player must not be misclassified merely because the async cache has not completed.

- [ ] **Step 5: Run return-stone tests**

Run the two focused Python contract files and the relevant PowerShell utility/PDC validators. Expected: all return-stone assertions pass and no unrelated utility behavior changes.

### Task 4: Add Angel Seal catalog and dupe-safe death preservation

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/AngelSealDeathPolicy.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/AngelSealInventorySnapshot.java`
- Test: `tests/test_angel_seal_contract.py`

**Interfaces:**
- Consumes: `CatalogItem`, `createOfficialItem`, `authenticCatalogItem`, existing PDC keys, `PlayerDeathEvent`, `PlayerInventory`.
- Produces: `AngelSealDeathPolicy.findSeal(...)`, `AngelSealDeathPolicy.preserve(...)`, and an event integration that mutates only the actual death event.

- [ ] **Step 1: Add the exact catalog row**

Add this item to the existing AR catalog without changing category routing or payment code:

```yaml
- id: angel_seal
  category: TOOL
  material: FEATHER
  source: AR_SHOP
  custom_model_data: 0
  name: "&dПечать ангела"
  rarity: EPIC
  price_ar: 1000
  cooldown_seconds: 0
  effect: ANGEL_SEAL
  max_stack_size: 1
  custom_texture_mode_allowed: false
  lore:
    - "&7Сохраняет вещи после смерти"
```

Use the project’s actual YAML key spelling if the loader uses a different existing name, while preserving these values.

- [ ] **Step 2: Define a pure seal-selection policy**

Scan only the player’s real inventory surfaces: storage contents (including hotbar), armor contents, and offhand. For each candidate call the existing authenticity helper; reject feathers based only on name, lore, material, or forged PDC. Select the first deterministic slot order and return its exact unique ID and slot location.

- [ ] **Step 3: Snapshot and preserve exact instances**

Build a snapshot containing copies of every non-empty storage, armor, and offhand `ItemStack`, including official/custom/donation/AR/admin metadata and unique IDs. The snapshot must not include XP or levels. Clear drops that correspond to preserved inventory items, set keep-inventory only for a real unprevented death, and restore the exact surfaces through Bukkit’s death semantics without adding a second copy.

- [ ] **Step 4: Consume exactly one seal**

Remove only the selected physical seal instance from its original slot before the death result is finalized. If stack amount is ever greater than one, decrement one and preserve the remaining authentic stack exactly; catalog creation must still force max stack 1 and event guards must reject artificial merging. Never scan ender chest, shulkers, containers, dropped entities, or external storage for activation.

- [ ] **Step 5: Respect death-prevention paths**

Do not consume a seal when `event.isCancelled()`, `event.getKeepInventory()` is already true, a totem or other `EntityResurrectEvent` succeeds, or another plugin prevents the real death. Do not call `setKeepLevel(true)` and do not modify dropped XP/level fields. Keep donation reclaim/loss journal logic from treating saved donation items as lost.

- [ ] **Step 6: Add event cleanup and anti-dupe guards**

Ensure no preserved item remains in `event.getDrops()` after the seal path, no duplicate restore is scheduled on repeated callbacks, and the seal itself cannot be preserved by its own snapshot. The second real death after one seal must be vanilla; two seals must result in exactly one seal consumed per death.

- [ ] **Step 7: Run Angel Seal focused tests**

Run the focused contract test and compile the pure policy classes. Expected: catalog, authentic/fake/tampered checks, all inventory surfaces, XP, keepInventory/totem, unique-ID, and no-ground-copy assertions pass.

### Task 5: Calibrate streamer-stick knockback

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CombatArtifactMath.java`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `tests/test_combat_ar_artifacts_contract.py`

**Interfaces:**
- Consumes: existing `awayArcVelocity` direction convention and melee/cooldown guards.
- Produces: a deterministic velocity calculation targeting approximately 10 blocks of backward displacement plus a clearly upward launch without changing trigger rules.

- [ ] **Step 1: Add a failing pure-math distance test**

Use a bounded simulation of Minecraft vertical gravity/drag and horizontal drag to assert the configured impulse reaches the target envelope, while direction reverses away from attacker and vertical velocity is positive.

- [ ] **Step 2: Implement the smallest math/config change**

Replace the underpowered constants or add a named `STREAMER_STICK_TARGET_BLOCKS` calculation; preserve `isStreamerMeleeHit`, owner/self/dead checks, one trigger per cooldown, and `setFallDistance(0.0F)`.

- [ ] **Step 3: Run combat tests**

Run the focused combat contract tests and relevant PowerShell combat validator. Expected: only the intended knockback behavior changes.

### Task 6: Build and execute local runtime verification

**Files:**
- Modify only generated local artifact output as part of the build; do not stage generated JAR unless explicitly needed by the release workflow.
- Test: relevant `tests/*.py` and `tests/*.ps1` files.

**Interfaces:**
- Consumes: compiled source, isolated `local-runtime` PostgreSQL, local Paper server, and existing AR bridge.
- Produces: fresh test/build/log/hash evidence and a no-production-deploy decision.

- [ ] **Step 1: Run focused and full regression tests**

Run the new contract tests, existing `copimine-artifacts` tests, relevant PowerShell validators, and the broad project regression command available in the checkout. Record exact passed/failed counts.

- [ ] **Step 2: Build twice and compare artifacts**

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-artifacts\build-plugin.ps1` twice, confirm compilation has no new errors, and compare the source-built JAR with the local server copy by SHA-256.

- [ ] **Step 3: Run local Paper/PostgreSQL smoke tests**

On the isolated local stack only: buy/claim `angel_seal` for 1000 AR, place one seal in main inventory, armor, and offhand in separate cases, perform real deaths, respawn, inspect all exact instances and XP, verify the seal count decreases one at a time, verify no ground duplicates, and verify the next unprotected death drops normally. Also smoke-test return-stone bed/fallback/cooldown and streamer-stick knockback.

- [ ] **Step 4: Inspect logs and git diff**

Require plugin enable success, no artifact/delivery exceptions, clean `git diff --check`, and only intended files changed. Preserve all unrelated dirty files and do not deploy to production.

### Task 7: Final evidence review

**Files:**
- Modify: none unless a verification-discovered defect requires returning to its owning task.

**Interfaces:**
- Consumes: fresh test/build/runtime outputs and `git status`.
- Produces: an evidence-based report with explicit gaps; no “fixed” claim without a passing command.

- [ ] **Step 1: Check every requirement against evidence**

Report catalog values, PDC/unique-ID behavior, exact death surfaces, XP semantics, anti-dupe result, restart binding result, return-stone cooldown/destination result, streamer distance result, and texture assumption.

- [ ] **Step 2: Report deployment status accurately**

If any real Paper death/anti-dupe smoke test cannot run, report `NOT DEPLOYED — runtime death/anti-dupe behavior not verified` and do not touch the production server.
