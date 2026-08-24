# End Rift completion — local runtime evidence

Date: 2026-08-24
Checkout: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`
Branch: `codex/end-rift-event`
Remote: `https://github.com/IliaZav/copimine.git`

## Safety boundary

- Verification used only the linked worktree and the isolated Paper copy at
  `local-runtime\end-rift-server`.
- Paper used loopback `127.0.0.1:25566`, RCON `127.0.0.1:25576`, and the
  disposable PostgreSQL cluster at `127.0.0.1:55433`.
- The resource-pack HTTP server was bound to `127.0.0.1:8092`.
- Launcher, website, production worlds, production databases, and deployment
  paths were not changed or contacted.
- AuthMe was temporarily moved out of this local runtime only so offline
  protocol test clients could connect; the original JAR is preserved in the
  local recovery directory.

## Fresh source/build gate

`tests\RunEndRiftEventChecks.ps1` completed with:

- WorldCore, Artifacts, End Event, and Fabric client builds successful.
- Resource pack build successful, SHA-1
  `3578cc2c91aa8d4307c607a60b9fefe5edd2b8c0`.
- Python End Rift contracts: `105 passed, 14 warnings`.
- Pure domain tests: `EndEventDomainTest`, `BossThresholdPolicyTest`,
  `EndRiftAiPolicyTest`, `ResourceProgressFormatterTest`, and
  `GateOpeningPlanTest` all `OK`.
- Durable tests: `EventStateStoreTest`, `DepositJournalTest`, and
  `EventLayoutStoreTest` all `OK`.
- The runner now includes the Creative full-run, boss-bar, and client texture
  quality contracts instead of silently omitting them.

Artifact hashes from the same gate:

| Artifact | SHA-256 |
| --- | --- |
| WorldCore | `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99` |
| Artifacts | `A2FEB49C31FA9BFA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE` |
| End Event | `059E671FBDCC244DDE94198ED7C413F334303748CC79628F7E7C404842E191AC` |
| Fabric client | `0CD49DBE1517EB2B7BC04B40990C707AF9AEF96F4E67E3432F62F4AB955AD9D6` |
| Resource pack ZIP | `4B47A89C7C686408D83C4D5669F9BFE3A687A2BBB811EE900629015B22E7616D` |

The installed local End Event JAR has the same SHA-256 as the source build.

## Paper state and physical visuals

Final local RCON status after the complete disposable runs:

```text
state=READY_FOR_PLAYERS
core=CopiMine 8,69,-31 requiredPlayers=2
arena=CopiMine [-12,66,-51]..[28,72,-11] volume=11767 gate=OPENED
resources=128/128, 64/64, 64/64, 100/100
pads=0/2 roster=0
visuals=coreOverlay=true coreModel=830002 runes=2/2 occupied=0
wave=0 event-mobs=0 boss=none half=false final=false endUnlocked=false victory=NONE
```

The display entity inspection reports the charged Core carrier with
`custom_model_data=830002`, and two idle rune carriers with
`custom_model_data=830003`. The carrier is Paper only; the target stone block
and vanilla block textures remain unchanged.

## Rune regression reproduced in the actual game

Two local protocol players were placed on the exact pad coordinates. RCON then
reported `pads=2/2`, `occupied=2`; both rune ItemDisplays changed to
`custom_model_data=830005` / `end_event_pad_occupied`. After the players were
teleported away, status returned to `occupied=0` and both carriers returned to
`830003` / `end_event_pad`.

Fresh Fabric client screenshots:

- Charged Core on its real target block:
  `CopiMineClient\run\screenshots\2026-08-24_09.19.09.png`
- Occupied rune, green model, with the player standing on its top surface:
  `CopiMineClient\run\screenshots\2026-08-24_09.20.35.png`
- Same rune after the player leaves, idle purple/cyan model on the floor:
  `CopiMineClient\run\screenshots\2026-08-24_09.20.54.png`
- Core-removal confirmation GUI opened by breaking the protected Core as OP:
  `CopiMineClient\run\screenshots\2026-08-24_09.26.11.png`

The textures in the source pack are 32x32 for Core, charged Core, idle rune,
occupied rune, and the item carriers. The display locations are anchored at
the exact block center/top-face coordinates; no floating stand or vanilla
material replacement is involved.

## Creative full-run on Paper

The local command `/cmend test run creative` was executed by the actual Fabric
client in Creative. It is guarded by `environment=local`, requires Creative,
uses a generation guard, and does not change the official phase, official
reward roster, End unlock, or rewards.

The authoritative log is
`local-runtime\end-rift-paper-start.out.log`. The latest successful run
contains these direct markers:

```text
CREATIVE_TEST_START ... gamemode=CREATIVE official_phase=READY_FOR_PLAYERS official_roster=0
CREATIVE_TEST_CORE ... coreOverlay=true coreModel=830002 runes=2/2 occupied=0
CREATIVE_TEST_RESOURCES ... charged=true
CREATIVE_TEST_RUNES ... runes=2/2 occupied=0
CREATIVE_TEST_WAVE_1 ... live=10 composition={enderman=4, spider=6}
CREATIVE_TEST_INTERMISSION_1 ... live=0
CREATIVE_TEST_WAVE_2 ... live=13 composition={enderman=6, spider=5, shulker=2}
CREATIVE_TEST_INTERMISSION_2 ... live=0
CREATIVE_TEST_WAVE_3 ... live=13 ... miniBossSpells=[rift_step, void_snare, echo_pulse]
CREATIVE_TEST_BOSS_ACTIVE ... health=1200.0 maxHealth=1200.0 bossbar=true
CREATIVE_TEST_HALF ... threshold=600.0 health=600.0
CREATIVE_TEST_CONTROL ... spell=will_distortion flight_expected=true
CREATIVE_TEST_FINAL_DRAIN ... threshold=120.0 finalHealth=200.0 drainFraction=0.6
CREATIVE_TEST_FINAL_WAVE ... live=16 composition={enderman=6, shulker=2, spider=8}
CREATIVE_TEST_BOSS_FINISH ... finalWaveLive=1
CREATIVE_TEST_CLEANUP ... success=true official_phase_unchanged=true official_roster=0
CREATIVE_TEST_COMPLETE ... success=true official_phase=READY_FOR_PLAYERS official_roster=0 endUnlocked=false
```

The same log records visible telegraph and flight markers for all three
mini-boss spells and the five boss spells. Spider stat markers report
`health=26.0 attack=4.0`, which is the requested vanilla spider value plus
`+10` health and `+2` damage. Containment watchdog markers repeatedly return
owned wave mobs and the boss to `CopiMine 8,70,-31`; no vertical escape path is
available.

Real client observations from the run:

- `CopiMineClient\run\screenshots\2026-08-24_09.37.55.png` shows an event
  Enderman texture in the live wave, the real Core and floor runes, and the
  Russian Creative-run message.
- `CopiMineClient\run\screenshots\2026-08-24_09.37.16.png` shows the live
  Rift Guardian texture, BossBar `1182/1200`, spell-flight particles, and the
  Russian spell telegraph.
- The resource-pack and renderer contracts additionally validate the spider,
  shulker, elite Enderman, and UUID-scoped guardian bindings. No global vanilla
  entity renderer override is used.

## Gate runtime evidence

Configured points were `CopiMine 20,70,-31` and `CopiMine 21,72,-31`, an
inclusive six-block volume. Paper logged deterministic top-down removal:

```text
END_EVENT_GATE_OPENING ... layers=3 ticksPerLayer=1 volume=6
END_EVENT_GATE_LAYER ... y=72 removed=2 index=1/3
END_EVENT_GATE_LAYER ... y=71 removed=2 index=2/3
END_EVENT_GATE_LAYER ... y=70 removed=2 index=3/3
END_EVENT_GATE_OPENED ... progress=6/6
```

The restart interruption check caught durable `OPENING` at `progress=2/6`,
stopped Paper, and verified after bootstrap that the complete six-block
snapshot was restored with `RESTORED_ON_BOOT`. Reopening finished at `OPENED`
and a repeated open returned the already-open result without mutating blocks.
The recovery sequence is retained in the local backup directory
`local-runtime\end-rift-completion-20260824-090445`.
