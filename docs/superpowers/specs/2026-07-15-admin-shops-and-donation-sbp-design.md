# Admin Shops, Gifts, and Donation SBP Design

## Scope

Add a `Shops` entry to the Minecraft admin hub. It opens a compact control menu
for creating and managing physical artifact shops and for issuing one
administrative gift to an online or offline player. Extend the shared shop flow
with AR and donation balances, donation-balance purchases, and a disabled but
production-shaped SBP payment adapter.

The feature must leave the existing player-facing shop as one physical shop
containing both AR and donation catalog items. Shops are not split by catalog
or currency.

## Roles

Both senior and junior administrators may open the `Shops` section, create a
shop, inspect shops, teleport to a shop, and issue gifts. Removing a shop is a
destructive operation and remains senior-only. It requires an explicit GUI
confirmation. Every administrative mutation is audited with actor, target, and
item or shop identifier.

## Admin Shop Hub

The main admin hub gains a `Shops` button. Its child menu has three actions:

1. `Create shop` reads the block in the administrator's line of sight, within
   the same six-block range used by the current shop command. It rejects a
   block already registered as a shop, creates a collision-resistant internal
   identifier, saves the existing `artifact_shops` record, and creates the
   normal protected block marker. The identifier is not shown above the block
   or to players.
2. `Issue item` launches the gift flow described below.
3. `Shops` lists every shop with pagination. Each shop card shows world and
   coordinates, unique purchasers, AR turnover, and donation-currency
   turnover. Its detail view provides teleport and the senior-only delete
   confirmation.

Shop statistics are calculated from persisted transactions, not menu sessions.
AR turnover comes from completed AR purchases. Donation turnover comes from
completed donation purchases that retain the originating physical `shop_id`.
Legacy donations without a shop id remain unassigned and are excluded from an
individual shop's totals.

## Administrative Gift Flow

The gift flow is entirely in the Minecraft GUI:

1. A paginated player picker lists known players from persistent data, not just
   Bukkit's online list. It displays offline players too.
2. The administrator selects `AR` or `Donation`.
3. A paginated catalog displays all items in that catalog, including disabled
   or hidden catalog entries.
4. A confirmation screen names the selected player and item and offers `Yes`
   and `No`.
5. `Yes` creates exactly one official pending delivery or owner-bound claim,
   depending on catalog type. It never charges AR or donation balance, never
   bypasses the normal official-item provenance rules, and records the admin
   actor and `ADMIN_GIFT` source.

The implementation reuses the normal delayed-delivery lifecycle. An AR item
therefore remains a repairable official AR item. Donation items retain their
normal owner-bound lifecycle. Existing duplicate entitlement protections apply
and a conflict is shown only to the administrator.

No public chat message is sent. The recipient alone receives the shop notice
`В лавке тебя ждёт подарок от администрации` through the existing delivery
notice mechanism.

## Player Shop Balances and Purchases

Every physical shop main menu shows the player's AR and donation balance in
the top-right information slot.

AR purchases preserve their existing payment and delivery behavior. Donation
items first check the player's donation balance:

- When sufficient, the player sees a purchase confirmation and the normal
  owner-bound donation entitlement is created after the balance charge.
- When insufficient, the shop opens the top-up picker for the active purchase.
  The displayed fixed packs are dynamically filtered from `100`, `200`,
  `300`, `500`, and `1000` rubles: only packs at least as large as the current
  shortfall are offered. This uses the live price and balance; no item price is
  hard-coded.
- A separate `Top up donation balance` action always shows all five packs,
  because it is not tied to an item purchase.

The originating physical shop id is carried through the donation session and
purchase so final donation turnover can be attributed correctly.

## Disabled SBP Adapter

The system keeps the existing donation-session model but introduces a provider
interface with create-session, poll-status, cancel-session, and webhook
verification operations. The active provider is `DISABLED` until merchant
credentials are configured.

In disabled mode, the shop can present the QR/payment UI but creates no
payable transaction, does not alter the donation balance, and does not allow
manual success. The adapter exposes configuration placeholders for provider
base URL, merchant identifiers, signing secret, webhook secret, return URL,
and enabled state. Credentials stay outside source control.

With a future provider enabled, a session returns its provider payment URL and
QR payload. A verified webhook, scoped idempotency key, and session ownership
check are required before one donation-balance credit is committed. The player
is then returned to the pending purchase confirmation if the new balance is
sufficient.

## QR Presentation and Placeholder Page

An inventory-slot QR preview is too small for reliable phone scanning. The
payment GUI instead provides an `Open QR` action that temporarily gives a
custom rendered filled map. The map uses a pixel-accurate black-and-white QR,
quiet zone, and short payload so it can be scanned when the player opens it
full-screen. The payment GUI includes an explicit `Cancel payment` action.

While the provider is disabled, the QR payload is exactly:

`http://copimine.ru:18080/oplataf.html`

The static page `oplataf.html` is publicly reachable at that path and presents
a responsive mobile-first notice that payments are in development. It does not
create a session, request credentials, or expose internal payment details.

No player item must be removed merely to show the QR. The temporary map is
tracked per payment session and is removed on cancel, session expiry, payment
completion, or player quit. If an implementation later needs to reserve an
inventory slot, it must serialize the complete original item stack and restore
its exact material, amount, metadata, and durability without overwriting an
unrelated item.

## Failure Handling and Tests

All database work and provider I/O run asynchronously; only Bukkit inventory
and map rendering operations run on the server thread. UI actions verify that
their target, catalog entry, and shop still exist at execution time. Stale
menus fail safely and return the administrator or player to the prior menu.

Tests cover role gates, offline selection, gift no-charge behavior, exactly-one
delivery, hidden catalog visibility for admins, player-only notice, dynamic
top-up filtering, disabled-provider no-credit behavior, QR payload, map
cleanup, donation shop attribution, shop-stat aggregation, and mobile page
availability.
