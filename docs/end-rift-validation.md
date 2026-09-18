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
| `sourceImplementationSha` | `042d351433cd8b2deb39142236b620a802b7a916` |
| `testedArtifactBuildSha` | `82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4` (SHA-256 of `CopiMineEndEvent.jar`, not a Git SHA) |
| `reportCommitSha` | `02a42f6bf944ce52370aa205686b8e150877c9f7` (`docs(end-rift): record exact-head hitbox closure`) |
| `githubActionsHeadSha` | `042d351433cd8b2deb39142236b620a802b7a916` |
| `nativeMinecraftTestedSha` | `NOT VERIFIED` |
| Deployment | Isolated local Paper/PostgreSQL validation only; no production deployment |

The current source HEAD contains the Wave 6 AI ownership changes and the Wave
7 live probe fixes. The commits after the last code-changing live run are
documentation/evidence-identity commits; the server plugin artifact remains
byte-identical to the recorded `testedArtifactBuildSha`.

## Current exact-head results

### Contract and static checks

The declared project Python 3.13 environment
`C:\Users\zavod\AppData\Local\Temp\copimine-end-rift-tests-20260916-py313\Scripts\python.exe`
reported:

```text
599 passed, 58 warnings in 13.10s
```

The system Python 3.14 collection was not used as a project result because it
does not contain the declared `fastapi` dependency; it stopped at two web-test
collection errors before running the suite.

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
| `CopiMineEndEvent.jar` | `82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4` |
| `CopiMineClient.jar` | `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce` |
| `CopiMineResourcePack.zip` | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| `Purpur server jar` | `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c` |

The resource-pack generator’s recorded SHA-1 is
`a04f7d1c93465cd0f79db6bad5c2b12c3f1ab6a6`.

## GitHub CI

The exact current head has both required workflows green:

- [push run 35305762545](https://github.com/IliaZav/copimine/actions/runs/35305762545)
- [pull-request run 35305765238](https://github.com/IliaZav/copimine/actions/runs/35305765238)

Both runs report `completed / success` for the exact implementation head
`042d351433cd8b2deb39142236b620a802b7a916`.

## Native visual gate

`nativeMinecraftTestedSha=NOT VERIFIED`. The Computer Use bridge exposed no
native application window (`apps=[]`) and only browser surfaces during the
current task, so no exact-head Minecraft screenshot or 15-second flight video
can be honestly attached as current proof. Existing PNG/MP4 files under
`artifacts/end-rift-v3-evidence/` are preserved, but are not relabeled as
evidence for `042d3514`.

This keeps the release gate open. Do not call the event visually released until
the native client is reachable and a screenshot/video is captured against the
same source/artifact identity recorded above.

## Required handoff state

The server-side remediation is evidenced by the exact-head contract, AI,
boss-hitbox, Wave 6, and Wave 7 passes. The remaining release blocker is the
exact-head native Minecraft screenshot/video matrix and its
`nativeMinecraftTestedSha`. Only then may this document change from
`INTERIM — RELEASE BLOCKED` to a release status.
