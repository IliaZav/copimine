# Развёртывание End Rift на production — 2026-11-19

Развёртывание выполнено по явному указанию владельца после того, как он
проверил SSH-доступ. Использован штатный порядок из
`docs/end-rift/final-26.3/REMOTE_PLUGIN_DEPLOYMENT_HANDOFF.md`: остановка
службы, проверяемая резервная копия, атомарная замена JAR, холодный запуск,
повторный холодный запуск и проверка через RCON.

## 1. Источник

* Ветка: `codex/rift-guardian-merge-20261117`.
* Коммит: `a8fb5c1fd2214e6d825dcf6138c7cb3cd0f26cd9`.
* CI: run `44472626414` — `static-and-contract`,
  `migration-postgres-contract` и `java-plugins` завершились успешно.
* Локальный gate: exit 0, `End Rift current local checks passed.`
* Полный `pytest`: 3708 passed, 23 skipped, 0 failed.

## 2. Целевой инстанс

| Параметр | Значение |
|---|---|
| Хост | `server-rpgrp` (`90.188.117.117:2222`) |
| Служба | `copimine-game.service` |
| Рабочий каталог | `/opt/copimine-game/minecraft/server` |
| Платформа | Paper 26.3, Java 25, порт `25565` |
| Игроков до остановки | 0 / 50 |

Служба описана как `CopiMine Paper test runtime (non-event integration)`;
фактически это единственный подключённый Paper-инстанс, который владелец
назвал production. Других запущенных Minecraft-служб на хосте нет.

## 3. Резервная копия и откат

Каталог (root-only, режим 700):

`/opt/copimine-backups/end-rift-20261119-a8fb5c1f`

Содержимое:

* `CopiMineEndEvent.jar.old` — 2562871 Б,
  `de443f35bde0d6d2c25ebbee6b956e64bb0c57d4c780ce5f1be188b2bcfc3019`;
* `config.yml.old`, `server.properties.old`, `spigot.yml.old`,
  `bukkit.yml.old`, `event-state.yml.old`, `event-layout.yml.old`,
  `deposit-journal.tsv.old`;
* `plugin-data.tar.gz` — данные плагина;
* `copimine.dump` — custom-format дамп PostgreSQL `copimine`;
  проверен `pg_restore --list` (795 записей);
* `SHA256SUMS`, `installation.json`, `service-before.txt`, `pre-stop.txt`.

Откат: остановить `copimine-game.service`, вернуть
`CopiMineEndEvent.jar.old` на место `CopiMineEndEvent.jar` с владельцем
`copimine-game:copimine` и режимом `644`, запустить службу. Мир и базу не
восстанавливать поверх более нового прогресса игроков.

## 4. Установленные артефакты

| Артефакт | Было | Стало |
|---|---|---|
| `plugins/CopiMineEndEvent.jar` | 2562871 Б / `de443f35...` | **2582610 Б / `987f31a7...`** |
| `CopiMineClient` JAR | 30247649 Б / `3421122b...` | **30252288 Б / `fbb3e3e5...`** |
| `CopiMineResourcePack-26.3` | 34373458 Б / `98d79505...` | без изменений |

JAR плагина установлен атомарно: `.new` → `sync` → `mv -f` → `sync`;
владелец `copimine-game:copimine`, режим `644`. Итоговый SHA-256 совпал с
закреплённым кандидатом. `/reload` не использовался.

Клиент опубликован как неизменяемый файл:

`https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-fbb3e3e5dad74fa4.jar`

Проверено скачивание по HTTPS: 30252288 Б, SHA-256
`fbb3e3e5dad74fa4e10a493e0f91fa7b76d25f78beed64cee76be5dcc361780a`.

Ресурс-пак не менялся и уже был опубликован; `server.properties` указывает
на `CopiMineResourcePack-26.3-98d7950558e7da38.zip` с SHA-1
`03951b4e43b5564b4d45ea71b07235997ff2bbd7`. Файл на сайте совпадает по
размеру и SHA-256 с локальной сборкой `build/minecraft-26.3/CopiMineResourcePack-26.3.zip`.

## 5. Проверка запуска

Первый запуск после замены:

* `CopiMineEndEvent` загружен и включён, `v0.1.0`;
* `End Event signed V4 bridge registered`;
* `CopiMineEndEvent services ready; phase=COLLECTING`;
* `Done (43.216s)`;
* `ERROR`/`SEVERE` по EndEvent в свежем журнале нет.

Второй, штатный холодный перезапуск:

* `Done (45.076s)`;
* `cmend status`: `state=COLLECTING`, событие
  `6294666a-c67d-4b40-82b4-d01bcc8706a0`, generation `408`, `wave=0`,
  `event-mobs=0`, `boss=none`, `endUnlocked=false`;
* `cmend debug invariants`: `failures=0`, `countersNonNegative=true`,
  `entityCount=4`, `tasks=0`, `wave7Blocks=0`;
* RCON `list`: 0 / 50 игроков;
* установленный SHA-256: `987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.

Имена ресурсов в выводе `cmend status` содержат русский текст в кодировке
консоли; это косметика RCON, а не ошибка состояния.

## 6. Границы доказательств

Статус: **PRODUCTION-INSTALLED, STARTUP-OBSERVED**. Установленные байты,
владелец, режим, два холодных запуска, включение плагина, V4-мост,
инварианты и состояние события подтверждены.

Не подтверждено: реальный игровой прогон волн 1–7, бой с боссом, визуал
Echo/Guardian/Kagune, звук, нагрузка на 2/5/10/11/20 игроков, MSPT/TPS и
клиентский FPS. На сервере не было игроков. Поэтому `nativeVerified`
остаётся `false`, а визуальные и боевые пункты — NOT VERIFIED.
