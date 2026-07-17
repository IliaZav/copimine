# Admin Shops and Donation SBP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build admin shop management, administrative delayed gifts, donation-balance buying, and a disabled SBP payment foundation without removing current flows.

**Architecture:** The admin plugin remains the root hub and delegates the new Shops menu to Artifacts. Artifacts owns all Minecraft menus, QR map lifecycle, shop data, catalog reads, and pending delivery. EconomyCore remains the single writer of donation balances and claims. The existing FastAPI app serves the public placeholder page and retains its existing mock session routes.

**Tech Stack:** Paper/Bukkit Java, PostgreSQL, FastAPI, static HTML/CSS, PowerShell validators.

---

## File Structure

- `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`: root button `open:shops` and delegation.
- `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`: admin shop UI, gift wizard, shop aggregates, donation UI, and QR map cleanup.
- `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`: durable zero-charge gifts and server-priced donation purchases.
- `copimine-artifacts/config.yml`: disabled-provider values.
- `admin-web/backend/main.py`: fail-closed provider configuration.
- `admin-web/frontend/oplataf.html`: responsive public placeholder.
- `tests/ValidateCopiMineAdminGiftFlow.ps1`, `tests/ValidateCopiMineShopDonationFlow.ps1`, `tests/ValidateCopiMineDisabledSbpPage.ps1`: feature contracts.

### Task 1: Failing feature contracts

**Files:** Create the three validators above; modify `tests/ValidateCopiMineDonationFlowStillWorks.ps1`.

- [ ] **Step 1: Write marker assertions**

```powershell
foreach ($marker in @('openAdminShopHub', 'openAdminGiftPlayersAsync', 'ADMIN_GIFT', 'createAdminGiftAsync', 'donationShortfall', 'MapRenderer')) {
  if ($source -notmatch [regex]::Escape($marker)) { $errors.Add("Missing marker: $marker") }
}
```

Assert exactly one gift, no donation debit in `createAdminGiftAsync`, offline player query, disabled catalog listing, player-only notice, `shop_id`, dynamic `pack >= shortfall`, the fixed pack list, disabled provider, the exact placeholder URL, map cleanup, and mobile page meta/CSS.

- [ ] **Step 2: Confirm red**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1; pwsh -File tests/ValidateCopiMineDisabledSbpPage.ps1`

Expected: fail because markers do not exist.

- [ ] **Step 3: Add validators to donation aggregate, run, and commit**

Run: `git add tests; git commit -m "test: cover admin shops and disabled SBP flow"`

### Task 2: Add EconomyCore services and schema

**Files:** Modify `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`; test the first two validators.

- [ ] **Step 1: Write the failing service signatures**

```java
CompletableFuture<Map<String, Object>> createAdminGiftAsync(UUID playerUuid, String playerName, String itemId, String actor, String shopId);
CompletableFuture<Map<String, Object>> purchaseFromDonationBalanceAsync(UUID playerUuid, String playerName, String itemId, String actor, String shopId, String idempotencyKey);
```

- [ ] **Step 2: Confirm red**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1`

Expected: fail with absent EconomyCore contract.

- [ ] **Step 3: Implement minimum transaction-safe code**

```java
lockDonationEntitlement(connection, playerUuid.toString(), normalized);
if (hasOpenDonationEntitlement(connection, playerUuid.toString(), normalized)) {
    throw new IllegalStateException("Player already has an active or unfinished donation entitlement for this item.");
}
```

Add `shop_id TEXT NOT NULL DEFAULT ''` idempotently to `donation_purchases`. The gift creates one zero-price `ADMIN_GIFT` purchase and one `UNCLAIMED` claim without calling `mutateDonationBalanceInConnection`. The purchase resolves catalog price on the server, debits the existing balance ledger once, saves the physical shop id, and creates its claim in the same transaction.

- [ ] **Step 4: Confirm green and commit**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1; pwsh -File copimine-economy-core/build-plugin.ps1`

Expected: validators pass and jar compiles.

Run: `git add copimine-economy-core tests; git commit -m "feat: add attributed donation gifts and purchases"`

### Task 3: Admin Shops menus and aggregates

**Files:** Modify `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java` and `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`; test `ValidateCopiMineAdminGiftFlow.ps1`.

- [ ] **Step 1: Write failing root-action and senior-delete assertions**

- [ ] **Step 2: Confirm red**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1`

Expected: fail before menu actions are present.

- [ ] **Step 3: Implement additive shop controls**

```java
public void openAdminShopHub(Player player) {
    if (!isArtifactsAdmin(player)) { noPermission(player); return; }
    openAdminShopHubMenu(player);
}
private String nextGeneratedShopId() {
    return "shop-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
}
```

Add `open:shops` to both applicable root hub variants. Reuse the existing six-block target check, `saveShop`, visual creation, cache, command paths, and `removeShopWithCleanup`. Add pagination, teleport, and senior-only confirmed delete. Query buyer count and AR turnover from completed `artifact_purchases`; query donation turnover from attributed `donation_purchases`; leave historical unassigned rows out.

- [ ] **Step 4: Confirm green and commit**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File copimine-artifacts/build-plugin.ps1; pwsh -File copimine-admin-plugin/build-plugin.ps1`

Expected: validator passes; jars compile.

Run: `git add copimine-admin-plugin copimine-artifacts tests; git commit -m "feat: manage shops from the admin hub"`

### Task 4: Offline administrative gift wizard

**Files:** Modify `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`; test `ValidateCopiMineAdminGiftFlow.ps1`.

- [ ] **Step 1: Write failing page, hidden catalog, confirmation, and notice assertions**

- [ ] **Step 2: Confirm red**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1`

Expected: fail until the wizard exists.

- [ ] **Step 3: Implement the wizard with bounded discovery**

```sql
SELECT DISTINCT player_uuid, player_name FROM (
 SELECT player_uuid, player_name FROM artifact_purchases
 UNION SELECT player_uuid, player_name FROM donation_purchases
 UNION SELECT owner_uuid, owner_name FROM artifact_item_instances
) known_players WHERE player_uuid<>'' ORDER BY lower(player_name), player_uuid LIMIT ? OFFSET ?
```

Store UUID/name, catalog kind, item id, and page in existing `SessionState`. Show AR and donation pages without filtering disabled entries. AR uses existing official pending-delivery creation tagged `ADMIN_GIFT`; donation calls the new EconomyCore API. Clear wizard state on confirmation, cancel, close, and quit; queue only `В лавке тебя ждёт подарок от администрации` to the recipient.

- [ ] **Step 4: Confirm green and commit**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File copimine-artifacts/build-plugin.ps1`

Expected: validator passes; jar compiles.

Run: `git add copimine-artifacts tests/ValidateCopiMineAdminGiftFlow.ps1; git commit -m "feat: issue delayed shop gifts from admin GUI"`

### Task 5: Donation balance purchase and QR map

**Files:** Modify `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`, `copimine-artifacts/config.yml`, and `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`; test `ValidateCopiMineShopDonationFlow.ps1`.

- [ ] **Step 1: Write failing balance, dynamic-pack, disabled-provider, map, and cleanup assertions**

- [ ] **Step 2: Confirm red**

Run: `pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1`

Expected: fail before flow exists.

- [ ] **Step 3: Implement balance UI and server-priced purchase**

```java
long shortfall = Math.max(0L, donationPrice - donationBalance);
List<Long> selectable = TOPUP_PACKS.stream().filter(pack -> standaloneTopUp || pack >= shortfall).toList();
```

Show AR and donation balance in the top-right slot. Keep AR methods unchanged. Use `DonationBalanceService.balanceAsync` only for display, then call `purchaseFromDonationBalanceAsync` for mutation and include the current shop id.

- [ ] **Step 4: Implement full-screen QR safely**

```java
private static final String DISABLED_PAYMENT_URL = "http://copimine.ru:18080/oplataf.html";
private static final List<Long> TOPUP_PACKS = List.of(100L, 200L, 300L, 500L, 1000L);
```

The payment GUI has `Open QR` and `Cancel payment`. `Open QR` issues a tracked temporary `FILLED_MAP` with a 128x128 pixel-accurate black/white QR and white quiet zone for the URL. Never replace the main-hand item. Delete only the temporary map on cancel, expiry, completion, or quit. Disabled mode can never credit balance or mark success.

- [ ] **Step 5: Confirm green and commit**

Run: `pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1; pwsh -File copimine-economy-core/build-plugin.ps1; pwsh -File copimine-artifacts/build-plugin.ps1`

Expected: validator passes; jars compile.

Run: `git add copimine-artifacts copimine-economy-core tests; git commit -m "feat: add donation balance flow to shops"`

### Task 6: Public disabled-SBP page

**Files:** Create `admin-web/frontend/oplataf.html`; modify `admin-web/backend/main.py`; test `ValidateCopiMineDisabledSbpPage.ps1`.

- [ ] **Step 1: Write and confirm the failing static-page test**

```powershell
$page = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\oplataf.html')
foreach ($marker in @('<meta name="viewport"', 'Оплата пока в разработке', '@media')) {
  if ($page -notmatch [regex]::Escape($marker)) { $errors.Add("Missing marker: $marker") }
}
```

Run: `pwsh -File tests/ValidateCopiMineDisabledSbpPage.ps1`

Expected: fail because the page does not exist.

- [ ] **Step 2: Create page and fail-closed config**

```html
<meta name="viewport" content="width=device-width, initial-scale=1">
<main><h1>Оплата пока в разработке</h1><p>Пополнение донат-баланса появится после подключения СБП.</p></main>
```

Use local mobile-first CSS, readable contrast, 16px body text, a media query, and no payment inputs. Default provider is `DISABLED`; activation requires non-empty merchant URL and secrets from environment. Retain existing mock/admin endpoints.

- [ ] **Step 3: Confirm green and commit**

Run: `pwsh -File tests/ValidateCopiMineDisabledSbpPage.ps1; pwsh -File tests/ValidateCopiMineDonationFlowStillWorks.ps1`

Expected: both pass.

Run: `git add admin-web tests; git commit -m "feat: add disabled SBP payment placeholder"`

### Task 7: Final verification

**Files:** Only established generated jars if build scripts update them.

- [ ] **Step 1: Run all validators**

Run: `pwsh -File tests/ValidateCopiMineAdminGiftFlow.ps1; pwsh -File tests/ValidateCopiMineShopDonationFlow.ps1; pwsh -File tests/ValidateCopiMineDisabledSbpPage.ps1; pwsh -File tests/ValidateCopiMineDonationFlowStillWorks.ps1`

Expected: all pass.

- [ ] **Step 2: Compile all modified plugins**

Run: `pwsh -File copimine-economy-core/build-plugin.ps1; pwsh -File copimine-artifacts/build-plugin.ps1; pwsh -File copimine-admin-plugin/build-plugin.ps1`

Expected: all jars compile without errors.

- [ ] **Step 3: Check scope before final commit**

Run: `git diff --check; git status --short`

Expected: no whitespace errors; stage only this feature's files.
