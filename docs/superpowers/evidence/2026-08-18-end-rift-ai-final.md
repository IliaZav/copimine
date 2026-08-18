# End Rift AI and final local verification

Date: 2026-08-18
Checkout: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`
Branch: `codex/end-rift-event`
Remote: `https://github.com/IliaZav/copimine.git`

## Safety boundary

- Launcher and website sources were not changed.
- No production server, world, player data, production database, upload, or
  deployment was used.
- Paper verification used only disposable directories below this worktree's
  `local-runtime` directory and loopback RCON.
- The PostgreSQL connection used by the event checks was the isolated local
  database on `127.0.0.1:55433`; no production database was contacted.

## AI implemented

- Boss max health is `1200.0`; the half-health boundary is `600.0`, the final
  threshold is `120.0`, and the final-drain phase leaves the boss at `200.0`.
- The boss selects a fair target from living event participants, avoids its
  current/recent target when another eligible player exists, rotates choices,
  telegraphs every cast, changes its spell pool at 50% and 10%, and leashes or
  teleports back to the arena when displaced.
- Boss spells are `void_blast`, `rift_projectile`, `void_mark`,
  `summon_servants`, and the 50% control-distortion spell
  `will_distortion`. Projectile count and lifetime are bounded.
- Elite Endermen in waves are stable mini-bosses: each entity receives one
  spell for its whole lifetime. The three spell profiles are `rift_step`
  (teleport strike with slow), `void_snare` (delayed mark/trap with slow), and
  `echo_pulse` (area pulse with knockback). Each cast has a visible telegraph.
- Endermites keep their vanilla texture and receive the configured `+10.0`
  max-health and `+2.0` attack-damage bonuses.

## Source and test gate

`tests/RunEndRiftEventChecks.ps1` completed successfully:

- WorldCore, Artifacts, End Event, Fabric client, and resourcepack builds
  passed.
- Python End Rift contracts: `69 passed, 9 warnings`.
- Pure domain tests: `EndEventDomainTest OK`, `BossThresholdPolicyTest OK`,
  `EndRiftAiPolicyTest OK`.
- Durable tests: `EventStateStoreTest OK`, `DepositJournalTest OK`,
  `EventLayoutStoreTest OK`.
- The new persistence regression starts eight concurrent saves. It failed
  before the fix because atomic temp-file replacement raced; serializing the
  store save operation now makes all concurrent saves pass.
- Resourcepack SHA-1: `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.
- Artifact SHA-256 values:

  - WorldCore: `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99`
  - Artifacts: `A2FEB49C31FA9BFA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE`
  - End Event: `4ADAE897C41E79DA399C8A5DBD4599E76CD03323DE13C1A782A97AA0CF636EF2`
  - Fabric client: `C1299D204C0E1A6B13885DECB8989B75B4EBCAA50C718F687EDCE4FFA85DE1C3`

## Live Paper evidence

Official accelerated run:

- Server directory: `local-runtime/end-rift-final-check-20260818`
- Event id: `d59ad0ef-6598-44ad-8cd2-ca1875d3b262`
- The log records `FINAL_DRAIN -> FINAL_WAVE`, final-wave defeat,
  `BOSS_DEFEATED`, `BOSS_FINISH -> VICTORY_PROCESSING`, rewards for the
  frozen roster, victory music, and the final `UNLOCKED` state. See
  `final-check-paper.out.log` lines 430, 676, and 685-693.
- The same run observed the boss at 1200 HP, then forced checks at 600 HP and
  200 HP to verify the half and final thresholds. The boss spawned with a
  real Paper entity UUID and only the official boss-death path completed the
  victory saga.
- AI-focused Paper run:
  `local-runtime/end-rift-ai-check-20260818/ai-check-paper-elites-db.out.log`
  records target rotation, boss telegraph/cast, stable mini-boss casts for
  `rift_step`, `void_snare`, and `echo_pulse`, plus boss and wave leash events.
- Restart after victory returned `state=UNLOCKED` and
  `victory=VICTORY_COMPLETE`; Artifacts reported `bridge ready=true
  postgres=true`. No `RECOVERY_REQUIRED` or `NoClassDefFoundError` appeared
  in the restart log.
