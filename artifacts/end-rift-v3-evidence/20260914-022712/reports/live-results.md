# End Rift V3 live results — 2026-09-14

All live commands ran against the isolated local Purpur/Paper 1.21.1 runtime
on port `25566`. The production plugin artifact used by that runtime was
`C61543A81FF6DD2DE42DB41FC5E2E5487AD7669F1FFC4D130701B43A69D7D519`.

## Targeted Paper probes

| Probe | Result |
|---|---|
| Mob combat | `LIVE_MOB_COMBAT_PASS moved=2020 attacks=69 player_hurt=62 player_damage_applied=65 ai_targets=42 ai_paths=25` |
| Boss real HP | `before=2000 after=1720 delta=280`, cleanup `event-mobs=0 boss=none` |
| Shield | `shield_before=950 shield_after=950; vulnerable_before=3950 vulnerable_after=3942; restored_before=950 restored_after=950` |
| Wave 6 | `rings=3 radii=8,14,19 visual_displays=240 visual_points=64,80,96 leash_policy=true player_containment=true` |
| Wave 7 | `chambers=2 cells=480 visual_displays=120 barrier=13,68,-39 collision=true` |
| Wave 7 cleanup | `blocks_restored=true displays_removed=true transient_entities=0` |
| Obelisks | `players=2 obelisks=4 active_before=4 reflected_hits=3 first_target_hp=2 second_target_hp=1 destroyed=true pulse_radius=5 fireball_cap=1 real_blocks=true` |
| Combat Trace | `wave_traces=245 player_wave=58 exact_wave=55 boss_traces=15` |
| Five-player performance | `duration=30s samples=5 tps_avg=19.65 mspt_max=3.31 ping_max=25` |

The strict same-tick five-player damage probe used a synchronized burst and
continued regular attack cadence:

```text
LIVE_BOSS_MULTIPLAYER_REAL_HEALTH_PASS players=5 independent_attackers=5 events=495 before=5000 after=3000.8586 summed_final_damage=1999.14377391338560 expected=3000.85622608661440 health_delta=0.00237391338560 same_tick_event_groups=97
LIVE_BOSS_MULTIPLAYER_DAMAGE_CLEANUP_PASS boss=none
```

The small `health_delta` is the documented float write-rounding accumulated by
Paper entity health. All 495 accepted transactions were committed to real
boss HP, and the probe required at least 100 accepted events.

## Official end-to-end run

The two-player official run completed the current event from setup through
victory:

```text
event=32cb2f78-0932-47ee-bf4f-edf330568321
CURRENT_RITUAL_PASS players=2
CURRENT_WAVE_PASS wave=1 objective=RIFT_CARRIERS
CURRENT_WAVE_PASS wave=2 objective=RIFT_HUNT
CURRENT_WAVE_PASS wave=3 objective=RIFT_GATES
CURRENT_WAVE_PASS wave=4 objective=OBELISK_ASSAULT
CURRENT_WAVE_PASS wave=5 objective=BLACK_FOG
CURRENT_WAVE_PASS wave=6 objective=COLLAPSE_RINGS
CURRENT_WAVE_PASS wave=7 objective=REALITY_SPLIT
CURRENT_OFFICIAL_PASS waves=1,2,3,4,5,6,7 stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL victory=true
final state=UNLOCKED wave=0 event-mobs=0 boss=none victory=VICTORY_COMPLETE
```

The recovery smoke also passed after reading the active log and rotated gzip
logs. It verified a persisted phase, a forced unlock transition, and startup
state recovery.

## Combat Trace sample

The current trace contains the required event-level distinction. An accepted
boss hit recorded:

```text
raw=8.000 final=8.000 cancelled_before=false cancelled_after=true
health_before=4892.000 expected_health=4884.000 health_after=4884.000
health_next_tick=4884.000 accepted=true authority=REAL_ENTITY_HEALTH diagnosis=APPLIED
```

An explicit rejection records unchanged health and a reason; for example the
shield probe records `reason=permanent-guardian-shield` and unchanged HP. The
trace is opt-in and bounded; normal gameplay does not flood the log.

## Automated gate

The latest focused contracts for the live multiplayer and recovery harnesses
passed `4 passed`. The complete End Rift gate passed `68 passed in 1.11s`,
including Java policy tests, persistence/recovery fixtures, client build,
resource-pack build and plugin build. The five-player contract now rejects a
probe that emits fewer than 100 accepted boss events.

## What remains unverified

Native Minecraft rendering and input were not available through Computer Use.
Therefore this evidence does not claim native visual PASS for models,
textures, bossbar artwork, animations, tentacle bones, obelisk UV/orientation,
portal/core placement, room visibility or barrier appearance. See the native
QA note in the parent `client/` directory.
