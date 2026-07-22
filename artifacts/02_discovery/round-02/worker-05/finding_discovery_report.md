# Finding discovery report — round 02 / worker 05

## Coverage and closure

- Authoritative inputs: `rank_input.jsonl` and `deep_review_input.jsonl` (2,233 rows each; 2,233 unique repository paths; no missing files).
- Read receipt: 31,273,709 bytes and 815,217 lines were read across the assigned paths.
- Every assigned row is represented in `work_ledger.jsonl`. Rows without a distinct high-impact source/control/sink tuple are closed as `reviewed_no_candidate`; candidate rows are linked to the candidate ledgers below. Validation and attack-path phases are explicitly deferred to the coordinator.
- This report uses only independent round-02 review and does not import prior worker findings.
- `CAND-W05-009` is an adjacent release-validation script discovered during repository context review; it is recorded separately as out-of-worklist and does not alter the exhaustive 2,233-row receipt.

## Candidates

### CAND-W05-001 — HTTP authentication enabled by default when `ADMIN_PUBLIC_BASE_URL` is HTTP

`resolve_http_auth_setting` returns `True` whenever the setting is omitted and the public URL does not begin with `https://` (`admin-web/backend/main.py:281-299`). `auth_transport_is_allowed` then treats that value as sufficient for every login/refresh/recovery route (`admin-web/backend/main.py:1462-1473`). The built-in default URL is `http://admin.copimine.ru:18080`, and the deployment helper intentionally writes `ALLOW_INSECURE_HTTP_AUTH=1` for no-TLS installations (`deploy/shared/common.sh:252-262`). An Internet attacker able to observe or alter that HTTP connection can steal passwords, bearer cookies, and refresh cookies. Validation should confirm supported deployments that omit the flag and whether TLS termination is absent.

Affected entry points include admin/session/player login (`admin-web/backend/main.py:9271-9308`, `9652-9675`), player recovery confirmation (`9684-9690`), and both refresh endpoints (`9371-9377`, `9704-9710`).

### CAND-W05-002 — Unauthenticated treasury history exposes player actors and transaction details

`public_treasury_overview_sync` selects `actor` and `details` from `cmv4_bank_ledger` and returns actor names, amounts, timestamps, owner UUID/name, and history (`admin-web/backend/main.py:5338-5379`). `public_president_budget_history_sync` likewise returns sanitized but still identifying actor names, item/comment details, amounts, and timestamps (`5572-5610`). These projections are reachable without authentication through `/api/public/status`, `/api/public/president-budget/history`, and `/api/treasury/public` (`7831-7872`, `9480-9492`, `10553-10555`). The public Internet can therefore enumerate player-associated financial activity and transaction metadata. Validation should establish the intended public privacy policy and sensitivity of existing ledger actors/details.

### CAND-W05-003 — Non-atomic refresh-token rotation permits concurrent replay

`rotate_auth_pair_from_refresh_sync` verifies the old token, issues a new access/refresh pair, and only then revokes the old JTI (`admin-web/backend/main.py:3919-3940`, `3952-3968`). There is no transaction-level compare-and-set or row lock around verification and revocation. Two concurrent requests carrying the same stolen refresh cookie can both pass verification and receive independent valid refresh tokens before either revocation is committed. The race affects `/api/auth/refresh` and `/api/player/refresh` (`9371-9383`, `9704-9717`). Validation should exercise concurrent requests against the deployed persistence backend.

### CAND-W05-004 — Release auth patch extracts a root-selected archive without member containment checks

`release/copimine_auth_patch.sh` accepts an operator-supplied archive path and extracts it directly into a temporary directory (`:7-22`, `:53-60`). There is no per-member rejection for absolute names, `..`, symlinks, or hardlinks before `tar` writes. The script is documented for `sudo` use and subsequently copies payload files into the Minecraft plugin tree (`:6`, `:60-77`). A crafted archive supplied to the privileged patch workflow can overwrite files outside `WORK_ROOT`, potentially altering root-owned configuration or startup code. Validation should confirm the exact `tar` implementation and attacker/operator archive provenance.

### CAND-W05-005 — Ubuntu auth patch extracts a root archive without member containment checks

`deploy/ubuntu/copimine_auth_patch.sh` requires EUID 0 and verifies only a caller-provided SHA256 sidecar before running `tar -xzf "$ARCHIVE_PATH" -C "$WORK_ROOT"` (`:15-35`, `:72-78`). The hash check authenticates the sidecar relationship, not the safety of archive member names. Absolute/traversal/link members are not inspected before extraction, so a malicious or compromised release archive can write outside the temporary root while the script is privileged. Validation should confirm trusted-archive assumptions and `tar` link behavior.

### CAND-W05-006 — Release unpack-and-verify script trusts archive member paths

`release/final-20260717/copimine_unpack_and_verify.sh` tests only archive readability (`:166-176`) and then extracts tar or zip input directly to `EXTRACT_ROOT` (`:204-213`). No member-by-member containment or link validation runs before extraction. A crafted archive accepted by the release unpack workflow can escape the staging directory and overwrite files available to the invoking user; on privileged deployment this can become root-impacting. Validation should confirm whether this script is reachable with untrusted release input.

### CAND-W05-007 — Full replacement installer extracts a checked-but-unconfined archive

`release/final-20260717/copimine_full_replace.sh:193-233` checks readability and an optional SHA256 sidecar, but does not validate archive member names or link types. `extract_archive` then calls `tar -xzf` or `unzip -q` into `EXTRACT_ROOT` (`:245-254`). The installer is a full replacement workflow that runs service/filesystem operations after extraction, so a crafted archive can escape staging and overwrite files in the installer's execution context. Validation should confirm privilege and whether the sidecar is attacker-influenceable.

### CAND-W05-008 — Ubuntu rollback extracts an unconfined backup archive

`deploy/ubuntu/rollback.sh` checks only that `tar -tzf` can list the supplied backup and then extracts it into `stage_root` (`:43-45`). It does not reject absolute/traversal members or symlink/hardlink entries. A malicious or tampered rollback archive supplied to the root rollback command can write outside staging before the expected project-tree check, affecting deployment secrets, service files, or application code. Validation should confirm archive provenance and root execution in operational runbooks.

### CAND-W05-009 — PowerShell release validator extracts an unconfined archive

`scripts/validate_release_bundle.ps1` accepts an explicit `ArchivePath`, verifies the archive hash/manifest, and then invokes `tar -xzf $ArchivePath -C $tmpRoot` (`:1-13`, `:70-84`). It performs no member path or link validation before extraction. A crafted archive with traversal/link entries can escape the temporary directory in the validator's Windows execution context. Impact is lower when run as an ordinary build user, but it can become supply-chain or workstation compromise if run with elevated rights. Validation should confirm Windows tar behavior and whether the hash/manifest are operator-controlled.

### CAND-W05-010 — `/api/runtime` trusts loopback source address behind a reverse proxy

The runtime endpoint skips `require_panel_admin` whenever `is_loopback_request(request)` is true (`admin-web/backend/main.py:14970-14983`). The check uses the immediate client address; a reverse proxy commonly makes that address `127.0.0.1`, so an external caller forwarded by the proxy can receive startup diagnostics and managed runtime paths without panel authentication. The response includes project/app roots, environment/artifact paths, release metadata, and startup state. Validation should reproduce the production proxy topology and confirm whether loopback is reachable only from a trusted local health-check path.

## Deferred phase closures

This worker is discovery-only. Candidate-local validation and attack-path entries are recorded as explicit deferred rows in each `candidate_ledger.jsonl`; no exploit execution or remediation claim is made here.

Repository: target_sha256_850966f902d9016af18b00a3cb5dc36970fd95ed0f51dedec2e3208bf4c4c113
Version: codex-security-snapshot/v1:sha256:ee4f16137fa3ca57c0d7c29a1cfd0dfc4a224fa5048cbfa01581805d654ff39d2
