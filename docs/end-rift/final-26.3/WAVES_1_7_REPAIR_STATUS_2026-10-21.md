# End Rift Waves 1–7 repair status — historical snapshot from 2026-10-21

> Superseded by [the current evidence report](WAVES_1_7_CURRENT_EVIDENCE_2026-10-22.md). The runtime ports, artifact hashes, full-suite results, and profile status below describe the 2026-10-21 snapshot and must not be treated as current.

**State:** source implementation committed and local automated checks pass. Full player acceptance is still incomplete: the active `25567` server has an older EndEvent JAR, and the current candidate bridge reports that its V4 signing service is unavailable. No wave was completed by live players or headless bots. `nativeVerified` remains `false`.

## Implemented

| Wave | Changes in the committed source |
| --- | --- |
| 1 | Carrier custody and charge ownership are fenced to the active event/player lifecycle; recovery and delivery transitions have policy/runtime coverage. |
| 2 | Hunt timing and rotation follow the current 45-second/period contract; phantom positions and materialization are server-authored, bounded, generation-fenced, and rendered by the 26.3 client. The recent review fix prevents an unseen event's `CLEAR` packet from retiring the current event layout. |
| 3 | Sequential portal capture and portal-elite/Rift Giant policies are implemented and covered by runtime contract probes. |
| 4 | Pressure packs, bounded retries, obelisk assault, and physical reflected-fireball handling have policy, adapter, and bot-contract coverage. |
| 5 | Black-fog timing, safe-zone capacity, and wave-coordinator lifecycle rules are covered by policy and adapter tests. |
| 6 | Ritual Sphere capture, prisoner targeting, five-caster progression, spell/channel admission, physical anvil impacts, and runtime lifecycle are implemented with dedicated policy and adapter tests. |
| 7 | Chamber/trial lifecycle, participant return, entity cleanup, and no-generic-elite constraints have policy and lifecycle coverage. |

Supporting changes include V4 bridge fencing, plugin/database readiness handling, local test-runner hardening, and the acceptance checklist at `VISUAL_VERIFICATION_WAVES_1_7.md`. Boss and Kagune redesign work remains out of this Waves 1–7 scope.

## Verification

| Check | Result |
| --- | --- |
| `tests/RunEndRiftEventChecks.ps1 -SkipBuilds` | **PASS**, exit 0. The gate completed all local End Rift checks; its aggregate Python contract section reported 328 passed and 79 deprecation warnings. Java policy/runtime probes completed successfully. |
| 26.3 client `gradle --no-daemon test build` on pinned Temurin 25.0.4.1 / Gradle 9.8.1 | **PASS**, `BUILD SUCCESSFUL`. |
| Wave 2 phantom/callback Python contracts | **PASS**, 7 passed. |
| `tests/test_minecraft_migration_profile.py` | **PASS**, 48 passed, 1 skipped. |
| Client artifact README-size contract after lock/documentation update | **PASS**, 1 passed. |
| PostgreSQL junction-path guard, isolated rerun | **PASS**, 1 passed. |
| Full Python suite | Run result: **2,244 passed, 8 skipped, 7 failed, 124 warnings, 2 subtests passed**. Two of those failures were corrected/retested afterward: the README size assertion now passes, and the junction-path check passed in an isolated rerun. The remaining five failures are listed below; the full suite was not rerun after those two focused retests. |
| `pip_audit -r requirements-security.txt` | **PASS**, no known vulnerabilities in that requirements-file scope. |
| CodeRabbit CLI review | **0 issues** across 113 reviewed files. |

The full Python run's five remaining failures are:

1. `tests/test_copimine_client_mob_artifact.py::test_dev_jar_is_compiler_output_and_remap_output_is_newer` — legacy 1.21.1 client refmap differs from its compiler output.
2. `tests/test_integrated_artifact_catalog.py::test_latest_scythe_and_shield_catalog_values_are_preserved` — test expects `proc-chance=0.20`; current catalog value is `0.3`.
3. `tests/test_minecraft_migration_server_plugins.py::test_all_remaining_first_party_26_3_plugins_track_their_exact_build_and_runtime_jars` — an installed JAR in the active runtime is stale versus the locked candidate.
4. `tests/test_minecraft_migration_server_plugins.py::test_end_event_26_3_candidate_tracks_and_installs_the_real_target_jar` — the active `25567` runtime contains an older EndEvent JAR, SHA-256 `ede85b42fd29c8d917b552a5d4028fa9a9ed9a129f8db0b6982f2b93c19893bf`, while the built candidate is `9274112cc63ed39e62b1a6e3f5d7284b9d92876137c248f7507d65b0b61b1118`.
5. `tests/test_minecraft_migration_server_plugins.py::test_active_26_3_plugin_directory_matches_the_unique_locked_inventory` — active `25567` plugin inventory does not match the current lock.

The three runtime inventory failures were not “fixed” by replacing files under the running server. That runtime contains the selected world and was left untouched.

## Runtime evidence

- PostgreSQL 16.15 is running on `127.0.0.1:55434`; `pg_isready` accepted connections. A read-only authenticated query confirmed database `copimine_migration_test`, role `copimine_migration_test`, schema `copimine_migration_26_3_candidate`, loopback binding, SCRAM-SHA-256, and 102 base tables in that schema. No credential values or private environment-file details are recorded here.
- The `25567` Paper 26.3 build 205 server is already listening on loopback with AuthMe/AuthEffects and database-backed plugins enabled. Startup logs show EconomyCore, ElectionCore, Artifacts, and UltimateAdminPlus database readiness; EndEvent loaded in `UNCONFIGURED` state. The log also says the V4 signer is unavailable, so protected bridge controls remain disabled. The installed EndEvent JAR does not match the current candidate hash above.
- A separate loopback test server on `25566` has the current EndEvent candidate installed. Read-only RCON status showed `COLLECTING`, wave 0, zero roster/participants, and zero event mobs. This is server-idle evidence only.
- The PostgreSQL log contains one Windows shared-memory reservation warning (error code 487); PostgreSQL remained ready. No Paper `ERROR`/`SEVERE` startup lines were found in the inspected startup log.
- The previous official two-player bot attempt connected and completed setup but timed out waiting for authenticated V4 bridge readiness. It did not start a wave. Five-, ten-, eleven-, and twenty-player acceptance runs did not proceed because the two-player prerequisite did not pass.
- No wave-level performance measurements, native Minecraft gameplay inspection, or genuine F2 screenshots were produced. No synthetic image or lower-level check is substituted for that evidence.

## Locked artifacts

| Artifact | Size | SHA-256 / SHA-512 |
| --- | ---: | --- |
| Paper 26.3 build 205 | 55,242,379 | SHA-256 `a0a58f532f08054ea92eb916d3edb6ed3c86429d77175317304f6e995cce9968` |
| EndEvent 26.3 candidate | 1,851,218 | SHA-256 `9274112cc63ed39e62b1a6e3f5d7284b9d92876137c248f7507d65b0b61b1118`; SHA-512 `2ebe3998459f0fd0ff9e094aec685430d46e59209b2e924b2d3ba4502ffa5fa3e8a496247277293022c5b563d41aefdd66749f5777b68e8d264a51c69f77daa5` |
| CopiMineClient 26.3 candidate | 15,008,511 | SHA-256 `dbe0cad70a8772ff9caea4e7b180ee61f649e14932249386f32526e01c1f0e9c`; SHA-512 `fac8a2282f2e19ee16117655dbfb65686fab6377f2d01e9ca38e55a084eb8e1c7603f4c96f482ff329d1d4ec71f121438d2c53031097ea4c7cc9ce6e75e09a71` |
| Migration resource pack | 36,252,428 | SHA-256 `7229299416f31e9c22086b20b2adf397b8b5d3d4cf42ef969517b7f484173ca1` |

## Remaining acceptance work

1. Provision the existing V4 signer through the approved local secret mechanism; keep the pinned client trust anchor and fail-closed behavior intact.
2. Schedule a safe stop/start of the isolated candidate runtime, install the locked first-party plugin inventory, and verify that the intended server loads the current EndEvent JAR. Do not replace jars in the live `25567` process.
3. Rerun the official two-player end-to-end bot acceptance. After it passes, run the remaining roster sizes and capture wave-specific performance evidence.
4. Perform the documented native Minecraft scenarios twice, inspect genuine screenshots, and keep `nativeVerified=false` until those checks pass.
5. Revisit the five full-suite failures above in their correct scopes, then rerun the complete Python suite.

## Commit

- Implementation commit: `7fb4cb3360bd58fc1ac1b03af3c3183f5c4f243f` (`fix(end-rift): repair waves 1-7 mechanics`).
- Changes are local to `codex/copimine-source-of-truth-20261020`; no push was performed.
