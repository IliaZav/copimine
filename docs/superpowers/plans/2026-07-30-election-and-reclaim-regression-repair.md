# Election and Donation Reclaim Regression Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the reported election controls, polling-station replacement, and donation-loss reclaim paths work end-to-end and remain covered by regression tests.

**Architecture:** The frontend sends a minimal request for each election action. The backend validates web state and returns readable business failures, while the election plugin owns in-game station bindings and voting. The artifacts plugin records all eligible terminal loss through one idempotent journal-to-database transition.

**Tech Stack:** Java 21/Paper, Python/FastAPI/PostgreSQL, browser JavaScript, PowerShell validators, Gradle.

## Global Constraints

- Preserve historical election, player, inventory, tax, and world data.
- Keep one RP campaign; accept 2–4 candidates; permit every administrator rank to manage it.
- Keep voting in-game only, manual, and limited to 24–72 hours.
- Breaking a station disables only its binding; recreation restores interaction.
- Only `LOSS_ONLY` donation items may become `LOST_RECLAIMABLE`, once per unique item.
- Do not deploy. Mirror verified source changes into `release_stage/copimine` only after checks pass.

---

### Task 1: Lock down the election web-control contract

**Files:**
- Modify: `admin-web/frontend/assets/js/cabinet-runtime.js:4184-4212`
- Modify: `admin-web/backend/main.py:8281-8754`
- Modify: `tests/ValidateCopiMineElectionWebControls.ps1`

**Interfaces:**
- Consumes: `ElectionRpControlIn(action, stage, application_ids, candidate_uuid, voting_hours)`.
- Produces: `POST /api/elections/rp/control` with `{ electionId, stage, earlyFinished }` or a readable HTTP 400/409 detail.

- [ ] **Step 1: Write the failing request-shape regression assertions**

Add these assertions to `ValidateCopiMineElectionWebControls.ps1`:

```powershell
Require $frontend 'const payload = { action };' 'Election controls must begin with a minimal payload.'
Require $frontend 'payload.stage = stage;' 'Only a stage action may include a stage value.'
Require $frontend 'payload.voting_hours = Number' 'Only voting-stage actions may include a duration.'
Require $backend 'Нужно выбрать от 2 до 4 одобренных заявок' 'Candidate-count validation must be readable.'
Require $backend 'Сначала заверши дебаты' 'Voting must require completed debates.'
```

- [ ] **Step 2: Verify RED**

Run: `pwsh -NoProfile -File tests/ValidateCopiMineElectionWebControls.ps1`

Expected: failure because the existing client always sends empty `stage` and unrelated fields.

- [ ] **Step 3: Implement the minimal request and state guards**

Replace the unconditional object in `rpElectionControl` with:

```javascript
const payload = { action };
if (action === "stage") {
  payload.stage = stage;
  if (stage === "VOTING") payload.voting_hours = Number($("rpVotingHours")?.value || 24);
}
if (action === "finish" || action === "finish_early") {
  payload.candidate_uuid = $("rpWinnerUuid")?.value?.trim() || "";
}
```

In `rp_election_control_sync`, guard invalid stage, candidate count, duration, and early finish with `HTTPException(400|409, detail=<Russian user-facing message>)` before updates. Keep confirmation headers for finish, finish-early, and removal.

- [ ] **Step 4: Verify GREEN**

Run: `pwsh -NoProfile -File tests/ValidateCopiMineElectionWebControls.ps1`

Expected: `Election web controls contract: PASS`.

- [ ] **Step 5: Commit**

Run:

```powershell
git add admin-web/frontend/assets/js/cabinet-runtime.js admin-web/backend/main.py tests/ValidateCopiMineElectionWebControls.ps1
git commit -m "fix: harden election web controls"
```

### Task 2: Repair break and recreate of polling stations

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java:553-607,3859-3991,4630-4640`
- Modify: `tests/ValidateCopiMineElectionStations.ps1`

**Interfaces:**
- Consumes: `ProtectedBlockInfo(kind="POLLING_STATION", linkedId)` and `BlockLocationRef(world,x,y,z)`.
- Produces: inactive old station/protection rows and one active coordinate binding after recreation.

- [ ] **Step 1: Write failing lifecycle assertions**

Add these lines to the station validator:

```powershell
Require-Contains $core 'UPDATE polling_stations SET active=0,updated_at=? WHERE id=?' 'Breaking a station must deactivate its station row.'
Require-Contains $core 'UPDATE election_voting_blocks SET active=0,updated_at=? WHERE id=?' 'Breaking a station must deactivate its voting row.'
Require-Contains $core 'UPDATE protected_blocks SET active=0,updated_at=? WHERE linked_id=?' 'Breaking a station must deactivate protection.'
Require-Contains $core 'ON CONFLICT(election_id,world,x,y,z) DO UPDATE SET active=1' 'Recreation must reactivate a coordinate binding.'
```

- [ ] **Step 2: Verify RED**

Run: `pwsh -NoProfile -File tests/ValidateCopiMineElectionStations.ps1`

Expected: failure naming an absent full disable/recreate contract.

- [ ] **Step 3: Implement one transactional disable and coordinate upsert path**

Have the confirmed `rp:block:disable` action deactivate `polling_stations`, `election_voting_blocks`, and `protected_blocks` in one transaction, queue visual cleanup, and remove the in-memory protection key on the main thread. Make station creation lock the coordinate, reactivate all rows, upsert protection, and refresh its display marker after commit.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
pwsh -NoProfile -File tests/ValidateCopiMineElectionStations.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionRightClickActions.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionProtectedBlocksHardening.ps1
```

Expected: each exits 0.

- [ ] **Step 5: Commit**

Run:

```powershell
git add copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java tests/ValidateCopiMineElectionStations.ps1
git commit -m "fix: restore polling station after replacement"
```

### Task 3: Cover every donation-loss reclaim source once

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java:1046-1180,1478-1569,8027-8075,8109-8222`
- Modify: `tests/ValidateCopiMineDonationReclaimLossOnly.ps1`

**Interfaces:**
- Consumes: `OfficialDonationRef(ownerUuid, uniqueItemId, itemId)` from creative deletion, void/despawn, explosion/contact, and plugin removal.
- Produces: one journal record and `ACTIVE|DELIVERING|PENDING_DELIVERY|DELIVERED → LOST_RECLAIMABLE` transition with audit data.

- [ ] **Step 1: Write failing loss-path assertions**

Add:

```powershell
Require-Contains $artifacts 'handleCreativeDonationLoss' 'Creative deletion must record a loss.'
Require-Contains $artifacts 'case BLOCK_EXPLOSION:' 'Explosion must record a loss.'
Require-Contains $artifacts 'case CONTACT:' 'Cactus contact must record a loss.'
Require-Contains $artifacts 'case VOID:' 'Void must record a loss.'
Require-Contains $artifacts 'EntityRemoveEvent.Cause.PLUGIN' 'Plugin removal must record a loss.'
Require-Contains $artifacts 'lossJournalInFlight.add(key)' 'A unique item loss must be idempotent.'
```

- [ ] **Step 2: Verify RED**

Run: `pwsh -NoProfile -File tests/ValidateCopiMineDonationReclaimLossOnly.ps1`

Expected: failure for the currently unprotected terminal-loss path or missing duplicate guard.

- [ ] **Step 3: Route all eligible loss handlers through the same idempotent operation**

Call `recordDonationLossOnce(ref, reason)` before removing an item entity and call `flushPendingDonationLossJournalAsync()` only when it succeeds. Keep `BROKEN`, `CONSUMED`, `REPLACED_AFTER_LOSS`, and non-`LOSS_ONLY` items out of reclaim. Keep the reconciler source-state set exactly `Set.of("ACTIVE", "DELIVERING", "PENDING_DELIVERY", "DELIVERED")`.

- [ ] **Step 4: Verify GREEN**

Run:

```powershell
pwsh -NoProfile -File tests/ValidateCopiMineDonationReclaimLossOnly.ps1
pwsh -NoProfile -File tests/ValidateCopiMineArtifactsDonationFailedCleanupStatusGuard.ps1
pwsh -NoProfile -File tests/ValidateCopiMineArtifactsNoDeliveringCacheTrust.ps1
```

Expected: each exits 0, and no validator accepts a broken or consumed item as reclaimable.

- [ ] **Step 5: Commit**

Run:

```powershell
git add copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java tests/ValidateCopiMineDonationReclaimLossOnly.ps1
git commit -m "fix: record donation losses for reclaim"
```

### Task 4: Audit all actionable election handlers

**Files:**
- Create: `tests/ValidateCopiMineElectionRuntimeGuards.ps1`
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `admin-web/backend/main.py`

**Interfaces:**
- Consumes: web election controls, `MenuHolder` actions, and protected-station interactions.
- Produces: authorization/state failures instead of player-visible raw exceptions, and no database work on the Bukkit main thread.

- [ ] **Step 1: Write the failing guard-audit test**

Create assertions:

```powershell
Require-Contains $core 'hasElectionAdmin(player)' 'Every game election mutation needs an admin guard.'
Require-Contains $core 'runAsync(() ->' 'Station database work must be asynchronous.'
Require-Contains $core 'sendUserError(player, error' 'Unexpected player failures must be normalized.'
Require-Contains $backend 'advisory_lock(conn, "copimine-rp-election")' 'Web election mutations must be serialized.'
Require-NotContains $core 'printStackTrace()' 'Election handlers must not expose raw stack traces.'
```

- [ ] **Step 2: Verify RED**

Run: `pwsh -NoProfile -File tests/ValidateCopiMineElectionRuntimeGuards.ps1`

Expected: failure that identifies the first missing runtime guard.

- [ ] **Step 3: Repair the actual unguarded dispatchers**

Add `hasElectionAdmin` at the player-input dispatcher, `require_panel_admin` at HTTP endpoints, `advisory_lock` before every web mutation, and `runAsync` before plugin SQL. Send unexpected game exceptions to `sendUserError`; log only `safeError(error)` server-side.

- [ ] **Step 4: Run focused verification and builds**

Run:

```powershell
pwsh -NoProfile -File tests/ValidateCopiMineElectionRuntimeGuards.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionStateMachineStrictMatrix.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionVoteUniqueness.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionVotingAtomicity.ps1
pwsh -NoProfile -File tests/ValidateCopiMineElectionPublicErrors.ps1
& .\copimine-election-core\build.ps1
& .\copimine-artifacts\build.ps1
```

Expected: every validator and both builds exit 0.

- [ ] **Step 5: Commit**

Run:

```powershell
git add copimine-election-core copimine-artifacts admin-web/backend/main.py tests/ValidateCopiMineElectionRuntimeGuards.ps1
git commit -m "test: cover election runtime guards"
```

### Task 5: Mirror only verified source edits locally

**Files:**
- Modify: matching paths below `D:/Desktop/Copimine/release_stage/copimine/`

**Interfaces:**
- Consumes: the verified `main...HEAD` file list.
- Produces: identical source/test files in the local release-stage tree.

- [ ] **Step 1: List the exact changed paths**

Run: `git diff --name-only main...HEAD`

Expected: only declared source, test, and documentation paths.

- [ ] **Step 2: Mirror explicit source files**

Use `Copy-Item` with explicit source/destination paths for changed `.java`, `.py`, `.js`, `.ps1`, and `.md` files. Do not copy archives, world data, database dumps, `.git`, or generated JARs.

- [ ] **Step 3: Verify local equality**

Run `Get-FileHash` for each mirrored pair, then repeat the focused validators from `D:/Desktop/Copimine/release_stage/copimine/tests`.

Expected: hashes match and every validator exits 0.
