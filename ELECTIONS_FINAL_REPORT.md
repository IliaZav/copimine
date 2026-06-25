# CopiMine Elections Final Report

## Найденные файлы

- `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java` - основной модуль выборов, заявок, бюллетеней, участков, президента и sidebar.
- `db/migrations/20260611_001_copimine_v4_postgres.sql` - базовые таблицы выборов.
- `db/migrations/20260612_006_elections_hardening.sql` - новые защитные индексы выборов.
- `tests/ValidateCopiMineElection*.ps1` и `tests/ValidateCopiMineElectionsSystem.ps1` - автоматические проверки выборной системы.

## Что исправлено

- Запись голоса через участок усилена транзакцией: проверка дубля, пометка бюллетеня, вставка голоса, обновление кандидата, удаление vote-session и audit выполняются одним блоком.
- Добавлены error-code маркеры для типовых ошибок выборов: stage, duplicate application, invalid/used ballot, duplicate vote, station, results, president, sidebar.
- Добавлены PostgreSQL-ограничения: один голос на игрока/выборы, один ballot_id в ledger, один кандидат на игрока/выборы, одна активная заявка, один активный бюллетень и один активный участок на координаты.
- Добавлен отдельный набор election validators из ТЗ.
- Добавлена ручная smoke-матрица `ELECTIONS_MANUAL_TEST_MATRIX.md`.

## Как теперь работает

- Админ открывает `/cmultra` -> `Выборы`.
- Цикл проходит этапы: подготовка, приём заявок, ревью, кандидаты, выдача бюллетеней, голосование, подсчёт, завершение, президент.
- Игрок подаёт заявку через официальную книгу.
- ЦИК/админ одобряет или отклоняет заявку.
- Одобренная заявка создаёт кандидата только один раз.
- Игрок получает личный бюллетень, выбирает кандидата, затем физически опускает запечатанный бюллетень в участок.
- Голос считается только после участка и только один раз.
- Победитель назначается президентом после финального подсчёта.
- Sidebar показывает этап и состояние выборов на русском.

## Проверки

- `ValidateCopiMineElection*.ps1` - passed.
- `ValidateCopiMineElectionsSystem.ps1` - passed.
- `copimine-admin-plugin/build-plugin.ps1` - passed, jar скопирован в `minecraft/server/plugins`.

## Live smoke

Перед переносом на боевой сервер пройди `ELECTIONS_MANUAL_TEST_MATRIX.md` и заполни колонку "Фактический результат".
