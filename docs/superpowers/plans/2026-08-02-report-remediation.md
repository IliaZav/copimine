# CopiMine Audit Report Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every confirmed, actionable item from `pasted-text-1.txt`, prove the critical state-machine and security invariants with executable checks, publish a reproducible release, and deploy only the verified artifact.

**Architecture:** Keep each domain authoritative over its own state. Economy owns AR identity, balances, PIN verification, and durable delivery intents; Artifacts owns physical donation-item lifecycle; Narcotics owns narcotic instance identity; ElectionCore owns elections; the web layer validates and queues domain operations rather than silently mutating foreign state. All irreversible paths follow `validate -> durable intent -> reserve/lock -> physical change -> durable complete -> success`.

**Tech Stack:** Java 21/Paper/Purpur plugins, PostgreSQL, Python 3.13/FastAPI, browser JavaScript, PowerShell validators, GitHub Actions, release archive and Ubuntu systemd deployment.

## Global Constraints

- Preserve player, inventory, bank, tax, election, and world history; migrations are additive and recovery is fail-closed.
- A verifier never repairs or legalizes an untrusted item. Unknown or conflicting state goes to quarantine/manual review.
- A physical mutation never precedes its durable intent, and a success message never precedes durable completion.
- Persistent PINs are one-way hashes only; no `pin_sealed`, decryptable PIN, or API `visiblePin` survives the migration.
- One official narcotic physical item has exactly one instance ID and `amount=1`; AR serials have versioned signatures and a database registry.
- Domain permissions use least privilege; every confirmation callback rechecks permission, target, version, and current status.
- Production payment providers, credentials, worlds, inventories, and live database rows are not changed by tests or local packaging.
- Do not deploy until focused checks, plugin compilation, web checks, release validation, and remote preflight all pass.

## Evidence Ledger

Create `docs/audits/2026-08-02-report-evidence.md` with one row for each report ID (`P0-5` through `ITEM-10`) containing: requirement, changed seam, focused command, broader command, and `Confirmed|Partial|Failed|Unverified`. Duplicate report IDs may point to the same root fix, but no ID may be omitted.

---

### Task 1: Establish the reproducible baseline and failure matrix

**Files:**
- Create: `docs/audits/2026-08-02-report-evidence.md`
- Modify: `.github/workflows/ci.yml` only when a failing baseline proves the workflow is missing a required gate
- Test: existing `tests/RunCopiMineValidators.ps1`, Python contract/security scripts, and all plugin build scripts

- [ ] Run the complete validator set, Python suites, JavaScript syntax checks, resource-pack builder, and all first-party plugin builds on the clean `a1c5f86` baseline.
- [ ] For every failed check, reproduce once and classify it as implementation defect, test defect, environment limitation, or pre-existing failure before changing code.
- [ ] Record every report ID and the exact source locations found by search; do not treat a source-string assertion as behavioral proof.
- [ ] Add only the smallest focused regression test needed to demonstrate each confirmed root defect, run it red, then keep the red output in the evidence notes.

### Task 2: Harden AR authenticity, denomination, and economy crash recovery

**Files:**
- Modify: `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- Modify: `db/migrations/*.sql` and the economy schema migration code
- Modify: `tests/ValidateCopiMineArEconomyReleaseGuards.ps1`, `tests/ValidateCopiMineEconomyAtmOfflineSettlementRecovery.ps1`, and focused economy tests

- [ ] Replace generic legacy UUID acceptance and join-time self-healing with read-only verification; invalid serial/signature/version/material/owner/status is rejected and quarantined.
- [ ] Add an authoritative AR issuance registry with unique serial, denomination, owner/source, signature version, lifecycle status, and idempotency key; add unique constraints and a reconciliation query for `issued = physical_active + bank_deposited + consumed + quarantined`.
- [ ] Make deposit create and commit a durable `DEPOSITED` intent in the same transaction before removing the physical item; make restart recovery idempotent and manual-review unknown states.
- [ ] Make withdrawal create a `DEBITED` delivery row in the balance transaction before physical issue; deliver by idempotent token, record a durable receipt/provisional item, and never report success before `DELIVERED`.
- [ ] Ensure treasury scope cannot silently fall back to personal scope after access revocation; recheck active term/role in the mutation transaction and move bank target lookup off the GUI thread.
- [ ] Bind ATM rows to world UUID, expected material/model/fingerprint, and revalidate the physical anchor/display before every mutation; use non-placeable AR base material and bounded mailbox/TTL handling for dropped AR.
- [ ] Serialize account operations, make PIN lockout and debit one transaction under row lock, distinguish unavailable balance/tax from zero/inactive, and move WAL fsync to a bounded writer with an acknowledged completion.

### Task 3: Remove reversible PIN storage and disclosure

**Files:**
- Modify: `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- Modify: `admin-web/backend/main.py`, `admin-web/frontend/assets/js/player/treasury-pages.js`, `admin-web/frontend/assets/js/cabinet-runtime.js`, and related account/admin views
- Create/modify: additive migration removing `pin_sealed` data and tests for one-time PIN delivery

- [ ] Delete persistent `pin_sealed` columns/data from the authoritative schema while retaining a compatibility migration that clears old values without attempting decryption.
- [ ] Remove `seal_persistent_pin`, `reveal_persistent_pin`, `visible_personal_pin`, API `visiblePin`, and all routes that return a persistent or permanent PIN; verify only the one-way hash is used for comparison.
- [ ] Make temporary reset output one-time, expiring, audit-safe, and never returned to an administrator after the initial controlled delivery; permanent randomization becomes reset-and-change rather than admin-readable.
- [ ] Add negative tests that fail on `visiblePin`, `pin_sealed`, `unseal`, or decryptable PIN output and positive tests for verify/reset behavior.

### Task 4: Make donation purchase, repair, pending delivery, reclaim, and tax flows truthful

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- Modify: `db/migrations/*.sql`
- Modify: artifact/reclaim/tax validators and add deterministic failure-path tests

- [ ] Keep purchase success pending until `DELIVERED` is committed; keep the item provisional/locked on finalization failure and show review text instead of false success.
- [ ] Keep repaired items provisional/repair-locked until `completeRepair` commits; never repeat the physical repair or charge on recovery.
- [ ] Make `queuePendingArSettlement` return a completion result and make physical pending AR provisional until durable journal/DB commit; recover all crash points with exactly one result.
- [ ] Make Tax Clock exemption and donation claim completion one transaction with row lock, idempotency, claim/purchase ID as unique exemption ID, and explicit `CLAIMED`/`MANUAL_REVIEW` states.
- [ ] Route every terminal loss (break-before-destroy, void, cactus, explosion, creative deletion, plugin removal, despawn) through one idempotent loss transition; never reclaim broken/consumed/transferred/replaced items and never issue a free reclaim after a failed durable transition.
- [ ] Serialize claim-all per player or use one batch transaction; make addItem exact-instance verification survive same-tick listener movement/removal; keep mailbox recovery for full inventories.
- [ ] Batch trap/lava journal writes, preserve BlockData on parse errors via manual review, include world UUID in trap rows, replace real lightning with controlled visual/effect behavior, and make all interact variants explicit.
- [ ] Fix compass max-distance calculation, truthful distance copy, region policy and two-phase target validation; charge cooldown only after an uncancelled teleport.
- [ ] Block provisional items in every effect/equipment/damage/resurrection hook, clean player-facing English, mojibake, internal statuses, and mock SBP wording.

### Task 5: Enforce narcotic instance identity and crash-safe brewing

**Files:**
- Modify: `copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java` and migrations
- Modify: narcotic validators and add behavior tests for invalid IDs, stacks, ACK loss, reset races, and inventory-full paths

- [ ] Make `resolveOfficial` pure and read-only; reject missing/invalid ID, signature, registry status, owner/source, or migration allowlist and quarantine rather than mutating PDC.
- [ ] Add a versioned HMAC/signature and authoritative issuance registry with unique instance ID/status; create every official item at `amount=1`, never legalize arbitrary PDC flags, and remove the loose resolver from mutation paths.
- [ ] Make consumption reserve one exact instance under DB lock, return review status on uncertain ACK, keep persistent pending state on logout, and prevent duplicate physical copies from satisfying one reservation.
- [ ] Write ingredient intent before consuming the item; on completion failure return the exact ingredient through an owner mailbox; fix brew ownership to the first owner or explicit shared-party contract.
- [ ] Prevent cauldron ingredients/admin output from dropping into the world, bound chunk reconciliation by chunk index, remove completed locks, enforce PvP/region policy for wrong-mix effects, and make side effects idempotent or explicitly cosmetic.
- [ ] Validate journals for permissions, size, corruption, and generation/reset barriers; use bounded DB pool timeouts/leak detection and release-safe recovery.

### Task 6: Close ElectionCore concurrency, authorization, and ownership leaks

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
- Modify: `admin-web/backend/main.py` and election frontend routes
- Modify: election validators and add 20-way concurrent vote/law/cooldown tests

- [ ] Lock the active term/election row and recheck law-count, replacement cooldown, stage, deadline, role, candidate roster, and target version inside the mutation transaction.
- [ ] Use one versioned `PresidentAuthorityService`/advisory-lock contract for web and server operations; never authorize a mutation from a stale cache or let two state machines write the same transition.
- [ ] Make anonymous ballot tokens cryptographically random and unlinked, enforce DB uniqueness for participation/vote/ballot, and migrate or remove old personalized vote secrecy leaks.
- [ ] Use bounded executors for JDBC, per-player/action/target debounce, correct cancelled-event handling, scoped official-item inventory protection, stale-GUI stage checks, and explicit WorldGuard/region policy.
- [ ] Remove compiled legacy election handlers and AdminPlus PDC/lifecycle ownership; use versioned service interfaces and recheck permissions on every admin callback.
- [ ] Normalize player-facing errors into localized text plus correlation ID, and make pending tax reconciliation resumable with retry/SLA/admin visibility.

### Task 7: Make AuthEffects fail closed and time-correct

**Files:**
- Modify: `minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java`
- Modify: AuthEffects `plugin.yml`, build script, and AuthMe integration tests

- [ ] Subscribe to AuthMe login/register/logout/session-expire events when present; treat the local UUID set as a cache with TTL no longer than one second, never as authority.
- [ ] Query AuthMe on every authorization decision, fail closed on API error, remove cached state immediately on logout/force logout/quit, and review the command allowlist including password-change commands.
- [ ] Store the original Slowness timestamp/duration and restore only the remaining duration after authentication.
- [ ] Add an integration-shaped event/API test matrix for login, logout, forced logout, API outage, movement, inventory, pickup, command, and restart.

### Task 8: Harden Admin Web trust boundaries and browser behavior

**Files:**
- Modify: `admin-web/backend/main.py`, `admin-web/backend/startup_checks.py`, `admin-web/backend/discord_bot.py`, and `admin-web/backend/plugin_registry.py`
- Modify: `admin-web/frontend/assets/js/**/*.js` and relevant HTML/CSS
- Modify: `admin-web/requirements.txt`, migrations, E2E/contract scripts, and `.env.example`

- [ ] Require a stable production `SECRET_KEY`, PostgreSQL auth storage, real payment provider when donations are enabled, and fail closed on insecure public HTTP admin authentication; keep HTTP only for non-authenticated public/download surfaces.
- [ ] Move login lockout to PostgreSQL/Redis with worker-safe TTL, invalidate sessions/refresh tokens on role revocation, require CSRF/origin checks, secure cookies under HTTPS, and add correlation IDs to 500 responses/logs.
- [ ] Move schema DDL to startup migration code; keep domain writes behind one authoritative API/outbox instead of direct plugin-table dual writes; serialize election/shop price operations with version/idempotency keys.
- [ ] Validate RCON tokens as complete commands with control/newline/semicolon rejection; bind dangerous confirmation tokens to actor/action/target/version with short TTL.
- [ ] Replace `innerHTML`/dynamic global handler dispatch with textContent or strict sanitization and a static handler map; do not turn backend outages into empty production-looking lists or fake metrics.
- [ ] Add Playwright/browser coverage for login, refresh, CSRF, role revoke, stale tab, double submit, timeout/retry, spinner cleanup, HTTP policy, price drift, election drift, and error correlation.
- [ ] Split `main.py` into domain modules only where it reduces review blast radius without changing endpoint contracts; keep explicit response schemas and one authoritative audit/plugin-event outbox.

### Task 9: Finish WorldCore, catalog, visual, and release hygiene

**Files:**
- Modify: `copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java`
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
- Modify: `copimine-artifacts/items.yml`, `copimine-artifacts/config.yml`, `resourcepacks/**`, catalog validators, and release scripts
- Modify: `.github/workflows/ci.yml`, `deploy/release_manifest.json`, and release documentation

- [ ] Make safecheck await all redirects and report moved/failed/skipped; use operation-bound teleport tokens containing player, operation, worlds, destination range, and expiry; separate command and trusted-plugin policy, prevent redirect cycles, and rate-limit safe-point checks per tick.
- [ ] Save border state with atomic replace plus flush/fsync and make GUI success depend on persisted state, not command dispatch; localize all OperationResult status/reason strings and remove mojibake.
- [ ] Add one catalog schema validator for item IDs, material/CMD uniqueness, effect/model existence, price, cooldown, supply/per-player limits, reclaim policy, and lore/mechanics consistency; remove hardcoded duplicate probabilities and document real lightning/compass behavior.
- [ ] Add main/offhand/action and inventory transport matrices for every critical item; ensure resource-pack/client/server registries share ID/version metadata and run deterministic model/golden checks.
- [ ] Remove release-only internal labels, raw SQL/stack traces, mock/debug/AI/TODO/FIXME/mojibake strings from source and binaries; retain structured internal audit codes only in internal logs.
- [ ] Make CI required for `main`, build JARs from source only, compare two independent builds, generate SBOM, dependency vulnerability reports, signed manifest, binary-string scan, schema upgrade/rollback tests, Paper/Purpur integration, browser E2E, canary/rollback rehearsal gates, and a release checklist.
- [ ] Add health output containing package commit, source commit, per-plugin SHA-256/readiness, schema version/readiness, and deployment evidence.

### Task 10: Verify, publish, deploy, and report

**Files:**
- Modify: evidence ledger and release report only after fresh checks
- Runtime: server `92.126.27.114:2222` using the user-provided upload/install path

- [ ] Run focused regression tests after each task, then all Python tests, all PowerShell validators, Node syntax checks, plugin builds, resource-pack reproducibility, security scans, and release validation from a clean checkout.
- [ ] Build one release archive, record hashes and source/package/deployed SHAs, and perform remote preflight without mutating data; preserve server backup and use only `/home/qwerty/copimine-upload/install_release.sh <archive>` through the authorized sudo rule.
- [ ] Verify systemd services, health endpoint, installed plugin hashes, database/schema readiness, logs, and rollback artifact on the server; do not use `--wipe-worlds` or modify worlds/accounts.
- [ ] Inspect `git status`, stage only intended source/tests/docs/release files, commit with a concise message, push the current non-default branch, and create a draft PR unless the user explicitly requests direct main publication.
- [ ] Complete the evidence ledger for all report IDs and provide the final Russian report with `fixed / partial / not applicable / unverified`, exact files/lines, tests, deployment SHA, and residual limitations.

## Self-review before execution

- P0/P1, REL, ECO, ART, ELEC, NARC, WEB, WORLD, AUTH, ADMIN, ITEM, and K are all represented above; duplicate findings point to the owning task.
- No task relies on a source-string check alone; each critical behavior has a required executable or runtime evidence layer.
- No task authorizes deletion of live data, credentials, worlds, or accounts; migrations and deployment preserve recovery paths.
- The baseline resource-pack validator failure remains an explicit Task 1 investigation item, not silently reclassified.
