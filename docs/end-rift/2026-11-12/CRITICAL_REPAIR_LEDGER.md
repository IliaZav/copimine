# Critical repair and live acceptance — 12 November 2026

Start: `1b36283eae152dcb4a060ed756d85498b83aa83a` on
`codex/rift-guardian-polish-20261110`; fetched GitHub matched. The separate
source-of-truth checkout's dirty JARs, archives and manifests are preserved.
Latest user instructions and `MASTER_REBUILD_CONTRACT.md` override the older
canonical mechanics. Both canonical files were reread and their SHA-256 values
remain `6e1245b4…de50caf` and `0c781e42…a94ae50` (full hashes in `AGENTS.md`).

## Review triage

The CLI cache contains the requested 17 major issues (`coderabbit review
findings --agent`, captured in `C:/Temp/cm-review-17-findings.txt`). The cached
plain-text header identifies this directory and `origin/main`; it does not
expose a base SHA. The user identifies the review baseline as `3f224940`.
Every finding is assessed against current production code, not its old line.

| # | Finding | Current evidence / next action |
|---|---|---|
| 1 | Ordinary-pillar test asks about the next pillar | Confirmed; correcting the test reproduced the same defect in actual `impact()`. Ordinary pillars now retire on their own first hit. |
| 2 | Inferno protected cells compared as full records | Pending coordinate-based reproduction and repair. |
| 3 | Guardian death generation ignored | Pending generation-fenced restore tests. |
| 4 | Fracture state discarded on restore failure | Pending journal failure/retry reproduction. |
| 5 | Fractures erupt at the shared arena anchor | Pending per-cell runtime geometry regression. |
| 6 | Cocoon stage/elapsed/checkpoint disagreement | Earlier adapter fixes are present; recheck actual boundary use and pure-policy API before closing. |
| 7 | Echo copy admission checks unmapped family | Pending mapped-family adapter test. |
| 8 | Full-tree compile gate uses Paper 1.21.1 | Pending pinned 26.3 dependency repair. |
| 9 | Existing fractures omitted from safe-floor budget | Pending occupied-coordinate budget regression. |
| 10 | Ring row zero never damages; rows do not sweep | Pending actual moving rig/contact implementation. |
| 11 | Fog footprints stop shrinking for 5–20 players | Pending capacity and path-preserving progression tests. |
| 12 | Final skeleton is spawned before saved adoption | Confirmed in `restore()`; next ownership/recovery slice. |
| 13 | Inferno heat overwrites safe cells | Pending initialization/tick coordinate tests. |
| 14 | Core cells admitted as safe ground | Pending standable/protected-coordinate tests. |
| 15 | javac argument file does not quote paths | Pending space-containing path regression. |
| 16 | Final pillar has four displays but tick requires three | Reproduced: valid final stages rejected and a final pillar missing one display admitted. Fixed by current pillar type. |
| 17 | Inferno starts fully heated | Pending staged outward-front regression. |

## P0.1a — Ram presentation admission and seven impacts

Additional confirmed defect: `createArmor()` built three pieces while the real
tick required seven. The old runtime probe substituted a seven-piece builder,
hiding the startup failure.

New probe executes production `createArmor`, `addArmor`, `buildPillar`, and the
whole visual-admission branch of `advance`; only Paper display creation is
detached. Before fixes: 15 failures / 1 pass. After correcting armour: six
failures isolated the final-pillar mismatch, including acceptance of incomplete
geometry. After correcting pillar type: all geometry cases pass.

The corrected ordinary-collapse test exposed a production error as well:
`impact()` queried remaining collisions after the ordinal had advanced. It now
uses the struck pillar's ordinal. Four one-hit pillars retire independently;
the final pillar remains for its first two collisions and retires on the third.
Seven physical armour pieces have stable section IDs and shed once per receipt.
The existing six client texture stages are a separate presentation surface;
their final mapping/native appearance still requires acceptance.

Files: `runtime/RiftRamEncounter.java`, `test_rift_ram_runtime.py`, new
`test_rift_ram_visual_admission_runtime.py`.

- Focused builders, seven-impact, roster, permit, stomp and client-stage suites:
  **27 passed**, exit 0. Log: `C:/Temp/cm-ram-p0a-regression.log`.
- `endEventJar`: **BUILD SUCCESSFUL**, exit 0. Log:
  `C:/Temp/cm-ram-p0a-build.log`. Stage-only candidate verification follows the
  actual rebuilt JAR; this does not install it in a running server.
- Paper: **NOT VERIFIED**.
- Minecraft visuals and performance: **NOT VERIFIED**.
- Skeleton restore, final-skeleton death/completion ownership and restart matrix:
  still open; this slice does not claim them fixed.

No production or installed-client replacement is authorized. The live acceptance
run must use a separate correctly initialized Paper/PostgreSQL/client runtime.

## P0.1b — Saved marksman, deaths and accepted landing

Reproductions exposed three integration layers: `restore()` built a new guard
before resolving the saved UUID (24 failing actor cases); the main event restart
cleanup deleted the saved guard before runtime restoration (2 failing cases);
and Ram death prematurely deleted a surviving guard and completed the wave.
Additional role-height/corrupt-local-counter cases failed 12/44. The landing
adapter published `skeletonFallen` despite canceled or misdirected movement;
all four accepted/canceled/checkpoint scenarios failed before its correction.

`RiftRamRecoveryPolicy` now validates schema-2 actor identities and schema-3
explicit spawned/dead/fallen receipts. Exact saved body/marksman IDs survive
restart cleanup. The runtime adopts and validates the saved guard before any
builders, rejects missing/foreign/contradictory actors, accepts bounded platform
height using its floor footprint, and validates local pillar damage without
silently resetting malformed values. A confirmed dead guard never respawns.
Schema-2 cannot prove a completed death episode; that ambiguity enters recovery.

Both owned actor deaths now reach the runtime. Either death order requires both
receipts before completion. Shutdown retains persistent actors and pauses AI;
transient presentation is removed. A collision committed before landing resumes
the same actor's pending landing. Landing requires successful movement, matching
actual position and a durable save before success presentation.

Files: `RiftRamEncounter.java`, main `CopiMineEndEvent.java`, new
`domain/RiftRamRecoveryPolicy.java`, runtime/admission tests and three new
saved-actor/restart-cleanup/accepted-landing probes. Tests execute production
adapters with detached Paper ports; they do not boot Paper.

- Focused runtime, recovery, landing, roster, skeleton, stomp, permit, plugin-pin
  and inventory suites: **215 passed, 3 skipped**, exit 0;
  `C:/Temp/cm-ram-recovery-verified.log` (38.18 seconds).
- Modern `endEventJar`: **BUILD SUCCESSFUL**, exit 0;
  `C:/Temp/cm-ram-recovery-build.log`.
- Stage-only installer: **Verified 30 pinned plugin candidates**;
  `C:/Temp/cm-ram-recovery-stage.log`. No installed runtime changed.
- Candidate: **2,403,582 bytes**, SHA-256
  `92e0a41f2b9775531969ceccff1b1909c2960c7bb8616c57287b42ed1dc12a1b`.
- Full suite, full CI, real Paper, restart of an actual server, native pictures
  and measured performance: **NOT VERIFIED for this slice**.
- Remaining Wave 6 visual review: final-pillar authored height/offsets and
  damage geometry must survive rebuild/update; six client armour stages need
  actual client acceptance. No visual completion claim is made here.

## P0.2 — Fracture cell centers, collision and retained restoration

Actual production state/collision reproduction: two selected cells at (-8,3)
and (8,-3) produced a hit at the unrelated shared anchor and none at their
visible intended cells. Each journaled floor cell now supplies its own column
center `(x+.5, floorY+1, z+.5)` to rendering and collision. Shared batch lifetime
and one-hit ledger remain authoritative. Moving between columns cannot yield a
second hit in the same eruption. Canceled damage now passes through the existing
accepted-damage adapter; rejected hits produce no custom launch or hit receipt.
This is a spatial/collision repair, not acceptance of final geyser artwork.

Five actual restore/teardown cases failed before correction: missing world,
foreign block edit, refused block write, failed journal commit and missing
journal. Successful restoration must match every original BlockData and then
commit the journal. Already restored cells allow an idempotent retry. Failed
cleanup retains runtime and coordinate ownership. Failed apply stays on a
restore-only path; DONE cannot forget unresolved entries. Restore retries during
active ticks are bounded to once per second. Session cancellation returns a
failure while fracture ownership remains, preventing a silent generation reset.

Cold recovery previously only recognized MAGMA, although warning stages write
cracked blocks, and marked the journal restored even after unconfirmed writes.
The actual prior recovery branch reproduced four failures. A dedicated recovery
adapter recognizes all owned fracture surfaces, preserves foreign edits,
verifies exact originals (including an optional web) and enters recovery before
journal retirement when any operation is unconfirmed.

Three cell-selection assertions reproduced occupied-floor and full-record
identity defects. Selection now keys protection/occupation by (x,z), counts
unique arena cells, subtracts existing mutations from the safe-floor budget and
respects the total simultaneous cap.

- Focused actual state/collision/restore/cold-recovery probes plus fracture,
  inferno/Echo policy and wiring suites: **40 passed**, exit 0;
  `C:/Temp/cm-fracture-accepted-green.log` (17.02 seconds).
- Modern `endEventJar`: **BUILD SUCCESSFUL**, exit 0;
  `C:/Temp/cm-fracture-build.log`.
- Pin/inventory checks: **142 passed, 3 skipped**, exit 0;
  `C:/Temp/cm-fracture-pins.log`.
- Stage-only verification: **30 pinned plugin candidates**, no installation.
- Candidate: **2,405,303 bytes**, SHA-256
  `b2f1ca868e1af070550b79cbcfe95fcbdd765aff3fc1005ddb14482a59f1a11a`.
- Paper crash/restart, native collision pictures, full official run and
  performance: **NOT VERIFIED**. Regression ports detach actual Bukkit IO;
  they do not simulate a real server restart or prove actual journal fsync IO.

## P0.4 — One cocoon clock and generation-fenced death restoration

Two new domain regressions failed: an absolute tick sequence transitioned from
LANDING to MELEE_FURY at tick 41; missing/invalid/foreign stored generation was
accepted by `GuardianDeathLedger.decode`. The fall policy and production adapter
now share the absolute descent clock: FALLING through 39, LANDING at 40 through
59, MELEE_FURY at 60. The real movement adapter still owns accepted descent,
landing cue, saved next sample and AI release; there is no second clock reset.

Two additional actual snapshot-adapter cases failed because the event ignored
stored death-ledger generation and contradictory living/dead health. New saves
include the generation-scoped ledger. Restoration decodes it before loading
slots and checks its living/dead/broken state against the saved health and fall
receipts. Missing legacy ledgers still reconstruct from existing health; malformed
or foreign ledgers and contradictory receipts enter recovery. Negative slots,
unknown keys and simultaneous living/dead receipts are rejected.

- Cocoon, real fall movement, generation/health snapshot, death wiring and combat
  profile adapter suites: **61 passed**, exit 0;
  `C:/Temp/cm-cocoon-generation-adapter-green.log` (20.99 seconds).
- Pin/inventory checks: **142 passed, 3 skipped**, exit 0;
  `C:/Temp/cm-cocoon-generation-pins.log`.
- Modern `endEventJar`: **BUILD SUCCESSFUL**, exit 0;
  `C:/Temp/cm-cocoon-generation-build.log`. Stage-only verification: 30 candidates.
- Candidate: **2,405,592 bytes**, SHA-256
  `0551f3bdb2bd92ba3d918ffb52b4552f7aa3e20e52db46e71aa69cc5a6994855`.
- Actual landing animation in Minecraft, real server restart and uninterrupted
  official encounter: **NOT VERIFIED**. No production/client replacement.

## Whole-suite checkpoint c5990587 and client staging reconciliation

`Python313 -m pytest -q -ra tests --basetemp=C:/Temp/cm-critical-c599-full
-p no:cacheprovider`: **5 failed, 3521 passed, 11 skipped, 2 subtests passed**,
exit 1, 769.15 seconds. Log: `C:/Temp/cm-critical-c599-full.log`.
Four failures exposed mixed legacy client build/staging/remap outputs (including
a dev refmap inconsistent with compiler output and a ZIP carrying a different
client). The fifth was a source-string test demanding unconditional
`completed=true` after Ram death; its assertion now requires both actual death
receipts and conditional cleanup. Actual death-order behavior is covered by
P0.1b, not replaced with a new unconditional completion shortcut.

A fresh JDK21/Gradle8.10.2 `jar remapJar` succeeded in 26 seconds. Its remapped
legacy client SHA-256 is `e642a1aa85e1fbfe039562035b26d1c538cb2877e7342714ef593de6f3f55f0e`,
matching the previously staged JAR. Thus the previous staged JAR itself did not
need replacement: the inconsistent local output and installable ZIP needed
reconciliation. `build_modpack.ps1 -SyncBuiltClient` rebuilt the ZIP and updated
its SHA1/SHA256 and public snapshot together. Exact pre-change copies were saved
in `artifacts/critical-c599-client-stage-backup/`. The separate source-of-truth
checkout, installed Prism mod and production were untouched.

- Targeted artifact/current-contract/Ram-contract recheck: **143 passed,
  1 skipped**, exit 0, 2.42 seconds;
  `C:/Temp/cm-critical-five-failures-green.log`.
- Legacy build: `C:/Temp/cm-critical-legacy-client-build.log`.
- ZIP sync: `C:/Temp/cm-critical-client-stage-sync.log`.
- ZIP: **25,187,185 bytes**, SHA-256
  `4c183a3ea3aa7bbc931844a212d79a6a0c8abe3dc1ec89c88789a89916716ade`.
- The failed whole run and failed event gate are retained as failures. A new
  complete run/gate is still required; targeted success does not replace it.

## Staging harness — independent database, prepared Voice Chat, bounded Windows start

New isolated runtime was prepared under this worktree, with 30 pinned plugin
candidates and a fresh PostgreSQL database on loopback port 55435. The existing
55434 cluster, credentials and worlds were not changed. EULA was initially kept
false; the user explicitly accepted the Minecraft EULA for this local fixture,
after which only its own eula.txt was updated.

Real preflight reproduced two harness defects: preparation never called the
existing Voice Chat loopback configuration writer; the launcher/lifecycle
hardcoded port 55434 even though the initializer supported a separate fixture
port. A behavioral lifecycle regression rejected a healthy owned alternate-port
fixture. Preparation now writes the validated loopback Voice Chat configuration.
The launcher takes an explicit `-PostgresPort` (default 55434), validates private
settings against it and threads it through cluster state/PID, listener, readiness
and identity checks. Privileged/invalid ports and shared port 55433 are rejected.
Wrong credentials, foreign processes, mismatched ports, path/reparse boundaries,
role/schema ownership and loopback SCRAM requirements remain enforced.

The first actual start brought PostgreSQL up but stalled before Paper: the
native pg_ctl pipeline retained the persistent child's inherited stdout handle.
A compiled Windows launcher reproduction took 12,430 ms waiting for a 12-second
child although pg_ctl itself had exited. The helper now uses hidden file-backed
launcher IO, caches its native handle and waits only for that PID with a bounded
60-second timeout. It never waits for the whole PostgreSQL process tree. Both
successful/nonzero launcher cases are tested, with exact owned test-child cleanup.

- Harness, initializer/restart, process-handle, pin/installer suites:
  **152 passed, 1 skipped**, exit 0, 20.87 seconds;
  `C:/Temp/cm-staging-launcher-final-green.log`.
- Process-handle regression after fix: **1 passed**, 1.28 seconds;
  `C:/Temp/cm-pgctl-launch-handle-green.log`.
- Actual read-only full-plugin/DB preflight: exit 0;
  `C:/Temp/cm-critical-server-preflight4.log`.
- First fixture DB initialization: `C:/Temp/cm-critical-postgres-init.log`.
- Isolated Paper launch is in progress (console
  `C:/Temp/cm-critical-staging-paper-console2.log`); server startup and dependency
  survival beyond 180 seconds are not yet accepted. No native client evidence.
- AuthMe/AuthEffects remain enabled. No authentication/password bypass or
  production/installed-client replacement was performed.

## Full-tree gate — actual pinned API and Unicode-safe javac arguments

The old script returned success for 386 sources using the hardcoded 1.21.1 API,
which is not acceptance against the required 26.3 platform. The real old
argument-file writer failed a javac probe in a path containing spaces and Cyrillic
(`error: invalid flag: ??????`): unquoted ASCII output corrupted the input.

The gate now resolves the actual endEvent Gradle source-set classpath, invokes
`verifyPaperApi` (locked size and SHA-256), compiles the complete six source roots
with the pinned JDK, writes quoted UTF-8 argument files, and validates its owned
temporary directory before recursive cleanup. `-KeepOutputs` retains diagnostics.
No Maven 1.21.1 fallback, classpath jar glob or unquoted source path remains.

- Real javac argument-file regression: **1 passed**, exit 0, 1.19 seconds;
  `C:/Temp/cm-fulltree-args-green.log` (old failure: `cm-fulltree-args-red.log`).
- Actual full-tree compile in `C:/Temp/cm fulltree юникод`:
  **386 sources, 1065 classes, exit 0**, with verified
  Paper API `26.3.build.205-stable`, SHA-256
  `2c4ced387dd3786a7f811923eedce574349e036446f91d10669578a3827aa252`.
  Log: `C:/Temp/cm-critical-fulltree-26-green.log`.
  Retained evidence: `C:/Temp/cm fulltree юникод/cm-fullcompile-b5d777e120e1483f9f1521a1ad616f8b/`.

## First actual staging startup observation

Paper completed startup at **10:27:34 Europe/Moscow**, 86.927 seconds after
bootstrap (fresh fixture included the Mojang download). The bridge signer was
reported ready at 10:27:25. End Rift enabled at 10:27:34 and answered the actual
console command `cmend status` again at **10:31:49**, 255 seconds later, still
UNCONFIGURED with no encounter started. The old ~180-second dependency shutdown
was not observed in this fixture. Election/admin database readiness was logged.

Installed and built event JARs independently hash to
`31128f303684550f20db69999859bcdfcb679ecf4456862b7d11605f13a20b7b`
(2,406,055 bytes) after the Phase 4 inferno and Kagune ring repairs recorded at
the end of this file; the earlier reading of `0551f3bd…` / 2,405,592 bytes was the
pre-repair build. Console: `C:/Temp/cm-critical-staging-paper-console2.log`;
Paper log: `local-runtime/end-rift-server-26.3/logs/latest.log`.
This proves bootstrap/dependency survival and command availability only:
**no wave, boss collision, restoration, client rendering or official run has yet
been verified on this new runtime**. AuthMe and AuthEffects remain enabled.

---

## Round 2026-11-12 (second): four defects reproduced and repaired

Every claim below was reproduced before repair and is held by a test that was
confirmed to fail, not by inspection. Each fix was then mutated back and the
mutation caught, except where explicitly noted.

### P0 3.3 - the three-row Kagune ring (two defects)

1. **Row 0 drew but never struck.** `startKaguneRingCast` called
   `renderKaguneRingRow(…, 0)`, while `damageKaguneRingRow` was reached only from
   the ticker, for the row it advanced TO. A player standing in the inner band was
   never evaluated and took nothing from the first ring.
2. **No row swept.** `renderKaguneRingRow` ran once per row at `emergeRadius()`,
   and `ROW_TRAVEL_BLOCKS` had no call site in the event at all, so each ring was a
   single static frame. The hit test was fixed at the emerge radius for the same
   reason.

Repair: `KaguneRingGapPolicy.rowReachesAt(row, radius, sweepProgress)` plus a
`rowHits` overload carrying the progress, so the band grows outward with the sweep
and the visible gap stays the safe gap. The event passes progress to both the draw
and the hit test and redraws every tick.

**Testing note that matters.** The first version of these tests asserted call
shapes, and two mutations survived: removing the progress term from the drawn
radius, and ignoring progress inside the policy. They were replaced by
`tests/test_end_rift_kagune_ring_sweep.py`, which compiles the real policy and
asserts that 9 blocks is out of reach at emergence and in reach at half sweep, that
12.4 is reached at full sweep, that nothing reaches past the authored travel, that
every progress covers its own outermost point, that the gap stays safe at every
progress, and that NaN/negative/over-unity progress clamp. All four mutations are
now caught.

### P1 4.1 - Phase 4 inferno (three defects)

1. **The heat front never travelled.** Initialisation called
   `heatedInfernoCells(…, 1.0D)`, and 1.0 means "the sweep reached the rim", so
   every cell caught fire on tick 0 and the authored centre-to-edge heating of
   contract 14.2 was invisible.
2. **Safe ground could be re-lit.** Neither the initialisation pass nor the tick
   sweep excluded the cells the policy had just painted cold.
3. **`ARENA_INFERNO` was selectable outside `OVERLOAD`.** The pool offered it to
   `RAGE` and `LAST_SEAL`, while the ticker's first act each tick is
   `clearArenaInferno()` for any phase other than `OVERLOAD`: the boss paid the
   cooldown, journaled the floor, and the next tick undid it - one tick of flame.
   Removed from both pools; Phase 4 keeps it.

Also removed a hardcoded debug `FileWriter` to
`C:/Users/zavod/AppData/Local/Temp/diag_out.txt` that opened a handle on every
inferno render tick.

### P1 4.2 - Phase 5 echo decoy rendering (client)

The server spawns a decoy as a `PILLAGER` deliberately, so it never shares the boss
entity type, HP or defeat effects, and binds visual id `END_RIFT_ECHO_DECOY_V1`.
The client could not honour that: Guardian model selection lived in one branch of
`LivingEntityRendererMixin.copimine$selectWaveModel` gated on
`state instanceof EndermanRenderState`, and a Pillager extracts an
`IllagerRenderState`. No branch matched, `copimine$activeModel` stayed null, and
vanilla drew a small brown illager in place of the Guardian it imitates.
`EndermanRendererSelection` and `EndermanEntityRendererMixin` both modelled the
decoy correctly and are simply never reached by a Pillager - policy correct,
dispatch unreachable.

Repair: one `copimine$wearsGuardianBody` predicate lists the visual ids that wear
the Guardian body (guardian, decoy, replica) and `copimine$isGuardianState`
delegates to it, so scale and model decisions cannot drift; an Illager route
selects the Guardian model and is tested before the Enderman route.
Compilation was verified, not assumed: the first attempt failed with
`cannot find symbol: class IllagerRenderState` until the import was added.

### P1 4.3 - small Kagune cadence

`SmallKaguneSpawnPolicy` implements contract 18.1 completely, and its timing half
was never called: `nextDueMillis`, `intervalMillis` and `due` had zero call sites in
the event. The only trigger was the `SMALL_KAGUNE` boss spell, which shares a pool
with `FINAL_STRIKE`, `AMBUSH_GRAB` and `VOID_MARK`, so "every 15-20 seconds" was
really "whenever the die says so".

Repair: `tickSmallKaguneSpawner`, driven from `tickBoss` rather than spell
selection. The window advances whether or not a batch fitted - otherwise reaching
the thirty-living cap would freeze the clock for the rest of the phase - and it is
fenced by the encounter generation so a restart inside Last Seal starts a fresh
window. `spawningAllowed` gates it, so breaking the cocoon stops new spawns while
survivors stay killable. The forced lifetime was already retired
(`SmallKagunePolicy.expired` is hard false) and was not disturbed.

### Pin fallout, and the distinction that matters

Two independent pins moved and both were repinned from locally built artifacts
whose digests matched the values CI itself reported as `actual`, so two independent
builds agreed on each:

| artifact | size | digest |
| --- | --- | --- |
| `CopiMineClient-0.1.3+26.3.jar` | 15 014 117 | SHA-512 `6760934e…` |
| `CopiMineEndEvent-26.3.jar` | 2 406 055 | SHA-256 `31128f30…`, SHA-512 `fd63c735…` |

Two local-state failures during this work are **not** repository defects and are
recorded so they are not mistaken for one: the installer refused because a local
Paper test server from an earlier round held the named lifecycle mutex, and it then
reported a plugin size mismatch because `build/minecraft-26.3/server-plugins` still
held the previous staged copy while `…/plugins/jars` held the new one.

### What this round does NOT establish

Not one of these four repairs has been observed in a running Minecraft client.
No wave, boss engagement, ring cast, inferno cast, decoy sighting or small-Kagune
cadence was witnessed. Status of all four is IMPLEMENTED and AUTOMATED-TESTED
only; LIVE-PAPER-VERIFIED and VISUALLY-VERIFIED remain unmet.
