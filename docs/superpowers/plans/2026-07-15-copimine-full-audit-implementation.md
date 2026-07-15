# CopiMine Full Audit And Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair and verify the CopiMine server, commerce, custom-item, election, complaint, web, and Ubuntu replacement surfaces under `opt/copimine` while preserving TAB behavior.

**Architecture:** Keep the current Paper plugins, FastAPI backend, PostgreSQL schema, static frontend, and resource-pack builder. Introduce a shared, deterministic runtime configuration contract and a machine-readable custom-item mapping contract, then make each runtime consume and validate those contracts. Keep AR and donation state separate, remove shop `ItemDisplay` overlays from the active runtime, and preserve existing route/API/auth/TAB contracts.

**Tech Stack:** Java 21/Paper 1.21.1, PostgreSQL/JDBC, Python 3/FastAPI, vanilla HTML/CSS/ES modules, PowerShell validators, Python resource-pack builder, Ubuntu systemd shell scripts.

---

## Working rules

- All product changes stay inside `D:\Desktop\Copimine\opt\copimine`.
- Existing dirty files belong to the user; inspect and preserve them. Do not reset or discard unrelated changes.
- TAB files under `minecraft/server/plugins/TAB` are read-only compatibility inputs. Do not edit their config, groups, textures, or font providers.
- Every behavior change follows red-green-refactor: write one failing validator, run it, implement the smallest fix, run the focused validator, then run the owning suite.
- Use one focused commit per completed task. Stage only that task's files.
- Do not copy secrets from the live server into the repository or output them in diagnostics.

## File map

| Area | Files and responsibility |
| --- | --- |
| Artifact runtime | `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`, `items.yml`, `config.yml` |
| Economy runtime | `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java` |
| Elections | `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`, `config.yml` |
| Admin/plugin commands | `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java` |
| Narcotics/world | `copimine-narcotics/src/**`, `copimine-world-core/src/**` |
| Web backend | `admin-web/backend/main.py`, `commerce_catalog.py`, `plugin_registry.py`, `startup_checks.py`, `envfile.py`, `discord_bot.py`, `download_manager.py`, `deploy_runtime.py` |
| Web frontend | `admin-web/frontend/index.html`, `shops.html`, cabinet pages, `assets/css/**`, `assets/js/**` |
| Resource pack | `resourcepacks/src/**`, `resourcepacks/models_manifest.json`, `build-resourcepack.py` |
| Deployment | `deploy/ubuntu/**`, `scripts/package_full_release.ps1`, `release/**` |
| Validators | `tests/ValidateCopiMine*.ps1`, `tests/manual/**` |

### Task 1: Capture a reproducible baseline and audit inventory

**Files:**
- Create: `tests/ValidateCopiMineFullAuditInventory.ps1`
- Create: `tests/manual/COPIMINE_FULL_AUDIT_RUNBOOK_RU.md`
- Modify: none

- [ ] **Step 1: Write the failing inventory validator**

Create a PowerShell validator that resolves the six first-party plugin source files, the web entrypoint, catalog, resource-pack manifest, TAB config, and deploy scripts. It must fail if a required source file is missing and print a count of Java/Python/HTML/CSS/PNG/JAR files.

```powershell
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$required = @(
  'copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java',
  'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java',
  'copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java',
  'copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java',
  'copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java',
  'copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java',
  'admin-web/backend/main.py',
  'copimine-artifacts/items.yml',
  'resourcepacks/models_manifest.json',
  'minecraft/server/plugins/TAB/config.yml',
  'deploy/ubuntu/copimine_full_replace.sh'
)
foreach ($relative in $required) {
  if (-not (Test-Path (Join-Path $Root $relative) -PathType Leaf)) {
    throw "Missing audit input: $relative"
  }
}
$extensions = @('.java','.py','.html','.css','.js','.png','.jar')
foreach ($extension in $extensions) {
  $count = @(Get-ChildItem $Root -Recurse -File -Filter "*$extension" -ErrorAction SilentlyContinue).Count
  Write-Output "$extension=$count"
}
```

- [ ] **Step 2: Run the validator and record the baseline**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineFullAuditInventory.ps1
git status --short
git log -5 --oneline
```

Expected: the validator reports the current source/artifact inventory; `git status` shows the pre-existing dirty worktree and no generated files from this task.

- [ ] **Step 3: Record the server collection runbook**

Document commands that collect, without secrets, the Ubuntu tree, service status, plugin list, recent logs, PostgreSQL readiness, database/schema names, and the exact `/opt/copimine` layout. The runbook must use `sed`/`awk` redaction for `.env` values and must never print `POSTGRES_PASSWORD`, tokens, cookies, or RCON passwords.

- [ ] **Step 4: Run the existing focused validators before changes**

Run:

```powershell
Get-ChildItem .\tests\ValidateCopiMine*.ps1 | ForEach-Object {
  & powershell -ExecutionPolicy Bypass -File $_.FullName
  if ($LASTEXITCODE -ne 0) { Write-Output "FAILED $($_.Name)" }
}
```

Save only summarized failures in the runbook. Do not call the result a complete security scan because the user cancelled Codex Security setup.

- [ ] **Step 5: Commit the baseline tooling**

```powershell
git add tests/ValidateCopiMineFullAuditInventory.ps1 tests/manual/COPIMINE_FULL_AUDIT_RUNBOOK_RU.md
git commit -m "test: capture full audit baseline"
```

### Task 2: Make PostgreSQL configuration resolution canonical

**Files:**
- Create: `admin-web/backend/runtime_config.py`
- Modify: `admin-web/backend/envfile.py`
- Modify: `admin-web/backend/main.py`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
- Modify: `deploy/ubuntu/copimine_full_replace.sh`
- Modify: `deploy/ubuntu/copimine_unpack_and_verify.sh`
- Create: `tests/ValidateCopiMinePostgresEnvContract.ps1`
- Create: `tests/ValidateCopiMineArtifactShopStartupDiagnostics.ps1`

- [ ] **Step 1: Write failing env-contract validators**

```powershell
$ErrorActionPreference = 'Stop'
$backend = Get-Content (Join-Path (Split-Path -Parent $PSScriptRoot) 'admin-web/backend/runtime_config.py') -Raw -ErrorAction SilentlyContinue
$artifacts = Get-Content (Join-Path (Split-Path -Parent $PSScriptRoot) 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java') -Raw
if ($artifacts -notmatch 'COPIMINE_ENV_FILE') { throw 'Artifacts must support COPIMINE_ENV_FILE.' }
if ($artifacts -notmatch 'selected.*env|env.*selected|safe.*diagnostic') { throw 'Artifacts must expose a non-secret env source diagnostic.' }
if (-not $backend) { throw 'runtime_config.py is required.' }
```

- [ ] **Step 2: Run the validators and confirm they fail**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMinePostgresEnvContract.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactShopStartupDiagnostics.ps1
```

Expected: FAIL because the canonical module and non-secret diagnostics do not exist yet.

- [ ] **Step 3: Implement the Python configuration contract**

`runtime_config.py` must load values with this precedence: explicit process environment, `COPIMINE_ENV_FILE`, `/opt/copimine/admin-web/.env`, then the local application `.env`. It must return a redacted `source_label`, never include password values, and parse only the supported PostgreSQL keys.

```python
from dataclasses import dataclass
from pathlib import Path
import os

@dataclass(frozen=True)
class PostgresRuntimeConfig:
    host: str
    port: int
    database: str
    user: str
    password: str
    schema: str
    source_label: str

    def safe_label(self) -> str:
        return f"postgresql://{self.host}:{self.port}/{self.database}?schema={self.schema} source={self.source_label}"

def resolve_postgres_config(app_root: Path) -> PostgresRuntimeConfig:
    candidates = []
    explicit = os.getenv('COPIMINE_ENV_FILE', '').strip()
    if explicit:
        candidates.append(Path(explicit))
    candidates.extend([Path('/opt/copimine/admin-web/.env'), app_root / '.env'])
    values = {}
    source = 'process environment'
    for candidate in candidates:
        if candidate.is_file():
            values.update(read_env_file(candidate))
            source = str(candidate)
            break
    values.update({key: value for key, value in os.environ.items() if key.startswith(('POSTGRES_', 'PG'))})
    password = values.get('POSTGRES_PASSWORD', values.get('PGPASSWORD', '')).strip()
    if not password:
        raise RuntimeError(f'POSTGRES_PASSWORD is missing; checked {source}')
    return PostgresRuntimeConfig(
        values.get('POSTGRES_HOST', values.get('PGHOST', '127.0.0.1')),
        int(values.get('POSTGRES_PORT', values.get('PGPORT', '5432'))),
        values.get('POSTGRES_DB', values.get('PGDATABASE', 'copimine')),
        values.get('POSTGRES_USER', values.get('PGUSER', 'copimine')),
        password,
        values.get('POSTGRES_SCHEMA', values.get('PGSCHEMA', 'copimine')),
        source,
    )
```

- [ ] **Step 4: Mirror the same precedence in Java**

Refactor `loadPgSettings()` and `envCandidates()` so `COPIMINE_ENV_FILE` is first, the deployed `/opt/copimine/admin-web/.env` is always considered, process environment wins over file values, and the thrown error includes only candidate paths and the missing key. Add `safeLabel()` logging after a successful connection and never log `password`.

- [ ] **Step 5: Add a startup readiness check without a fake fallback**

The plugin must run `SELECT current_database(), current_schema()` after pool creation, then create/upgrade the artifact tables. On failure it must disable itself with `ARTIFACTS_POSTGRES_UNAVAILABLE` and the safe connection label. It must not create a SQLite purchase database or allow a fake AR purchase.

- [ ] **Step 6: Wire deployment validation**

The Ubuntu scripts must check that `.env` exists with mode `0600`, `POSTGRES_PASSWORD` is present without printing it, `pg_isready` succeeds, and the target database is reachable before restarting Minecraft. The script must preserve the external PostgreSQL database during full replacement.

- [ ] **Step 7: Run focused checks and commit**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMinePostgresEnvContract.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactShopStartupDiagnostics.ps1
python -m compileall .\admin-web\backend
git add admin-web/backend/runtime_config.py admin-web/backend/envfile.py admin-web/backend/main.py copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java deploy/ubuntu tests/ValidateCopiMinePostgresEnvContract.ps1 tests/ValidateCopiMineArtifactShopStartupDiagnostics.ps1
git commit -m "fix: unify CopiMine postgres runtime configuration"
```

### Task 3: Build the authoritative custom-item mapping from the supplied archive

**Files:**
- Create: `resourcepacks/item_texture_sources.json`
- Modify: `copimine-artifacts/items.yml`
- Modify: `resourcepacks/models_manifest.json`
- Modify: `resourcepacks/build-resourcepack.py`
- Create: `tests/ValidateCopiMineCustomItemTextureMapping.ps1`
- Create: `tests/ValidateCopiMineCustomItemModelDataUnique.ps1`
- Create: `tests/ValidateCopiMineCustomItemArchiveCoverage.ps1`
- Add: `resourcepacks/src/assets/copimine/textures/item/artifacts/**`

- [ ] **Step 1: Add a failing archive/mapping validator**

The validator must list the 20 catalog ids, require an explicit source archive path or checked-in provenance entry, reject donation model data `0`, reject duplicate `(base_material, custom_model_data)` pairs, and reject manifest rows with missing model/texture files.

```powershell
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$catalog = Get-Content (Join-Path $root 'copimine-artifacts/items.yml') -Raw
$required = @('zmei_gorynych','smena_bez_perekura_pickaxe','lesnoy_bespredel_axe','kopatel_transhey_shovel','fermer_bez_sna_hoe','dezhurniy_argument_sword','vechniy_razgon_firework','treasurer_chestplate','copimine_miner_pickaxe','craftsman_hammer','batin_remen_sudnogo_dnya','nu_ty_i_nakopal_blyat_pickaxe','kosa_nalogovoy_inspekcii','kaska_prorab_huev','mne_pohuy_ya_v_tanke_vest','ne_segodnya_suka_shield','ya_esche_ne_vse_isportil_totem','pohuy_na_debaffy_amulet','vremya_platit_nalogi_clock','gde_moy_lut_blyat_compass')
foreach ($id in $required) { if ($catalog -notmatch [regex]::Escape($id)) { throw "Missing item $id" } }
$manifest = Get-Content (Join-Path $root 'resourcepacks/models_manifest.json') -Raw | ConvertFrom-Json
foreach ($row in $manifest.items) { if ([int]$row.custom_model_data -le 0) { throw "Invalid model data for $($row.id)" } }
```

- [ ] **Step 2: Run it and confirm the current failure**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemTextureMapping.ps1
```

Expected: FAIL because the donation entries currently have `custom-model-data: 0` and the manifest has no donation rows.

- [ ] **Step 3: Record explicit archive mapping**

Create `item_texture_sources.json` with one object per catalog id. Use exact archive-relative paths. Map `No_Donate` only to AR ids and `Donate` only to donation ids. Preserve the shield's base/nopattern choice and record the compass/clock frame directories. The file must contain `source_archive_sha256` and the source archive path, but no copied secret data.

- [ ] **Step 4: Assign stable model data**

Keep existing AR IDs `10001` through `10012`. Assign donation IDs in the `20001` through `20010` range, unique per base material, and record them in `items.yml`, the Java donation catalog parser output, and the manifest. Do not use the narcotics `810xxx` range or block marker `12002/14002` and `14004` ranges.

- [ ] **Step 5: Copy and normalize the source textures**

Copy the 16x16 PNGs into `assets/copimine/textures/item/artifacts/`. Normalize names to the catalog ids. Assemble the 64 compass frames into a vertical 16x1024 PNG and the 64 clock frames into a vertical 16x1024 PNG, with matching `.mcmeta` files using `frametime: 1` and `interpolate: false`. Preserve the two shield images as documented variants, not two catalog items.

- [ ] **Step 6: Generate models and vanilla overrides**

Extend `build-resourcepack.py` so the manifest validates every catalog row, generates one model per item, writes one `assets/minecraft/models/item/<material>.json` override per base material, and copies animation metadata. Existing TAB and narcotics assets remain in `REQUIRED_SOURCE_FILES` and are not renamed.

- [ ] **Step 7: Run mapping/build tests and commit**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemTextureMapping.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemModelDataUnique.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemArchiveCoverage.ps1
python .\resourcepacks\build-resourcepack.py
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineResourcePackBuildAndHash.ps1
git add copimine-artifacts/items.yml resourcepacks/item_texture_sources.json resourcepacks/models_manifest.json resourcepacks/build-resourcepack.py resourcepacks/src tests/ValidateCopiMineCustomItem*.ps1
git commit -m "feat: map supplied custom item textures"
```

### Task 4: Repair custom-item creation and interaction contracts

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Create: `tests/ValidateCopiMineCustomItemsJavaCatalogParity.ps1`
- Create: `tests/ValidateCopiMineDonationDefenseChance.ps1`
- Create: `tests/ValidateCopiMineArtifactEffectContracts.ps1`

- [ ] **Step 1: Write failing behavior validators**

Check that Java creation applies the catalog model data to AR and donation items, that every catalog effect appears in the correct event set, that defense effects honor configured chance/cooldown, and that all donation base materials match the mapping.

- [ ] **Step 2: Run focused validators**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemsJavaCatalogParity.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineDonationDefenseChance.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactEffectContracts.ps1
```

Expected: fail for donation model data and for defense effects that currently skip `proc-chance`.

- [ ] **Step 3: Centralize item model-data application**

Make `createOfficialItem()` and donation runtime synthesis call one helper that sets the model data, item id, unique id, owner UUID, source, bound, reclaimable, and item type PDC values. Reject an enabled custom-texture item with missing or zero model data during catalog load.

- [ ] **Step 4: Make defense effects honor the contract**

Before `PRORAB_HELMET`, `TANK_VEST`, and `NOT_TODAY_SHIELD` apply, call the same chance helper used by combat effects, then apply the configured minimum cooldown. Keep the existing damage bounds and owner checks. Add tests for chance zero, chance one, cooldown, and a valid trigger.

- [ ] **Step 5: Close effect safety gaps**

Protect temporary cobweb placement from protected blocks, non-air blocks, unloaded worlds, and concurrent cleanup. Make tree-chain and farm harvest operations respect the existing limits and never operate on another player's protected area. Make the hammer's lore and actual Haste duration agree.

#### Tax-clock contract

- Implement `vremya_platit_nalogi_clock` as a persistent three-calendar-month exemption using an absolute UTC `expires_at` value in ElectionCore-backed PostgreSQL state.
- Make activation idempotent for the same player and persistent artifact instance while the exemption is active; artifact cooldown clicks must not extend the expiration indefinitely.
- Make tax calculation, tax-office display, and payment eligibility checks honor the active exemption server-side.
- Add an explicit `TAX_CLOCK_EXEMPTION` source/row to the president's tax-receipts query and GUI. It shows zero AR, the player name, and expiration while active, and is never mislabeled as a normal tax payment.
- Return the existing expiration on repeated activation and fail closed with a clear message when ElectionCore or PostgreSQL persistence is unavailable.
- Cover expiration arithmetic, idempotent reactivation, tax-due suppression, GUI row projection, and restart/reload persistence with tests before implementation.

- [ ] **Step 6: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineCustomItemsJavaCatalogParity.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineDonationDefenseChance.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactEffectContracts.ps1
git add copimine-artifacts/src tests/ValidateCopiMineCustomItemsJavaCatalogParity.ps1 tests/ValidateCopiMineDonationDefenseChance.ps1 tests/ValidateCopiMineArtifactEffectContracts.ps1
git commit -m "fix: enforce custom item runtime contracts"
```

### Task 5: Remove protected-block visual strips and stale overlay state

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `copimine-artifacts/config.yml`
- Create: `tests/ValidateCopiMineArtifactNoShopItemDisplays.ps1`
- Create: `tests/ValidateCopiMineArtifactVisualCleanup.ps1`

- [ ] **Step 1: Write failing source validators**

Assert that disabled `custom-block-visuals` cannot call spawn/repair from enable or chunk-load paths, and that cleanup removes only entities with the plugin-owned PDC marker. Assert that the delete path marks the database row inactive.

- [ ] **Step 2: Run and confirm the current failure**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactNoShopItemDisplays.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineArtifactVisualCleanup.ps1
```

- [ ] **Step 3: Implement one-way block-only behavior**

Keep `custom-block-visuals.enabled: false`, remove the active repair/spawn calls from startup and chunk-load paths when disabled, keep `onShopVisualInteract` inert for disabled mode, and run PDC-owned cleanup on startup and disable. Mark stale `protected_block_visuals` rows inactive without deleting unrelated rows.

- [ ] **Step 4: Verify delete/reload/restart paths**

Run the focused validators and the existing protected-block tests. Review the final Java diff for any unbounded `getNearbyEntities` cleanup or entity removal without the CopiMine PDC marker.

- [ ] **Step 5: Commit**

```powershell
git add copimine-artifacts/src copimine-artifacts/config.yml tests/ValidateCopiMineArtifactNoShopItemDisplays.ps1 tests/ValidateCopiMineArtifactVisualCleanup.ps1
git commit -m "fix: remove artifact shop visual overlays"
```

### Task 6: Audit and repair economy transaction boundaries

**Files:**
- Modify: `copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Modify: `admin-web/backend/main.py`
- Create: `tests/ValidateCopiMineEconomyIdempotency.ps1`
- Create: `tests/ValidateCopiMineDonationArSeparation.ps1`
- Create: `tests/ValidateCopiMineEconomySqlSafety.ps1`

- [ ] **Step 1: Write failing transaction validators**

Require unique idempotency keys for mark-paid, donation credit, purchase intent, claim, reclaim, and AR purchase. Require parameterized SQL and explicit status transitions. Reject any endpoint that can credit AR from donation input or donation from AR input.

- [ ] **Step 2: Run current validators**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineEconomyIdempotency.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineDonationArSeparation.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineEconomySqlSafety.ps1
```

- [ ] **Step 3: Fix atomic transitions**

Use row locks or equivalent conditional updates for payment sessions, balances, purchases, claims, and artifact instances. Return the already-applied result for repeated idempotency keys. Roll back balance changes if physical delivery or claim creation fails.

- [ ] **Step 4: Bound admin test flows**

Keep admin role/CSRF/confirmation/rate-limit checks on top-up, mark-paid, test purchase, and registry writes. Ensure error responses use stable codes and never include SQL text or stack traces.

- [ ] **Step 5: Run economy self-tests and commit**

```powershell
python .\admin-web\scripts\sql_selftest.py
python .\admin-web\scripts\security_selftest.py
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineEconomyIdempotency.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineDonationArSeparation.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineEconomySqlSafety.ps1
git add copimine-economy-core/src copimine-artifacts/src admin-web/backend/main.py tests/ValidateCopiMineEconomy*.ps1 tests/ValidateCopiMineDonationArSeparation.ps1
git commit -m "fix: harden economy transaction boundaries"
```

### Task 7: Audit elections and complaint/bug-report flows

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
- Modify: `admin-web/backend/main.py`
- Modify: `admin-web/frontend/assets/js/player/account-pages.js`
- Create: `tests/ValidateCopiMineElectionStateTransitions.ps1`
- Create: `tests/ValidateCopiMineElectionPrivacy.ps1`
- Create: `tests/ValidateCopiMineComplaintCommands.ps1`
- Create: `tests/ValidateCopiMineBugReportPlayerMessage.ps1`

- [ ] **Step 1: Write failing validators**

Validate election state transitions, authorization, secrecy, and generic admin DB write protection. Validate `/report`, `/appeal`, `/reporta`, `/cmultra bugreport <code>`, rate/duplicate behavior, and that the player sees a stable report code without exception class or internal details.

- [ ] **Step 2: Run the validators**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineElectionStateTransitions.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineElectionPrivacy.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineComplaintCommands.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineBugReportPlayerMessage.ps1
```

- [ ] **Step 3: Repair election boundaries**

Trace every command and web endpoint that mutates election data. Require the election service/GUI boundary, preserve sealed ballot data, prevent generic DB editor writes, and make emergency transitions auditable and idempotent.

- [ ] **Step 4: Repair report flow**

Keep `/reporta` as the explicit submission command, expire one pending bug context after a bounded TTL, make duplicate submission return the original report result, clip player notes, and separate player-safe messages from staff/backend diagnostic metadata. Use parameterized SQL and a safe event payload builder instead of concatenated JSON where the existing Java HTTP client permits it.

- [ ] **Step 5: Verify player/admin messages**

Test both a normal complaint and an exception-triggered bug report. The player message must contain only the stable code/ID and next action; staff gets source/action/location; backend gets structured metadata with secrets and raw tokens excluded from public output.

- [ ] **Step 6: Commit**

```powershell
git add copimine-election-core/src copimine-admin-plugin/src admin-web/backend/main.py admin-web/frontend/assets/js/player/account-pages.js tests/ValidateCopiMineElection*.ps1 tests/ValidateCopiMineComplaintCommands.ps1 tests/ValidateCopiMineBugReportPlayerMessage.ps1
git commit -m "fix: harden elections and player reports"
```

Include active tax-clock exemption rows in the president's receipts projection without mixing them into actual tax-payment totals.

### Task 8: Complete the manual security audit and repair web backend defects

**Files:**
- Modify: `admin-web/backend/main.py`
- Modify: `admin-web/backend/*.py` for confirmed defects only
- Create: `tests/ValidateCopiMineBackendAuditSurface.ps1`
- Create: `tests/ValidateCopiMineBackendSafeErrors.ps1`
- Create: `tests/ValidateCopiMineBackendPathAndUploadSafety.ps1`

- [ ] **Step 1: Build the backend surface inventory**

List every route and classify it as public, player-authenticated, admin, owner, plugin-key, or internal. Trace auth cookie/refresh rotation, CSRF, role checks, rate limits, input parsing, SQL calls, filesystem access, subprocess/systemd/RCON access, Discord requests, upload/download paths, and error handlers.

- [ ] **Step 2: Write and run failing backend validators**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineBackendAuditSurface.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineBackendSafeErrors.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineBackendPathAndUploadSafety.ps1
python .\admin-web\scripts\security_selftest.py
```

- [ ] **Step 3: Repair only proven findings**

Keep cookie-only auth and refresh rotation, enforce CSRF on every state change, keep owner-only keys protected, canonicalize and bound file paths, reject open redirects, cap SQL/dashboard limits, redact exceptions, and make plugin registry writes allowlist/schema/backup/audit/reload gated. Preserve the existing API response shapes unless a current response is demonstrably unsafe or unusable.

- [ ] **Step 4: Verify backend imports and route smoke tests**

```powershell
python -m compileall .\admin-web\backend
python .\admin-web\scripts\backend_smoketest.py
python .\admin-web\scripts\sql_selftest.py
python .\admin-web\scripts\security_selftest.py
```

- [ ] **Step 5: Commit backend repairs**

```powershell
git add admin-web/backend admin-web/scripts tests/ValidateCopiMineBackend*.ps1
git commit -m "fix: harden admin backend boundaries"
```

### Task 9: Rewrite web styles and finish shop surfaces

**Files:**
- Modify: `admin-web/frontend/index.html`
- Modify: `admin-web/frontend/shops.html`
- Modify: `admin-web/frontend/cabinet/dashboard.html`
- Modify: `admin-web/frontend/assets/css/tokens.css`
- Modify: `admin-web/frontend/assets/css/base.css`
- Modify: `admin-web/frontend/assets/css/layout.css`
- Modify: `admin-web/frontend/assets/css/shop.css`
- Modify: `admin-web/frontend/assets/css/admin.css`
- Modify: `admin-web/frontend/assets/css/responsive.css`
- Modify: `admin-web/frontend/assets/js/public/homepage.js`
- Modify: `admin-web/frontend/assets/js/admin/commerce-pages.js`
- Modify: `admin-web/frontend/assets/js/cabinet-runtime.js`
- Create: `tests/ValidateCopiMineAdminHubShopsTab.ps1`
- Create: `tests/ValidateCopiMineWebsiteFullAuditMarkup.ps1`

- [ ] **Step 1: Write failing markup/style validators**

Require a dedicated home/admin shop entry, AR/donation labels, live catalog status, the shared color tokens, visible focus styles, mobile breakpoints, reduced-motion media query, and no changes to TAB files.

- [ ] **Step 2: Run the validators before rewriting**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineAdminHubShopsTab.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineWebsiteFullAuditMarkup.ps1
```

- [ ] **Step 3: Implement the shell and shop entry**

Use the approved control-room tokens, compact navigation, server-pulse rail, AR/donation split, real Minecraft item icons, robust empty/error/loading states, keyboard focus, mobile layout, and reduced-motion behavior. Keep existing element IDs used by the JS modules and preserve `/shops.html` and cabinet hash routes.

- [ ] **Step 4: Remove broken encoding and placeholder output**

Convert edited HTML/CSS/JS files to UTF-8, verify Russian text round-trips, remove player-facing mojibake and diagnostic placeholders, and keep technical error codes only where they are actionable.

- [ ] **Step 5: Run static web validators and commit**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineAdminHubShopsTab.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineWebsiteFullAuditMarkup.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineWebsiteNoUnsafeInnerHtml.ps1
git add admin-web/frontend tests/ValidateCopiMineAdminHubShopsTab.ps1 tests/ValidateCopiMineWebsiteFullAuditMarkup.ps1
git commit -m "feat: rebuild CopiMine commerce interface"
```

### Task 10: Audit all remaining plugin stubs and cross-plugin contracts

**Files:**
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientBridgePayloads.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientCapabilityService.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientCapabilityState.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientVisualCommand.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientVisualEffectService.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/config/NarcoticsConfigService.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/model/ConfiguredEffect.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/model/NarcoticDefinition.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/recipe/IngredientEntry.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/recipe/NarcoticsRecipeService.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/resourcepack/NarcoticsResourcePackAudit.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/use/OverdoseService.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/util/BlockKey.java`
- Modify: `copimine-narcotics/src/me/copimine/visualruntime/VisualRuntimeService.java`
- Modify: `copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java`
- Create: `tests/ValidateCopiMinePluginContractAudit.ps1`
- Create: `tests/ValidateCopiMineNoPlayerFacingStubText.ps1`

- [ ] **Step 1: Inventory stubs and dangerous paths**

Search first-party sources for `TODO`, `FIXME`, `stub`, `placeholder`, empty handlers, swallowed exceptions, arbitrary file/process paths, unbounded world scans, main-thread SQL, raw SQL concatenation, unsafe JSON concatenation, and player-facing stack/error text. Record each result as fixed, intentionally unsupported with a safe message, or covered by an existing contract.

- [ ] **Step 2: Write and run validators**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMinePluginContractAudit.ps1
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineNoPlayerFacingStubText.ps1
```

- [ ] **Step 3: Repair confirmed gaps**

Preserve plugin ownership boundaries: Economy owns balances, Election owns election state, Artifacts owns item instances, Narcotics owns narcotics effects, World owns world limits, and AdminPlus owns admin/report orchestration. Add bounded async work, explicit error codes, and audit events where missing.

- [ ] **Step 4: Commit the cross-plugin fixes**

```powershell
git add copimine-narcotics/src copimine-world-core/src copimine-admin-plugin/src copimine-election-core/src tests/ValidateCopiMinePluginContractAudit.ps1 tests/ValidateCopiMineNoPlayerFacingStubText.ps1
git commit -m "fix: close first-party plugin contract gaps"
```

### Task 11: Rebuild jars, resource pack, release manifests, and Ubuntu replacement

**Files:**
- Modify: plugin build outputs under `copimine-*/build/**` and the distributable JARs
- Modify: `resourcepacks/build/**`
- Modify: `deploy/installer_manifest.json`
- Modify: `deploy/release_manifest.json`
- Modify: `scripts/package_full_release.ps1`
- Modify: `release/**` only for the final generated bundle
- Create: `tests/ValidateCopiMineReleaseArtifactsFresh.ps1`
- Create: `tests/manual/COPIMINE_FULL_REPLACE_SMOKE_RU.md`

- [ ] **Step 1: Run every focused source validator**

```powershell
Get-ChildItem .\tests\ValidateCopiMine*.ps1 | Sort-Object Name | ForEach-Object {
  & powershell -ExecutionPolicy Bypass -File $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "Validator failed: $($_.Name)" }
}
```

- [ ] **Step 2: Build each Java plugin with the repository toolchain**

Use the existing build scripts and local Gradle/Maven distributions discovered in the repository. For each jar, verify `plugin.yml`, source version, required dependencies, and SHA-256. Do not rebuild TAB or change its files.

- [ ] **Step 3: Build and inspect the resource pack**

```powershell
python .\resourcepacks\build-resourcepack.py
tar.exe -tf .\resourcepacks\build\CopiMineResourcePack.zip | Select-String 'artifacts|narcotics|tab|models/minecraft/item'
```

- [ ] **Step 4: Generate fresh manifests and checksums**

Run the existing package script, then verify every manifest hash matches the actual file and `server.properties` points to the generated resource-pack SHA-1. Reject a dirty source marker unless the manifest explicitly records the final generated revision.

- [ ] **Step 5: Add full-replacement dry-run checks**

The Ubuntu script must validate archive contents, preserve `/opt/copimine/db` and external `.env`, stop/restart services in order, install files atomically, restore ownership/modes, run `pg_isready`, run backend startup checks, and call a health endpoint. The Windows helper must create the archive from `opt/copimine` and print the exact `scp`/`ssh` commands without embedding credentials.

- [ ] **Step 6: Commit release tooling**

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineReleaseArtifactsFresh.ps1
git add deploy scripts resourcepacks/build resourcepacks/build-resourcepack.py tests/ValidateCopiMineReleaseArtifactsFresh.ps1 tests/manual/COPIMINE_FULL_REPLACE_SMOKE_RU.md
git commit -m "build: package verified CopiMine replacement"
```

### Task 12: Verification, review, and handoff

**Files:**
- Modify: `tests/manual/COPIMINE_FULL_REPLACE_SMOKE_RU.md` with actual results
- Create: `docs/reports/2026-07-15-copimine-full-audit-report.md`
- Modify: no TAB source files

- [ ] **Step 1: Run the full available verification set**

```powershell
python -m compileall .\admin-web\backend
python .\admin-web\scripts\backend_smoketest.py
python .\admin-web\scripts\sql_selftest.py
python .\admin-web\scripts\security_selftest.py
Get-ChildItem .\tests\ValidateCopiMine*.ps1 | Sort-Object Name | ForEach-Object {
  & powershell -ExecutionPolicy Bypass -File $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "Validator failed: $($_.Name)" }
}
```

- [ ] **Step 2: Inspect the final diff and TAB preservation**

```powershell
git diff --check
git diff --name-only HEAD~12..HEAD | Select-String 'minecraft/server/plugins/TAB' | ForEach-Object { throw "TAB changed: $_" }
git status --short
```

- [ ] **Step 3: Request code review**

Dispatch a reviewer with the final base/head SHAs and ask it to prioritize database initialization, economy/election authorization, custom-item identity, report privacy, path traversal, and TAB regressions. Fix all critical/important findings and rerun the affected validators.

- [ ] **Step 4: Write the audit report**

The report must list inspected surfaces, confirmed fixes, tests and commands with exit status, skipped runtime checks requiring the user's server output, residual risk, and the exact Windows output-collection and Ubuntu full-replacement commands.

- [ ] **Step 5: Final completion gate**

Only claim a bug is fixed after the original symptom test or the strongest available reproduction passes. If PostgreSQL live validation cannot run locally, report it as unknown until the user supplies the server command output; do not call the live shop end-to-end verified.
