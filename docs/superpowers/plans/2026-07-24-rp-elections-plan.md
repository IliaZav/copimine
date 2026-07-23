# Упрощённые RP-выборы CopiMine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Полностью заменить старую вкладку и игровое GUI выборов на проверяемый двухэтапный RP-процесс.

**Architecture:** PostgreSQL-контур сайта остаётся источником заявок и админских операций. `CopiMineElectionCore` предоставляет одно игровое GUI, защищённые голосовательные блоки и атомарную запись голосов; исторические legacy-таблицы не удаляются, но legacy actions не исполняются.

**Tech Stack:** Python/FastAPI, vanilla JavaScript/CSS, Java Paper plugin, PostgreSQL, PowerShell validation scripts.

---

### Task 1: Document the migration boundary

**Files:**
- Create: `docs/superpowers/specs/2026-07-24-rp-elections-design.md`
- Create: `docs/superpowers/plans/2026-07-24-rp-elections-plan.md`

- [x] Записать правила, интерфейс, ограничения и сценарии отказа. Документы являются русской инструкцией для владельца; идентификаторы API/БД остаются английскими.

### Task 2: Replace the core election hub

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`

- [ ] Заменить `openElectionRoot` и `openManagementMenu` на RP-меню без старых stage/ЦИК кнопок.
- [ ] Добавить действия `rp:application`, `rp:select`, `rp:debates`, `rp:voting`, `rp:block:create`, `rp:block:disable`, `rp:finish`, `rp:president:remove`.
- [ ] Создавать блок по `getTargetBlockExact(8)`, проверять активную RP-кампанию и повтор координат.
- [ ] В меню кандидатов использовать головы и хранить выбор в action/context; разрешать только 2–4 approved заявок.
- [ ] Перед голосованием проверять 2+ кандидатов и 1+ активный блок; при открытии задавать срок 24–72 часа.
- [ ] При завершении считать `votes`, требовать выбор UUID только при ничьей и создавать срок 7 дней.

### Task 3: Disable legacy election actions safely

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`

- [ ] Убрать из нового меню ссылки на CIK/chair/seal/ballot/second-round.
- [ ] В `handleMenuAction` и `isLegacyElectionAction` направлять устаревшие actions в новый hub с понятным сообщением и без SQL-изменения.
- [ ] В fallback AdminPlus показывать новый раздел и не открывать legacy-панели.

### Task 4: Harden the player voting path

**Files:**
- Modify: `copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`
- Modify: `admin-web/backend/main.py`
- Modify: `admin-web/frontend/assets/js/cabinet-runtime.js`

- [ ] Скрывать форму заявки после `DEBATES`; после `VOTING` показывать только статус.
- [ ] Повторно проверять stage/deadline/block/candidate/UUID внутри транзакции.
- [ ] Обеспечить идемпотентное повторное нажатие, понятные сообщения и запись аудита.
- [ ] Синхронизировать таймер голосования и результаты на сайте.

### Task 5: Automated verification

**Files:**
- Modify/Create: `tests/ValidateCopiMineElectionRpWorkflow.ps1`
- Modify: `ELECTIONS_MANUAL_TEST_MATRIX.md`

- [ ] Запустить Python compile, Java compile/tests, JS syntax checks и существующие validators.
- [ ] Выполнить статическую проверку отсутствия старых кнопок/action в новом hub.
- [ ] Прогнать сценарии отказа и проверить код возврата/сообщение.
- [ ] Исправить каждую найденную ошибку и повторить полный набор проверок.

### Task 6: Release and deployment

**Files:**
- Modify: `RELEASE_DEPLOYMENT.md` and release manifests as needed.

- [ ] Собрать плагины и сайт из текущего checkout.
- [ ] Проверить SHA-256 и содержимое архива.
- [ ] Закоммитить только относящиеся файлы, отправить в `main` согласно текущему remote.
- [ ] Передать архив штатным install script, перезапустить сервисы и проверить health endpoint/logs.
- [ ] Сверить на сервере версии core/AdminPlus/site и выполнить финальный smoke сценарий.
