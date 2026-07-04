# Reproducible Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать CopiMine воспроизводимо устанавливаемым и обновляемым на Ubuntu без ручных исправлений файлов после деплоя.

**Architecture:** Новый deploy-контур будет строиться вокруг общих shell-функций и runtime metadata, а backend `admin-web` получит отдельные модули startup/download/runtime вместо дальнейшего усложнения `main.py`.

**Tech Stack:** Bash, PowerShell, FastAPI, Python 3.12, PostgreSQL, systemd, nginx.

---

### Task 1: Deploy file layout and shared shell helpers

**Files:**
- Create: `deploy/shared/common.sh`
- Create: `deploy/ubuntu/install.sh`
- Create: `deploy/ubuntu/update.sh`
- Create: `deploy/ubuntu/rollback.sh`
- Create: `deploy/ubuntu/backup.sh`
- Create: `deploy/ubuntu/verify.sh`
- Modify: `scripts/setup_clean_deploy_ubuntu.sh`
- Modify: `scripts/final_copimine_check.sh`

- [ ] Add a shared shell library for path resolution, logging, service control, hash sync, secret loading, nginx validation, DB bootstrap and world wipe.
- [ ] Rebuild Ubuntu entrypoints as thin commands over the shared library instead of bespoke one-off logic.
- [ ] Keep backward compatibility by turning old `scripts/setup_clean_deploy_ubuntu.sh` and `scripts/final_copimine_check.sh` into wrappers around the new deploy flow.

### Task 2: Windows wrappers and release packaging

**Files:**
- Create: `deploy/windows/install.ps1`
- Create: `deploy/windows/update.ps1`
- Create: `deploy/windows/rollback.ps1`
- Create: `deploy/windows/backup.ps1`
- Create: `deploy/windows/verify.ps1`
- Modify: `scripts/package_full_release.ps1`

- [ ] Add Windows wrappers for packaging, metadata sync and local release validation.
- [ ] Make release packaging emit data that the Ubuntu install/update scripts can consume directly.
- [ ] Preserve the existing full-release archive flow while moving naming and metadata to the new deploy contract.

### Task 3: Admin Web runtime modules

**Files:**
- Create: `admin-web/backend/deploy_runtime.py`
- Create: `admin-web/backend/startup_checks.py`
- Create: `admin-web/backend/download_manager.py`
- Modify: `admin-web/backend/main.py`

- [ ] Extract runtime path, manifest and hash helpers from `main.py` into `deploy_runtime.py`.
- [ ] Implement startup self-checks covering env, PostgreSQL, directories, downloads, resourcepack, modpack, Discord and Minecraft assets.
- [ ] Implement a download manager that serves files with MIME, size, SHA1 and SHA256 metadata and raises explicit HTTP errors for missing artifacts.

### Task 4: Health, runtime and CSRF hardening

**Files:**
- Modify: `admin-web/backend/main.py`
- Modify: `admin-web/.env.example`

- [ ] Replace the trivial `/api/health` with a dependency-aware health snapshot.
- [ ] Add `/api/runtime` with build, path, commit and artifact state.
- [ ] Tighten CSRF/origin logic so proxy-aware origin evaluation and token issuance are consistent after clean deploy.

### Task 5: nginx, systemd, hashes and world wipe

**Files:**
- Modify: `admin-web/deploy/copimine-admin.service`
- Modify: `admin-web/deploy/copimine-discord-bot.service`
- Modify: `admin-web/deploy/copimine-minecraft.service`
- Modify: `admin-web/deploy/nginx-copimine-admin-18080.conf`
- Modify: `admin-web/deploy/nginx-copimine-admin-https.conf`
- Modify: `resourcepacks/build-resourcepack.py`
- Modify: `scripts/thirdparty/build_modpack.sh`
- Modify: `scripts/thirdparty/build_modpack.ps1`
- Modify: `scripts/wipe_game_worlds_keep_state.sh`

- [ ] Make service and nginx templates compatible with generated env/runtime values.
- [ ] Ensure resourcepack and modpack builders update manifests and hashes consumed by backend and deploy scripts.
- [ ] Convert world wipe into a reusable deploy operation with the required seed.

### Task 6: Verification and release contract

**Files:**
- Modify: `tests/ValidateCopiMineUbuntuReleaseStructure.ps1`
- Modify: `tests/ValidateCopiMineUbuntuLiveSmoke.ps1`
- Add or modify any deploy-related validation files required by the new layout

- [ ] Update validation scripts to assert the new deploy layout and runtime behavior.
- [ ] Run builds, run validations, fix failures and only then commit/push.
