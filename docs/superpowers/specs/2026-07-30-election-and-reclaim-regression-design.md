# Election and donation reclaim regression repair

## Goal

Repair the live election controls, polling-station lifecycle, and donation-item loss recovery without changing the agreed RP election rules or deleting historical data.

## Scope

The repair covers the controls that currently surface the generic "you found a bug" notice: saving the candidate roster, starting debates, configuring or starting voting, and early campaign completion. It also covers a polling station that is broken by an administrator and then recreated at the same coordinates, and reclaim eligibility after an owned donation item is lost in the void, an explosion, a cactus, creative deletion, or removal by another plugin.

## Election behavior

- A single campaign may be active. A current president blocks a new campaign until resignation, removal, or natural term expiry.
- Owner, senior administrators, and junior administrators share election-management access.
- Applications are accepted only on the website from authenticated, linked players. They contain Minecraft name, motivation, server plans, programme, and optional faction.
- An administrator can select two through four candidates. Starting debates with fewer than two must return a clear validation message, not an internal error.
- Debates and voting start and finish only through administrator actions. Voting duration is 24 to 72 hours; an active duration can only increase and can never exceed 72 hours. Administrators can finish voting early.
- Voting remains in-game only, through any active polling station, and one UUID can confirm one irreversible vote.
- The winner is assigned automatically; a tie is resolved by any administrator. The presidential term is seven days and resignation/removal closes the associated taxes without starting a replacement campaign.

## Polling-station lifecycle

The station record remains historical when an administrator breaks its block, but it must no longer be active at that position. Creating a station must deterministically reactivate or create an active binding for the placed block. Interaction resolution must only use active bindings whose physical block is present, so replacing the block restores interaction while broken blocks cannot be used.

## Donation reclaim behavior

All terminal loss paths feed one idempotent transition from the current owned instance state to `LOST_RECLAIMABLE`. The transition records an audit event and never produces a second reclaim entry for the same unique item. Intentional use, valid transfer, and normal reclaim replacement remain non-reclaimable.

## Implementation boundaries

- The web frontend owns input conversion and user-facing validation messages.
- The backend owns API validation, authorization, and stable election error responses.
- The election plugin owns authoritative game state, station bindings, and voting.
- The artifacts plugin owns authoritative loss detection and instance-state transitions.
- Database migrations are additive; no accounts, worlds, inventories, taxes, or election history are removed.

## Verification

Each reported UI operation receives a regression test for its request payload and response contract. Election tests cover the two-candidate guard, 24/72-hour constraints, early finish, and station break/recreate resolution. Artifact tests cover all five loss paths and duplicate prevention. The relevant plugins compile, backend checks run, and the same verified edits are copied to the local release-stage source tree.
