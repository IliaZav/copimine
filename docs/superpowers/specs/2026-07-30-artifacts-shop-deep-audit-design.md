# Artifacts Shop Deep Audit Design

## Goal

Audit and harden the in-game artifacts shop so purchases, donation claims, item ownership, and loss-only recovery fail safely without duplicating or losing entitlement state.

## Scope

- Shop catalog and price binding, cart confirmation, inventory delivery, and transaction idempotency.
- Donation balance claims and physical-item lifecycle. Real payment-provider integration remains out of scope.
- Official item authenticity, owner binding, transfer, destruction, and recovery after terminal loss.
- Loss journal coverage for void, cactus/contact damage, explosions, creative deletion, entity despawn, and plugin removal.
- Bukkit main-thread safety, async database boundaries, stale GUI/session cleanup, and player-facing error handling.

## Safety rules

- Catalog data is server-authoritative and malformed records fail closed.
- One paid claim and one physical instance may produce at most one current entitlement.
- Only official `LOSS_ONLY` instances may transition to `LOST_RECLAIMABLE`, once per unique item.
- Failed delivery remains recoverable; a delivered, consumed, broken, transferred, or replaced item must not re-enter reclaim.
- Audit migrations are additive and preserve purchase, player, and world data.

## Verification

The audit first runs the existing artifacts, donation, AR shop, and cart validators. Every confirmed defect receives a regression assertion before its implementation. Final verification compiles the plugin and reruns the focused and full related validator suites from a clean branch state.
