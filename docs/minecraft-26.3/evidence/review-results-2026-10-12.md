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

## Current migration branch review attempt — 2026-10-12

- CodeRabbit CLI 0.8.2 was authenticated and invoked on the full PR diff at current HEAD `bf55bd3e7119a5624d2b58704be74de3d20eb847`, base `7f722fd5e657a3d0b7307087dd936f5451791c3e`.
- The full-branch review did not run: CodeRabbit returned `too_many_files`, reporting 312 changed files against its 150-file limit. No findings were produced or stored; this is not a `0 issues` result. The documentation-only review completed separately with `0 findings`; the independent local re-review is tracked below.
- The first scoped documentation review produced six minor findings: pin Python via the absolute interpreter selected by `py -3.13`; repair a code fence; distinguish 13 locked artifacts from 12 separately installed JARs and bundled Fabric API; define the README's pinned Java paths; state GitHub exact-SHA CI as required and GitLab as diagnostic; and distinguish private working captures from the exact evidence files required in the signed CI bundle. All six were corrected in the current uncommitted documentation diff.
- CodeRabbit CLI 0.8.2 reran `coderabbit review --agent --dir docs --base-commit 7f722fd5e657a3d0b7307087dd936f5451791c3e` on 2026-10-12 and completed with `0 findings`. It reviewed the changed migration docs and evidence text; one PNG screenshot was excluded as unsupported binary input.
- Independent local review found two accuracy issues after those fixes: the 07:45 runtime validation timestamp was not supported by the recorded inventory manifest, and the review status wording did not distinguish completed CodeRabbit review from pending independent review. The source manifest timestamp is now identified as 06:13:45 MSK; the 07:56 on-disk check was later superseded by a repeatable 08:22 on-disk validation. Neither check establishes Paper process/listener liveness. The status wording separates the CodeRabbit and local-review results.
- CodeRabbit CLI 0.8.2 reviewed the docs scope again on 2026-10-12 and found one minor issue in the on-disk inventory transcript: the recorded command was pseudocode and omitted the repository-root definition. This was verified by rerunning the inventory validation successfully from PowerShell with Python 3.13; the transcript now records the executable command and its `29`-plugin pass output. Independent local re-review confirmed the corrected transcript and liveness boundary with no actionable finding.
- Independent local reviewers separately re-checked the corrected on-disk inventory transcript and refreshed Paper readiness evidence. They confirmed the transcript makes no process/listener claim, the Paper JAR hash/size match the lock, the loopback listener and `Done` log are directly observed, and no player login or gameplay claim is made. No actionable findings. The latest read-only process/listener/hash observation was repeated at 08:31 MSK; `nativeVerified` remains false.
- Final scoped review: `coderabbit review --agent --dir docs --base-commit 7f722fd5e657a3d0b7307087dd936f5451791c3e` completed on 2026-10-12 with `0 findings` across 28 migration documentation/evidence text files. The existing native-client screenshot was excluded as unsupported binary input. This is a documentation/evidence review; the full branch review remains unavailable because the CLI limit is 150 files and the branch diff is 312 files.
