# CopiMine: переход на Minecraft 26.3

Документ описывает миграционный кандидат Minecraft 26.3, его воспроизводимую сборку, проверки и критерии приёмки. Игровые волны и redesign поведения мобов остаются вне этого этапа.

## Текущий статус

`tools/minecraft-26.3/profile.lock.json` и `server-plugins.lock.json` — источник точных версий и контрольных сумм. До подтверждения настоящего запуска Paper и Fabric клиента, входа в игру, проверки resource pack и снимков экрана статус остаётся `migration-candidate`, а `nativeVerified` — `false`. Успешные unit-тесты, сборка, CI или запуск одного только сервера этот статус не повышают.

Локальный тестовый сервер изолирован от сети: Paper привязан только к `127.0.0.1`, использует отдельную PostgreSQL БД и для тестов работает с `online-mode=false`. Это позволяет подключить лицензированный или offline-клиент с того же компьютера; такая конфигурация не делает сервер публичным и не должна открываться наружу напрямую. Публичный вход требует отдельно проверенной границы proxy/auth и не входит в эту локальную настройку.

## Зафиксированная сборочная база

| Компонент | Закреплённая версия или источник |
| --- | --- |
| Minecraft | 26.3, Mojang manifest и client JAR по SHA-1 в `profile.lock.json` |
| Paper | build 169 beta, JAR по SHA-256 и размеру в `profile.lock.json` |
| Java сборки | Temurin 25; Grim compatibility patch собирается Java 21 |
| Gradle | 9.8.1, distribution SHA-256 закреплён в lock |
| Fabric | Loader 0.19.5, Loom 1.17.21, Fabric API 0.162.0+26.3 |
| CopiMineClient | 0.1.1+26.3; точные размер и SHA-512 закреплены в lock |
| Gradle зависимости | SHA-256 manifest и описание происхождения в `GRADLE_DEPENDENCY_VERIFICATION.md` |
| Серверные плагины | Lock с URL, версией, размером, SHA-256 и SHA-512 в `server-plugins.lock.json` |

Версии `candidate` означают, что артефакт подлинно закреплён и подготовлен, но ещё не прошёл полный реальный Minecraft acceptance. Они не являются гарантией совместимости конкретного плагина с работающим сервером.

## Файлы и ответственность

- `profile.lock.json` фиксирует Paper, client, Fabric и resource pack.
- `client-mods.lock.json` фиксирует внешние моды для профиля клиента.
- `server-plugins.lock.json` фиксирует полный набор модулей и неизменённых upstream-плагинов.
- `java-plugin-compile-dependencies.lock.json` фиксирует compile-only зависимости общих Java плагинов.
- `PrepareMigrationCandidates.ps1` строит и сверяет миграционные артефакты.
- `install_migration_server_plugins.py` безопасно stage/install-проверяет серверные плагины и inventory.
- `InstallMigrationProfile.ps1` устанавливает профиль и отдельно проверяет его установленное состояние через `-ValidateOnly`.
- `StartMigrationTestServer.ps1` запускает только принадлежащий миграции runtime и изолированную PostgreSQL конфигурацию.
- `RunJavaPluginCi.ps1` строит custom Java-плагины и запускает их компиляционные и policy-регрессии.
- `NATIVE_ACCEPTANCE_SIGNING.md` описывает сбор реального evidence, подпись и проверку доверительной границы.
- `MIGRATION_PROGRESS.md` — append-only журнал фактических тестов, review, CI и блокеров с датами.
- `MIGRATION_RUNBOOK.md` — пошаговая сборка, установка в локальный клиент, проверки артефактов и границы acceptance.
- `DATABASES.md` — карта изолированной PostgreSQL БД и миграционной схемы.

## Клиентские моды

Lock содержит все 13 внешних JAR из установленного профиля `D:\.minecraft\versions\CopiMine\mods`. Версии, source release IDs, URL и хэши каждого JAR закреплены в `client-mods.lock.json`; значения `version` страницы релиза могут отличаться от версии внутри `fabric.mod.json`.

| Мод | Версия Minecraft 26.3 |
| --- | --- |
| Fabric API | 0.162.0+26.3 |
| AppleSkin | 3.0.10+mc26.3 |
| BetterF3 | 20.0.0 |
| Cloth Config | 26.3.159+fabric |
| Emotecraft | 3.5.0-a.build.171 |
| FPS Reducer | 26.3-2.18 |
| Iris | 1.11.7+mc26.3 |
| JourneyMap | 26.3-6.0.10+fabric |
| Armor Durability HUD | 8.1.1.6 |
| Player Animation Library | 1.2.8+mc.26.3 |
| Sodium | 0.9.2+mc26.3 |
| Simple Voice Chat | 2.6.24+26.3 |
| CustomSkinLoader | 15.1 |

## Подготовка кандидата

Запускайте из чистого migration checkout с Python 3.13.16, Java 25 и Java 21. Скрипт сверяет закреплённые версии Java и хеши исходных и собранных файлов; не заменяйте результаты локальными JAR неизвестного происхождения.

```powershell
py -3.13 -m pytest -q -rs tests/test_minecraft_26_3_paper.py tests/test_minecraft_migration_profile.py tests/test_minecraft_migration_server_plugins.py
py -3.13 .\scripts\minecraft\install_migration_server_plugins.py --stage-only
```

Для полной сборки задайте абсолютные пути к закреплённым Java 25 и Java 21 и выполните:

```powershell
$Python313 = (py -3.13 -c 'import sys; print(sys.executable)').Trim()
.\scripts\minecraft\PrepareMigrationCandidates.ps1 `
  -BuildJavaHome $BuildJava25Home `
  -GrimPatchJavaHome $GrimPatchJava21Home `
  -PythonExe $Python313
```

Профиль установленного Minecraft клиента можно проверить без записи:

```powershell
.\scripts\minecraft\InstallMigrationProfile.ps1 `
  -ProfileDirectory 'D:\.minecraft\versions\CopiMine' `
  -ValidateOnly
```

`-ValidateOnly` не чинит профиль автоматически. После любой установки повторите проверку SHA-512 каждого внешнего JAR и CopiMineClient по lock.

Проверка `-ValidateOnly` выполняется без записи: сверяет установленный профиль с lock, проверяет все 13 внешних JAR по размеру, SHA-512 и `fabric.mod.json`, CopiMineClient, SHA-256 resource pack, его включение в `options.txt` и receipt. Для неё должны быть доступны локальные source-build артефакты CopiMineClient и pack; этот режим не скачивает моды и не исправляет файлы. Чтобы проверить только JSON определения профиля и закреплённые источники внешних модов без собранных клиентских артефактов, используйте `-ValidateDefinitionOnly`.

Lock клиента хранит Fabric mod id, версию релиза источника и точную версию из `fabric.mod.json` каждого внешнего JAR. Подготовка кандидата и установщик сравнивают ID и внутреннюю версию метаданных с lock. При обычной установке одна старая копия закреплённого мода под другим именем побайтно копируется в `migration-backups/client-mods/<timestamp-id>/`; содержимое исходного JAR включается в журнал отката и удаляется из `mods` только перед атомарной установкой закреплённого файла. При ошибке транзакция восстанавливает исходный JAR. `-ValidateOnly` сообщает о конфликте без изменений; несколько конфликтных JAR с одним mod id и файлы-ссылки блокируют установку.

## Тестовые слои

Запускайте слои отдельно, чтобы видеть, что именно доказано:

```powershell
# Python contract suite на CI-совместимом Python
py -3.13 -m pytest -q -rs tests

# Сборка и тесты client-порта на закреплённом Gradle/Java 25
# Запускать через PrepareMigrationCandidates.ps1 либо migration CI workflow.

# Общий Java plugin gate на закреплённом Java 21
.\scripts\minecraft\RunJavaPluginCi.ps1 `
  -JavaHome $GrimPatchJava21Home `
  -WorkspaceRoot $PWD.Path `
  -TemporaryDirectory $env:TEMP `
  -PythonExe $Python313

# Локальная схема и миграция на выделенном loopback PostgreSQL
py -3.13 -m pytest -q -rs tests/test_minecraft_26_3_custom_plugin_database_contracts.py
```

Remote acceptance состоит из двух независимых pipeline: GitHub Actions и GitLab CI на одном исходном SHA. Postgres integration test должен запускаться с выделенным временным PostgreSQL service; `skipped` не считается PASS. Сверяйте commit SHA и статус каждого провайдера отдельно.

Оба pipeline проверяют `--require-evidence-for-accepted-locks` на любой ветке. Для обычного кандидата с `runtime-candidates` и `nativeVerified=false` подпись не требуется; если в lock-файлах появляется принятое состояние, проверка без подписанного evidence завершается ошибкой. Если файлы native acceptance добавлены, CI на любой ветке сверяет подпись, доверенный ключ и исходный commit SHA. GitLab запускает ветки `codex/minecraft-26-3-migration` и `codex/minecraft-26-3-migration-candidate-YYYYMMDD` по push и подавляет для них synthetic merge-request pipeline, чтобы источник подписи сверялся с точным commit, а не merge commit.

## Перенос старой схемы кандидатов

ElectionCore сначала переносит значения старых колонок uuid и name в канонические player_uuid и player_name. После копирования он удаляет ограничения PRIMARY KEY/UNIQUE и самостоятельные уникальные индексы, зависящие от uuid или name, включая индексы-выражения вроде `UNIQUE(lower(name))`, затем снимает NOT NULL с этих двух compatibility-колонок. Зависимости индексов проверяются через PostgreSQL catalog `pg_depend`, а не только по `indkey`, поскольку expression indexes имеют нулевой номер атрибута в этом массиве. Новые записи AdminPlus содержат только канонические поля, поэтому сохранение старых ограничений иначе могло бы блокировать кандидата в следующем голосовании.

До создания уникального индекса (election_id, player_uuid) пустые legacy-идентификаторы помечаются как неактивные, а повторные строки одного игрока и выборов сохраняются в candidate_migration_duplicate_archive. Их голоса и администраторские корректировки суммируются в сохраняемой строке. Если внешний ключ зависит от старого ключа uuid/name, PostgreSQL не разрешит удалить его без каскада; вся транзакция откатится, чтобы не повредить зависимые данные. Такой случай требует отдельной миграции этой зависимости перед повторным запуском.

Проверка схемы использует изолированный PostgreSQL copimine_migration_test: тест создаёт старую таблицу с PRIMARY KEY(uuid), UNIQUE(name) и NOT NULL, запускает реальный DDL из ElectionCore, проверяет перенос старой строки и выполняет новую каноническую вставку того же игрока в другое голосование. Отдельный контракт проверяет, что периодическая AR-сверка не запускает два прохода одновременно и освобождает guard после каждого завершения.

## Изменения в обработке продаж AR

`transferToAccount` одной банковской транзакцией уменьшает баланс покупателя и увеличивает баланс целевого счёта. Запись покупки теперь сохраняет ID этого перевода как уже завершённый доход. Фоновый обработчик не создаёт вторую банковскую операцию: старые `PENDING` строки с `bank_tx_id` переводятся в `CREDITED`, а строки без подтверждённого ID уходят на ручную проверку.

Если БД вернула неоднозначный результат записи покупки, plugin не возвращает деньги немедленно. Покупка и её доставляемый экземпляр сверяются повторно; перед решением о возврате сверщик ждёт минимальный интервал, берёт такой же PostgreSQL advisory lock, как операция записи, и проверяет факт commit в ограниченном lock timeout. Немедленный возврат остаётся только для двух явных quota-ошибок, которые возникают до commit.

Экономический bridge сохраняет время перевода в миллисекундах, а общий plugin clock возвращает секунды; политика восстановления явно приводит текущее время к миллисекундам и откладывает автоматический возврат при будущем timestamp или переполнении.

## Условия полной приёмки

AR-сверка запускается scheduler каждые 200 тиков. Атомарный guard разрешает только один активный проход и всегда освобождается в finally: это защищает cursor, pending recovery state и счётчик повторного сканирования, если запрос к БД длится дольше интервала запуска.

Миграцию можно считать принятой только после всех пунктов:

1. Точная сборка из чистого checkout, без неучтённых generated или локальных файлов.
2. Полные автоматические тесты и review изменённого кода, без незакрытых actionable findings.
3. Успешный GitHub Actions pipeline и успешный GitLab pipeline на одном и том же commit SHA.
4. Два штатных запуска полного locked plugin inventory на Paper 26.3 и фактическое состояние всех записей плагинов.
5. Запуск профиля CopiMine на Minecraft 26.3, подключение к локальному серверу, проверка ресурс-пака, клиентского bridge и нужных визуальных эффектов.
6. Свежие серверные/клиентские логи, inventory и скриншоты F2, сверенные с lock и связанные с проверяемым SHA.
7. Подписанная native acceptance запись, где `sourceCommit` указывает на проверенный source commit. Любое изменение кода после него требует нового запуска, evidence и подписи.

Полная процедура, включая AuthMe, полный inventory и подпись, описана в `NATIVE_ACCEPTANCE_SIGNING.md`. Логи, снимки, ключи и runtime DB credentials не коммитятся. Native evidence и любой публичный login/proxy security setup не имитируются unit-тестами.
