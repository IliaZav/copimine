# End Rift Event — инструкция текущего запуска

Документ описывает только текущий schema 4 flow из
`docs/superpowers/specs/2026-09-10-end-rift-event-v3-final.md`. Запускать его
нужно на локальном или staging Paper-сервере. Production не используется для
проверки.

Цели волн по порядку: `RIFT_CARRIERS`, `RIFT_HUNT`, `RIFT_GATES`,
`OBELISK_ASSAULT`, `BLACK_FOG`, `COLLAPSE_RINGS`, `REALITY_SPLIT`.

## Команды

Команды ниже требуют OP или соответствующее permission.

Core и состояние:

- `/cmend status` — текущая фаза, generation, Core и состав участников.
- `/cmend core set <игрок>` — сделать наведённый реальный блок Core и создать пять рун.
- `/cmend core info` — координаты Core и состояние ресурсов.
- `/cmend core rebuild` — восстановить event-визуал Core и рун из сохранённого состояния.
- `/cmend core remove confirm` — запросить снятие Core и закрыть текущую попытку.

Арена:

- `/cmend arena pos1` и `/cmend arena pos2` — задать углы.
- `/cmend arena info` — показать сохранённую область.
- `/cmend arena clear confirm` — очистить только временную разметку арены.
- `/cmend arena border <секунды>` — показать частицами границу на заданное время.

Ресурсы и ритуал:

- `/cmend resources status` — показать внесённые и требуемые предметы.
- `/cmend resources add <MATERIAL> <amount>` — внести предметы в Core.
- `/cmend resources reset confirm` — удалить только накопленный прогресс ресурсов.
- `/cmend ritual start` — начать проверку рун.
- `/cmend ritual cancel confirm` — отменить удержание рун.
- `/cmend ritual cleanup confirm` — убрать текущие ритуальные визуалы.
- `/cmend ritual reset confirm` — сбросить прогресс события с safety-check.
- `/cmend ritual unlock confirm` — открыть следующий доступ после подтверждённой победы.

Проходы Wave 3:

- `/cmend gate pos1`, `/cmend gate pos2` — задать углы прохода.
- `/cmend gate setat <x1> <y1> <z1> <x2> <y2> <z2>` — задать проход из локальной консоли.
- `/cmend gate info` — показать сохранённые точки и состояние.
- `/cmend gate preview` — визуально показать слои.
- `/cmend gate open [ticks-per-layer]` — открыть проход анимацией.
- `/cmend gate close [ticks-per-layer]` — закрыть проход анимацией.
- `/cmend gate restore confirm` — вернуть исходные блоки из журнала.
- `/cmend gate delete confirm` — удалить описание прохода и временные блоки.

Портальная комната:

- `/cmend portalroom set` — сохранить текущую точку портальной комнаты.
- `/cmend portalroom info` — показать её координаты.

Диагностика и disposable test:

- `/cmend wave spawn <1-7>` — создать одну тестовую волну без официальной награды.
- `/cmend wave clear` — удалить event-owned тестовые волны.
- `/cmend boss spawn [official confirm]` — создать тестового или официального босса.
- `/cmend boss info` — показать реальное здоровье и фазу.
- `/cmend boss damage <n>` — нанести тестовый урон через серверную политику.
- `/cmend boss phase <awakening|hunt|rift|overload|rage|last_seal>` — выбрать фазу для disposable проверки.
- `/cmend boss freeze` и `/cmend boss unfreeze` — заморозить только локальную диагностическую сущность.
- `/cmend boss kill cleanup` — удалить тестового босса и event-owned связанные объекты.
- `/cmend boss kill simulate-victory confirm` — пройти disposable ветку выдачи победы.
- `/cmend boss spell <void_blast|rift_projectile|rift_arrows|void_mark|summon_servants|arena_inferno|final_strike>` — запустить одну проверку способности.
- `/cmend client status` — состояние client bridge.
- `/cmend client bindboss [player]` и `/cmend client clear [player]` — локальная привязка визуального теста.
- `/cmend debug <packets|objectives|hazards|perf|ai|trace on|off|status>` — включить диагностический вывод.
- `/cmend test run <creative|wave|scene clear|diagnostics fail|ai|boss|teleport|visuals|music>` — запустить disposable тестовый сценарий.

## Подготовка

1. Убедиться, что мир арены — `CopiMine`, а сервер запущен с текущим plugin JAR.
2. Выполнить `/cmend arena info`. Если арена уже сохранена, не очищать её и не
   создавать заново: карта пользователя сохраняется.
3. Навестись на нужный блок, выполнить `/cmend core set <игрок>`, затем проверить
   `/cmend core info` и наличие пяти рун на полу.
4. Внести четыре типа ресурсов через `resources add` и сверить `resources status`.
5. Поставить игроков на разные руны. Ритуал считается готовым только после
   пяти секунд непрерывного удержания без дубликатов.

## Что происходит по волнам

Wave 1 — носители разлома, кольцевая волна от Core, постепенный spawn и reward
на Core.

Wave 2 — охота: цели выбираются из живых участников, target rotation bounded,
натуральные мобы не вовлекаются.

Wave 3 — три портала/прохода, по две pushing-цели в группе, безопасное открытие и
закрытие слоями. Блоки проходов временные и восстанавливаются из журнала.

Wave 4 — до шести обелисков по pressure profile. Их нельзя ломать оружием.
Каждый активный столб пульсирует кольцом 5 блоков и создаёт Rift Fireball.
Повредить столб можно только отражённым event-снарядом, три попадания разрушают
его. Снаряд не ломает арену и не наносит урон боссу.

Core Restoration — волновая награда и восстановление event-owned изменений,
идемпотентно через сохранённый marker.

Wave 5 — три цикла чёрного тумана: telegraph, безопасное окно, freeze и
постепенное возвращение поля.

Wave 6 — пары и локальные кольца. У каждой пары свой deadline и относительное
вращение; состояние соседней пары не влияет на текущую.

Wave 7 — reality split: комнаты изолированы по целям, pathfinding, projectile,
AoE и teleport до открытия прохода.

После Wave 7: 20 секунд `PRE_BOSS_COOLDOWN`, затем boss cinematic и реальный
босс. Его фазы: `AWAKENING`, `HUNT`, `RIFT`, `OVERLOAD`, `RAGE`, `LAST_SEAL`.
В `LAST_SEAL` появляются постоянные серверные щупальца с grab/hold/throw
анимациями. После смерти босса идут finish, выдача персональных наград и
`UNLOCKED`; Core сохраняется как памятный объект, если это предусмотрено
сценой, но event-owned временные сущности удаляются.

## Награды

Каждый официальный участник получает свой `rift_core_shard`. Roll `night_cloak`
делается отдельно для каждого участника с вероятностью 30% и сохраняется до
выдачи. Повторная попытка после рестарта не меняет результат. Убийство или
ручное удаление event-owned сущностей не должно создавать дополнительный loot.

## Очистка

При reset, удалении Core, аварийной остановке, смене generation, disable или
restart нужно убедиться, что исчезли мобы, босс, обелиски, fireball, displays,
порталы, щупальца, частицы-задачи и временные блоки. Для проверки использовать
`/cmend status`, `/cmend debug hazards`, логи и повторный запуск локального
сервера. Незакрытый hazard journal означает `RECOVERY_REQUIRED`; продолжать
событие до восстановления нельзя.

## Тестовый порядок

Сначала запускать unit/contract gate, затем сборку plugin, client и resource
pack. После этого — Paper smoke и disposable wave tests. Полное прохождение
проводить минимум для 2 игроков; отдельные pressure/scaling проверки — для 3,
10 и 20 игроков. Если runtime или визуальный этап фактически не проводился,
в отчёте писать `NOT VERIFIED`, а не заменять его статическим grep.
