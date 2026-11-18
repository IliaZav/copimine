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
text. Two such overrides are live and must not be "repaired" back:

| Canonical line | Live decision | Evidence |
|---|---|---|
| Wave 5: three cycles, 40/50/60 combat baselines | 5-10 cycles by frozen roster, shortening safe-zone run window, longer fog | `docs/end-rift/2026-11-10/WAVES_1_5_REPAIR.md` states the user's latest Wave 5 rules supersede the older three/five-cycle documents |
| Guardian ambush: grab-or-miss after a dodgeable warning | valid committed target is grabbed with 100 % success; miss animation is only for cancellation | Guardian specification section 6.5 itself records this as the latest override |
| Guardian section 6.3: individual 90-active-second defence respawns | a genuinely killed cocoon defender is **permanently destroyed** for the encounter generation; the boss regeneration stops permanently once the cocoon breaks | `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` says the user's newest decisions override older documents, "Remove the existing 90-second guardian respawn behavior", "Never respawn after being genuinely killed" and "The user explicitly wants a permanent final fight after the cocoon breaks" |

Anything else that contradicts the canonical text is a defect, not a variant.

## 4b. Surfaced contradictions awaiting a decision

Canonical section 0 says that when two sources still contradict, preserve the
data and surface the exact conflicting statements instead of inventing a third
behaviour. This one is recorded rather than resolved.

**Wave 6 assigned guard slots.** The canonical table (section 8.2) fixes total
assigned guard slots at **5 / 10 / 15** for rosters of 2-4 / 5-8 / 9-20, with at
most **1 / 2 / 3** Rift Giant replacements.

The shipped roster policy returns **1 / 2 / 3 / 4 / 5** guards *per mage*
(five mages, so **5 / 10 / 15 / 20 / 25** total) as the roster grows:

* `WaveSixRosterPolicy.guardsFor` - 1 at 2-4, then 2 at 5-8, 3 at 9-12, 4 at
  13-16, 5 at 17-20;
* `RitualSphereScalingPolicy.GUARDS_PER_CASTER = 5` and its profile validation
  accepts one to five per mage.

The Rift Giant cap does match the canonical table: `RitualGuardAbilityPolicy`
uses 1 / 2 / 3 for 2-4 / 5-8 / 9-20.

The repository claims this ramp is a user requirement (`W6-009`):
`tests/RitualSphereScalingPolicyTest.java` says the 5/10/15 table is replaced by
"one guard at a 2-4 party, +1 per four players, up to the five-per-mage
ceiling", and `docs/end-rift/2026-10-31/UNIFIED_SPECIFICATION_REGISTER.md`
records the guard table as "max five, applied once (`W6-008`, `W6-009`)".

So rosters of 13-20 field **20 or 25** assigned guards where the canonical text
allows **15**. Either the W6-009 ramp is a genuine user decision that outranks
the canonical table, or the table is right and 13-20 is over-guarded. No source
in the tree decides which; the code therefore stays as shipped and the conflict
is recorded here for the user. Changing the ramp to 15/20/25 without a decision
would be inventing a behaviour, and so would shrinking 13-20 to 15.

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