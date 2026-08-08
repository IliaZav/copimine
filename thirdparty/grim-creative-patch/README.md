# GrimAC Creative inventory correction

This is a one-class overlay for the live `GrimAC v2.3.74-40684fb` JAR.

The upstream inventory corrector schedules Creative client predictions for a
five-tick reconciliation and can call `updateInventory()` after the server has
already accepted a Creative action. On this server that full container sync
rolls back valid split, merge, drag, and pick-from-Creative actions.

`build-grim-creative-patch.ps1` refuses to run unless its input is exactly:

`7f1df9c02b52c1d2f69949a0c465781ed8c53dd0c52234feec01785e675ff5d1`

The only intentional behavior change is an early return in
`CorrectingPlayerInventoryStorage.tickWithBukkit()` for `GameMode.CREATIVE`.
All normal-mode correction code remains in the rebuilt class.

Upstream source reference: <https://github.com/GrimAnticheat/Grim/tree/40684fb>.
The overlay source is GPL-3.0-or-later, matching GrimAC.
