# Website and Plugin Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit and repair the CopiMine website and plugin code so user-facing actions fail safely with clear feedback and the known election and lost-item workflows remain reliable.

**Architecture:** The website is a FastAPI backend serving a static JavaScript frontend. Tests assert backend/frontend contracts directly. Minecraft functionality is split into Gradle plugin modules, with Elections and Shop/LostItems carrying the highest current risk. Work proceeds from reproducible browser/API behaviour to a narrow code fix, then tests and builds.

**Tech Stack:** Python/FastAPI, static HTML/CSS/JavaScript, PostgreSQL-compatible persistence, Java/Gradle/Paper plugins, Node syntax checks, Playwright-backed in-app browser.

## Global Constraints

- Keep real payment integration disabled and do not alter provider credentials or billing flows.
- Do not touch production runtime, worlds, inventories, banks, or player accounts.
- Preserve manual election phase control and the approved election rules.
- Every production change needs a regression test or an existing test that demonstrates the fixed contract.

## Work Items

- [ ] 1. Establish a baseline and live-site audit

  **Files:** `admin-web/backend/main.py`, `admin-web/frontend/**`, `admin-web/scripts/**`

  - Run the existing backend, security, election, regression, and frontend-contract suites without modifications.
  - Start an isolated local FastAPI instance with non-production data and inspect public pages, the login boundary, responsive layout, navigation, and console/network failures in the in-app browser.
  - Inventory API calls made by election, commerce, recovery, and account screens; trace each to its backend handler.
  - Record reproducible failures before editing implementation code.

- [ ] 2. Repair website logic and feedback contracts

  **Files:** `admin-web/backend/main.py`, `admin-web/frontend/assets/js/**/*.js`, `admin-web/scripts/*test*.py`

  - Write or extend a focused failing regression test for each confirmed mismatch.
  - Implement the smallest backend and frontend change that makes validation, permissions, response shapes, busy states, and visible feedback agree.
  - Run the focused test immediately after each fix, then the relevant contract suite.

- [ ] 3. Repair verified visual and accessibility defects

  **Files:** `admin-web/frontend/**/*.html`, `admin-web/frontend/assets/css/**/*.css`, `admin-web/frontend/assets/js/**/*.js`

  - Use browser screenshots and DOM inspection to identify concrete layout, hierarchy, keyboard-focus, contrast, or narrow-screen defects.
  - Add a targeted browser- or static-regression assertion before changing the affected UI behaviour where practical.
  - Preserve existing content and cabinet navigation while making state and critical actions clearer.

- [ ] 4. Run website integration and release checks

  **Files:** `admin-web/scripts/**`, `admin-web/frontend/assets/js/**/*.js`

  - Re-run all website regression and security suites.
  - Run JavaScript syntax checks and inspect changed browser routes on the isolated server.
  - Confirm no production payment route or provider configuration changed.

- [ ] 5. Audit and harden plugins

  **Files:** `plugins/**/src/main/java/**/*.java`, `plugins/*/build.gradle.kts`, plugin tests where present

  - Build every plugin module and scan source for unsafe inventory, event, persistence, listener-registration, and null/exception paths.
  - Reproduce and test any high-confidence Elections or Shop/LostItems defect found during review before patching it.
  - Add narrowly scoped guards and regression coverage without changing game rules or production data.

- [ ] 6. Final verification and integration

  **Files:** all changed files

  - Review the diff for accidental configuration, credential, payment, or runtime-data changes.
  - Run all applicable website tests, JavaScript checks, plugin builds, and targeted browser tests.
  - Commit intentional changes, merge the verified branch into local `main`, and push the approved work to `origin/main`.
