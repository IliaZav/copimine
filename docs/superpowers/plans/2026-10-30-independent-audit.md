# Website reproducibility, privacy and mutation audit

> Execute inline with systematic debugging and test-driven development. Do not change the running site or another agent's worktree.

**Goal:** Repair demonstrable defects in the current website release and document the real Minecraft integration boundary.
**Baseline:** 85e5a81b902520dcf89d8b21f8a4fc99ffbb2c7f, branch audit/chatgpt-site-20261030.
**Architecture:** Keep FastAPI/PostgreSQL, the existing frontend and signed game adapter. Reference data is packaged explicitly; public responses never carry privileged bridge data. Validation precedes irreversible effects and request reservations.
**Spec:** docs/superpowers/specs/2026-10-23-site-production-design.md and latest explicit user requirements.

## Constraints
- Preserve original visual design, user identities, inventories, money and production worlds.
- Never commit .env, runtime databases, private keys, credentials or arbitrary files from ignored directories.
- No production deployment in this audit. Isolated SQLite/PostgreSQL tests only.
- Report code tests separately from live browser/game acceptance.

## Review focus
Clean checkout startup; guest access to bridge details; failed local validation leaving PENDING receipts; account deletion transaction rollback; uploads overwriting good files before validation.

## Tasks
1. Package vetted reference data and protect administrative action definitions. Add clean-checkout and public-boundary regressions; run RED before copying only selected data or changing static routes.
2. Filter public game health without mutating cached privileged state. Test guests, ordinary players and authorized administrators against a realistic bridge response.
3. Validate transfer/checkout and admin mutation inputs before reserving operations. Preserve idempotent replay and ambiguous post-dispatch failures; test no reservation/no side effect on local rejection.
4. Fix confirmed account and upload lifecycle defects with behavioral regression tests. Run current full test suite and JavaScript syntax checks.
5. Independent CodeRabbit review; investigate findings; commit and publish isolated branch. Document residual integration and native verification requirements.

## Evidence
Baseline command: site .venv python -m pytest -q -ra --tb=short tests with unique temporary directories.
Observed RED: 4 failed, 21 passed, 87 errors; missing frontend/data prevents create_app on a clean checkout.
Evidence directory: C:/Temp/cm-audit-evidence-20261030 (not tracked).

## Progress
- Specifications read, current branch and remote verified; original workspaces preserved.
- Implemented and tested clean-checkout data packaging, public health projection, transaction preflight, atomic media publication, production settings validation, proxy limits and catalog revisions.
- Original audit run: 152 passed, one pre-existing deprecation warning; JavaScript syntax checks passed.
- Independent CodeRabbit review: two minor duplicate alias issues, corrected with regression coverage; zero major/critical issues reported for this diff.
- Added website CI. Commits and remote CI are the remaining publication steps.
- External bridge provenance, full PostgreSQL integration and native browser acceptance remain explicitly unaccepted; see docs/AUDIT_2026-10-30.md.

## Follow-up after the original audit

- Reproduced and fixed player account deletion failing on NOT NULL identity fields and failing to return its cookie-clearing response. The regression asserts the database row, HTTP response and anonymous session after deletion.
- Reproduced and fixed timed blocks remaining disabled after their deadline. Active login is denied; the same account can log in after expiry.
- Corrected duplicate localized labels for music discs, armor trim templates and banner patterns, with a catalog invariant test.
- Follow-up full website suite: 155 passed, no skips, one existing Starlette/httpx deprecation warning. The 152 count above is the historical result from the original audit commit.
- Follow-up CodeRabbit review of the integrated audit commit and these corrections: 0 issues reported across the reviewed website change.
