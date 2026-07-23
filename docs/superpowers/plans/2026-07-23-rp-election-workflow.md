# RP Elections and President Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the complicated chair/ballot election process with a manual-admin RP campaign containing only the public stages “Debates” and “Voting”, while preserving election history, strengthening president and tax rules, and keeping all existing player, website, whitelist, bank, inventory, and world data.

**Architecture:** The web panel owns authenticated candidate applications and the administrator’s candidate selection. `CopiMineElectionCore` remains the only authority for stage changes, protected voting blocks, online-only votes, winner assignment, president terms, and tax closure. The database keeps legacy rows for history, but new elections use a small explicit workflow: `APPLICATIONS_OPEN` (private setup state), `DEBATES`, `VOTING`, and `COMPLETED`; only the two middle states are shown as election stages to players.

**Tech Stack:** Java/Paper plugin (`copimine-election-core`), PostgreSQL migrations, FastAPI backend (`admin-web/backend/main.py`), vanilla HTML/CSS/JavaScript cabinet (`admin-web/frontend`), PowerShell/Java regression tests, Ubuntu systemd deployment.

---

## Confirmed product rules

- There is at most one campaign at a time. A new campaign is rejected while an active president has an unexpired term; the president must resign or be removed by an administrator first.
- The owner and every administrator, including junior administrators, may create campaigns, review applications, select candidates, create voting blocks, and change/finish stages.
- Applications are submitted only through the website. The form contains Minecraft nickname, reason for running, planned server changes, short programme, and optional faction/organisation.
- The administrator may approve from two through four candidates. Fewer than two prevents the campaign from starting.
- Debates and voting are started and ended manually. Voting lasts between 24 and 72 hours; the deadline may be extended only while voting and never beyond the original voting start plus 72 hours.
- Vote totals are visible in the election panel during voting. Each online active player may vote once on the server, cannot change the vote, and must confirm after choosing a candidate head.
- Any number of protected voting blocks may be created. A non-administrator cannot break one. If an administrator breaks it, the election and votes remain intact; placing another block reconnects it to the same campaign.
- Candidate heads use current Minecraft skins, with a standard head fallback; hovering shows the candidate nickname.
- At completion the highest vote total becomes president automatically. A tie is resolved by an administrator choosing the winner in a confirmation dialog.
- The presidential term is exactly seven days. The president may resign from the GUI or by the agreed in-game action; an administrator may remove the president. No acting president and no automatic new election are created.
- Changing or ending a presidential term archives the old taxes immediately. The new president creates a new tax. Existing tax rules remain: 0–5 AR, only 24/48/72 hours, voluntary full payment, no accumulation, and the three-month subscription exemption.
- The right-hand panel remains structurally unchanged.
- Existing election history is read-only history; website accounts, whitelist, worlds, inventories, bank balances, and other runtime data are preserved.

## Files and responsibilities

### Election runtime

- Modify `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`:
  - replace the public stage menu with the two-stage RP flow;
  - add application-free candidate selection checks (2–4 active candidates);
  - add protected voting-block creation/reconnection and online-only vote validation;
  - add confirmation, single-vote, tie-resolution, winner assignment, resignation, and forced-removal paths;
  - close taxes whenever a president term ends or changes;
  - keep legacy chair/ballot rows readable but unreachable from the new workflow.
- Modify `copimine-election-core/plugin.yml` only if a new explicit command/permission is needed; keep the existing admin permission nodes compatible.
- Modify `copimine-election-core/config.yml` for the 7-day term and 24/72-hour voting bounds, with the same values used by the backend and migration defaults.

### Web backend and data

- Modify `admin-web/backend/main.py`:
  - add authenticated candidate-application input/output models and endpoints;
  - add administrator endpoints for campaign creation, candidate approval (max four), stage start/end, voting deadline extension, block metadata, tie winner, resignation/removal, and read-only history;
  - make all mutations idempotent and actor-audited; reject website voting and reject stage changes by non-administrators;
  - keep the right-panel payload and current tax payload field names stable.
- Add `db/migrations/20260723_013_election_rp_workflow.sql`:
  - add nullable application fields `motivation`, `server_plan`, `programme`, `faction`, `submitted_via`, `submitted_by_account_id`;
  - add campaign fields `applications_opened_at`, `voting_started_at`, `voting_deadline_at`, `voting_ended_at`, `closed_reason`;
  - add `election_voting_blocks(election_id, world, x, y, z, active, created_at, removed_at)` with a unique active location constraint;
  - add `election_vote_receipts(election_id, voter_uuid, candidate_uuid, cast_at)` with a unique `(election_id, voter_uuid)` constraint;
  - add `president_terms.resignation_reason`, `removed_by`, and `ended_at` where absent;
  - add indexes for active campaign, candidate application status, voting deadline, block location, and vote receipts;
  - do not delete or rewrite legacy election tables.
- Update `admin-web/backend/discord_bot.py` only to publish the new public campaign status and candidate programme summaries; it must not accept applications or votes.

### Web pages and shared UI

- Modify `admin-web/frontend/cabinet/elections.html` and `admin-web/frontend/assets/js/cabinet-runtime.js`:
  - authenticated application form with the five requested fields;
  - compact admin workflow: campaign status, pending applications, approve/reject buttons, selected candidates (2–4), manual Debates/Voting controls, deadline control, voting-block list, tie-resolution and president actions;
  - candidate cards with head, nickname, programme, vote count, and progress bars;
  - clear errors and centered confirmation dialogs for every destructive or vote action;
  - preserve the existing right-hand panel and show history below the active campaign.
- Modify `admin-web/frontend/assets/style.css` for the two-stage labels, candidate cards, heads, deadline badges, block cards, confirmation modal, responsive layout, and light/dark contrast without reintroducing the retired legacy page.
- Add `docs/ELECTIONS_RP_GUIDE_RU.md` with player and administrator instructions: application, debates, starting/ending voting, block placement, one-vote rule, tie handling, winner/term, resignation/removal, and tax reset behavior.

### Tests and release

- Add `tests/ValidateCopiMineElectionRpWorkflow.ps1` for the Java/plugin contract.
- Add `admin-web/scripts/election_rp_contract_test.py` for backend request validation, permissions, idempotency, deadline bounds, and payload compatibility.
- Extend `admin-web/scripts/frontend_backend_contract_test.py` for the application form, admin controls, confirmation modal, and unchanged right-panel fields.
- Update `copimine-election-core/build-plugin.ps1`, release metadata, and the generated plugin/resource-pack/modpack manifests through the existing packaging scripts.

## Implementation tasks

### Task 1: Freeze the current state and add migration tests

**Files:** `db/migrations/20260723_013_election_rp_workflow.sql`, `tests/ValidateCopiMineElectionRpWorkflow.ps1`, `docs/ELECTIONS_RP_GUIDE_RU.md`.

- [ ] Write migration assertions for existing accounts, whitelist, worlds, bank balances, and historical election rows; the migration must only add columns/tables/indexes.
- [ ] Write failing contract assertions for one active campaign, 2–4 candidate bounds, website-only applications, and preserved legacy rows.
- [ ] Run the assertions against a disposable PostgreSQL schema and verify they fail before the runtime changes.
- [ ] Add the first version of the guide so names and rules are fixed before UI work.
- [ ] Commit only the migration/tests/docs baseline.

### Task 2: Implement the backend application and campaign API

**Files:** `admin-web/backend/main.py`, `db/migrations/20260723_013_election_rp_workflow.sql`, `admin-web/scripts/election_rp_contract_test.py`.

- [ ] Add request models with explicit limits: nickname 3–32 characters, motivation/programme/server plan 1–2000 characters, optional faction 0–120 characters; trim whitespace and reject control characters.
- [ ] Require an authenticated site account linked to a Minecraft UUID; allow the current president to apply, but reject duplicate applications in the same campaign.
- [ ] Add admin-only mutations with an audit row for every decision; make repeated approve/reject/start/end requests return the current state without duplicating rows.
- [ ] Enforce `APPLICATIONS_OPEN` as private setup, require 2–4 approved candidates before `DEBATES`, and require the same selected candidates before `VOTING`.
- [ ] Enforce `24h <= voting_deadline - voting_started <= 72h`; permit only forward extensions while voting and cap at `voting_started + 72h`.
- [ ] Return public candidate programmes and vote totals without exposing voter UUIDs or PIN/account secrets.
- [ ] Run `python admin-web/scripts/election_rp_contract_test.py`; expected result: all API validation and authorization cases pass.
- [ ] Commit the backend and migration changes.

### Task 3: Replace the plugin state machine with the RP state machine

**Files:** `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`, `copimine-election-core/config.yml`, `copimine-election-core/plugin.yml`, `tests/ValidateCopiMineElectionRpWorkflow.ps1`.

- [ ] Add one transition guard that rejects a new campaign while an active president term exists and rejects fewer than two selected candidates.
- [ ] Make the player-facing stage menu expose only Debates and Voting; keep old stage records readable for history and map old active records to the nearest safe private setup state during migration.
- [ ] Add manual admin actions for start/end debates and start/end voting; no automatic stage timer may advance the campaign.
- [ ] Add online-only vote validation: `Player` must be online, voter UUID must be on the server, campaign must be in Voting, and a unique receipt must not already exist.
- [ ] Add the confirmation menu before recording a vote, then write the receipt and vote atomically so double clicks cannot create a second vote.
- [ ] Add a tie menu available only after voting is ended; an admin selects one tied candidate and the plugin records the decision and audit event.
- [ ] On completion, create exactly one seven-day `president_terms` row for the winner; on resignation/removal, end the term, archive its tax, and leave the server without an acting president.
- [ ] Reconnect protected blocks by election ID, prevent non-admin breaking, and make admin removal leave campaign rows and votes untouched.
- [ ] Run the Java build and all existing election validators plus `ValidateCopiMineElectionRpWorkflow.ps1`.
- [ ] Commit the plugin change and built jar.

### Task 4: Build the website application and admin workflow

**Files:** `admin-web/frontend/cabinet/elections.html`, `admin-web/frontend/assets/js/cabinet-runtime.js`, `admin-web/frontend/assets/style.css`.

- [ ] Render the application form only for authenticated linked players; show submitted status and prevent duplicate submissions.
- [ ] Render an admin-only review table with the five answers, approve/reject reason, selected-candidate count, and a hard stop at four approvals.
- [ ] Render the two public stages, manual controls, bounded deadline editor, active blocks, candidates, live vote totals, winner/tie action, and seven-day term status.
- [ ] Add a centered modal that confirms candidate approval, stage changes, vote casting, tie resolution, resignation, and removal; show server errors in the modal instead of appending `[object Object]` at the page bottom.
- [ ] Keep the right-side panel field names and layout contract unchanged; add regression selectors for those fields.
- [ ] Run `node admin-web/scripts/frontend_runtime_selftest.mjs` and `python admin-web/scripts/frontend_backend_contract_test.py`.
- [ ] Commit the web UI and guide changes.

### Task 5: Verify gameplay, tax closure, and compatibility

**Files:** `admin-web/scripts/backend_smoketest.py`, `admin-web/scripts/security_selftest.py`, `tests/ValidateCopiMineElectionStateMachineStrictMatrix.ps1`, `admin-web/TEST_REPORT.txt`.

- [ ] Test the complete path: create campaign → submit two applications → approve 2–4 → start/end debates → start voting for 24h → cast two online votes → end voting → assign winner.
- [ ] Test rejection paths: one candidate, five approvals, website vote, offline voter, second vote, changed vote, deadline over 72h, and stage changes by an unauthorized account.
- [ ] Test multiple blocks, block break/replacement, candidate head fallback, tooltip nickname, and vote confirmation.
- [ ] Test president resignation and admin removal; verify no acting president, no automatic new campaign, and old tax cannot be paid after closure.
- [ ] Test tax invariants (0–5 AR, 24/48/72 hours, full voluntary payment, subscription exemption) and unchanged right-panel payload.
- [ ] Record commands, expected outputs, and any known non-blocking warnings in `admin-web/TEST_REPORT.txt`.
- [ ] Commit test updates and the release report.

### Task 6: Package, review, and deploy without data wipe

**Files:** `deploy/release_manifest.json`, generated archive under `release/`, `docs/SAFE_DEPLOY_UBUNTU_RU.md`.

- [ ] Run the complete local test suite and `scripts/package_full_release.ps1`; verify archive SHA-256, plugin jar checksum, modpack checksum, and resource-pack checksum.
- [ ] Create a pre-deploy PostgreSQL dump and checksums for whitelist, `.env`, account data, worlds, banks, inventories, and election history; keep them under `/opt/copimine-backups`.
- [ ] Upload the archive to `qwerty@100.108.97.11:/home/qwerty/copimine-upload` and verify the remote SHA-256 before installation.
- [ ] Run `sudo bash /home/qwerty/copimine-upload/install_release.sh <archive>` without `--wipe-worlds` and without a database dump. The installer must preserve runtime paths and website accounts.
- [ ] Verify `copimine-admin`, `copimine-minecraft`, `nginx`, Discord bot/bridge, and PostgreSQL services; verify served modpack/resource-pack hashes and `require-resource-pack=true`.
- [ ] Run a real-site smoke test: login, submit an application, open the admin election page, inspect candidate data, and confirm the old UI is not served.
- [ ] Push the final commits to `main` only after all tests and remote checks pass; record commit SHA, archive SHA-256, service statuses, and rollback path in the final report.

## Acceptance checklist

- [ ] One active campaign maximum; new campaign blocked while a president term is active.
- [ ] Website-only applications with all five fields and optional faction.
- [ ] Admins and junior admins have the same election permissions.
- [ ] Two to four approved candidates required; no five-candidate or one-candidate campaign can start.
- [ ] Only Debates and Voting are exposed; both are manual.
- [ ] Voting is 24–72 hours, extendable only up to 72 hours total.
- [ ] Online-only, one-time, confirmed vote; live totals visible; tie resolved by admin.
- [ ] Multiple protected blocks work; breaking one never resets the election.
- [ ] Winner automatically receives a seven-day term; resignation/removal closes taxes and leaves no acting president.
- [ ] Historical data, accounts, whitelist, worlds, banks, inventories, and right panel remain intact.
- [ ] Local and real-server tests pass; the final archive and `main` contain the same release manifest.

## Self-review and known assumptions

- Applications have no automatic deadline because the administrator explicitly controls both stages; the private `APPLICATIONS_OPEN` setup state prevents this from adding a third player-facing stage.
- Candidate identity requires a linked Minecraft account so the plugin can create a head and enforce one-person/one-vote rules; no website voting endpoint will be added.
- A tied result is not auto-resolved: an administrator must select one tied candidate after voting ends, as requested.
- Deployment is a full file replacement with backups, not a world/database wipe. The requested preservation of accounts, whitelist, worlds, balances, inventories, and history takes precedence.

## Repository analysis findings added before implementation

The current implementation is a multi-stage ballot/committee workflow, so a direct replacement without compatibility boundaries would recreate the reported failures. The following findings are now part of the execution plan:

1. `CopiMineElectionCore.java` currently exposes `PREPARATION`, `APPLICATIONS`, `REVIEW`, `DEBATES`, `VOTING`, `COUNTING`, `SECOND_ROUND`, `FINISHED`, and `PRESIDENT_TERM`. `ElectionStateMachine` requires review, active round candidates, and an active polling station before voting. The new player-facing flow will therefore use a compatibility adapter rather than deleting enum values or historical rows.
2. `ensureElectionExists` currently creates a new `PREPARATION` election whenever no active row exists and deactivates the previous row. The replacement must first lock and check `president_terms` and reject creation while a non-expired term exists; it must never silently deactivate a live campaign.
3. `approveApplication` currently requires `REVIEW`, `submitted_at > 0`, and creates `round_candidates`. The website workflow will mark a submitted web application directly, keep the same candidate tables for compatibility, and atomically enforce the 2–4 limit.
4. `polling_stations`, `protected_blocks`, and the item-ballot path currently depend on `cik_chairs` and a second deposit action. New voting blocks will keep protected coordinates but route ordinary players directly to a confirmation GUI and write one vote receipt in the same transaction; chair assignment and physical ballot issuance will remain history-only.
5. `confirmBallotChoice` currently confirms an item and waits for `depositBallot`; it cannot implement “one click, one vote” by itself. A separate `confirmDirectVote` path will validate online status, campaign/deadline, candidate membership, and the unique voter constraint before recording the vote.
6. `assignPresident` currently uses `max(7, president_term_days)`, so an administrator can accidentally create a term longer than seven days. The RP path will always write exactly `7 * 86_400_000` milliseconds and archive taxes for the previous active term in the same transaction.
7. `removePresident` currently updates the election found by `currentElectionId`, which is unsafe after a campaign is completed or stopped. The replacement will lock the active `president_terms` row first, close its tax, clear the active president state, and leave no acting-president row.
8. The backend has an admin review endpoint but no authenticated player candidate-application endpoint. It also uses `require_admin` for review, which excludes junior administrators. The new review/control endpoints will use the panel-admin guard while dangerous database operations remain owner/full-admin-only.
9. `admin-web/frontend/cabinet/elections.html` currently renders the old station/chair/ballot sections and the player navigation has no elections route. The new view will branch by role: a player sees the application form and public campaign status; an administrator sees review, candidate selection, stage, block, tie, and president controls.
10. `admin-web/backend/election_detail_sync` already has a safe public/admin projection. It will be extended with `votingDeadlineAt`, `votingStartedAt`, selected candidate programmes, active voting blocks, and public vote totals without adding voter identities to the response.

These boundaries are deliberately additive: old history remains readable, the new workflow is explicit, and every state-changing path is checked in both the web layer and the plugin before release.
