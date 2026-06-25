# CopiMineNarcotics Phase 1 Implementation Plan

## Goal

Rebuild `CopiMineNarcotics` from scratch for Phase 1 only without touching donation, website, or Discord whitelist flows. Deliver a clean narcotics gameplay plugin with full cauldron brewing, all approved recipes, `Жужево`, official PDC items, hidden overdose logic, admin commands, storage and processing guards, safe resource pack integration, admin-controlled visual modes, validators, jar build, and a manual smoke checklist.

## Constraints

- Do not break `CopiMineUltimateAdminPlus`, `CopiMineEconomyCore`, `CopiMineElectionCore`, or `CopiMineArtifacts`.
- Do not change the shared resource pack URL.
- Do not globally override vanilla assets.
- Keep fallback visuals as the default. Overlay and shader paths must require explicit admin enablement.
- Clear only narcotics-related DB/state.
- Use the old narcotics runtime only as unsafe reference; do not preserve its architecture.

## Phases

### 1. Rebuild plugin skeleton

- Replace the current single-file narcotics runtime with a package-based structure.
- Rewrite `plugin.yml`, `config.yml`, and build scripts for multi-class compilation.
- Keep command ownership inside `CopiMineNarcotics`.
- Remove old black-market and admin-give-only assumptions from the active runtime.

### 2. Implement narcotics domain and config

- Add narcotic definitions for all approved items and `Жужево`.
- Add strict PDC schema with `copimine_item_type=RP_NARCOTIC`.
- Add recipe registry, ingredient matchers, official item factory, and item authenticity checks.
- Add admin config for texture mode, visual modes, effect toggles, overdose weights, and cooldowns.

### 3. Implement brewing, use, and overdose

- Add full-water vanilla cauldron brewing with order-independent recipes.
- Add invalid-mixture fallback to `Жужево`.
- Add item consumption, hidden overdose scale, and per-player usage windows.
- Block milk/bucket cheese-out during active overdose if configured.
- Add storage allowed/blocking rules for official finished items only.

### 4. Add DB, async services, and reset

- Introduce narcotics-only tables and idempotent schema application.
- Keep DB work off the main thread.
- Add audit logging and narcotics-only reset.
- Persist usage and overdose state where needed; keep hot gameplay caches in memory.

### 5. Add visual runtime and resource pack integration

- Implement visual runtime with modes `FALLBACK`, `OVERLAY`, `SHADER`.
- Default to fallback only.
- Add admin commands to enable visuals, choose mode, and toggle specific effects.
- Generate or add safe placeholder narcotics textures/assets under `assets/copimine/...`.
- Document asset search/licensing outcome and use self-made placeholders where needed.

### 6. Validators, smoke checklist, build, verification

- Add Phase 1 narcotics validators for runtime, recipes, PDC, storage/processing guards, visuals, threading, and resource pack assets.
- Add `tests/manual/NARCOTICS_TEXTURES_SHADERS_SMOKE_CHECKLIST.md`.
- Build jar and resource pack.
- Run validators and syntax/build checks relevant to touched code.

## Verification checkpoints

1. Plugin rebuild compiles before adding complex gameplay.
2. Cauldron recipe flow works and issues official items.
3. Official items consume, apply normal effects, and update overdose scale.
4. Storage allowed/blocking rules work without affecting ordinary ingredients.
5. Visual runtime defaults to fallback and does not activate overlay/shader unless explicitly enabled.
6. Resource pack builds, zip validates, and SHA1 updates if pack changed.
7. Validators pass for the new narcotics Phase 1 contract.
