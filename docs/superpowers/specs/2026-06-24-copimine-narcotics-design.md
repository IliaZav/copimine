# CopiMineNarcotics Design V2

**Date:** 2026-06-24

**Scope:** Phase 1 only. New `CopiMineNarcotics` from scratch with full narcotics gameplay, cauldron brewing, recipes, `Жужево`, official PDC items, hidden overdose scale, admin commands, storage and processing guards, texture mode `VANILLA/CUSTOM`, visual and shader framework with fallback, resource pack integration, validators, build output, and manual smoke checklist. No donation, website, or Discord whitelist work in this phase.

## Compatibility Audit

### Current plugin compatibility

- `CopiMineUltimateAdminPlus` depends on `CopiMineEconomyCore` and acts as hub/delegator.
- `CopiMineElectionCore` depends on `CopiMineEconomyCore`.
- `CopiMineArtifacts` depends on `CopiMineEconomyCore`.
- Current `CopiMineNarcotics` has no hard dependency on other CopiMine modules.
- This allows a clean narcotics rebuild without coupling gameplay to economy, elections, or admin runtime.

### Current narcotics runtime

- Existing `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java` is a small admin-give-only plugin.
- It already uses `NamespacedKey`, `PersistentDataContainer`, and `CustomModelData`.
- It has no PostgreSQL runtime, no cauldron brewing, no overdose scale, no texture modes, no resource-pack manifest integration, and no shared visual runtime.
- Russian UI strings in `plugin.yml` and `config.yml` are mojibake and must be replaced.
- The old runtime is acceptable only as unsafe reference for basic PDC style and command naming.

### Resource pack state

- Shared resource pack source exists at `resourcepacks/src/assets/copimine/...`.
- Existing pack already contains block visual manifests and textures under the `copimine` namespace.
- This is compatible with extending the same pack for narcotics textures, overlays, shader assets, and manifests.
- Pack rules already state no global vanilla override.

### DB and threading risk snapshot

- Current narcotics runtime has no DB, so all narcotics tables for the rebuild will be new.
- Main-thread risk today is not SQL but direct gameplay and effect handling on use events.
- New design must keep Bukkit world, inventory, and entity work on main thread and all DB work async.
- No full-world scans and no unbounded repeating tasks are allowed.

### Item and PDC risk snapshot

- Current plugin only checks `item_id` plus `CustomModelData`.
- New runtime must formalize PDC schema for official narcotic items and reject fake vanilla copies.
- Finished narcotics and `Жужево` must be blocked in all processing and automation inventories, but still allowed in player inventory, chest, barrel, shulker box, and ender chest storage.

## Architecture

The approved architecture is a two-layer design.

### Layer 1: Narcotics gameplay layer

`CopiMineNarcotics` owns:

- cauldron brewing
- recipe matching
- item issuing
- item consumption
- overdose logic
- admin commands
- config and DB migrations
- narcotics-only reset and self-check

This layer knows what each narcotic is, what ingredients it requires, and what gameplay effects it applies.

### Layer 2: Shared visual runtime layer

A shared internal visual runtime package or module is introduced for reusable visual logic:

- visual profile registry
- capability detection
- shader, overlay, and fallback routing
- per-player visual sessions
- timed cleanup
- texture mode handling
- resource-pack manifest contract

This layer does **not** know recipes, cauldrons, or overdose thresholds. It only applies named visual profiles to a player for a bounded duration.

### Why this architecture

- Avoids rebuilding the old monolith.
- Keeps narcotics gameplay independent from visual implementation details.
- Lets future systems reuse the same visual runtime without coupling to narcotics.
- Centralizes fallback behavior so the plugin never lies about “real shaders” when only overlay or fallback modes are active.

## Ownership Boundaries

### `CopiMineNarcotics`

- Owns narcotics DB tables.
- Owns official narcotic item schema.
- Owns brewing state and overdose state.
- Calls visual runtime by profile ID only.

### Visual runtime

- Owns visual mode selection and fallback chain.
- Owns active per-player visual sessions.
- Owns cleanup on quit, death, world change, and plugin disable.
- Does not own narcotics recipes or gameplay rules.

### Resource pack

- Remains one shared large pack.
- Narcotics assets live only inside `assets/copimine/...`.
- No global replacement of vanilla block or item textures.

## Planned File Structure

### Plugin root

- `copimine-narcotics/plugin.yml`
- `copimine-narcotics/config.yml`
- `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`

### Gameplay services

- `me/copimine/narcotics/config/NarcoticsConfig.java`
- `me/copimine/narcotics/config/NarcoticsConfigService.java`
- `me/copimine/narcotics/db/NarcoticsDatabase.java`
- `me/copimine/narcotics/db/NarcoticsMigrationService.java`
- `me/copimine/narcotics/recipe/NarcoticsRecipeService.java`
- `me/copimine/narcotics/recipe/IngredientMatcher.java`
- `me/copimine/narcotics/cauldron/CauldronBrewingService.java`
- `me/copimine/narcotics/cauldron/CauldronState.java`
- `me/copimine/narcotics/item/NarcoticItemFactory.java`
- `me/copimine/narcotics/item/NarcoticItemService.java`
- `me/copimine/narcotics/use/NarcoticUseService.java`
- `me/copimine/narcotics/use/OverdoseService.java`

### Visual runtime

- `me/copimine/visualruntime/VisualRuntimeService.java`
- `me/copimine/visualruntime/VisualProfileRegistry.java`
- `me/copimine/visualruntime/VisualSessionStore.java`
- `me/copimine/visualruntime/TextureModeService.java`
- `me/copimine/visualruntime/OverlayRuntime.java`
- `me/copimine/visualruntime/ShaderCapabilityService.java`
- `me/copimine/visualruntime/FallbackVisualApplier.java`

### Commands and listeners

- `me/copimine/narcotics/command/NarcoticsAdminCommand.java`
- `me/copimine/narcotics/check/NarcoticsSelfCheck.java`
- `me/copimine/narcotics/listener/NarcoticsCauldronListener.java`
- `me/copimine/narcotics/listener/NarcoticsUseListener.java`
- `me/copimine/narcotics/listener/NarcoticsProtectionListener.java`
- `me/copimine/narcotics/listener/NarcoticsPlayerCleanupListener.java`

## Database Design

Only narcotics-related state is introduced or reset.

### Required tables

- `narcotics_schema_version`
- `narcotics_brewing_states`
- `narcotics_player_overdose`
- `narcotics_player_usage_window`
- `narcotics_config_values`
- `narcotics_admin_audit`
- `narcotics_item_texture_migrations` optional

### Rules

- All migrations must be idempotent.
- Reset clears only these tables.
- No changes to economy, election, auth, website, or artifact tables.
- Brewing state is keyed by `world + x + y + z`.
- No public per-player narcotics logs are exposed.

## Brewing Model

Brewing must work on any ordinary **full water cauldron**.

### Flow

1. Player places a normal vanilla cauldron.
2. Cauldron is filled to full water level.
3. Player right-clicks with an ingredient.
4. Ingredient is consumed from hand.
5. Brewing state updates at the cauldron coordinates.
6. Ingredient order is ignored.
7. After every add:
   - exact recipe match is checked
   - “can still become some valid recipe” is checked
8. If exact match exists:
   - result item drops
   - cauldron state resets
9. If no valid recipe remains:
   - `Жужево` drops
   - cauldron state resets
10. If cauldron is broken or loses water before completion:
   - stored ingredients drop back
   - state is deleted

### Explicit non-features

- No admin-created special cauldrons.
- No protected cauldron blocks.
- No display entities over brewing cauldrons.
- No global registry of every cauldron in the world.

## Items and PDC

All finished narcotics and `Жужево` are official items with PDC metadata.

### Required PDC

- `copimine_item_type = RP_NARCOTIC`
- `narcotic_id`
- `narcotic_version`
- `official = true`

### Validation

- Normal vanilla items with the same material do nothing.
- Renamed items without PDC do nothing.
- Wrong `CustomModelData` or mismatched PDC is rejected.

### Storage and processing policy

Allowed:

- player inventory
- chest
- barrel
- shulker box
- ender chest

Blocked for finished official `RP_NARCOTIC` items and `Жужево`:

- player crafting grid
- crafting table
- crafter or autocrafter
- furnace
- smoker
- blast furnace
- brewing stand
- smithing table
- anvil
- grindstone
- stonecutter
- hopper
- dropper
- dispenser
- similar processing or automation inventories

Important:

- Only finished official narcotics and `Жужево` are blocked in those inventories.
- Ordinary recipe ingredients are **not** blocked and must remain usable in normal gameplay and storage.

## Recipes

All recipes are order-independent. The exact Phase 1 catalog is:

### `feta`

- item id: `feta`
- display name: `Фета`
- material: `WHITE_DYE`
- texture key: `narcotic_feta`
- `CustomModelData`: `810001`
- overdose weight: `30`
- recipe ingredients:
  - `WHITE_DYE`
  - `GLOWSTONE_DUST`
  - `RABBIT_FOOT`
  - potion with effect `WEAKNESS` level I
- compatible potion note:
  - splash or lingering weakness potion also counts if the effect type matches
- normal effects:
  - `SPEED` level II for 90 seconds
  - `RESISTANCE` level I for 40 seconds
- overdose effects:
  - `POISON` for 90 seconds
  - `WEAKNESS` for 90 seconds
  - `SLOWNESS` for 90 seconds
- visual effect id: `DESATURATE`

### `kola`

- item id: `kola`
- display name: `Кола`
- material: `SUGAR`
- texture key: `narcotic_kola`
- `CustomModelData`: `810002`
- overdose weight: `35`
- recipe ingredients:
  - `SUGAR`
  - `DIAMOND`
  - any jungle leaves variant
- normal effects:
  - `SPEED` level II for 180 seconds
- overdose effects:
  - custom `INFECTION` fallback for 180 seconds
  - `POISON` for 90 seconds
- visual effect id: `COLOR_CONVOLVE`

### `girion`

- item id: `girion`
- display name: `Гирион`
- material: `SLIME_BALL`
- texture key: `narcotic_girion`
- `CustomModelData`: `810003`
- overdose weight: `40`
- recipe ingredients:
  - `SLIME_BLOCK`
  - `TURTLE_HELMET`
  - `EMERALD`
- normal effects:
  - `REGENERATION` level I for 90 seconds
  - `RESISTANCE` level I for 40 seconds
  - `ABSORPTION` level I for 120 seconds
- overdose effects:
  - `SLOWNESS` level IV for 20 seconds
  - `WITHER` level I for 40 seconds
- visual effect id: `SCAN_PINCUSHION`

### `sbp`

- item id: `sbp`
- display name: `Сбп`
- material: `GOLD_NUGGET`
- texture key: `narcotic_sbp`
- `CustomModelData`: `810004`
- overdose weight: `30`
- recipe ingredients:
  - `GOLD_NUGGET`
  - `GOLDEN_CARROT`
  - `STRING`
- normal effects:
  - `LUCK` level I for 300 seconds
  - `INVISIBILITY` level I for 180 seconds
- overdose effects:
  - `WEAVING` for 180 seconds if supported by the server version
  - custom fallback if `WEAVING` is unsupported
  - inverted movement for 120 seconds
- visual effect id: `GREEN_NOISE`
- inverted movement rules:
  - affects only movement
  - does not rotate camera
  - expires by timer and by `clearoverdose`

### `sos`

- item id: `sos`
- display name: `Сось`
- material: `BONE_MEAL`
- texture key: `narcotic_sos`
- `CustomModelData`: `810005`
- overdose weight: `35`
- recipe ingredients:
  - `BONE`
  - `IRON_BLOCK`
  - `GHAST_TEAR`
- normal effects:
  - `STRENGTH` level II for 90 seconds
  - `JUMP_BOOST` level II for 90 seconds
- overdose effects:
  - `BLINDNESS` for 20 seconds
  - `SLOWNESS` for 90 seconds
- visual effect id: `INVERT`

### `drun`

- item id: `drun`
- display name: `Друн`
- material: `PAPER`
- texture key: `narcotic_drun`
- `CustomModelData`: `810006`
- overdose weight: `25`
- recipe ingredients:
  - `AMETHYST_BLOCK`
  - `END_ROD`
  - `IRON_INGOT`
- normal effects:
  - `HASTE` level II for 60 seconds
  - `GLOWING` for 40 seconds
- overdose effects:
  - `HUNGER` for 60 seconds
  - `UNLUCK` for 60 seconds if supported
  - custom fallback if `UNLUCK` is unsupported
- visual effect id: `WOBBLE`

### `chups`

- item id: `chups`
- display name: `Чупс`
- material: `BLUE_STAINED_GLASS_PANE`
- texture key: `narcotic_chups`
- `CustomModelData`: `810007`
- overdose weight: `30`
- recipe ingredients:
  - `BLUE_STAINED_GLASS`
  - potion with effect `SPEED` level I
  - `COCOA_BEANS`
  - `IRON_NUGGET`
- compatible potion note:
  - splash or lingering speed potion I also counts if the effect type matches
- normal effects:
  - `SPEED` level I for 90 seconds
  - `WATER_BREATHING` for 180 seconds
  - `REGENERATION` for 90 seconds
  - `SLOW_FALLING` for 90 seconds
- overdose effects:
  - `POISON` for 120 seconds
  - `WITHER` for 40 seconds
  - custom `INFECTION` fallback for 180 seconds
  - `BAD_OMEN` for 6000 seconds if supported
- visual effect id: `BLOBS`

### `borshevik`

- item id: `borshevik`
- display name: `Борщевик`
- material: `KELP`
- texture key: `narcotic_borshevik`
- `CustomModelData`: `810008`
- overdose weight: `40`
- recipe ingredients:
  - `LARGE_FERN`
  - `DRIED_KELP_BLOCK`
  - `SUGAR_CANE`
- normal effects:
  - `REGENERATION` for 45 seconds
  - `FIRE_RESISTANCE` for 180 seconds
- overdose effects:
  - `DARKNESS` for 60 seconds
  - `INSTANT_DAMAGE` level I once
- visual effect id: `PENCIL`

### `zhuzevo`

- item id: `zhuzevo`
- display name: `Жужево`
- material: `BROWN_DYE`
- fallback material: `ROTTEN_FLESH`
- texture key: `narcotic_zhuzevo`
- `CustomModelData`: `810009`
- overdose weight: `100`
- recipe source:
  - has no normal recipe
  - appears only from an invalid mixture
- normal effects:
  - none
- on use:
  - immediately triggers overdose
  - selects several random or combined effects from all overdose pools
  - selects a random visual effect from available profiles
  - shows no text
  - plays no sound

## Consumption and Overdose

### Consumption

When a player consumes an official narcotic item:

- item authenticity is validated
- one item is consumed
- normal effects are applied
- usage window updates
- overdose logic re-evaluates
- visual runtime is called

No chat spam, no sounds, no debug text, and no player-facing hidden-scale information.

### Hidden overdose scale

The overdose scale is internal only.

- no GUI
- no sidebar
- no messages describing the exact scale
- no values exposed to player

The service tracks recent usage weights per player and promotes the player into overdose when thresholds are exceeded.

### Overdose rules

- overdose applies item-specific downside effects
- may alter movement for specific profiles like `sbp`
- must clean up by timer or admin clear
- cannot be removed trivially if the design says milk is blocked during overdose

`Chups` fourth-use overdose behavior is part of the required validator set and must be implemented exactly.

## Visual and Shader Framework

### Normal gameplay

- visuals stay silent by default
- no overlay or shader noise while the player is not consuming or overdosing

### Active use and overdose

When active:

- fallback visuals are the only default behavior
- overlay or shader paths are used only if an admin explicitly enabled visuals, enabled the mode, and enabled the specific effect IDs through commands
- fallback chain is mandatory and truthful

Priority:

1. fallback visuals
2. overlay mode from resource pack if admin-enabled and assets exist
3. shader-capable mode only if admin-enabled, technically supported, and assets exist

### Safety rules

- No false claims that “true shader” is active if only fallback is used.
- No forced unsupported client shader path.
- No global broadcast effects.
- No unbounded repeating task per player.
- Cleanup required on quit, death, plugin disable, and world change.

## Internet Search and Licensing Policy for Shader and Overlay Assets

Before creating self-made assets, the implementation must first search for compatible online shader, overlay, and post-effect assets.

Allowed sources:

- assets with clear permissive or commercially safe licenses
- MIT
- Apache-2.0
- CC0
- CC-BY with attribution
- other clearly permissive redistribution-safe licenses

Required documentation for every third-party asset used:

- source URL
- author
- license name
- local copy of `LICENSE` or `NOTICE` if provided
- entry in `assets/copimine/THIRD_PARTY_NARCOTICS_VISUALS.md`

Forbidden:

- hotlinking
- runtime downloads from external URLs
- random shader packs without explicit license
- assets with unclear redistribution rights
- claiming support for OptiFine-only or Iris-only packs as mandatory server-side visuals

If no compatible and safely licensed assets are found:

- create self-made placeholder overlays or shader-adjacent assets locally
- document them as self-made placeholders
- keep gameplay fully functional in fallback mode

## Texture Modes and Resource Pack

The existing shared resource pack remains the only pack.

### Modes

- `VANILLA`
- `CUSTOM`

Default:

- gameplay must still work in vanilla mode

`CUSTOM` mode:

- uses narcotics models, textures, and overlays from the shared pack
- may include migration utilities for online items

### Resource pack additions

Expected additions under `assets/copimine/...`:

- narcotics item textures
- narcotics item models
- overlays
- optional shader assets
- manifests:
  - narcotics items
  - narcotics visuals
  - optional shader capability manifest

### Rules

- Keep the current pack URL unchanged.
- No global vanilla override.
- No duplicate `CustomModelData`.
- Rebuild zip and recompute SHA1 if pack changes.

## Admin Commands

Primary command root:

- `/cmnarcotics`

Planned subcommands:

- `reload`
- `reset confirm`
- `selfcheck`
- `give <player> <item>`
- `giveall <player>`
- `texture mode vanilla`
- `texture mode custom`
- `texture migrate online`
- `texture migrate nearby`
- `visuals status`
- `visuals enable <effectId>`
- `visuals disable <effectId>`
- `visuals mode fallback`
- `visuals mode overlay`
- `visuals mode shader`
- `visuals test <player> <effectId> [seconds]`
- `clearoverdose <player>`
- `info <player>`
- `setweight <id> <value>`
- `setthreshold <value>`
- `setwindow <seconds>`
- `setduration <seconds>`

All admin actions must be permission-gated and logged to narcotics audit storage.

## Threading and Performance

### Main thread only

- Bukkit inventory, world, and entity operations
- player effect application
- item consume and drop
- any visual entity interaction

### Async only

- DB queries and updates
- migration work
- heavy self-check work

### Hard rules

- no full-world scan
- no every-tick DB work
- no unbounded maps
- bounded executor only
- safe shutdown

## Old Runtime Removal Strategy

The old runtime is not migrated forward.

### Required actions

- replace the old admin-give-only behavior
- remove old black-market and runtime assumptions from active logic
- keep old file only as unsafe reference until fully replaced
- clear only narcotics-related state when reset is invoked

## Validators

Validators for Phase 1 must cover:

### Gameplay

- clean plugin structure
- old runtime removed
- DB reset only narcotics
- any vanilla full water cauldron brewing
- no special cauldron creation
- order-independent recipes
- all recipes configured
- PDC required
- no finished-item crafting, furnace, brewing, or automation processing
- storage still allowed
- ingredients not blocked
- no item dupe

### Overdose

- hidden overdose scale
- `Chups` fourth-use overdose
- milk blocked during overdose
- no player-facing scale exposure

### Visuals

- visuals disabled in ordinary gameplay
- per-effect toggle
- no global broadcast
- cleanup works
- shader fallbacks
- no false shader claims
- no forced unsupported client shaders

### Resource pack

- texture assets in pack
- vanilla and custom texture mode
- no vanilla global override
- `CustomModelData` uniqueness
- manifests exist
- licenses documented where needed
- internet search and third-party notice rules are enforced

### Performance and safety

- no main-thread heavy DB
- no world-wide scan
- no duplicate effect tasks
- safe cleanup

## Build and Outputs

### Required outputs

- `CopiMineNarcotics.jar`
- updated resource pack only if assets changed
- validators
- manual smoke checklist

### Manual smoke checklist file

- `tests/manual/NARCOTICS_TEXTURES_SHADERS_SMOKE_CHECKLIST.md`

## Manual Smoke Coverage

Required manual checks:

- every recipe
- every admin command
- texture mode `VANILLA/CUSTOM`
- online item migration
- visual mode disabled, enabled, and fallback
- cauldron break
- water loss reset
- overdose
- processing and automation block protection
- cleanup on quit, death, and reload

## Risks

### Main risks

- rebuilding another monolith
- overpromising shader support
- item duplication on brew break, restart, or double interaction
- unsafe inventory or storage migration

### Mitigations

- keep a strict layer boundary
- centralize visual fallback logic
- prefer bounded migrations
- enforce validator coverage before claiming completion
