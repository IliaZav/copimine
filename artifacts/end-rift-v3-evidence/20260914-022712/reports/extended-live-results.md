# End Rift V3 extended live probes — 2026-09-14

These probes ran after the initial evidence checkpoint against the same
isolated local Paper runtime and the same verified production plugin hash
`C61543A81FF6DD2DE42DB41FC5E2E5487AD7669F1FFC4D130701B43A69D7D519`.

## AI and visual-contract probes

```text
LIVE_CURRENT_WAVE_AI_PASS wave=1 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=2 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=3 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=4 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=5 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=6 decision_marker=1 bounded=1
LIVE_CURRENT_WAVE_AI_PASS wave=7 decision_marker=1 bounded=1
LIVE_CURRENT_BOSS_AI_PASS target_selection=1 brain_decision=1 profile=AWAKENING
LIVE_CURRENT_TELEPORT_GUARD_PASS kind=wave outside=false
LIVE_CURRENT_TELEPORT_GUARD_PASS kind=boss outside=false
LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL teleport_guards=2
```

```text
CURRENT_VISUAL_CLIENTS_PASS count=5 core=8,68,-39
CURRENT_BOSS_VISUAL_CUES_PASS spells=distinct phase_updates=recorded
MOB_VISUAL_TOTAL_PASS
CURRENT_WAVE3_VISUAL_PASS portals=3 layers=FRAME,INNER,SHARD displays=12
CURRENT_WAVE4_VISUAL_PASS obelisk=real-block-state telegraph=present
CURRENT_MUSIC_CATALOG_PASS tracks=24 event_scope=manual_local_test
CURRENT_VISUAL_FIVE_PLAYER_PASS clients=5 wave_front=true portals=true obelisk=true boss_cues=true music_tracks=24 cleanup_requested=true
```

The five clients here are Mineflayer protocol clients used for server/client
contract signals. They are not native Minecraft windows. The probe explicitly
reported `NATIVE_CLIENT_SCREENSHOT=NOT_VERIFIED`.

## Scaling matrix

```text
players=2  obelisks=4/4 fireballs=1/8 pulse_radius=5 hard_cap=56 cleanup eventMobs=0
players=5  obelisks=4/4 fireballs=0/8 pulse_radius=5 hard_cap=56 cleanup eventMobs=0
players=10 obelisks=5/5 fireballs=1/8 pulse_radius=5 hard_cap=56 cleanup eventMobs=0
players=20 obelisks=6/6 fireballs=3/8 pulse_radius=5 hard_cap=56 cleanup eventMobs=0
```

All four load probes reported `staggered=true`, `arena_bound=true` and removed
their transient entities.

## Spells, sound catalog and player damage path

```text
LIVE_SPELL_MATRIX_PASS boss=7 mini=5 assigned=arrow_salvo,echo_pulse,rift_euphoria,rift_step,void_snare music_phases=24 cleanup=1 current_phases=6
LIVE_RUNE_WAIT_MUSIC_PASS state=START_RITUAL pads=2/2 track=copimine:end_rift/ritual_wait loop_seconds=22 client_sound_packet=true
LIVE_RUNE_WAIT_MUSIC_CLEANUP_PASS state=READY_FOR_PLAYERS pads=0/2 occupied=0 event-mobs=0 boss=none
```

The spell matrix exercised telegraph, flight, cast, impact and recovery for
boss and mini-boss spells. It did not alter projectile policy.

## Bossbar layout repair

The client HUD contract first failed because phase labels were drawn at
`frameY + 40` while the cast/status line was drawn at `y + 57`, causing the
two rows to overlap. The repair uses separate phase-label and cast-status
offsets, keeps the compact frame, and was verified with:

```text
python -m pytest -q tests/test_end_rift_client_hud_contract.py
2 passed
CopiMineClient Gradle test
BUILD SUCCESSFUL
```

This is a source/build verification only; native in-game bossbar rendering is
still explicitly **NOT VERIFIED** because no Minecraft client window was
exposed to Computer Use.

## Scene, gate and destructive cleanup probes

```text
GATE_POINTS_PASS bot=GateDeepQA pos1=8,68,-39 pos2=8,67,-42 source=server-side-crosshair
CORE_REMOVAL_GUI_PASS bot=CoreDeepQA state=UNCONFIGURED coreOverlay=false runes=0/0 restored_block=minecraft:crying_obsidian displays=0
End Rift local scene smoke passed.
PASS recovery startup logs current=latest.log rotated=8
```

The Core-removal probe restored the local Core in its `finally` block. A
follow-up `cmend status` showed `COLLECTING`, Core `8,68,-39`, zero event mobs,
zero boss and zero projectiles. Gate open/restore and portal-room checks passed.

## Remaining native gate

Computer Use still exposes no native app (`apps=[]`). Native models/textures,
UVs, bone animation, clipping, bossbar rendering, screenshots/video/audio and
client FPS remain **NOT VERIFIED**.
