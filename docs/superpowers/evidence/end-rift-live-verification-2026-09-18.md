# End Rift live verification — 2026-09-18

Status: `INTERIM — RELEASE BLOCKED — native exact-head Minecraft NOT VERIFIED`.
This evidence record is deliberately split by execution layer.

## Provenance fields

- `sourceImplementationSha`: `042d351433cd8b2deb39142236b620a802b7a916`
- `testedArtifactBuildSha`: `82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4` (SHA-256 of `CopiMineEndEvent.jar`)
- `reportCommitSha`: `02a42f6bf944ce52370aa205686b8e150877c9f7` (`docs(end-rift): record exact-head hitbox closure`)
- `githubActionsHeadSha`: `a9de3e3091faf9cb348c93908403f850f2241cf8`
- `githubActionsSourceImplementationSha`: `042d351433cd8b2deb39142236b620a802b7a916`
- `nativeMinecraftTestedSha`: `NOT VERIFIED`
- repository: `IliaZav/copimine`
- branch: `codex/end-rift-event`
- pull request: `https://github.com/IliaZav/copimine/pull/3`

The artifact hash is separate from the Git source SHA. The final probe-only
commits did not change the server plugin byte stream; the local start script
verified the artifact hash before each live run.

The current documentation head has green GitHub Actions runs:

- push: [35309013913](https://github.com/IliaZav/copimine/actions/runs/35309013913)
- pull request: [35309017715](https://github.com/IliaZav/copimine/actions/runs/35309017715)

Both required jobs (`static-and-contract` and `java-plugins`) completed
successfully. Their End Rift diagnostic artifact digests are
`sha256:95ccc1092fa6656b3415bd1b1c7634e98f9b55ebc46a17e8e13b5eab3446db2d`
and
`sha256:2261384df51e5db63c363ad5e83352bedf809221897fa04539ea6afbfb010fa4`.

## Contract layer

Using the declared project environment at
`C:\Users\zavod\AppData\Local\Temp\copimine-end-rift-tests-20260916-py313\Scripts\python.exe`:

```text
python -m pytest -q tests
599 passed, 58 warnings in 13.10s
```

The system Python 3.14 attempt is recorded as an environment mismatch only:
it lacks `fastapi` and failed collection in two web tests before executing the
suite. It is not treated as a source failure.

## Current AI live probe

`tests/RunEndRiftAiPhasesLive.ps1` passed against the current source/artifact
pair. The probe verified all seven wave adapters, bounded target selection,
boss brain selection, all six boss phases, teleport containment, the complete
Wave 6 caster lifecycle, and fail-closed cleanup:

```text
LIVE_CURRENT_WAVE_AI_PASS wave=1..7
LIVE_CURRENT_BOSS_AI_PASS target_selection=1 brain_decision=1 profile=AWAKENING
LIVE_W6_CASTER_GUARDED_PASS casters=4 guards=12 passive=true target=none ownership=true
LIVE_W6_CASTER_EXPOSED_PASS caster_count=1 guards=0 passive=true target=none
LIVE_W6_CASTER_AWAKENED_PASS caster_count=1 native_ai=true target_allowed=true mixed_state=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=AWAKENING profile=true transition=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=HUNT profile=true transition=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=RIFT profile=true transition=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=OVERLOAD profile=true transition=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=RAGE profile=true transition=true
LIVE_CURRENT_BOSS_PHASE_PASS phase=LAST_SEAL profile=true transition=true
LIVE_CURRENT_TELEPORT_GUARD_PASS kind=wave outside=false
LIVE_CURRENT_TELEPORT_GUARD_PASS kind=boss outside=false
LIVE_CURRENT_AI_PASS waves=1,2,3,4,5,6,7 boss_phases_verified=6 caster_lifecycle_verified=3 teleport_guards=2
LIVE_CURRENT_CLEANUP_PASS event_mobs=0 mobile=0 boss=false casters=0 guards=0 visuals=0
```

## Wave 6 live probe

Passing exact-head detailed run:
`local-runtime/wave6-ritual-live-20260918065715244.log`.

```text
LIVE_WAVE6_CASTER_GUARDED_PASS casters=4 guards=12 native_ai=false targets=0 ownership=true
LIVE_WAVE6_WAITING_FOR_PRISONER_PASS outside_seal_verified=true auto_capture=false
LIVE_WAVE6_CAPTURE_ORDER_PASS
LIVE_WAVE6_RESTART_RECOVERY_PASS rehydrated=true phase=READY_FOR_PLAYERS wave=6 casters=4 guards=12 visual_displays=1 prisoner_preserved=true capture_replayed=false overdue_drain_replayed=True log_rotated=True
LIVE_WAVE6_DRAIN_19_5S_PASS health=5 unchanged=true
LIVE_WAVE6_DRAIN_20S_PASS health_before=5 health_after=3 damage=2
LIVE_WAVE6_EXTERNAL_DAMAGE_IMMUNITY_PASS health=3 cases=melee,projectile,generic,fall
LIVE_WAVE6_DRAIN_FLOOR_PASS remaining=1 intensity_not_increased=true
LIVE_WAVE6_EXTERNAL_DAMAGE_IMMUNITY_PASS health=1 cases=melee,projectile,generic,fall
LIVE_WAVE6_PROJECTILE_ORIGIN_PASS sphere_origin=true projectile_spawn=true max_distance=0.25 caster_origin=false
LIVE_WAVE6_ZONE_EFFECTS_PASS target=EndRiftWave6D wither=true slowness=true reverse_expected=true poison=false prisoner_zone_effects=false
LIVE_WAVE6_ABILITY_ROLES_PASS projectile=server sphere zone=4x4 reverse=server control_swap=server
LIVE_WAVE6_FREE_TARGET_CONTROL_PASS reverse=true swap=true prisoner_excluded=true reverse_swap_mutex=true
LIVE_WAVE6_CASTER_EXPOSED_PASS caster_count=1 guards_removed=3 native_ai=false target=none ownership=true
LIVE_WAVE6_CASTER_AWAKENED_PASS caster_count=1 native_ai=true target_allowed=true mixed_state=true ownership=true
LIVE_WAVE6_COMPLETION_CLEANUP_PASS sphere=false beams=0 zones=0 controls=0 prisoner_released=true prisoner_tag_removed=true transient_entities=0
LIVE_WAVE6_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false casters=0 guards=0 visuals=0
```

A shorter client-window attempt is retained at
`local-runtime/wave6-ritual-live-20260918064931171.log`. It timed out at the
zone-effect observation but emitted `LIVE_WAVE6_CLEANUP_ZERO_STATE_PASS`; it
is reported as a failed attempt, not hidden. The exact-head rerun above used a
600-second client window and passed.

## Wave 7 live boundary probe

The current exact-head structured report is
`artifacts/end-rift-diagnostics/20260918-064101-74356366d22b/`. It contains
`summary.json`, `report.md`, `metadata.json`, the complete
`end-rift-events.jsonl` stream, artifact hashes, and the step log.

```text
LIVE_WAVE7_PLAYER_DAMAGE_LEDGER_PASS players=2 positive_attackers=2
LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0 phase_unchanged=true
LIVE_WAVE7_COMMAND_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0
LIVE_WAVE7_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false visuals=0 barriers=0
END_RIFT_DIAGNOSTIC_REPORT_PASS
```

Machine-readable result fields include `result=PASS`,
`livePaperResult=PASS`, `diagnosticReportResult=PASS`,
`diagnosticEventsDropped=0`, `writeFailures=0`, empty entity/task/control/
projectile leak arrays, and empty `wave7RestoreMismatches`.

## Exact-head boss hitbox live probe

The exact current HEAD was exercised by
`tests/RunEndRiftBossHitboxLive.ps1` against isolated local Paper. The
committed evidence summary is
`docs/superpowers/evidence/end-rift-boss-hitbox-live-2026-09-18.md`; the
ignored raw log has SHA-256
`200f19f5412becdb50e3270f1d26d041cd32afcd280057fbfecfc99664878115`.

```text
LIVE_BOSS_HITBOX_PROFILE_PASS parts=HEAD,CHEST,PELVIS,LEFT_UPPER_ARM,LEFT_FOREARM,RIGHT_UPPER_ARM,RIGHT_FOREARM,LEFT_LEG,LEFT_LEG,RIGHT_LEG,RIGHT_LEG proxies=11 generation=1162 tagged=true parent=aa22dbea-6b6c-4274-940b-6029e03a531b bounded=true
LIVE_BOSS_HITBOX_PROXY_REMOVAL_CONFIRMED old_uuid_absent=true replacement_count=11
LIVE_BOSS_HITBOX_MELEE_PASS attacks=1 accepted=1 before=5000 after=4995 single_authority=true
LIVE_BOSS_HITBOX_MISS_PASS attacks=1 accepted=0 before=4995 after=4995 carrier_ray_validated=true
LIVE_BOSS_HITBOX_PROJECTILE_PASS projectile_events=1 before=4995 after=4994 uuid_deduped=true
LIVE_BOSS_HITBOX_INVULNERABILITY_PASS before=950 after=950 phase=last_seal accepted=0
LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS proxies=0 second_cleanup=true
```

## Artifact hashes

- `CopiMineEndEvent.jar`: `82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4`
- `CopiMineClient.jar`: `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce`
- `CopiMineResourcePack.zip`: `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9`
- `Purpur server jar`: `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c`

## Native visual boundary

Native exact-head Minecraft verification is `NOT VERIFIED`. The Computer Use
bridge exposed `apps=[]` and only browser surfaces, so there is no honest
current screenshot or video for this SHA. Existing native PNG/MP4 files are
preserved but are not claimed as exact-head proof. Static model-board/UV
evidence is also not a substitute for a native render.

The exact manual/native capture procedure is committed at
`docs/superpowers/evidence/end-rift-native-capture-procedure.md`. It is a
runbook, not visual evidence: it does not change the current `NOT VERIFIED`
result until fresh screenshots and the continuous flight recording are taken
against the recorded source and artifact hashes.

## Release decision

Server-side implementation and live Paper gates are evidenced as passed. This
record is not a visual release: the current GitHub Actions head and native
Minecraft capture fields must be filled after those gates are actually
observed.
