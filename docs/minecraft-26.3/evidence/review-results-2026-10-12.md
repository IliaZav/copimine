# Migration review evidence — 2026-10-12

## CodeRabbit

- CLI: `coderabbit` 0.8.2, authenticated agent review.
- Command: `coderabbit review --agent --uncommitted --fresh --dir scripts/minecraft`.
- Result: completed, `0 issues`; reviewed `scripts/minecraft/InstallMigrationProfile.ps1` and `scripts/minecraft/inspect_legacy_release_client_bridge.py`.
- The review followed the YAML merge override regression fix and profile JSON ID/case handling change.

## Independent local reviewer

- Read-only review of `InstallMigrationProfile.ps1` and `tests/test_minecraft_migration_profile.py`.
- Result: no actionable findings. Confirmed the installer uses the profile JSON ID, permits only a case-insensitive directory name match, and the Windows regression test exercises a case-only rename without changing the profile JSON ID or receipt.
- The reviewer did not run tests; the focused regression result is recorded separately in the validation evidence.

## Codex Security

- Scan ID: `6554f4da-4bfc-414c-a913-40f24bac89d1`.
- Immutable range: `00f2c6927c9db3c58599fc77e257c6d1ea623061..10c174ac103a3ddaeb84b91db0a0b97baa9d694c`.
- Result: complete coverage of 25 changed files, 0 reportable findings.
- A candidate noted that a deliberately modified client can forge the public compatibility HELLO. It was assessed as not applicable to server security because it grants no extra privileges; the limitation remains a product compatibility boundary documented in the source docs.
- This scan predates the profile case fix and YAML merge override fix. A new scan of the final commit is required and will supersede it for the published candidate.
