# Election Security and Runtime Audit Design

## Purpose

Audit and repair `CopiMineElectionCore` without changing the approved RP
election rules or deploying anything to a live server.

## Fixed product rules

- Only one active campaign may exist; a current president blocks a new one
  until resignation, removal, or expiry.
- Every administrator rank, including junior administrators, may administer
  elections.  Every mutating action must still re-check that permission.
- Applications are website-only.  An administrator selects two through four
  approved applications before debates can start.
- Stages are manual.  Voting may be opened only for 24, 48, or 72 hours; an
  open vote is never shortened and cannot exceed 72 hours in total.
- A player votes only in-game, once, by UUID, after confirmation.  The vote
  cannot be changed.
- Polling stations may be recreated after an administrator removes their
  protected block.  Recreating a station must restore interaction without
  erasing campaign data or vote history.
- Finishing a campaign early is available in the administrator UI.  Election
  history, accounts, worlds, inventories, banks, and whitelist data remain.

## Audit approach

The audit treats every menu click, chat prompt, protected-block interaction,
and asynchronous database transition as an untrusted entry point.  For each
entry point it traces authorisation, current campaign/stage reads, input
normalisation, database mutation, and the Bukkit main-thread boundary.

Confirmed defects are repaired one at a time with an executable regression
test first.  Static contract validators are supplemented by a Java build and
the relevant complete election-validator suite.  No real payment functionality
or production service configuration is in scope.
