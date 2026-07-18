# CopiMine Release Web And Plugin Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the web application, every custom plugin, item texture, and release artifact; repair confirmed defects and leave a reproducible, deployable release branch.

**Architecture:** Preserve the FastAPI plus static-module architecture and the existing Paper plugins. Treat the server as the source of truth for prices, roles, balances, deliveries, recipes, and payment state. Keep UI refinements in the release CSS layer and existing page modules, with the resource pack as the only source for item visuals.

**Tech Stack:** Python 3/FastAPI, vanilla HTML/CSS/ES modules, Java 21/Paper, PostgreSQL-compatible schema, PowerShell validators, Browser/IAB, Figma reference capture.

---

### Task 1: Establish a clean evidence baseline

**Files:**
- Review: `tests/RunCopiMineValidators.ps1`
- Review: `admin-web/scripts/backend_smoketest.py`
- Review: `admin-web/scripts/{regression_contract_test,sql_selftest,security_selftest}.py`

- [ ] Run the complete validator suite and record the exact pass/fail count.
- [ ] Run backend, SQL, regression, and security self-tests with the repository virtual environment.
- [ ] Compile every custom plugin with its existing build script and preserve only tracked release JAR updates.
- [ ] Check the working tree before each edit; never stage the pre-existing untracked CSS or screenshots.

### Task 2: Audit web contracts and CMS actions

**Files:**
- Review/modify only when reproduced: `admin-web/backend/main.py`
- Review/modify only when reproduced: `admin-web/backend/{commerce_catalog,deploy_runtime,plugin_registry,startup_checks}.py`
- Review/modify only when reproduced: `admin-web/frontend/assets/js/**`
- Add focused regression checks under `tests/` or `admin-web/scripts/` for each confirmed defect.

- [ ] Build a route-to-caller inventory for player actions, admin CMS actions, balance adjustments, recipe changes, YuKassa payment state, cart checkout, and delayed delivery.
- [ ] Test authorization, CSRF, request bounds, idempotency, transaction rollback, audit logging, and user-safe errors for every mutation family.
- [ ] Reproduce any confirmed missing check with a failing focused test before changing production code.
- [ ] Keep real-provider checks truthful: validate configuration and callback contracts locally; do not simulate a paid YuKassa transaction without provider credentials.

### Task 3: Audit plugins, items, textures, and encoding

**Files:**
- Review/modify only when reproduced: `copimine-*/src/**`
- Review/modify only when reproduced: `copimine-*/items.yml`
- Review/modify only when reproduced: `resourcepacks/{build-resourcepack.py,models_manifest.json,item_texture_sources.json}`
- Add focused validators under `tests/` for each repaired contract.

- [ ] Compile the six custom plugins and inspect their command, permission, database, scheduler, inventory, and exception paths.
- [ ] Run the texture/model/manifest validators and verify every sellable item has a game and web preview.
- [ ] Scan player-facing sources for broken UTF-8 and validate fixed resource-pack hashes.
- [ ] For every confirmed bug, first add a regression check, make the smallest source fix, rebuild the owning JAR, and rerun the focused check.

### Task 4: Redesign and validate the public site and cabinets

**Files:**
- Review/modify only confirmed owners under `admin-web/frontend/assets/css/release-ui.css`
- Review/modify only confirmed owners under `admin-web/frontend/assets/js/{public,player,admin,theme}/**`
- Review/modify only confirmed markup under `admin-web/frontend/{*.html,cabinet/*.html}`
- Create a Figma reference screen from the approved live direction without replacing source-controlled UI with a screenshot.

- [ ] Keep the existing two green themes, but remove duplicate visual noise, technical player-facing text, excess labels, and inert controls.
- [ ] Use semantic controls for all binary/choice/numeric actions. Add motion only for navigation, feedback, state transitions, and content reveal; respect reduced motion.
- [ ] Verify desktop and mobile public routes, cart, sign-in, player preview, and admin preview in Browser with console/error and interaction checks.
- [ ] Compare screenshots with the Figma reference and correct clipping, overlap, contrast, tap targets, menu state, asset loading, and keyboard focus.

### Task 5: Release verification and publishing

**Files:**
- Modify release manifests only if source or generated package evidence requires it.
- Save visual evidence outside committed source unless a repository validator requires it.

- [ ] Run the full validator suite, all self-tests, Python compilation, JavaScript syntax checks, plugin builds, and resource-pack reproducibility check after the final edit.
- [ ] Record external limits separately: actual Ubuntu/PostgreSQL/YooKassa live confirmation cannot be fabricated locally.
- [ ] Inspect the final diff, commit only owned changes, push `codex/release-overhaul`, and report concrete results by plugin and user-facing flow.
