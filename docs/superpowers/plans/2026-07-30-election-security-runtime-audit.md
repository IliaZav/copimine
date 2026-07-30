# Election Security and Runtime Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce, diagnose, and repair election-plugin runtime failures and
security-sensitive state transitions while preserving approved RP rules.

**Architecture:** `CopiMineElectionCore.java` owns Bukkit event handling,
menus, asynchronous database work, and election state transitions.  Existing
PowerShell validators inspect durable Java/web contracts; new regression
validators will assert each newly confirmed runtime invariant before a focused
Java change is made.

**Tech Stack:** Java 21, Paper API, PostgreSQL SQL accessed through JDBC,
PowerShell contract validators, Git.

## Global Constraints

- Do not deploy, restart services, or enable real payments.
- Preserve election history and all unrelated game and account data.
- Keep applications website-only and voting in-game only.
- Permit only two through four selected candidates and only 24/48/72-hour
  voting terms.
- All new production behaviour requires a regression test that was observed
  failing before the matching source change.

---

### Task 1: Establish a reproducible baseline

**Files:**
- Review: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Review: `tests/ValidateCopiMineElection*.ps1`
- Review: `tests/RunCopiMineValidators.ps1`

**Interfaces:**
- Consumes: the released election source and its test contracts.
- Produces: a recorded list of baseline failures and the source locations that
  can reproduce each reported action.

- [ ] **Step 1: Build the election plugin**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-election-core\build-plugin.ps1`

Expected: `javac` and `jar` exit with code 0.

- [ ] **Step 2: Run the full election validator set**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunCopiMineValidators.ps1 -Pattern 'ValidateCopiMineElection*.ps1'`

Expected: Every selected validator reports PASS; otherwise retain its exact
failure message as the first audit candidate.

- [ ] **Step 3: Trace reported UI actions to their state mutations**

Inspect `handleMenuAction`, `setRpDebates`, `setRpVoting`,
`applyRpCandidateSelection`, `finishRpElectionEarly`, and polling-station
creation/removal methods.  Record the call chain, current-stage guard, and
database statement for each of the reported duration, debates, candidate-save,
early-finish, and station-replacement paths.

### Task 2: Audit access and state-transition boundaries

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Create or modify: `tests/ValidateCopiMineElectionRuntimeGuards.ps1`

**Interfaces:**
- Consumes: menu actions from `handleMenuAction(Player,String,ClickType,MenuHolder)`.
- Produces: state transitions that re-check administrator authority, active
  campaign identity, current stage, selected candidate count, and duration at
  the database mutation boundary.

- [ ] **Step 1: Write one failing validator per confirmed missing guard**

For a confirmed defect, assert the exact enclosing method and required guard,
for example `hasElectionAdmin(player)` before a mutable action and a
stage-qualified `UPDATE elections ... WHERE id=? AND current_stage=?`.

- [ ] **Step 2: Run the new validator before production changes**

Run: `powershell -NoProfile -File .\tests\ValidateCopiMineElectionRuntimeGuards.ps1`

Expected: failure naming the absent guard or unsafe SQL condition.

- [ ] **Step 3: Implement the smallest matching Java change**

Keep external UI action names stable.  Re-read authoritative database state in
the asynchronous mutation, return a descriptive `IllegalStateException` for
invalid state, and schedule Bukkit UI feedback on the main thread.

- [ ] **Step 4: Run the focused regression and state-machine validators**

Run: `powershell -NoProfile -File .\tests\ValidateCopiMineElectionRuntimeGuards.ps1; powershell -NoProfile -File .\tests\ValidateCopiMineElectionStateMachineStrictMatrix.ps1; powershell -NoProfile -File .\tests\ValidateCopiMineElectionRpManualTransitions.ps1`

Expected: all three exit with code 0.

### Task 3: Audit polling-station lifecycle and voting integrity

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Create or modify: `tests/ValidateCopiMineElectionRuntimeGuards.ps1`
- Review: `tests/ValidateCopiMineElectionRpStationReplacement.ps1`

**Interfaces:**
- Consumes: persisted `election_voting_blocks` and `protected_blocks` rows.
- Produces: a recreated station with a refreshed protection cache and an
  interaction route to the active vote menu, while preserving prior votes.

- [ ] **Step 1: Write a failing validator for each confirmed lifecycle gap**

Require the precise transaction order: deactivate stale protection, refresh
the cache, then remove the physical block; create at the same location using
an upsert that restores `active=1`.

- [ ] **Step 2: Run it to observe the failure**

Run: `powershell -NoProfile -File .\tests\ValidateCopiMineElectionRuntimeGuards.ps1`

Expected: failure identifies the absent or incorrectly ordered operation.

- [ ] **Step 3: Implement only the proven lifecycle correction**

Do not delete election, candidate, ballot, or vote records.  Do not allow a
stale station id to route a player to an admin-only screen.

- [ ] **Step 4: Run lifecycle and vote-integrity checks**

Run: `powershell -NoProfile -File .\tests\ValidateCopiMineElectionRpStationReplacement.ps1; powershell -NoProfile -File .\tests\ValidateCopiMineElectionStations.ps1; powershell -NoProfile -File .\tests\ValidateCopiMineElectionVoteUniqueness.ps1; powershell -NoProfile -File .\tests\ValidateCopiMineElectionVotingAtomicity.ps1`

Expected: all commands exit with code 0.

### Task 4: Complete coverage and release verification

**Files:**
- Modify: files identified by confirmed defects only.
- Review: `tests/ValidateCopiMineElection*.ps1`

**Interfaces:**
- Consumes: all focused test results.
- Produces: a compilable plugin and a complete, passing election validation
  suite.

- [ ] **Step 1: Build the final plugin**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-election-core\build-plugin.ps1`

Expected: exit code 0.

- [ ] **Step 2: Run all election validators fresh**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunCopiMineValidators.ps1 -Pattern 'ValidateCopiMineElection*.ps1'`

Expected: zero failed validators.

- [ ] **Step 3: Inspect the diff and commit only intended source, tests, and documentation**

Run: `git diff --check; git status --short`

Expected: no whitespace errors and no generated build artifact staged unless
the repository already tracks that exact release artifact intentionally.

- [ ] **Step 4: Fast-forward the verified commit into `main` and retest there**

Run: `git checkout main; git merge --ff-only codex/election-security-audit`

Expected: fast-forward succeeds; repeat Steps 1 and 2 on `main` before push.
