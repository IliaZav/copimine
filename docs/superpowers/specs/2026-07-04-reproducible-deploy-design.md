# CopiMine Reproducible Deploy Design

**Goal**

Убрать ручные правки при развёртывании CopiMine на новый Ubuntu-сервер и сделать единый воспроизводимый контур установки, обновления, проверки и отката для сайта, Minecraft, Discord-бота, PostgreSQL, nginx, resource pack и modpack.

**Constraints**

- Текущая архитектура проекта сохраняется.
- Источником истины остаётся репозиторий, а не ручные патчи на сервере.
- Все секреты должны создаваться и читаться из отдельного каталога.
- Миры можно вайпать, но нельзя задевать сайт, БД, whitelist, права и плагины.
- Сборка и проверка должны запускаться локально из проекта.

## Chosen Design

### 1. Deploy framework

Вместо разрозненных скриптов проект получает единый deploy-контур:

- `deploy/ubuntu/install.sh`
- `deploy/ubuntu/update.sh`
- `deploy/ubuntu/rollback.sh`
- `deploy/ubuntu/backup.sh`
- `deploy/ubuntu/verify.sh`
- `deploy/windows/install.ps1`
- `deploy/windows/update.ps1`
- `deploy/windows/rollback.ps1`
- `deploy/windows/backup.ps1`
- `deploy/windows/verify.ps1`

Обе платформы используют один общий shell-модуль с инвариантами для путей, хешей, секретов, world wipe, nginx/systemd и release metadata. Windows-скрипты остаются обёртками для локальной упаковки и подготовки релиза; Ubuntu-скрипты выполняют реальную установку на сервере.

### 2. Runtime manifest and secrets

Проект будет генерировать и читать единый runtime state:

- секреты хранятся в `/opt/copimine-secrets`
- release metadata хранится в `deploy/release_manifest.json`
- runtime metadata дополняется commit/hash/status данными

Это даёт единый источник данных для verify-скриптов, `/api/runtime`, modpack/resourcepack ссылок и health-check.

### 3. Admin Web hardening

`admin-web` остаётся на FastAPI, но получает:

- startup self-check модуль
- download manager для `downloads/` и `resourcepacks/`
- расширенный `/api/health`
- новый `/api/runtime`
- явную диагностику env/dirs/postgres/systemd/downloads/resourcepack/modpack/discord
- стабильную CSRF/origin/cookie модель без ручных серверных патчей

### 4. Packaging and verification

Resource pack и modpack продолжают собираться существующими builder-скриптами, но deploy-слой будет:

- автоматически пересчитывать SHA1/SHA256
- синхронизировать `server.properties`
- синхронизировать manifests
- валидировать nginx/systemd/ports/download URLs

### 5. World wipe

Вайп миров будет вынесен в повторно используемую deploy-функцию, которая:

- останавливает Minecraft
- удаляет только `world`, `world_nether`, `world_the_end`
- переписывает `level-seed`
- не трогает БД, whitelist, ops, плагины и сайт

## File Map

- `deploy/shared/common.sh`: общие shell-функции deploy-контура
- `deploy/ubuntu/*.sh`: серверные install/update/rollback/backup/verify команды
- `deploy/windows/*.ps1`: локальная подготовка релиза и проверка
- `admin-web/backend/deploy_runtime.py`: env/runtime/download/resourcepack/modpack helpers
- `admin-web/backend/startup_checks.py`: self-check и health/runtime snapshot
- `admin-web/backend/download_manager.py`: безопасная отдача файлов и metadata
- `admin-web/backend/main.py`: интеграция middleware, startup, endpoints
- `scripts/setup_clean_deploy_ubuntu.sh`: будет сведён к thin wrapper или перенаправлен на новый install flow
- `scripts/final_copimine_check.sh`: будет перенаправлен на новый verify flow
- `scripts/package_full_release.ps1`: будет синхронизирован с новым deploy manifest

## Risks And Mitigations

- `main.py` очень большой. Новую логику нужно выносить в отдельные backend-модули, а не продолжать раздувать файл.
- На сервере уже есть исторические service/nginx сценарии. Новый deploy должен валидировать конфиги до применения.
- Старые smoke-скрипты могут ожидать старые имена файлов. Их нужно обновить синхронно с новым deploy-контуром.

## Success Criteria

- Чистый Ubuntu deploy поднимает PostgreSQL, admin-web, nginx, Discord bot и Minecraft без ручных патчей.
- `/api/health` и `/api/runtime` дают фактическое состояние зависимостей.
- `deploy/ubuntu/verify.sh` находит сломанные env, nginx, DB, downloads и hashes.
- Resource pack и modpack URL/SHA больше не расходятся.
- Вайп миров выполняется отдельной штатной командой и использует сид `-1861153001556076901`.
