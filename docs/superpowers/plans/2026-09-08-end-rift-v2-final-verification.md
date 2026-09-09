# End Rift Event V2 Final Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:verification-before-completion and superpowers:systematic-debugging. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the committed End Rift V2 against its design contract, repair only reproducible defects, and publish a clean, evidence-backed verification commit on `codex/end-rift-event`.

**Architecture:** Treat Paper as the authority for encounter state, health, targets, rewards and cleanup. Audit the existing Bukkit facade, pure policies, client bridge/renderers and resource-pack pipeline separately, then exercise the preserved local Paper world through the existing RCON/live harness. Any repair must be introduced by a failing regression test and validated across the relevant layers.

**Tech Stack:** Java 21/Paper, Fabric client, Gradle, JUnit-style pure Java tests, Python contracts, PowerShell local Paper/RCON probes, PostgreSQL-backed Artifacts integration, resource-pack JSON/PNG/OGG assets.

**Spec:** `docs/superpowers/specs/2026-09-07-end-rift-event-v2-design.md`, `docs/superpowers/specs/2026-09-07-end-rift-event-v2-codex-master-prompt.md`, `docs/art/end-rift-tentacle-artist-brief-ru.txt`

## Global Constraints

- Work only in `D:/Desktop/Copimine/copimine-main/.worktrees/end-rift-event` on `codex/end-rift-event`.
- Preserve the current local map, whitelist, PostgreSQL data and runtime configuration; do not touch launcher, website source or production services.
- Keep all official decisions server-authoritative; client code may render but never decide hits, health, targets, phase, rewards or grabs.
- Keep Combat Trace disabled by default and use it before changing any damage path.
- Do not use global `noDamageTicks = 0`, virtual boss HP, blanket Wave 3 knockback, unbounded entity/particle loops or unjournaled block mutations.
- Do not claim visual or runtime completion without fresh evidence; label every unavailable real-client check `NOT VERIFIED`.

---

### Task 1: Reconfirm repository and runtime baseline

**Files:**
- Read: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`, `EventConfig.java`, `EventSnapshot.java`, `EventStateStore.java`, `EventTaskRegistry.java`, `HazardMutationJournal.java`, all `domain/*`, all `runtime/*`.
- Read: `CopiMineClient/src/main/java/me/copimine/client/*`, `CopiMineClient/src/main/resources/assets/copimineclient/*`.
- Read: `copimine-artifacts/items.yml`, Artifacts API and WorldCore API.
- Write only if needed: `docs/testing/end-rift-v2-release-evidence.md`.

- [ ] Run `git status`, `git branch --show-current`, `git rev-parse HEAD`, `git log --oneline -20`, `git diff`, and `git diff --staged`; record the starting SHA.
- [ ] Run source searches for `BossVirtualHealthPolicy`, `FINAL_DRAIN`, `FINAL_RITUAL`, `FINAL_WAVE`, old Tower Defense/Rift Storm identifiers, banned combat ActionBar strings, unregistered schedulers, and TODO/FIXME.
- [ ] Query local RCON with `cmend status`, `cmworld end status`, and `list`; verify the current map and resources are preserved and no event entities are active.
- [ ] Check ports `25566`, `25576`, `8092`, `8093`, and record local Paper/plugin/pack status without connecting to production.

### Task 2: Run the complete automated gate before editing

**Files:**
- Execute: `tests/RunEndRiftEventChecks.ps1`.
- Inspect: all generated build/test logs and `tests` scripts included by the gate.

- [ ] Run the full End Rift gate and record every failure, warning and pass count.
- [ ] Build the server plugin, Artifacts plugin, client and resource pack using the project scripts; record artifact hashes.
- [ ] Run the source/asset contracts and pure Java policy suite independently when the aggregate output is insufficient.
- [ ] If a test fails, stop implementation and classify it as code, test-harness, environment or pre-existing before changing anything.

### Task 3: Reproduce and classify combat damage behavior

**Files:**
- Read/execute: `copimine-end-event/src/me/copimine/endevent/runtime/CombatTraceService.java`, `domain/CombatTraceRecord.java`, `tests/RunEndRiftCombatTraceLive.ps1`, mob/boss live probes.
- Modify only after RED evidence: the single listener/policy identified by the trace.
- Test: the existing combat trace and damage regression tests.

- [ ] Enable the admin-gated trace only for the local reproduction and run separate mob/mob, mob/boss and same-tick multi-player hit scenarios.
- [ ] Confirm the first divergence using tick, cancellation, hurt-resistance, health-before/next-tick, phase, shield and MSPT fields.
- [ ] Add or extend a failing regression for that exact cause, run it RED, then make one minimal root-cause repair.
- [ ] Re-run trace, mob-combat, real-health boss and multi-player damage probes; verify accepted hits change real entity health exactly once and shield/cinematic rejection remains explicit.

### Task 4: Audit and exercise W1–W6, lifecycle and cleanup

**Files:**
- Read/execute: wave controllers/policies, `AttemptLifecycleController`, `TransitionRuneController`, `WaveSixChamberController`, `EventStateStore`, `HazardMutationJournal`.
- Test: wave, rune, chamber, reward/persistence and restart/wipe probes.

- [ ] Verify transitions `W1 → intermission → W2 → ... → W6 → PRE_BOSS_COOLDOWN → BOSS_CINEMATIC → BOSS_ACTIVE` and reject direct W5→Boss paths.
- [ ] Verify rune uniqueness, five-second simultaneous hold, reset at leave/death/disconnect/world-change, outer-edge placement after W4 and no runes after W6.
- [ ] Verify W1 Carrier delivery, W2 target rotation, W3 exactly three portals/two pushers per pack, W4 4-second safe warning plus 3-second fog, restoration, W5 rings/prisoner/duo flow and W6 2/3/4-room isolation.
- [ ] Inject wipe/restart/disable during fog, prisoner, chamber and tentacle hold; assert emerald/barrier/ice restoration, generation task cancellation, entity/projectile/VFX cleanup and Core resource preservation.

### Task 5: Audit Boss V2, tentacles, rewards and shard

**Files:**
- Read: boss stage/director/health policies, tentacle runtime/model/animation code, Artifacts reward boundary, shard policies and item definition.
- Test: boss phase, real-health, obelisk, tentacle, reward, shard and authenticity probes.

- [ ] Verify real entity max/current health and boss bar ratio for 2/3/4/5/8/10/15/20 players; verify the server health ceiling fails safely instead of clamping or falling back to virtual HP.
- [ ] Verify phase thresholds, intent-based attack selection, non-repeated heavy attacks, timing markers, one-shot obelisk generation, reflected-only obelisk damage and the 15-second shield-break window.
- [ ] Verify permanent/temporary tentacle caps, server-authoritative grab/release, idle/shield loops, disconnect/death cleanup and all artist-brief bones/markers.
- [ ] Verify per-player W1–W6 and boss reward idempotency, persisted Night Cloak roll, authentic owner-bound shard, 3-second active channel, 600-second cooldown and 1800-second Abyss Anchor cooldown.

### Task 6: Validate client/resource-pack and visual evidence

**Files:**
- Read/build: `CopiMineClient`, client bridge/state/renderers, portal/boss/tentacle assets, resource-pack model/texture/animation/sound manifests.
- Evidence: existing screenshots/videos or fresh local-client captures.

- [ ] Validate protocol bounds, generation cleanup, missing-asset fallback, vanilla fireball isolation, model JSON, animation marker references, PNG dimensions, sound registration and pack hash.
- [ ] Run a real CopiMineClient visual pass for W1, W2, W3 portal/pushers, W4 zones/fog/restoration, W5 rings/prisoner, W6 chambers, Boss phases, obelisk and tentacle idle/grab/throw/death.
- [ ] Save exact screenshot/video paths and do not substitute unit tests for real-client evidence; mark unavailable captures `NOT VERIFIED`.

### Task 7: Run balance/performance matrix and final release gate

**Files:**
- Execute: two/three/ten-player live runs, twenty-player stress probe, performance/cleanup probes.
- Update: `docs/testing/end-rift-v2-release-evidence.md` with commands and exact output.

- [ ] Run full 2-player W1–W6/Boss victory, 3-player chamber distribution, 10-player zone/chamber/Boss scaling and 20-player bounded stress.
- [ ] Record entity/projectile/display/VFX/task counts, TPS, MSPT and ping; verify no runaway growth or server collapse.
- [ ] Repeat key tests/builds/source scans on the final tree, inspect `git diff --check`, confirm no secrets or production/launcher/site edits, and only then commit/push.
- [ ] Verify remote SHA equals local SHA and final `git status` is clean.

## Self-review checklist

- [ ] Every required design section has implementation, automated coverage and a runtime/visual status.
- [ ] Every remaining limitation is explicitly `NOT VERIFIED` with its reason.
- [ ] The final report names root causes, fixes, commands, outputs, screenshots/videos, hashes and commit SHAs without claiming unobserved behavior.
