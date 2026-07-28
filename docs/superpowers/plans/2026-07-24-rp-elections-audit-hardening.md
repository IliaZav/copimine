# RP Elections Audit Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every read and write path of the simplified two-stage RP elections workflow safe against stale menus, concurrent requests, mixed rounds, and invalid block state.

**Architecture:** PostgreSQL remains the source of truth. The web control path and ElectionCore share a database-level single-active-campaign invariant; every mutation rechecks the current campaign and stage while holding the campaign row lock. Read models only include the current round, and stale GUI actions fail closed without changing another campaign.

**Tech Stack:** FastAPI/Python, PostgreSQL, Java Paper plugin, PowerShell contract validators.

---

### Task 1: Lock the one-campaign invariant in every writer

**Files:**
- Modify: `db/migrations/20260724_014_rp_election_audit_hardening.sql`
- Modify: `admin-web/backend/main.py`
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Test: `tests/ValidateCopiMineElectionAuditHardening.ps1`

- [ ] Add a migration that repairs duplicate active RP rows deterministically, then creates a partial unique index for one active `rp-two-stage` row.
- [ ] Create the same index during both application and plugin bootstrap so either writer is protected on a fresh database.
- [ ] Add a contract assertion that both writers use the index and keep all old rows as history.

### Task 2: Make public/admin read models round-safe

**Files:**
- Modify: `admin-web/backend/main.py`
- Test: `tests/ValidateCopiMineElectionAuditHardening.ps1`

- [ ] Read `current_round` from the selected RP campaign and constrain candidate vote aggregates, public turnout, player results, and admin results to that round.
- [ ] Keep the voter-identity query private and preserve the no-website-voting rule.
- [ ] Add a contract assertion for every aggregate query.

### Task 3: Close stale Java block actions safely

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Test: `tests/ValidateCopiMineElectionAuditHardening.ps1`

- [ ] Revalidate the active RP campaign, stage, block id, world, and coordinates inside the same transaction when creating a voting block.
- [ ] Revalidate the current campaign ownership before disabling a block; stale GUI actions must not update another election.
- [ ] Reject blank world names after trimming and handle malformed player UUIDs without rolling back an otherwise valid election finish.

### Task 4: Verify and release without a data wipe

**Files:**
- Modify: `ELECTIONS_MANUAL_TEST_MATRIX.md`

- [ ] Run the RP contract test, all election validators, Python compilation, Java build/tests, and diff checks.
- [ ] Build a new full release, verify its hash and manifest, upload it, and install without `--wipe-worlds` or a database dump.
- [ ] Check remote services, GET `/api/health`, the public election endpoint, and the deployed plugin/resource versions.
