# CopiMine Donation Shop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full donation shop foundation end-to-end: separate donation currency, mock SBP sessions with QR, website-only item purchase, in-game claim and reclaim, owner-bound donation items, admin top-up/test flows, validators, security hardening, and release verification.

**Architecture:** `CopiMineEconomyCore` remains the single owner of donation money, payment sessions, purchases, and claims. `CopiMineArtifacts` remains the single owner of the shared donation catalog, official item PDC, physical issuance, reclaim, effect logic, and anti-dupe checks. `admin-web` becomes the canonical payment and purchase surface, while the in-game GUI starts top-up sessions and handles claim/reclaim.

**Tech Stack:** Java/Paper plugins, PostgreSQL via existing DB helpers, FastAPI backend, vanilla JS frontend, local QR generation in Python, PowerShell validators, existing plugin build scripts.

---

## File Structure

### Existing files to modify

- `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
  - Extend donation schema, fixed-pack rules, provider/session services, purchase-intent service, idempotent mark-paid, and admin donation APIs exposed via Bukkit services if needed.
- `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\plugin.yml`
  - Add or verify any admin command/perms that donation services need, if a new in-game entry point is introduced here.
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
  - Add shared donation catalog runtime, owner-bound PDC enforcement, claim and reclaim screens, fixed donation statuses, physical issuance safety, loss tracking, and effect handlers.
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml`
  - Add the donation item catalog entries, prices, cooldowns, proc chances, and effect profile ids.
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\config.yml`
  - Add donation GUI settings, website base URLs, QR fallback strings, and reclaim behavior settings.
- `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
  - Add player donation packs/session/purchase endpoints, admin mock SBP controls, QR generation, catalog-backed purchase validation, audit, CSRF/rate-limit wiring, and response shaping.
- `D:\Desktop\Copimine\opt\copimine\admin-web\requirements.txt`
  - Add local QR dependencies if absent.
- `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
  - Add or update page shells for donation balance, donation shop, player donation history, and admin donation management.
- `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`
  - Add player donation cabinet screens, admin donation screens, QR session flows, and website-only purchase UX.
- `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\style.css`
  - Add visual styling for donation cards, status badges, QR blocks, top-up pack buttons, and admin session controls.

### New files to create

- `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_DONATION_SBP_FOUNDATION_SMOKE.md`
  - Manual smoke checklist for website, admin, claim, reclaim, QR, and security flows.
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationBalanceSeparateFromAr.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPacksFixed.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationOneRubOneUnit.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationNoArPurchaseWithRealMoney.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpProviderInterface.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpSessionsTable.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrGeneration.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrNoExternalApi.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineGameSbpQrFallback.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminTopUp.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminMarkPaid.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPurchaseSpendsBalance.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimCreatesOwnerBoundItem.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationNoDuplicateClaims.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationProviderNoSecretsInUi.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationCsrfAndRateLimit.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAuditLog.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopSelectedItemsExist.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopExactNames.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDonationOnlyNoAr.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopOwnerBoundPdc.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDropNoStoreNoTransfer.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDeathReclaimFlow.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDuplicateOwnedItems.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopPrivatePurchaseLinks.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopWebsiteApi.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoFakePayment.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCooldowns.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopControlledLightningNoFireGrief.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryServiceSafeRollback.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryNoProtectedBlocks.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopVampirismCapped.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopAmuletDoesNotClearNarcoticsOverdose.ps1`
- `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCompassOwnerOnlyDeathLocation.ps1`

### Planned logical units inside existing Java files

- `CopiMineEconomyCore`
  - donation pack allowlist
  - mock SBP provider/session service
  - QR payload builder
  - purchase-intent service
  - donation session status transitions
- `CopiMineArtifacts`
  - donation catalog loader
  - donation item factory and lore builder
  - owner-bound inventory guards
  - claim delivery service
  - reclaim service
  - donation effect dispatcher
  - QR GUI adapter and fallback link sender

---

### Task 1: Lock Donation Catalog and Shared Price Contract

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\config.yml`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopSelectedItemsExist.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopExactNames.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDonationOnlyNoAr.ps1`

- [ ] **Step 1: Write the failing validators for the required donation roster and donation-only pricing**

```powershell
$content = Get-Content $ArtifactsItems -Raw
$requiredIds = @(
  "batin_remen_sudnogo_dnya",
  "nu_ty_i_nakopal_blyat_pickaxe",
  "kosa_nalogovoy_inspekcii",
  "kaska_prorab_huev",
  "mne_pohuy_ya_v_tanke_vest",
  "ne_segodnya_suka_shield",
  "ya_esche_ne_vse_isportil_totem",
  "pohuy_na_debaffy_amulet",
  "vremya_platit_nalogi_clock",
  "gde_moy_lut_blyat_compass"
)
foreach ($id in $requiredIds) {
  if ($content -notmatch [regex]::Escape($id)) { throw "Missing donation item id: $id" }
}
if ($content -match "priceAr") { throw "Donation catalog must not expose AR pricing." }
```

- [ ] **Step 2: Run validators to verify they fail on the current donation catalog**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopSelectedItemsExist.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDonationOnlyNoAr.ps1
```

Expected: FAIL because the current catalog still revolves around AR-priced shop items and does not yet define the exact donation roster.

- [ ] **Step 3: Add the shared donation catalog entries and version metadata**

```yaml
donation-catalog:
  catalog-version: 1
  updated-at: 1761321600
  website-base-url: "https://admin.copimine.ru/shop/donation"
  items:
    - item-id: "batin_remen_sudnogo_dnya"
      display-name: "Батин Ремень Судного Дня"
      base-material: "NETHERITE_AXE"
      price-donation: 500
      enabled: true
      source: "DONATION_SHOP"
      owner-bound: true
      reclaim-policy: "LOSS_ONLY"
      consume-policy: "BREAKABLE"
      effect-profile-id: "BATIN_REMEN"
      cooldown-seconds: 25
      proc-chance: 0.15
      max-stack: 1
      repairable: true
      custom-texture-mode-allowed: true
```

```java
private record DonationCatalogItem(
        String itemId,
        String displayName,
        Material baseMaterial,
        long priceDonation,
        boolean enabled,
        String source,
        boolean ownerBound,
        String reclaimPolicy,
        String consumePolicy,
        String effectProfileId,
        int cooldownSeconds,
        double procChance,
        int maxStack,
        boolean repairable,
        boolean customTextureModeAllowed
) {}
```

- [ ] **Step 4: Load the catalog in Artifacts and reject missing/duplicate ids at startup**

```java
private final Map<String, DonationCatalogItem> donationCatalogById = new ConcurrentHashMap<>();
private int donationCatalogVersion = 0;

private void loadDonationCatalog() {
    donationCatalogById.clear();
    ConfigurationSection root = getConfig().getConfigurationSection("donation-catalog");
    if (root == null) {
        throw new IllegalStateException("donation-catalog section is required.");
    }
    donationCatalogVersion = Math.max(1, root.getInt("catalog-version", 1));
    for (Map<?, ?> raw : root.getMapList("items")) {
        String itemId = String.valueOf(raw.get("item-id")).toLowerCase(Locale.ROOT);
        if (itemId.isBlank() || donationCatalogById.containsKey(itemId)) {
            throw new IllegalStateException("Duplicate or blank donation item id: " + itemId);
        }
        donationCatalogById.put(itemId, parseDonationCatalogItem(raw));
    }
}
```

- [ ] **Step 5: Re-run the donation catalog validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopSelectedItemsExist.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopExactNames.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDonationOnlyNoAr.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add copimine-artifacts/items.yml copimine-artifacts/config.yml copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java tests/ValidateCopiMineDonationShopSelectedItemsExist.ps1 tests/ValidateCopiMineDonationShopExactNames.ps1 tests/ValidateCopiMineDonationShopDonationOnlyNoAr.ps1
git commit -m "feat: add shared donation catalog contract"
```

### Task 2: Extend EconomyCore with Fixed Donation Packs and Mock SBP Sessions

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationBalanceSeparateFromAr.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPacksFixed.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationOneRubOneUnit.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpProviderInterface.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpSessionsTable.ps1`

- [ ] **Step 1: Write failing validators for fixed pack allowlist, separate donation balance, and mock provider contract**

```powershell
$code = Get-Content $EconomyCore -Raw
if ($code -notmatch 'Set\.of\(50L,\s*100L,\s*250L,\s*500L,\s*1000L\)') {
  throw "Missing fixed donation pack allowlist."
}
if ($code -notmatch 'interface DonationPaymentProvider') {
  throw "DonationPaymentProvider interface is required."
}
if ($code -match 'AR.*donation|donation.*AR') {
  throw "Donation and AR must remain separate in balance logic."
}
```

- [ ] **Step 2: Run the validators to verify current code is incomplete**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPacksFixed.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpProviderInterface.ps1
```

Expected: FAIL until the fixed pack and provider/session logic are implemented.

- [ ] **Step 3: Extend the donation schema with richer session fields and idempotency**

```java
update(connection, """
CREATE TABLE IF NOT EXISTS donation_payment_sessions(
    id TEXT PRIMARY KEY,
    player_uuid TEXT NOT NULL DEFAULT '',
    provider TEXT NOT NULL DEFAULT 'MOCK_SBP',
    amount_rub BIGINT NOT NULL DEFAULT 0,
    donation_units BIGINT NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'RUB',
    status TEXT NOT NULL DEFAULT 'CREATED',
    qr_payload TEXT NOT NULL DEFAULT '',
    qr_image_path TEXT NOT NULL DEFAULT '',
    idempotency_key TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL DEFAULT 0,
    paid_at BIGINT NOT NULL DEFAULT 0,
    cancelled_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
)""");
update(connection, "CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_sessions_idempotency ON donation_payment_sessions(idempotency_key) WHERE idempotency_key<>''");
```

- [ ] **Step 4: Add fixed-pack allowlist and mock provider service**

```java
private static final Set<Long> DONATION_PACKS = Set.of(50L, 100L, 250L, 500L, 1000L);

public interface DonationPaymentProvider {
    Map<String, Object> createSession(UUID playerUuid, String playerName, long amountRub, String idempotencyKey) throws Exception;
    Map<String, Object> markPaid(String sessionId, String actor) throws Exception;
    Map<String, Object> cancel(String sessionId, String actor) throws Exception;
}

private final class MockSbpDonationPaymentProvider implements DonationPaymentProvider {
    @Override
    public Map<String, Object> createSession(UUID playerUuid, String playerName, long amountRub, String idempotencyKey) throws Exception {
        if (!DONATION_PACKS.contains(amountRub)) {
            throw new IllegalArgumentException("Unsupported donation pack: " + amountRub);
        }
        long units = amountRub;
        String sessionId = "donation-session-" + UUID.randomUUID();
        String qrPayload = "SBP|MOCK|" + amountRub + "|" + playerUuid;
        return tx(connection -> {
            update(connection, "INSERT INTO donation_payment_sessions(id,player_uuid,provider,amount_rub,donation_units,currency,status,qr_payload,idempotency_key,created_at,expires_at,updated_at) VALUES(?,?,?,?,?,'RUB','PENDING',?,?, ?, ?, ?)",
                    sessionId, playerUuid.toString(), "MOCK_SBP", amountRub, units, qrPayload, idempotencyKey, now(), now() + 15 * 60_000L, now());
            return Map.of("sessionId", sessionId, "amountRub", amountRub, "donationUnits", units, "status", "PENDING", "qrPayload", qrPayload);
        });
    }
}
```

- [ ] **Step 5: Add idempotent mark-paid and donation balance ledger credit**

```java
public Map<String, Object> markPaid(String sessionId, String actor) throws Exception {
    return tx(connection -> {
        List<Map<String, Object>> rows = queryList(connection, "SELECT player_uuid,amount_rub,donation_units,status,idempotency_key FROM donation_payment_sessions WHERE id=? FOR UPDATE", sessionId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unknown session: " + sessionId);
        Map<String, Object> row = rows.getFirst();
        if ("PAID".equalsIgnoreCase(string(row.get("status")))) {
            return Map.of("sessionId", sessionId, "status", "PAID", "idempotent", true);
        }
        String playerUuid = string(row.get("player_uuid"));
        long units = longValue(row.get("donation_units"));
        String ledgerKey = "donation-session-paid-" + sessionId;
        DonationBalanceServiceImpl.this.add(UUID.fromString(playerUuid), "", units, "SBP_MOCK_TOPUP", actor, "mock_sbp", ledgerKey);
        update(connection, "UPDATE donation_payment_sessions SET status='PAID',paid_at=?,updated_at=? WHERE id=?", now(), now(), sessionId);
        return Map.of("sessionId", sessionId, "status", "PAID", "creditedUnits", units, "idempotent", false);
    });
}
```

- [ ] **Step 6: Re-run the economy donation validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationBalanceSeparateFromAr.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPacksFixed.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationOneRubOneUnit.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpProviderInterface.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineMockSbpSessionsTable.ps1
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java tests/ValidateCopiMineDonationBalanceSeparateFromAr.ps1 tests/ValidateCopiMineDonationPacksFixed.ps1 tests/ValidateCopiMineDonationOneRubOneUnit.ps1 tests/ValidateCopiMineMockSbpProviderInterface.ps1 tests/ValidateCopiMineMockSbpSessionsTable.ps1
git commit -m "feat: add mock sbp donation session foundation"
```

### Task 3: Add Purchase Intent, Claim States, and Admin Donation Actions

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPurchaseSpendsBalance.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminTopUp.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminMarkPaid.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationNoDuplicateClaims.ps1`

- [ ] **Step 1: Write failing validators for purchase-intent, fixed admin top-up, and claim lifecycle**

```powershell
$code = Get-Content $EconomyCore -Raw
if ($code -notmatch 'purchaseIntentAsync') { throw "Missing purchase-intent service." }
if ($code -notmatch "CLAIM_PENDING") { throw "Missing purchase CLAIM_PENDING status." }
if ($code -notmatch "UNCLAIMED'.*RESERVED'.*DELIVERING'.*CLAIMED" ) { throw "Claim lifecycle not visible in code." }
```

- [ ] **Step 2: Run validators to confirm the purchase flow still fails**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPurchaseSpendsBalance.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminTopUp.ps1
```

Expected: FAIL until purchase-intent and fixed admin flows are implemented.

- [ ] **Step 3: Implement a purchase-intent service backed by catalog allowlist and donation ledger**

```java
public CompletableFuture<Map<String, Object>> purchaseIntentAsync(UUID playerUuid, String playerName, String itemId, long priceDonation, String actor) {
    return dbFuture("donation purchase intent", () -> tx(connection -> {
        String normalized = first(itemId, "").toLowerCase(Locale.ROOT);
        if (!validateDonationItemId(normalized)) {
            throw new IllegalArgumentException("Unknown donation item: " + normalized);
        }
        DonationBalanceServiceImpl.this.subtract(playerUuid, playerName, priceDonation, "DONATION_PURCHASE", actor, "donation_shop", "purchase-intent-" + playerUuid + "-" + normalized + "-" + now());
        String purchaseId = "donation-purchase-" + UUID.randomUUID();
        String claimId = "donation-claim-" + UUID.randomUUID();
        update(connection, "INSERT INTO donation_purchases(id,player_uuid,item_id,price,status,created_at,updated_at) VALUES(?,?,?,?, 'CLAIM_PENDING', ?, ?)",
                purchaseId, playerUuid.toString(), normalized, priceDonation, now(), now());
        update(connection, "INSERT INTO donation_item_claims(id,player_uuid,item_id,amount,status,claimed_at,created_at,updated_at,purchase_id,actor) VALUES(?,?,?,?, 'UNCLAIMED', 0, ?, ?, ?, ?)",
                claimId, playerUuid.toString(), normalized, 1L, now(), now(), purchaseId, actor);
        return Map.of("purchaseId", purchaseId, "claimId", claimId, "itemId", normalized, "status", "CLAIM_PENDING");
    }));
}
```

- [ ] **Step 4: Restrict admin donation balance changes to the fixed top-up buttons**

```java
public TxnResult addFixedDonationPack(UUID playerUuid, String playerName, long amount, String actor, String reason) throws Exception {
    if (!DONATION_PACKS.contains(amount)) {
        return new TxnResult(false, "INVALID_PACK", "Разрешены только пакеты 50 / 100 / 250 / 500 / 1000.", 0L, "");
    }
    return donationBalanceService.addAsync(playerUuid, playerName, amount, reason, actor, "admin_pack", "admin-pack-" + playerUuid + "-" + amount + "-" + now()).join();
}
```

- [ ] **Step 5: Re-run the purchase and admin validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationPurchaseSpendsBalance.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminTopUp.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAdminMarkPaid.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationNoDuplicateClaims.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java tests/ValidateCopiMineDonationPurchaseSpendsBalance.ps1 tests/ValidateCopiMineDonationAdminTopUp.ps1 tests/ValidateCopiMineDonationAdminMarkPaid.ps1 tests/ValidateCopiMineDonationNoDuplicateClaims.ps1
git commit -m "feat: add donation purchase intent and fixed admin actions"
```

### Task 4: Implement Owner-Bound PDC, Claim Delivery, and Lost-Item Reclaim

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimCreatesOwnerBoundItem.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimedItemsNotSuspicious.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopOwnerBoundPdc.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDuplicateOwnedItems.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDeathReclaimFlow.ps1`

- [ ] **Step 1: Write failing validators for owner-bound PDC, active-instance uniqueness, and reclaim lifecycle**

```powershell
$code = Get-Content $Artifacts -Raw
if ($code -notmatch 'DONATION_SHOP_ITEM') { throw "Missing donation item PDC type." }
if ($code -notmatch 'LOST_RECLAIMABLE') { throw "Missing lost reclaimable state." }
if ($code -notmatch 'REPLACED_AFTER_LOSS') { throw "Missing replaced-after-loss state." }
```

- [ ] **Step 2: Run validators to capture the current delivery gap**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimCreatesOwnerBoundItem.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDeathReclaimFlow.ps1
```

Expected: FAIL until PDC and reclaim behavior are implemented.

- [ ] **Step 3: Add the official donation item factory and instance status transitions**

```java
private ItemStack createOfficialDonationItem(DonationCatalogItem item, UUID ownerUuid, String ownerName, String purchaseId, String uniqueItemId) {
    ItemStack stack = new ItemStack(item.baseMaterial());
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(color("&f" + item.displayName()));
    meta.lore(List.of(
            color("&7Предмет лавки CopiMine"),
            color("&7Привязан к владельцу: &f" + ownerName),
            color("&7Не выпадает и не передаётся"),
            color("&7После внешней потери можно вернуть через лавку")
    ));
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(keyCopimineItemType, PersistentDataType.STRING, "DONATION_SHOP_ITEM");
    pdc.set(keyCatalogItemId, PersistentDataType.STRING, item.itemId());
    pdc.set(keyUniqueItemId, PersistentDataType.STRING, uniqueItemId);
    pdc.set(keyOwnerUuid, PersistentDataType.STRING, ownerUuid.toString());
    pdc.set(keyOwnerName, PersistentDataType.STRING, ownerName);
    pdc.set(keyPurchaseId, PersistentDataType.STRING, purchaseId);
    stack.setItemMeta(meta);
    return stack;
}
```

- [ ] **Step 4: Implement claim delivery and reclaim with row-lock/atomic instance checks**

```java
private void deliverDonationClaim(Player player, DonationClaimRow row, DonationCatalogItem item) throws Exception {
    UUID playerUuid = player.getUniqueId();
    if (hasActiveDonationInstance(playerUuid, item.itemId())) {
        throw new IllegalStateException("У тебя уже есть активный экземпляр этого предмета.");
    }
    requireFreeSlot(player, item.displayName());
    String uniqueItemId = "donation-item-" + UUID.randomUUID();
    ItemStack stack = createOfficialDonationItem(item, playerUuid, player.getName(), row.purchaseId(), uniqueItemId);
    markDonationClaimDeliveringAsync(playerUuid, row.claimId()).join();
    persistDonationInstance(uniqueItemId, item.itemId(), playerUuid, row.purchaseId(), "ACTIVE");
    player.getInventory().addItem(stack);
    completeDonationClaimAsync(playerUuid, row.claimId()).join();
}

private void reclaimLostDonationInstance(Player player, ArtifactInstanceRow lost) throws Exception {
    if (!"LOST_RECLAIMABLE".equalsIgnoreCase(lost.status())) throw new IllegalStateException("Предмет нельзя вернуть.");
    if (hasActiveDonationInstance(player.getUniqueId(), lost.itemId())) throw new IllegalStateException("У тебя уже есть активный экземпляр.");
    requireFreeSlot(player, lost.itemId());
    replaceLostInstanceAndIssueNewOne(player, lost);
}
```

- [ ] **Step 5: Re-run the claim/reclaim validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimCreatesOwnerBoundItem.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationClaimedItemsNotSuspicious.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopOwnerBoundPdc.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDuplicateOwnedItems.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopDeathReclaimFlow.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java tests/ValidateCopiMineDonationClaimCreatesOwnerBoundItem.ps1 tests/ValidateCopiMineDonationClaimedItemsNotSuspicious.ps1 tests/ValidateCopiMineDonationShopOwnerBoundPdc.ps1 tests/ValidateCopiMineDonationShopNoDuplicateOwnedItems.ps1 tests/ValidateCopiMineDonationShopDeathReclaimFlow.ps1
git commit -m "feat: add owner-bound donation claim and reclaim flow"
```

### Task 5: Add Inventory Guards and Donation Effect Profiles

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDropNoStoreNoTransfer.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCooldowns.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopControlledLightningNoFireGrief.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryServiceSafeRollback.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryNoProtectedBlocks.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopVampirismCapped.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopAmuletDoesNotClearNarcoticsOverdose.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCompassOwnerOnlyDeathLocation.ps1`

- [ ] **Step 1: Write failing validators for drop/store/transfer guards and key effect safety**

```powershell
$code = Get-Content $Artifacts -Raw
foreach ($needle in @('PlayerDropItemEvent','InventoryClickEvent','InventoryDragEvent','InventoryMoveItemEvent','InventoryPickupItemEvent')) {
  if ($code -notmatch $needle) { throw "Missing guard for $needle" }
}
if ($code -notmatch 'strikeLightningEffect') { throw "Controlled lightning effect not found." }
if ($code -notmatch 'LOST_RECLAIMABLE') { throw "Loss handling not wired into item lifecycle." }
```

- [ ] **Step 2: Run validators to see the missing safety coverage**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDropNoStoreNoTransfer.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryServiceSafeRollback.ps1
```

Expected: FAIL until guards and effect handlers are added.

- [ ] **Step 3: Implement inventory and transfer guards for donation items**

```java
private boolean isOfficialDonationItem(@Nullable ItemStack stack) {
    if (stack == null || stack.getType().isAir()) return false;
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return false;
    return "DONATION_SHOP_ITEM".equals(meta.getPersistentDataContainer().get(keyCopimineItemType, PersistentDataType.STRING));
}

@EventHandler(ignoreCancelled = true)
public void onDonationDrop(PlayerDropItemEvent event) {
    if (isOfficialDonationItem(event.getItemDrop().getItemStack())) {
        event.setCancelled(true);
    }
}
```

- [ ] **Step 4: Implement safe effect dispatch with cooldowns and protection checks**

```java
private boolean canRunDonationCombatEffect(Player owner, LivingEntity target, String effectId, int cooldownSeconds) {
    if (!(target instanceof Player victim)) return false;
    if (!isPvpAllowed(owner.getLocation()) || !isPvpAllowed(victim.getLocation())) return false;
    if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) return false;
    if (isProtectedZone(owner.getLocation()) || isProtectedZone(victim.getLocation())) return false;
    return donationCooldownReady(owner.getUniqueId(), effectId, cooldownSeconds);
}

private void runControlledLightning(LivingEntity target) {
    World world = target.getWorld();
    world.strikeLightningEffect(target.getLocation());
}
```

- [ ] **Step 5: Re-run the donation guard and effect validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopNoDropNoStoreNoTransfer.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCooldowns.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopControlledLightningNoFireGrief.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryServiceSafeRollback.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopBuryNoProtectedBlocks.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopVampirismCapped.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopAmuletDoesNotClearNarcoticsOverdose.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopCompassOwnerOnlyDeathLocation.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java tests/ValidateCopiMineDonationShopNoDropNoStoreNoTransfer.ps1 tests/ValidateCopiMineDonationShopCooldowns.ps1 tests/ValidateCopiMineDonationShopControlledLightningNoFireGrief.ps1 tests/ValidateCopiMineDonationShopBuryServiceSafeRollback.ps1 tests/ValidateCopiMineDonationShopBuryNoProtectedBlocks.ps1 tests/ValidateCopiMineDonationShopVampirismCapped.ps1 tests/ValidateCopiMineDonationShopAmuletDoesNotClearNarcoticsOverdose.ps1 tests/ValidateCopiMineDonationShopCompassOwnerOnlyDeathLocation.ps1
git commit -m "feat: add donation item guards and effect handlers"
```

### Task 6: Build In-Game Donation GUI, QR Session Start, Claim, and Reclaim Screens

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineGameSbpQrFallback.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopPrivatePurchaseLinks.ps1`

- [ ] **Step 1: Write failing validators for the five-button donation GUI and QR fallback**

```powershell
$code = Get-Content $Artifacts -Raw
foreach ($label in @('Каталог','Донат-баланс','Пополнить','Мои покупки','Вернуть утерянные предметы')) {
  if ($code -notmatch [regex]::Escape($label)) { throw "Missing donation GUI label: $label" }
}
if ($code -notmatch 'MapQrRenderer' -or $code -notmatch 'session code') {
  throw "Missing QR renderer or fallback session-code path."
}
```

- [ ] **Step 2: Run validators to verify the current GUI is not yet aligned**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineGameSbpQrFallback.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopPrivatePurchaseLinks.ps1
```

Expected: FAIL.

- [ ] **Step 3: Add the root donation GUI sections and private website-link flow**

```java
private void openDonationRoot(Player player) {
    Inventory inv = Bukkit.createInventory(menuHolder("DONATION_ROOT"), 27, color("&2Донатная лавка"));
    inv.setItem(10, button(Material.BOOK, "&aКаталог", List.of("&7Все донат-предметы и статусы.")));
    inv.setItem(11, button(Material.EMERALD, "&aДонат-баланс", List.of("&71 ₽ = 1 Donation", "&7AR не связан с Donation.")));
    inv.setItem(12, button(Material.MAP, "&aПополнить", List.of("&7Пакеты: 50 / 100 / 250 / 500 / 1000")));
    inv.setItem(14, button(Material.CHEST, "&aМои покупки", List.of("&7Получить купленные предметы.")));
    inv.setItem(15, button(Material.RECOVERY_COMPASS, "&aВернуть утерянные предметы", List.of("&7Только LOST_RECLAIMABLE.")));
    player.openInventory(inv);
}

private void sendPrivateDonationWebsiteLink(Player player, DonationCatalogItem item) {
    String encoded = URLEncoder.encode(item.itemId(), StandardCharsets.UTF_8);
    player.sendMessage(color("&7Открой страницу покупки: &f" + websiteBaseUrl() + "/" + encoded));
}
```

- [ ] **Step 4: Add payment-session creation and QR fallback integration**

```java
private void openDonationTopUpMenu(Player player) {
    Inventory inv = Bukkit.createInventory(menuHolder("DONATION_TOPUP"), 27, color("&2Пополнить Donation"));
    for (long pack : List.of(50L, 100L, 250L, 500L, 1000L)) {
        inv.addItem(button(Material.SUNFLOWER, "&aПакет " + pack, List.of("&71 ₽ = 1 Donation", "&7Создать mock SBP session")));
    }
    player.openInventory(inv);
}

private void showDonationSession(Player player, Map<String, Object> session) {
    if (renderQrMapIfAvailable(player, session)) {
        return;
    }
    player.sendMessage(color("&7Сессия оплаты: &f" + session.get("sessionId")));
    player.sendMessage(color("&7Открой страницу: &f" + donationSessionUrl(String.valueOf(session.get("sessionId")))));
}
```

- [ ] **Step 5: Re-run the GUI and QR fallback validators**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineGameSbpQrFallback.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopPrivatePurchaseLinks.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java tests/ValidateCopiMineGameSbpQrFallback.ps1 tests/ValidateCopiMineDonationShopPrivatePurchaseLinks.ps1
git commit -m "feat: add donation shop gui and qr fallback"
```

### Task 7: Add Backend QR Generation and Player/Admin Donation APIs

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\requirements.txt`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrGeneration.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrNoExternalApi.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationProviderNoSecretsInUi.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationCsrfAndRateLimit.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAuditLog.ps1`

- [ ] **Step 1: Write failing validators for local QR generation, admin mark-paid, CSRF, and no-secret UI**

```powershell
$code = Get-Content $MainPy -Raw
if ($code -notmatch 'import qrcode') { throw "main.py must generate QR locally." }
if ($code -match 'api.qrserver.com|googleapis|chart.googleapis') { throw "External QR API is forbidden." }
if ($code -notmatch '/api/player/donation/sbp/session') { throw "Missing player SBP session API." }
if ($code -notmatch '/api/admin/donation/sbp/session/.*/mark-paid') { throw "Missing admin mark-paid endpoint." }
```

- [ ] **Step 2: Run the web validators to confirm the current donation API is incomplete**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrGeneration.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrNoExternalApi.ps1
```

Expected: FAIL.

- [ ] **Step 3: Add QR dependency and backend QR generation**

```text
qrcode[pil]==7.4.2
Pillow==11.1.0
```

```python
import io
import qrcode
from fastapi.responses import Response

def build_donation_qr_png(payload: str) -> bytes:
    qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(payload)
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white")
    buf = io.BytesIO()
    image.save(buf, format="PNG")
    return buf.getvalue()
```

- [ ] **Step 4: Add player session, purchase-intent, and admin mark-paid endpoints with audit**

```python
@app.get("/api/player/donation/packs")
async def player_donation_packs(_: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    return {"packs": [50, 100, 250, 500, 1000], "currency": "RUB", "unitsPerRub": 1}

@app.post("/api/player/donation/sbp/session")
async def player_create_donation_session(data: PlayerDonationSessionIn, request: Request, account: dict[str, Any] = Depends(require_player)) -> dict[str, Any]:
    require_csrf(request)
    enforce_rate_limit(request, f"donation-session:{account['username']}", 10, 300)
    if data.amount not in {50, 100, 250, 500, 1000}:
        raise HTTPException(status_code=400, detail="Разрешены только пакеты 50 / 100 / 250 / 500 / 1000.")
    result = await bg(create_mock_sbp_session_sync, account["minecraft_uuid"], account["minecraft_name"], data.amount)
    audit_event(account["username"], "donation.session.create", details={"amount": data.amount, "sessionId": result["sessionId"]})
    return result
```

- [ ] **Step 5: Re-run backend checks and validators**

Run:

```powershell
python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrGeneration.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrNoExternalApi.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationProviderNoSecretsInUi.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationCsrfAndRateLimit.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationAuditLog.ps1
```

Expected: `py_compile` PASS and validators PASS.

- [ ] **Step 6: Commit**

```bash
git add admin-web/backend/main.py admin-web/requirements.txt tests/ValidateCopiMineWebSbpQrGeneration.ps1 tests/ValidateCopiMineWebSbpQrNoExternalApi.ps1 tests/ValidateCopiMineDonationProviderNoSecretsInUi.ps1 tests/ValidateCopiMineDonationCsrfAndRateLimit.ps1 tests/ValidateCopiMineDonationAuditLog.ps1
git commit -m "feat: add mock sbp api and qr generation"
```

### Task 8: Implement Frontend Player Cabinet and Admin Donation Screens

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\style.css`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopWebsiteApi.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoBrokenRussianEncoding.ps1`

- [ ] **Step 1: Write failing validators for player donation pages and admin donation controls**

```powershell
$js = Get-Content $AppJs -Raw
foreach ($label in @('Донат-баланс','Донатная лавка','Мои донат-предметы','История','Mock SBP Sessions','Provider Settings')) {
  if ($js -notmatch [regex]::Escape($label)) { throw "Missing donation UI label: $label" }
}
```

- [ ] **Step 2: Run the frontend validators to confirm the current UI is incomplete**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopWebsiteApi.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoBrokenRussianEncoding.ps1
```

Expected: FAIL or partial PASS with missing donation screens.

- [ ] **Step 3: Add the player donation cabinet screens in `app.js`**

```javascript
async function renderDonationBalanceView() {
  const [balance, history, packs] = await Promise.all([
    safeApi("/api/player/donation/balance", { linked: false, balance: 0 }),
    safeApi("/api/player/donation/history", { history: [] }),
    safeApi("/api/player/donation/packs", { packs: [50, 100, 250, 500, 1000] })
  ]);
  return section("Донат-баланс", [
    metric("Баланс", formatDonate(balance.balance || 0), "Donation units", "good"),
    panel("Пакеты", "1 ₽ = 1 единица донат-баланса", renderPackButtons(packs.packs || [])),
    panel("История", "Пополнения и списания donation balance.", table("donation-history", history.history || [], [
      ["Дата", (row) => formatDate(row.created_at)],
      ["Изменение", (row) => formatDonate(row.delta)],
      ["Причина", "reason"]
    ]))
  ]);
}
```

- [ ] **Step 4: Add admin donation panels and QR session status controls**

```javascript
async function renderAdminDonationView() {
  const overview = await safeApi("/api/admin/donation/overview?limit=120", { summary: {}, sessions: [], claims: [], balances: [], ledger: [] });
  return section("Donation Control", [
    panel("Donation Balance", "Фиксированные пакеты +50 / +100 / +250 / +500 / +1000.", renderAdminBalanceControls()),
    panel("Mock SBP Sessions", "Управление mock-сессиями оплаты.", renderSessionTable(overview.sessions || [])),
    panel("Donation Shop", "Каталог, test purchase, статусы.", renderDonationCatalogAdmin()),
    panel("Claims", "Pending, delivery review, reclaimable.", renderClaimTable(overview.claims || [])),
    panel("Provider Settings", "Активный provider: MOCK_SBP. Секреты не показываются.", renderProviderInfo())
  ]);
}
```

- [ ] **Step 5: Run frontend syntax check and validators**

Run:

```powershell
node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationShopWebsiteApi.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoBrokenRussianEncoding.ps1
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add admin-web/frontend/index.html admin-web/frontend/assets/app.js admin-web/frontend/assets/style.css tests/ValidateCopiMineDonationShopWebsiteApi.ps1
git commit -m "feat: add donation player and admin web ui"
```

### Task 9: Add Manual Smoke, Build Scripts, and Release Validators

**Files:**
- Create: `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_DONATION_SBP_FOUNDATION_SMOKE.md`
- Modify: `D:\Desktop\Copimine\opt\copimine\tests\` (new validator scripts listed above)

- [ ] **Step 1: Write the donation smoke checklist**

```markdown
# CopiMine Donation SBP Foundation Smoke

## Website
- Player sees donation balance.
- Player sees 50 / 100 / 250 / 500 / 1000 packs.
- Player creates mock SBP session.
- QR is shown from local backend generation.
- Admin marks session paid.
- Balance increases exactly once.
- Player buys donation item on the website.
- UI says "Заберите предмет в игре".

## Game
- Player opens donation shop root with five buttons.
- QR map render or fallback link/session code appears.
- Claim succeeds only with free slot.
- Reclaim succeeds only for LOST_RECLAIMABLE items.
- Broken/consumed items are not reclaimable.
```

- [ ] **Step 2: Build the changed plugins and run syntax checks**

Run:

```powershell
& D:\Desktop\Copimine\opt\copimine\copimine-economy-core\build-plugin.ps1
& D:\Desktop\Copimine\opt\copimine\copimine-artifacts\build-plugin.ps1
python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py
node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js
```

Expected: both jars rebuilt and copied to `minecraft\server\plugins`, Python check PASS, Node check PASS.

- [ ] **Step 3: Run all donation validators as a batch**

Run:

```powershell
Get-ChildItem D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonation*.ps1 | ForEach-Object { pwsh -File $_.FullName }
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrGeneration.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebSbpQrNoExternalApi.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineGameSbpQrFallback.ps1
```

Expected: PASS for the entire donation batch.

- [ ] **Step 4: Commit**

```bash
git add tests/manual/COPIMINE_DONATION_SBP_FOUNDATION_SMOKE.md tests/*.ps1
git commit -m "test: add donation smoke checklist and validators"
```

### Task 10: Code Review, Security Hardening, and Final Verification

**Files:**
- Review: `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
- Review: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Review: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Review: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`
- Review: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineArtifactsNoMainThreadDb.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebCsrfProtection.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoUnsafeInlineCsp.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoLocalStorageAuthToken.ps1`

- [ ] **Step 1: Run a focused code review against the finished donation changes**

Checklist:

```text
- No donation balance mutation outside EconomyCore.
- No physical donation issuance outside Artifacts.
- No main-thread DB in PlayerInteract/InventoryClick handlers.
- No duplicate ACTIVE instances.
- No claim auto-repeat from DELIVERY_REVIEW.
- No QR payload or secrets in audit/UI.
- No AR wording in donation purchase flow.
- No fake "payment succeeded" before PAID.
```

- [ ] **Step 2: Strengthen security and fix any findings inline**

```python
def require_fixed_donation_pack(amount: int) -> int:
    if amount not in {50, 100, 250, 500, 1000}:
        raise HTTPException(status_code=400, detail="Разрешены только фиксированные пакеты 50 / 100 / 250 / 500 / 1000.")
    return amount
```

```java
if (Bukkit.isPrimaryThread()) {
    throw new IllegalStateException("Donation DB transition must not block the main thread.");
}
```

- [ ] **Step 3: Re-run core security validators and full donation batch**

Run:

```powershell
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineArtifactsNoMainThreadDb.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebCsrfProtection.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoUnsafeInlineCsp.ps1
pwsh -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoLocalStorageAuthToken.ps1
Get-ChildItem D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonation*.ps1 | ForEach-Object { pwsh -File $_.FullName }
```

Expected: PASS.

- [ ] **Step 4: Final build and artifact verification**

Run:

```powershell
& D:\Desktop\Copimine\opt\copimine\copimine-economy-core\build-plugin.ps1
& D:\Desktop\Copimine\opt\copimine\copimine-artifacts\build-plugin.ps1
Get-Item D:\Desktop\Copimine\opt\copimine\copimine-economy-core\CopiMineEconomyCore.jar | Select-Object Name,Length,LastWriteTime
Get-Item D:\Desktop\Copimine\opt\copimine\copimine-artifacts\CopiMineArtifacts.jar | Select-Object Name,Length,LastWriteTime
Get-Item D:\Desktop\Copimine\opt\copimine\minecraft\server\plugins\CopiMineEconomyCore.jar | Select-Object Name,Length,LastWriteTime
Get-Item D:\Desktop\Copimine\opt\copimine\minecraft\server\plugins\CopiMineArtifacts.jar | Select-Object Name,Length,LastWriteTime
```

Expected: local jars and copied server jars exist with fresh timestamps and non-zero size.

- [ ] **Step 5: Commit**

```bash
git add copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java admin-web/backend/main.py admin-web/frontend/assets/app.js admin-web/frontend/index.html admin-web/frontend/assets/style.css tests/*.ps1 tests/manual/COPIMINE_DONATION_SBP_FOUNDATION_SMOKE.md
git commit -m "chore: harden donation shop release flow"
```

## Self-Review

### Spec coverage

- Donation currency separation is covered in Tasks 1 through 3.
- Mock SBP foundation and QR are covered in Tasks 2, 6, 7, and 8.
- Website-only purchase is covered in Tasks 3, 7, and 8.
- In-game claim and reclaim are covered in Tasks 4 and 6.
- Owner-bound item lifecycle and anti-dupe are covered in Tasks 4 and 5.
- Admin top-up, mark-paid, and audit are covered in Tasks 2, 3, 7, and 10.
- Security hardening and code review are covered explicitly in Task 10.

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Every task lists exact file paths and explicit commands.
- Every code-writing step includes a concrete code sketch or SQL statement.

### Type consistency

- `DonationCatalogItem`, `DonationPaymentProvider`, `purchaseIntentAsync`, claim statuses, and instance statuses are named consistently across tasks.
- `CLAIM_PENDING` is used only for purchases, while `UNCLAIMED -> RESERVED -> DELIVERING -> CLAIMED` remains the claim lifecycle.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-25-copimine-donation-shop-plan.md`.

Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints

Because you already asked to start implementation immediately, default to **Inline Execution** unless you explicitly want the subagent path instead.
