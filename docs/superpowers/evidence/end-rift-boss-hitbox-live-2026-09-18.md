# End Rift boss hitbox live evidence — 2026-09-18

Status: `PASS` for the isolated local Paper boss-hitbox probe; native Minecraft
visual verification remains `NOT VERIFIED`.

## Provenance

- repository: `IliaZav/copimine`
- branch: `codex/end-rift-event`
- exact source HEAD: `042d351433cd8b2deb39142236b620a802b7a916`
- local server: Purpur, `127.0.0.1:25566`, RCON `127.0.0.1:25576`
- environment: `local-runtime` only; no production server was started
- test command:

  ```powershell
  powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftBossHitboxLive.ps1 `
    -BotName EndRiftHbQ -BotDurationSeconds 18 -TimeoutSeconds 90 `
    -EvidencePath .\local-runtime\boss-hitbox-live-20260918-exact-042d3514.log
  ```

- raw local log: `local-runtime/boss-hitbox-live-20260918-exact-042d3514.log`
- raw local log SHA-256: `200f19f5412becdb50e3270f1d26d041cd32afcd280057fbfecfc99664878115`
- resource-pack generator SHA-1 observed by the bot: `a04f7d1c93465cd0f79db6bad5c2b12c3f1ab6a6`

The first invocation before Paper startup failed closed with RCON connection
refused and wrote `LIVE_BOSS_HITBOX_PASS=NOT_VERIFIED`; it is not counted as a
pass. The server was then started by `tests/StartEndRiftLocal.ps1`, the exact
probe was rerun, and the local Paper process was stopped through RCON after
the passing run.

## Passing markers

```text
LIVE_BOSS_HITBOX_PROFILE_PASS parts=HEAD,CHEST,PELVIS,LEFT_UPPER_ARM,LEFT_FOREARM,RIGHT_UPPER_ARM,RIGHT_FOREARM,LEFT_LEG,LEFT_LEG,RIGHT_LEG,RIGHT_LEG proxies=11 generation=1162 tagged=true parent=aa22dbea-6b6c-4274-940b-6029e03a531b bounded=true
LIVE_BOSS_HITBOX_PROXY_REMOVAL_CONFIRMED removed=1edef603-5b65-4010-938d-161e362dc921 replacement_count=11 old_uuid_absent=true
LIVE_BOSS_HITBOX_SELF_HEAL_PASS recreated=true proxies=11 generation=1162 pdc=true
LIVE_BOSS_HITBOX_NO_DUPLICATE_PASS unique_proxy_ids=11 proxy_count=11
LIVE_BOSS_HITBOX_MELEE_PASS attacks=1 accepted=1 before=5000 after=4995 single_authority=true
LIVE_BOSS_HITBOX_MISS_PASS attacks=1 accepted=0 before=4995 after=4995 carrier_ray_validated=true
LIVE_BOSS_HITBOX_PROJECTILE_PASS projectile_events=1 before=4995 after=4994 uuid_deduped=true
LIVE_BOSS_HITBOX_INVULNERABILITY_PASS before=950 after=950 phase=last_seal accepted=0
LIVE_BOSS_HITBOX_CLEANUP_PASS proxies=0 boss=none first_cleanup=true
LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS proxies=0 second_cleanup=true
```

This is server/runtime evidence for the composite hitbox and authoritative
damage path. It does not prove that the client renderer places every visible
cube exactly over those envelopes; that still requires a fresh native
Minecraft screenshot at the same source/artifact identity.

