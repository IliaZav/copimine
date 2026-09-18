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
| `sourceImplementationSha` | `d13a0a84e5dfce575378447c112b5029a34bdc44` (`fix(end-rift): recover articulated tentacle carriers`) |
| `testedArtifactBuildSha` | `c33e6b704439ad10091d2142ef3522ecbe9067e17c0965f79c4f75516f3e4cad` (SHA-256 of `CopiMineEndEvent.jar`, not a Git SHA) |
| `reportCommitSha` | `d13a0a84e5dfce575378447c112b5029a34bdc44` (functional source/artifact evidence commit) |
| `githubActionsHeadSha` | `d13a0a84e5dfce575378447c112b5029a34bdc44` |
| `githubActionsSourceImplementationSha` | `d13a0a84e5dfce575378447c112b5029a34bdc44` |
| `nativeMinecraftTestedSha` | `NOT VERIFIED` |
| Deployment | Isolated local Paper/PostgreSQL validation only; no production deployment |

The current source HEAD contains the Wave 6 amplifier policy, stale-scene
cleanup, prisoner anchor/VFX corrections, the client entity-renderer
descriptor fix, and the articulated tentacle-carrier fallback. The server
plugin artifact is byte-identical to the recorded
`testedArtifactBuildSha`.

## Current exact-head results

### Contract and static checks

The project Python 3.13 environment
`D:\Desktop\Copimine\copimine-main\.venv313\Scripts\python.exe`
reported:

```text
616 passed, 58 warnings in 16.79s
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

After rebuilding the plugin and restarting the isolated server at the current
head, the bootstrap path also emitted:

```text
WAVE6_LEGACY_WAVE7_ARTIFACTS_PURGED reason=bootstrap-non-wave7 displays=0 blocks=0
WAVE6_LEGACY_WAVE3_PORTALS_PURGED reason=bootstrap-non-wave7 displays=0
```

The server then reported `phase=COLLECTING`, `wave=0`, `event-mobs=0`, and no
active boss. This closes the stale-scene gap where a restart could otherwise
leave old walls visible until Wave 6 started.

The client texture root cause was also reproduced in the exact local client
log: the old `EndermanEyesFeatureRendererMixin` descriptor expected
`LivingEntity` while the renderer callback supplies `Entity`, causing the
resource-pack load to be removed. The mixin now targets `Entity`; the rebuilt
client and staged modpack hashes are recorded below.

### Exact local client profile deployment and runtime load

The requested client profile was synchronized at:
`D:\.minecraft\versions\ServerRP_copy_1`. The root-level `D:\.minecraft`
profile was not changed. The profile now has exactly one
`CopiMineClient-*.jar` in its `mods` directory:

```text
CopiMineClient-0.1.1.jar
SHA-256 81b9366bc6a8c5883464ee9df680f1404bbbc9157e35415e89018b8bc83880df
```

The current resource pack is installed at
`D:\.minecraft\versions\ServerRP_copy_1\resourcepacks\CopiMineResourcePack.zip`
and is listed once, first in the profile's active `resourcePacks` option:

```text
SHA-256 34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9
```

The old `options.txt` was preserved as
`options.txt.bak-end-rift-20260918`. The current client was then launched
against the local server at `127.0.0.1:25566`. Its fresh
`logs/latest.log` records both `copimineclient 0.1.1` and
`file/CopiMineResourcePack.zip` in the active resource manager, and records
no CopiMine/mixin/Rift Guardian error. The captured runtime log is
`local-runtime/client-direct-20260918120300.stdout.log`.

The client-side tentacle fix now recognizes the real `ItemDisplay` carrier by
`CustomModelData=830017` in addition to the bridge UUID, deduplicates both
discovery paths, and renders the articulated rig even when bridge pose data
arrives late. In that late-data case it uses the deterministic `READY` pose.
`DisplayEntityRendererMixin` suppresses the vanilla flat-item fallback for the
same carrier, which is the path that previously produced the tiny red square
instead of the large tentacle model. The rig remains the intended roughly
4.75-block display; its size is not being hidden by an arbitrary scale hack.

This is runtime load evidence, not a visual acceptance claim. The same log
contains unrelated malformed shield-model errors from another installed
resource-pack/mod combination and a harmless ignored backup directory; neither
mentions `copimineclient`, the Rift Guardian renderer, or the CopiMine entity
assets.

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
| `CopiMineClient.jar` | `81b9366bc6a8c5883464ee9df680f1404bbbc9157e35415e89018b8bc83880df` |
| `CopiMineResourcePack.zip` | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| `Purpur server jar` | `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c` |
| `CopiMineMods.zip` | `d59e93a9423cf5e7ec414cfe759c776f986d06aab206046b0991f7604698a471` |

The resource-pack generator’s recorded SHA-1 is
`a04f7d1c93465cd0f79db6bad5c2b12c3f1ab6a6`.

## GitHub CI

The current source head `d13a0a84e5dfce575378447c112b5029a34bdc44` has both
required workflows green:

- [push run 35322355210](https://github.com/IliaZav/copimine/actions/runs/35322355210)
- [pull-request run 35322359295](https://github.com/IliaZav/copimine/actions/runs/35322359295)

Both runs report `completed / success` and completed the
`static-and-contract` and `java-plugins` jobs.

## Native visual gate

`nativeMinecraftTestedSha=NOT VERIFIED`. The Minecraft process did launch and
the fresh profile log proves that the current client JAR and resource pack
loaded, but the Computer Use bridge still exposed no native application window
(`apps=[]`) and only browser surfaces during the current task. Therefore no
exact-head Minecraft screenshot or 15-second flight video can be honestly
attached as current visual proof. Existing PNG/MP4 files under
`artifacts/end-rift-v3-evidence/` are preserved, but are not relabeled as
evidence for `d13a0a84`.

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
