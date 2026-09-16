# END RIFT FINAL VERIFICATION

Verification record for the CopiMine End Rift event work on 2026-09-16.

## Repository

- Repository: [IliaZav/copimine](https://github.com/IliaZav/copimine)
- Branch: `codex/end-rift-event`
- Base SHA: `79781d8c8be3827078a77972ccba851df4eb819f`
- Source implementation SHA: `f7701b5b`
- Pull request: [#3](https://github.com/IliaZav/copimine/pull/3)
- Deployment: local/staging validation only; no production deployment was performed

## Change verified in this record

Wave 6 Ritual Casters intentionally use server-controlled behavior while the
Ritual Guards are alive. Their native mob AI is disabled during channeling,
they have no player target, and the event controller owns the Ritual Sphere.
The live AI probe previously treated `aiEnabled == mobile` as a universal
invariant. That was an incorrect harness assertion and made a valid Wave 6
state fail.

The fix keeps the original diagnostic field order for existing parsers and
appends role-aware counters:

- `ritualCasters`
- `ritualCastersPassive`
- `ritualCastersTargeted`
- `ritualGuards`

The Wave 6 probe now asserts that every caster is passive and has no target,
then expects `aiEnabled == mobile - ritualCasters`. Waves 1–5 and 7, and the
boss diagnostic, retain the stricter `aiEnabled == mobile` check. A Python
regression contract covers the role-aware probe and Java diagnostic fields.

## Runtime evidence

All runtime checks below used the isolated local Paper server, local
PostgreSQL, the checked-out `codex/end-rift-event` source, and local resource
pack/client artifacts. The live server and disposable clients were stopped by
their test cleanup handlers after each probe.

### Passed on the current source build

```text
LIVE_CURRENT_AI_DIAGNOSTICS label=wave-6 mobile=16 enabled=12 ritual_casters=4 passive_casters=4 caster_targets=0 guards=12 expected_enabled=12 outside=0 on_core=0
LIVE_CURRENT_WAVE_AI_PASS wave=6
LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL teleport_guards=2

LIVE_MOB_COMBAT_PASS moved=1628 attacks=94 player_hurt=43 player_damage_applied=76 ai_targets=49 ai_paths=24

PASS recovery state=UNLOCKED|UNCONFIGURED|COLLECTING|READY_FOR_PLAYERS
PASS recovery endUnlocked=true
PASS recovery durable event-state phase and end-unlocked=true
PASS recovery persisted phase and forced ready transition
PASS recovery startup logs current=latest.log rotated=8
End Rift durable recovery smoke passed.
```

Boss and multiplayer runtime evidence already recorded for this source line:

```text
LIVE_BOSS_REAL_HEALTH_PASS boss=... status=hp-5000/5000 physical-5000/5000 attribute-unclamped=true current-health-marker=true legacy-virtual-marker=false
LIVE_BOSS_HITBOX_PROFILE_PASS parts=HEAD,CHEST,PELVIS,LEFT_UPPER_ARM,LEFT_FOREARM,RIGHT_UPPER_ARM,RIGHT_FOREARM,LEFT_LEG,LEFT_LEG,RIGHT_LEG,RIGHT_LEG proxies=11 generation=1162 tagged=true bounded=true
LIVE_BOSS_MULTIPLAYER_REAL_HEALTH_PASS players=2 independent_attackers=2 events=87 before=5000 after=4709.7637 summed_final_damage=290.23903036117555 health_delta=0.00273036117555 same_tick_event_groups=43
LIVE_BOSS_MULTIPLAYER_DAMAGE_CLEANUP_PASS boss=none
LIVE_RIFT_WAVE4_OBELISK_PASS players=2 obelisks=4 active_before=4 reflected_hits=3 first_target_hp=2 second_target_hp=1 destroyed=true pulse_radius=5 fireball_cap=1 real_blocks=true
```

Wave 6 and Wave 7 geometry checks reached the physical assertions:

```text
LIVE_WAVE6_RITUAL_SPHERE_PASS casters=4 guards=12 drain_interval_ms=20000 drain_hp=2 health_floor=1 visual_displays=1 legacy_rings=false
LIVE_WAVE7_ONE_BLOCK_WALL_PASS chambers=2 cells=152 columns=38 visual_displays=38 wall_material=barrier barrier=13,69,-39 collision=true connected=raster
LIVE_WAVE7_RESTART_RECOVERY_PASS rehydrated=true collision=true visible=true barrier=13,69,-39 journal_replayed=true
```

### Not passed / not claimed as complete

The disposable two-client Wave 7 natural-completion probe was run twice, with
45/120-second and 240/180-second client/marker windows. Both runs reached the
Sphere, one-block wall, and restart-rehydration markers, but timed out waiting
for `END_RIFT_CHAMBERS_COMPLETE.*chambers=2`. The final run observed 14 live
event mobs after 240 seconds. Both clients emitted attack packets; the Paper
log recorded accepted player-to-mob damage only for the second client's UUID.
The probe therefore remains an open runtime investigation and is not reported
as a pass. Its cleanup handler removed the disposable entities and stopped its
temporary Paper process.

Native Minecraft GUI visual acceptance for the exact final source SHA is also
`NOT VERIFIED IN GAME`: the computer bridge returned no native application
window (`apps=[]`) during this run. The tracked model board is static source
evidence, not a substitute for a current native client capture.

## Automated validation

Run from the task-scoped Python 3.13 environment:

```text
python -m pytest -q tests                         # 508 passed, 58 warnings
.\tests\RunCopiMineValidators.ps1               # 659/659 passed
.\tests\RunEndRiftEventChecks.ps1              # 149 passed, 53 warnings; Java/recovery gates passed
git diff --check                                  # passed
```

The full test run uses the declared project dependencies in
`C:\Users\zavod\AppData\Local\Temp\copimine-end-rift-tests-20260916-py313`.
The system Python 3.14 environment is not used as a project-failure signal:
it lacks the declared FastAPI dependency and has no compatible pinned
`psycopg-binary` wheel for that interpreter.

Security checks completed for the same source line:

- `pip-audit -r admin-web/requirements.txt --strict`: no known vulnerabilities
- `security_selftest.py`: passed
- `backend_security_regression_test.py`: passed; its simulated audit-sink
  stack traces are expected test output

## Artifact identity

The local source/staged artifacts used during verification were:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| End Rift server plugin | 786,613 | `968fa18decbc0aa71548f46b13a1dec198cb79bb93175ca14187b9bc575d7e76` |
| Fabric client JAR | 9,450,203 | `4efe18fed6f7877e094575d636e8d5bc76bbb67cfe62e57b5a436122113fa298` |
| Modpack ZIP | 21,651,501 | `8c582afbd4f19267bf8645ec304f5c83d9ecbe5465f1351818e095d2a1ed3b7d` |
| Resource pack ZIP | 24,150,260 | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |

The resource-pack HTTP endpoint returned SHA-1
`a04f7d1c93465cd0f79db6bad5c2b12c3f1ab6a6`.

Static model evidence is tracked at
`artifacts/end-rift-v3-evidence/end-rift-mob-model-board-20260916.png`; it
covers the dedicated ordinary, elite, guardian, ritual-guard, and Wave 6
ritual-caster roles. It is explicitly labeled static because the native GUI
capture gate above is still open.

## Changed files in this verification commit

- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- `tests/RunEndRiftAiPhasesLive.ps1`
- `tests/test_wave6_ritual_caster_behavior_contract.py`
- `docs/end-rift-validation.md`

## Final handoff rule

Do not call this event visually released until the native Minecraft capture
and the Wave 7 natural-completion probe have both been rerun against the exact
GitHub commit reported in the final handoff. A failed, skipped, or unverified
gate stays `NOT VERIFIED IN GAME`.
