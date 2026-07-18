# Shop Cart And Item Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the technical-looking shop catalogue with a clear item showcase, one visible cart entry point, two separate checkouts, and server-created delayed deliveries for every paid item.

**Architecture:** The browser keeps only item identifiers in a local cart and never sends prices. Two batch checkout routes load the current catalogue, check the player's PIN and balance, create all purchase and delayed-delivery rows in one database transaction, and return the issued item IDs. The public shop reads item-specific image URLs from the catalogue; those images are copied from the same resource-pack sources used by the game.

**Tech Stack:** FastAPI, PostgreSQL-compatible repository layer, vanilla ES modules, CSS, Paper plugin, Minecraft resource pack, PowerShell validators, Browser/IAB.

---

## Design Direction

The page is a compact Minecraft trading desk for regular players. Cards show a large pixel texture, human-readable name, one useful effect sentence, price, and one add-to-cart action. Internal material names, effect IDs, database IDs, and raw cooldown tokens are not shown. A single chest-cart control appears in the shop header; `/cart.html` separates AR and donation orders into two independent checkout bands. The signature element is the pixel-texture shelf: a calm square image tile with a subtle green frame that identifies every item at a glance without decorative panels inside panels.

## File Structure

- `admin-web/backend/commerce_catalog.py`: exposes stable public image URLs and short player-facing descriptions from the existing catalogue.
- `admin-web/backend/main.py`: validates batch cart requests and writes AR purchases/pending deliveries or donation claims atomically.
- `admin-web/frontend/shops.html`: shop header, catalogue mounts, and the single cart control.
- `admin-web/frontend/cart.html`: public cart route with separate AR and donation checkout sections.
- `admin-web/frontend/assets/js/public/shop-cart.js`: local cart persistence, item IDs only, counters, and cart-change events.
- `admin-web/frontend/assets/js/public/site-render.js`: clean product cards, image rendering, and cart entry points.
- `admin-web/frontend/assets/js/public/cart-page.js`: authenticated checkout UI and response handling.
- `admin-web/frontend/assets/js/public/public-page.js`: selects the cart renderer for `public-cart`.
- `admin-web/frontend/assets/css/release-ui.css`: product-card, cart, focus, and responsive rules.
- `admin-web/frontend/assets/item-textures/*`: website copies of resource-pack textures, including static previews for animated items.
- `admin-web/scripts/sync_item_visuals.py`: deterministic resource-pack-to-site image sync and mapping validation.
- `tests/ValidateCopiMineShopCartContract.ps1`: ensures cart routes, server-side validation, idempotency, and delayed-delivery writes exist.
- `tests/ValidateCopiMineShopCartPresentation.ps1`: rejects technical catalogue fields in visible product cards and requires the header cart plus separated cart sections.
- `tests/ValidateCopiMineWebsiteItemVisuals.ps1`: checks every sellable item has a website texture matching the resource-pack mapping.
- `admin-web/scripts/backend_smoketest.py`: adds isolated cart checkout verification.

### Task 1: Cart Contract

**Files:**
- Create: `tests/ValidateCopiMineShopCartContract.ps1`
- Modify: `admin-web/backend/main.py`

- [ ] Write a failing validator that requires two player routes, bounded item lists, server-side catalogue lookup, PIN checking, per-cart idempotency, and inserts into `artifact_pending_deliveries` or `donation_item_claims`.
- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineShopCartContract.ps1` and confirm it fails before routes exist.
- [ ] Add `PlayerCartCheckoutIn`, normalise a unique bounded item list, and add `/api/player/shop/cart/ar/checkout` plus `/api/player/shop/cart/donation/checkout`.
- [ ] Use the existing pending-delivery and donation-claim schemas; calculate all prices from the current server catalogue and debit each cart only after every requested item is valid.
- [ ] Re-run the focused validator until it passes.

### Task 2: Atomic Checkout Verification

**Files:**
- Modify: `admin-web/scripts/backend_smoketest.py`

- [ ] Add a failing isolated test which creates two valid AR items and two valid donation items, calls each cart route once, retries with the same idempotency key, and asserts exactly one delayed-delivery row or claim per item.
- [ ] Confirm the test fails while the routes are missing.
- [ ] Verify insufficient balance, repeated IDs, unavailable catalogue items, and a wrong PIN leave no partial purchase rows.
- [ ] Run `admin-web\.codex-venv\Scripts\python.exe admin-web/scripts/backend_smoketest.py` and require `Backend smoketest OK`.

### Task 3: Catalogue Images

**Files:**
- Create: `admin-web/scripts/sync_item_visuals.py`
- Create: `tests/ValidateCopiMineWebsiteItemVisuals.ps1`
- Modify: `admin-web/backend/commerce_catalog.py`
- Create/update: `admin-web/frontend/assets/item-textures/*`

- [ ] Add a failing validator that checks each AR or donation catalogue ID has a matching resource-pack texture and website preview asset.
- [ ] Run the validator and confirm it fails before site previews exist.
- [ ] Copy each item texture from `resourcepacks/src/assets/copimine/textures/item/artifacts/` into the website asset folder; write a still first-frame preview for animated compass and clock textures.
- [ ] Expose only `/assets/item-textures/<item-id>.png` as `image_url` from the catalogue.
- [ ] Run the resource-pack mapping validators, the new website-visual validator, and `powershell -ExecutionPolicy Bypass -File resourcepacks/build-resourcepack.ps1`.

### Task 4: Product Showcase

**Files:**
- Create: `tests/ValidateCopiMineShopCartPresentation.ps1`
- Modify: `admin-web/frontend/shops.html`
- Modify: `admin-web/frontend/assets/js/public/site-render.js`
- Modify: `admin-web/frontend/assets/css/release-ui.css`

- [ ] Write a failing validator that rejects visible `base_material` and `effect_profile_id` metadata, requires an item image, price, human description, add-to-cart action, and one header cart control.
- [ ] Run it and confirm it fails on the current technical card renderer.
- [ ] Replace the chip layout with a responsive product shelf: image, category, title, plain effect description, price, and add action.
- [ ] Keep one cart control in the shop header with an accessible count; update it on cart changes and do not add duplicate cart controls to other public pages.
- [ ] Re-run the focused validator and `node --check` for changed modules.

### Task 5: Cart Route And Checkout Screen

**Files:**
- Create: `admin-web/frontend/cart.html`
- Create: `admin-web/frontend/assets/js/public/shop-cart.js`
- Create: `admin-web/frontend/assets/js/public/cart-page.js`
- Modify: `admin-web/frontend/assets/js/public/public-page.js`
- Modify: `admin-web/frontend/assets/css/release-ui.css`

- [ ] Store only `{ itemId, currency }` in local storage and deduplicate items by currency plus item ID.
- [ ] Render two separate order sections on `/cart.html`: AR and donation, each with its own total, PIN input, remove controls, and checkout command.
- [ ] Send only the item IDs, PIN, and generated idempotency key to the matching batch route. On success remove only confirmed items and show the game pickup instruction.
- [ ] For a signed-out visitor, retain the cart and send the visitor to sign-in instead of exposing an unsafe checkout form.
- [ ] Verify desktop and 390px mobile layout, empty states, button disabled states, keyboard focus, and reduced motion.

### Task 6: Plugin And Release Verification

**Files:**
- Verify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Verify: `resourcepacks/models_manifest.json`
- Verify: `resourcepacks/item_texture_sources.json`

- [ ] Confirm the plugin reads `artifact_pending_deliveries` for AR purchases and `donation_item_claims` for donation purchases, creates one owner-bound item per claim, and uses the existing resource-pack model IDs.
- [ ] Run focused shop, texture, plugin-build, backend, contract, security, and full validator checks.
- [ ] Test `/shops.html` and `/cart.html` in Browser at desktop and mobile widths; capture screenshots after adding an AR item and a donation item.
- [ ] Run a security review of the final diff, stage only scoped files, commit, and push `codex/release-overhaul`.
