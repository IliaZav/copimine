# Canonical specification pins

Date: 2026-11-18
Purpose: make the two canonical engineering specifications checkable from this
repository instead of relying on a manual re-hash at the start of every task.

## 1. Authority order

`COPIMINE_END_RIFT_WAVES_CANONICAL.md` section 0 fixes the priority order:

1. latest explicit user decision
2. these two canonical English specifications
3. compatible October 25 completion documents
4. compatible October 22 master / Wave 6 detail
5. current repository per-wave documents
6. old v5 proposals and implementation descriptions

If two sources still contradict, preserve data and surface the exact conflicting
statements rather than inventing a third behaviour.

## 2. Pinned revisions

| Document | Bytes | SHA-256 |
|---|---:|---|
| `COPIMINE_END_RIFT_WAVES_CANONICAL.md` | 47 126 | `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf` |
| `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` | 51 711 | `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50` |

The canonical files live outside the repository at `D:\Downloads\`. They are
deliberately not vendored: they are the user's working copies, and copying them
here would create a second editable source of truth.

`tests/test_canonical_spec_pins.py` hashes each file when it is available and
skips when it is not (CI runners do not have `D:\Downloads\`). A changed hash is
a deliberate signal: the specification moved, so every requirement that cites it
must be re-read before the next change.

## 3. Re-verify

```powershell
python -c "import hashlib,pathlib; print(hashlib.sha256(pathlib.Path(r'D:\Downloads\COPIMINE_END_RIFT_WAVES_CANONICAL.md').read_bytes()).hexdigest())"
python -c "import hashlib,pathlib; print(hashlib.sha256(pathlib.Path(r'D:\Downloads\COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md').read_bytes()).hexdigest())"
```

## 4. Recorded user decisions that override a canonical line

The authority order puts a latest explicit user decision above the canonical
text. Seven such overrides are live and must not be "repaired" back:

| Canonical line | Live decision | Evidence |
|---|---|---|
| Wave 5: three cycles, 40/50/60 combat baselines | 5-10 cycles by frozen roster, shortening safe-zone run window, longer fog | `docs/end-rift/2026-11-10/WAVES_1_5_REPAIR.md` states the user's latest Wave 5 rules supersede the older three/five-cycle documents |
| Guardian ambush: grab-or-miss after a dodgeable warning | valid committed target is grabbed with 100 % success; miss animation is only for cancellation | Guardian specification section 6.5 itself records this as the latest override |
| Guardian section 6.3: individual 90-active-second defence respawns | a genuinely killed cocoon defender is **permanently destroyed** for the encounter generation; the boss regeneration stops permanently once the cocoon breaks | `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` says the user's newest decisions override older documents, "Remove the existing 90-second guardian respawn behavior", "Never respawn after being genuinely killed" and "The user explicitly wants a permanent final fight after the cocoon breaks" |
| Wave 2 section 4.1/4.3: 45-second mark periods, at least two complete periods, no five-second group pauses | the mark transfers approximately every **20 seconds** while the current group is alive, three finite groups, approximately **5-second** pause after a cleared group, elite enemies only in the third group | `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` Wave 2 section 3.1 lists exactly this behaviour and adds "Avoid changing the gameplay to an unrelated 45-second marker or endlessly spawning new groups" |
| Wave 2 section 4.2: roughly **25-35** stable gray phantom Endermen for the marked client | approximately **70** client-only spectral Endermen with a **50** figure safe fallback, identical on server (`WaveTwoPhantomLayoutPolicy`) and client (`WaveTwoPhantomLayout`) | `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` section 3.2 records the 35/25-versus-70/50 mismatch, states "The intended presentation is approximately 70 client-only spectral Endermen when the arena can support them", and requires "one compatible authoritative count contract" |
| Wave 6 section 8: Ritual Sphere, five channeling mages, a prisoner and the guard table | Wave 6 is the **Rift Ram** encounter: one oversized Ravager lured into the Core, four ordinary pillars plus one oversized final pillar (three hits) with a skeleton on top. The sphere, its mages, its prisoner and its guard table are **not the live encounter** | Latest user decision 2026-11-18 ("in Wave 6 we completely dropped the sphere mages"). The tree already agrees: `EndRiftObjective.objective(6)` returns `RIFT_RAM`, and `RITUAL_SPHERE`/`COLLAPSE_RINGS` are marked `isLegacy()` snapshot-only ids "never selected for live Wave 6". `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` section 6 is titled "WAVE 6 - RIFT RAM AND THE FINAL SKELETON PILLAR" |
| Wave 7 section 9.1: two-to-four isolated chambers with one-block partitions | the whole party shares **one sequential open arena**; the four named trials (Warden, Personal Echo, Mobile Marksman, Rift Archmage) run back to back with **no walled rooms** | Latest user decision 2026-11-18 ("in Wave 7 we completely dropped the rooms in favour of minibosses one after another"). The tree already agrees: `RealitySplitChamberController.beginWave7` delegates to `beginOpenArena`, and `docs/end-rift/2026-11-15/GUARDIAN_COMBAT_ANIMATIONS_AND_AUDIO.md` records the one-open-arena override as still in force |

Anything else that contradicts the canonical text is a defect, not a variant.

## 4b. Surfaced contradictions resolved by a user decision

Canonical section 0 says that when two sources still contradict, preserve the
data and surface the exact conflicting statements instead of inventing a third
behaviour.

**Wave 6 assigned guard slots - now moot.** The canonical table (section 8.2)
fixed total assigned guard slots at **5 / 10 / 15** for rosters of 2-4 / 5-8 /
9-20, while the shipped roster policy returned **1 / 2 / 3 / 4 / 5** guards *per
mage*. That conflict only ever applied to the Ritual Sphere, which the
2026-11-18 decision replaced with the Rift Ram encounter (row above). The live
Wave 6 no longer selects the sphere, so there is no over-guarding to resolve:

* `EndRiftObjective.objective(6)` is `RIFT_RAM`, never `RITUAL_SPHERE`;
* the sphere runtime entry points (`tickCurrentRitualSphereObjective`,
  `restorePersistedRitualSphereObjective`, `startRitualSphereObjective`) have no
  live caller;
* `WaveSixRosterPolicy.guardsFor` and `RitualSphereScalingPolicy` are reachable
  only from the encrypted sandbox/diagnostic harnesses and their unit tests.

The guard table therefore describes retired code, not a live contract. Cleanup
of that retired sphere code is tracked as a follow-up; it is unreachable in
production either way.

## 5. Artifact re-pin conventions

Two different pins exist and they are refreshed differently.

**Plugin candidate** (`tools/minecraft-26.3/server-plugins.lock.json`): tie the
pin to a real local build with the pinned JDK, then bump `candidate` `size`,
`sha256` and `sha512` **and prepend the replaced `sha256` to
`candidate.replaceSha256`**. Any change to `copimine-end-event` source, even a
comment, moves the jar.

**Client artifact** (`tools/minecraft-26.3/profile.lock.json`): rebuild with
`gradle --no-daemon --project-cache-dir build/minecraft-26.3/gradle-project-cache/client -p tools/minecraft-26.3/client test jar`
under the pinned JDK after clearing the three build directories, then bump
`clientArtifact` `size` and `sha512` together with the literals in
`tests/test_minecraft_migration_profile.py` and `docs/minecraft-26.3/README.md`.
Loom's `preserveFileTimestamps = false` and `reproducibleFileOrder = true` make
the output reproducible; a clean local build has matched the CI build byte for
byte. There is no `replaceSha256` list for this pin.

Historical documents under `docs/end-rift/<date>/` keep the digests that were
true on their date and are not rewritten.