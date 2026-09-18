# End Rift Wave 6 amplifier live evidence — 2026-09-18

This evidence was collected while validating the implementation now recorded
at Git commit
`2f8e8ed38775046b493eb7a6c0b95c0c627e1d2e` on branch
`codex/end-rift-event`. The live amplifier run itself predates the final
credential-removal-only commit, so the markers below are unchanged functional
evidence from the same source line. It is a Paper/Purpur local-staging result,
not a native-client visual acceptance result.

## Environment

- server: isolated local Paper on `25566`, RCON on `25576`
- resource pack: SHA-256
  `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`
- server plugin: SHA-256
  `c33e6b704439ad10091d2142ef3522ecbe9067e17c0965f79c4f75516f3e4cad`
- client mod: SHA-256
  `1173a108fa03bb3bf7338b40d230612a98324feed80a7fc379ba75a28aac9769`
- probe evidence log: `local-runtime/wave6-amplifier-live-20260918103134126.log`

## Exact client profile runtime load

The requested client profile was synchronized at
`D:\.minecraft\versions\ServerRP_copy_1`. It contains one current
`CopiMineClient-0.1.1.jar` with the client hash above, and
`CopiMineResourcePack.zip` with the resource-pack hash above. The pack is
listed once in the profile's active resource-pack option. The fresh client
runtime log is `local-runtime/client-direct-20260918111901.stdout.log`; it
records `copimineclient 0.1.1` and `file/CopiMineResourcePack.zip` in the
active resource manager and no CopiMine/mixin/Rift Guardian error.

This proves artifact installation and runtime loading only. The Computer Use
bridge did not expose the native Minecraft window, so it does not prove that
the model is visually correct in-game.

## Live markers

```text
LIVE_WAVE6_AMPLIFIER_CASTER_READY role=AMPLIFIER state=GUARDED_CASTING guards=3
LOCAL_TEST_HOOK drain=hold response=§aLOCAL_TEST_HOOK drain=hold.
LIVE_WAVE6_AMPLIFIER_EXPOSED_PASS guards=0 state=EXPOSED_CASTING
LIVE_WAVE6_AMPLIFIER_KILL_COMMAND response=Killed Заклинатель
LIVE_WAVE6_AMPLIFIER_LOSS_PASS removed=true before=1 after=0 caster_count=1
LOCAL_TEST_HOOK drain=release response=§aLOCAL_TEST_HOOK drain=release.
LIVE_WAVE6_AMPLIFIER_PASS before=1 after=0 projectile_delta=1 cooldown_reduced=true duration_reduced_after_loss=true scheduler_turn_consumed=false
```

The Paper log also recorded:

```text
WAVE6_LEGACY_WAVE7_ARTIFACTS_PURGED reason=wave6-start displays=1 blocks=0
WAVE6_LEGACY_WAVE3_PORTALS_PURGED reason=wave6-start displays=0
WAVE6_RITUAL_PRISONER_CAPTURED radius=1.25
WAVE6_RITUAL_ABILITY amplifier_count=1 successful_drains=0 effective_projectiles=4 effective_intensity=1 cooldown_ms=10725
WAVE6_RITUAL_ZONE_TELEGRAPH amplifier_count=1 duration_ms=5150
WAVE6_RITUAL_ABILITY amplifier_count=0 successful_drains=0 effective_projectiles=3 effective_intensity=0 cooldown_ms=11000
WAVE6_RITUAL_ZONE_TELEGRAPH amplifier_count=0 duration_ms=5000
```

After the plugin was rebuilt from the current head and the isolated server was
restarted, the bootstrap cleanup was also observed:

```text
WAVE6_LEGACY_WAVE7_ARTIFACTS_PURGED reason=bootstrap-non-wave7 displays=0 blocks=0
WAVE6_LEGACY_WAVE3_PORTALS_PURGED reason=bootstrap-non-wave7 displays=0
```

The post-restart `/cmend status` was `state=COLLECTING`, `wave=0`,
`event-mobs=0`, `boss=none`, and `occupied=0`.

Cleanup returned `event-mobs=0`, `boss=none`, and no transient objective
visuals. The local drain-hold command is gated to the local/staging test
dispatcher and is reset during ritual cleanup; it is not a production balance
override.

## Native visual status

Computer Use reported `apps=[]` during this run, so there is no fresh exact-head
Minecraft screenshot or 15-second video to attach. Existing images under
`artifacts/end-rift-v3-evidence/` were not relabeled as current evidence.
`nativeMinecraftTestedSha` therefore remains `NOT VERIFIED` until the player
opens the supplied server in the real client and captures the model, prisoner
sphere, stale-wall cleanup, and boss visuals.
