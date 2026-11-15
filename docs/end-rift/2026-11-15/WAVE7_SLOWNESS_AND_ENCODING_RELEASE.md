# Wave 1 carrier Slowness II, Cyrillic display-name repair, one spell-pair defect and two stale probes — 2026-11-15 (sixth increment)

Source: branch `codex/rift-wave7-visuals-20261114`.
Supersedes the artifacts in `WAVE7_HIT_DEATH_POSES_RELEASE.md` (installed source
`8d94cb58`), which stays as the record of that deploy.

## What was wrong

### 1. The carrier debuff had drifted off the canonical value

`d998fc8b` raised the Wave 1 carrier debuff to "Slow III" (`CARRIER_SLOWNESS_AMPLIFIER = 2`).
The canonical Waves specification fixes the value:

- §3.2: "Apply **Slowness II** during genuine possession, independent of held item."
- `MASTER_REBUILD_CONTRACT.md:377`: "Carrier Slowness II when carrying."
- `docs/end-rift/final-26.3/WAVE_01.md` and `REQUIREMENT_TEST_MATRIX` W1-03 agree.
- The user's objective, §5 Wave 1: "carrier Slowness II".

`APPROVED_WAVE_CONTRACT.md:87` also says "Slowness II", but that line is the
elite-zombie melee debuff, not the carrier. The user's decision for this increment
is to keep **Slowness II**, i.e. to reverse `d998fc8b`.

Minecraft potion amplifiers are zero-based, so Slowness II is amplifier `1`
(Slowness I = `0`).

### 2. Four player-facing names were stored as cp1251-mangled text

The Rift Ram encounter's server display names and the client boss-bar title were
double-encoded: UTF-8 bytes reinterpreted as cp1251. `RiftRamEncounter` carried the
Ram and marksman names, `RiftRamBossBarHud` the boss-bar title, so the Wave 6 boss
bar and the Ram names rendered as garbage instead of Cyrillic.

This was pre-existing at the increment base `44853f6b`, and CI's two encoding
validators (`ValidateCopiMineNoBrokenRussianEncoding.ps1` and the mojibake wrapper
that delegates to it) were already failing on exactly these files.

## What changed

Server (`copimine-end-event`):

- `CopiMineEndEvent.java`: `CARRIER_SLOWNESS_AMPLIFIER` `2 → 1`, with the canonical
  citation at the constant and at the lease code that applies the effect. The lease
  comparison (`getAmplifier() == CARRIER_SLOWNESS_AMPLIFIER`) and the
  "a stronger foreign effect already does the job" check
  (`getAmplifier() >= CARRIER_SLOWNESS_AMPLIFIER`) both follow the constant, so
  nothing else needed to move.
- `RiftRamEncounter.java`: the two mangled display names repaired byte-precisely
  (`Таран Разлома`, `Стрелок Разлома`).

Client (`tools/minecraft-26.3/client`):

- `RiftRamBossBarHud.java`: the boss-bar title repaired (`Таран Разлома`,
  `ТАРАН РАЗЛОМА` in the upper-case variant).

Both repairs are byte-level substitutions of the same four string literals, not a
re-encode of the files: no other byte in either file moved.

## A real authoring defect: two spells shared one particle pair

Found while getting the gate green, and not a test problem.

`BossSpellVisualCompositionPolicy` promises in its own class comment that "every
spell has its own colour and its own particle pair", and its own probe
(`noTwoSpellsShareASignature`) checks exactly that. Two pairs collided:

| Particle pair | Spells | Read |
|---|---|---|
| `PORTAL`/`END_ROD` | `phase_shift`, `rift_step` | an unfurling phase ring and a caster gate |
| `REVERSE_PORTAL`/`SMOKE` | `void_snare`, `void_burst` | a closing chain cage and an outward rupture |

Both collisions are present byte-identically at the base `44853f6b` (verified by
reading the base blob), so this was pre-existing, not a regression introduced here.
The fix moves the two later entries onto their own accents, because the pair is
what separates one tell from another:

- `rift_step`: accent `END_ROD → FLASH` (its hue is already the pale photon
  `0xFFF06B`; `END_ROD` was `phase_shift`'s accent over the same `PORTAL` primary).
- `void_burst`: accent `SMOKE → DUST_PURPLE` (violet dust matches its deep-violet
  hue `0x5B2BD6`; `SMOKE` was `void_snare`'s accent over the same `REVERSE_PORTAL`).

The three hues stay unique and no other entry moved. These fields are authoring
metadata: the boss renderer draws from `BossVisualCuePolicy` banks, from
`BossSpellGeometryPolicy` silhouettes and from the composition's *hue*, and never
reads `primaryParticle`/`accentParticle`, so this changes no pixel — it makes the
table honest about the promise the probe enforces. It does change class constants,
so the plugin JAR and every pin moved with it (see below).

## Two stale End Rift probes repaired

Neither failure was caused by this increment's changes; both were reproduced at the
increment base `44853f6b` and repaired in the probes only.

| Probe | Drift | Why it was stale |
|---|---|---|
| `tests/BossTargetPolicyTest.java` | asserted the spell selector must obey the pursuit lock ("the brain must obey the adapter's locked primary target") | `08f6df01` deliberately made spell selection independent of the pursuit lock ("commit independent spell targets"), and `docs/end-rift/2026-11-14/BOSS_RELEASE_ADMISSION_AND_CAST_TARGETS.md` delegates "Update any obsolete test expectations that force spell selection to hold the navigation focus: only pursuit holds that lock." |
| `tests/SpellVisualPolicyTest.java` | asserted `profiles().size() == 14` against a hand-copied list of 14 ids | `0fe0e779` authored the five ids `miniBossSpellFor` actually emits (`arrow_rain`, `rift_bolt`, `dragon_breath`, `void_burst`, `web_shot`), so the table has held 19 profiles since then. Reproduced at the base: the base table already carried 19 profiles against the 14-id probe and failed with the identical assertion |

Both probes were **strengthened**, not deleted or loosened:

- `BossTargetPolicyTest.testSpellSelectionIsIndependentOfThePursuitLock` keeps the
  pursuit-lock claim (through `BossTargetPolicy.chooseTarget`, which still locks)
  and adds the spell-selector contract: a spell pick may leave the locked target
  (`ISOLATED_OPPORTUNITY`), a stale lock target is ignored, and repeating the locked
  target still costs a preference that stays **bounded** (`0 < nudge <= 1.0`).
- `SpellVisualPolicyTest` validates **every** authored table entry (name not equal
  to the id, at least three layers, a positive particle budget, a distinct accent
  particle), requires every `EndRiftAiPolicy.MiniBossSpell` id to be authored, and
  requires the five live-rotation constants specifically. A count equality was
  replaced by containment plus full enumeration, so authoring a new spell can no
  longer fail the probe while authoring a *stub* spell can no longer pass it.

`BossSpellVisualCompositionPolicyTest` also carried a hand-copied ten-id list in
`everyBossSpellIsAuthored` while the table held twenty entries, so half of it was
never checked. That list is now:

- a **shrink floor** (`spellIds().size() >= 20`), so deleting an authoring entry has
  to be a deliberate edit of the number rather than a silent return to the generic
  cue;
- **full validation of every entry** in the table (keys itself, non-blank primary
  and accent, primary ≠ accent, `minecraft:` sound, readable signature);
- **enumeration of `EndRiftAiPolicy.MiniBossSpell.values()`**, so a new constant that
  is never authored fails here instead of casting with no tell.

The five melee and ambush `BossSpell` entries (`melee_swipe`, `chest_strike`,
`ground_slam`, `ambush_grab`, `small_kagune`) are deliberately absent: they are
contact moves, and `BossVisualCuePolicy` authors no cue bank for any of them.

## Guard strength checked by mutation

Each mutation was applied to an isolated copy of the product source; the probe had
to FAIL, and the source was restored byte-identically afterwards.

| Mutation | Guard | Result |
|---|---|---|
| `BossTargetSelector` returns the locked target when it is still living | `BossTargetPolicyTest` | FAILS — "spell selection must be free to leave the locked pursuit target" |
| locked-target penalty `1.0 → 5.0` | `BossTargetPolicyTest` | FAILS — "the locked-target preference must stay bounded…" |
| delete the `web_shot` profile (an id the old 14-id list never named) | `SpellVisualPolicyTest` | FAILS — "the authored roster shrank to 18 spells" |
| `web_shot` layers `3 → 2` | `SpellVisualPolicyTest` | FAILS — "web_shot needs layered particles" |
| `web_shot` display name becomes its internal id | `SpellVisualPolicyTest` | FAILS — "web_shot must not fall back to an internal id" |
| `web_shot` accent particle becomes the primary one | `SpellVisualPolicyTest` | FAILS — "web_shot needs a distinct accent particle" |
| `rift_step` accent back to `END_ROD` (the pre-fix collision) | `BossSpellVisualCompositionPolicyTest` | FAILS — "distinct primary particle pair: rift_step is PORTAL/END_ROD" |
| `void_burst` accent back to `SMOKE` (the second collision) | `BossSpellVisualCompositionPolicyTest` | FAILS — "distinct primary particle pair: … REVERSE_PORTAL/SMOKE" |
| `web_shot` key renamed to `web_shot_legacy` | `BossSpellVisualCompositionPolicyTest` | FAILS — "authored miniboss spell: web_shot" |
| `echo_pulse` accent set to its own primary | `BossSpellVisualCompositionPolicyTest` | FAILS — "primary and accent differ: echo_pulse" |
| the `final_strike` entry deleted | `BossSpellVisualCompositionPolicyTest` | FAILS — "the authored roster did not shrink below 20 spells (found 19)" |
| `void_burst` hue set to `void_blast`'s hue | `BossSpellVisualCompositionPolicyTest` | FAILS — "distinct hue: void_blast is 0x7a3bff" |
| control: restored sources | all three probes | PASS, byte-identical restore (`True`) |

## Artifacts

| Artifact | Bytes | SHA-256 | SHA-1 | SHA-512 |
|---|---:|---|---|---|
| `CopiMineEndEvent-26.3.jar` | 2 500 218 | `116a757f4d925a4af7f0acd68631edff0204192dd4d5d696bcd951907b535a27` | `2db3b0567ab7105886d1b69a8c288a2f0ba021e1` | `1380a913158e240d02d897194c2690ea408b971aa28cf70f007b6637b5972607f58edb7a5111051bc103e46aea74455a97edaaf4385537ad2577d891d8b5a2fe` |
| `CopiMineClient-0.1.4+26.3.jar` | 30 096 785 | `6150f64fc81147a1b9c00d496088468c22792c6398327b9a138c4576bdb52deb` | `3d24f29fb49d7312ba11ea0fd63f2530f8e45889` | `dcccf75ba8d39cbcef7da8ca64bacbe70ec70434b8f10104239add74927f8f8d035234516492655564674d11cffeae576d414803cdbc0e5be69739db7b47c25e` |
| `CopiMineResourcePack-26.3.zip` (unchanged) | 34 241 093 | `40f569decb8f8f369ed15255f87b6e9e35f6c5848a09284a732f4ca1e172ee0c` | — | — |

Published client name for this client (first 16 hex of its SHA-256):
`CopiMineClient-0.1.4+26.3-6150f64fc81147a1.jar`.

Both JARs were verified reproducible, and both were re-verified again after the CI
gate's own rebuild. The plugin candidate is rebuilt from source by the pinned
Gradle `migrationPlugins` task (`scripts/minecraft/BuildMigrationPlugins.ps1`,
Temurin 25.0.4.1 / Gradle 9.8.1); the client is unchanged by this increment except
for the boss-bar title.

Pins updated to match the shipped candidates:
`tools/minecraft-26.3/server-plugins.lock.json` (candidate size/SHA-256/SHA-512 and
the previous `40128d88…` prepended to `replaceSha256`),
`tools/minecraft-26.3/profile.lock.json`, `docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json`,
`docs/minecraft-26.3/README.md`, `tests/test_minecraft_migration_server_plugins.py`,
`tests/test_minecraft_migration_profile.py`.

## Artifact-level proof that the fixes ship

Source strings are not proof that a compiled artifact contains the repair, so both
were checked inside the built JARs:

- `javap -p -constants` on `me.copimine.endevent.CopiMineEndEvent` in the plugin JAR
  reports `private static final int CARRIER_SLOWNESS_AMPLIFIER = 1;`.
- A byte scan of every `.class` entry in both JARs finds `Таран Разлома`,
  `Стрелок Разлома` and `ТАРАН РАЗЛОМА` (plugin: 3 + 4 classes; client: 1 + 1 + 1
  classes) and finds **zero** occurrences of their cp1251 double-encoded forms.
  Those broken byte sequences are deliberately not reproduced here: the
  repository's own mojibake validator treats those characters as corruption
  wherever they appear, documentation included, which is why this record
  describes them instead of quoting them.
- `javap -p -c` on `me.copimine.endevent.domain.BossSpellVisualCompositionPolicy`
  in the rebuilt plugin shows the corrected pairs in the bytecode of `build()`:
  `rift_step` pairs `PORTAL` with `FLASH`, `void_burst` pairs `REVERSE_PORTAL` with
  `DUST_PURPLE`, and `phase_shift`/`void_snare` still carry the `PORTAL`/`END_ROD`
  and `REVERSE_PORTAL`/`SMOKE` pairs they own.

## Verification status

| Area | Status |
|---|---|
| Carrier debuff is Slowness II (amplifier 1) in source and in the shipped bytecode | IMPLEMENTED |
| Four mangled display names repaired, in source and in both shipped JARs | IMPLEMENTED |
| Slowness lease probes and inventory-acceptance probe assert amplifier 1 | AUTOMATED-TESTED |
| Every authored spell has a unique hue and a unique particle pair (20 compositions) | IMPLEMENTED |
| All three End Rift probes fail against mutations and pass against the real source (2 + 4 + 6 mutations) | AUTOMATED-TESTED |
| Both JARs match their pins and rebuild reproducibly | AUTOMATED-TESTED |
| Local Java plugin CI gate (`RunJavaPluginCi.ps1`) on this tree | **PASS** — `GATE_EXIT=0`, `End Rift child process exit code: 0`, `Java plugin CI gate passed with Temurin 21.0.12.1.`; `AssertionError=0`, `Traceback=0`, `FAILED=0`; inside the gate `BossSpellVisualCompositionPolicyTest: PASS (275 checks)`, `SpellVisualPolicyTest OK`, `BossTargetPolicyTest OK` |
| Full `python -m pytest -q tests/` | **PASS** — `3650 passed, 21 skipped, 2 subtests passed` (707.69s), exit 0. An earlier run of the same tree reported one failure, `test_migration_postgres_start_process.py::test_pg_ctl_start_waits_for_launcher_not_persistent_child[success]` (`assert 8645 < 5000`): that module and its probe are byte-identical to HEAD and to base `44853f6b`, neither is touched by this increment, and the probe passes 6/6 in isolation at ~1.9 s, so it was a wall-clock flake under full-suite load. The re-run is green |
| GitHub Actions on the pushed commit | **PASS** — run `43770781270` on `948c058c`, conclusion `success`, all three jobs green: `static-and-contract`, `migration-postgres-contract`, `java-plugins` (26m41s, including `Run shared Java plugin release gate`). The previous run on `2400320d` failed before the Python tests ran, on an external Apache mirror 404 for the pinned Maven archive; commit `948c058c` repoints that mirror with the artifact's sha512 unchanged |
| Production re-deploy of these artifacts | **LIVE-VERIFIED** — `INSTALLED_SERVICE_READY`; service active, `MainPID=42933`, restarted `2026-11-16 05:38:32 +07`; on-disk plugin `116a757f…` (2 500 218 B); `[CopiMineEndEvent] Enabling CopiMineEndEvent v0.1.0`, `Done (45.501s)`; 0 ERROR/SEVERE since restart; `cmend status` responding (`state=COLLECTING`, `generation=408`); new client asset served over HTTPS (200, 30 096 785 B); pack unchanged (`34241093` B served) |
| Wave 1 carrier slowness actually felt on screen | **BLOCKED** — needs a real client |
| Seeing the two repaired tells (phase ring vs gate, chain cage vs rupture) on screen | **BLOCKED** — needs a real client |
| Live Wave 7 / 2/5/10/11/20-player acceptance | **BLOCKED** — needs live players or bots |
| Obelisk `full_hd` texture resample (1240×1269 vs 1254×1254) | **BLOCKED** — see `TEXTURE_REVIEW_2026-11-14.md` |

## Commits

On `codex/rift-wave7-visuals-20261114`, on top of `b0e9eb96`:

| Commit | What it carries |
|---|---|
| `17c80564` | The three product fixes: carrier Slowness II, the repaired display names, and the two unique spell particle pairs |
| `881d04f2` | The two repaired probes, the strengthened composition probe, and the behavioural slowness assertions |
| `2400320d` | Re-pins for both rebuilt artifacts (plugin candidate and client) |
| `948c058c` | Repoints the pinned CoreProtect Maven archive to `archive.apache.org` (see below) |

The fourth commit is not part of the Wave 7 work. CI was red on every branch
before the Python tests could run, because Apache rotated Maven 3.10.0 out of
their current mirrors (`dlcdn` and `downloads` both return 404). The archive
mirror still serves the same artifact — 10 102 082 bytes, sha512 `22d31676…8337`,
matching both the lock and the `.sha512` the archive publishes — so only the
mirror URL moved; the content pin is unchanged and `Save-PinnedArtifact` still
verifies it before use.

This document is added by the commit that follows those four.