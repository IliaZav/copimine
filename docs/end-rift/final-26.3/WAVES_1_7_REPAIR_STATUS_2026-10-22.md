# End Rift Waves 1–7 repair status — 2026-10-22

> **Архивный снимок, заменённый текущим отчётом** `WAVES_1_7_CURRENT_EVIDENCE_2026-10-22.md`. Записи ниже относятся к более раннему состоянию процесса Paper и кандидатов. Для текущих хешей, состояния сервера и тестов используйте новый отчёт.

## Continuation evidence — 2026-10-22

- Protected PostgreSQL backup: `local-runtime/migration-backups/database/end-rift-predeploy-20261022T010608Z-82432dba/copimine_migration_test.dump`, 254,679 bytes, SHA-256 `1bccb5b7c2628a803ccc7d9cc701b4fe22ca96160ce4ea8397893ccd35c0a3f8`. The PostgreSQL 16.15 custom archive listed 528 entries and restored with `--exit-on-error` into a separate temporary database. Verification found the expected schema, 102 base tables, and five migration markers; only that disposable database was dropped. The source database and its private files were not changed. Backup-directory ACL contains only the current user, SYSTEM, and local Administrators.
- `tests/RunEndRiftEventChecks.ps1 -SkipBuilds` was rerun on the current tree and exited 0. It passed the Wave 7 Echo probe (178), navigation/passage (25), current Python contracts (335), Wave 6 mechanics (50), Node bot/protocol tests (13), ritual restart contract (4), Java policy/runtime and persistence suites, artifact-hash verification, and `git diff --check`. Pillow deprecation notices and one bounded diagnostic-queue warning were non-failing.
- Codex Security diff scan `52e10002-dfd7-43a3-8191-64a7d654b9bb` completed against the exact working-tree snapshot `codex-security-snapshot/v1:sha256:e79ace267741d0fff03fc87182fa0a3a04541bca164cc5beaae76b9d1ce35776`: 49/49 selected paths reviewed, zero reportable findings, six covered surfaces. This is a diff review, not a repository-wide or runtime-attack audit.
- Paper PID 22072 is the official runtime at `127.0.0.1:25567`, with parent `StartMigrationTestServer.ps1` process 11976. Console input was attempted for `save-all flush` and `stop`, but no acknowledgement appeared and Paper remained running. The owner console is not attached to this Codex task; no force-stop, JAR replacement, or restart occurred. The 25566 listener is `qbittorrent.exe`, has no observed connection to PostgreSQL 55434, and was not changed.
- No consistent world snapshot was taken because the intended Paper process did not stop. The active level remains `world-codex-wave-20261018`; EndEvent `event-state.yml` remains `UNCONFIGURED`. Its config names `CopiMine`, which is not the active level. No blocks, arena settings, or event state were edited.
- Read-only arena discovery found no `WorldGuard regions.yml` for the active world, no WorldEdit schematics, and no EndEvent `event-layout.yml`. `StartMigrationTestServer.ps1 -FullPluginSet -ValidateOnly` was rerun and exited 1 at the strict active-inventory check; the official installer `--validate-only` exited 1 because the live server holds the lifecycle lock. Neither command started/stopped a service or replaced a plugin.
- The preserved `event-state.yml.bak` is older than `event-state.yml` but also says `UNCONFIGURED`, with an empty world and zero core/arena coordinates. It contains no recoverable arena layout and was not changed.
- CodeRabbit and Codex Security reviewed earlier working-tree snapshots, before the final dead-Skeleton callback guard. That final guard was manually reviewed and covered by a red/green regression plus the event gate, but neither external review was rerun afterward. The earlier reviews excluded two changed binary plugin JARs and do not establish runtime or gameplay acceptance.
- The normal-zombie response gap is now implemented: after a normal wave Skeleton receives confirmed player damage, up to two nearest eligible normal wave Zombies within 8 blocks defend that Skeleton for 7 seconds. The attacker lease cannot be stolen by later Skeleton hits; death, unregister, expiry, stale targets, and unrelated mob roles are covered. The response is queued only after the authoritative/native health delta confirms damage was applied.
- `WaveSkeletonProtectionPolicyTest` and the focused adapter/coordinator checks pass. The protection-focused runs reported 77 passed with 4 Pillow deprecation warnings, then 54 passed with the coordinator probe. The full event gate below also passed with the new policy included.
- The pinned client/bridge integration gate passed before the final dead-Skeleton callback guard. After that guard, two consecutive pinned Gradle 9.8.1 `migrationPlugins --rerun-tasks` builds produced identical EndEvent bytes. Current candidate size is 1,869,495 bytes; SHA-256 `ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c`; SHA-512 `e2066f0775483d626a7cc316c9f72e0a585cfc4ea4b9df7b75ec83fc171d6a6ce4cf369c7e0c518af29f610013dfcb3a7dba5d3f56dbb0b44a0472653fb17b79`. The lock and preserved evidence copy match; the candidate is not installed.

## Current state

The source tree, local database-backed Paper runtime, installed plugin files, client profile, and automated gates were inspected on 2026-10-22. The repository was already dirty when this work resumed; existing edits and untracked files were preserved. No commit or push was made. Wave 6 guard role, ranged-target, spider-bite, Enderman melee, and normal-zombie Skeleton-protection fixes are in source with passing policy/adapter coverage. The delayed response now revalidates that the Skeleton is still a live owned wave mob before assigning defenders. The latest candidate is locked at SHA-256 `ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c` and preserved in `artifacts/end-rift-candidates/wave6-skeleton-dead-target-20261022/`; it is not installed in the active Paper process. The active-inventory gate remains open.

The seven-wave implementation has source, policy, adapter-probe, protocol, and build coverage. **No wave was completed on the live Paper server in this verification run.** The event remains UNCONFIGURED, its V4 signer is unavailable, and the configured arena is empty. Live wave and multiplayer acceptance therefore remain open. nativeVerified=false.

The 2026-10-21 report and JSON are retained as historical snapshots and marked superseded below. Their runtime, test, and hash values are not current.

## Repository and execution scope

- Repository: D:/Desktop/Copimine/copimine-source-of-truth
- Branch: codex/copimine-source-of-truth-20261020
- HEAD: 88cd812a62fff626a760a6c00e4ea351d9bcc3e0
- Implementation commit in history: 7fb4cb3360bd58fc1ac1b03af3c3183f5c4f243f
- Current working tree: dirty; pre-existing tracked and untracked work was preserved.
- No subagents were started, following the owner's direct instruction.
- No production database, user-created End Rift arena, or unrelated world was modified.

## Wave implementation and verification boundary

| Wave | Current source and automated coverage | Live Paper / bot result |
| --- | --- | --- |
| 1 | Carrier custody, charge ownership, recovery, and delivery lifecycle policies are present; Java policy/runtime and contract gates passed. | Not run. |
| 2 | 45-second mark rotation and bounded server-authored phantom/materialization behavior are covered by policy, client-protocol, and contract checks. | Not run; no two-period mark scenario was executed. |
| 3 | Sequential portal capture, portal spawn, elite, and Rift Giant policies are covered. Portal layer creation rolls back partial display sets; focused regression passed. | Not run; no natural capture/spawn/completion scenario was executed. Portal orientation and appearance remain unverified in Minecraft. |
| 4 | Pressure packs, obelisk timing, physical fireball reflection, projectile provenance, and bot targeting checks passed their local gates. | Not run; no live fireball or reflection was observed. |
| 5 | Fog timing, safe-zone capacity, refill, effect lease, and lifecycle checks passed. | Not run; no live fog cycle was executed. |
| 6 | Five-caster progression and guard AI policies pass the local gates. The Enderman melee burst spans two pinned 20-tick vanilla attack windows (40 server ticks). Confirmed hits on normal Skeletons trigger at most two nearby normal Zombies; delayed callbacks reject dead or unregistered Skeletons. Policy, adapter, and coordinator regressions pass. | Not run; no prisoner, spell, Enderman melee, Skeleton-protection, or anvil fight was executed live. |
| 7 | Named trial actor, room separation, Echo duel, physical passage, return/grace, cleanup, and no-generic-elite policy checks passed. | Not run; no natural trial or handoff was executed. |

These results establish source and automated-gate status only. They do not establish normal or elite attacks against real players, wave completion, multiplayer scaling, server performance during waves, or visual correctness.

## PostgreSQL and Paper runtime

### PostgreSQL

- Version: PostgreSQL 16.15.
- Existing binaries: D:/Desktop/Copimine/copimine-source-of-truth/local-runtime/toolchains/postgresql-16.15/pgsql/bin.
- Data directory: local-runtime/end-rift-server-26.3/postgres-data; existing initialized data and private environment files were preserved.
- Listener: 127.0.0.1:55434; pg_isready accepted connections.
- Database: copimine_migration_test.
- Role: copimine_migration_test.
- Schema: copimine_migration_26_3_candidate.
- Authentication remains SCRAM-SHA-256 and the listener remains loopback-only.
- Before the EndEvent lock was reconciled, the official launcher read-only validator confirmed the owned Paper process, isolated database identity, and loopback boundary. After the candidate lock changed, its current `-ValidateOnly` path stops earlier at the strict active-plugin inventory check because the running Paper still has the previous EndEvent JAR. A separate read-only PostgreSQL lifecycle check now confirms the existing cluster and database identity without starting a service.
- An authenticated read-only inspection found 102 base tables. Existing ElectionCore migration records were present, including 20260611_001_plugin_postgres_v4.
- A private backup was previously restored into a disposable database and verified; the disposable database was then removed. No password or private environment values are included here.

### Paper

- Paper 26.3 build 205 is running on 127.0.0.1:25567 with Temurin 25.0.4.1.
- The active world remains world-codex-wave-20261018.
- server.properties remains loopback-bound with RCON disabled. AuthMe and AuthEffects remain enabled.
- The server reached Done. The full locked plugin set is loaded. EconomyCore, ElectionCore, Artifacts, and UltimateAdminPlus reported database readiness; Narcotics and EndEvent enabled.
- The non-EndEvent locked migration candidate JARs still match the active plugin inventory. The active EndEvent JAR remains SHA-256 `43a06dd862a1cc8ee0961867de38cd2ef7b065cb40237457303c5999accc299c`; its bytecode has `ENDERMAN_MELEE_BURST_TICKS=15`. The latest current-source candidate is 1,869,495 bytes, SHA-256 `ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c`, SHA-512 `e2066f0775483d626a7cc316c9f72e0a585cfc4ea4b9df7b75ec83fc171d6a6ce4cf369c7e0c518af29f610013dfcb3a7dba5d3f56dbb0b44a0472653fb17b79`. Two consecutive pinned Gradle 9.8.1 `migrationPlugins --rerun-tasks` builds produced identical bytes. The candidate lock, build output, and preserved evidence copy match. The candidate is not installed: Paper still holds the lifecycle lock, and the strict active-inventory gate correctly fails until a controlled installation and restart. The candidate manifest is not an installer receipt.
- The official plugin installer refuses validation while the Paper lifecycle lock is held. Paper is running as a child of an external PowerShell process, and no managed Codex terminal is attached; its JAR was not replaced or its process stopped.
- A fresh Codex app terminal read also returned “No app terminal session is attached to this thread yet”; the owning PowerShell console remains unavailable here.
- EndEvent reports phase UNCONFIGURED; event-state.yml has no arena world or bounds, and no event layout was found in the active runtime.
- Narcotics reports SIGNER_UNAVAILABLE with CONFIGURATION_ABSENT; protected bridge sends stay disabled. No approved private signing-key file or configured key path was available in the inspected runtime/environment.
- A fresh presence-only check found `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE` unset in the current process, user, and machine environment scopes; the runtime contains only its two PostgreSQL `.env` files. No key path or contents were printed. The external Paper process environment was not inspected; its startup log records `CONFIGURATION_ABSENT`.
- No CopiMine plugin startup errors were found. Paper did emit a non-fatal Windows OSHI performance-counter bootstrap error (0x80070057); the server continued to Done. AuthMe GeoLite and offline voice-chat encryption warnings remain.
- Port 25566 is currently owned by qbittorrent.exe (PID 18240), not Paper. It listens on several interfaces. It was not used or changed.

## Automated checks performed

| Check | Result |
| --- | --- |
| Full Python suite after the final dead-Skeleton guard | **1 failed**, exit 1: 2,291 passed, 1 failed, 8 skipped, 124 warnings, 2 subtests passed in 561.55 seconds. The sole failure is `test_active_26_3_plugin_directory_matches_the_unique_locked_inventory`: active receipt EndEvent SHA `43a06…` differs from locked candidate SHA `ac85d5…`. The strict assertion remains intact. Log: `%TEMP%/copimine-pytest-final-0b79706b16834017accd4c647f6344a2.log`. |
| Earlier full Python suite before the latest Wave 6 guard iteration | **Historical PASS**, exit 0: 2,277 passed, 8 skipped, 124 warnings, 2 subtests passed in 514.64 seconds; superseded by the latest full-suite result above. |
| Latest `tests/RunEndRiftEventChecks.ps1 -SkipBuilds` | **PASS**, exit 0 after the dead-Skeleton callback guard: Wave 7 Echo 178, navigation/passage 25, Waves 1/3/4/5 139 passed and 1 skipped, lifecycle 51, Wave 7 return 10, additional focused groups 8/6/3/64/2/3/75/149, current Python contracts 335, Wave 6 mechanics 50, Java policy/recovery suites including `WaveSkeletonProtectionPolicyTest`, Node bot reflection 13, ritual restart 4, artifact hashes, and diff hygiene all passed. Pillow deprecation warnings and one diagnostic queue warning were non-failing. |
| Skeleton-protection focused policy/adapter/coordinator checks | **PASS**: earlier focused runs reported 77 passed with 4 Pillow deprecation warnings, followed by 54 passed for coordinator plus protection. After manual review found that a lethal hit could enqueue a response after Skeleton death, the new `dead-skeleton` test failed first (expected RED), then the completed adapter suite passed 9/9 including dead and unregistered Skeleton cases. |
| Current-turn focused regression recheck | **PASS**, exit 0: `py -3.13 -m pytest tests/test_end_rift_guard_tactics_runtime.py tests/test_end_rift_wave6_visual_mechanics.py tests/test_wave6_ritual_caster_behavior_contract.py tests/test_migration_database_environment_output.py tests/test_migration_postgres_lifecycle.py tests/test_migration_postgres_restart_contract.py -q --basetemp <unique %TEMP% directory>`; 81 passed, 4 Pillow `Image.Image.getdata` deprecation warnings, 21.45 seconds. This verifies source/runtime-policy and database-preservation contracts, not live Paper gameplay. |
| PostgreSQL module dependency re-import regression | **PASS**, 2 passed: a disposable initialized-cluster fixture now reaches the guard after module imports and confirms the pre-existing env files are unchanged; the startup import-order contract also passes. This does not run against or modify the live database. |
| tests/test_end_rift_guard_tactics_runtime.py | **PASS**, 13 passed. Enderman remains in melee at ticks 120 and 139, exits at tick 140, and cannot restart before its cooldown. |
| tests/test_minecraft_migration_server_plugins.py before candidate-lock update | **PASS**, 137 passed, 1 skipped with the previous pinned candidate. Historical for the old lock state. |
| `javap -verbose` on preserved candidate and active EndEvent JARs | **PASS**. `ENDERMAN_MELEE_BURST_TICKS` is 40 in the current-source candidate and 15 in the active runtime JAR. |
| Previous isolated EndEvent candidate before Skeleton-protection update | **PASS**, two builds produced 1,862,930 bytes and matched SHA-256 `138ca3011bbbb6ee01a6819e2455bde5e021a910ca01737b33accbf4e1e91e6b`. Superseded by the current candidate below. |
| Node bot/protocol tests in the event gate | **PASS**, 13 passed, 0 failed. A separate protocol test run also passed 19 tests. |
| tests/RunClientBridgeV4Integration.ps1 with Temurin 25.0.4.1, Gradle 9.8.1, and -SkipEndRiftGate | **PASS**, exit 0 after the source fix. Client JUnit suite, Paper bridge unit suite, source guards, pinned migration plugin builds, plugin resources, and Paper API verification succeeded. It generated the current-source EndEvent candidate, but did not start Paper or load the private signer. |
| Pinned artifact rebuild and lock reconciliation | **PASS**, both `RunClientBridgeV4Integration.ps1 ... -SkipEndRiftGate` and a second `migrationPlugins --rerun-tasks` build exited 0 and produced identical EndEvent SHA-256/SHA-512/size. The candidate lock now matches those bytes. |
| Previous candidate after initial Skeleton-protection update | **PASS**, pinned client/bridge integration and migration plugin builds passed before the final dead-target guard; this 1,869,386-byte candidate is retained at `artifacts/end-rift-candidates/wave6-skeleton-protection-20261022/` and superseded below. |
| Current pinned EndEvent candidate after dead-target guard | **PASS**, two consecutive pinned Gradle 9.8.1 `migrationPlugins --rerun-tasks` builds exited 0 and yielded 1,869,495 bytes, SHA-256 `ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c`, and SHA-512 `e2066f0775483d626a7cc316c9f72e0a585cfc4ea4b9df7b75ec83fc171d6a6ce4cf369c7e0c518af29f610013dfcb3a7dba5d3f56dbb0b44a0472653fb17b79`. Lock, build output, and `artifacts/end-rift-candidates/wave6-skeleton-dead-target-20261022/CopiMineEndEvent-26.3.jar` match. Candidate is not installed. |
| Previous candidate-lock focused migration artifact checks | **4 passed, 1 failed**. The strict active-inventory test saw the active old SHA `43a06…`, while that earlier candidate was `567bb…` and had not been installed. Historical; the lock now points at the latest candidate below. |
| `tests/test_minecraft_migration_server_plugins.py` after the dead-target candidate-lock update | **136 passed, 1 failed, 1 skipped** in 17.49 seconds. Candidate, installer, and predecessor allowlist checks pass; the only failure is the strict active-inventory gate because runtime receipt SHA `43a06…` differs from locked candidate SHA `ac85d5…`. The assertion was not weakened. |
| copimine-end-event/build-plugin.ps1 with Temurin 25.0.4.1 | **PASS**, exit 0 with 7 deprecation warnings. The source/staging JAR was generated outside the active local-runtime plugin directory. |
| Pinned client build `gradle ... test jar` with Temurin 25.0.4.1 and Gradle 9.8.1 | **PASS**, 281 tests passed, 0 failed/skipped; the expanded Wave 2 head-look test covers north/east/south/west. |
| Repeated pinned client `jar --rerun-tasks` | **PASS**, same 15,010,246-byte CopiMineClient JAR and SHA-256/SHA-512 as the preceding `test jar` build. |
| `test_profile_lock_matches_the_repeatable_current_client_build` plus `test_copimine_client_mob_artifact.py` | **PASS**, 6 passed, 1 skipped. The profile lock was updated after a red check exposed the prior client digest. |
| Full `tests/test_minecraft_migration_profile.py` and `tests/test_copimine_client_mob_artifact.py` suite | **PASS**, 53 passed, 2 skipped in 88.41 seconds with a unique external pytest basetemp. |
| Full Python suite on the current dirty tree | **2,278 passed, 8 skipped, 3 failed** in 649.33 seconds. Follow-up fixed the stale client size and added the receipt-verified EndEvent predecessor SHA to the upgrade allowlist; the only remaining failure is strict active inventory because Paper still has SHA `43a06…` loaded while the candidate lock is `138ca…`. |
| Full Python suite after those follow-up fixes | **2,280 passed, 8 skipped, 1 failed** in 614.01 seconds; 124 warnings and 2 subtests passed. The sole failure is `test_active_26_3_plugin_directory_matches_the_unique_locked_inventory`: the running Paper receipt still names EndEvent SHA `43a06…` while the candidate lock expects `138ca…`. |
| Targeted follow-up: README size, runtime-upgrade allowlist, active inventory | **2 passed, 1 failed**. The failure remains the expected exact active/candidate EndEvent mismatch; the assertion was not relaxed. |
| InstallMigrationProfile.ps1 -ProfileDirectory D:/.minecraft/versions/Copimine | **PASS**, installed current locked client JAR and resource pack; both now match their candidate checksums. |
| InstallMigrationProfile.ps1 ... -ValidateOnly | **PASS**, validated the updated CopiMine 26.3 profile without launching a graphical client. |
| StartMigrationTestServer.ps1 ... -FullPluginSet -ValidateOnly before EndEvent candidate-lock reconciliation | **PASS**, historical read-only confirmation of the owned Paper process, loopback endpoint, and isolated PostgreSQL identity. |
| `tests/test_migration_database_environment_output.py`, `tests/test_migration_postgres_lifecycle.py`, `tests/test_migration_postgres_restart_contract.py` with an isolated pytest basetemp | **PASS**, 9 passed in 4.22 seconds. |
| `StartMigrationTestServer.ps1 -JavaHome <Temurin 25.0.4.1> -PostgresBin <PostgreSQL 16.15 bin> -FullPluginSet -ValidateOnly` after the earlier EndEvent candidate-lock reconciliation | **BLOCKED**, exit 1 at the strict active-inventory check: active EndEvent SHA was `43a06…`, earlier candidate SHA was `567bb…`. This check predates the latest Wave 6 candidate. It did not start or modify either service. |
| `Ensure-MigrationPostgresReady ... -StartIfStopped:$false` using the repository lifecycle module | **PASS**, exit 0 on the current recheck; PostgreSQL 16.15 is running from the owned data directory and the read-only identity check confirms host `127.0.0.1`, port `55434`, database, schema, and role. It did not start a service or change database state. |
| Current-turn existing-cluster identity recheck | **PASS**, exit 0: repository `Ensure-MigrationPostgresReady -StartIfStopped:$false` returned `Ready=true`, `StartedByThisCall=false`, PostgreSQL 16.15, and the expected loopback/database/schema/role identity. No service start or database write was requested. |
| Lifecycle/environment contracts plus strict active-inventory follow-up, rerun with a unique external pytest basetemp | **9 passed, 1 failed** in 4.15 seconds. All PostgreSQL lifecycle, restart, and secret-output tests passed; the only failure is the strict active inventory: Paper's receipt still has EndEvent SHA `43a06…`, while the current-source candidate lock is `138ca…`. An initial invocation hit pytest's `pytest-current` cleanup `PermissionError`; the fresh-basetemp rerun completed and produced this result. |
| `tests/test_migration_postgres_lifecycle.py`, `tests/test_migration_postgres_restart_contract.py`, `tests/test_migration_database_environment_output.py` after the runtime-validator diagnostic correction | **PASS**, 10 passed in 4.29 seconds. Includes the regression for accurate validation error labels; no database service or credentials were changed. |
| Official `StartMigrationTestServer.ps1 ... -FullPluginSet -ValidateOnly` against the latest EndEvent candidate | **BLOCKED**, exit 1 at strict active plugin inventory. The failure now reports `Migration runtime validator failed` and retains the underlying `Active plugin inventory differs from the locked 26.3 candidates` reason. ValidateOnly did not start or stop Paper or PostgreSQL. |
| Read-only Paper process and log inspection | **PASS** for process ownership: PID 22072 matched the pinned Temurin executable, runtime-root marker, port `25567`, and Paper 26.3 build 205 JAR; `latest.log` contains `Done`, all nine required plugin enable markers, and zero `[ERROR]` lines. The signer reason remains `CONFIGURATION_ABSENT`; no wave was run. |
| Focused Wave 3 portal and Wave 6 prisoner/client checks (`test_end_rift_portal_layer_runtime.py`, `test_end_rift_portal_presentation_contract.py`, `test_wave3_portal_spawn_runtime_contract.py`, `test_end_rift_prisoner_client_boundaries.py`, `test_minecraft_26_3_client_command_api.py`, `test_end_rift_wave6_visual_mechanics.py`) | **PASS**, 121 passed, 4 Pillow deprecation warnings. This is source/adapter evidence, not native visual or live-bot acceptance. |
| install_migration_server_plugins.py --validate-only, attempted after the current-source candidate build | **BLOCKED**, exit 1: the server holds the lifecycle lock. No bypass or installation was attempted. |
| pg_isready.exe -h 127.0.0.1 -p 55434 | **PASS**, accepting connections. |
| Read-only PostgreSQL migration verification | **PASS**, `copimine_migration_26_3_candidate.cmv4_schema_migrations` exists and all 4 expected ElectionCore migration markers are present; ElectionCore and EconomyCore both logged database-ready messages. The read-only catalog/marker queries did not change data or print credentials. |
| PostgreSQL log review | No authentication failures and no PostgreSQL error lines after current PID 30596 started at 23:41 MSK. The log contains older pre-start failures: one missing `donation_item_claims` table on Oct 20; 15 missing `president_terms` table attempts and one denied `data_directory` probe on Oct 21; and one shared-memory child reservation error (Windows code 487). Read-only catalog checks now find both tables, with 4/4 ElectionCore migration markers present. The current Paper startup log has 0 `[ERROR]`/FATAL/PANIC markers. |
| py -3.13 -m pip_audit -r requirements-security.txt | **PASS**, no known vulnerabilities in the audited requirements-file scope. |
| CodeRabbit uncommitted review | **0 issues** across 40 text files before the final dead-Skeleton callback guard; two changed binary plugin JARs were excluded. The final guard was manually reviewed and tested, but the external review was not refreshed. |
| CI for the current dirty working tree | Not run; there is no final committed SHA for this working-tree state. |

The 124 Python warnings are deprecation warnings from FastAPI/Pillow and are not test failures.

## Artifacts

| Artifact | Size | SHA-256 | Candidate/runtime or installed profile |
| --- | ---: | --- | --- |
| Paper 26.3 build 205 | 55,242,379 | a0a58f532f08054ea92eb916d3edb6ed3c86429d77175317304f6e995cce9968 | Locked server artifact inspected. |
| CopiMineEndEvent active runtime JAR | 1,859,571 | 43a06dd862a1cc8ee0961867de38cd2ef7b065cb40237457303c5999accc299c | Still loaded by Paper; burst value 15. Kept as an exact replaceable predecessor in the candidate lock. |
| CopiMineEndEvent latest current-source candidate | 1,869,495 | ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c | Two pinned builds matched; lock and `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar` match. Not installed. SHA-512: `e2066f0775483d626a7cc316c9f72e0a585cfc4ea4b9df7b75ec83fc171d6a6ce4cf369c7e0c518af29f610013dfcb3a7dba5d3f56dbb0b44a0472653fb17b79`. |
| Preserved latest candidate copy | 1,869,495 | ac85d5eb794612982963f03ed1226cf6ed7617fdad956111d09c2c0b337eb43c | `artifacts/end-rift-candidates/wave6-skeleton-dead-target-20261022/CopiMineEndEvent-26.3.jar`; manifest records source revision, pinned tool versions, both rebuild commands, and active-runtime boundary. Not an installer receipt. |
| Previous Skeleton-protection candidate | 1,869,386 | 4f6019d3d95efa5582a4613ad4da961c6b8731aecbce639254fee2952896a9e3 | Preserved at `artifacts/end-rift-candidates/wave6-skeleton-protection-20261022/CopiMineEndEvent-26.3.jar`; superseded by the dead-target revalidation fix. |
| Earlier preserved EndEvent candidate | 1,859,554 | 567bb428e36483baee15657d347c1e7afcf659540c3a8691f3657ad977ad25a8 | `artifacts/end-rift-candidates/wave6-enderman-40-tick-20261022/CopiMineEndEvent-26.3.jar`; superseded by the latest candidate above. |
| CopiMineEndEvent source build script output | 1,658,237 | 6372b2a8dd3385bbcd3463d2b71081747bde4c42aebdb22d52fa27e68b874fe2 | Local source/staging output from `copimine-end-event/build-plugin.ps1`; not the locked migration candidate or active runtime JAR. |
| CopiMineNarcotics | — | d7e46fcc2bcfb8f31216cb1ea51c53bf3f492d29bc3b8c227540cb38e8ced7e2 | Candidate and active runtime JAR match. |
| CopiMineArtifacts | — | 0c3d91b2f763f62797b04791341026d09384258dcb181e82230d76c9dd391d9a | Candidate and active runtime JAR match. |
| CopiMineEconomyCore | — | 7fd9c779e0a5ca80b5dad4effcc0bf59d5cbcd181be7400e10432b6e69cabd32 | Candidate and active runtime JAR match. |
| CopiMineElectionCore | — | d2256c6976cb88a2a98b372d5f7e1b1db63013a52fdcfe519a065e3896395b65 | Candidate and active runtime JAR match. |
| CopiMineUltimateAdminPlus | — | 70eb0caa57ae48d918170fd9fd4f9ce853bfd8c967315f774cccc606b4abe9f0 | Candidate and active runtime JAR match. |
| CopiMineWorldCore | — | 1b6822c1733b8ddc3dc12760e8319caed014f3148493bf1c826ca6f5153e3f14 | Candidate and active runtime JAR match. |
| AuthEffects | — | 09aa0017cd8731470eb2324af327fc5780490c317469881f82a8d8c5bfd5e11c | Candidate and active runtime JAR match. |
| CopiMineClient 0.1.3+26.3 | 15,010,246 | 7109be41f04d8bce5a880712d7a5b7350f08a894fc312205f08b1c9d2914b5c1 | Candidate and installed profile match. SHA-1: ceddfbf9e1ffc20e71a67c5b01ca0425b460281d; SHA-512 matches `profile.lock.json`. |
| CopiMineResourcePack 26.3 | 36,252,428 | 7229299416f31e9c22086b20b2adf397b8b5d3d4cf42ef969517b7f484173ca1 | Candidate and installed profile match. SHA-1: ae863669efb0bb08210ef268f2a74f4ea247552a. |

## Live acceptance and remaining blockers

No official bot roster was run in this verification pass:

| Participants | Result |
| ---: | --- |
| 2 | Not run; V4 signer unavailable and event arena is unconfigured. |
| 5 | Not run; blocked by the same prerequisites and by the two-player gate. |
| 10 | Not run; blocked by the same prerequisites and by the two-player gate. |
| 11 | Not run; blocked by the same prerequisites and by the two-player gate. |
| 20 | Not run; blocked by the same prerequisites and by the two-player gate. |

Earlier two-bot setup timed out waiting for authenticated V4 bridge readiness and never started a wave. It is not a wave pass.

Required next inputs and work:

1. Identify the existing approved Ed25519 private-key file path or the approved local configuration mechanism. Do not paste the key or password into chat. Do not generate a replacement key or change the pinned public trust anchor.
2. Identify the intended existing arena world and its owned layout/coordinates, or the authoritative event-layout file. The active EndEvent configuration is empty; no arena or world was changed.
3. Stop Paper normally from its owning console after a save completes, then use the official installer to promote the current-source EndEvent candidate and verify the receipt. Do not replace a JAR while Paper is running.
4. With the signer and existing arena configured, run the official two-player natural gameplay scenario first, then the 5/10/11/20 participant matrix, required restarts/failure cases, performance measurements, and wave-specific combat acceptance. Do not use forced wave completion as evidence.
5. Perform the manual F2/video checklist in VISUAL_VERIFICATION_WAVES_1_7.md; keep nativeVerified=false until real gameplay evidence is captured and inspected.
6. Review and commit the current dirty worktree only after the owner's existing local edits are identified and the live acceptance gaps are resolved. No commit or push was made during this pass.

## Historical report

The previous snapshot remains at WAVES_1_7_REPAIR_STATUS_2026-10-21.md and its JSON companion. It is retained for audit history only and is not the current status.
