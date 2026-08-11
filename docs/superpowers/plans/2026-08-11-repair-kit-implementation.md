# Repair Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add and harden only the physical `Ремкомплект` AR artifact with ten-AR price, five server-side uses, visible vanilla durability, and no custom-item or processing-inventory repair bypasses.

**Architecture:** Keep the existing catalog and `createOfficialItem` path. Add one `REPAIR_KIT` catalog effect backed by a vanilla `SHEARS` ItemStack with Paper `Damageable#setMaxDamage(5)`, a PDC integer for remaining uses, and a pure math seam for repair/durability calculations. Handle use in a dedicated interaction listener so the existing artifact effects are unchanged; fail closed for all CopiMine PDC metadata and all vanilla processing paths.

**Tech Stack:** Paper/Bukkit 1.21 API, Java 21 source, YAML catalog, standalone Java unit tests, Python `unittest` source contracts, PowerShell validators.

## Global Constraints

- Change only the existing/required `Ремкомплект` logic, catalog configuration, and tests.
- Price is exactly `10 AR`; item has exactly five successful repairs.
- Each successful repair restores 25% of target maximum durability, clamped to the maximum.
- The kit never repairs CopiMine AR, donation, admin-only, narcotic, election, official-AR, or any other CopiMine-PDC item, including another kit or itself.
- A failed/cancelled/invalid repair never consumes a use.
- The kit is a one-item stack and its state persists in PDC plus a real five-point vanilla damage durability bar; no client mod or new Minecraft item ID.
- The companion full AR-artifact task adds `Камень возвращения`, `Бесконечный факел`, and combat corrections in separate handlers without changing existing brewing/world/database state.
- Do not touch the world, database contents, or live server.

### Task 1: Characterization and failing tests

**Files:**
- Create: `tests/test_repair_kit_contract.py`
- Create: `tests/RepairKitMathTest.java`
- Create: `copimine-artifacts/src/me/copimine/artifacts/RepairKitMath.java` (test target is intentionally absent during RED)

- [ ] Write catalog, source-contract, math, and exploit-guard tests first.
- [ ] Run `python tests/test_repair_kit_contract.py` and the Java test command; confirm failure is caused by the missing `repair_kit` catalog/effect and missing math seam, not by a malformed test.

### Task 2: Catalog and state math

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Create: `copimine-artifacts/src/me/copimine/artifacts/RepairKitMath.java`

- [ ] Add the exact AR catalog entry: `repair_kit`, `SHEARS`, `AR_SHOP`, `&dРемкомплект`, `EPIC`, `price_ar: 10`, `REPAIR_KIT`, and stack-safe vanilla material.
- [ ] Implement pure clamped 25% repair, five-to-zero use transitions, and visible damage mapping.
- [ ] Run the focused math/catalog tests green.

### Task 3: Server-side item use and bypass guards

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`

- [ ] Add the repair-kit PDC key and initialize five uses plus real `Damageable` damage/lore in the existing factory only for `REPAIR_KIT`.
- [ ] Add a dedicated right-click handler for mainhand/offhand, validate the opposite hand as a non-CopiMine damaged vanilla `Damageable`, apply 25% repair, then atomically update/remove one kit use.
- [ ] Fail closed on cancelled events, zero/full/non-damageable/custom/self/kit targets and malformed PDC state.
- [ ] Prevent use-count reset/consumption through item damage, crafting, anvil, smithing, grindstone, mending, furnace/brewing, hopper, dispenser/dropper, and item merging without changing unrelated artifact behavior.
- [ ] Reject the kit from the existing paid AR repair GUI so its five-use state cannot be reset.
- [ ] Run focused tests and build after this slice.

### Task 4: Regression and release checks

**Files:** no new product files beyond the above.

- [ ] Run all repair-kit tests and existing `copimine-artifacts` regression/contract tests.
- [ ] Build `CopiMineArtifacts.jar` and inspect the diff/status for unrelated changes.
- [ ] Report any unavailable runtime/integration checks explicitly; do not deploy.
