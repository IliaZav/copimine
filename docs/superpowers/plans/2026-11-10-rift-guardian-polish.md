# Rift Guardian combat and presentation implementation plan

**Goal:** finish a coherent group boss encounter, including equivalent manual spawn aliases, stable AI, faithful attack geometry, stronger impacts, six escalating music phases, a controlled victory scene and a textured mouth-themed HUD.

**Architecture:** retain Paper authority and the six canonical phases. Repair the existing brain and manual runtime first. Extract pure bounded presentation policies and reuse the signed bridge for optional camera feedback; no gameplay depends on client effects. Keep all work isolated from DeepSeek in this managed worktree until a reviewed integration point.

**Tech stack:** Paper 26.3 / Java 25, Fabric 26.3, existing signed V4 bridge, original Bedrock assets, resource-pack OGG, generated transparent HUD texture.

**Spec:** both canonical specifications in `D:\Downloads`, the 2026-10-31 contract, and the user's November 10 boss-only instructions. Latest explicit requests authorize short camera shake and new bossbar art. No computer-use or desktop control.

## Constraints and evidence

- Preserve real HP, accepted damage/cancellation, roster/generation fences, authored clips and hit markers, independent Kagune roles, shields/respawns and exactly-once victory/rewards/WorldCore unlock.
- No random dash or frequent combat teleport; a stable pursuit focus may coexist with a different legal cast target.
- Preserve the integration worktree and its uncommitted files. Do not deploy while another executor's runtime ownership is unknown.
- Baseline integration `f8ef41af`: local 3203 tests passed, nine environment skips; Java gate and 666 validators passed. Hosted CI is blocked by two CRLF-packaged client JSON resources, not a gameplay assertion. The boss worktree starts at that checkpoint.
- Native/client visual and audio verification remain unperformed; no simulated preview is gameplay proof.

## Review focus

1. Manual spawn follows the same combat controller without official rewards.
2. Sustained simultaneous group damage does not make pursuit oscillate every few seconds.
3. Every current spell, including AMBUSH_GRAB and SMALL_KAGUNE, is reachable from the brain and retains its correct hazard role.
4. Cosmetic effect origin/range/direction follows the actual committed attack; no misleading future hit or fake projectile target.
5. Camera feedback is short, distance-limited, replay-safe and configurable; reload/unbind/quit clears it and never changes player aim or server movement.

## Task 1 — AI and manual runtime

Files: `domain/BossAbilityId.java`, `BossAbilitySelector.java`, `BossPerceptionSnapshot.java`, `BossGroupAggroPolicy.java`, narrow methods in `CopiMineEndEvent.java`; behavioral tests under `tests/`.

- [x] Add failing compiled regressions for missing final-phase roles, incorrect player health fraction, repeated ability selection cursor and stable group focus.
- [x] Feed perception from `activeBossParticipants()` and player max HP, not boss HP. Ensure successful brain selection advances its bounded cursor once.
- [x] Register AMBUSH_GRAB and SMALL_KAGUNE with their actual distinct hazard roles; reject impossible contact choices without an invalid fallback.
- [x] Preserve primary focus through legitimate lock and committed casts; improve retaliation/fairness using bounded current target history.
- [x] Make `/cmend spawn boss` route to `/cmend boss spawn`, including permissions/help/tab completion. Verify phase progression, music, bar, model binding and attacks for disposable manual actor without official rewards.
- [x] Run compiled domain and actual-handler tests; build target plugin.

## Task 2 — Impact geometry and authored animation

Files: existing `BossContactStrikePolicy`, `BossSpellVisualCompositionPolicy`, new focused presentation policy/helper as needed, server attack/cue adapter methods and tests.

- [x] Test bounded oriented swipe, chest flight and slam sampling against actual gameplay ranges and committed direction/contact markers.
- [x] Give attacks distinct 3D silhouettes and coherent release/contact/recovery; retain original authored animations and add compatible visual-only layers only where necessary.
- [x] Improve phase entry and death collapse through generation-owned finite effects, sounds and tasks. No additional gameplay damage, duplicate reward or repeated transition.
- [x] Test effect budgets, stale callbacks, cancellation, wrong world, distance culling and cleanup.

## Task 3 — Client camera feedback

Files: a pure camera envelope policy/runtime, focused camera mixin, `ClientConfig.java`, active mixin config, existing authenticated event dispatch and shared codec only if a dedicated impact receipt is required.

- [x] Add failing tests for finite amplitude/duration, distance attenuation, duplicate/stale suppression and opt-out.
- [x] Derive feedback from signed attack/phase/death animation markers; never restart on bind heartbeat or apply to unrelated players.
- [x] Apply transient render-camera offsets only, without changing player rotation/controls or server position. Clear on world/unbind/disconnect.
- [x] Compile against the actual 26.3 Camera signatures and run client tests.

## Task 4 — Music and mouth-themed HUD

Files: six boss OGG assets and music manifest/generator/config as appropriate; `EndRiftBossBarHud.java`; new `end_rift_bossbar_guardian_mouth_v2.png`; resource packaging and tests.

- [x] Inspect original model/skin, generate original transparent pixel-art frame with mouth/fangs, verify actual dimensions/alpha and integrate correct UV/track geometry.
- [x] Keep true HP and phase, readable shield/cast information, smooth bounded damage chip, small-screen scaling and no implementation jargon in player HUD.
- [x] Select/rearrange documented CC0 source material into six distinct increasingly urgent musical profiles; keep cues audible and loops bounded. Preserve credits and record actual duration/levels.
- [x] Verify manual/official phase changes play the right track once and cleanup stops owned audio.
- [x] Test packaged resources, alpha/track bounds, phases, clipping and audio durations; mark subjective/native acceptance pending.

## Task 5 — Review and integration

- [x] Run focused RED/GREEN evidence, plugin/bridge/client build, appropriate broader regressions, asset validation and CodeRabbit review. Final local event gate exit 0.
- [ ] Re-read specifications before integration review. Compare against current DeepSeek HEAD/diffs and merge only this boss scope without discarding either side.
- [ ] Rebuild and pin actual compatible bundle after integration, resolve reproducibility and exact-SHA CI, and deploy only at the user's resumed single-owner release boundary.
- [ ] Report source, automated, Paper, installed and native evidence separately; retain explicit remaining gaps.
