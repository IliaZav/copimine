# Bow and Crossbow Resource-Pack Animations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Add custom pixel-art textures and vanilla-compatible draw/charge states for the two AR bows, the explosive crossbow, and all recipe-note books, then build and run the isolated local server with the resulting resource pack.

**Architecture:** Keep the server-side item identity in the existing `copimine-artifacts/items.yml` catalog. Extend `resourcepacks/build-resourcepack.py` so generated `bow.json` and `crossbow.json` preserve vanilla predicates and append item-specific predicates that include `custom_model_data`. Use one common note model referenced by eight written-book CMD mappings. Store every new texture as a 16×16 RGBA PNG and every animation state as a separate model/texture pair.

**Tech Stack:** YAML catalog, Python resource-pack builder, Minecraft item-model JSON predicates, Pillow, Python pytest contracts, PowerShell local-stack/build scripts.

## Global Constraints

- Do not register new Minecraft item IDs.
- Do not require a client mod or change the network protocol.
- Preserve vanilla bow and crossbow draw/charge predicates and timing.
- Do not change old PNG files or existing old artifact models.
- Do not touch brewing, shops, databases, the world, or production.
- Use only the isolated local stack at `127.0.0.1` for runtime verification.
- New images must be hard-edged 16×16 RGBA pixel art with no text or watermark.

---

### Task 1: Add failing resource-pack contracts

**Files:**
- Create: `tests/test_bow_crossbow_resourcepack_contract.py`
- Modify: `tests/test_teleport_bow_no_infinity_contract.py` only if the new CMD/name assertions belong there.

**Interfaces:**
- Consumes the catalog, manifests, source models, source PNGs, and `build-resourcepack.py`.
- Produces deterministic assertions for catalog CMD values, vanilla predicate preservation, animation frame files, note-model reuse, PNG dimensions/mode, and ZIP inclusion.

- [ ] **Step 1: Write the failing test**

Assert that `combat_crossbow`, `cobblestone_trail_bow`, and `explosive_crossbow` have positive CMD values `10014`, `10015`, and `10016`; all eight `narcotic_recipe_*` rows have positive CMD values `10017`–`10024`; source models exist for bow/crossbow base and frames; generated root models contain the vanilla predicates `pulling`, `pull`, `charged`, and `firework`; every new PNG is 16×16 RGBA; and all listed files appear in `resourcepacks/build/CopiMineResourcePack.zip` after the build.

- [ ] **Step 2: Run the new test and confirm the expected failure**

Run:

```powershell
local-runtime\venv\Scripts\python.exe -m pytest -q tests/test_bow_crossbow_resourcepack_contract.py
```

Expected: FAIL because the catalog still has zero CMD values for these items and the new models/textures/predicates do not exist.

### Task 2: Add catalog CMD mappings and vanilla predicate generation

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Modify: `resourcepacks/item_texture_sources.json`
- Modify: `resourcepacks/models_manifest.json`
- Modify: `resourcepacks/build-resourcepack.py`
- Test: `tests/test_bow_crossbow_resourcepack_contract.py`

**Interfaces:**
- `build_stage()` continues to generate `assets/minecraft/models/item/bow.json`, `crossbow.json`, and `written_book.json`.
- `vanilla_special_overrides(material)` returns the exact vanilla bow/crossbow predicates before custom overrides.
- Custom overrides contain both the item CMD and the state predicate, so a custom item wins while drawing but vanilla items keep vanilla behavior.

- [ ] **Step 1: Implement catalog/manifests**

Set the three projectile CMD values to `10014`, `10015`, `10016`, mark them as custom-textured, and assign `10017`–`10024` to the eight recipe-note entries. Add one manifest row per catalog item, while all note rows reference the same `copimine:item/artifacts/recipe_note` model and texture.

- [ ] **Step 2: Implement predicate composition**

Preserve the vanilla bow predicates:

```json
{"predicate":{"pulling":1},"model":"minecraft:item/bow_pulling_0"}
{"predicate":{"pulling":1,"pull":0.65},"model":"minecraft:item/bow_pulling_1"}
{"predicate":{"pulling":1,"pull":0.9},"model":"minecraft:item/bow_pulling_2"}
```

Preserve the vanilla crossbow predicates for pull stages, charged arrow, and charged firework. Append custom base/pull/charged entries with `custom_model_data` so the last matching custom state wins without overriding vanilla items.

- [ ] **Step 3: Run the focused contract**

Run the same pytest command. Expected: still FAIL only for missing model/texture assets, proving the catalog and builder assertions are active.

### Task 3: Create pixel textures and model frames

**Files:**
- Create: `resourcepacks/src/assets/copimine/textures/item/artifacts/teleport_bow*.png`
- Create: `resourcepacks/src/assets/copimine/textures/item/artifacts/cobblestone_trail_bow*.png`
- Create: `resourcepacks/src/assets/copimine/textures/item/artifacts/explosive_crossbow*.png`
- Create: `resourcepacks/src/assets/copimine/textures/item/artifacts/recipe_note.png`
- Create: matching JSON files in `resourcepacks/src/assets/copimine/models/item/artifacts/`

**Interfaces:**
- Bow frame model names end in `_pulling_0`, `_pulling_1`, `_pulling_2`.
- Crossbow frame model names end in `_pulling_0`, `_pulling_1`, `_pulling_2`, `_charged`, `_charged_firework`.
- Recipe notes reference one common `recipe_note.json` model.

- [ ] **Step 1: Create RGBA 16×16 assets**

Convert the approved pixel-art designs into hard-edged 16×16 RGBA PNGs. Use separate pull-stage images so the bow limbs/string visibly follow the vanilla draw progression. Keep the crossbow centered and horizontally symmetric in every state; only the string/bolt/TNT indicator changes between states.

- [ ] **Step 2: Create model JSONs**

Use `minecraft:item/generated` with one `layer0` texture per frame. Keep the base models and pull/charge frame references under the `copimine:item/artifacts/` namespace.

- [ ] **Step 3: Run focused source checks**

Run:

```powershell
local-runtime\venv\Scripts\python.exe -m pytest -q tests/test_bow_crossbow_resourcepack_contract.py
```

Expected: PASS for source assets and model contracts; ZIP assertions remain pending until the pack is built.

### Task 4: Build and validate the pack

**Files:**
- Modify generated: `resourcepacks/build/CopiMineResourcePack.zip`, `.sha1`, `.sha256` (do not stage generated build output unless repository policy tracks it).
- Test: `tests/test_bow_crossbow_resourcepack_contract.py`

- [ ] **Step 1: Build**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\resourcepacks\build-resourcepack.ps1
```

- [ ] **Step 2: Verify ZIP/model integrity**

Run the focused test again and inspect the ZIP with Python `ZipFile.testzip()`. Confirm the SHA-1 sidecar equals the actual ZIP hash and the old tracked PNG hashes are unchanged.

### Task 5: Rebuild plugin, restart isolated local server, and verify runtime boundary

**Files:**
- Modify generated local runtime only: `minecraft/server/plugins/CopiMineArtifacts.jar` and copied `items.yml`.

- [ ] **Step 1: Rebuild the plugin**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-artifacts\build-plugin.ps1
```

- [ ] **Step 2: Restart local stack without world reset**

Run `scripts/local-stack.ps1 -Action stop`, then `-Action start`. Do not delete or restore the world/data directories.

- [ ] **Step 3: Verify active services and pack delivery**

Run `scripts/local-stack.ps1 -Action status`; verify PostgreSQL, website, Minecraft, and RCON are ready. Fetch `http://127.0.0.1:8090/resourcepacks/CopiMineResourcePack.zip` and verify HTTP 200 plus matching SHA-1.

### Task 6: Broad regression and handoff

- [ ] **Step 1: Run focused and existing artifact tests**

Run the new contract plus the existing AR/utility/repair/notes/teleport/catalog/local-stack tests.

- [ ] **Step 2: Inspect scope**

Run `git status --short`, `git diff --stat`, and a diff restricted to the new resource-pack files. Confirm no old PNG, world, production path, brewing, or shop file was changed.

- [ ] **Step 3: Report evidence**

Report changed files, CMD mappings, preserved predicates, frame files, test count, build result, local addresses, and explicitly state that production was not deployed.
