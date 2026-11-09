# End Rift — round status, 2026-11-09 (afternoon)

## Wave 2 phantom-activation fix — VERIFIED BY AUTOMATED TEST

Root cause found in production code, not in a fixture. `spawnNextWaveGroup`
reserved a phantom spawn and cleared only `waveTwoPhantomActivationLocation`,
`waveTwoPhantomActivationGroup` and `waveTwoPhantomActivationCycle`, leaving
`waveTwoPhantomActivationState` TARGETABLE for the group that had just been
booked.

Both readers treat a lingering TARGETABLE as an in-flight activation:

- the booking path cannot take either branch for the next group, because the
  stale fence carries the previous group number;
- `retryPendingWaveTwoPhantomActivation` returns before re-driving the debt.

Wave 2 therefore spawned its first Endermen subwave, cleared it, and waited
forever while subwaves two and three stayed owed — the stranded-group
completion the Wave 2 contract forbids.

Fixture note: the harness stub answered a flat `false`, so no subwave could
ever be booked and the stale state was never reached. The stub now models the
real two-step path (warn first, book on a later call).

Result: `test_end_rift_waves2_4_mechanics.py` 62 passed / 2 failed -> 64 passed.
Waves 1/2/6/7 together: 81 passed.

## Full suite

3168 passed, 15 skipped, 6 failed (12:59). Every one of the six failures belongs
to another plugin and none touches End Event:

- 4 x CopiMineNarcotics — the committed `copimine-narcotics/CopiMineNarcotics.jar`
  differs from the rebuilt artifact, so its lock is stale.
- 2 x CopiMineClient artifact size — the client candidate on disk does not match
  the locked build artifact.

The End Event pin tests pass with the rebuilt jar
(size 1992770, sha256 4853d301..., sha512 0b70fb27...), re-pinned in the lock,
the inventory, the migration receipt and the two inline test literals.

## Live run — NOT VERIFIED, and why

The local server booted with the fixed plugin (31.7 s, services ready) and
ArenaBot connected. A wave 2 start was refused by a real precondition:

- `participants=0`, `pads=0/1`, `marked=0`;
- the bot stands at `-21.3, 30, 24.3` — outside the arena
  (`[-20,97,-20]..[20,103,20]`), and below it;
- `tp ArenaBot 0 100 0` does not hold: the client's own physics puts the body
  back, because injected input never reaches the game through GLFW raw input.

With no marked participant, no phantom layout can resolve, every
Endermen-bearing subwave defers, and the wave ends after the 180 s
`arena-empty` grace with `mobs=0`. That is a consequence of having no
controllable player, not a plugin defect.

What the live run does confirm: the server starts cleanly with this jar, the
event reaches `services ready`, and `tps=20.00` with `mspt<0.7`.
