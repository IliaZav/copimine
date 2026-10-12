# Приёмка CI и review для Minecraft 26.3

Документ фиксирует проверяемые результаты migration checkpoint. Волны 1–7 и Rift Guardian остаются отложены. Git push, зелёная сборка или CodeRabbit review сами по себе не означают, что миграция принята.

Политика CI обновлена 2026-10-11 по решению владельца проекта: GitHub Actions является обязательным источником приёмки; GitLab — резервное зеркало, его pipeline может быть диагностическим и не блокирует результат. Записи ниже сохраняют фактические статусы на момент прежних запусков.

## Исторический снимок migration candidate — 2026-10-12, 01:24 UTC

Этот блок был записан до завершения push/PR runs #889/#890 и до повторного подтверждения Paper listener. Актуальный статус находится в конце документа.

- Рабочая ветка `codex/minecraft-26-3-migration-candidate-20261010` основана на `00f2c6927c9db3c58599fc77e257c6d1ea623061`; локальные исправления и проверки завершены, публикация exact-head SHA и GitHub Actions ещё ожидаются. Draft PR [#5](https://github.com/IliaZav/copimine/pull/5) остаётся открытым и не слит.
- Свежая сводка проверок, точные артефактные хэши и блокеры: [validation evidence](VALIDATION_EVIDENCE_2026-10-12.md). Транскрипты команд хранятся в `evidence/`.
- Локальные проверки после последних исходников: Python — `1555 passed, 7 skipped, 88 warnings`; миграционные PowerShell validators — `636 passed, 0 failed, 25 skipped`; отдельная проверка профиля — `2 passed`; PostgreSQL contracts — `27 passed`; CopiMineClient Gradle — `31 suites / 127 tests`. Транскрипты и SHA-256 указаны в [validation evidence](VALIDATION_EVIDENCE_2026-10-12.md).
- Старый `scripts/package_full_release.ps1` собирает клиент 1.21.1. Он теперь fail-closed, если профильный migration lock существует либо если разобранная YAML-конфигурация требует CopiMineClient 26.3; guard использует SafeLoader и отклоняет malformed/ambiguous YAML и повторяющиеся ключи до создания output. Legacy bundle не является выпуском миграции. `PrepareMigrationCandidates.ps1` строит отдельные 26.3 артефакты по lock; production release bundle требует отдельной миграционной упаковки.
- GitHub — обязательный CI и основной review authority: на новом exact-head SHA должны пройти все Actions jobs. GitLab используется как резервное зеркало.
- Последний подтверждённый локальный Paper 26.3 build 169 запуск и read-only сверка 29 активных JAR с lock/receipt относятся к 2026-10-11; текущий процесс сейчас не подтверждён. В startup logs есть PostgreSQL readiness и предупреждение ProtocolLib о неподтверждённой upstream поддержке Minecraft 26.3.
- `nativeVerified=false`: нет входа CopiMineClient, отказа vanilla/wrong-version клиента, bridge handshake в игре и F2 screenshots для текущих хэшей. Последняя попытка запуска TLauncher дала только пустые окна, без Minecraft-процесса. Изменённый клиент может подделать публичный HELLO, поэтому это проверка совместимости для обычного клиента, не криптографическая аттестация JAR; ограничение описано в [политике client enforcement](AUTH_AND_CLIENT_MOD_ENFORCEMENT.md).

## Кодовый checkpoint 3a31dab9

- Полный SHA: 3a31dab971cea6b81b1ee8252635f054c397777b.
- GitHub и GitLab refs проверены через git ls-remote; обе указывали на этот SHA.
- Draft PR [#5](https://github.com/IliaZav/copimine/pull/5) и draft MR [!1](https://gitlab.com/copimine-group/Copimine-project/-/merge_requests/1) не слиты.
- Lock был исправлен после того, как build JAR получил восстановление orphan-shop, а ожидаемый digest отстал. Две чистые сборки Gradle 9.8.1 на JDK 25 дали одинаковый JAR: 325573 байта, SHA-256 64340259981c4e4bb56e3e1168c8f901f2b658794338eeef458b2eb0617de5ef, SHA-512 de4448b0a959caf006ad2dbd5904c3be0c8b88bdb82d132168df275574730ad5c89c04f8fe790e04846ee540ea906ddd05566ddc72971efca665b111dfcab222.
- В commit `3a31dab9` обновлены candidate SHA-256/SHA-512/size и test assertions; legacy baseline тогда не менялся. После commit `9f6a7c08` точный SHA-256 установленного старого `CopiMineArtifacts.jar` добавлен в `replaceSha256` в последующем working-tree diff; lock и регрессионная проверка этого перехода вошли в commit `afcc75cd`. Candidate SHA-256/SHA-512/size остались неизменными. Кандидат по-прежнему требует runtime-validation, `nativeVerified=false`.

## Проверки кандидата и тестов

| Проверка | Результат | Граница доказательства |
| --- | --- | --- |
| Две чистые сборки CopiMineArtifacts | Одинаковые байты и размер/хэши выше | Повторяемость сборки, не runtime-совместимость |
| PrepareMigrationCandidates.ps1 -StageOnly | PASS, 31 pinned plugin candidates | Lock/staging integrity, не запуск Paper |
| Candidate build/runtime digest test в изолированном harness | 1 passed, 107 deselected | JAR bytes/metadata, не запуск игры |
| GitHub Python suite для 3a31dab9 | 1540 passed, 1 failed, 7 skipped, 88 warnings | Один Windows matcher contract упал; подробности ниже |
| Локальные dangling-link проверки после test fix | 1 passed, 1 skipped, 106 deselected | Symlink-capability test пропущен локально; отдельный lstat regression прошёл |

## GitHub Actions: результат 3a31dab9

GitHub завершил оба triggers для точного SHA:

| Trigger | Run | Итог |
| --- | --- | --- |
| Push | [38114509080](https://github.com/IliaZav/copimine/actions/runs/38114509080) | PostgreSQL contract — passed; static-and-contract — failed; java-plugins — skipped |
| Pull request | [38114510726](https://github.com/IliaZav/copimine/actions/runs/38114510726) | PostgreSQL contract — passed; static-and-contract — failed; java-plugins — skipped |

Падение было в test_dangling_plugin_temp_symlink_is_rejected_before_backup_or_copy: Windows runner создал dangling symlink и installer правильно отклонил её до mutation, но тест ожидал только текст temporary|symlink|link. Фактический ValueError — Unrecognized staging file in plugin directory: new.candidate. Соседний regression, который проверяет тот же fail-closed path через lstat, ожидал это сообщение. Исправление расширяет matcher теста, оставляя проверки старого JAR, отсутствия внешней копии/backup и сохранения symlink. Исправленный source SHA должен пройти оба GitHub triggers заново; результат для 3a31dab9 не переносится на последующий commit.

## Исторический GitLab CI результат — резервное зеркало

Для SHA 3a31dab9 GitLab создал [push pipeline 2935239276](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935239276) и [MR pipeline 2935239311](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935239311). Оба завершились со статусом failed, не запустив ни одной job (списки jobs пусты). Штатный API запуск вернул HTTP 400: Identity verification is required in order to run CI jobs.

Это account-level блокировка проверки личности, не результат тестов. GitLab остаётся резервным хранилищем по текущему решению владельца проекта; его pipeline не является обязательным migration acceptance gate. GitHub Actions на опубликованном candidate SHA остаётся обязательным.

## Code review и Security

- CodeRabbit CLI 0.8.2: 0 issues на lock и candidate integrity test; отдельный свежий review документации не нашёл issues.
- Независимый локальный reviewer сверил candidate hashes/test coverage; actionable findings нет.
- Codex Security diff scan d3a550a1-2321-4c7f-9bee-f826dfb9dd63 завершён для 8f02bb311d44eeb84f47da99b19b7f304fa8942e..3a31dab971cea6b81b1ee8252635f054c397777b: coverage complete, 0 findings. Он проверял diff и supporting source; Minecraft runtime не запускался.
- Code Review connector прочитал статусы GitHub PR checks; это CI diagnostics, не отдельный source review.

## Paper, клиент и native acceptance

До runtime upgrade локальный Paper 26.3 build 169 слушал 127.0.0.1:25568 (PID 30552) со старым CopiMineArtifacts.jar: 325468 байт, SHA-256 3bd3dd68…. Новый JAR не копировали в работающий процесс: сервер сначала штатно остановили и только затем установили candidate. После перезапуска Paper загрузил candidate 643402…; актуальные runtime evidence приведены в разделе «Текущий кодовый diff 9f6a7c08».

Computer Use вернул apps: [] вместе с ошибкой browser request-header policy; Remote Desktop Commander сообщил, что DESKTOP-DQLROSV offline. В текущей сессии не подтверждены native login, F2 screenshots, paired client/server logs, загрузка всего candidate inventory и resource-pack acceptance. nativeVerified=false сохраняется.

## Gates на момент checkpoint `afcc75cd` (историческая запись)

1. Для этого старого checkpoint ожидалось завершить GitLab identity verification и получить успешные push/MR jobs; на `afcc75cd` GitLab создал два pipeline, но не запустил ни одной job. Это историческое требование superseded решением владельца: GitHub — обязательный CI/review authority, GitLab — необязательное резервное зеркало и его CI не блокирует acceptance.
2. Выполнить native login в профиле `D:\.minecraft\versions\CopiMine`, проверить моды/resource pack и сохранить F2 evidence, привязанный к exact source SHA. Среда Computer Use пока недоступна.
3. Только после подписанного native acceptance закрывать migration checkpoint. До этого игровые стадии Waves 1–7 и Rift Guardian не запускать.

GitBook не подключён к организации: доступный API ранее вернул items: []. Эта документация хранится в репозитории и доступна через draft PR/MR.

## Итоговый исходный commit afcc75cd

- Полный source SHA: `afcc75cd3fed0eb305650a9970c02c6174c71102`. Этот commit добавил точный старый SHA-256 `3bd3dd68f58f9fad7e23ef8a6c18c0095c1004e4d2a5b59fd9276183dc16e6b7` в `CopiMineArtifacts.replaceSha256` и regression assertion. Новый кандидат неизменен: 325573 байта, SHA-256 `64340259981c4e4bb56e3e1168c8f901f2b658794338eeef458b2eb0617de5ef`; SHA-512 приведён выше.
- Локальный full-suite запуск выполнен на рабочем дереве, основанном на `9f6a7c08`, с точным lock/test diff, который затем вошёл в `afcc75cd`: `py -3.13 -m pytest -q -rs tests` — `1541 passed, 7 skipped, 88 warnings` за 260.82 секунды, без failures. Два PostgreSQL-контракта, пропущенные общим suite без DSN, отдельно прошли на изолированной тестовой БД: `2 passed in 0.99s`.
- GitHub push run [38117821447](https://github.com/IliaZav/copimine/actions/runs/38117821447) и PR run [38117823735](https://github.com/IliaZav/copimine/actions/runs/38117823735) завершились success на точном SHA `afcc75cd3fed0eb305650a9970c02c6174c71102`. В каждом прошли `migration-postgres-contract`, `static-and-contract` и `java-plugins`.
- GitLab push pipeline [2935306765](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935306765) и MR pipeline [2935306782](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935306782) завершились failed до запуска jobs; в обоих списки jobs пусты. Для этого аккаунта CI API возвращает `Identity verification is required in order to run CI jobs`. Это внешний account-level gate, GitLab tests не выполнены.
- CodeRabbit CLI review пяти изменённых файлов завершился `0 issues` после фикса одного minor замечания о прошедшем runtime-состоянии. Independent source reviewers не нашли actionable findings. Codex Security diff scan `7b4f3c1a-4570-4fd9-a1e7-9762072901f2` — `0 findings`, coverage `complete` для исходного security snapshot, включавшего тот же lock/test diff до commit. GitHub Code Review connector подтвердил статусы CI, он не является отдельным source review.
- Старый Paper процесс остановлен штатно, затем candidate был установлен миграционным installer с backup/rollback и receipt validation. Paper 26.3-169 на Java 25 перезапущен с 29 плагинами из lock; `CopiMineArtifacts 1.1.0-shops-blocks-prices` загрузился, EconomyCore, AdminPlus и ElectionCore сообщили `ENABLED`/`DB READY` на отдельной PostgreSQL. AuthMe/AuthEffects отключены только в loopback-тестовом runtime.
- Server status handshake к `127.0.0.1:25568` вернул `Paper 26.3`, protocol `777`, online `0/1`; listener сейчас привязан к loopback. Профиль `D:\.minecraft\versions\CopiMine` прошёл read-only `InstallMigrationProfile.ps1 -ValidateOnly`, сверив client profile и моды по lock. Это не заменяет фактический запуск клиента, вход или визуальную проверку resource pack.
- При старте остаются OSHI/Windows Perflib, ProtocolLib 26.3 compatibility/`Unsafe` deprecation и offline-mode voice encryption warnings. `Computer Use` в этой сессии видит только браузеры (`apps: []`), поэтому Minecraft GUI, реальный вход и F2 screenshot не выполнены. `nativeVerified=false` сохраняется; Waves 1–7 и Rift Guardian не начинались.

## Локальный candidate после mandatory CopiMineClient — 2026-10-11

Это отдельное последующее рабочее дерево на ветке `codex/minecraft-26-3-migration-candidate-20261010`, основанное на `00f2c6927c9db3c58599fc77e257c6d1ea623061`; эти результаты не относятся к `afcc75cd` до создания и публикации нового commit.

| Проверка | Результат | Что подтверждает |
| --- | --- | --- |
| CopiMineClient Gradle `test jar` (Gradle 9.8.1 / Java 25) | PASS; 14,827,743 bytes; SHA-512 `68be0d8cf07d38c375cf49595e60ec8f21d4897414bff308dd09976e18fbf2127acfa6de84674cbbdd1c67e6b832a4ad52dccfa5b06253ea93e92d0c1b6f559e` | Версию 0.1.1+26.3 и nested Fabric API; не заменяет реальное подключение клиента |
| First-party Paper 26.3 migration candidates | CopiMineEndEvent 1,349,199 bytes, SHA-256 `c77873078f3eb61bd3f3c5059aec0f3f89b04c7f5b2044c972ecf58a47c113dd`; CopiMineNarcotics 225,221 bytes, SHA-256 `d53bafb4ebe9d65b8a0cfa67a82d8acf48ac0328639ceadfd9854dbad5f23a97` | Собранные артефакты сверены с lock; не заменяет runtime проверку всех плагинов |
| Полный Python 3.13 suite без default DB DSN | `1551 passed, 6 skipped, 88 warnings` | Изолированные PostgreSQL contracts отдельно дали `27 passed`; native client и Windows-only private-key/symlink semantics остаются недоказанными |
| PostgreSQL contracts | `27 passed`; все два DB integration tests вошли в полный suite | Только изолированную `copimine_migration_test`, не production DB |
| CopiMine profile install + `-ValidateOnly` | PASS: 12 external mods + CopiMineClient 0.1.1+26.3 + resource pack; standalone Fabric API backup-removed | Закреплённые профилированные байты и metadata, не фактический запуск GUI |
| Paper 26.3-169 runtime | PASS: `Done (29.234s)`, 29 plugins initialized, loopback status protocol 777, 0/1; custom EconomyCore/ElectionCore/Artifacts/AdminPlus report DB ready | Startup/inventory/status; не native gameplay acceptance |
| Mandatory client contracts | PASS: exact-version, default-required, timeout, abuse guards, registered hanging-break guard; hanging-break reviewer finding fixed | Source-level event contracts; plugin-message challenge is not cryptographic proof |

Profile validator выявил Windows PowerShell 5.1 incompatibility с .NET-only `SHA512.HashData`/`Convert.ToHexString`. Она исправлена на `SHA512.Create().ComputeHash()` и покрыта regression test; пользовательский профиль после этого установлен и `-ValidateOnly` прошёл.

Local Java gate `RunJavaPluginCi.ps1` завершился внешней загрузочной ошибкой на legacy 1.21.1 CopiMineClient build: Mojang `server.jar` ожидал 51,627,615 bytes, после трёх попыток получено 34,766,848 bytes (`BUFFER_UNDERFLOW with EOF`). Это не помечается как успешная Java gate; GitHub Actions должен быть проверен на итоговом SHA.

Профиль, установленный сервер и локальный Minecraft находятся на одном ПК, но Computer Use сообщает `apps: []`, а Desktop Commander — `DESKTOP-DQLROSV offline, last seen 22h ago`. Нативное подключение, отклонение vanilla/wrong-version клиентов и F2 screenshots не проверены; `nativeVerified=false`.

Исторически, на момент снимка 2026-10-12 01:24 UTC, этот diff ещё ожидал CI и публикации. Этот статус заменён более поздней контрольной точкой для d12b526 в конце документа.

## Актуальный CI checkpoint — 2026-10-12, SHA d12b526

GitHub exact-head push run [#889](https://github.com/IliaZav/copimine/actions/runs/38191785993) и PR run [#890](https://github.com/IliaZav/copimine/actions/runs/38191788666) — отдельные новые запуски на SHA d12b52638c90b6a5479a9508547165604ad1f1c9. В обоих прошли все jobs: static-and-contract, migration-postgres-contract и java-plugins. Проверка workflow-run API подтвердила conclusions. Более ранние runs #887/#888 относились к предыдущему SHA 876e2f07 и остаются отдельным историческим результатом.

GitHub остаётся обязательной CI-проверкой по решению владельца. В GitLab ветка синхронизирована с GitHub, но pipelines [#2936887334](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2936887334) и [#2936887383](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2936887383) завершились до запуска jobs; GitLab тесты не запускались и результат нельзя обозначать как pass.

Migration CI зелёный, но acceptance остаётся открытой. Native-evidence steps пропущены условием workflow, потому что в checkout SHA d12b526 отсутствовали файлы artifacts/minecraft-26.3/native-acceptance/. Отдельно от причины пропуска CI шагов, настоящий вход игрока и bridge handshake пока не подтверждены. См. актуальное migration evidence в VALIDATION_EVIDENCE_2026-10-12.md и progress ledger в MIGRATION_PROGRESS.md.
