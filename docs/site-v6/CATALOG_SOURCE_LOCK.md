# CopiMine website v6: reviewed catalog source

Reviewed: 2026-10-16. This is a source lock for the website prototype, NOT a claim that this file is the current production database.

## Canonical source for this prototype

- Repository: IliaZav/copimine
- Branch: codex/site-launcher-audit
- Reviewed commit: a16dc50002f3cde94a4f0e350a18fccd4e1c8bf5
- File: copimine-artifacts/items.yml
- Git blob: 8d564579d1b10dd44edce6b409e8951abf93bd28
- Local SHA-256: c528a1d1c7ec8e186b0d362a5eaafd16e16099ba04edfdc202696741b7c85ce8
- 41 catalog records: 31 public entries (20 AR, 11 donation), 8 restricted notes, 2 administrative entries.
- The removed teleport compass MUST NOT be reintroduced from older branches.

## Cross-check results

The local site-launcher-audit copy matches this source. Older main/deep-audit and End Rift copies still include the removed compass and differ in item definitions. A local test runtime containing a one-item fixture is not the shop's complete catalog. Do not pick a catalog just because its file modification time is newer. The deployment owner must reconcile the actual production catalog/database before migration.

Keep stable item IDs. Keep the approved storefront display-name overrides separately; do not overwrite them with legacy display strings when reimporting YAML. Preserve source name, storefront name, source hash, visibility and whether the name override is approved. A short description must describe the item's verified behavior, not marketing copy.

Do not merge conflicting definitions across branches without review (for example, the event and donation variants of night_cloak). Do not expose restricted recipes, administrative entries or internal editing reasons in public API payloads.

## Zmei Gorynych asset correction

The handheld model resourcepacks/src/assets/minecraft/models/item/zmei_gorinich_sword.json uses copimine:item/artifacts/zmei_gorynych, not the legacy flat path copimine:item/zmei_gorynych.

Correct reviewed texture:
- resourcepacks/src/assets/copimine/textures/item/artifacts/zmei_gorynych.png
- Git blob: 2c4d98c8a6d1465b97dc598a82a132d8325e1a6a
- 16 x 16 PNG, 363 bytes

The actual release pack/model dispatch must be checked against the deployed client before claiming parity with a running server. This lock records the reviewed handheld texture.

## Website and admin integration boundary

The offline v6 UI is a prototype. Player grants, teleports, bans, voice/text mutes, inventory snapshots and recovery actions must use typed, permission-checked backend operations. Browser role values are not authority. Do not copy the prototype's test-role switch into production.

Before exposing actions: server-side session validation, CSRF, deny-by-default RBAC, target hierarchy, recent MFA for elevated actions, reason/audit records, idempotency and a result acknowledgement from the game adapter are required. Queued or disconnected actions must never be labeled executed.

Map settings use one policy for all public captions and publication workers: interval, minimum data age, enabled state and delayed player-layer visibility. Public terrain bundles must not include world save files, seeds, credentials, playerdata or private waypoints.

This commit only documents the reviewed source. It changes no plugin, item behavior, production database or deployment.
