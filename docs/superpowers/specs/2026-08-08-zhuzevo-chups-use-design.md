# Zhuzevo Use and Chups Jump Boost Design

**Date:** 2026-08-08
**Status:** Approved approach; implementation starts after written-spec review

## Goal

Restore use of a valid Zhuzevo item when another listener has cancelled the
underlying block interaction, while keeping the block action denied, and add
Jump Boost III to Chups' normal effects.

## Evidence and root cause

The current checkout and server were checked before editing:

- Commit `a684e1e` on 2026-08-01 changed the Narcotics listener to run at
  `HIGHEST`, accept cancelled events, and immediately return when
  `event.isCancelled()` is true.
- That return happens after `resolveOfficial(inHand)` but before the state,
  reservation, and `OverdoseService` path. A valid narcotic therefore cannot
  be consumed if a region or interaction listener cancelled the block action.
- The server player data contains a signed
  `NARCOTIC_STACK:zhuzevo:v1` item with CustomModelData `810009`, and the
  database contains an active Zhuzevo stack identity. This rules out missing
  item metadata and a missing issuance row as the primary cause for the
  current item.
- No election source, cauldron source, or brewing state will be changed.

## Scope

### In scope

- `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`:
  reorder only the official-narcotic interaction path so it is evaluated
  before the generic cancelled-event guard.
- `copimine-narcotics/config.yml`: add
  `type:JUMP_BOOST,amplifier:2,duration_seconds:90` to Chups normal effects.
- `tests/test_requested_gameplay_fixes.py`: add regression contracts for the
  event ordering and the exact Chups effect.
- Rebuild and verify the Narcotics plugin. The already-existing AR patch in
  AdminPlus remains untouched by this design; it may be included in the final
  deployment only after an independent hash comparison.

### Out of scope

- Election source, election JAR, website, economy, or database schema.
- `CauldronBrewingService`, recipe handling, brewing persistence, or brewing
  event ordering.
- Removing official-item provenance checks, HMAC checks, issued-instance
  registration, or reservation durability.
- Any new inventory resynchronization or anti-dupe handler.

## Behavior

For a validated official narcotic in the main hand:

1. Keep the existing official-item resolver as the gate.
2. Process only `RIGHT_CLICK_AIR` and `RIGHT_CLICK_BLOCK` as use actions.
3. Keep `setUseItemInHand(DENY)` and deny the underlying clicked block when it
   is unsafe/interactable.
4. Preserve the existing ready-state check, async-capacity check, durable
   reservation, state commit, effect application, and physical consumption.
5. Set the event cancelled for the vanilla interaction side effect, but do
   not reject the already validated narcotic solely because another listener
   cancelled the block interaction first.

For non-narcotic items, preserve the existing cancelled-event guard and the
existing cauldron path byte-for-byte in behavior. This makes the change local
to item consumption and does not alter brewing authorization or mutation.

For Chups, the normal effect list gains Bukkit amplifier `2`, which is the
third effect level. Overdose effects remain unchanged.

## Security and compatibility

The change bypasses cancellation only after `resolveOfficial` succeeds, so an
ordinary or forged item is not granted a new use path. A clicked container or
block remains denied through `setUseInteractedBlock(DENY)`. The database-first
reservation and exact physical removal remain unchanged.

## Tests and verification

- Add a failing source contract that requires the official-narcotic branch to
  appear before the generic `event.isCancelled()` return.
- Add a failing config contract that requires Chups normal effects to contain
  `JUMP_BOOST` with amplifier `2`.
- Run the focused tests in RED before the production edit, then GREEN after it.
- Build only `copimine-narcotics` with its existing build script.
- Inspect the built JAR for the changed handler/config and run the complete
  relevant Python and PowerShell contract suites.
- Confirm no diff under election or cauldron paths.
- Before deployment, make timestamped remote backups of the old plugin JAR and
  mutable config, use the repository's existing release installer workflow,
  verify local/remote SHA-256, remapped/runtime JAR identity, and
  `copimine-minecraft` service status. Never wipe worlds or the database.
