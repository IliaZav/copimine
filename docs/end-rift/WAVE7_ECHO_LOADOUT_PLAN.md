# Echo copied loadout checkpoint

> Use Superpowers:executing-plans inline. This extends V3 Task E; it does not activate the new personal duel runtime.

**Goal:** Freeze a safe replica inventory once, preserve finite quantities and durability on restore, and exercise its item-use boundary through the existing local Echo probe.

**Architecture:** A plain bounded inventory state owns immutable item descriptors and mutable quantities/damage. A Paper adapter reads a player's slots once, constructs fresh allowlisted vanilla replica items, and never transfers a real stack or privileged item metadata. The existing presentation probe gains an explicit copied-loadout mode; its ordinary fixed-gear mode remains useful for parity checks.

**Spec:** Supplied `01_WAVE7_IMPLEMENTATION_EN.md`, sections 6–7, Task E, and `06_DECISIONS_AND_COVERAGE_EN.md`. The full Waves goal remains binding.

**Global constraints:** 41 original slots plus a separate allowance of exactly two ordinary golden apples; first creation repairs supported replicas only. No real inventory writes, native equipment drops or XP. No fake player, new encounter engine or main boss changes. Restarted quantities must come from a validated current receipt, not a fresh snapshot. No native acceptance claim from tests.

**Review focus:** Full inventories and offhand aliasing; stale quantity receipts; cancelled or repeated use completion; hidden PDC/custom attributes/charged nested items; native equipment readers returning mutable stack handles.

## Task 1 — Frozen safe inventory and finite state

- [x] RED: run `tests/test_wave7_echo_loadout.py`; assert immutable first capture, exactly two extra apples, no original mutation, finite consumption, stale revision rejection and unchanged durability after encode/restore.
- [x] Implement `domain/wave7/EchoLoadoutState.java` (42 bounded slots, descriptors, counts/damage/revision, strict map codec) and `runtime/wave7/EchoReplicaInventory.java` (Paper capture/material validation/fresh equipment).
- [x] Test the actual adapter against narrow item API fixtures, then compile against pinned Paper. Publish supported vanilla items and explicit custom-item gaps.

## Task 2 — Existing local presentation path

- [x] RED: copied mode never creates a weapon absent from its frozen slots; a completed minimum-32-tick golden apple use consumes one, an accepted replacement/quit/stale generation consumes none; repeated ticks do not consume again. Existing five-tick dispatch normally completes at35; exact native timing parity is open.
- [x] Extend `EchoPresentationProbe` with optional replica inventory, and `/cmend test echo loadout [player]` with the same existing permission/environment/capability checks as `start`.
- [x] Keep this probe disposable; it is not durable claim restoration, owner-private combat or authoritative bow/melee combat. State codec coverage establishes the future persistence boundary only.

## Task 3 — Review and publication

- [x] Run full Python suite, server build, registered End Rift gate and `git diff --check`. Client/pack remain byte-identical if no client/asset changes are necessary.
- [ ] Review the scoped immutable public diff with CodeRabbit, verify each comment, commit/push only owned files, verify exact remote SHA and actual CI.
- [ ] Update the GitHub-linked ledger with implemented/tested/native-blocked distinctions. Keep native parity, full Task E and named trial activation open.

Ruling: custom items with PDC, custom models/attributes, unbreakable flags or preloaded crossbow contents are blocked in this first adapter. Fresh vanilla descriptors prevent arbitrary artifact hooks and nested contents from executing. Cost if wrong: an explicitly unsupported item may leave the replica less equipped; it cannot be counted as perfect loadout parity until its specific first-party adapter is audited and tested.
