# CopiMine: финальный отчет аудита всех плагинов

Дата: 2026-06-12

## Итог

Проект переведен в режим совместной работы `CopiMineUltimateAdminPlus`, `CopiMineArtifacts`, `CopiMineNarcotics` и `AuthEffects`. Главный плагин остается владельцем PostgreSQL, AR, PIN и bridge; Artifacts и Narcotics используют официальный bridge и не ведут собственную экономику.

## Что исправлено

- `AuthEffects` переписан на корректные nLogin events: эффект до авторизации, очистка после `/login` и `/register`, очистка сессии на выходе, отмена задач на disable.
- `CopiMineNarcotics` усилен против GUI-краев: drag в меню отменяется, состояние покупки/PIN/cooldown очищается на выходе и disable, перед списанием AR повторно проверяется свободный слот.
- `CopiMineNarcotics` получил отдельные permission-узлы `copimine.narcotics.reload` и `copimine.narcotics.confiscate` без поломки старого `copimine.narcotics.admin`.
- `CopiMineArtifacts` больше не показывает игроку сырые bridge/SQL ошибки при покупке и ремонте: игрок видит безопасный код, подробности уходят в лог.
- `CopiMineArtifacts` логирует ошибки reload/shop-admin/history без раскрытия внутренних сообщений игроку.
- `CopiMineUltimateAdminPlus` получил совместимость с новыми permission aliases: `copimine.elections.admin`, `copimine.elections.curator`, `copimine.bank.admin`, `copimine.diagnostics`.

## Проверки и валидаторы

Добавлены общие валидаторы:

- `tests/ValidateCopiMineAllPluginsAudit.ps1`
- `tests/ValidateCopiMinePluginYml.ps1`
- `tests/ValidateCopiMineCommandsPermissions.ps1`
- `tests/ValidateCopiMineGuiSessions.ps1`
- `tests/ValidateCopiMineNoRawSqlErrorsToPlayers.ps1`
- `tests/ValidateCopiMinePostgresSchemaSync.ps1`
- `tests/ValidateCopiMineNoMainThreadSql.ps1`
- `tests/ValidateCopiMineBridgeContracts.ps1`
- `tests/ValidateCopiMineNoSecretsInLogs.ps1`
- `tests/ValidateCopiMineLocalizationRu.ps1`
- `tests/ValidateCopiMineNoEncodingCorruption.ps1`
- `tests/ValidateCopiMineNoDoubleClickDuplication.ps1`
- `tests/ValidateCopiMineSchedulersCleanup.ps1`

Обновлен существующий `tests/ValidateCopiMinePermissions.ps1` под новый релизный режим с permission aliases.

## Собранные jar

- `minecraft/server/plugins/CopiMineUltimateAdminPlus.jar`
- `minecraft/server/plugins/CopiMineArtifacts.jar`
- `minecraft/server/plugins/CopiMineNarcotics.jar`
- `minecraft/server/plugins/AuthEffects.jar`

## Ручное тестирование

Полная матрица для первого админского smoke/live теста находится в `FULL_ALL_PLUGINS_MANUAL_TEST_MATRIX.md`. В ней есть пустая колонка "Фактический результат", чтобы тестировщик мог пройти все функции и отметить реальное поведение.


