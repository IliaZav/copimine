# End Rift Verification Log — 2026-10-31

Evidence only. Every number here was produced by a command in this worktree on
this host. Anything not measured is marked BLOCKED or NOT RUN, never assumed.

Worktree: `D:\Desktop\Copimine\copimine-source-of-truth\.worktrees\integration-20261030`
Branch: `codex/copimine-integration-20261030`
Baseline commit: `db1b732716915defec87db5169f83927e386f3d7`

## 1. Canonical spec re-verification

| File | Revision | Bytes | SHA-256 | Match |
|---|---|---|---|---|
| `D:\Downloads\COPIMINE_END_RIFT_WAVES_CANONICAL.md` | 2026-10-27.1 | 47126 | `6E1245B44A449A0EF1BAB9193606398660C68F6BB2DE7D7BB6082CC8CDE50CAF` | yes |
| `D:\Downloads\COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` | 2026-10-27.1 | 51711 | `0C781E42E274A3F27762E0B568CC1D8FFD7DFD8623E5A0F1DEB65EA73A94AE50` | yes |

Both must be re-read and re-hashed after every compaction before further
spec-dependent work.

## 2. User work preserved

Backup directory (outside tracked source):
`C:\Users\zavod\AppData\Local\Temp\opencode\copimine-backup-20261031-133320`

| File | Bytes |
|---|---|
| `tracked.patch` (`git diff --binary` of all tracked modifications) | 180034 |
| `status.txt` | 3984 |
| `RitualPrisonerPlacementPolicy.java` | 1588 |
| `test_tab_online_word_plural.py` | 2599 |
| `BlackFogSafeVolumePolicy.java` | 3637 |

23 modified + 5 untracked files at backup time. `.serena/` and
`CopiMineClient/bin/` are never staged.

## 3. Baseline test run (before any repair commit)

```
python -m pytest -q -ra tests --basetemp=C:/Temp/cpm-baseline-1334 -p no:cacheprovider
5 failed, 2901 passed, 15 skipped, 124 warnings, 2 subtests passed in 782.54s
```

Failures (all release-provenance drift, master defect #6):

1. `tests/test_ci_release_job_contract.py::test_client_artifact_size_documentation_matches_the_profile_lock`
2. `tests/test_minecraft_migration_profile.py::test_profile_lock_matches_the_repeatable_current_client_build`
3. `tests/test_minecraft_migration_server_plugins.py::test_end_event_26_3_candidate_tracks_and_installs_the_real_target_jar`
4. `tests/test_minecraft_migration_server_plugins.py::test_active_26_3_plugin_directory_matches_the_unique_locked_inventory`
5. `tests/test_own_plugin_inventory_contract.py::test_inventory_tests_and_installed_hashes_are_real`

The audit's "752 selected / 2 failed / 136 passed / 2 skipped" figure does not
reproduce on this tree; the measured value above is the one that counts.

Skips recorded as host/environment limits, not passes:

- 2 × boss projectile cover (scenario intentionally inert)
- `COPIMINE_PROFILE_CLIENT_JAR` unset (1)
- `pwsh.exe` / PowerShell Core unavailable on Windows PowerShell host (4)
- GrimAC.jar fixture intentionally ignored (1)
- `COPIMINE_TEST_POSTGRES_DSN` unset (2)
- POSIX archive mode bits on Windows (1)
- file symlink creation unavailable on Windows (2)
- POSIX private-key permissions (1)

## 4. Installed artifact baseline and its repair

| Artifact | Before (audit claim) | Now (measured, reproducible) |
|---|---|---|
| Installed EndEvent jar | `440e081e…77d73a` | `d53f0997a8f555235e7484d1c733b1c7c70d5d809caf557a3f421453065b4141` (1 804 590 B) |
| Staged candidate | `51b412ff…643d2f4` (2 019 018 B) | same digest as the installed jar |

`build-plugin.ps1` writes `copimine-end-event/CopiMineEndEvent.jar` and copies it
straight into the staging server, but it never refreshed
`build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar` — the file the
lock actually pins. Every source change therefore left the pin measuring an
older build, and each re-pin recorded whatever stale jar happened to be on disk.
That is the real root cause of the changing digests, not a nondeterministic
build.

Measured, not assumed: two consecutive `clean` builds of the current tree
produce the identical artifact (1 804 590 B / `d53f0997…b4141`), so the pin is
now reproducible from source. The superseded digest stays in `replaceSha256`
(67 entries) so the installer can still recognise an older approved build
instead of treating it as unknown.

## 5. Repairs verified

Filled in as commits land. Each row names the command actually run.

| Repair | Command | Result |
|---|---|---|
| Wave 5: client accepts all five fog cycles | `gradle -p tools/minecraft-26.3/client test --tests '*EndEventBlackFog*'` | BUILD SUCCESSFUL, 11 tests, exit 0 |
| Wave 5: server wire cycle reaches 5 | `pytest tests/test_wave5_black_fog_adapter.py` | 16 passed, exit 0 |
| Active 26.3 client suite | `gradle -p tools/minecraft-26.3/client test` | BUILD SUCCESSFUL, exit 0 |
| Release pin / modpack provenance | `pytest tests/test_minecraft_migration_profile.py tests/test_ci_release_job_contract.py tests/test_minecraft_26_3_pack.py` | 111 passed, 2 skipped, exit 0 |
| Wave 5: safe-volume radius replaced by footprint mask (W5-017) | `gradle -p tools/minecraft-26.3/client test` | BUILD SUCCESSFUL, exit 0 |

### 5.1 Wave 5 cycles 4 and 5 were silently invisible (AUD-04)

**Reproduced on the current tree.** `BlackFogTimingPolicy.CYCLE_COUNT` is 5 and
the server emits `<dimension>|wave5|<cycle+1>`, so a full run sends
`wave5|1` … `wave5|5`. `EndEventBlackFogManager.validCycle` accepted only
`"1"`, `"2"` and `"3"`, and `apply` rejects a packet whose mode is invalid.
The client therefore dropped cycle four and five: the arena rendered fully lit
while the server had already applied Blindness/Wither and the safe-volume
damage rule. That is the "weak/missing fog" symptom from the user reports, and
it is a protocol-range bug, not a rendering-density one.

The stale expectation was pinned by its own test
(`acceptsOnlyTheOneBasedThreeCycleRangeInTheRuntimeManager`, which asserted
`wave5|4` must be rejected). Both the production check and that expectation are
now aligned with the five-cycle contract, and the wire test asserts the server
really reaches protocol cycle 5.

While fixing it, the new `rejectsNonNumericCycleWithoutThrowing` test failed
against the first draft: `Integer.parseInt` threw `NumberFormatException` on a
capability-invalid string, which would escape into the client tick loop. The
validator now rejects non-digits explicitly, so a foreign or corrupted bridge
payload is a refused packet rather than a client crash.

### 5.2 The legacy 1.21.1 client carried the identical defect

`CopiMineClient/src` ships a second `EndEventBlackFogManager` with its own
`validCycle`, and it had the same three-cycle range, so the 1.21.1 line hid fog
cycles four and five exactly like the 26.3 line. Its test
(`acceptsOnlyTheOneBasedThreeCycleProtocolRange`) even asserted that `wave5|4`
"must" be rejected. Both were corrected the same way, and the staged
`thirdparty/client-mods/CopiMineClient-0.1.1.jar` was rebuilt so the modpack no
longer ships the old behaviour.

**Trap found while rebuilding it (cost three builds).** Running the legacy
project with this repo's pinned Gradle 9.8.1 makes Fabric Loom 1.8.13 resolve to
"Finished remapping", exit 0, and then write a **261-byte jar containing only
`META-INF/MANIFEST.MF`** — no error, no warning, `BUILD SUCCESSFUL`. Every
class silently disappears. The same project on its own isolated Gradle 8.10.2
(`CopiMineClient/.gradle-dist/gradle-8.10.2`, the one `RunJavaPluginCi.ps1`
uses) produces the full 356-entry, 13 035 666-byte jar. Always verify the
**entry count** of a client jar, never just the exit code, and never build the
1.21.1 client with the 26.3 Gradle.

**Third trap — the one that actually kept CI red.** Three contracts compare
`thirdparty/client-mods/CopiMineClient-0.1.1.jar` to a fresh `remapJar` output
**byte for byte**. The legacy build never set `preserveFileTimestamps = false`,
so Gradle stamped every entry with the wall-clock mtime; each CI rebuild
differed at the first entry and the artifact could never match a committed
copy, no matter how carefully it was staged. The 26.3 client already had both
flags. Adding them to the 1.21.1 build makes the jar reproducible: two
independent clean builds on this tree produce the same 13 035 588 bytes and the
same SHA-256 `bc67cd22…`, and an independent fresh build matches the staged
jar exactly.

**Second trap.** `build_modpack.ps1` only re-stages the client jar when passed
`-SyncBuiltClient`; without it, it rebuilds the archive from whatever stale jar
is already staged and still prints matching hashes. Passing that switch alone
does not prove freshness either — read back the staged jar.

### 5.3 Wave 5 safe-volume occlusion (W5-017): radius removed, per-pixel mask still blocked

**Superseded.** The safe volume used to keep a uniform
`SAFE_VIEW_RADIUS_BLOCKS = 4.0` readable radius. That is exactly what W5-017
rejects: from safety a player read terrain well outside the safe footprint,
could look over the fog wall, and could see a second emerald island across the
fog. The radius constant is gone.

**What replaced it.** `BlackFogSafeVolumePolicy.maskFor(...)` now builds the
readable region from the exact published safe cells, expanded by a bounded
`SAFE_MARGIN_BLOCKS = 0.5` so an occupant standing on the footprint boundary
still reads the floor under their feet. Two properties follow:

- `SAFE_READ_DISTANCE_BLOCKS` is **derived** from the widest footprint the
  server can publish (`sqrt(2) * (3/2 + 0.5)` ≈ 2.83 blocks), not an independent
  view radius, and is asserted to stay **below** the old 4-block value;
- `SafeMask.aroundCamera(...)` narrows readability to the island the camera
  actually occupies via a bounded 4-neighbour flood fill. A second safe island
  is emerald too, so a mask that accepted any published cell would have cleared
  the view straight through the wall.

`EndEventBlackFogMixin` now resolves the current island from the published
emerald floors (bounded flood fill, `MAX_ISLAND_CELLS = 64`) instead of testing
one block column, and feeds that mask to `fogEndBlocks`.

**Still NOT a per-pixel world mask, and this cannot be closed by local effort.**
Measured against the pinned 26.3 client jar, not assumed:

| Fact | Evidence |
|---|---|
| Post-chain uniforms are resolved at `PostChain.load` from the JSON and the frame only receives effect **ids** | `GameRenderer.preparePostEffects(List<Identifier>)` |
| Only `minecraft:main` and `minecraft:entity_outline` are offered as external targets | `LevelTargetBundle.MAIN_TARGETS` |
| No depth target is exposed to post chains | `MAIN_TARGETS` contains no depth id; depth lives on `RenderTarget.getDepthTexture()` |
| Shaders get `ProjMat` (no inverse, no view matrix) and `Globals` (camera block pos/offset) | `shaders/include/projection.glsl`, `globals.glsl` |

Without a depth sampler and an inverse view-projection a fragment cannot
reconstruct the world-space point of its ray, so an exact per-pixel volume mask
is not reachable through the pinned post-effect pipeline. The honest status is:
**the W5-017 radius shortcut is closed; the per-pixel mask remains BLOCKED by
the available rendering API**, and it is reported as such rather than claimed as
implemented. Native visual confirmation of the new boundary is still required
(section 6).

### 5.4 Wave 7 Echo — owners now share one region (ECHO-005) — CLOSED in code

**Fixed on this tree.** `ECHO-005` requires a shared combat region with
server-enforced owner-pair rules and *no individual walls*. The Wave 7 Echo stage
used to build owner-local lanes with physical separators, opened only after two
adjacent duels had both completed. That geometry is gone:

| Change | Location |
|---|---|
| One shared region plan; lanes are starting points, not cells | `Wave7EchoDuelCellPolicy.sharedRegionPlan` |
| Region access for every admitted owner, Core column and outside excluded | `Wave7EchoDuelCellPolicy.allowsSharedRegionAccess` |
| Movement/teleport gates use the shared region, not per-owner lanes | `CopiMineEndEvent` `isNamedEchoCellLocationAllowed` / `...TransitionAllowed` |
| Owner and copy health checkpoints regroup only on leaving the region | `CopiMineEndEvent` `checkpointNamedEchoHealthChanges` |
| No separator geometry, no per-owner world mutation, no rollback tickets | `CopiMineEndEvent` `namedEchoDuelBarrierPlan` / `spawnRealitySplitBarriers` |
| `Wave7EchoDuelCellBarrierPolicy` deleted | `domain/` |

Isolation is unchanged where it already worked: `EchoCombatAdmission` admits
damage and effects only between a copy and its own original, and every admitted
owner still gets exactly one `DuelRecord` simultaneously (ECHO-001/ECHO-002).

Regression coverage: `tests/test_echo_realms_and_pairs.py` executes the
production policy over 1-20 owners (one lane per owner, adjacent lanes share an
exact edge, the ring is covered, invalid rosters rejected, neighbouring-lane
movement admitted, Core column and outside rejected);
`tests/test_wave7_echo_cell_barrier_runtime.py` asserts no separator is ever
planned, built or restored; the legacy cell tests in
`test_wave7_named_runtime.py`, `test_wave7_passage_commit.py` and
`test_wave7_single_room_hazard_journal_recovery.py` were updated with a written
reason (`ECHO-005` supersession) rather than deleted.

Still open for this item: native two-client evidence that the two groups cannot
see or interfere with each other (section 6).

## 6. Native visual verification

BLOCKED. No authorised Minecraft client session was driven during this pass, so
no screenshot or video evidence exists for any wave. Per the contract this means
every visual requirement is **unverified**, not passed.

## 7. Final gates

| Gate | Status |
|---|---|
| `python -m pytest -q -ra tests` | **2928 passed, 15 skipped, 0 failed** (573.49 s, exit 0) |
| `tests/RunEndRiftEventChecks.ps1 -JavaHome <pinned> -LegacyClientJavaHome <jdk-21>` | **passed**, exit 0 — "End Rift current local checks passed." |
| `scripts/minecraft/RunJavaPluginCi.ps1` | NOT RUN yet |
| Active 26.3 client tests/build | **BUILD SUCCESSFUL**, exit 0 |
| PostgreSQL migration/integration | BLOCKED (`COPIMINE_TEST_POSTGRES_DSN` unset) |
| `git diff --check` | checked before commit |

The 15 skips are host/environment limits, not passes: no `pwsh` on this host,
no `COPIMINE_TEST_POSTGRES_DSN`, no Windows symlink privilege, POSIX archive
mode bits and private-key permissions, the intentionally-ignored GrimAC
fixture, and the two deliberately inert boss-projectile cover scenarios.