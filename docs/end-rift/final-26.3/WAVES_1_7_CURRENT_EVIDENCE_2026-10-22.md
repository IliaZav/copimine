# End Rift Waves 1–7 — текущая матрица доказательств, 2026-10-22

> **Исторический снимок.** Актуальные статусы, установленные hashes и blockers находятся в `CURRENT_HANDOFF.md`, `REQUIREMENT_TEST_MATRIX.md`, `BOT_ACCEPTANCE.md`, `PERFORMANCE_REPORT.md` и `OWN_PLUGIN_INVENTORY.md`. Числа ниже (включая 2307 тестов и старый hash EndEvent `7026719d...`) относятся к более раннему состоянию и не являются текущим acceptance.

Это текущая сводка реализации и локальной проверки по master contract. Она заменяет более ранний снимок `WAVES_1_7_REPAIR_STATUS_2026-10-22.md`; старые данные сохранены в нём и помечены как superseded. Это не акт игрового принятия: live wave, бот-матрица и ручная графическая проверка ещё не выполнены.

## Требования и состояние реализации

| Область | Исправление / сохранённое требование | Доказательство и граница |
| --- | --- | --- |
| Wave 1 | Сохранены владение переносимыми объектами, charge/custody, восстановление и доставка; добавлена устойчивость к повторному удалению/выгрузке мобов. | Политики, lifecycle probes, Java и Python контракты прошли. Live перенос игроками не запускался. |
| Wave 2 | Таймер метки остаётся 45 секунд; Q/W/E/R prisoner controls сохранены. Исправлены bounded phantom/materialization и клиентское расположение рендеров. | Java policy/protocol/client tests и Python contracts прошли. Реальная двухпериодная метка не проверялась. |
| Waves 3–5 | Сохранены портал/elite/Rift Giant, физический projectile provenance/reflection, fog, capacity/refill и effect leases; закрыты частичные display rollback и lifecycle утечки. | Event regression и policy/adapter probes прошли. Нативные portal/fireball/fog сценарии не запускались. |
| Обычный и elite AI | Сохранены ограниченные групповые intent/target policies, физические dodgeable projectile paths и ownership-bound cleanup; generic elites не добавлены в Wave 7. | Java policy/event regression и Python runtime contracts прошли. Combat performance с участниками не замерялся. |
| Wave 6 | Исправлены guard role/range/aggro/abilities, caster progression, Enderman melee burst (40 ticks), Spider/guard tactics и Skeleton protection. Подтверждённый удар по normal wave Skeleton вызывает не более двух ближайших обычных Zombies; отложенный callback повторно проверяет, что Skeleton жив и принадлежит волне. | Red/green regression для dead Skeleton и текущие Java/Python/adapter probes прошли. Нативный бой с игроками не запускался. |
| Wave 7 | Сохранены ровно четыре утверждённых испытания; generic elites не добавлены. Проверены Echo, навигация/проход, возврат/grace, cleanup и ownership. | Wave 7 Echo (178), navigation/passage (25), return (10) и lifecycle probes прошли. Естественное прохождение игроками не запускалось. |
| Исторические F2 жалобы | Исходный набор из 92 F2 изображений просмотрен; воспроизведённые кодовые дефекты исправлены в source и покрыты контрактными/адаптерными тестами. Дизайн арены, босса и Kagune не менялся. | Нового нативного скриншота после сборки нет. Документированный ручной графический gate остаётся обязательным; GUI automation не применялась. |

## Автоматические проверки

| Проверка | Результат | Ограничение |
| --- | --- | --- |
| Полный Python suite, Python 3.13.16 + Pillow 13.0.0 | **2307 passed, 8 skipped, 124 warnings, 2 subtests passed**, exit 0, 610.78 s; полный suite запускался с изолированным Pillow 13.0.0 target без изменения глобальной Python среды. Log: `%TEMP%/CopiMine-Pytest-Pillow13-final-20261022-d3b8fa.log`, JUnit `build/pytest-wave-pillow13-final-20261022-d3b8fa-junit.xml`. | Восемь skip относятся к отсутствующему внешнему profile artifact, GrimAC fixture и платформенным Windows/POSIX правам/ссылкам. Paper player scenarios этим не подтверждаются. |
| Dependency vulnerability audit, `pip-audit --strict` | **PASS:** `admin-web/requirements.txt` и `requirements-security.txt` оба вернули `No known vulnerabilities found` при обновлённом Pillow 13.0.0. | На исходном pushed SHA `8e15640ce9167b4128fed71699acf1b2027f8f5d` GitHub обнаружил 24 advisories для Pillow 12.3.0 и сообщил fix version 13.0.0. Это сорвало `static-and-contract`, вызвало skip зависимых шагов и job `java-plugins`; данный запуск не засчитывается как acceptance. |
| Полный `RunJavaPluginCi.ps1` | **665/665 validators passed**, 0 failed, 0 skipped; End Rift child process exit 0; JavaPluginCI exit 0. Проверены pinned Temurin 21.0.12.1 и 25.0.4.1, Gradle 9.8.1; все pre-existing build outputs/plugin data восстановлены. | Прогон завершился до последней изолированной правки V4 readiness lookup. Текущий V4 sender/client/build coverage после неё дан следующей строкой; финальная authoritative проверка JavaPluginCI — exact-SHA GitHub Actions. Log: `%TEMP%/CopiMine-JavaPluginCI-final-20261022-0f6867c042324365a59e95be9d59d8e8.log`. |
| `RunClientBridgeV4Integration.ps1` после исправления offline player lookup | **281 client tests passed, 0 failed/errors/skipped; 94 Paper bridge tests, 0 failed/errors, 1 skipped**; migration plugin builds, resources и fail-closed contract guards прошли. | Это автоматическая интеграция/компиляция, а не live Paper + Fabric authenticated handshake. Полный End Rift gate был пропущен в этом вызове (`-SkipEndRiftGate`), его отдельный JavaPluginCI указан выше. Log: `%TEMP%/CopiMine-V4Integration-final-20261022-04970f1de95741edab9a30b62fe6c0fa.log`. |
| End Rift event gate | Wave 7 Echo 178, navigation/passage, return/lifecycle, recovery, participant inventory, combat observations; текущий Python contract 337 passed; Java policy/persistence группы и `git diff --check` прошли. | Автоматические probes; wave force-clear не запускался. Полный текущий Python suite приведён выше. |
| Junction-проверка PostgreSQL lifecycle | Path ordering исправлен: raw workspace/script/runtime paths проверяются на reparse ancestors до `Resolve-Path`. Регрессия: **1 passed** на длинном `build/pytest...` пути; DB contract module: **26 passed, 2 skipped**. | Assertion нормализует whitespace в PowerShell formatted error, чтобы line-wrap слова `reparse` не давал ложный fail. Никакие данные при отказе не создаются. |
| PostgreSQL backup/recovery | PostgreSQL 16.15 custom dump: 254679 bytes, SHA-256 `1bccb5b7c2628a803ccc7d9cc701b4fe22ca96160ce4ea8397893ccd35c0a3f8`; восстановление в отдельную disposable БД с `--exit-on-error`, схема и 102 таблицы подтверждены, 5 migration markers найдены. Disposable БД удалена. | Исходная БД и user-built worlds сохранены. Production schema не удалялась и не сбрасывалась. |

Финальный полный pytest выполнялся командой `py -3.13 -m pytest -q --basetemp build/pytest-wave-pillow13-final-20261022-d3b8fa --tb=short --junitxml build/pytest-wave-pillow13-final-20261022-d3b8fa-junit.xml` с `PYTHONPATH` на временный target Pillow 13.0.0; полный вывод сохранён в `%TEMP%/CopiMine-Pytest-Pillow13-final-20261022-d3b8fa.log`. JavaPluginCI log: `%TEMP%/CopiMine-JavaPluginCI-final-20261022-0f6867c042324365a59e95be9d59d8e8.log`.

## PostgreSQL, Paper и arena configuration

- PostgreSQL 16.15 работает на `127.0.0.1:55434`; DB `copimine_migration_test`, role `copimine_migration_test`, schema `copimine_migration_26_3_candidate`, SCRAM-SHA-256, loopback only. `pg_isready` подтвердил listener; существующие данные и private env files сохранены.
- Paper 26.3 build 205 под Temurin 25.0.4.1 достиг `Done (28.702s)!` на **установленном exact candidate inventory**, слушает только `127.0.0.1:25567`, RCON выключен, AuthMe 6.0.1 и AuthEffects включены. Загружены все 29 плагинов; PID 40456 остаётся запущен. Повторный запуск и `--validate-only` во время работающего Paper штатно отказались на общем lifecycle mutex; файлы проверены read-only SHA-256 сравнением с текущими кандидатами.
- Серверный `level-name` и `event-state.yml` оба указывают `CopiMine`. `event-state.yml` schema 4: phase `COLLECTING`, core `(8,68,-39)`, required players 2, bounds `(-12,65,-59)..(28,71,-19)`; `event-layout.yml` содержит те же границы. Gate status остаётся `UNSET`, snapshot пустой.
- Локальный `migration-plugin-receipt.json` совпадает со всеми 29 JAR в plugin directory: нет отсутствующих, лишних файлов или digest mismatch.
- `CopiMineArtifacts` сообщил `Artifacts bridge ready=true postgres=true`; EndEvent сообщил `services ready; phase=COLLECTING`. Живой бой не запускался. World/config проверены без очистки чанков, блоков или волн.
- Paper startup записал системное предупреждение Windows OSHI/Perflib о performance counters; AuthMe также не скачал необязательный GeoLite DB. Оба сообщения не помешали загрузке плагинов или строке `Done`.

## Установленные артефакты

| Плагин | Установленный файл | SHA-256 | SHA-512 | Размер |
| --- | --- | --- | --- | ---: |
| CopiMineEndEvent | `CopiMineEndEvent.jar` | `7026719d47ec3c7acd66bf3b3e77357782f6f71d54fe5684279b86c58d5e8ea5` (historical value for this 2026-10-22 snapshot) | `e3a1a739c6fbc955cb4807b6b8d9f0131c786211d1eab4e6b2c82ac040206946f343a9a14e1bc64ccf603d3c9687007bd4562703e99d8d19b60ecbf6465a3fb7` | 1898776 |
| CopiMineArtifacts | `CopiMineArtifacts.jar` | `75547398ec77fdc083c37de5a5d6449b166efb92c8af942ab0bddc5d675cf109` | `010ce63cf87d78226d02d04e277c693740c3bff4bb8110537900370f59f84c0975e1b2be24216fba67343e014028e6a06660c2263de2f9daa021a7410b473c71` | 363393 |
| CopiMineNarcotics | `CopiMineNarcotics.jar` | `d7e46fcc2bcfb8f31216cb1ea51c53bf3f492d29bc3b8c227540cb38e8ced7e2` | `dd4f6a90e8fa47d4c2bae9c9fa72ef23e856d72078376c959156b60693d3419abf636ae7d35a2fec21a713d8c4b2a6e50eb0064579e2e3333c0444f6c62048a4` | 372524 |

Три указанных JAR совпали с lock, официальным installer receipt и локальными файлами после штатного install/restart на момент снимка. Текущий установленный EndEvent и его hashes указаны в `CURRENT_HANDOFF.md`; `nativeVerified=false`.

## Открытые acceptance gates

| Gate | Результат |
| --- | --- |
| Подписанный V4 readiness | **Не готов.** Paper сообщает `SIGNER_UNAVAILABLE`, cause `CONFIGURATION_ABSENT`; `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE` отсутствует в process/user/machine environment. Существующий `narcotics-signing-secret.b64` проверен установленным loader против pinned V4 trust anchor и не совпал; ключ не применялся и не заменялся. Проверка source подтвердила статически заданный V4 public trust anchor без поддержанного isolated runtime test-anchor override; поэтому test-only keypair не создавался, так как текущая архитектура не может согласованно подключить его к Paper и Fabric client без изменения trust configuration. Подписи, production trust settings и ownership checks не ослаблялись. Автотесты V4 используют отдельные тестовые ключи и не являются live handshake. |
| Протокольные боты 2/5/10/11/20 | **Не выполнены.** AuthMe включён; одобренного V4 signer нет, а найденные live wrappers привязаны к отдельному legacy runtime `25566`, поэтому они не применялись к canonical runtime `25567`. Пароли не вводились, аутентификация/signature gates не обходились. |
| Recovery/cleanup с реальными участниками | Политики, startup/restart, lifecycle и cleanup probes прошли; реальные disconnect/restart/failure scenarios во время волн не запускались. |
| Нагрузочные замеры | Во время волн не проводились. Idle runtime sample имел 0 owned living mobs и 0 projectiles, поэтому не считается combat performance acceptance. |
| Ручной F2/video gate | Ожидает реальную Minecraft сессию и просмотр новых изображений/видео. Старые F2 материалы не заменяют это доказательство. |
| GitHub / CodeRabbit | Первый GitHub push run для `8e15640ce9167b4128fed71699acf1b2027f8f5d`: `migration-postgres-contract` **success**, `static-and-contract` **failure** на Pillow security audit, `java-plugins` **skipped**; это не acceptance. Pins и контрактные ожидания исправлены, оба строгих локальных audits и полный Pillow 13 Python suite прошли. CodeRabbit review текущего полного diff: **0 issues**, исключены только 3 неподдерживаемых бинарных файла. Новый corrective commit и exact-SHA GitHub rerun ещё ожидают. |

Исходный Git HEAD и origin до первого commit совпали: `88cd812a62fff626a760a6c00e4ea351d9bcc3e0`. Первый push — `8e15640ce9167b4128fed71699acf1b2027f8f5d`; после fix Pillow локально подготовлен corrective commit, который получит новый exact-SHA GitHub run.
