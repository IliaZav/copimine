# CopiMine End Rift — latest-head remediation and release-closure report

Date: 2026-09-18 (Europe/Moscow)

Status: `INTERIM — RELEASE BLOCKED — native exact-head Minecraft NOT VERIFIED`

Repository: `IliaZav/copimine`

Branch: `codex/end-rift-event`

## Executive result

The current branch contains the server-authoritative End Rift guardian rig,
model-aligned composite hitboxes, Wave 6 Ritual Sphere behavior, Wave 7
one-block barrier behavior, role-aware caster AI ownership, structured
diagnostics, and local Paper cleanup/recovery probes. The exact current HEAD
also passed the new live boss-hitbox probe and both required GitHub Actions
workflows.

The release is not visually closed. Computer Use currently exposes no native
Windows application (`apps=[]`), so an exact-head Minecraft screenshot and
continuous 15-second flight video cannot be captured honestly. Historical
PNG/MP4 files are retained for review but are not relabeled as proof for this
HEAD. No production deployment was performed.

## Provenance and identity

| Field | Value |
| --- | --- |
| sourceImplementationSha | `042d351433cd8b2deb39142236b620a802b7a916` |
| last code-changing live Paper SHA | `74356366d22b8438903c76b4cb7eda5eed22242a` |
| tested server artifact SHA-256 | `82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4` |
| tested client artifact SHA-256 | `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce` |
| tested resource pack SHA-256 | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| Purpur SHA-256 | `30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c` |
| report introduction commit | `02a42f6bf944ce52370aa205686b8e150877c9f7` |
| GitHub Actions head | `042d351433cd8b2deb39142236b620a802b7a916` |
| nativeMinecraftTestedSha | `NOT VERIFIED` |

The commits after `74356366` in this closure sequence are documentation and
evidence-identity commits. The server JAR SHA-256 remained identical, and the
Wave 6/Wave 7 live records therefore identify their code-changing runtime SHA
separately instead of pretending those older runs were new native captures.

## Implemented scope

### Guardian model, renderer, and animation contract

- Bedrock guardian geometry is imported with hierarchy, pivots, cube rotation,
  and per-face UV data preserved.
- Idle, running, hurt, dying, chest-strike, ground-slam, and swipe animation
  data are wired through the client animation player with bind/reset handling.
- Head/neck look control is isolated from body pose channels.
- Event Enderman and Rift Spider renderer/model selection is scoped so the
  guardian model is not applied to unrelated vanilla entities.
- The ordinary vanilla bossbar remains the active HUD path.

Static/resource contract coverage passed. Native silhouette, texture sampling,
animation playback, and absence of visual noise in an actual client remain
`NOT VERIFIED` until the Minecraft window is exposed.

### Model-aligned boss hitbox

- Persistent server-side `Interaction` proxies cover head, chest, pelvis,
  upper/forearms, and legs.
- Proxies carry parent, part, generation, and event metadata; reconciliation
  recreates missing proxies without reusing the removed UUID.
- The damage route is authoritative and deduplicates same-tick overlap.
- Melee, projectile, empty-space miss, last-seal invulnerability, and cleanup
  are covered by the exact-head local probe.

Exact-head live evidence is committed in
`docs/superpowers/evidence/end-rift-boss-hitbox-live-2026-09-18.md`; the raw
local log is intentionally kept under ignored `local-runtime`.

### Wave 6 Ritual Sphere

The detailed local live run recorded:

```text
LIVE_WAVE6_CASTER_GUARDED_PASS casters=4 guards=12 native_ai=false targets=0 ownership=true
LIVE_WAVE6_WAITING_FOR_PRISONER_PASS outside_seal_verified=true auto_capture=false
LIVE_WAVE6_CAPTURE_ORDER_PASS
LIVE_WAVE6_RESTART_RECOVERY_PASS rehydrated=true phase=READY_FOR_PLAYERS wave=6 casters=4 guards=12 visual_displays=1 prisoner_preserved=true capture_replayed=false overdue_drain_replayed=True log_rotated=True
LIVE_WAVE6_DRAIN_19_5S_PASS health=5 unchanged=true
LIVE_WAVE6_DRAIN_20S_PASS health_before=5 health_after=3 damage=2
LIVE_WAVE6_EXTERNAL_DAMAGE_IMMUNITY_PASS health=3 cases=melee,projectile,generic,fall
LIVE_WAVE6_DRAIN_FLOOR_PASS remaining=1 intensity_not_increased=true
LIVE_WAVE6_PROJECTILE_ORIGIN_PASS sphere_origin=true projectile_spawn=true max_distance=0.25 caster_origin=false
LIVE_WAVE6_ZONE_EFFECTS_PASS target=EndRiftWave6D wither=true slowness=true reverse_expected=true poison=false prisoner_zone_effects=false
LIVE_WAVE6_ABILITY_ROLES_PASS projectile=server sphere zone=4x4 reverse=server control_swap=server
LIVE_WAVE6_FREE_TARGET_CONTROL_PASS reverse=true swap=true prisoner_excluded=true reverse_swap_mutex=true
LIVE_WAVE6_CASTER_EXPOSED_PASS caster_count=1 guards_removed=3 native_ai=false target=none ownership=true
LIVE_WAVE6_CASTER_AWAKENED_PASS caster_count=1 native_ai=true target_allowed=true mixed_state=true ownership=true
LIVE_WAVE6_COMPLETION_CLEANUP_PASS sphere=false beams=0 zones=0 controls=0 prisoner_released=true prisoner_tag_removed=true transient_entities=0
LIVE_WAVE6_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false casters=0 guards=0 visuals=0
```

Evidence: `local-runtime/wave6-ritual-live-20260918065715244.log`.
The detailed Paper run was executed against the code-changing SHA shown above;
the current HEAD preserves the same server artifact bytes.

### Wave 7 Reality Split

The structured diagnostic bundle
`artifacts/end-rift-diagnostics/20260918-064101-74356366d22b/` records two
players receiving positive accepted damage, restart rehydration, natural
completion cleanup, command cleanup, zero transient entities, zero barriers,
and no diagnostic drops/write failures. The implementation uses journaled
one-block physical walls plus a separate visual layer. WorldBorder is not used
for chamber walls because it is a global square boundary rather than a
per-chamber raster.

The bundle remains explicitly attributed to `74356366` and is not silently
rewritten as a fresh 042 runtime capture.

## Verification results

| Gate | Result | Evidence |
| --- | --- | --- |
| Python contract suite | `PASS` | `599 passed, 58 warnings in 13.10s` using the declared Python 3.13 environment |
| Repository validators | `PASS` | `VALIDATOR_SUMMARY total=659 passed=659 failed=0 skipped=0` |
| End Rift current local checks | `PASS` | builds, client/resource pack, current contracts, Java policies, hashes, diff hygiene |
| Boss hitbox live probe at 042 | `PASS` | exact-head local log and committed evidence doc |
| AI phase/caster live probe | `PASS` | all waves, six boss phases, caster lifecycle, teleport guards, cleanup |
| Wave 6 Paper live probe | `PASS` | detailed exact artifact run with restart/drain/roles/cleanup markers |
| Wave 7 Paper live probe | `PASS` | two-player damage ledger, restart, natural/command cleanup, zero residue |
| GitHub Actions push | `PASS` | [run 35305762545](https://github.com/IliaZav/copimine/actions/runs/35305762545) |
| GitHub Actions pull request | `PASS` | [run 35305765238](https://github.com/IliaZav/copimine/actions/runs/35305765238) |
| Exact-head native Minecraft screenshot | `NOT VERIFIED` | Computer Use returned `apps=[]` |
| Exact-head 15-second flight video | `NOT VERIFIED` | no controllable native Minecraft window |
| Production deployment | `NOT PERFORMED` | deliberate release boundary |

The system Python 3.14 collection failure due to missing `fastapi` is an
environment mismatch, not a project result; the declared environment is the
one used for the passing suite.

## Native visual evidence boundary

The Computer Use runtime currently exposes only browser surfaces and returns
an empty native-app list. Its available CUA object also lacks the native
window-control methods needed to launch/select a Minecraft window. This is an
external capability/state blocker, not a reason to claim that the plugin is
fixed visually.

Therefore the following remain open:

1. exact-head front/side/flight screenshots of the supplied guardian model;
2. visual confirmation of UVs, texture quality, pivots, animation clips, and
   absence of vanilla overlays or pixel noise;
3. in-game confirmation that every visible model part tracks the corresponding
   hitbox through pose changes;
4. fresh Wave 6 sphere, Wave 7 wall, HUD, and cleanup captures;
5. a continuous 15-second flight recording and description of its frames.

Once a controllable Minecraft window is available, the manual capture must
use the same branch/artifact hashes above, then populate
`nativeMinecraftTestedSha`, screenshot/video paths, and the final visual
matrix. Until that happens the release verdict stays `NOT READY FOR FINAL
RELEASE`.

The reproducible operator runbook for that capture is
`docs/superpowers/evidence/end-rift-native-capture-procedure.md`. It records
the isolated-session startup, native-window refusal rule, exact boss and
hitbox scenes, animation/phase matrix, Wave 6 and Wave 7 visual states, mob
role matrix, 15-second flight path, hashes, publication, and cleanup. The
runbook is intentionally committed while the native bridge is unavailable;
its presence is not a visual pass.

## Publication and safety boundary

- Changes are confined to `codex/end-rift-event`.
- GitHub publication is limited to the requested repository branch.
- No production server, remote world, player account, or live deployment was
  changed.
- Old untracked captures and diagnostics in the worktree are user-owned and
  were preserved; they were neither deleted nor relabeled.
