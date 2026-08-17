# End Rift Event v5 local verification

Date: 2026-08-17
Checkout: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`
Branch: `codex/end-rift-event`
Remote: `https://github.com/IliaZav/copimine.git`

## Safety boundary

- The launcher and website were not modified.
- No production server, production world, production database, upload, or
  deployment was used.
- Paper ran only from `local-runtime/end-rift-server` on `127.0.0.1:25566` with
  RCON on `127.0.0.1:25576`.
- PostgreSQL was the isolated local database on `127.0.0.1:55433` with the
  local `copimine` database/schema. The event config required
  `environment: local`.

## Build and contract gates

`tests/RunEndRiftEventChecks.ps1` passed all gates:

- WorldCore, Artifacts, and End Event plugin builds passed.
- Fabric client build and tests passed.
- Resourcepack build passed: SHA-1
  `37CC65404740CBEB4BEA09924DE88B0DD80AB722`.
- Python End Rift contracts: `34 passed`.
- Pure domain tests: `EndEventDomainTest OK`, `BossThresholdPolicyTest OK`.
- Durable tests: `EventStateStoreTest OK`, `DepositJournalTest OK`,
  `EventLayoutStoreTest OK`.
- Local artifact SHA-256 values:

  - WorldCore: `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99`
  - Artifacts: `A2FEB49C31FA9FBA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE`
  - End Event: `B716B67916ABFF364E4ECDA68AEB005E156D31C3A6B445219FED6D9D6FC3D1DF`

The repository-wide `python -m pytest -q` was also attempted. Two unrelated
admin-web tests could not be collected because this Python environment does
not have `fastapi`; the End Rift runner intentionally uses source contracts
that do not import that web backend.

## Live local Paper path

Event id: `d0757099-0a6d-4da5-adfa-2ed8d797a8e9`
Core: `CopiMine 10,68,-38`
Required roster: `5`

Observed transitions and runtime evidence:

| Check | Evidence |
|---|---|
| Fresh bootstrap | Typed plugins loaded; state `UNCONFIGURED`; missing-Core test commands refused safely. |
| Core placement | Player command placed Core and five bounded pads; state became `COLLECTING`. |
| Resources and roster | Required resources reached `100/100`, `64/64`, `128/128`, `64/64`; pads reported `5/5`. |
| Countdown | `COLLECTING -> READY_FOR_PLAYERS -> COUNTDOWN`; exactly five UUIDs were frozen. |
| Waves | `WAVE_1` spawned 13, `WAVE_2` spawned 16, `WAVE_3` spawned 20 in the five-player scale. |
| Boss | Official boss spawned; `BOSS_PHASE_50` was recorded; final damage entered `FINAL_RITUAL`. |
| Final drain retry | With no eligible living players, repeated `FINAL_DRAIN_WAITING` was recorded and the one-shot drain was not consumed. A local helper entering the arena allowed the retry. |
| Final ritual | `FINAL_WAVE_STARTED ... count=20`; clearing event-owned final entities moved the event to `BOSS_FINISH`. |
| Victory gate | Only `boss kill simulate-victory confirm` for the official boss produced `BOSS_DEFEATED`, `END_UNLOCKED ... code=OPENED`, and `VICTORY`. |
| Durable rewards | One Return Stone world request and five UUID-specific shard requests were persisted. No idempotency key was duplicated. |
| Restart recovery | New JAR restart reported persistent `UNLOCKED`; recovery smoke reported `VICTORY_COMPLETE`, `endUnlocked=true`, `boss=none`, and no failed-closed bootstrap. |

## Local database evidence

For this event, the local PostgreSQL query returned:

- `rift_core_shard`: 5 rows, 5 distinct recipients, 5 distinct idempotency
  keys; one `DELIVERED` and four `PENDING_DELIVERY` rows, as expected for
  offline participants.
- `return_stone`: one `EVENT_WORLD` row, `DELIVERED`.
- Duplicate-idempotency query: zero rows.
- Pending-delivery table: one claimed shard and four pending shard deliveries.

## Known non-blocking local warnings

- Paper/Purpur reports the existing Bukkit `EntityRemoveEvent` API as
  deprecated; the plugin still builds and runs, but this should be migrated
  when the server API replacement is available.
- During the first five-client run, the Windows local Paper process stalled
  while synchronously writing playerdata and clients timed out. The thread
  dump pointed to Purpur `PlayerDataStorage`, not End Event code; the server
  recovered, the event continued, and the restart/recovery run was clean.
