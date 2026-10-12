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


## Fullset migration evidence review — 2026-10-12

- Scope: the seven-file evidence checkpoint based on source SHA `892b6980e88d40439f9364d9f951220ecd53c50c`: CI acceptance, progress ledger, validation evidence, two sanitized startup logs, focused-test transcript, and per-plugin hash manifest. This was a documentation/evidence-only diff; no production source changed.
- CodeRabbit CLI 0.8.2, authenticated agent mode, fresh uncommitted review: completed with `0 issues`.
- Independent local reviewer initially found three evidence-precision issues (single snapshot wording, PacketEvents update-check HTTP 403 omission, and manifest filename field description). All were corrected. Final read-only re-review found no actionable issues and confirmed the manifest and log hashes, plugin counts, test transcript, and `nativeVerified=false` boundary.
- These reviews assess the evidence/documentation diff. They do not replace native Minecraft login, plugin live-behavior, or recovery verification.

## Published CI-status ledger review — 2026-10-12

- CodeRabbit CLI 0.8.2 fresh review of `CI_ACCEPTANCE.md` and `MIGRATION_PROGRESS.md`: `0 issues`.
- Independent local reviewer confirmed both GitHub run IDs and all three successful jobs per trigger on exact SHA `e42b37deb593379a620f08173044e453beab3b0c`; verified native-evidence steps were skipped and `nativeVerified=false` remains accurate.
- The same review confirmed GitLab push/MR pipelines for that SHA failed before jobs with empty job lists, and the workflow/API warnings are described as warnings rather than test failures. No actionable findings.
