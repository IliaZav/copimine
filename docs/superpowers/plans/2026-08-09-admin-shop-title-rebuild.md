# Admin Shop Title Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить в GUI лавок администратора безопасную DB-only перезагрузку надписей над лавками.

**Architecture:** PostgreSQL остаётся источником истины. Асинхронный обработчик сначала загружает `artifact_shops`, затем на Bukkit main thread удаляет только PDC-owned `SHOP_TITLE_DISPLAY` из загруженных чанков и создаёт подписи только для включённых DB-лавок с существующим якорем. При загрузке чанка orphan-подписи очищаются тем же ownership-предикатом.

**Tech Stack:** Java 21, Paper/Purpur Bukkit API 1.21.1, PostgreSQL через существующий `PgPool`, PowerShell contract tests, SHA-256 JAR verification, systemd restart.

## Global Constraints

- Не удалять и не изменять мир, whitelist, БД-лавки или таблицы БД.
- Не удалять `TextDisplay` по видимому тексту; использовать только PDC `SHOP_TITLE_DISPLAY` и `visual_linked_id`.
- Не выполнять JDBC на Bukkit main thread.
- Перед визуальным удалением полностью успешно загрузить `artifact_shops`.
- На сервер загружать только новый `CopiMineArtifacts.jar`, сохранив backup старого JAR, затем выполнить короткий restart `copimine-minecraft`.

---

### Task 1: Write the failing regression contract

**Files:**
- Create: `tests/ValidateCopiMineArtifactShopTitleRebuild.ps1`
- Read: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`

- [ ] **Step 1: Write assertions for the GUI button, action route and DB-first rebuild.**

```powershell
$menu = [regex]::Match($source, '(?s)private void openAdminShops\(Player var1\)\s*\{.*?(?=\r?\n\s*public void openAdminShopHub)')
if (-not $menu.Success -or $menu.Value -notmatch 'setAction\(var3, var2, 47,.*?admin:shop:refresh_titles') { throw 'Missing title rebuild button.' }
if ($source -notmatch '"admin:shop:refresh_titles"' -or $source -notmatch 'rebuildAdminShopTitlesAsync\(var1\)') { throw 'Missing title rebuild action route.' }
$rebuild = [regex]::Match($source, '(?s)private void rebuildAdminShopTitlesAsync\(Player var1\)\s*\{.*?(?=\r?\n\s*private )')
if (-not $rebuild.Success -or $rebuild.Value -notmatch 'loadShopsFromPostgres\(') { throw 'Rebuild must load DB before cleanup.' }
if ($rebuild.Value -notmatch 'SHOP_TITLE_DISPLAY' -or $rebuild.Value -notmatch 'spawnShopTitleDisplay\(') { throw 'Rebuild must own cleanup and recreation.' }
```

- [ ] **Step 2: Run the contract and confirm the expected RED failure.**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineArtifactShopTitleRebuild.ps1
```

Expected: FAIL because the button, action and rebuild method do not exist yet.

### Task 2: Implement DB-first title rebuild and orphan cleanup

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`

- [ ] **Step 1: Add `rebuildAdminShopTitlesAsync(Player)` with DB-first ordering.**

The method must call `loadShopsFromPostgres()` inside `runAsync`, call `removeLoadedShopTitleDisplays()` and `createLoadedShopTitleDisplays()` only inside the success `runSync` callback, and report removed/created counts to the player. On SQL failure, log and leave current entities untouched.

- [ ] **Step 2: Add ownership-only loaded-entity cleanup.**

Scan loaded chunks and remove an entity only when it is a `TextDisplay` whose PDC `visual_entity_type` is `SHOP_TITLE_DISPLAY`. Do not inspect or match `display.getText()`.

- [ ] **Step 3: Add DB-only recreation.**

Iterate `shopsByLocation.values()` after the successful DB reload. For each enabled shop, require a loaded Bukkit world/chunk and a non-air anchor block before calling `spawnShopTitleDisplay`.

- [ ] **Step 4: Clean orphan titles on chunk load.**

Before spawning registered shop titles in `repairShopTitleDisplays(String worldName, int chunkX, int chunkZ)`, remove loaded `SHOP_TITLE_DISPLAY` entities whose linked id is not present in the fresh in-memory DB shop map for that world/chunk.

### Task 3: Wire the action into the admin shop GUI

**Files:**
- Modify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`

- [ ] **Step 1: Add the button in slot 47.**

Use `setAction` with action `admin:shop:refresh_titles`, Russian title `&bПерезагрузить надписи`, and a tooltip explaining that only DB shops are rebuilt.

- [ ] **Step 2: Route the action with an admin permission check.**

Call `rebuildAdminShopTitlesAsync(var1)` only for an authorized artifact administrator, then reopen the shop GUI after completion using the freshly loaded cache.

- [ ] **Step 3: Run the new contract and existing shop contracts.**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineArtifactShopTitleRebuild.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineArtifactShopCommandCleanup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineArtifactShopGuiDbRefresh.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineAdminShopsLayout.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineShopNamingAndTitleDisplay.ps1
```

Expected: all commands exit 0.

### Task 4: Build and perform the scoped live update

**Files:**
- Build: `copimine-artifacts/CopiMineArtifacts.jar`
- Deploy: `/opt/copimine/minecraft/server/plugins/CopiMineArtifacts.jar`

- [ ] **Step 1: Build the plugin and verify the focused contracts.**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File copimine-artifacts/build-plugin.ps1
git diff --check
```

- [ ] **Step 2: Compare local and live JAR SHA-256 before claiming deployment.**

Upload to a temporary path, verify the expected SHA-256, retain the old JAR at `/home/qwerty/copimine-upload/plugin-backups/CopiMineArtifacts.jar.pre-<stamp>`, replace only the plugin JAR, and restart only `copimine-minecraft`.

- [ ] **Step 3: Verify runtime invariants.**

Confirm `systemctl is-active copimine-minecraft`, the live JAR hash, Paper-remapped JAR existence, `level-name=CopiMine`, unchanged world region count and whitelist hash, and startup log lines for loading/enabling `CopiMineArtifacts`.

- [ ] **Step 4: Verify the target orphan is absent.**

Using RCON, temporarily load only chunk `[0,-4]`, query `TextDisplay` entities near `X=6,Y=70,Z=-63`, confirm no CopiMine `SHOP_TITLE_DISPLAY` remains, then remove the temporary force-load.
