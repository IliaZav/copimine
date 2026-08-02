# CopiMine audit remediation evidence — 2026-08-02

This ledger maps every finding ID from the supplied audit report to the
remediation seam and the evidence available for the release.

Evidence codes:

- **V** — `tests/RunCopiMineValidators.ps1`: 653/653 passed.
- **P** — Python compile, backend security regression and runtime-hardening self-test passed.
- **E** — election/station focused validators passed.
- **R** — full release package validation passed; the latest signed local
  archive SHA-256 is `059ee2b3f9492becceb6414fb59714ec50c28ffc7235664b15e8175e9fb68f75`.
- **S** — production health/runtime/service checks passed on the target server.
- **D** — production PostgreSQL exact row-count audit after the clean-state reset.
- **M** — production Minecraft log and AuthMe configuration check after restart.
- **B** — production backup timer and retention/cleanup check.

`Confirmed` means the implementation seam and the listed executable or live
check passed. `Partial` means the structural guard exists but a separate
third-party, hosted-CI, browser-E2E, mutation, or concurrency rehearsal is
still needed for absolute proof; it is not presented as a failed production
check.

## P0/P1 — money, items, authentication and truthful completion

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| P0-1 | Confirmed | All terminal artifact loss paths use one idempotent reclaim/loss transition; a broken item cannot receive a free replacement. | V, R |
| P0-2 | Confirmed | AR verification is read-only and rejects unknown/invalid serials, material, denomination, owner, status and signature; no UUID-only mint path remains. | V, R |
| P0-3 | Confirmed | Versioned AR HMAC covers serial, material, denomination, certified state and version. | V, R |
| P0-4 | Confirmed | Deposit writes `cmv8_ar_deposit_intents` durably before physical removal and has idempotent recovery. | V, D |
| P0-5 | Confirmed | Withdrawal debits and creates a durable delivery row in the same transaction before physical delivery. | V, D |
| P0-6 | Confirmed | Persistent PINs are one-way hashes; migration removes `pin_sealed` without decrypting it. | V, P, D |
| P0-7 | Confirmed | A counterfeit narcotic is quarantined/read-only rejected; it cannot become official through verification. | V, R |
| P0-8 | Confirmed | Official narcotics are amount `1` with one unique signed instance ID; stack scans split/reject duplicates. | V, R |
| P0-9 | Confirmed | Cross-plugin admin checks recognize only the approved admin roles and deny junior/foreign roles. | V |
| P0-10 | Confirmed | AuthEffects subscribes to AuthMe login/register/logout events and clears local state on logout/quit; API-first fallback is fail-closed. | V, M |
| P1-11 | Confirmed | Shop success is emitted only after DB finalization/delivery commits. | V |
| P1-12 | Confirmed | Repair success is emitted only after `completeRepair` commits. | V |
| P1-13 | Confirmed | Pending AR remains provisional until `DELIVERED`; uncertain ACK becomes recovery/manual review. | V |
| P1-14 | Confirmed | Tax exemption and donation claim use one idempotent transaction/claim identity. | V |
| P1-15 | Partial | CI now runs Python behavior/regression checks, 653 validators, compilation and provenance checks; full third-party/Paper/browser behavior still needs hosted integration runners. | P, V, R |

## REL — reproducible release and deployment controls

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| REL-1 | Confirmed | Packaging captures the exact source commit in release/installer manifests and validates a clean source tree; the last installed runtime is source `c7b903a860e0841eb415dccab23ba9f1866705b1`, while the final pending archive is source `f63e7ed4948be918e194dcd44429848b2905bc6f`. | R, S |
| REL-2 | Partial | `.github/workflows/ci.yml` is wired to push/PR branches and gates Python, validators, builds and clean provenance; hosted Actions run was not queried from this environment. | V |
| REL-3 | Partial | First-party JARs are rebuilt from source during packaging and their SHA-256 values are recorded; legacy committed binary outputs remain in the repository for deployment compatibility. | R |
| REL-4 | Partial | Two clean first-party rebuilds are byte-for-byte reproducible and the result is hash-validated; an independent second host/builder was not available. | R |
| REL-5 | Confirmed | A formal SPDX 2.3 SBOM is generated, hash-validated and included in the release bundle. | R |
| REL-6 | Confirmed | The release manifest has a detached OpenSSH Ed25519 signature; the allowlisted public key is verified by both bundle validation and installer preflight. | R, S |
| REL-7 | Confirmed | Pinned Python dependencies pass the strict `pip-audit` gate on the production Python 3.13 target. | P, R |
| REL-8 | Confirmed | Ordered PostgreSQL migrations and schema readiness are applied during install; production migration plan completed successfully. | R, S, D |
| REL-9 | Partial | Replacement keeps a previous release backup and has rollback scripts; a destructive rollback rehearsal was not performed on the live server. | R, B |
| REL-10 | Partial | Installer gates service activation, health and artifact hashes; there is no separate traffic canary environment. | R, S |
| REL-11 | Confirmed | The release SBOM scanner checks all 42 packaged binary artifacts for private-key, cloud/payment-token and leaked-PIN markers. | V, R |
| REL-12 | Partial | Focused behavioral/self-test checks were added beside legacy contract validators; mutation testing of every string assertion was not run. | P, V |
| REL-13 | Unverified | No mutation-testing engine was available in the deployment environment. | — |
| REL-14 | Partial | Third-party plugin presence, hashes, startup and compatibility checks are part of release verification; their internal behavior is outside source ownership. | R, M |
| REL-15 | Confirmed | Packaging, archive validation, installer preflight, service/health checks and runtime artifact checks form an automatically blocking release path. | R, S |

## ECO — economy and AR integrity

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| ECO-1 | Confirmed | Legacy acceptance is removed from normal verification and cannot run indefinitely. | V |
| ECO-2 | Confirmed | Verification is read-only and never self-heals an untrusted item. | V |
| ECO-3 | Confirmed | `cmv8_ar_assets` is the authoritative serial registry with lifecycle/status fields. | V, D |
| ECO-4 | Partial | Registry/ledger/reconciliation seams and conservation guards are present; a live high-volume conservation simulation was not run. | V, D |
| ECO-5 | Confirmed | Serial uniqueness and physical-instance state are enforced by registry/locks; duplicate physical paths are quarantined. | V, D |
| ECO-6 | Confirmed | AR signatures are versioned and the verifier rejects unsupported versions. | V |
| ECO-7 | Confirmed | Journal/settlement failures return an explicit non-success result and enter recovery/manual review. | V |
| ECO-8 | Confirmed | WAL force is moved out of the game callback into bounded writer/recovery flow. | V |
| ECO-9 | Confirmed | Durable intent precedes physical pending delivery. | V |
| ECO-10 | Confirmed | Player-facing success waits for `DELIVERED`. | V |
| ECO-11 | Confirmed | Treasury scope rechecks role/term in the mutation transaction and cannot silently fall back to personal scope. | V |
| ECO-12 | Confirmed | Treasury/bank target lookup is asynchronous and not executed in the GUI thread. | V |
| ECO-13 | Confirmed | Authoritative current-role/term checks replace stale permission-cache authorization. | V |
| ECO-14 | Confirmed | ATM rows bind world/coordinate/material/model/anchor identity. | V |
| ECO-15 | Confirmed | ATM display/anchor is revalidated before every mutation. | V |
| ECO-16 | Confirmed | Official AR uses a non-placeable base material and placement is blocked. | V |
| ECO-17 | Confirmed | Official AR drops/destruction are blocked or routed to bounded mailbox/loss handling. | V |
| ECO-18 | Confirmed | Duplicate serial detection and quarantine/reconciliation paths are present. | V |
| ECO-19 | Partial | Double-click/idempotency and nonstandard inventory paths have contract coverage; full live inventory event simulation remains unverified. | V |
| ECO-20 | Confirmed | PIN lockout, failed-attempt increment and debit/verification are row-locked in one transaction. | V, P |
| ECO-21 | Confirmed | Tax service distinguishes unavailable/error from zero or inactive tax. | V |
| ECO-22 | Partial | Pool operations have bounded/error-safe paths and release checks; long-running leak/load rehearsal was not run. | V, P |
| ECO-23 | Confirmed | Balance API errors fail closed instead of returning zero. | V, P |
| ECO-24 | Confirmed | Same-account operations are serialized with row/advisory locks and idempotency. | V |

## ART — donation artifacts and shop behavior

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| ART-1 | Confirmed | Shop/admin authorization uses centralized cross-plugin role checks. | V |
| ART-2 | Confirmed | Delete confirmation rechecks actor, shop, version and permission. | V |
| ART-3 | Confirmed | Shop teleport validates a safe standing point before teleporting. | V |
| ART-4 | Confirmed | Reload uses validated replacement/config state rather than partially applying invalid data. | V |
| ART-5 | Confirmed | Purchase waits for durable `DELIVERED` completion. | V |
| ART-6 | Confirmed | Repair waits for durable `COMPLETED` state. | V |
| ART-7 | Confirmed | Reclaim is idempotent and terminal loss cannot generate a free replacement. | V |
| ART-8 | Confirmed | Break-before-destroy, damage and removal events route through the loss transition. | V |
| ART-9 | Confirmed | Claim-all is serialized/batched and uses mailbox recovery for full inventory. | V |
| ART-10 | Confirmed | Tax clock identity and claim completion share the same artifact/claim transaction boundary. | V |
| ART-11 | Confirmed | Trap journal work is queued/batched instead of one synchronous write per block. | V |
| ART-12 | Confirmed | Lava conversion uses the same queued/batched journal path. | V |
| ART-13 | Confirmed | BlockData parse failures never silently replace state with AIR; they enter review/recovery. | V |
| ART-14 | Confirmed | Trap persistence includes world identity. | V |
| ART-15 | Confirmed | Lightning behavior is controlled/cosmetic and no longer an uncontrolled gameplay strike. | V |
| ART-16 | Confirmed | Interact handling covers air/block and relevant main/offhand variants. | V |
| ART-17 | Confirmed | Compass distance variables and minimum/maximum semantics are corrected. | V |
| ART-18 | Confirmed | Compass message reports actual measured distance and configured bounds truthfully. | V |
| ART-19 | Confirmed | Compass method/name reflects current mechanic, not the removed death mechanic. | V |
| ART-20 | Confirmed | Compass target selection rechecks region policy. | V |
| ART-21 | Confirmed | Teleport cooldown is charged only after an uncancelled successful teleport. | V |
| ART-22 | Confirmed | Target and player state are revalidated between lookup and teleport. | V |
| ART-23 | Confirmed | Provisional items are blocked in damage/effect/equipment and other effect sources. | V |
| ART-24 | Confirmed | Claim UI shows player-safe pending/review/completed states. | V |
| ART-25 | Confirmed | Player-facing GUI strings are localized; internal status names are not shown. | V |
| ART-26 | Confirmed | Mojibake in purchase/player feedback is removed. | V |
| ART-27 | Confirmed | Mock SBP foundation/admin placeholder was removed or made non-operational. | V |
| ART-28 | Confirmed | `zmei_gorynych` probability/config/lore has one consistent source. | V |
| ART-29 | Confirmed | Pending restore state is durable and tied to the authoritative recovery path. | V |
| ART-30 | Confirmed | `addItem` is followed by exact-instance/durable verification; failure enters mailbox/review. | V |

## ELEC — elections, polling stations and RP lifecycle

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| ELEC-1 | Confirmed | Law count is checked under the locked active term/election transaction. | V |
| ELEC-2 | Confirmed | Law replacement cooldown is checked under the same lock/idempotency boundary. | V |
| ELEC-3 | Confirmed | President authorization rechecks authoritative state instead of trusting a cache. | V |
| ELEC-4 | Confirmed | ElectionCore/EconomyCore use one versioned president-authority contract. | V |
| ELEC-5 | Confirmed | Tax unavailable/error is distinct from no tax. | V |
| ELEC-6 | Confirmed | Async status/database work uses bounded executors rather than common ForkJoinPool. | V |
| ELEC-7 | Confirmed | Debounce is scoped by player/action/target and does not consume legal unrelated clicks. | V |
| ELEC-8 | Confirmed | Protected-block placement uses `ignoreCancelled=false` and reactivation logic, so WorldGuard cancellation is respected without making stations dead. | E |
| ELEC-9 | Confirmed | Official item protection is scoped to foreign/unsafe movement and permits own-inventory rearrangement. | V |
| ELEC-10 | Confirmed | Every stale GUI action rechecks current stage/deadline/target. | V |
| ELEC-11 | Confirmed | Web election mutations are serialized/advisory-locked and do not bypass ElectionCore ownership. | V |
| ELEC-12 | Confirmed | Legacy personalized vote state is excluded/cleaned by migrations and current-round reads. | V, D |
| ELEC-13 | Confirmed | Anonymous ballot tokens use unpredictable unlinked randomness. | V |
| ELEC-14 | Confirmed | Participation/ballot/vote uniqueness is enforced in DB and transaction logic. | V |
| ELEC-15 | Confirmed | Pending tax reconciliation is resumable with retries and manual-review visibility. | V |
| ELEC-16 | Confirmed | Legacy election handlers are not compiled into the release runtime. | V, R |
| ELEC-17 | Confirmed | AdminPlus no longer owns ElectionCore PDC/lifecycle state; bridge calls are versioned. | V |
| ELEC-18 | Confirmed | Player-facing election wording is localized and free of internal labels. | V |
| ELEC-19 | Confirmed | Error codes are paired with actionable localized messages/correlation IDs. | V, P |
| ELEC-20 | Partial | Atomic uniqueness and stale-action contracts are covered; a real 20-way live concurrent vote rehearsal was not run. | V, D |

## NARC — narcotics, brewing and recovery

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| NARC-1 | Confirmed | `resolveOfficial` is pure/read-only and rejects invalid IDs/signatures. | V |
| NARC-2 | Confirmed | One official item has one ID and amount `1`; stacks are split/rejected. | V, R |
| NARC-3 | Confirmed | Loose PDC migration/legalization is removed from mutation paths. | V |
| NARC-4 | Confirmed | Official narcotics carry a versioned HMAC/signature. | V |
| NARC-5 | Confirmed | `narcotics_issued_instances` is the authoritative issuance registry. | V, D |
| NARC-6 | Confirmed | Final products, admin output and all issued units are amount `1`. | V, R |
| NARC-7 | Confirmed | Consumption reserves one exact instance, never the quantity of an arbitrary stack. | V |
| NARC-8 | Confirmed | Lost/uncertain ACK returns recovery/manual-review state instead of contradictory success. | V |
| NARC-9 | Confirmed | Logout/reconnect leaves durable pending reservation state for reconciliation. | V |
| NARC-10 | Confirmed | Ingredient intent is written before physical consumption. | V |
| NARC-11 | Confirmed | Failed completion returns the exact ingredient via owner mailbox. | V |
| NARC-12 | Confirmed | Brew owner is fixed at creation/first owner rather than last contributor. | V |
| NARC-13 | Confirmed | Cauldron destruction never drops ingredients to the world; owner/manual review handles uncertainty. | V |
| NARC-14 | Confirmed | Full inventory restore uses mailbox, not an untracked world drop. | V |
| NARC-15 | Confirmed | Loaded-chunk reconciliation is indexed/bounded rather than scanning the entire cache. | V |
| NARC-16 | Confirmed | Completed/expired locks are removed and bounded. | V |
| NARC-17 | Confirmed | Wrong-mix damage checks PvP and region policy before applying effects. | V |
| NARC-18 | Confirmed | Side effects are owned/idempotent and recoverable after restart. | V |
| NARC-19 | Confirmed | Admin give queues mailbox delivery when inventory is full; it never drops an untracked item. | V |
| NARC-20 | Confirmed | Narcotics UI errors are localized. | V |
| NARC-21 | Confirmed | Compensation strings are localized and color codes are converted safely. | V |
| NARC-22 | Partial | Pool failure/leak guards exist in the release path; a long-running load/leak test was not run. | V |
| NARC-23 | Partial | Journal permission/size/corruption/recovery checks exist; production journal corruption injection was not performed. | V |
| NARC-24 | Confirmed | Reset generation/barrier blocks new writes and callbacks during reset. | V |

## WEB — HTTP, authentication, admin and personal cabinets

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| WEB-1 | Confirmed | PIN storage and verification use one-way hashes only. | P, V, D |
| WEB-2 | Confirmed | `visiblePin`/persistent PIN fields are absent from API responses. | V, P |
| WEB-3 | Confirmed | Admin randomization returns only one-time in-game delivery/recovery state, never the permanent PIN. | V |
| WEB-4 | Confirmed | The public gateway now serves port 80 directly: pages/health/downloads return `200`; public login/session auth returns `426`; direct unproxied loopback HTTP is the only authentication exception. The public 443 listener is disabled until TLS is deliberately introduced. | V, S |
| WEB-5 | Confirmed | Auth cookies are `Secure` on authenticated transport and refresh tokens are HttpOnly. | V, P |
| WEB-6 | Confirmed | Login limits are persisted in PostgreSQL `auth_login_limits`, worker-safe across processes. | V, D |
| WEB-7 | Confirmed | Production requires a stable configured secret and rejects generated fallback. | V, S |
| WEB-8 | Confirmed | Production rejects SQLite auth and uses PostgreSQL. | V, S |
| WEB-9 | Partial | PostgreSQL access is centralized and bounded; extended pool load/leak testing remains external. | P, V |
| WEB-10 | Confirmed | Request-path DDL is removed; migrations run at startup/install. | V, S |
| WEB-11 | Confirmed | Domain ownership/authoritative plugin APIs and guarded web mutation seams prevent silent foreign-table writes. | V |
| WEB-12 | Confirmed | Election web/server operations use advisory locks, current-stage checks and idempotency. | V |
| WEB-13 | Confirmed | Shop price changes validate live catalog/version and are audited. | V |
| WEB-14 | Confirmed | Retry/idempotency keys prevent a second money operation after network timeout. | V, P |
| WEB-15 | Confirmed | Stale tab actions revalidate target/version/status. | V |
| WEB-16 | Confirmed | Balance mutations lock/recheck current value instead of applying a stale set. | V |
| WEB-17 | Confirmed | `safeApi` preserves explicit failures and does not silently convert outages to empty production lists. | V |
| WEB-18 | Confirmed | Demo/fallback metrics are marked/unavailable and are not presented as live production data. | V |
| WEB-19 | Confirmed | Dynamic UI uses safe DOM/text construction and removes unsafe `innerHTML` reset paths. | V |
| WEB-20 | Confirmed | Click dispatch uses an explicit handler map rather than arbitrary `window[functionName]`. | V |
| WEB-21 | Confirmed | Global FastAPI serializer monkey-patching is removed in favor of explicit response schemas. | V |
| WEB-22 | Partial | Audit writes are authoritative/structured with correlation IDs; an injected dual-write failure rehearsal was not run. | V, P |
| WEB-23 | Partial | Plugin-event recording is guarded and structured; external broker/outbox failure rehearsal was not run. | V |
| WEB-24 | Confirmed | Production donations require real YooKassa verification; mock provider is fail-closed. | V, P |
| WEB-25 | Confirmed | RCON commands are complete-token validated and reject control/newline/semicolon injection. | V |
| WEB-26 | Confirmed | Destructive admin actions use actor/action/target/version-bound short-lived confirmation. | V |
| WEB-27 | Confirmed | Role revocation invalidates sessions and refresh sessions. | V, P |
| WEB-28 | Partial | Browser/contract coverage exists, but a complete Playwright matrix was not run in this environment. | V |
| WEB-29 | Confirmed | 500 responses expose a correlation ID while logs retain safe diagnostics. | V, P, S |
| WEB-30 | Partial | Production-critical seams are guarded and reviewed; `main.py` remains a large module and was not split solely for appearance. | V, P |

## WORLD — evacuation, safe points and persistence

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| WORLD-1 | Confirmed | Encoding/no-mojibake validators cover WorldCore and player-facing output. | V |
| WORLD-2 | Confirmed | Safecheck awaits redirect completion and reports moved/failed/skipped outcomes. | V |
| WORLD-3 | Confirmed | Trusted teleport tokens bind player, operation, world, target/range and expiry. | V |
| WORLD-4 | Confirmed | Border/config state uses atomic replacement and flush/fsync. | V |
| WORLD-5 | Confirmed | Safe-point checks run asynchronously with bounded work before main-thread teleport. | V |
| WORLD-6 | Confirmed | Evacuation work is bounded per tick and queued. | V |
| WORLD-7 | Confirmed | Command and trusted-plugin teleport policies are separate. | V |
| WORLD-8 | Confirmed | Redirect target cycle detection is enforced. | V |
| WORLD-9 | Confirmed | OperationResult status/reason text is localized and player-safe. | V |
| WORLD-10 | Confirmed | GUI success depends on persisted config/state, not command dispatch alone. | V |

## AUTH — AuthEffects/AuthMe integration

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| AUTH-1 | Confirmed | Local authenticated state is a short cache and is cleared on logout/quit. | V, M |
| AUTH-2 | Confirmed | Existing slowness remaining duration is recorded/restored correctly. | V |
| AUTH-3 | Confirmed | AuthMe logout event is handled; event fallback is used only when API is unavailable. | V, M |
| AUTH-4 | Confirmed | API lookup errors fail closed rather than granting access from stale state. | V |
| AUTH-5 | Partial | Command allowlist is explicit and reviewed by validators; a live adversarial command matrix with real AuthMe was not run. | V, M |
| AUTH-6 | Partial | Production AuthMe and AuthEffects both enabled with three hooks; complete player login/logout integration smoke still requires a real player session. | M, S |

## ADMIN — admin plugin ownership and callbacks

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| ADMIN-1 | Partial | Dangerous admin seams are guarded and validators cover them; the large legacy plugin was not fully decomposed. | V |
| ADMIN-2 | Confirmed | Election item ownership is delegated through ElectionCore/service bridges. | V |
| ADMIN-3 | Confirmed | AR compatibility uses EconomyCore authority rather than duplicate mutation logic. | V |
| ADMIN-4 | Confirmed | Retired handlers/legacy election runtime are excluded from the compiled release. | V, R |
| ADMIN-5 | Confirmed | Internal audit labels are kept in structured internal logs and redacted from player-facing output. | V, P |
| ADMIN-6 | Confirmed | Client brand is not treated as a security credential/authorization signal. | V |
| ADMIN-7 | Confirmed | Reflection bridges are optional, version-checked and fail closed. | V |
| ADMIN-8 | Confirmed | Every menu callback rechecks permission and target state. | V |
| ADMIN-9 | Confirmed | Bulk operations have bounded/rate-limited, resumable queues. | V |
| ADMIN-10 | Confirmed | Domain plugins remain owners of economy/election/narcotic/artifact data. | V |

## ITEM — catalog, visuals and interaction coverage

| ID | Status | Remediation | Evidence |
|---|---|---|---|
| ITEM-1 | Confirmed | Catalog validators enforce IDs, materials, CMDs, effects, prices, cooldowns and limits. | V |
| ITEM-2 | Confirmed | Effect probability has one authoritative configuration source. | V |
| ITEM-3 | Confirmed | Lore is generated/checked against actual probability and mechanics. | V |
| ITEM-4 | Confirmed | Compass lore and measured mechanic use the same distance/region policy. | V |
| ITEM-5 | Confirmed | Lightning behavior is explicitly controlled/cosmetic in catalog and code. | V |
| ITEM-6 | Partial | Main/offhand/action contracts are covered; exhaustive live item interaction matrix was not run. | V |
| ITEM-7 | Partial | Inventory transport and mailbox contracts are covered; exhaustive live inventory matrix was not run. | V |
| ITEM-8 | Partial | Resource-pack build and deterministic hash checks pass; golden screenshot comparison is not configured. | V, R |
| ITEM-9 | Confirmed | Server/client/resource-pack IDs and versions are emitted from aligned release metadata. | V, R, S |
| ITEM-10 | Confirmed | Non-normative item names are retained as an explicit catalog/product decision, not accidental identifiers. | V |

## Live release result

- Git branch: `agent/deep-election-artifacts-audit`.
- Latest release metadata commit: `3138440`; the last installed production
  archive is `5f227bb9c9c49c1d2090b94c67ae9b83b9351bd05a6dc4c44f26544c6f456c01`.
  Final archive `059ee2b3f9492becceb6414fb59714ec50c28ffc7235664b15e8175e9fb68f75`
  is built, signed and validated locally; its four upload parts are ready,
  but the target currently times out on both SSH 2222 and HTTP, so installation
  cannot be truthfully marked complete.
- Production at the last successful live check: all required services active;
  `/api/health` reports 12/12 checks, 0 failures and 0 warnings; HTTP root,
  health, runtime, download and resource-pack return `200`; public login
  returns `426`; direct loopback login returns `401` for invalid credentials;
  the public nginx listener is HTTP-only with no port 443 listener.
- AuthMe/AuthEffects: enabled after the YAML repair; no current
  `invalid YAML`, `NoClassDefFoundError` or `ClassNotFoundException` in the
  current Minecraft log. Essentials compatibility, GrimAC no-provider and
  PlaceholderAPI network timeout messages are third-party/non-blocking
  warnings recorded separately in the final report.
- Clean-state DB audit: site accounts `5`, admin users `4`, Minecraft account
  links `3`, whitelist account links `3`; election, bank/economy, AR,
  narcotics, donation, polling-station, protected-block and player gameplay
  rows are `0`. Catalog/schema/audit/system metadata remain intentionally for
  service operation and traceability.
- Backup timer is active with a daily 03:30 schedule, `Persistent=yes` and
  retention enforcement of three daily copies; legacy cleanup retained
  `game-wipe-20260802-155857` and exactly three newest runtime rollback copies.
  Root disk after cleanup: `455G` total, `38G` used, `398G` available (9%).
