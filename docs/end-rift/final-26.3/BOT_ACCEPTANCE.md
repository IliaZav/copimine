# Bot acceptance — End Rift Waves 1–7

Source commit: `d44c8f51be00f3b6d911135f7722a155cbf20c13`. Machine-readable source: `BOT_ACCEPTANCE.json`.

## Result

**No real player/bot acceptance run was executed in this pass.** The required authenticated V4 signer is unavailable (`COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE` absent) and the pinned trust anchor has no approved isolated test override. AuthMe, V4 and ownership checks were not disabled. No random test keypair was generated.

The disposable Paper server and PostgreSQL are stopped. Canonical ports `25567` and `55434` have no listeners. Port `25566` is an unrelated process and was not used.

## Required matrix

| Players | Required scope | Status | Accepted hits | Wins / losses |
|---:|---|---|---:|---|
| 2 | W1–W7, boss handoff; one free fighter in W6 | BLOCKED | 0 | 0 / 0 |
| 5 | W1–W7, boss handoff | BLOCKED | 0 | 0 / 0 |
| 10 | W1–W7, boss handoff | BLOCKED | 0 | 0 / 0 |
| 11 | W1–W7, both anvil volleys | BLOCKED | 0 | 0 / 0 |
| 20 | W1–W7, both anvil volleys | BLOCKED | 0 | 0 / 0 |

Targeted boundary scenarios (3/4, 4/5, 8/9, 10/11, 19/20/21, one remaining live player, spectator/late join), second seed, repeat-2/repeat-10 leak checks, death/quit/reconnect and real restart recovery are **NOT RUN** for the same reason.

## Existing runner inventory

The repository already contains the live entry points listed in `BOT_ACCEPTANCE.json`, including two/five/ten-player runners, Wave 6 ritual, Wave 6/7 boundaries, mob combat, obelisk, spell matrix and recovery smoke. Their presence is not evidence that they ran.

## Rules kept

- Bot online is not a win; only accepted server damage/death/objective events count.
- No `/cmend wave clear`, `setHealth(0)`, direct counter increments or forced completion as acceptance.
- No hidden server coordinates for superhuman dodge.
- No god mode, creative/admin heal loop or anti-cheat bypass.
- Native visual/audio remains false.

## Safe next action

Provide the approved signer through its normal protected configuration, start the disposable runtime on loopback-only ports, then run the existing runners in order 2 → 5 → 10 → 11 → 20 plus targeted boundaries and recovery. Attach the actual logs, accepted-hit receipts, rosters, timings and exit codes before marking any row PASS.
