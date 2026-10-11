# Приёмка CI и review для Minecraft 26.3

Документ фиксирует проверяемые результаты migration checkpoint. Волны 1–7 и Rift Guardian остаются отложены. Git push, зелёная сборка или CodeRabbit review сами по себе не означают, что миграция принята.

## Кодовый checkpoint 3a31dab9

- Полный SHA: 3a31dab971cea6b81b1ee8252635f054c397777b.
- GitHub и GitLab refs проверены через git ls-remote; обе указывали на этот SHA.
- Draft PR [#5](https://github.com/IliaZav/copimine/pull/5) и draft MR [!1](https://gitlab.com/copimine-group/Copimine-project/-/merge_requests/1) не слиты.
- Lock был исправлен после того, как build JAR получил восстановление orphan-shop, а ожидаемый digest отстал. Две чистые сборки Gradle 9.8.1 на JDK 25 дали одинаковый JAR: 325573 байта, SHA-256 64340259981c4e4bb56e3e1168c8f901f2b658794338eeef458b2eb0617de5ef, SHA-512 de4448b0a959caf006ad2dbd5904c3be0c8b88bdb82d132168df275574730ad5c89c04f8fe790e04846ee540ea906ddd05566ddc72971efca665b111dfcab222.
- В commit `3a31dab9` обновлены candidate SHA-256/SHA-512/size и test assertions; legacy baseline тогда не менялся. Следующий source diff `9f6a7c08` отдельно добавил SHA-256 установленного старого `CopiMineArtifacts.jar` в `replaceSha256`, чтобы разрешить только этот точный upgrade-переход. Candidate SHA-256/SHA-512/size остались неизменными. Кандидат по-прежнему требует runtime-validation, `nativeVerified=false`.

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

## GitLab CI: account-level blocker

Для SHA 3a31dab9 GitLab создал [push pipeline 2935239276](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935239276) и [MR pipeline 2935239311](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935239311). Оба завершились со статусом failed, не запустив ни одной job (списки jobs пусты). Штатный API запуск вернул HTTP 400: Identity verification is required in order to run CI jobs.

Это account-level блокировка проверки личности, не результат тестов. Нельзя считать GitLab CI пройденным, пока не появятся независимые успешные push/MR pipelines для одного и того же source SHA.

## Code review и Security

- CodeRabbit CLI 0.8.2: 0 issues на lock и candidate integrity test; отдельный свежий review документации не нашёл issues.
- Независимый локальный reviewer сверил candidate hashes/test coverage; actionable findings нет.
- Codex Security diff scan d3a550a1-2321-4c7f-9bee-f826dfb9dd63 завершён для 8f02bb311d44eeb84f47da99b19b7f304fa8942e..3a31dab971cea6b81b1ee8252635f054c397777b: coverage complete, 0 findings. Он проверял diff и supporting source; Minecraft runtime не запускался.
- Code Review connector прочитал статусы GitHub PR checks; это CI diagnostics, не отдельный source review.

## Paper, клиент и native acceptance

До runtime upgrade локальный Paper 26.3 build 169 слушал 127.0.0.1:25568 (PID 30552) со старым CopiMineArtifacts.jar: 325468 байт, SHA-256 3bd3dd68…. Новый JAR не копировали в работающий процесс: сервер сначала штатно остановили и только затем установили candidate. После перезапуска Paper загрузил candidate 643402…; актуальные runtime evidence приведены в разделе «Текущий кодовый diff 9f6a7c08».

Computer Use вернул apps: [] вместе с ошибкой browser request-header policy; Remote Desktop Commander сообщил, что DESKTOP-DQLROSV offline. В текущей сессии не подтверждены native login, F2 screenshots, paired client/server logs, загрузка всего candidate inventory и resource-pack acceptance. nativeVerified=false сохраняется.

## Оставшиеся gates

1. Отправить описанный ниже итоговый документированный diff в оба remotes и сверить, что ветки указывают на один SHA.
2. Получить GitHub push и PR checks, а также GitLab push и MR pipelines на этом SHA. GitHub успешно прошёл для `9f6a7c08`; GitLab identity verification всё ещё блокирует jobs.
3. Выполнить native login в профиле `D:\.minecraft\versions\CopiMine`, проверить моды/resource pack и сохранить F2 evidence, привязанный к exact source SHA. Среда Computer Use пока недоступна.
4. Только после подписанного native acceptance закрывать migration checkpoint. До этого игровые стадии Waves 1–7 и Rift Guardian не запускать.

GitBook не подключён к организации: доступный API ранее вернул items: []. Эта документация хранится в репозитории и доступна через draft PR/MR.

## Текущий кодовый diff 9f6a7c08

- Source SHA `9f6a7c08f7f5799a9c1ba120003f092f6f43fe88` содержит исправленный Windows matcher test и последующий allowlist upgrade для текущего установленного `CopiMineArtifacts` (`3bd3dd68f58f9fad7e23ef8a6c18c0095c1004e4d2a5b59fd9276183dc16e6b7`). Candidate остаётся 325573 байта, SHA-256 `64340259981c4e4bb56e3e1168c8f901f2b658794338eeef458b2eb0617de5ef`; соответствующий SHA-512 зафиксирован выше. Установленный JAR до upgrade был 325468 байт.
- GitHub PR run [38115685991](https://github.com/IliaZav/copimine/actions/runs/38115685991) и push run [38115683193](https://github.com/IliaZav/copimine/actions/runs/38115683193) завершились success на точном SHA `9f6a7c08f7f5799a9c1ba120003f092f6f43fe88`; в обоих прошли `migration-postgres-contract`, `static-and-contract` и `java-plugins`.
- GitLab push pipeline [2935260363](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935260363) и MR pipeline [2935260385](https://gitlab.com/copimine-group/Copimine-project/-/pipelines/2935260385) завершились failed до создания jobs; оба списка jobs пусты. GitLab CI заблокирован требованием account identity verification, это не успешный и не выполненный тест.
- Полный локальный pytest на старом runtime до остановки: `2 failed, 1539 passed, 7 skipped, 88 warnings`; оба failure относились к stale `CopiMineArtifacts` в активном runtime. Новый candidate после этого установлен через миграционный installer, старые JAR сохранены в backup. Startup validator сверил фактический plugin inventory и receipt.
- Полный `py -3.13 -m pytest -q -rs tests` после обновления runtime: `1541 passed, 7 skipped, 88 warnings` за 260.82 секунд, без failures. Два PostgreSQL-контракта, пропущенные основным suite из-за отсутствующего общего DSN, отдельно запущены на изолированной тестовой БД: `2 passed`.
- Старый Paper процесс был штатно остановлен; журнал подтверждает сохранение игроков, мира и chunks overworld/nether/end. Затем Paper 26.3-169 на Java 25 запустился с 29 плагинами из 31 locked entries. AuthMe/AuthEffects отключены только в loopback-тестовом runtime. `CopiMineArtifacts 1.1.0-shops-blocks-prices` загрузился; EconomyCore, AdminPlus и ElectionCore сообщили `ENABLED`/`DB READY` через отдельную PostgreSQL.
- Server status handshake к `127.0.0.1:25568` вернул `Paper 26.3`, protocol `777`, online `0/1`. Профиль `D:\.minecraft\versions\CopiMine` прошёл `InstallMigrationProfile.ps1 -ValidateOnly` без мутации. Это не заменяет настоящий клиентский вход или screenshot.
- На старте остаются предупреждения среды: OSHI не может прочесть некорректную Windows Perflib запись; ProtocolLib сообщает, что 26.3 ещё не подтверждён upstream, и JDK сообщает о будущей деградации `Unsafe`; Simple Voice Chat предупреждает о незашифрованном голосе в offline-mode. Сервер и voice-chat привязаны к loopback. Требуется native login/visual test до снятия `nativeVerified=false`.
- `Computer Use` в этой сессии видит только браузеры (`apps: []`), поэтому Minecraft GUI, вход игроком и F2 screenshot не выполнены. Волны и gameplay redesign остаются отложены.
- Итоговый fresh CodeRabbit review пяти файлов этого изменения завершился `0 issues`; первый проход нашёл minor устаревшее описание старого runtime JAR, которое исправлено до итогового review. Codex Security diff scan `7b4f3c1a-4570-4fd9-a1e7-9762072901f2` завершён с `0 findings` и `complete` coverage для exact source/security diff; он не заменяет native acceptance.
