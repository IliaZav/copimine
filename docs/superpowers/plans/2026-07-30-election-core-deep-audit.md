# CopiMineElectionCore Deep Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find and repair confirmed ElectionCore failure paths without changing the agreed RP rules or historical data.

**Architecture:** The audit treats GUI actions, asynchronous PostgreSQL persistence and Paper events as a single stateful boundary. Existing validators establish the permitted workflow; new regressions protect each confirmed repaired gap.

**Tech Stack:** Java 21, Paper API, PostgreSQL JDBC, PowerShell validators and Python contract checks.

## Global Constraints

- Preserve election, player, inventory, tax and world history.
- Keep one manual, in-game RP campaign with two to four candidates.
- Keep voting duration within 24 to 72 hours and do not connect real payments.
- Allow every administrator rank to manage elections.
- Do not deploy or restart the production server.

## Task 1: Establish a baseline

**Files:** `tests/ValidateCopiMineElection*.ps1`, `admin-web/scripts/election_rp_contract_test.py`, `copimine-election-core/build-plugin.ps1`.

**Interfaces:** Consumes current plugin source and web contract; produces a recorded baseline of reproducible failures.

- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunCopiMineValidators.ps1 -Pattern 'ValidateCopiMineElection*.ps1'`.
- [ ] Run `python .\admin-web\scripts\election_rp_contract_test.py` and `powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineInteractiveElectionRp.ps1`.
- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\copimine-election-core\build-plugin.ps1`.
- [ ] Record every failed command with its exact source contract before changing Java.

## Task 2: Trace all mutations and errors

**Files:** Modify `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`; create or modify `tests/ValidateCopiMineElectionRuntimeGuards.ps1`.

**Interfaces:** Consumes menu actions, Paper events and PostgreSQL state; produces authorised asynchronous operations with a safe player message.

- [ ] Use `rg -n 'handle.*Action|onInventoryClick|@EventHandler|executeUpdate|execute\(|prepareStatement|runAsync|runSync' copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java` to map each write to its role check, phase guard, transaction and response.
- [ ] For every confirmed missing guard, add an exact static assertion to the runtime validator and execute it before the Java change; it must fail on the pre-fix source.
- [ ] Correct only the traced handler or persistence helper, preserving action names and schema.
- [ ] Re-run the new validator and require `Election runtime guards: PASS`.

## Task 3: Verify campaign-control transitions

**Files:** Modify `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`; modify `tests/ValidateCopiMineElectionRuntimeGuards.ps1` only for confirmed gaps.

**Interfaces:** Consumes campaign state, roster and voting duration; produces a valid manual transition or a readable rejection with no partial write.

- [ ] Run `ValidateCopiMineElectionStateMachineStrictMatrix.ps1`, `ValidateCopiMineElectionRpManualTransitions.ps1`, `ValidateCopiMineElectionRpActionFeedback.ps1` and `ValidateCopiMineElectionRpWorkflow.ps1`.
- [ ] Add a red assertion only when tracing proves a missing candidate-count, duration, current-stage or early-finish guard.
- [ ] Use a transaction or conditional update to prevent an intermediate campaign state, then re-run the complete Task 3 test set.

## Task 4: Verify stations, ballots and president finalization

**Files:** Modify `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`; modify `tests/ValidateCopiMineElectionRuntimeGuards.ps1` only for confirmed gaps.

**Interfaces:** Consumes an active coordinate, player UUID, candidate UUID and election id; produces a usable station, one immutable receipt and an atomic president/tax transition.

- [ ] Run `ValidateCopiMineElectionRpStationReplacement.ps1`, `ValidateCopiMineElectionStations.ps1`, `ValidateCopiMineElectionVoteUniqueness.ps1`, `ValidateCopiMineElectionVotingAtomicity.ps1` and `ValidateCopiMineElectionWinnerConfirmRequired.ps1`.
- [ ] Add red coverage for any concrete missing active-row, physical-block, candidate or current-stage revalidation.
- [ ] Keep votes and history; apply only the minimal lifecycle or transaction correction and re-run this task's suite.

## Task 5: Package and integrate verified changes

**Files:** Modify `copimine-election-core/CopiMineElectionCore.jar` only after source changes; modify this plan and the paired design specification.

**Interfaces:** Consumes final Java source and test suite; produces a matching JAR and an auditable commit in `main`.

- [ ] Re-run the entire fresh election validator suite, web contract and runtime guard validator.
- [ ] Compile with `build-plugin.ps1`, then verify `plugin.yml` and `CopiMineElectionCore.class` are present in the JAR.
- [ ] Run `git diff --check main...HEAD` and inspect the changed-file list; it must only contain ElectionCore, focused tests and audit documents.
- [ ] Commit the verified change, fast-forward `main` and push it to `origin`.
