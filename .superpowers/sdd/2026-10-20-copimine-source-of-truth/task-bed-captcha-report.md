# bed-captcha-fix: selective source integration

Status: source integration complete; focused automated checks pass; awaiting independent review. No push, deployment, server startup, or native Minecraft verification was performed. `nativeVerified=false`.

## Scope and provenance

- Worktree: `D:\Desktop\Copimine\copimine-source-of-truth`.
- Branch: `codex/copimine-source-of-truth-20261020`.
- Initial observed HEAD: `e6110bc8b2781ce6250012ab2defe4dec13511e6`; refreshed immediately before edits to `7c82d4223cbef3bc2e0b4123c6c61f542900f6eb` after the controller's documentation commit. Both preflights were clean.
- Source branch tip: `3d590abc`, `origin/codex/bed-captcha-fix`.
- Common ancestor: `9682331c5a932113cefb6ce95ece4ae3fe6aeeab`. The eight source commits diverge from an older base; whole-file restoration would downgrade current AuthEffects, narcotics, treasury and hardening changes.
- Remote checked: `origin=https://github.com/IliaZav/copimine.git`; GitLab remote and linked worktrees inventoried read-only. No other worktree was edited.
- Integration used commit-local `git diff <commit>^ <commit> -- <selected paths>` patches, each checked using `git apply --check -` before `git apply -`. No merge/cherry-pick or whole-file checkout was used. The backend regression function was extracted from commit-local source and inserted while preserving the current cleanup and test ordering.

## All eight commits

| Commit | Decision | Reason |
| --- | --- | --- |
| `eca69da3` | Excluded | Current AuthEffects already permits captcha; current hardening already disables Essentials respawn override. JAR excluded. Existing canonical tests cover this behavior. |
| `07220947` | Selected | Memoizes public donation-catalog availability by file mtime and size under a lock. Includes its concurrency/invalidation test. |
| `e6e85d79` | Selected | Configurable public status/skin request limits with portable defaults 120/60 and minimum 50. Includes route contract test. |
| `1aa67c39` | Excluded | Live capacity settings are host-specific: Paper IO/worker thread counts and server.properties player/view capacity. server.properties explicitly prohibited by brief. |
| `6119ccb3` | Excluded | Current apply_game_hardening.sh already resyncs Essentials after startup and before final ownership restoration, covered by the stronger current bed-respawn contract. |
| `09278625` | Excluded | Canonical `f26eaa47` already supersedes this implementation with NarcoticSigningKeyring, maximum eight legacy keys and stronger tests/installer hardening. Candidate's unbounded legacy loader and JAR would regress it. |
| `65b51abd` | Selected with safeguard | Resolve player administration/profile lookups through durable links; show credential controls for authorized administrators even with stale site cache. Preserve current tests and add a rejected-stale-identity regression and actual identity check after hydration. |
| `3d590abc` | Excluded | AuthMe unregister removes authentication registration, while the proposed button, confirmation and success toast describe logout. The endpoint also accepts empty/unrecognized RCON responses as success. This feature is not safely applicable as written; no partial endpoint/UI/test imports were made. |

## Selected files

- `admin-web/backend/main.py`: memo/rate-limit integration and durable player identity lookup used by profile/update/delete/name lookup, retaining canonical treasury and auth source.
- `admin-web/backend/public_config_cache.py`: new portable memo helper.
- `admin-web/frontend/assets/js/cabinet-runtime.js`: existing credential controls depend on administrator permission, with backend identity resolution handling the account link.
- `admin-web/scripts/backend_security_regression_test.py`: active durable-link administration regression plus new mismatch rejection regression; existing test cleanup preserved.
- `admin-web/scripts/frontend_backend_contract_test.py`: credential visibility assertions from `65b51abd`; no unregister assertions.
- `tests/test_public_config_catalog_cache.py`: concurrent reads share a load; catalog changes invalidate cache.
- `tests/test_public_rate_limit_contract.py`: public endpoint rate-limit contract.
- This report is safe documentation. Other branch documentation was not needed.

## Explicitly excluded files

- `copimine-narcotics/CopiMineNarcotics.jar`, `minecraft/server/plugins/AuthEffects.jar`: binaries/generated artifacts.
- `minecraft/server/server.properties`: prohibited runtime configuration.
- `minecraft/server/config/paper-global.yml`: host-specific capacity tuning.
- `copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java`, `deploy/ubuntu/copimine_unpack_and_verify.sh`, `tests/test_narcotics_signing_contract.py`: newer canonical implementation retained.
- `deploy/shared/harden_game_runtime.py`, `deploy/ubuntu/apply_game_hardening.sh`: equivalent or stronger canonical behavior retained.
- `minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java`: captcha already present and current AuthMe exemption work retained.
- `tests/test_bed_and_captcha_contract.py`: duplicate/weaker than canonical `test_bed_respawn_contract.py` and `test_launcher_auth_slowness_contract.py`.
- Unregister-specific hunks in main.py, cabinet-runtime.js and both admin-web test scripts: excluded for the reasons above.

Configuration/script diffs were inspected without executing deployment scripts. Candidate signer paths are configurable durable runtime locations, not embedded secret values; they are nevertheless excluded because the canonical code supersedes them. No credentials, private keys, live configuration values or runtime files were imported. Selected rate settings only add environment variable names and portable numeric defaults. All generated test output is confined to ignored scratch/cache paths and excluded from the commit.

## Self-review ruling and RED/GREEN evidence

The imported identity resolver returned the first hydrated cached account even when hydration replaced its UUID with another active link. A stale cache could therefore select another identity for account administration. Added `assert_player_identity_lookup_rejects_a_stale_cache_for_another_link`; ran the full backend regression script and observed `AssertionError` at the stale UUID lookup (exit 1). Added a post-hydration UUID match check, or case-insensitive name match when UUID is absent; mismatches continue to the next durable lookup. Reran the complete script: exit 0, `Backend security regression test OK`. The new test covers stale UUID rejection, stale name rejection and successful lookup of the actual linked identity. This is the only corrective behavior beyond the selected source patches.

## Commands and results

Read-only comparison:

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
git remote -v
git worktree list
git merge-base HEAD origin/codex/bed-captcha-fix
git log --format='%h %s' --reverse HEAD..origin/codex/bed-captcha-fix --name-only
git diff --stat HEAD...origin/codex/bed-captcha-fix
git diff 9682331c HEAD -- <candidate source paths>
git diff HEAD...origin/codex/bed-captcha-fix -- <candidate source/config/script/test paths>
git log --oneline 9682331c..HEAD -- <already implemented paths>
```

Focused final checks (run from the target worktree):

```powershell
py -3.13 admin-web/scripts/backend_security_regression_test.py
py -3.13 -m pytest -q --basetemp=.superpowers/sdd/2026-10-20-copimine-source-of-truth/pytest-bed-captcha-final tests/test_public_config_catalog_cache.py tests/test_public_rate_limit_contract.py tests/test_bed_respawn_contract.py tests/test_narcotics_signing_contract.py tests/test_launcher_auth_slowness_contract.py
py -3.13 admin-web/scripts/frontend_backend_contract_test.py
git diff --check
```

Results:

- Backend regression: exit 0, `Backend security regression test OK`. Existing audit/panel writer failure simulations intentionally emit stack traces; these assertions passed. Existing Starlette httpx deprecation warning is informational.
- Focused pytest: exit 0, **13 passed in 1.81s**.
- Frontend/backend contract: exit 0, `routes=226 references=152 direct_calls=151`.
- `git diff --check`: exit 0, only Git's informational LF/CRLF notices.
- Initial generic Python 3.14 dependency probe lacked FastAPI. Initial pytest under Python 3.14 passed assertions but failed during cleanup of the global temp `pytest-current` symlink with WinError 5; no cleanup workaround touched that shared path. Switched to existing Python 3.13 with installed backend dependencies and explicit worktree scratch `--basetemp`; final focused run completed cleanly.

Limits: these are source and automated isolated SQLite/contract checks. They do not prove PostgreSQL concurrency, deployed RCON behavior, live server mechanics, or native client acceptance. No full build was needed for the selected Python/JavaScript source changes; excluded Java files were not rebuilt.


## Independent review: fix round 1

Base for this separate fix commit: `18e61ca7f94b9204ee68d38f89625d7cd9f2190d`. Implemented the two important identity findings and the minor rate-test finding in one fix round. No push or runtime operations.

Changes:

- Candidate queries now iterate every ordered result for both UUID and name, including both durable link sources. Removed the six resolver `LIMIT 1` clauses; rejecting a hydrated identity continues to the next result in the same source before moving to the next source.
- Added `lock_player_account_identity`, shared by credential update and deletion. In the same transaction it locks the account using `FOR UPDATE`, rejects a changed cached UUID, locks relevant Minecraft and whitelist link rows using `FOR UPDATE`, and revalidates the effective active durable identity and target ownership. Changed/revoked/reassigned identities return HTTP 409 before credentials, whitelist, or deletion statements are changed. Accounts predating durable links retain their valid cached-identity behavior.
- Deletion explicitly pins whitelist removal and associated identity cleanup to the originally requested UUID/name, after locked validation.
- Rate-limit test computes the production constant assignment expressions from `main.py` with a controlled environment, then resolves the actual route argument. Removed duplicated production defaults from the test. It checks the minimum with low configured values and verifies an explicit configured value is honored.
- Added `tests/test_player_identity_admin_contract.py` with four candidate-iteration scenarios (cache UUID/name and both durable name sources), four deterministic rebind scenarios (update/delete, cache plus link/link-only), and a valid-link deletion/whitelist target check. Existing backend regression checks continue to cover valid credential updates and durable-link updates.

RED evidence:

```powershell
py -3.13 -m pytest -q --basetemp=.superpowers/sdd/2026-10-20-copimine-source-of-truth/pytest-review-red tests/test_player_identity_admin_contract.py
py -3.13 -m pytest -q --basetemp=.superpowers/sdd/2026-10-20-copimine-source-of-truth/pytest-review-race-red tests/test_player_identity_admin_contract.py -k 'mutation or valid_link'
```

The initial run reproduced all four candidate failures. Its deletion fixtures initially lacked legacy production relation tables; those empty disposable tables were added to the fixture. The corrected race run then demonstrated **4 failed, 1 passed, 4 deselected**, with all four failures being the intended `DID NOT RAISE HTTPException` assertions, and valid deletion passing. This establishes the update and delete race regressions independently of missing-table fixture errors.

Final verification:

```powershell
py -3.13 -m pytest -q --basetemp=.superpowers/sdd/2026-10-20-copimine-source-of-truth/pytest-review-green tests/test_player_identity_admin_contract.py tests/test_public_rate_limit_contract.py tests/test_public_config_catalog_cache.py
py -3.13 admin-web/scripts/backend_security_regression_test.py
py -3.13 -m pytest -q --basetemp=.superpowers/sdd/2026-10-20-copimine-source-of-truth/pytest-review-compat tests/test_public_rate_limit_contract.py tests/test_bed_respawn_contract.py tests/test_narcotics_signing_contract.py tests/test_launcher_auth_slowness_contract.py
py -3.13 admin-web/scripts/frontend_backend_contract_test.py
git diff --check
```

Results:

- Focused identity/cache/rate suite: exit 0, **12 passed, 2 subtests passed in 68.21s**. Existing FastAPI lifespan deprecation warnings only.
- Complete backend security regression: exit 0, `Backend security regression test OK`; existing intentional audit/panel write failure simulations logged as before. Full output retained only in ignored `backend-review-fix.log`.
- Compatibility/rate suite: exit 0, **13 passed, 2 subtests passed in 2.07s**. The two rate tests overlap with the focused run; there are **23 distinct passing pytest cases** across these commands.
- Frontend/backend contract: exit 0, `routes=226 references=152 direct_calls=151`.
- Diff whitespace check: exit 0; informational Git LF/CRLF notice only.

Self-review: the shared helper is used by both resolver-to-lock mutation paths. A changed identity reaches HTTP 409 before whitelist removal, and the deterministic tests verify the account remains present and its credentials remain unchanged. Both PostgreSQL link queries explicitly request row locks and retain them through the surrounding transaction. The fixture reproduces the database state visible after an interleaving by injecting the rebind just after resolution; SQLite does not provide PostgreSQL row-lock behavior, so this is deterministic interleaving coverage plus source inspection, not a claim of a live concurrent PostgreSQL test. Native/runtime acceptance remains unperformed and `nativeVerified=false`.
