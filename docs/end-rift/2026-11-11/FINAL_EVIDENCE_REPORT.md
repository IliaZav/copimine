# End Rift — final evidence report

Branch `codex/rift-guardian-polish-20261110`. HEAD at first publication was
`86abedddf95b578116f69952a78989e89bf52d93`; the report was later extended with
the live-server work described in section E.2.

This report separates what is **implemented**, what is **proven by automated
test**, what is **proven by mutation**, what has been **run on a live Paper
server**, and what has been **looked at in a Minecraft client**. Those are five
different claims. Four of them now have evidence; the fifth still does not.

---

## Status at a glance

| Claim | Evidence |
|---|---|
| Code implements the contracts | source, 3,415 pytest + Java gate |
| Behaviour is pinned by tests | 3,415 passed / 18 skipped; gate "End Rift current local checks passed" (exit 0) |
| Tests actually bite | mutation-tested, each with the failing message |
| CI green on HEAD | **yes — all 3 jobs on `373731e3`**, and again on `c4947231` and `4c5ad30e` |
| Runs on a live Paper server | **partially — boot, wave 1 and boss entity proven; see E.2** |
| Looks right in Minecraft | **no — no screenshot of any of this work** |

The last row is the one that matters most to the user and it is still empty. The
live-server row moved from "never run" to "run, partially proven", and E.2 states
precisely which parts of it hold.

---

## A. Repository and commits

- Branch: `codex/rift-guardian-polish-20261110`
- Baseline: `f8ef41af`
- HEAD: `86abeddd` — pushed, remote matches
- Worktree: `C:\Users\zavod\.codex\worktrees\rift-guardian-polish-20261110\integration-20261030`

Commits this round:

| Commit | What |
|---|---|
| `3be9f39d` | Rage can summon the decoys it was written for |
| `bc2efe97` | Commit the artifact churn the gates produce |
| `cba450f4` | A release candidate that matches its own lock |
| `d526db09` | Measure the fog in the only screenshots that exist |
| `67fa26e2` | Phase 5 echoes become visible actors instead of silent damage |
| `f8af667b` | Release candidate for the echo fix, pins verified twice |
| `86abeddd` | The plugin build reproduces across both entry points |

An earlier near-miss is on the record: an accidental `git add` swept another
agent's cocoon-repair work into a docs commit, published as `171580fe`, then
corrected by amending the message to `1ecd90b1` so both bodies of work are
described honestly. No source was lost; the merge `e5bf4aee` preserved both
histories. Both tree IDs were verified identical.

### Continuous integration

Run `43134355604` on `373731e3`, all three jobs green — the first green run on
this branch:

```
static-and-contract         success   (incl. pinned candidates + Python contract tests)
migration-postgres-contract success
java-plugins                success   (incl. the shared End Rift release gate)
```

Getting there required two separate fixes, and in both cases the *local* signal
was misleading:

- The pinned-candidates step failed with `actual=52d1448e` against
  `expected=af45b9a2`. Those runs were on commits that predated the repin. I
  nearly repinned to `52d1448e` on the strength of the CI message; reproducing
  CI's own `PrepareMigrationCandidates.ps1` locally produced the committed
  `7128fc95` and proved the lock was right.
- The contract tests then failed on a staged client jar that no longer matched a
  from-scratch compile. This machine had a **cached** legacy build that happened
  to equal the stale committed jar, so the comparison passed here without ever
  running. Deleting the build directory reproduced CI at once.

---

## B. Waves

For every wave the honest status is the same on two of the columns: **no live
Paper run, no client verification.** What varies is how much automated coverage
exists.

| Wave | Implemented | Automated tests | Live Paper | Client seen |
|---|---|---|---|---|
| 1 charge custody | yes | yes (custody, duplicate rejection) | no | **no** |
| 2 spectral hunt | yes | yes (layout, groups, music) | no | **no** |
| 3 portals | yes | yes (spawn, capture, one-shot close) | no | **no** |
| 4 obelisks | yes | yes (roster-scaled budget, knockback, journal) | no | **no** |
| 5 fog | yes | yes (palette, safe zones, wither tiers) | no | **no** |
| 6 Rift Ram | yes | yes (7 impacts, 3-hit final pillar, skeleton) | no | **no** |
| 7 minibosses | yes | yes (2/3 marksmen, per-actor AI) | no | **no** |

### What the screenshots do establish about Wave 5

Eight PNGs exist from 10 November 23:28–23:35. Measured by averaging three
horizontal bands per image on a 7-pixel stride:

```
23:31:34_3   top rgb=(17,25,32) blue-red +15   bottom (100,82,66) blue-red -34
23:35:27     top rgb=(16,22,27) blue-red +11   bottom (124,104,81) blue-red -43
```

A positive blue-red at the top of frame is blue-teal fog. The corrected
`wave5_volume.fsh` palette scores +0.020 on its soul term; the teal it replaced
scored +0.075. The shots match the stale palette, consistent with the installed
Prism client being a different build from the pin (564 entries vs 565, fifteen
fog entries differing, `BlackFogGuidePolicy.class` absent).

The bottom band sits about 4.4× the luminance of the top band, so near-floor
geometry is visible and the fog is **not** the black screen the user described.
That one property the images do establish. Nothing else.

---

## C. Boss phases

All six phases are implemented and covered by domain tests plus adapter tests.
None has been run on a server or watched in a client.

- **Phase 1 Awakening** — pursuit, swipes, 3D rift projectiles, Void Blast.
- **Phase 2 Hunt** — target rotation, pursuit, chest strike, arrow volley, mark.
- **Phase 3 Rift** — real block mutation through the hazard journal with
  PREPARED/APPLIED ordering and exact restoration; geysers.
- **Phase 4 Overload** — reversible arena-wide inferno with survivable routes,
  then restoration.
- **Phase 5 Rage** — the subject of this round, below.
- **Phase 6 Last Seal** — cocoon with 6–8 permanent-fate guardian slots, 0.2 %/s
  regeneration capped at 5 %, permanent cocoon break, authored fall, 3× incoming
  damage melee fury.

### Phase 5 this round

Four independent defects made the Echo mechanics do nothing:

1. `spellPool(RAGE)` omitted `SUMMON_SERVANTS`, the only spell that reaches
   `summonEchoDecoys`. The decoys were unreachable in their own phase.
2. `tickEchoAttackReplicas` was called only inside `pinBossToFinalSealCore`,
   below a LAST_SEAL gate, so copies started at Rage entry never advanced.
3. `startEchoAttackReplicas` recorded identity and timings but spawned no entity
   and published no model, while the tick applied real damage — invisible damage,
   forbidden by 23.3.
4. The Rage entry passed `VOID_BLAST`, a family the policy refuses, so every copy
   was discarded immediately.

(3) and (4) nearly cancelled out, which is why the phase read as merely missing
rather than dangerous.

Now: distinct `ECHO_DECOY` / `ECHO_REPLICA` event kinds with separate actor
records and cleanup; a real Enderman actor spawned before the copy is registered;
`damageAllowed` consulted in both the strike and explosion branches; Rage and
generation fences that clear rather than skip; one advance per server tick.

**And the client did not know the two visual ids.** Three dispatch sites each fell
through: the texture catalog returned null, the renderer switch hit
`default -> vanilla()`, and the mixin's guardian flag was false. Decoys would
have rendered as vanilla Endermen — advertising the trick and hiding the authored
model in one move. All three now carry the arms.

---

## D. Global systems

- **Projectile admission** — reconciled; skeleton arrows visible via
  `setVisibleByDefault` at all spawn sites, enforced by a contract test that walks
  every `spawnArrow(`.
- **Client sync** — generation ids, snapshot reconciliation, stale-packet
  rejection.
- **Boss damage** — accepted-hit routing through the adapter; decoy damage returns
  real `before - after`, not the requested amount.
- **Echo damage** — gated by `damageAllowed`; no actor means no damage.
- **Arena mutation** — journal with PREPARED/APPLIED and exact restoration,
  verified by adapter tests at floor Y 69, 101 and −20.
- **Reproducibility** — the plugin digest is identical under Gradle
  `endEventJar` and under CI's `PrepareMigrationCandidates.ps1`:
  `7128fc9557a389a8f4813fb1ea5e8130b0b936ce92d003073fe5f194dfcab544`, 2,398,506 B,
  `Verified 30 pinned plugin candidates`, exit 0.

---

## E. Test evidence

Commands, verbatim results:

```
python -m pytest tests -q
  3415 passed, 18 skipped, 124 warnings, 2 subtests passed  (659.32s)

tests\RunEndRiftEventChecks.ps1 -JavaHome <temurin-25.0.4.1>
  End Rift current local checks passed.

scripts/minecraft/PrepareMigrationCandidates.ps1
  Verified 30 pinned plugin candidates   EXIT=0

pytest tests/test_minecraft_migration_server_plugins.py \
       tests/test_minecraft_migration_profile.py \
       tests/test_ci_release_job_contract.py -q
  232 passed, 5 skipped
```

Mutations performed and observed this round (each: fail with a specific message,
restore, re-run green):

| Mutant | Failure |
|---|---|
| remove `SUMMON_SERVANTS` from RAGE | "Rage must offer SUMMON_SERVANTS or the Echo decoys are unreachable" |
| remove `damageAllowed` actor guard | "a copy without a visible actor dealt damage @44" |
| delete echo arms in renderer switch | "…needs its own case arm: the switch is exhaustive…" |
| drop `echoFamily` from mixin predicate | "the echo ids must sit inside the guardian predicate" |
| move `RIFT_PROJECTILE` to the stationary branch | "mobile cast must allow legal locomotion: RIFT_PROJECTILE" |
| `KaguneRingGapPolicy.sameDirection` → `return false` | "two gaps at the same angle are the same lane" |
| zero `MIN_GAP_SEPARATION_BLOCKS` | "a radius-2 ring fits exactly 2 separated gaps, got 6" |
| `BlackFogShelterMobPolicy` radius → `0.0` | "a wide body beside the refuge still overlaps it" |
| drop the walkable guard in `exit()` | "no walkable cell means no exit" |
| drop the stuck/surround branch in `BossIntentSelector` | "being stuck wins over stacking" |
| make `OVERLOAD` ignore `arenaLocked` | "OVERLOAD recovers while the arena is locked" |

Most of these were reproduced by me directly rather than taken from a report.

### Four classes that had no test at all

While the live run was blocked I went looking for policy classes with no test
reference anywhere, rather than assume the suite was complete. Four had none:

- `BlackFogShelterMobPolicy` — the whole of contract 6.6, "no hostile mob inside a
  refuge".
- `BossIntentSelector` — the whole reaction table of contract 10.4: surround,
  cluster, spread, stuck, per-phase fallback.
- `KaguneRingGapPolicy.sameDirection` — the rule that the three ring rows may not
  share one straight escape lane.

Each could have been deleted or disabled outright with every gate still green.
The gap was found by mutating the code, not by reading it: two mutants of the ring
policy survived the existing 6 ring tests, and both only failed once new
assertions were written. Writing the tests also corrected two of my own wrong
assumptions — that a spread radius of 4.0 is not "stacked" (it is), and that
leaving `bossPosition` null is harmless (it silently centres the boss, making
any nearby player look like an encirclement). Both are now comments in the tests.


Seven more classes had no behavioural test at all, and all seven now do:

- `WaveMusicMixPolicy` — the wave 5 music ducking, and the scope check that stops
  the mod rewriting vanilla or other-mod audio;
- `RiftRamVisualPolicy` — the six Wave 6 armour stages, including the clamp that
  keeps a malformed payload from rendering an invisible ram;
- `RemoteVisualCapacityPolicy` — the concurrent-visual bound, and the
  idempotency that stops an update to a running effect being dropped;
- `ClientVisualCommandPolicy` — STOP and CLEAR_ALL must pass even when the player
  has disabled server visuals, or an overlay can never be cleared;
- `EchoEyesFeature` — the violet eye tint and the draw order that keeps it
  readable (presentation half of ECHO-003);
- `KaguneRingGapPolicy.sameDirection`, `BlackFogShelterMobPolicy`,
  `BossIntentSelector` — already covered above.

Every one of them was mutated after the test was written, and every mutant was
caught. The two that caught nothing on the first attempt were my own weak
assertions, not the code: the ring test originally checked only monotonicity,
which a zeroed separation constant leaves intact, and the music test needed an
exact-gain assertion rather than a glide check alone.
### A mutation that lied

My first attempt to mutate the Rage spell pool reported the gate still green,
which would have meant the test did not bite. The mutant was at fault, not the
test: `BossPhasePolicy` has two `SUMMON_SERVANTS` lines, and a blind string
replace removed the RIFT one. A second attempt spliced by byte offset and
corrupted the line into `l.SUMMON_SERVANTS`. Only the third, which located the
Rage block first and removed the line inside it by position, produced a real
mutant and a real failure. A mutant that does not compile proves nothing.

### A red CI that was not a code failure, and one that was

- Run `43092554243` failed in *Publish Python test report* and *Upload CI
  diagnostics* with `Failed to CreateArtifact … "<!DOCTYPE " is not valid JSON` —
  GitHub's artifact API served HTML. Tests in the same job: 3361 passed.
- Runs `43110366363` and `43112300058` failed for real, reporting
  `actual=52d1448e` against `expected=af45b9a2`. Both were commits that predated
  the repin. I nearly repinned to `52d1448e` on the strength of the message;
  reproducing CI's build path locally showed the committed digest is correct and
  that `52d1448e` was the older commit's source. Repinning then would have
  pinned an artifact no build here produces.


### E.3 A real geometry defect, found by auditing rather than by testing

The Wave 7 Archmage's `rift_sigil_ring` was authored at `texOffs(56, 44)`. An
8 x 0.4 x 8 cube needs 32 units of UV width - `2 * (8 + 8)` - so it ran from
u=56 to u=88 on a **64-wide** sheet. The ring therefore sampled wrapped texture
from the far edge of the atlas and rendered as a smear.

This is the "poor UV mapping" the user reported on the Wave 7 minibosses, and it
is a real defect rather than a style choice: the ring is the flat disc around
the staff head that is supposed to be the readable "this is a spellcaster" cue at
medium range, and the piece that grows while a spell winds up.

It was found by parsing the model's own UV literals and comparing each box
against the sheet the model declares - not by any existing test. Fixed by moving
the ring to `texOffs(12, 54)`, a rectangle verified free against all fourteen
other authored boxes. Only the UV origin changed; no part was moved, removed or
resized, so the silhouette is byte-identical and only the texture is repaired.

Two follow-ups came out of checking the fix properly:

1. The audit was generalised from one model to every `*Model.java` in the client
   (9 files, 21 boxes). All now fit, including the Warden against the vanilla
   128x128 sheet and the Endermite against a 64x64 one. Models whose sheet
   cannot be determined are skipped rather than passed, because treating an
   unknown as a success is how the original bug survived.

2. `EndRiftAtlasUvLayout.fit` remaps solid faces into painted atlas islands,
   which would have made the authored offset irrelevant - **except** that it
   returns early for any part containing `rune` or `sigil`. `rift_sigil_ring`
   matches, so its authored UVs reach the GPU unchanged and there is no safety
   net. That is why the fix matters. It is also a fragile invariant in a
   non-obvious direction: removing the `sigil` skip would silently make this
   whole audit assert something that no longer affects rendering, and it would
   keep passing. The test therefore checks the skip itself.

Reverting the one-token offset change reproduces the failure with the real
numbers: "the sigil ring needs 32.0 units of UV width but only 8 remain after
u=56".

**Still unverified:** that the ring now looks right. The fix is proven correct
arithmetically and the invariant is pinned, but rebuilding the client jar and
looking at the model in game has not been done. The installed Prism client is
unchanged and still stale.
### E.2 Live Paper server run — the first one this round

Full write-up: [`LIVE_PAPER_RUN.md`](LIVE_PAPER_RUN.md). An isolated local Paper
26.3 server (port 25566, RCON 25576, `online-mode=false`) was booted against the
release candidate. The RC jar's sha256 (`7128fc95…cab544`, 2 398 506 B) was
verified before boot **and** re-read from the deployed jar afterwards.

**Proven:**

| Claim | Evidence |
|---|---|
| The RC plugin loads and boots cleanly | `Enabling CopiMineEndEvent v0.1.0`, `Done (23.448s)!` |
| Wave 1 creates real server entities | `END_RIFT_WAVE_GROUP_SPAWN wave=1 … spawned=22`; with a player present `@e[type=minecraft:enderman]` = **22** |
| Those entities pursue and strike | six `WAVE_MOB_DAMAGE … cause=ENTITY_ATTACK final=5.599999904632568 cancelled=true`, attacker UUID matching a spawn-line UUID |
| Event-owned damage interception works | `cancelled=true` on those hits |
| The boss is a real, mobile entity | 20 000 HP, NBT-readable name, `~16.8 blocks` of pursuit in 3 s across three samples, two `BOSS_MELEE_ATTACK total=20.0` |

The third row is the one that closes a loop: the same entity UUIDs that were
logged at spawn are the attackers that land damage, so the mobs are not a log
artefact.

**A hypothesis of mine that this run falsified.** I theorised that
`spawned=22` printed the *planned* group size rather than reality, and started
patching `spawnWaveGroup` to count what it actually created. The bot-present
retest disproved it: with a player online, exactly 22 Endermen existed in the
world and immediately attacked. The 22 were real; `@e` had simply been blind to
unloaded chunks. The patch was reverted uncommitted. The theory is recorded in
`LIVE_PAPER_RUN.md` as an explicitly ruled-out cause.

**Not proven, and stated as such in the live-run document:**

1. The boss **phase machine** (HUNT / RIFT / OVERLOAD / LAST_SEAL as a driven
   progression). Only one transition was observed, `AWAKENING -> RAGE`, and it
   was induced by command rather than earned through damage.
2. Boss **HP loss from a real player hit**. `BOSS_DAMAGE_ACCEPTED` with
   `health_before`/`health_after` was never observed; the 20 000 → 5 800 drop came
   from the harness transition path, not a strike.
3. The **Phase 5 echo decoys**. Zero actors proven. No `ECHO_DECOY` or
   `ECHO_REPLICA` line was ever emitted and no decoy entity existed. This is the
   newest feature and it remains the least verified item in the whole round.
4. **Waves 2–7.** Only wave 1 was exercised.
5. **Cleanup / orphans** after stopping the event.
6. All **client-side visuals**.

**Three environment blockers, each explained rather than worked around:**

- `EndRiftLocalAuthMode.ps1` refuses a `local-runtime` that is a junction. That
  guard is deliberate and correct; the probe was driven over RCON instead.
- The probe script pins `-Xmx512M`; on this machine that OOMs during
  `ImageFrame` enable and TPS falls to 7.86. The server was relaunched with
  `-Xmx2048M` and booted in 23 s.
- The plugin **fail-closes after ~180 s**: `CopiMineArtifacts` needs PostgreSQL,
  and this machine has none (no server, no docker, empty `end-rift.env`). End Rift
  then logs `dependencies are unavailable; disabling to prevent partial startup`.
  This is correct fail-closed behaviour, and it is a production-safety property
  worth having: the event refuses to run half-initialised rather than running
  degraded. It is also the single reason items 1–5 above could not be reached.

---
## F. Outstanding work

Stated plainly, with no softening:

1. **The live Paper run is partial.** A server was booted against the RC and
   wave 1 was proven end to end (22 real Endermen, six real hits). The boss exists
   and moves. But the boss **phase machine**, **player-dealt HP loss**, the
   **Phase 5 echo decoys**, waves **2–7** and **cleanup** are all unproven, because
   the plugin fail-closes after ~180 s for want of PostgreSQL and this machine has
   none. No 2/5/10/11/20-player roster has been exercised.
2. **No client verification.** No model, animation, spell, fog, geyser, Echo
   copy, Kagune ring, cocoon fall or boss death has been seen rendering. Every
   visual acceptance item in section 26 is unmet.
3. **The echo fix is verified server-side only.** That the two ids resolve to the
   Guardian family is tested. That the result reads as another Rift Guardian is
   not.
4. **The cocoon fall is adapter-tested, not watched.** Descent, landing and
   final-fury melee are wired; nobody has observed the boss come down.
5. **The installed Prism client is stale** (15,013,152 B vs the pinned
   15,013,971 B) and has **not** been replaced — replacement needs explicit
   authorization.
6. **The guardian-polish installer pins `c4abda95`** in its `OLD` dictionary and
   would refuse the current build; it needs regenerating before use.

---

## G. Production safety

Release candidate: `artifacts/rc-67fa26e2`.

| Artifact | Size | Pin |
|---|---|---|
| `CopiMineEndEvent.jar` | 2,398,506 | sha256 `7128fc95…` = lock |
| `CopiMineClient-0.1.3+26.3.jar` | 15,013,971 | sha512 `a8b505bd…` = lock |
| `CopiMineNarcotics.jar` | 370,499 | unchanged |
| `CopiMineResourcePack-26.3.zip` | 33,371,080 | unchanged |

The manifest was written, re-read, and every artifact hashed again from disk and
compared field by field. All four match.

**Nothing was deployed.** Production was not touched, the Live arena was not
reset, no world or database file was replaced, and the user's Prism client was
not overwritten. All four flags in the manifest remain `false`.

Rollback for either target is a jar copy plus a restart; no world data, database
migration or arena state is involved in swapping a jar.
