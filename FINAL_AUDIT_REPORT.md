# CopiMine final audit report

Дата подготовки: 2026-06-11

## Что проверено

- Исходники:
  - `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`
  - `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
  - `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`
  - `admin-web/backend`
  - `admin-web/frontend`
  - `db/migrations`
  - `tests/Validate*.ps1`
- GUI:
  - `/cmultra`
  - банк и банкоматы
  - PIN GUI банка
  - выборы и live sidebar
  - `/cmartifacts`
  - PIN GUI лавки артефактов
  - `/cmnarcotics`
  - PIN GUI чёрного рынка
- SQL и PostgreSQL:
  - `atm_audit`
  - `ar_atms`
  - `atm_events`
  - `cmv4_bank_accounts`
  - `cmv4_bank_ledger`
  - `cmv4_bank_transfers`
  - `artifact_items_catalog`
  - `artifact_shops`
  - `artifact_purchases`
  - election tables
- Безопасность:
  - PIN не хранится и не показывается открыто.
  - Лавка не делает deposit/withdraw.
  - Artifacts не пишет напрямую в банковские таблицы.
  - Игрокам не показываются `SQLException`, `PSQLException`, Java class names и stacktrace.
  - Секреты `.env`, пароли и PIN не логируются.

## Что исправлено

- Исправлена причина ошибки `PSQLException: column "time" of relation "atm_audit" does not exist`.
  - Код теперь создаёт и пишет `atm_audit.created_at`.
  - Добавлена совместимость: если в старой таблице есть `time`, значения копируются в `created_at`.
  - Добавлена миграция `db/migrations/20260611_003_bank_atm_audit_gui_fix.sql`.
  - Добавлена расширенная миграция `db/migrations/20260612_004_gui_bank_shop_full_fix.sql`.
- Улучшена русификация банка и банкоматов.
  - `ATM registry`, `Deposit all`, `Withdraw`, `Transfer recipient`, `Invalid bank PIN` заменены на русские пользовательские строки.
  - PIN GUI банка переименован в нейтральное `Введите код`.
- Исправлен live sidebar.
  - Убраны кракозябры вида `РљР...` и `в”`.
  - Оставлена попытка скрытия правых score-цифр через Bukkit/Paper number format.
- Улучшены безопасные ошибки GUI.
  - В чат игрока больше не выводятся технические SQL/Java сообщения.
  - Технические детали остаются только в server log.
- Исправлен PIN GUI `CopiMineNarcotics`.
  - Маска PIN больше не находится в title GUI.
  - Ввод отображается внутри интерфейса как маска.
- Улучшена лавка `CopiMineArtifacts`.
  - Добавлен полный V4-каталог из 12 товаров: оружие, броня и инструменты.
  - Главное меню лавки переведено в плотный Minecraft UI: категории, покупки, ремонт, помощь и закрытие.
  - Чёрный рынок встроен как отдельная вкладка лавки, но появляется только в активный день рынка.
  - Покупка чёрного рынка остаётся в `CopiMineNarcotics` и идёт через тот же официальный bank bridge.
- Добавлена отдельная команда `/cmbank`.
  - Она открывает раздел банка, AR и банкоматов без поиска через главное меню.
- Добавлен `messages_ru.yml` для русских GUI/messages-маркеров AdminPlus.
- Добавлена финальная матрица ручного тестирования:
  - `FULL_MANUAL_TEST_MATRIX.md`
  - В ней есть пустая колонка `Фактический результат` для приёмки админом.
- Добавлен финальный validator:
  - `tests/ValidateCopiMineFinalGuiBankShopFix.ps1`
- Добавлены отдельные validators из финального ТЗ:
  - `ValidateCopiMineAtmAuditSchema.ps1`
  - `ValidateCopiMineRussianUi.ps1`
  - `ValidateCopiMineSidebarEncoding.ps1`
  - `ValidateCopiMinePinGui.ps1`
  - `ValidateCopiMineBankShopSeparation.ps1`
  - `ValidateCopiMineArtifactsAdminSettings.ps1`
  - `ValidateCopiMineGuiNoRawSqlErrors.ps1`
  - `ValidateCopiMineGuiArchitecture.ps1`

## Что подтверждено проверками

- Все три jar пересобраны и скопированы в `minecraft/server/plugins`.
- Все `tests/Validate*.ps1` прошли.
- `admin-web/frontend/assets/app.js` прошёл `node --check`.
- `admin-web/backend/main.py` прошёл `python -m py_compile`.
- Локальный smoke-preflight корректно блокирует запуск без `POSTGRES_PASSWORD`.

## Важное условие live-запуска

На локальной Windows-копии нет реального `POSTGRES_PASSWORD` в `admin-web/.env`, поэтому полный live smoke с включением банка/bridge невозможен без секрета.

На Ubuntu перед запуском обязательно проверь:

```text
/opt/copimine/admin-web/.env
POSTGRES_PASSWORD=реальный_пароль
```

Если пароль отсутствует или равен `CHANGE_ME`, `CopiMineUltimateAdminPlus` остановится, а `CopiMineArtifacts` и `CopiMineNarcotics` не включатся. Это правильное поведение, чтобы не было рассинхрона экономики.
