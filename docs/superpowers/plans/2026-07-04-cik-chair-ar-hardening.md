# CIK Chair And AR Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the polling-station chair workflow and tighten official AR behavior without replacing the current CopiMine architecture.

**Architecture:** ElectionCore remains the owner of polling stations, CIK chairs, seals, and chair removal requests. AdminPlus and EconomyCore keep the existing AR item flow, while AdminPlus enforces world/inventory destruction and placement rules around official AR ore items.

**Tech Stack:** Paper/Purpur plugins, Java, PostgreSQL, in-plugin GUI menus, existing build-plugin scripts.

---

### Task 1: ElectionCore chair workflow

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`

- [ ] Make station right-click behavior branch by role and current chair assignment.
- [ ] Keep self-claim flow for free stations, but open the public station card only when no chair exists.
- [ ] Extend chair GUI with station info, reissue seal, and removal request actions.
- [ ] Add a persistent table for chair removal requests plus admin review actions.
- [ ] Add a CIK admin section for pending chair removal requests and wire approve/reject flows.

### Task 2: Official AR rules

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- Modify: `D:/Desktop/Copimine/opt/copimine/copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`

- [ ] Normalize all official AR items to one stackable form with blue name and no lore.
- [ ] Keep storage containers allowed and functional blocks blocked.
- [ ] Allow placement through the current free-placement flow, but treat placed official AR as leaving official circulation.
- [ ] On placed-block break, drop only ordinary vanilla output and delete the official AR record path.
- [ ] Make manual AR drop destroy the official item instead of creating a transferable entity.

### Task 3: Verification and delivery

**Files:**
- Modify as needed: touched plugin sources and built jar outputs

- [ ] Rebuild `copimine-election-core`, `copimine-admin-plugin`, and `copimine-narcotics` if impacted transitively.
- [ ] Run one autonomous verification pass against the touched plugin flows.
- [ ] Fix any compile or validator failures from that pass.
- [ ] Commit and push the final integrated state to `main`.
