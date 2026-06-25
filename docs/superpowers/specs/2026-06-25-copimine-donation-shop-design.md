# CopiMine Donation Shop Design

**Date:** 2026-06-25

**Scope:** Donation shop only. This pass covers donation currency, mock SBP payment foundation, website purchase flow, owner-bound meme items, in-game claim and reclaim, admin top-up and audit, and the supporting API and DB contracts. It must not rewrite elections, narcotics, WorldCore, CopiMineClient, Iris, Emotecraft, CoreProtect, or the wider resource pack beyond what is directly needed for donation shop UX.

## Compatibility Audit

### Current module ownership

- `CopiMineEconomyCore` already owns donation balance, donation ledger, test purchase, and claim tables and is the correct home for payment sessions, balance mutations, and purchase bookkeeping.
- `CopiMineArtifacts` already owns artifact catalog, instance records, issuance, shop block interaction, PDC tagging, and suspicious-item handling and is the correct home for donation item instances, reclaim, and gameplay effects.
- `admin-web` already has donation-related player and admin endpoints, but they currently expose only a partial foundation and still need a proper player shop flow, mock SBP sessions, fixed top-up packs, and QR rendering.
- `CopiMineUltimateAdminPlus` must remain a hub/delegator and must not become a second owner of donation logic.

### Current gaps that this design closes

- Donation balance exists, but the product split between top-up, purchase, claim, and reclaim is not fully codified.
- Donation item delivery exists, but the lifecycle for owner-bound reclaimable items needs stricter status rules and anti-dupe boundaries.
- Website donation flows currently say that real payments are unavailable, but there is no complete mock SBP foundation with QR, fixed packs, admin mark-paid, and canonical purchase pages.
- In-game shop UX does not yet fully separate browsing, balance top-up, claim, and lost-item reclaim.

## Product Rules

### Donation currency

- Donation items are purchased only with `Donation`, never with `AR`.
- `Donation` is a separate currency with the fixed conversion rule `1 real ruble = 1 donation unit`.
- `AR` cannot be bought with real money and must not be mixed with donation balance in UI, SQL, or business logic.
- Fixed top-up packs are only `50 / 100 / 250 / 500 / 1000`.

### Mock SBP foundation

- Real payment provider integration is out of scope for this pass.
- The system must still implement a real payment foundation:
  - `DonationPaymentProvider` interface
  - payment session table
  - QR generation
  - admin `mark paid`
  - donation balance ledger
  - purchase spend from donation balance
  - claims after purchase
- The active provider for this phase is `MOCK_SBP`.
- No UI may claim that a real payment succeeded until the session state is `PAID`.

### Shop split

- Top-up may be started from the website and from the in-game donation GUI.
- Purchase of donation items happens only on the website.
- Claim and reclaim happen only in the game through the donation shop block GUI.

## Core Architecture

### Shared donation catalog

Donation items must not be implemented as scattered `if` chains by display name. A single shared runtime catalog is mandatory.

Every catalog entry includes:

- `item_id`
- `display_name`
- `base_material`
- `price_donation`
- `enabled`
- `source = DONATION_SHOP`
- `owner_bound = true`
- `reclaim_policy = LOSS_ONLY`
- `consume_policy`
- `effect_profile_id`
- `cooldown_seconds`
- `proc_chance`
- `max_stack`
- `repairable`
- `custom_texture_mode_allowed`
- `catalog_version`
- `updated_at`

The catalog is runtime/config owned, not player-data owned. Website, EconomyCore, and Artifacts must all read the same `item_id` and price contract from the same catalog version.

### Module boundaries

`CopiMineEconomyCore` owns:

- `donation_accounts`
- `donation_balance_ledger`
- `donation_payment_sessions`
- `donation_purchases`
- `donation_item_claims`
- fixed donation packs
- `DonationPaymentProvider`
- mock SBP session lifecycle
- admin top-up and admin mark-paid

`CopiMineArtifacts` owns:

- donation catalog runtime
- `artifact_item_instances`
- official donation PDC schema
- physical issuance in-game
- lost-item reclaim
- gameplay effects
- suspicious/fake item handling
- anti-dupe checks for `ACTIVE` instances

## Donation Item Model

### PDC contract

Every official donation item must carry CopiMine PDC that includes at least:

- `copimine_item_type = DONATION_SHOP_ITEM`
- `catalog_item_id`
- `unique_item_id`
- `owner_uuid`
- `owner_name`
- `source = DONATION_SHOP`
- `bound = true`
- `reclaimable = true` only for loss-only items

Gameplay must never trust display name, lore, or base material as the source of truth. Effects only activate after validating:

- official CopiMine PDC
- valid `unique_item_id`
- matching `owner_uuid`
- instance status in DB
- cooldown
- PvP and protection rules

### Instance table model

Each issued item gets one row in `artifact_item_instances` with:

- `unique_item_id`
- `item_id`
- `owner_uuid`
- `purchase_id`
- `status`
- `durability_state`
- `created_at`
- `updated_at`

`CLAIM_PENDING` does not live in `artifact_item_instances`. Before physical issuance, the state lives in `donation_item_claims`.

### Instance statuses

- `ACTIVE`
- `LOST_RECLAIMABLE`
- `REPLACED_AFTER_LOSS`
- `BROKEN`
- `CONSUMED`
- `DELETED_AS_INVALID`

### Behavioral classes

- `Permanent bound gear`
  Weapons, armor, shields, tools, and other durable items. External loss is reclaimable. Breaking by durability is not reclaimable.
- `Consumable relic`
  Items that spend themselves through their own effect. After use they become `CONSUMED` and must be bought again.
- `Active utility item`
  Items with use-based or passive utility effects. External loss is reclaimable, but internal consumption or breakage is not.

## Loss, Breakage, and Reclaim

### What counts as external loss

Donation items must not be lost forever because of:

- death
- lava
- fire
- void
- despawn
- cleanup
- server-side deletion bugs

Those cases move the item instance to `LOST_RECLAIMABLE`.

### What is not reclaimable

- If the item broke through durability, status becomes `BROKEN`.
- If the item spent itself through its own gameplay mechanic, status becomes `CONSUMED`.
- `BROKEN` and `CONSUMED` are not reclaimable.

### Reclaim rules

- Reclaim is a dedicated in-game screen named `Вернуть утерянные предметы`.
- It shows only instances with status `LOST_RECLAIMABLE`.
- Reclaim is not bulk-return. The player chooses a specific item.
- Reclaim creates a new `unique_item_id`.
- The old instance becomes `REPLACED_AFTER_LOSS`.
- If a stale old item resurfaces after reclaim, it must be treated as invalid and go through the suspicious/cleanup path such as `DELETED_AS_INVALID`.
- Reclaim is blocked if the player already has an `ACTIVE` instance for the same entitlement.

## Purchase, Claim, and Reclaim Workflow

### Payment and top-up flow

- Balance changes only after payment session status becomes `PAID`.
- Duplicate `mark paid` or future webhook replay must never credit balance twice.
- `CopiMineEconomyCore` must apply idempotent ledger mutations with an `idempotency_key`.

### Purchase flow

- Purchase happens only on the website.
- The website sends `purchase-intent` with only `item_id`.
- The backend resolves the price from the shared catalog. Frontend price is never trusted.
- `EconomyCore` atomically:
  - verifies enough donation balance
  - subtracts donation balance
  - creates `donation_purchase` with status `CLAIM_PENDING`
  - creates `donation_item_claim` with `UNCLAIMED`
- Purchase does not immediately create an `ItemStack`.

### Claim flow

Physical issuance happens only in-game via `CopiMineArtifacts`.

Claim lifecycle:

- `UNCLAIMED -> RESERVED -> DELIVERING -> CLAIMED`

Claim rules:

- validate owner UUID
- validate claim status
- validate inventory space
- validate that the player does not already have an `ACTIVE` instance for the same entitlement
- create new `unique_item_id`
- create `artifact_item_instance`
- issue item physically
- finalize claim only after successful instance creation and item issuance

Failure rules:

- if issuance never started, claim may go back to `UNCLAIMED`
- if issuance may already have happened but finalization failed, claim becomes `DELIVERY_REVIEW`
- `DELIVERY_REVIEW` must not auto-repeat issuance

### Reclaim flow

Reclaim is separate from claim.

- Only `LOST_RECLAIMABLE` instances appear in the reclaim screen.
- Reclaim verifies that no `ACTIVE` instance exists for the same entitlement.
- Old instance becomes `REPLACED_AFTER_LOSS`.
- New instance becomes `ACTIVE`.
- Reclaim writes audit events.

## Website and Game UX

### In-game donation shop

The root GUI must stay short and predictable. It contains exactly these five core actions:

- `Каталог`
- `Донат-баланс`
- `Пополнить`
- `Мои покупки`
- `Вернуть утерянные предметы`

`Каталог`:

- shows all enabled donation items
- shows price in donation units
- shows the main effect, cooldown, and status
- if the item is not bought, clicking sends the player a private website link for that exact item page
- if the item is bought and there is no `ACTIVE` instance, status is `Можно забрать`
- if an `ACTIVE` instance already exists, status is `Уже у тебя`

`Донат-баланс`:

- shows current donation balance
- shows recent top-up and spend history
- clearly states that `Donation` and `AR` are separate currencies

`Пополнить`:

- shows only pack buttons `50 / 100 / 250 / 500 / 1000`
- creates a payment session
- tries to render QR through a temporary `MapQrRenderer`
- always has a mandatory fallback:
  - link to the website payment page
  - session code

`Мои покупки`:

- shows bought items and pending claims
- provides in-game claim only where claim status is `UNCLAIMED`

`Вернуть утерянные предметы`:

- shows only `LOST_RECLAIMABLE` items
- reclaims one chosen item at a time

### Website player cabinet

The website is the canonical place for payment and purchase. It contains four donation-related screens:

- `Донат-баланс`
- `Донатная лавка`
- `Мои донат-предметы`
- `История`

`Донат-баланс`:

- current donation balance
- pack buttons `50 / 100 / 250 / 500 / 1000`
- create mock SBP session
- QR, session id, status, expiry, and top-up history
- honest note that the real provider is not yet connected

`Донатная лавка`:

- item catalog
- owned and not-owned states
- buy button
- after purchase the UI says `Заберите предмет в игре`

`Мои донат-предметы`:

- all purchases
- claim state
- reclaimable state
- instructions that physical issuance and reclaim happen in-game

`История`:

- top-ups
- purchases
- claims
- admin test grants

### Admin panel

The admin panel contains five sections:

- `Donation Balance`
- `Mock SBP Sessions`
- `Donation Shop`
- `Claims`
- `Provider Settings`

`Provider Settings` shows `MOCK_SBP` as the active provider and a future-provider slot, but must not expose secrets.

## Payment Foundation and QR

### Donation provider contract

`DonationPaymentProvider` is mandatory and must support the mock phase cleanly. The mock provider is `MOCK_SBP`.

The provider contract covers:

- create payment session
- resolve QR payload
- query status
- mark paid
- cancel
- future webhook/provider callback mapping

### QR generation

- QR is generated locally on the backend.
- External QR APIs are forbidden.
- The website is the canonical QR display.
- The game may show the same session through `MapQrRenderer`.
- If in-game QR rendering is unavailable, donation flow still works through website link plus session code.

### Idempotency

- repeated `mark paid` must not create duplicate balance credits
- repeated callback processing must not create duplicate balance credits
- repeated purchase intent must not silently create duplicate purchases
- repeated claim or reclaim clicks must not create duplicate active items

## Database and Status Contracts

### Canonical tables

- `donation_accounts`
- `donation_balance_ledger`
- `donation_payment_sessions`
- `donation_purchases`
- `donation_item_claims`
- `artifact_item_instances`

### Session statuses

- `CREATED`
- `PENDING`
- `PAID`
- `CANCELLED`
- `EXPIRED`

### Purchase statuses

- `PAID`
- `CLAIM_PENDING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

### Claim statuses

- `UNCLAIMED`
- `RESERVED`
- `DELIVERING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

### Data ownership rules

- `EconomyCore` is the only owner of donation balance, payment sessions, purchases, and claims.
- `Artifacts` is the only owner of physical issuance, item instances, PDC, reclaim, effects, and suspicious/fake item cleanup.

## API Contracts

### Player API

- `GET /api/player/donation/balance`
- `GET /api/player/donation/history`
- `GET /api/player/donation/packs`
- `POST /api/player/donation/sbp/session`
- `GET /api/player/donation/sbp/session/{sessionId}`
- `GET /api/player/donation/sbp/session/{sessionId}/qr.png`
- `GET /api/player/shop/donation-items`
- `GET /api/player/shop/donation-items/{itemId}`
- `GET /api/player/shop/owned`
- `POST /api/player/shop/purchase-intent`

### Admin API

- `GET /api/admin/donation/overview`
- `GET /api/admin/donation/sessions`
- `POST /api/admin/donation/add-balance`
- `POST /api/admin/donation/test-purchase`
- `POST /api/admin/donation/sbp/session/{sessionId}/mark-paid`
- `POST /api/admin/donation/sbp/session/{sessionId}/cancel`
- `GET /api/admin/shop/donation-items`

## Security and Anti-Dupe Rules

### Input validation

- `item_id` only from the donation catalog allowlist
- top-up pack only from `50 / 100 / 250 / 500 / 1000`
- all SQL parameterized
- all state-changing endpoints require auth, CSRF, and role check
- rate limit is mandatory on:
  - create session
  - purchase intent
  - admin mark-paid
  - admin top-up
  - admin test purchase

### Anti-dupe rules

- one entitlement may not have two `ACTIVE` instances at once
- claim and reclaim must use atomic transition or row lock
- any stale resurfaced item with inactive DB status goes through invalid/suspicious cleanup
- no gameplay effect may fire for a fake or non-owned item

### Audit

Audit events are required for:

- payment session creation
- mark paid
- cancel session
- add balance
- purchase creation
- claim reserved
- claim delivered
- claim delivery review
- reclaim issued
- invalid/fake item cleanup

The audit log must not store:

- QR payloads
- provider secrets
- private tokens

## Mandatory Donation Item Roster

The following item names and ids are fixed and must not be renamed.

1. `batin_remen_sudnogo_dnya`
   - Name: `Батин Ремень Судного Дня`
   - Base item: `NETHERITE_AXE`
   - Role: heavy PvP control item
   - Key effects: chance for `Slowness II`, controlled lightning visual, temporary shield disable, short `Strength I` self-buff

2. `nu_ty_i_nakopal_blyat_pickaxe`
   - Name: `Кирка “Ну ты и накопал, блять”`
   - Base item: `NETHERITE_PICKAXE`
   - Role: control pickaxe with safe temporary bury effect
   - Key effects: chance to bury target about three blocks down, temporary suffocation trap with guaranteed rollback, extra `Mining Fatigue III`, owner mining utility buff

3. `kosa_nalogovoy_inspekcii`
   - Name: `Коса Налоговой Инспекции`
   - Base item: `NETHERITE_HOE`
   - Role: weapon with tax-themed extra damage and capped vampirism
   - Key effects: controlled bonus damage, `Hunger`, limited self-heal, short chase speed buff

4. `kaska_prorab_huev`
   - Name: `Каска “Прораб хуев”`
   - Base item: `NETHERITE_HELMET`
   - Role: builder-survivor helmet
   - Key effects: fall-damage mitigation, attacker `Mining Fatigue II`, short owner `Haste I`

5. `mne_pohuy_ya_v_tanke_vest`
   - Name: `Жилет “Мне похуй, я в танке”`
   - Base item: `NETHERITE_CHESTPLATE`
   - Role: defensive chestplate
   - Key effects: short `Resistance I`, controlled reflect damage, low-health `Absorption I`

6. `ne_segodnya_suka_shield`
   - Name: `Щит “Не сегодня, сука”`
   - Base item: `SHIELD`
   - Role: retaliation shield
   - Key effects: chance to apply `Nausea II`, controlled lightning visual near attacker, short `Resistance I`

7. `ya_esche_ne_vse_isportil_totem`
   - Name: `Тотем “Я ещё не всё испортил”`
   - Base item: `TOTEM_OF_UNDYING`
   - Role: controlled emergency survival relic
   - Key effects: chance to survive lethal damage at `1 HP`, short `Resistance I`, long cooldown, no stacking, no free duplicate live copies

8. `pohuy_na_debaffy_amulet`
   - Name: `Амулет “Похуй на дебаффы”`
   - Base item: `HEART_OF_THE_SEA`
   - Role: utility debuff cleaner
   - Key effects: clears one allowed debuff on cooldown, may grant short `Resistance I`
   - Hard restriction: must not clear narcotics overdose or protected system effects

9. `vremya_platit_nalogi_clock`
   - Name: `Часы “Время платить налоги”`
   - Base item: `CLOCK`
   - Role: tax-status utility item with light combat flavor
   - Key effects: reads tax status through ElectionCore bridge when available, minor combat slowdown proc

10. `gde_moy_lut_blyat_compass`
    - Name: `Компас “Где мой лут, блять?”`
    - Base item: `COMPASS`
    - Role: owner-only last-death locator
    - Key effects: points only to the owner’s last death location, respects closed or protected worlds

Numeric price points remain catalog-owned configuration values rather than hardcoded design constants. The design fixes the currency model, pack sizes, ownership rules, and effect boundaries; individual prices are intentionally adjustable through the shared catalog without changing DB or API shape.

## Manual Validation Targets

The implementation plan must include manual smoke coverage for:

- website top-up with mock SBP session
- QR render on website
- in-game top-up with QR render or safe fallback
- admin mark-paid and idempotent balance credit
- website-only purchase
- in-game claim
- reclaim after external loss
- no reclaim after `BROKEN` or `CONSUMED`
- no duplicate `ACTIVE` instances
- fake item rejection
- protected-zone, PvP, and cooldown enforcement on all special effects

## Out of Scope

- real SBP provider
- selling `AR` for real money
- rewriting elections, narcotics, WorldCore, or CopiMineClient
- final production custom textures for donation items
- new unrelated website subsystems
