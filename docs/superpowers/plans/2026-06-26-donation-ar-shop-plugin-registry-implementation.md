# Donation / AR Shop / Plugin Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Довести donation foundation, AR shop foundation и backend plugin registry до рабочего и безопасного состояния без большого frontend redesign в этом проходе.

**Architecture:** `CopiMineEconomyCore` остается владельцем donation money-flow, payment sessions, purchases и claims. `CopiMineArtifacts` остается владельцем physical item-flow, `artifact_item_instances`, owner-bound PDC, claim/reclaim и anti-dupe. `admin-web` получает только минимальный рабочий commerce/backend layer и безопасный allowlisted plugin registry.

**Tech Stack:** Java/Paper plugins, PostgreSQL, Python/FastAPI backend, vanilla JS frontend, PowerShell validators, GitHub workflow, codex-security review gate.

---

## 1. File / Table Map

### Existing files to modify

- `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
  - donation accounts, ledger, sessions, purchases, claims, idempotency, admin top-up, mock SBP, service API for Artifacts
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
  - physical donation item issuance, reclaim, anti-dupe, AR catalog runtime, AR repair restrictions, in-game shop GUI
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml`
  - canonical AR catalog and donation catalog source
- `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
  - minimal player/admin donation endpoints, purchase-intent, admin top-up, mock SBP control, plugin registry route wiring
- `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`
  - minimal player/admin commerce flows only, without redesign
- `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_DONATION_SHOP_SMOKE_CHECKLIST.md`
  - manual smoke for donation top-up, purchase, claim, reclaim, AR separation, plugin registry

### New files to add or finalize

- `D:\Desktop\Copimine\opt\copimine\admin-web\backend\commerce_catalog.py`
  - shared loader for donation and AR catalog data from `items.yml`
- `D:\Desktop\Copimine\opt\copimine\admin-web\backend\plugin_registry.py`
  - manifest loader, allowlist checks, schema validation, backup/apply/reload helpers, audit helpers
- `D:\Desktop\Copimine\opt\copimine\admin-web\backend\plugin_registry_manifest.json`
  - allowlisted plugin ids, config paths, editable keys, schema
- validators in `D:\Desktop\Copimine\opt\copimine\tests\Validate*.ps1`
  - donation, AR separation, plugin registry safety, idempotency, claim lifecycle

### Tables to reuse and harden

- `donation_accounts`
- `donation_balance_ledger`
- `donation_payment_sessions`
- `donation_purchases`
- `donation_item_claims`
- `artifact_item_instances`

### Schema/index changes expected inside existing runtime `ensureSchema`

- missing uniqueness/indexes for donation idempotency
- uniqueness or guarded lookup for one active entitlement per player/item
- fast lookup by `(player_uuid, status)` for claims
- reclaim lookup by `(owner_uuid, status, item_id)`

---

## 2. Delivery Order

1. Clean the current foundation state and fix obvious broken plan/code assumptions already found in `EconomyCore`, `Artifacts`, and `admin-web`.
2. Lock the donation catalog contract and AR catalog contract as shared source-of-truth data.
3. Harden donation DB schema/indexes and replay rules in `EconomyCore`.
4. Finish fixed-pack payment session creation and session status handling.
5. Finish idempotent `mark-paid`, cancellation, expiration, and admin top-up.
6. Finish purchase-intent so it is fully backend-priced and transaction-safe.
7. Finish claim state transitions in `EconomyCore`.
8. Finish physical claim delivery, cache synchronization, and anti-dupe in `Artifacts`.
9. Finish reclaim and loss-recovery flow in `Artifacts`.
10. Finish AR shop separation, AR-only repair path, and repair bypass blocking.
11. Finish minimal player web commerce flow.
12. Finish minimal admin commerce flow.
13. Finish plugin registry backend foundation and live-config safety.
14. Add and run validators plus manual smoke docs.
15. Run diff-scoped `codex-security` review and fix validated findings.
16. Run full-project independent review with `codex-security`.
17. Fix validated full-project findings that block release-readiness.
18. Only then perform the explicit Git/GitHub handoff sequence.

### Expanded implementation phases

#### Phase A: foundation cleanup before feature work

- remove or fix known mojibake and broken messages in touched commerce files
- reconcile partially implemented helpers already present in:
  - `commerce_catalog.py`
  - `plugin_registry.py`
  - `CopiMineEconomyCore.java`
  - `CopiMineArtifacts.java`
- resolve any manifest/config path mistakes before building new behavior on top

#### Phase B: shared catalog normalization

- make the donation catalog complete enough for backend purchase validation
- make the AR catalog complete enough for in-game AR purchase/repair policy
- ensure both catalogs are loadable by one shared backend helper without mixing currencies

#### Phase C: donation schema/index hardening

- verify runtime `ensureSchema` creates the missing unique indexes
- add or fix indexes for:
  - session idempotency
  - donation ledger idempotency
  - one active entitlement per player/item
  - claim lookup by owner/status
- verify old data survives idempotent re-run of schema code

#### Phase D: payment sessions and top-up

- fixed pack validation
- session creation
- local QR generation
- session status polling
- session cancel / expire behavior
- admin `mark-paid`
- admin top-up
- audit trail

#### Phase E: donation purchase flow

- player purchase-intent endpoint
- backend price resolution
- donation balance debit
- purchase row creation
- claim row creation
- idempotent retry behavior
- player-facing response contract

#### Phase F: claim lifecycle

- reserve flow
- delivering flow
- claimed flow
- delivery review fallback
- no auto-release after physical issuance begins
- no duplicate claim completion

#### Phase G: physical issuance and anti-dupe

- `artifact_item_instances` creation
- `unique_item_id` generation
- `instanceToItem` cache updates
- owner-bound PDC write
- leftovers/full inventory handling
- stale resurfaced item invalidation

#### Phase H: reclaim and loss journal

- external loss detection
- `LOST_RECLAIMABLE` marking
- reclaim screen
- replacement issuance
- old instance invalidation
- broken/consumed non-reclaimable path

#### Phase I: AR shop foundation

- AR purchase runtime
- AR catalog rendering
- AR claim if applicable
- AR-only repair
- block all non-AR repair bypasses
- keep AR completely separate from donation balance

#### Phase J: web player commerce glue

- donation balance page
- payment packs page
- session QR/status page
- donation shop list/detail
- owned/claim-pending rendering
- history rendering
- deep links back to in-game claim

#### Phase K: web admin commerce glue

- donation overview
- sessions list
- mark-paid
- cancel session
- admin top-up
- test purchase if retained
- provider settings surface for `MOCK_SBP`

#### Phase L: plugin registry foundation

- manifest validation
- allowlist enforcement
- schema validation
- backup-before-apply
- audit log
- live config target correctness
- controlled reload

#### Phase M: validation and smoke coverage

- new validators
- strengthened existing validators
- compile/build matrix
- manual smoke checklist refresh

#### Phase N: security gate

- diff-scoped `codex-security` review
- fix pass
- rerun checks

#### Phase O: full release gate

- whole-project `codex-security` review
- full bugfix pass on validated issues
- final green build/validator state
- Git/GitHub handoff only after the explicit review -> fix -> git sequence

---

## 3. Risks That Must Be Closed

### Idempotency risks

- repeated `mark-paid` can double-credit donation balance
- repeated `purchase-intent` can double-spend or create duplicate claims
- repeated claim click can create two physical items
- repeated reclaim can create two `ACTIVE` instances
- admin top-up can be replayed

### Anti-dupe / lifecycle risks

- claim must not auto-release after physical issuance has started
- `DELIVERY_REVIEW` must never silently revert to `UNCLAIMED`
- stale pre-reclaim item must become invalid if it appears again
- one entitlement must not yield two `ACTIVE` items for one owner

### Separation risks

- donation and AR must not share balance, ledger, prices, or catalog ownership
- site can start payment and purchase, but physical claim/reclaim must stay in-game only
- plugin registry must not become a raw config editor or arbitrary file writer

---

## 4. Task Plan

### Task 1: Lock donation and AR catalog contracts

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml`
- Create/Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\commerce_catalog.py`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`

- [ ] Normalize `items.yml` so AR and donation are separate branches with stable ids, prices, and source fields.
- [ ] Ensure every donation entry carries at least `item-id`, `display-name`, `price-donation`, `source=DONATION_SHOP`, `owner-bound=true`, `reclaim-policy=LOSS_ONLY`, `repairable`, `effect-profile-id`.
- [ ] Ensure every AR entry carries `source=AR_SHOP` and AR-only price semantics.
- [ ] Implement `commerce_catalog.py` as the single backend loader for player/admin site flows so the website does not duplicate catalog rules in ad-hoc code.
- [ ] Update `main.py` to read donation catalog and AR catalog through `commerce_catalog.py` instead of scattered constants.
- [ ] Add/refresh validators that fail if one catalog entry mixes donation and AR pricing or if backend ignores the catalog source branch.

### Task 2: Harden donation payment sessions and top-up in EconomyCore

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`

- [ ] Enforce fixed top-up packs only: `50`, `100`, `250`, `500`, `1000`.
- [ ] Make create-session reject any non-allowlisted amount before creating DB rows.
- [ ] Keep `MOCK_SBP` as the active provider, but finish the provider abstraction so later real provider integration plugs into the same session model.
- [ ] Make `mark-paid` idempotent per session and owner so repeating the same success path does not double-credit donation balance.
- [ ] Ensure admin top-up uses an idempotency key and audit trail.
- [ ] Ensure donation balance mutations validate replay context, not just raw idempotency key existence.
- [ ] Ensure session expiration is time-unit correct and expired sessions cannot be marked paid.
- [ ] Build and run donation idempotency/session validators after this task.

### Task 3: Harden purchase-intent and claim creation in EconomyCore

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`

- [ ] Make purchase-intent accept only `item_id`; backend resolves price from the catalog.
- [ ] Atomically debit donation balance, write donation ledger, create purchase, and create claim.
- [ ] Keep purchase statuses canonical: `PAID`, `CLAIM_PENDING`, `CLAIMED`, `DELIVERY_REVIEW`, `CANCELLED`.
- [ ] Keep claim statuses canonical: `UNCLAIMED`, `RESERVED`, `DELIVERING`, `CLAIMED`, `DELIVERY_REVIEW`, `CANCELLED`.
- [ ] Ensure purchase-intent is idempotent so browser retry or double-click does not create duplicate debits.
- [ ] Ensure owner and item validity are checked before purchase row creation.
- [ ] Ensure no internal ids that should stay backend-internal are leaked to ordinary player responses unless intentionally needed for the flow.

### Task 4: Harden physical claim delivery in Artifacts

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`

- [ ] Reserve claim before any issuance attempt.
- [ ] Transition `UNCLAIMED -> RESERVED -> DELIVERING -> CLAIMED` only through atomic backend/service calls.
- [ ] Create `artifact_item_instances` before or during controlled physical delivery with a status model that cannot be replayed into a dupe.
- [ ] Update in-memory `instanceToItem` cache consistently so newly issued official items are not flagged suspicious.
- [ ] If issuance never started, release safely back to `UNCLAIMED`.
- [ ] If issuance may have started but finalization failed, move claim to `DELIVERY_REVIEW`, never auto-release.
- [ ] Enforce “one entitlement, one ACTIVE instance” rule.
- [ ] Make any old item that resurfaces after reclaim go through invalid/suspicious cleanup path.

### Task 5: Add reclaim flow only for external loss

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`

- [ ] Build the in-game screen `Вернуть утерянные предметы`.
- [ ] Show only `LOST_RECLAIMABLE` instances there.
- [ ] Allow reclaim one item at a time, not mass reclaim.
- [ ] On reclaim, convert old instance to `REPLACED_AFTER_LOSS`, generate a new `unique_item_id`, create a new `ACTIVE` instance, then issue the replacement.
- [ ] Block reclaim for `BROKEN` and `CONSUMED`.
- [ ] Ensure reclaim is denied if an `ACTIVE` item for the same entitlement already exists.
- [ ] Write audit events for reclaim issue and stale item invalidation.

### Task 6: Add AR shop foundation and keep AR separate from donation

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml`

- [ ] Finalize AR catalog/runtime branch with `source=AR_SHOP`.
- [ ] Keep AR purchases and AR ownership in Artifacts, separate from donation balance and donation ledger.
- [ ] Ensure AR items are purchasable/claimable through AR flows only.
- [ ] Keep donation items and AR items separated in GUI and runtime checks.
- [ ] Ensure repair is allowed only for AR items according to Artifacts’ rules.
- [ ] Explicitly block repair bypasses via `Mending`, anvil, grindstone, smithing, crafting and similar functional blocks for donation items or protected special items.

### Task 7: Finish minimal web commerce glue

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`

- [ ] Finish player donation balance/history endpoints.
- [ ] Finish fixed-pack top-up session start endpoint.
- [ ] Finish session status and local QR image flow for `MOCK_SBP`.
- [ ] Finish donation item list/detail endpoints from the shared catalog.
- [ ] Finish purchase-intent endpoint wired to backend pricing and idempotency.
- [ ] Keep physical claim/reclaim out of the web layer.
- [ ] Keep frontend changes minimal: no redesign, only working screens/buttons for balance, packs, purchase, history, “pick up in game”.
- [ ] Keep admin commerce endpoints minimal but workable: overview, sessions, mark-paid, cancel, admin top-up, test purchase if retained.

### Task 8: Finish safe plugin registry foundation

**Files:**
- Create/Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\plugin_registry.py`
- Create/Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\plugin_registry_manifest.json`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`

- [ ] Move plugin registry to manifest-driven allowlist only.
- [ ] Keep plugin ids allowlisted explicitly.
- [ ] Keep editable config keys allowlisted explicitly.
- [ ] Add schema validation before apply.
- [ ] Add backup before apply.
- [ ] Add audit after validate/apply/reload/backup actions.
- [ ] Prevent arbitrary file writes and raw path construction from request input.
- [ ] Make sure config paths target the intended live config surface rather than silently editing irrelevant source files.
- [ ] Keep reload logic controlled per plugin and never arbitrary.

### Task 9: Validators and manual smoke

**Files:**
- Add/Modify: `D:\Desktop\Copimine\opt\copimine\tests\Validate*.ps1`
- Modify: `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_DONATION_SHOP_SMOKE_CHECKLIST.md`

- [ ] Add or strengthen validators for fixed packs.
- [ ] Add or strengthen validators for backend-priced purchase-intent.
- [ ] Add or strengthen validators for idempotent donation balance changes.
- [ ] Add or strengthen validators for strict claim lifecycle.
- [ ] Add or strengthen validators for reclaim loss-only policy.
- [ ] Add or strengthen validators for AR repair restrictions.
- [ ] Add or strengthen validators for donation/AR separation.
- [ ] Add or strengthen validators for plugin registry allowlist, backup, and no arbitrary write.
- [ ] Extend manual smoke with end-to-end steps:
  - top up via MOCK_SBP
  - repeat `mark-paid` and verify no double credit
  - purchase donation item on site
  - claim in game
  - simulate loss and reclaim
  - verify no second active instance
  - buy AR item without touching donation balance
  - verify plugin registry backup/apply/audit path

---

## 5. Validator Set To Add or Refresh

- `ValidateCopiMineDonationMockSbpFixedPacks.ps1`
- `ValidateCopiMineDonationPurchaseIntentBackendPriced.ps1`
- `ValidateCopiMineDonationClaimLifecycleStrict.ps1`
- `ValidateCopiMineDonationReclaimLossOnly.ps1`
- `ValidateCopiMineDonationIdempotencyScopedToOwner.ps1`
- `ValidateCopiMineDonationExpiredSessionsNotPayable.ps1`
- `ValidateCopiMineDonationClaimNoAutoReleaseAfterPhysicalDelivery.ps1`
- `ValidateCopiMineDonationClaimDeliveryReviewStatus.ps1`
- `ValidateCopiMineDonationClaimedItemsNotSuspicious.ps1`
- `ValidateCopiMineDonationEntitlementUniqueness.ps1`
- `ValidateCopiMineArtifactsArRepairOnly.ps1`
- `ValidateCopiMineArtifactsArSeparatedFromDonation.ps1`
- `ValidateCopiMinePluginRegistryAllowlist.ps1`
- `ValidateCopiMinePluginRegistryBackupAudit.ps1`
- `ValidateCopiMinePluginRegistryNoArbitraryWrite.ps1`
- existing web security validators touched by this pass if routes or auth flow change

---

## 6. Build / Compile / Check Matrix

### Java builds

- `D:\Desktop\Copimine\opt\copimine\copimine-economy-core\build-plugin.ps1`
- `D:\Desktop\Copimine\opt\copimine\copimine-artifacts\build-plugin.ps1`

### Python checks

- `python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- `python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\commerce_catalog.py`
- `python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\plugin_registry.py`

### Frontend syntax

- `node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`

### Validators

- all donation / AR / plugin registry validators touched by this pass
- any existing auth/CSRF/rate-limit validators affected by endpoint changes

---

## 7. Security Review Gate Before Git / Push

### Gate A: diff-scoped security review

- Run `codex-security` over the changed donation/shop/payment/plugin-registry files:
  - `CopiMineEconomyCore.java`
  - `CopiMineArtifacts.java`
  - `admin-web/backend/main.py`
  - `admin-web/backend/commerce_catalog.py`
  - `admin-web/backend/plugin_registry.py`
- Required topics:
  - double credit
  - duplicate purchase
  - claim/reclaim dupe
  - unsafe config write
  - missing auth/CSRF/rate limit
  - stale active item / stale cache
  - broken Russian / unsafe admin messages if found in touched files

### Gate B: fix-pass on validated findings

- Fix every validated issue before any push.
- Re-run the exact build and validator impacted by each fix.
- If a finding is intentionally deferred, document it as residual risk and block Git/GitHub until user accepts it.

### Gate C: whole-project independent review -> fix -> git

The user explicitly asked for a final three-step sequence at the end. This pass must therefore end with:

1. **Проверка**
   - run a full independent `codex-security` repository review across all active plugins and backend modules
   - include:
     - `CopiMineEconomyCore`
     - `CopiMineArtifacts`
     - `CopiMineElectionCore`
     - `CopiMineNarcotics`
     - `CopiMineWorldCore`
     - `CopiMineUltimateAdminPlus`
     - `admin-web`
   - treat it as an expert review gate for bugs, security gaps, transaction flaws, mojibake, stale state, async/main-thread issues, and dead routes/actions

2. **Исправление**
   - fix everything validated by that review inside this scope or in adjacent active modules if the finding blocks release-readiness
   - rerun builds and validators after each meaningful fix cluster
   - update manual smoke docs if behavior changes

3. **Гит**
   - only after green checks and the independent review gate is satisfied, use the GitHub workflow to inspect `git status`, prepare the final commit/push set, and publish to the repository
   - do not push unresolved validated high-severity issues

---

## 8. Done Criteria

This implementation pass is done only when all of the following are true:

1. Donation top-up works end-to-end on `MOCK_SBP`.
2. Only fixed packs `50 / 100 / 250 / 500 / 1000` are accepted.
3. `mark-paid` is idempotent and does not double-credit.
4. Purchase-intent uses backend catalog price and is idempotent.
5. Claim lifecycle safely reaches `CLAIMED` or `DELIVERY_REVIEW`, never silent dupe/release.
6. Reclaim works only for `LOST_RECLAIMABLE`.
7. Donation items are owner-bound and validated by PDC + DB state, not display name.
8. AR shop is separated from donation balance and donation catalog.
9. Plugin registry works only through allowlisted plugin ids, allowlisted keys, schema validation, backup, and audit.
10. Minimal web commerce flow works without a redesign.
11. Diff-scoped security review is green or residual risks are explicitly accepted.
12. Final whole-project review -> fix -> git sequence is completed in that order.

---

## 9. Execution Notes

- Do not rewrite the whole site or admin panel in this pass.
- Do not mix donation currency with AR.
- Do not move physical claim/reclaim to the web layer.
- Do not turn plugin registry into a raw config/file editor.
- Do not publish to GitHub before the explicit review -> fix -> git gate.
