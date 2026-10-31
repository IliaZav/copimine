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
| _pending_ | | |

## 6. Native visual verification

BLOCKED. No authorised Minecraft client session was driven during this pass, so
no screenshot or video evidence exists for any wave. Per the contract this means
every visual requirement is **unverified**, not passed.

## 7. Final gates

| Gate | Status |
|---|---|
| `python -m pytest -q -ra tests` | see §5 final row |
| `tests/RunEndRiftEventChecks.ps1 -JavaHome <pinned>` | NOT RUN yet |
| `scripts/minecraft/RunJavaPluginCi.ps1` | NOT RUN yet |
| Active 26.3 client tests/build | NOT RUN yet |
| PostgreSQL migration/integration | BLOCKED (`COPIMINE_TEST_POSTGRES_DSN` unset) |
| `git diff --check` | pending |