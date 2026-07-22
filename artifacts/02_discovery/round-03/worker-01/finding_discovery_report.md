# Finding discovery report — round 03 worker 01

## Scope and coverage

The unchanged `rank_input.jsonl` and `deep_review_input.jsonl` worklists each contain 2,233 rows. Every deep-review row is closed in `work_ledger.jsonl`; the same repository-wide pass covered the ranked paths and the web, plugin, deployment, and configuration surfaces they reference.

## Candidate

One plausible high-impact candidate was retained: `CAND-W01-HTTP-AUTH`, a transport-authentication downgrade in `admin-web/backend/main.py`. When `ALLOW_INSECURE_HTTP_AUTH` is omitted, `resolve_http_auth_setting` returns true for any non-HTTPS `ADMIN_PUBLIC_BASE_URL` (lines 281–299). `auth_transport_is_allowed` therefore permits login over public HTTP (1462–1473), and access/refresh cookies use a request-derived Secure flag that is false over HTTP (1506–1532). A network observer can capture credentials or bearer cookies and take over accounts, including privileged administrator sessions. Deployment validation is recommended because TLS termination and environment overrides determine reachability.

## Dispositions

Other reviewed HTTP, plugin, payment, RCON, database, filesystem, and game-state paths had apparent authorization, ownership, provider-verification, allowlist, or containment controls and did not form a second distinct high-impact source/sink/control tuple in this round. The local ignored `admin-web/.env` was not present in either supplied worklist; any secret exposure there is therefore deferred outside this worker's ranked/deep-review scope and no secret values are copied into artifacts.

## Closure

`raw_candidates.jsonl` and the per-candidate ledger contain the retained candidate. `worker_receipt.json` records counts and artifact paths. All 2,233 deep-review rows have an explicit disposition.
