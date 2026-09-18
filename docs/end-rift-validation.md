# End Rift validation status

`INTERIM — RELEASE BLOCKED — native exact-head Minecraft NOT VERIFIED`

This is the current validation record for the CopiMine End Rift work on
2026-09-18. It separates source/contract checks, live Paper checks, artifact
identity, CI, and native-client evidence. A passing server-side gate is not a
visual release claim.

## Provenance

| Field | Value |
| --- | --- |
| Repository | [IliaZav/copimine](https://github.com/IliaZav/copimine) |
| Branch | `codex/end-rift-event` |
| Pull request | [#3](https://github.com/IliaZav/copimine/pull/3) |
| `sourceImplementationSha` | `e35224fd3ad7b9886d95f71873e7286ba19b2a1c` (`fix(end-rift): restore wave 6 visuals and amplifier runtime`) |
| `testedArtifactBuildSha` | `4d7386e47e8728538fbc185d14cec2a654d4c4f19e4ab59d1217bc6977c50e36` (SHA-256 of `CopiMineEndEvent.jar`, not a Git SHA) |
| `reportCommitSha` | `e35224fd3ad7b9886d95f71873e7286ba19b2a1c` (functional source/artifact evidence commit) |
| `githubActionsHeadSha` | `PENDING — pushed current head; verify the resulting workflow run before release` |
| `githubActionsSourceImplementationSha` | `PENDING — current push` |
| `nativeMinecraftTestedSha` | `NOT VERIFIED` |
| Deployment | Isolated local Paper/PostgreSQL validation only; no production deployment |

The current source HEAD contains the Wave 6 amplifier policy, stale-scene
cleanup, prisoner anchor/VFX corrections, and the client entity-renderer
descriptor fix. The server plugin artifact is byte-identical to the recorded
`testedArtifactBuildSha`.

## Current exact-head results

### Contract and static checks

The project Python 3.13 environment
`D:\Desktop\Copimine\copimine-main\.venv313\Scripts\python.exe`
reported:

```text
615 passed, 58 warnings in 18.17s
```

The system Python 3.14 collection was not used as a project result because it
does not contain the declared `fastapi` dependency; it stopped at two web-test
collection errors before running the suite.

The current End Rift gate was also run with that same dependency-complete
interpreter and completed with `242 passed, 53 warnings`, followed by all pure
Java policy and persistence/recovery checks.

### AI and Wave 6 live gates

The current `tests/RunEndRiftAiPhasesLive.ps1` run passed on
`sourceImplementationSha`:

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

The detailed exact-head Wave 6 run used a 600-second disposable-client
window and is recorded in
`local-runtime/wave6-ritual-live-20260918065715244.log`. It passed capture
ordering, restart rehydration, overdue drain replay, 19.5/20-second cadence,
health floor, external-damage immunity, projectile origin, zone effects,
reverse/control swap, caster lifecycle, completion cleanup, and zero-state
cleanup.

One shorter 180-second-client attempt is retained at
`local-runtime/wave6-ritual-live-20260918064931171.log`; it timed out while
waiting for the zone-effect observation and still passed its cleanup check.
It is explicitly not counted as a pass or hidden. The longer exact-head rerun
passed the same gate.

The focused current-head amplifier run is recorded at
`local-runtime/wave6-amplifier-live-20260918103134126.log`. It used nine real
offline clients, reached the five-caster profile, captured the real prisoner,
removed the amplifier's three guards, exposed the amplifier, and then removed
the live Заклинатель by UUID. Its acceptance markers were:

```text
LIVE_WAVE6_AMPLIFIER_CASTER_READY role=AMPLIFIER state=GUARDED_CASTING guards=3
LIVE_WAVE6_AMPLIFIER_EXPOSED_PASS guards=0 state=EXPOSED_CASTING
LIVE_WAVE6_AMPLIFIER_LOSS_PASS removed=true before=1 after=0 caster_count=1
LIVE_WAVE6_AMPLIFIER_PASS before=1 after=0 projectile_delta=1 cooldown_reduced=true duration_reduced_after_loss=true scheduler_turn_consumed=false
```

The same live log and server log show the amplifier overlay at
`effective_projectiles=4`, `effective_intensity=1`, `cooldown_ms=10725`, then
the unamplified path at `effective_projectiles=3`, `effective_intensity=0`,
`cooldown_ms=11000`; the zone duration changes from `5150` ms to `5000` ms.
The Wave 6 start log removed one stale Wave 7 display
(`displays=1 blocks=0`) and found no Wave 3 portal display (`displays=0`).
After cleanup the server reported zero event mobs and zero transient visuals.

The client texture root cause was also reproduced in the exact local client
log: the old `EndermanEyesFeatureRendererMixin` descriptor expected
`LivingEntity` while the renderer callback supplies `Entity`, causing the
resource-pack load to be removed. The mixin now targets `Entity`; the rebuilt
client and staged modpack hashes are recorded below.

### Wave 7 live boundary gate

The latest exact-head report is
`artifacts/end-rift-diagnostics/20260918-064101-74356366d22b/`. Its structured
report records:

```text
LIVE_WAVE7_PLAYER_DAMAGE_LEDGER_PASS players=2 positive_attackers=2
LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0 phase_unchanged=true
LIVE_WAVE7_COMMAND_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0
LIVE_WAVE7_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false visuals=0 barriers=0
END_RIFT_DIAGNOSTIC_REPORT_PASS
```

The report also records `diagnosticEventsDropped=0`, empty entity/task/control/
projectile leak lists, empty Wave 7 restore mismatches, and the exact artifact
hashes. Both real disposable players produced positive accepted damage before
natural chamber completion.

### Exact-head boss hitbox live gate

The current HEAD was tested against isolated Paper after the server was
started with `tests/StartEndRiftLocal.ps1`. The first attempt while RCON was
stopped failed closed and was recorded as `NOT VERIFIED`; the rerun passed and
the server was stopped through RCON afterwards. The passing raw log is
`local-runtime/boss-hitbox-live-20260918-exact-042d3514.log` with SHA-256
`200f19f5412becdb50e3270f1d26d041cd32afcd280057fbfecfc99664878115`.

```text
LIVE_BOSS_HITBOX_PROFILE_PASS parts=HEAD,CHEST,PELVIS,LEFT_UPPER_ARM,LEFT_FOREARM,RIGHT_UPPER_ARM,RIGHT_FOREARM,LEFT_LEG,LEFT_LEG,RIGHT_LEG,RIGHT_LEG proxies=11 generation=1162 tagged=true parent=aa22dbea-6b6c-4274-940b-6029e03a531b bounded=true
LIVE_BOSS_HITBOX_SELF_HEAL_PASS recreated=true proxies=11 generation=1162 pdc=true
LIVE_BOSS_HITBOX_MELEE_PASS attacks=1 accepted=1 before=5000 after=4995 single_authority=true
LIVE_BOSS_HITBOX_MISS_PASS attacks=1 accepted=0 before=4995 after=4995 carrier_ray_validated=true
LIVE_BOSS_HITBOX_PROJECTILE_PASS projectile_events=1 before=4995 after=4994 uuid_deduped=true
LIVE_BOSS_HITBOX_INVULNERABILITY_PASS before=950 after=950 phase=last_seal accepted=0
LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS proxies=0 second_cleanup=true
```

The committed evidence summary is
`docs/superpowers/evidence/end-rift-boss-hitbox-live-2026-09-18.md`.

## Artifact identity

| Artifact | SHA-256 |
| --- | --- |
| `CopiMineEndEvent.jar` | `4d7386e47e8728538fbc185d14cec2a654d4c4f19e4ab59d1217bc6977c50e36` |
| `CopiMineClient.jar` | `1173a108fa03bb3bf7338b40d230612a98324feed80a7fc379ba75a28aac9769` |
| `CopiMineResourcePack.zip` | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| `Purpur server jar` | `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c` |
| `CopiMineMods.zip` | `e5c9c848df155ff0922bbfa82bafa94f50a164f1da49f671d548233da6dec239` |

The resource-pack generator’s recorded SHA-1 is
`a04f7d1c93465cd0f79db6bad5c2b12c3f1ab6a6`.

## GitHub CI

The recorded CI-evidence head `a9de3e3091faf9cb348c93908403f850f2241cf8`
has both required workflows green. It is a documentation-only descendant of
the code source `042d351433cd8b2deb39142236b620a802b7a916`; later report-only
commits do not change the server/client/resource-pack hashes.

- [push run 35309013913](https://github.com/IliaZav/copimine/actions/runs/35309013913)
- [pull-request run 35309017715](https://github.com/IliaZav/copimine/actions/runs/35309017715)

Both runs report `completed / success` for `a9de3e30`. Each run completed the
`static-and-contract` and `java-plugins` jobs. The push run published
`end-rift-event-gate-diagnostics` with digest
`sha256:95ccc1092fa6656b3415bd1b1c7634e98f9b55ebc46a17e8e13b5eab3446db2d`;
the pull-request run published the corresponding digest
`sha256:2261384df51e5db63c363ad5e83352bedf809221897fa04539ea6afbfb010fa4`.
The four GitHub warnings are upstream Node.js 20/setup-java v4 deprecation
notices; no required job failed.

## Native visual gate

`nativeMinecraftTestedSha=NOT VERIFIED`. The Computer Use bridge exposed no
native application window (`apps=[]`) and only browser surfaces during the
current task, so no exact-head Minecraft screenshot or 15-second flight video
can be honestly attached as current proof. Existing PNG/MP4 files under
`artifacts/end-rift-v3-evidence/` are preserved, but are not relabeled as
evidence for `042d3514`.

The exact operator procedure for the remaining native gate is committed at
`docs/superpowers/evidence/end-rift-native-capture-procedure.md`. It covers
native-window identity, boss/model/hitbox captures, the Wave 6 Ritual Sphere,
the Wave 7 physical barrier, the mob matrix, the continuous 15-second flight
video, evidence hashing, and cleanup. Until that procedure produces fresh
captures for this source/artifact identity, `nativeMinecraftTestedSha` remains
`NOT VERIFIED`.

This keeps the release gate open. Do not call the event visually released until
the native client is reachable and a screenshot/video is captured against the
same source/artifact identity recorded above.

## Required handoff state

The server-side remediation is evidenced by the exact-head contract, AI,
boss-hitbox, Wave 6, and Wave 7 passes. The remaining release blocker is the
exact-head native Minecraft screenshot/video matrix and its
`nativeMinecraftTestedSha`. Only then may this document change from
`INTERIM — RELEASE BLOCKED` to a release status.

## Current local handoff

The isolated Paper server is intentionally left running for manual native
verification:

```text
same machine: 127.0.0.1:25566
Radmin VPN:   26.29.99.140:25566
resource pack: http://26.29.99.140:8092/CopiMineResourcePack.zip
website:       http://127.0.0.1:8093
state:         COLLECTING, event-mobs=0, boss=none, visuals=core overlay only
```
