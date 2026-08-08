# AR Creative and Zhuzevo Deep-Scan Report

Date: 2026-08-08
Repository: `D:\Desktop\Copimine\copimine-main`
Commit: `eff54e9` (`fix: restore creative AR transport and repair Zhuzevo`)
Branch: `agent/deep-election-artifacts-audit`

## Scope

Only the AR Creative transport path and Zhuzevo use were changed. Election
source and artifacts were not changed. `CauldronBrewingService.java`, recipe
code, brewing configuration, and brewing persistence were not changed.

## Root causes

### AR in Creative

AdminPlus had two Creative bypasses, but both required
`isOfficialArCreativeClick(...)`. That predicate required the EconomyCore HMAC
signature to validate. The live player probe contained AR items issued before
the current AR signing key, so those stacks failed strict recognition and fell
through to `inventoryLocks`/`checkMode` and the remaining AdminPlus protections.
The Creative path therefore was not vanilla for old, otherwise movable AR
stacks.

The fix removes the signature-gated Creative predicate and the legacy
`InventoryCreativeEvent` branch. `onInv`, `onProtectedItemClick`, and
`onProtectedItemDrag` now return for a Creative player before generic lock or
protected-inventory checks. The menu branch remains first, so AdminPlus menus
continue to work. No AR quantity mutation or inventory resync was added.

### Zhuzevo

The live player snapshot contained `zhuzevo` with the registered fungible
identity `NARCOTIC_STACK:zhuzevo:v1`, but old HMAC signatures. Strict
`resolveOfficial(...)` correctly rejected those signatures, so the normal use
path never ran. The database still had the exact identity as `ACTIVE`, and the
snapshot showed no consumption reservation.

The fix adds a narrow recovery path for right-click use only. It accepts only
the exact current-version Zhuzevo PDC shape, material, model data, stack
identity, and a well-formed old 32-byte signature. It then checks the exact
`ACTIVE` row asynchronously in PostgreSQL, confirms the main-hand stack did
not change, re-signs it with the current server key, and asks the player to
use it again. It does not authorize arbitrary items, consume an item, or alter
brewing/election state.

## Removed/changed handlers

- Removed AdminPlus `isOfficialArCreativeClick(...)`.
- Removed the AdminPlus `InventoryCreativeEvent`-specific AR gate.
- Creative early returns are now before `inventoryLocks`, `checkMode`, and the
  anvil protection path.
- Added `NarcoticItemFactory.resolveRegisteredStackCandidate(...)` and
  `repairRegisteredStack(...)`.
- Added the read-only `NarcoticsDatabase.isActiveIssuedInstance(...)` check.
- Added `CopiMineNarcotics.repairRegisteredZhuzevoUse(...)`.

No legacy AR handlers (`onOfficialArCreative`, AR inventory-move/hopper/
dispense/spawn/smelt handlers, or `normalizeArInventoryState`) remain in the
first-party AdminPlus/EconomyCore/Narcotics source or the rebuilt AdminPlus
class method table.

## Verification

- TDD regression contracts: red on the old logic, green after the patch.
- Python tests: `118 passed, 4 warnings` using `.venv313`.
- Focused AR/Narcotics/election-boundary PowerShell validators: `20/20`
  passed.
- Broad AR/Narcotics validator batch: `154/160` passed. Six pre-existing
  brewing/wrong-mix contract failures remain against the current brewing
  implementation; none references a file changed by this patch, and no
  brewing file was modified.
- AdminPlus and Narcotics plugin builds: passed.
- Two consecutive builds produced identical hashes.
- Local/module and local/server plugin copies:

  - AdminPlus: `3fa1588d80b736a409920b35bfe21c477c21c0a238b35d2cf1cd1035b548d09e`
  - Narcotics: `1677464a1ac9597a5e9baf8ea04aebb8683b600fa3db26c324770ad32da26b1f`

## Deployment evidence

- Remote backup: `/home/qwerty/copimine-upload/backups/codex-ar-zhuzevo-20260808-134508/`
- Backup hashes before replacement:

  - AdminPlus: `7846490e564948403d581bc0d97386d95a17ae611a8cfcc8daa98e96a51ad29f`
  - Narcotics: `36384f5523f91587040efd03f9cf8ff49aa7339120ae779d03d5239a7673a868`

- Remote live hashes after replacement equal the local hashes above.
- Paper remapped hashes after restart:

  - AdminPlus: `fe688118556a6ead6df77e940c76ed8ee15526d6c9d640fbaed982a0bd03c10d`
  - Narcotics: `aea58d9a149cd9006c088eb4746330ffc73e032e96b2fbd551334a2a1a6c073c`

- `copimine-minecraft`: `active`.
- Startup log confirms both JARs were remapped and both plugins enabled.
- Startup log has no AdminPlus/Narcotics error; it reports
  `Restored 0 pending cauldron brew state(s)`.
- Read-only post-deploy database check: `zhuzevo_active=1`, `ar_assets=2`.
- Deployment used only atomic replacement of the two plugin JARs, RCON
  `save-all`, and RCON `stop`; no world or database wipe/reset was run.

## Scan gap

The requested native Codex Security Deep Scan was attempted once, but its
discovery worker could not start because this desktop session did not provide
the required managed filesystem permission profile. The exact result was:

`Codex Security Deep Scan discovery did not start or rejoin ... parent must provide a managed filesystem permission profile.`

The remaining evidence is the manual multi-pass source/history/JAR-bytecode/
Paper-remap/server-log/database scan documented above. No manual client
gameplay was performed; the final player-visible confirmation still requires a
Creative AR move/drag and one stale-Zhuzevo use in-game.
