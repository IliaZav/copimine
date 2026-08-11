# Utility AR artifacts

This companion specification records the server-side utility slice from the
2026-08-11 AR-artifact release task. It keeps the existing catalog, purchase,
PDC authenticity, and Paper event architecture; it does not add item IDs,
client code, database migrations, or world data.

| ID | Material | Price | Cooldown | Effect |
|---|---|---:|---:|---|
| `repair_kit` | `SHEARS` | 10 AR | 0 s | five successful 25% repairs of ordinary vanilla damageable items |
| `return_stone` | `ECHO_SHARD` | 300 AR | 300 s | three-second channel to a safe personal respawn or main-world spawn |
| `infinite_torch` | `TORCH` | 100 AR | 0 s | successful placement leaves the exact official instance available |

All three are `AR_SHOP`, `EPIC`, purple `&d` items with `custom_model_data: 0`
and no required resource pack. `repair_kit` uses a five-point Paper damage bar
plus a PDC use count, and rejects all CopiMine official/custom targets and
vanilla repair/automation paths. `return_stone` stores its successful cooldown
timestamp in player PDC, cancels on damage/death/logout/item switch, and only
starts the cooldown after a successful safe teleport. `infinite_torch` restores
its exact snapshot only after a successful `BlockPlaceEvent`; cancelled or
protection-rejected placement does not restore or duplicate anything. Automated
container, crafting, repair, merge, and dispenser paths are fail-closed.
