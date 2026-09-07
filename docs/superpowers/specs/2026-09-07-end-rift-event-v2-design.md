# CopiMine End Rift Event V2 — подробная спецификация реализации

**Статус:** финальный дизайн по результатам обсуждения 2026-09-07; документ готов к проверке владельцем проекта. До отдельного одобрения этого файла не начинать реализацию.

**Ветка:** `codex/end-rift-event`

**Главная цель:** заменить текущую версию End Rift Event на цельный шестихвильный рейд + Boss V2, который честно масштабируется от 2 до 20 игроков, остаётся полностью сервер-авторитетным по геймплею, использует клиентский мод для качественных 3D/VFX, переживает рестарты без дюпов/грязных блоков и не объясняет боевые механики техническим текстом поверх HUD.

## 0. Как этот документ соотносится со старыми спеками

Этот файл является новым V2-контрактом и **замещает** старые End Rift gameplay/visual решения там, где они конфликтуют с ним:

- `docs/superpowers/specs/2026-08-17-end-rift-event-design.md`
- `docs/superpowers/specs/2026-08-24-end-rift-completion-design.md`
- `docs/superpowers/specs/2026-08-31-end-rift-boss-visuals-design.md`

Из старых документов сохранить без изменения следующие архитектурные принципы, если ниже явно не сказано обратное:

1. Paper-сервер является единственным источником истины для урона, HP, фаз, целей, наград, смерти, победы и открытия End.
2. `CopiMineWorldCore` остаётся единственным владельцем флага доступа в End. Использовать typed API `WorldAccessService`, не дублировать `end_locked` и не дергать команды как внутренний API.
3. `CopiMineArtifacts` остаётся единственным владельцем официальных артефактов, уникальных экземпляров, PDC-аутентичности и durable pending delivery. End Event не создаёт «официальный» предмет вручную по lore/name.
4. Состояние события сохраняется crash-safe: temp write + fsync + atomic replace/backup через существующий `EventStateStore`.
5. Все scheduled задачи принадлежат generation и отменяются общим `EventTaskRegistry`.
6. Все сущности/дисплеи/снаряды события должны иметь event/generation/kind tags и удаляться только как собственность текущего/устаревшего поколения.
7. Лаунчер, сайт, production server/world/database не входят в эту работу.

## 1. Что сейчас есть в репозитории и что требуется изменить

### 1.1. Сервер

Текущий entrypoint `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java` уже содержит почти весь gameplay, визуалы, команды, boss logic и wave logic в одном огромном классе. V2 **не реализовывать добавлением ещё нескольких тысяч строк в этот класс**. Entry point может остаться фасадом/композицией сервисов, но новые механики обязаны уходить в отдельные контроллеры и чистые policy-классы.

Текущая state machine знает только `WAVE_1..WAVE_5`, после `WAVE_5` сразу разрешает переход к boss cinematic/active. V2 требует `WAVE_6` и отдельную 20-секундную pre-boss фазу.

Текущий `WaveScalingPolicy` добавляет минимум +6 мобов уже на двух игроков и растит общее количество к hard cap. Для V2 это неподходящая стратегия: сложность должна расти через pressure budget, HP объектов, количество одновременных угроз и частоту механик, а не через десятки мобов.

Текущий `WaveMechanicsPolicy` содержит старые Tower Defense/Rift Storm и масштабирует порталы до 3–6. V2 использует ровно 3 портала, полностью удаляет старые Tower Defense/Rift Storm механики и заменяет Wave 4/5.

Текущий boss использует `BossVirtualHealthPolicy` и физическую проекцию HP. V2 должен перейти на настоящее HP entity.

### 1.2. Клиент

`CopiMineClient` сейчас умеет:

- entity/boss bindings;
- boss phase/bar state;
- control/reverse-movement state;
- полноэкранные visual/post-process эффекты;
- procedural Java-модель текущего Rift Guardian.

Но в протоколе нет world-space VFX объектов (луч, линия, декаль, цепь, трещина, энергетическая дуга), а `ClientVisualManager` в основном рисует fullscreen HUD/post-process. Для V2 нужен отдельный world-space renderer и расширение протокола.

### 1.3. Artifacts

`EventArtifactRewardService` уже является правильной durable boundary для event-наград. Использовать её для `rift_core_shard` и `night_cloak`. Идемпотентные ключи формировать до выдачи, результат chance-roll для Night Cloak сохранять до обращения к Artifacts.

`rift_core_shard` уже есть в `copimine-artifacts/items.yml`, но сейчас имеет старое имя/lore и cooldown 3600. Это изменить по разделу наград ниже.

## 2. Неподлежащие компромиссу инварианты

1. Ивент должен быть **реально проходим вдвоём** без механики, математически требующей третьего игрока.
2. Ивент при 10 игроках должен оставаться напряжённым и требовать распределения ролей, но не превращаться в 50+ мобов и ваншоты.
3. Геймплей авторитетен на сервере. Клиентский VFX никогда не решает, был ли hit, кто цель, сколько HP, когда закончилась фаза.
4. Ни одна визуальная система не должна создавать O(area × players × ticks) сущности/частицы.
5. Во время боя не показывать названия заклинаний, внутренние статусы, проценты, точный технический таймер и подсказки вида «отойдите от метки». Игрок читает механику глазами и звуком.
6. Все block mutation (emerald safe zones, Barrier, ice prison и любые реальные временные блоки) должны быть точно восстановимы при wave end, wipe, plugin disable и restart.
7. Если живых участников попытки стало 0, попытка проиграна полностью: очистка → стартовые руны → снова Wave 1. Ресурсы Core не теряются.
8. Смерть одного игрока при наличии живых товарищей не перезапускает ивент. Он может сразу нажать vanilla Respawn и вернуться.
9. Урон обычных event-мобов и их специальных атак снижается на 4 raw HP относительно текущего баланса; Boss V2 не подпадает под это глобальное вычитание.
10. Любая иммунность (Elite shield, boss shield, cinematic) должна иметь очевидный визуальный feedback, чтобы «0 урона» никогда не выглядел как баг.

## 3. Целевая state machine

Рекомендуемый набор верхнеуровневых фаз:

```text
UNCONFIGURED
COLLECTING
READY_FOR_PLAYERS
START_RITUAL
WAVE_1
INTERMISSION_1
WAVE_2
INTERMISSION_2
WAVE_3
INTERMISSION_3
WAVE_4
INTERMISSION_4
WAVE_5
INTERMISSION_5
WAVE_6
PRE_BOSS_COOLDOWN
BOSS_CINEMATIC
BOSS_ACTIVE
BOSS_FINISH
VICTORY_PROCESSING
UNLOCKED
RECOVERY_REQUIRED
```

Старые `FINAL_DRAIN`, `FINAL_RITUAL`, `FINAL_WAVE`, `ABSORPTION`-ориентированные ветви старого boss-flow не использовать в новой официальной попытке. Их можно временно оставить только для чтения старых snapshot/совместимости миграции, но они не должны быть достижимы новым V2 path.

### 3.1. Переходные руны

После каждой Wave 1–5 следующая волна **не начинается автоматически**.

- Появляется количество рун, равное числу живых/активных официальных участников, которых ожидаем для продолжения.
- Каждый участник занимает одну уникальную руну.
- Только когда все нужные руны заняты одновременно, запускается 5-секундный hold.
- Если кто-то сошёл, умер, вышел из мира или отключился — hold сбрасывается.
- Никакого ActionBar `5,4,3...`.
- Прогресс показывается заполнением рисунка руны, нарастающим свечением и beam в Core.
- После успешного hold руны полностью исчезают и пол остаётся исходным.

После Wave 4 эти руны размещать по внешнему краю арены, чтобы игроки **сами** дошли туда и Wave 5 начала всех снаружи Ring 1. Никаких принудительных teleport.

После Wave 6 рун нет: начинается safe cooldown 20 секунд.

### 3.2. Респавн и отключения

Официальный roster фиксируется при успешном стартовом ритуале попытки. Для live-attempt отдельно хранить:

- roster UUID;
- alive/dead status;
- online/offline;
- current chamber (Wave 6);
- attempt generation.

`PlayerDeathEvent` должен после обновления статуса проверять `livingActiveParticipants == 0`.

Если 0 → вызвать единый idempotent `performAttemptWipe(reason)`.

Если >0 → попытка продолжается. Умершему не ставить искусственный respawn timer. После `PlayerRespawnEvent` вернуть его в безопасную точку текущего encounter. В Wave 6 — в его текущую камеру/безопасный spawn этой камеры.

Quit не должен мгновенно считаться смертью, если это обычный краткий reconnect; однако нельзя оставлять попытку навсегда «живой» из-за offline UUID. Реализовать небольшой reconnect grace (конфиг, например 20–30 сек) либо считать offline участника не живым для wipe после разумного grace. Это должно быть детерминировано и тестироваться.

## 4. Wipe / crash recovery

### 4.1. `performAttemptWipe`

Один централизованный путь, никаких wave-specific «частичных reset»:

1. Закрыть текущую generation для новых callback.
2. `taskRegistry.cancelAll()`.
3. Удалить event-owned mobs/elites/boss/tentacles/projectiles/displays текущей generation.
4. Очистить safe-zone/fog/ring/chamber VFX.
5. Восстановить все journaled реальные блоки (Barrier, emerald, ice, gate-like temporary blocks) по исходному `BlockData`.
6. Снять event-owned potion/control states, target locks, prisoner/chamber state.
7. Очистить live wave progress.
8. Сохранить, что Core всё ещё charged и deposited resources остаются.
9. Увеличить generation/attempt generation.
10. Перевести в `READY_FOR_PLAYERS` и пересоздать только стартовые руны.

Повторный вызов wipe должен быть безопасен.

### 4.2. Restart во время боя

Не пытаться «продолжить» с середины Fog, Ring 2 или Tentacle grab. Это слишком много недетерминированного transient state.

При загрузке snapshot, если сохранена любая активная Wave 1–6 / PRE_BOSS / BOSS phase:

- убрать stale generation-owned content;
- восстановить journaled blocks;
- сохранить ресурсы Core;
- перейти к `READY_FOR_PLAYERS`;
- новая попытка начинается с Wave 1.

Это соответствует gameplay wipe semantics и безопаснее частичного resume.

### 4.3. Схема snapshot

Поднять `schema-version` (рекомендация: 2). Сохранить совместимость чтения старой schema.

Добавить durable поля минимум для:

- V2 phase;
- current generation/attempt id;
- charged resources;
- official roster;
- per-player reward issue statuses для wave/boss;
- per-player Night Cloak roll result (не только issued status);
- shard successful-use cooldown;
- Abyss Anchor cooldown per player;
- victory/unlock steps.

Не нужно сохранять точную позицию каждого моба, fog cells, ring animation angle, текущую стрелу, active tentacle animation и прочий transient combat state.

## 5. Общая система баланса

### 5.1. Урон мобов

Создать единый `EventMobDamagePolicy` и перестать разбрасывать корректировку урона по десяткам мест.

Для всех **не-Boss** event damage sources:

```text
newDamage = max(minimumDamage, oldEffectiveDamage - 4.0)
```

`minimumDamage` по умолчанию 1.0 raw HP для обычного удара, если механика не является purely displacement/control.

Policy применяется к:

- common mobs;
- elites;
- W5 guards/elite;
- W6 room enemies;
- event arrows/projectiles;
- mini-abilities этих врагов.

Не применять к:

- Boss V2 attacks;
- Wave4 scripted HP clamp;
- Wave5 prisoner drain;
- unavoidable displacement tentacle (у неё отдельный low/zero damage contract).

Не делать двойное вычитание: если базовый attribute уже уменьшен на 4, специальный EntityDamage event не должен снова уменьшать тот же hit.

### 5.2. Pressure budget вместо mob multiplication

Не использовать старую формулу `configured + 6` для duo.

Первый тестовый профиль одновременного давления:

```text
2 players:   4 common + 1 elite max, 1 heavy mechanic
3-4:         5-6 common + 1 elite, 1 heavy
5-6:         7 common + 1-2 elite, 1-2 heavy
7-8:         8 common + 2 elite, 2 heavy
9-10:        9-10 common + 2 elite, 2 heavy
11-15:       11 common + 2-3 elite, 2 heavy
16-20:       12 common + 3 elite, 3 heavy
```

Это **одновременно живые/активные**, а не обязательно total wave kills. Длинная волна может подавать несколько пакетов через этот cap.

### 5.3. Target pressure cap

При наличии альтернатив максимум примерно 2–3 обычных врага должны одновременно жёстко фокусить одного человека. Для W5/W6/Boss guards использовать свои caps.

Масштабировать сложность ростом:

- HP приоритетных целей;
- числом одновременно активных механизмов;
- cast frequency;
- multi-target count;
- количеством persistent tentacles;
- количеством safe zones/их размером;
- chamber-specific enemy profile.

Не масштабировать сильно raw damage.

## 6. Wave 1 — Носители Разлома

### 6.1. Цель

Обучить: найти приоритетную цель → убить → физически доставить энергию к Core.

### 6.2. Flow

1. Спавнится умеренная пачка common mobs.
2. Ровно один живой моб помечается `RIFT_CARRIER`.
3. Carrier получает:
   - заметную ауру;
   - непрерывный вертикальный/связующий beam;
   - увеличенное HP;
   - более активное repositioning.
4. Carrier погибает → создаётся один server-authoritative charge pickup/state.
5. Игрок подбирает/получает charge и должен подойти к Core примерно за 10 сек.
6. Успел → Core принимает 1/3.
7. Не успел → charge не исчезает; энергия визуально перелетает в другого живого event-моба, который становится Carrier.
8. Ровно 3 доставленных charge завершают wave.

### 6.3. Scaling

Objective всегда 3 charges.

Duo: примерно 1 Carrier + до 3–4 common одновременно.

10 players: 1 Carrier + до 8–9 common. Carrier HP примерно 2.0–2.3 от duo baseline, чтобы 10 игроков не удаляли его мгновенно.

### 6.4. VFX

Carrier читается без ActionBar. Beam — continuous world-space renderer, не линия из частиц. После смерти beam/energy переносится к charge.

## 7. Wave 2 — Охота / Метка Разлома

### 7.1. Flow

- Один живой игрок получает `RIFT_MARK`.
- Основная часть pack агрится на marked, но target pressure cap сохраняется.
- Остальные враги распределяются по другим участникам.
- Метка меняется после заданного интервала; не выбирать того же игрока подряд, если есть альтернативы.
- Рекомендовано 3 полноценных hunt cycles.
- Если marked умер, метка немедленно переходит следующему допустимому игроку.

### 7.2. VFX

Не отправлять `Метка Разлома: N сек.`.

Над/вокруг цели world-space glyph/ring. Ближе к смене состояния glyph ускоряется, трескается или меняет интенсивность.

## 8. Wave 3 — Врата Разлома

### 8.1. Порталы

Всегда **ровно 3** портала, последовательно:

```text
Portal 1 → Portal 2 → Portal 3
```

Повторно использовать чистую 5-секундную capture math из `PortalCapturePolicy`, если она не конфликтует с новым flow.

Только текущий портал активен для захвата.

### 8.2. Knockback defenders

Удалить старый подход, где Wave3-модификатор применяется всем защитникам.

В каждой defending pack:

- ровно 2 сущности получают роль `PORTAL_PUSHER`;
- только они получают дополнительный knockback;
- они визуально отличаются рифт-аурой/руками/контуром;
- остальные common mobs **не получают bonus knockback**.

При duo pack всё равно может иметь 2 pushers, но их общий pressure/cooldown должен быть мягче, чтобы два игрока не были бесконечно выбиты из capture zone.

### 8.3. 3D portal bug

Текущий визуал из FRAME/INNER/SHARD `ItemDisplay` на некоторых клиентах выглядит как чёрно-фиолетовая масса. Не пытаться чинить это только заменой пары частиц.

Требуемый путь:

1. Проверить custom model data 830007/830008/830009, JSON model references, textures и фактическую ориентацию `ItemDisplay`.
2. Сделать портал пространственно читаемым: толстая внешняя рама, глубина, отдельная внутренняя плоскость/поверхность Разлома, вращающиеся shards.
3. Frame и inner не должны лежать почти в одной плоскости с одинаковым scale, иначе Z-fighting/визуальная каша.
4. Inner rift должен иметь собственную depth offset и animation.
5. При capture внешний вид постепенно стабилизируется/схлопывается.
6. После capture портал сжимается внутрь и удаляется.
7. Добавить ручной screenshot acceptance test на реальном клиенте. Протокольный/unit test не считается доказательством красивого 3D.

Не показывать `Порталы X/Y`. Состояние должно читаться самим порталом.

## 9. Wave 4 — Чёрный Туман

### 9.1. Общий ритм

Wave 4 — длинная волна:

```text
Combat I
Safe Zones I (4 sec)
Fog I (3 sec)
Combat II
Safe Zones II (4 sec)
Fog II (3 sec)
Combat III
Safe Zones III (4 sec)
Fog III (3 sec)
Final cleanup
```

Чтобы сильная группа не пропустила механику за секунды, боевые сегменты должны иметь минимальный encounter time и/или required kills. Первый tuning ориентир: 40 / 50 / 60 секунд, но вынести в config.

### 9.2. Safe zones

Количество первой серии:

```text
first = min(5, ceil(players / 2.0))
second = max(1, first - 1)
third = max(1, second - 1)
```

Размеры:

```text
I: 3x3
II: 2x2
III: 1x1
```

Каждая зона принимает максимум 2 UUID.

При двух игроках всегда 1 зона, только размер уменьшается 3x3 → 2x2 → 1x1.

При 10 игроках 5 → 4 → 3 зон, то есть вместимость 10 → 8 → 6.

### 9.3. Реальные блоки и lock

- Пол зоны временно меняется на `EMERALD_BLOCK`.
- Сохранять исходный BlockData через bounded mutation journal.
- Над зоной зелёные VFX и непрерывный вертикальный beam.
- Как только в зоне 2 игрока, фиксировать их UUID.
- Вокруг зоны поднимается Barrier boundary, не позволяющая третьему войти.
- Barrier тоже journaled/owned и гарантированно снимается.
- Для 1x1 зоны два зафиксированных occupant не должны физически вытолкнуть друг друга; использовать временное team collision rule или серверные slot offsets, не global noclip.

### 9.4. Fog impact

Safe zones существуют 4 сек. Затем начинается 3-секундный fog.

Fog:

- покрывает арену примерно от пола до высоты 3 блоков;
- внутри объёма safe-zone fog отсутствует;
- вокруг safe-zone настолько плотная стена, что находящиеся внутри практически не видят внешнюю арену;
- не создавать частицу в каждом блоке.

Unsafe player на момент impact:

1. Если HP > 4.0 → установить HP = 4.0.
2. Если HP <= 4.0 → оставить как есть; не лечить и не добивать clamp-ом.
3. Blindness 15 sec.
4. Slowness III 4 sec.
5. Wither I 4 sec.

Safe occupants:

- не получают clamp/debuff;
- получают healing, default `Regeneration II` на fog duration, tuning через config.

### 9.5. Freeze combat

На все 3 секунды fog:

- event mobs stop AI/targets/movement;
- Enderman teleport запрещён;
- melee/casts не запускаются;
- event arrows/projectiles либо удаляются, либо полностью freeze так, чтобы не прилетели во время паузы;
- не трогать unrelated world mobs вне события.

По окончании 3 сек:

- fog VFX clear;
- emerald/restored floor;
- Barrier clear;
- zones clear;
- mobs resume.

### 9.6. Производительность fog

Реализовать fog как bounded visual field:

- крупная sparse lattice/слои;
- больше плотности возле камеры/границы safe-zone;
- client-assisted fog/volumetric overlay, если клиент поддерживает;
- server fallback viewer-scoped;
- не создавать Display на каждую клетку;
- не делать nested loops arenaArea × everyTick × everyPlayer.

## 10. После Wave 4 — зелёное восстановление Core

До появления внешних рун Wave5 Core делает 5–6-секундный restoration beat.

Gameplay:

- living official participants heal to current max HP;
- снять только event-owned negative effects, не произвольные эффекты других плагинов;
- каждый damageable item в main inventory, hotbar, armor и offhand ремонтируется на **40% от max durability**, cap до полностью целого;
- контейнеры/шалкеры внутри inventory не обходить рекурсивно;
- если участник ещё на death screen, записать pending restoration и применить после его respawn в intermission.

Repair formula:

```text
repairAmount = ceil(maxDurability * 0.40)
newDamage = max(0, oldDamage - repairAmount)
```

VFX: Core зелёный, rings/glyphs, continuous green beams к игрокам, мелкие sparks по экипировке. Никакого текста «починено 40%».

После завершения появляются руны **по краю арены** для Wave5.

## 11. Wave 5 — Кольца Коллапса

### 11.1. Геометрия

Три концентрических вращающихся `BlockDisplay` ring.

Рекомендованные радиусы:

- outer ~16–18;
- middle ~10–12;
- inner ~6–7.

Каждое кольцо ~20–24 displays, суммарно ориентир 60–72. Не создавать сотни display entities.

Визуал может вращаться/плавать/наклоняться, но gameplay boundary — отдельный стабильный server radial rule.

Кольцо **не останавливает вращение**, когда открывается gap. Gap — фиксированный server passage; сегменты визуально скрываются/переносятся в его сектор, чтобы проход всегда оставался свободным.

### 11.2. Общий flow

```text
outer defense
→ Ring 1 opens
→ Ring 1 combat
→ Ring 1 collapse
→ inter-ring pack
→ Ring 2 opens
→ paired guards
→ Ring 2 collapse
→ inter-ring pack
→ Ring 3 opens
→ Core trap/prisoner
→ 3 Guards + Elite
→ Ring 3 collapse
→ Wave 5 complete
```

Мобы ring зоны не могут path/teleport/стрелять за свою логическую границу, пока passage не открыт.

### 11.3. Ring 1

Несколько последовательных умеренных packs common enemies.

По прогрессу ring визуально:

- трещит;
- даёт arcs;
- ускоряет/нарушает rotation;
- отдельные blocks смещаются.

После зачистки ring не исчезает мгновенно, а visibly падает/разрушается.

### 11.4. Ring 2 — парные стражи

Два усиленных Guard на противоположных сторонах.

- один melee/teleport;
- один ranged.

Когда первый убит → открыть примерно 10-секундное окно.

Если второй убит внутри окна → success.

Если нет → первый reconstruct с 30–40% HP.

Никакого полного reset обоих.

### 11.5. Ring 3 — скрытая ловушка Core

Рядом с Core появляется одна необычная руна.

**Не объявлять заранее, что это ловушка или жертва.**

Первый игрок, который сам наступил на руну:

- становится `CORE_PRISONER`;
- переносится на заранее безопасную точку на/у Core;
- вокруг формируется ice cocoon;
- остаётся замороженным до полного конца Wave5 final combat.

После срабатывания допустим один атмосферный title/звук, сообщающий факт захвата, но не механику цифрами.

Prisoner:

- movement locked;
- attack/use disabled;
- event mobs never select as target;
- event projectiles/AoE ignore;
- no suffocation/knockback/fire/environmental accidental kill from encounter;
- не считать его активным combat target при scaling финала.

Каждые 50 сек:

```text
drain = 3 HP (1.5 hearts)
minimum prisoner HP = 1.0 HP (0.5 heart)
```

Если текущий drain опустил бы ниже 1.0 — clamp к 1.0.

Каждый **успешный** drain, реально снявший HP, добавляет один bounded strength stack финальным врагам.

Когда prisoner уже 1.0 HP:

- больше HP не снимать;
- новые stacks не добавлять.

Prisoner никогда не умирает от этой механики.

### 11.6. Финал Ring3: 3 Guard + 1 Elite

Количество всегда ровно 4 сущности, не масштабировать count.

От каждого Guard к Elite — continuous beam/tether.

Пока жив хоть один Guard:

- Elite имеет absolute encounter shield;
- любой hit по Elite должен давать shield flash/sound/VFX;
- hit event explicit cancel allowed только здесь.

После смерти всех 3 → `SHIELD_BREAK`, Elite vulnerable.

### 11.7. Duo special case

Это обязательная acceptance requirement.

При 2 official players один может попасть в prison, остаётся 1 combatant.

Поэтому:

- все 3 Guard видимы и поддерживают tethers;
- полноценно атакует только 1 Guard одновременно;
- остальные максимум делают редкие безопасные telegraph/support действия;
- Guard HP ориентир 55–65% normal profile;
- Elite HP ориентир 70–75% normal profile;
- combat flow для survivor фактически Guard1 → Guard2 → Guard3 → Elite.

Не допускается ситуация solo survivor vs 4 одновременно атакующих elites.

При 10 players после prison остаётся 9: все 3 Guard могут быть полноценно активны, с разными target.

### 11.8. Освобождение

После смерти Elite:

- снять enemy strength state;
- crack/shatter ice;
- вернуть prisoner полный max HP;
- снять event negatives;
- вернуть movement/use;
- восстановить journaled ice blocks;
- collapse Ring3.

Затем переходные руны для Wave6.

## 12. Wave 6 — Раскол Реальности

### 12.1. Количество камер

```text
2 players → 2 chambers
3 players → 3 chambers
4–20 → 4 chambers
```

Не создавать четвёртую active chamber для 2/3 игроков.

### 12.2. Распределение

На старте Wave6 **телепорт является частью механики**.

- собрать living participants;
- стабильным round-robin/shuffle распределить максимально равномерно;
- разница room sizes <=1;
- внутри комнаты у каждого distinct prevalidated spawn point;
- spawn points не пересекаются и имеют безопасный floor/headroom;
- 10 players пример: 3/3/2/2.

### 12.3. Chamber isolation — жёсткий контракт

У игрока, mob, projectile и spell instance есть `chamberId`.

До открытия passage:

- AI candidates только `playersInChamber(id)`;
- `EntityTargetLivingEntityEvent` отменяет foreign target;
- path target не ставится за boundary;
- Enderman teleport outside room cancel;
- projectile crossing locked boundary remove/deflect;
- AoE получает room-local list;
- mob другой комнаты не «видит» игрока сквозь стену.

Когда комната побеждена, passage в ещё активную соседнюю открывается. Игрок физически проходит границу → его `chamberId` обновляется, после чего новая комната может атаковать его.

### 12.4. Room-local scaling

Не использовать total roster для HP комнаты. Использовать число игроков **в этой комнате на старте/при пересчёте допустимого профиля**.

Первый tuning:

```text
1 player: HP x0.70–0.80, attack cadence x0.80, max 1 heavy at once
2 players: baseline x1.00
3 players: HP x1.25, cadence x1.10
4 players: HP x1.45, cadence x1.15
5 players: HP x1.60, cadence x1.20
```

Не увеличивать raw damage по этой таблице.

### 12.5. Chamber A — Два Клинка

Два Elite Enderman: Blade и Shadow.

- один material/vulnerable и активно атакует;
- второй phased и создаёт давление;
- роли меняются примерно каждые 8 сек (tuneable).

Abilities:

- `VOID_RUSH`: line telegraph → dash по линии → knockup.
- `BACKSTAB`: VFX behind target → delay → teleport strike.
- `CROSS_CUT`: два противника на противоположных сторонах → пересекающиеся dashes.
- `MARKED_HUNT`: временный фокус, второй враг старается мешать другому игроку.

AI conditions:

- target far → Rush;
- stationary → Backstab;
- players clustered → Cross Cut;
- pressure stale/target rotation due → Hunt.

Не выбирать spell случайным `nextInt()` без контекста.

Solo profile: только один из пары полноценно давит одновременно.

После смерти одного второй остаётся material и входит в bounded rage.

### 12.6. Chamber B — Шквал

Один Elite Skeleton.

Abilities:

- `FAN_BARRAGE`: несколько вееров, baseline ~36 arrows.
- `ARROW_RAIN`: ~24–30 around current/predicted positions.
- `CROSSFIRE`: 3 firing positions, ~12 per step.
- `PIERCING_SHOT`: line telegraph 1–1.2 sec → heavy fast projectile/arrow + knockback.
- `ARROW_TEMPEST`: ultimate, до ~60 реальных стрел при 4–5 players.

Ultimate scaling:

```text
1 player: 24–30
2: 36–40
3: 45–50
4–5: up to 60
```

AI:

- close target → Rift Step back + Fan;
- clustered → Rain;
- stationary → Piercing;
- spread → Crossfire;
- ultimate ready and pressure budget permits → Tempest.

Нужен active-arrow budget и per-player short hit cooldown, чтобы 20 collision events одного tick не сложили игрока мгновенно.

### 12.7. Chamber C — Стая Разлома

Три специальных Spider:

**Weaver**
- Web Shot;
- Web Line/temporary control strip.

**Leaper**
- выбирает изолированного;
- Predator Leap с хорошо видимой landing mark.

**Venom**
- ranged fan;
- aimed venom shot.

Role targeting не позволяет всем троим бесконечно фокусить одного при доступных альтернативных целях.

Периодическая combo: Weaver slow → Leaper dive → Venom covers escape.

Solo profile: combo serial, не simultaneous unavoidable overlap.

После смерти одного оставшиеся получают небольшой bounded rage.

### 12.8. Chamber D — Гравитационный Страж

Один Rift Warden/Elite Enderman.

- `SINGULARITY`: dark sphere, нарастающее притяжение, затем outward blast.
- `RIFT_CHAINS`: tether 2 игроков; разорвать дистанцией. **Никогда не выбирать при solo.**
- `REPULSION`: punish cluster.
- `PHASE_CRUSH`: line telegraph → dash/knockup для stationary/wall target.

AI:

- cluster → Repulsion;
- wide spread → Singularity;
- suitable pair → Chains;
- stationary/wall → Phase Crush.

### 12.9. Финал Wave6

Когда последняя active room закончена:

- убрать enemies/projectiles/hazards;
- walls crack/dissolve;
- VFX energy goes to Core;
- phase → `PRE_BOSS_COOLDOWN`.

## 13. PRE_BOSS_COOLDOWN — ровно 20 секунд

20 секунд полностью безопасны:

- нет wave/boss mobs;
- нет damage hazards;
- выдать Wave6 reward;
- игроки лечатся/меняют gear/располагаются.

Не показывать numeric countdown.

Показывать:

- первые ~10 sec редкие pulse Core;
- затем чаще;
- fragments/rifts around Core;
- последние 3–4 sec музыка/звук затихает и идёт сильный cinematic build.

После deadline → Boss cinematic.

## 14. Boss V2 — real HP

### 14.1. Удалить virtual HP

В официальном V2 boss path нельзя использовать `BossVirtualHealthPolicy` как authoritative pool и нельзя cancel every accepted hit ради ручного subtraction.

Нужно:

- установить реальный `GENERIC_MAX_HEALTH` entity;
- реальный current health;
- BossBar = realHealth / realMaxHealth;
- обычный разрешённый melee/projectile hit проходит Paper damage pipeline.

Cancel incoming boss damage только при явных состояниях:

- shield final phase;
- cinematic/invulnerable transition;
- другой явно задокументированный иммунитет.

Каждый cancel визуально отражается shield effect.

### 14.2. Server health ceiling

До спавна official boss проверить, что server/Paper max health ceiling допускает максимальный target. Требуемое значение минимум 20 000; рекомендуемый ceiling 50 000 для запаса.

Если ceiling недостаточен — не молча clamp к 2048 и не создавать virtual projection. Fail official boss start с понятной admin/server log ошибкой.

### 14.3. HP scaling — первый tuning

```text
2 = 5000
3 = 6000
4 = 7000
5 = 8500
6 = 9500
7 = 10500
8 = 11500
9 = 12500
10 = 13500
11–15 = interpolate 14500..17500
16–20 = interpolate 18000..20000
```

Точные промежуточные значения оформить чистой deterministic policy и unit tests.

### 14.4. Модель и анимации

Использовать предоставленную модель/анимации друга как источник движений, а не продолжать расширять процедурные approximations текущего Java model.

Имеющиеся animation clips:

- Idle;
- Running2;
- Swipe2;
- Hurt2;
- Dying2;
- `udar_iz_grudi` ~4.0417 sec, release около 2.0 sec;
- `udar_po_zemle2` ~9.0417 sec, impact около 5.5 sec.

Серверные gameplay markers обязаны совпадать с animation timing.

`Hurt2`: все accepted hits уменьшают HP, но визуальный Hurt clip можно throttle примерно до 4 ticks, чтобы 10–20 игроков не перезапускали анимацию каждую миллисекунду.

Не добавлять GeckoLib автоматически. Текущий клиент не имеет такой зависимости. Сначала проверить формат предоставленных geometry/animation JSON. Либо конвертировать в существующий Fabric ModelPart/keyframe runtime, либо добавить маленький собственный bounded animation loader. Новую тяжёлую зависимость вводить только если она реально оправдана и отдельно проверена.

## 15. Boss AI Director

Сделать intention-based director, а не случайный rotation spell pool.

Цикл:

```text
sample arena/player state
→ choose intent
→ choose legal attack
→ telegraph
→ release/impact
→ recovery
→ next decision
```

Global rules:

- same heavy attack нельзя два раза подряд;
- один игрок не должен быть primary target слишком долго;
- maintain recent target memory;
- heavy attacks всегда имеют telegraph и recovery;
- Rage сокращает decision/cooldowns, но не telegraph duration;
- не наслаивать две unavoidable mechanics на одного target;
- при 2 players pressure cap ниже, при 10 больше multi-target, а не x5 damage.

## 16. Boss phases

### Phase 1 — Awakening 100–80%

- Running2 chase;
- Swipe2 melee;
- chest beam / `udar_iz_grudi` ranged punish.

Минимум teleport/adds. Игроки учат базовый ритм.

### Phase 2 — Hunt 80–60%

- flank teleport;
- target rotation;
- teleport → Swipe;
- chest beam по retreating/distant targets.

### Phase 3 — Rift 60–45%

Вводится Ground Slam:

- animation `udar_po_zemle2`;
- floor cracks растут по ходу telegraph;
- gameplay impact около t=5.5s;
- damage/knockback только в impact;
- заметный recovery.

Rift Obelisks остаются отдельной читаемой mechanic этой phase, а не случайным spell среди десятка.

### Phase 4 — Overload 45–30%

Director комбинирует уже знакомые атаки короткими связками:

- Swipe → flank → chest;
- chest pressure → reposition → Slam;
- teleport to distant → Swipe → recovery.

Минимум обычных adds.

### Phase 5 — Rage 30–20%

- faster decisions;
- more multi-target beams/projectiles;
- more teleport;
- shorter recovery;
- telegraph duration не сокращать.

### Phase 6 — Last Seal 20–0%

Boss перемещается на Core и перестаёт roaming.

Появляется shield и persistent Guardian Tentacles.

## 17. Permanent Guardian Tentacles

Количество зависит от official roster:

```text
2 players    → 2
3–4          → 3
5–7          → 4
8–10         → 5
11–15        → 6
16–20        → 8
```

Не масштабировать длительность финала просто умножением `count × full HP`.

Определить `totalGuardianHealthBudget(players)` и распределять budget между количеством guardian. При росте count каждая individual tentacle может быть менее жирной, но одновременно создаётся больше направлений давления.

Target pressure: при наличии альтернатив максимум примерно 2 guardian одновременно жёстко давят одного игрока.

### 17.1. Guardian states

Рекомендуемый enum:

```text
EMERGING
READY
TELEGRAPH_GRAB
GRAB_SUCCESS
HOLD
THROW
MISS_RECOVERY
HIT_RECOVERY
DYING
DEAD_RESPAWN
```

Attack:

1. Ground crack telegraph.
2. Grab attempt.
3. Success → захват/короткий hold/damage/throw.
4. После success примерно 10 sec vulnerable/recovery.
5. Miss → примерно 4 sec recovery.

Killed guardian → respawn примерно 40 sec.

### 17.2. Shield break

Пока жив >=1 permanent guardian → Boss shielded.

Когда все permanent guardians одновременно dead:

- freeze all guardian respawn timers;
- Boss shield OFF;
- temporary tentacle spawning OFF;
- гарантировать ровно ~15 sec damage window.

Если Boss пережил window:

- shield returns;
- guardians restore according to controlled restart cycle;
- repeat.

## 18. Temporary Tentacles

Максимум одновременно:

```text
2–4 players  → 2
5–8          → 3
9–12         → 4
13–16        → 5
17–20        → 6
```

Lifetime <=10 sec.

Обычный temporary:

- ~1 sec visible crack telegraph;
- emerge;
- 1–несколько pressure actions;
- к 10 sec retract/remove;
- no persistent HP/loot.

Во время 15-sec Boss damage window не спавнятся.

### 18.1. Unavoidable under-player tentacle

Отдельный тип displacement.

- появляется непосредственно под выбранным player;
- это намеренно нельзя dodge до появления;
- гарантированно подбрасывает/выбрасывает с позиции;
- damage low или zero;
- цель — ломать статичную позицию, а не бесплатно отнимать много HP;
- не выбирать одного игрока несколько раз подряд, если есть альтернативы;
- visual language отличается от dodgeable crack-grab.

## 19. World-space VFX architecture

### 19.1. Главный принцип

Все «лучи» в ивенте — **настоящие непрерывные beams/ribbons**, а не `for (point) spawnParticle` линия.

Это относится к:

- boss chest beam;
- Carrier beam;
- Guard → Elite tethers;
- prisoner → Core;
- Core → buffed enemies;
- Core restoration beams;
- safe-zone vertical beams;
- Rift Chains;
- Obelisk links;
- cinematic Core links.

### 19.2. Client protocol

Текущий client protocol v2 не имеет world VFX packets. Расширить bounded protocol (рекомендуется bump до v3).

Новые semantic types, например:

```text
END_VFX_SPAWN
END_VFX_UPDATE
END_VFX_REMOVE
END_TENTACLE_BIND
END_TENTACLE_STATE
END_BOSS_ANIMATION
```

Не обязательно использовать эти точные строковые имена, но контракт должен быть typed/bounded.

VFX packet должен поддерживать:

- eventId;
- generation;
- unique instanceId;
- type;
- world/dimension identity;
- source anchor: fixed position или entity UUID + optional semantic bone/socket;
- target anchor: position/entity;
- width;
- palette/style id;
- start/end/lifetime;
- animation phase.

Все coordinates finite, strings bounded, lifetime capped. Клиент удаляет любой VFX при generation change, world change, death/disconnect и explicit remove.

### 19.3. Client classes

Рекомендуемое разбиение:

- `EndRiftWorldVfxManager` — state/lifetime.
- `EndRiftWorldVfxRenderer` — Fabric world render hook.
- `RiftBeamRenderer` — ribbons/beams.
- `GroundDecalRenderer` — circles/lines/cracks/marks.
- `RiftTrailRenderer` — projectile/Swipe trails.
- `RiftTentacleModel` / renderer — после получения artist asset.

Не смешивать это с fullscreen `ClientVisualManager`.

### 19.4. Beam rendering

Каждый beam визуально имеет:

- яркий узкий core;
- более широкий translucent halo;
- animated UV/flow вдоль направления;
- мягкое fade at endpoints;
- configurable width/palette.

Endpoints moving — server может отправлять anchor entity IDs, чтобы клиент интерполировал локально без packet every frame. Если endpoint server-only position, update bounded cadence (например 5 ticks), client interpolates.

### 19.5. Server fallback

Игрок без CopiMineClient всё равно должен понимать gameplay:

- sparse viewer-scoped particles;
- sounds;
- Display geometry/telegraph при необходимости.

Но fallback не должен возвращать технический ActionBar.

## 20. Визуал ключевых атак

### Boss chest beam

- chest/core glows;
- arcs converge;
- small ring opens;
- около animation t=2.0 continuous thick beam release;
- impact flash + ground/world ring.

### Swipe

Широкая изогнутая energy arc за рукой, короткий lifetime.

### Ground Slam

Растущие floor cracks, затем на t≈5.5 bright fracture + expanding shockwave.

### Teleport

Силуэт стягивается в вертикальные rift strips/щель; destination rift opens → model reforms.

### Obelisk

Crack → rise → pulse ring/beam. Не particle fountain every tick.

### Wave2 mark

3D glyph over target; intensity/rotation state communicates timing.

### Wave3 portal

Volumetric frame + deep inner rift + shards; no flat black-purple blob.

### Skeleton room

Real arrows + thin client trail. Arrow Rain может иметь rift tears overhead.

### Singularity

Dark sphere + rotating rings + inward lines.

### Rift Chains

Continuous tether between player anchors, visually stretches/tenses.

### Tentacle

Dodgeable: crack/deformation before emerge.

Unavoidable: instant vertical rupture directly under target, distinct silhouette/effect.

## 21. Удаление технического боевого текста

Провести явный source audit всех `sendActionBar`, combat `sendTitle`, boss HUD strings.

Из официального combat path удалить/не использовать сообщения уровня:

- `Мини-босс готовит: <spell>`
- `Хранитель готовит: <spell>`
- `Эйфория Пустоты: <effect>`
- `Метка Разлома: N сек.`
- `Порталы Разлома: X/Y · удерживайте портал 5 сек.`
- старые `Пульс ядра: ...`
- старые `Шторм Разлома: ...`
- `Разлом раскрывается... N сек.`
- `Приговор Разлома: отойдите от метки`
- `Приговор Разлома обрушился на арену`
- `Финальный удар: Разлом рушится`
- `Осколок: XX%`
- `Ритуал: N`

Оставить можно:

- редкий атмосферный title названия волны/сцены;
- victory/cinematic text;
- административные/защитные ошибки (`арена защищена`, wrong resource, reward owner) вне объяснения боевой механики.

`EndRiftAiPolicy.displayName` можно оставить для debug/admin logs, но не surface игроку.

### 21.1. Boss HUD

Custom boss HUD должен показывать:

- `СТРАЖ РАЗЛОМА`;
- graphical health bar.

Не показывать:

- numeric `HP / maxHP`;
- phase label;
- cast state label;
- `EXHAUSTED/JUDGMENT/...`.

Цвет/музыка/модель/VFX могут атмосферно меняться по phase.

## 22. Награды после волн

Награда выдаётся **персонально каждому official participant** с durable idempotency, а не одним общим item pile, за которое можно драться.

Начальный tuning:

```text
Wave 1:
12 ENDER_PEARL
12 EXPERIENCE_BOTTLE
24 COOKED_BEEF
12 ARROW

Wave 2:
18 ENDER_PEARL
18 EXPERIENCE_BOTTLE
18 GOLDEN_CARROT
1 GOLDEN_APPLE
8 GOLD_INGOT

Wave 3:
20 ENDER_PEARL
24 EXPERIENCE_BOTTLE
3 DIAMOND
12 AMETHYST_SHARD
2 ECHO_SHARD

Wave 4:
24 ENDER_PEARL
32 EXPERIENCE_BOTTLE
4 DIAMOND
2 GOLDEN_APPLE
3 ECHO_SHARD

Wave 5:
32 ENDER_PEARL
40 EXPERIENCE_BOTTLE
6 DIAMOND
3 GOLDEN_APPLE
5 ECHO_SHARD
16 AMETHYST_SHARD

Wave 6:
40 ENDER_PEARL
56 EXPERIENCE_BOTTLE
8 DIAMOND
4 GOLDEN_APPLE
8 ECHO_SHARD
1 NETHERITE_SCRAP
```

Экономические amounts могут быть скорректированы после реального test run, но схема/idempotency обязательны.

Текущий `WaveRewardPolicy` и config parser расширить с 1–5 до 1–6.

Если обычные material rewards создаются самим EndEvent, для каждого player/wave нужен durable issue marker. Нельзя иметь только event-wide `waveRewardsIssued={4}`, если выдача каждому участнику может частично провалиться.

Рекомендуемые keys:

```text
end-rift:<eventId>:wave:<wave>:player:<uuid>:bundle
```

## 23. Финальная награда Boss

Каждому official participant после durable victory:

- гарантирован authentic `rift_core_shard`;
- independent 30% roll на authentic `night_cloak` (`Плащ Ночи`);
- большой resource/XP bundle.

Персональный 30% roll:

1. Вычислить/сгенерировать outcome один раз.
2. **Сохранить outcome durable до issue call.**
3. На restart/retry использовать сохранённый outcome, не reroll.
4. Если true → Artifacts `issueToPlayer` с stable idempotency key.
5. Если false → сохранить final `NOT_WON`, больше не пробовать roll.

Рекомендуемые keys:

```text
end-rift:<eventId>:boss:player:<uuid>:rift-core-shard
end-rift:<eventId>:boss:player:<uuid>:night-cloak
```

`EventArtifactRewardService` является единственным способом создать official artifact.

Пример ordinary boss bundle (tuning):

- 3000 XP;
- 12 Diamond;
- 2 Netherite Scrap;
- 12 Echo Shard;
- 64 Experience Bottle;
- 6 Golden Apple;
- 64 Ender Pearl;
- 32 Chorus Fruit.

## 24. Осколок Ядра Разлома

### 24.1. Item presentation

В `copimine-artifacts/items.yml`:

```text
id: rift_core_shard
rarity: LEGENDARY
name: &eОсколок Ядра Разлома
lore: &eИз него всё ещё доносятся отголоски хаоса Разлома.
```

Только жёлтое название и одна жёлтая атмосферная lore line. Не перечислять способности.

Можно оставить glint, если скрыты лишние enchant text.

### 24.2. Активная способность

Authentic owner делает **ПКМ предметом по земле**.

- channel 3 sec;
- player должен оставаться примерно в радиусе 1 block от start position;
- полученный damage отменяет channel;
- world change/logout отменяет;
- успешный cast телепортирует к заранее проверенной safe point возле Core;
- полный successful cooldown 10 min (600 sec);
- failed/cancelled cast только короткий anti-spam 3–5 sec, не полный cooldown;
- active teleport disabled во время active End Rift combat/attempt phases, чтобы не обходить кольца/камеры.

Никакого `Осколок 37%` ActionBar.

VFX: rune under feet + нарастающий continuous beam + sound; визуал сам сообщает channel.

### 24.3. Пассивы, если authentic shard находится в inventory

1. **Ender Pearl self-damage = 0.**
2. Enderman по-прежнему агрится по обычным правилам, но damage, нанесённый Enderman владельцу, уменьшается на 50%.
3. В настоящем End world владелец получает Strength II + Speed II.
   - Использовать `WorldAccessService.isEndWorld(world)` или равноценную authoritative проверку, не только строку имени мира.
   - Эффекты желательно скрыть от particles/icon, если Paper API позволяет корректно, чтобы lore не раскрывала свойства.
   - При потере shard/выходе из End снять только эффекты, принадлежащие shard service, не чужие более сильные potion effects.
4. **Abyss Anchor:** раз в 30 min при падении/гибели в Void автоматически спасает к Core safe point и оставляет 1 сердце (2 HP).
   - Не срабатывать во время active End Rift attempt.
   - Cooldown durable, relog/restart не сбрасывает.
   - Не дюпать totem/death flow: перехватить до необратимого death, четко определить priority относительно Totem и протестировать.

### 24.4. Authenticity

Ни одна способность не должна работать на fake item с таким же name/lore/CMD. На каждом entry point использовать Artifacts authenticity (`isAuthenticArtifact(stack, player, context)`) и owner binding.

Behavior может оставаться в End Event plugin, чтобы Artifacts не получил hard dependency обратно на End Event. Item definition/issuance остаётся в Artifacts.

## 25. Night Cloak

Если `night_cloak` ещё отсутствует в Artifacts catalog, добавить как official event-only artifact отдельной задачей реализации.

Эта спецификация определяет только выдачу: independent 30% каждому official participant. Конкретные gameplay свойства Плаща, если они ещё не зафиксированы другим контрактом, **не придумывать внутри End Rift implementation** без отдельного решения владельца.

## 26. Известные баги и обязательный способ исправления

### BUG-1: периодически перестаёт проходить урон

Симптом от владельца: два игрока могут бить **разных** мобов; на короткий момент damage перестаёт применяться, потом снова работает. Наблюдалось и на Boss.

Запрещено сразу «чинить» установкой `noDamageTicks=0` без доказательства.

Сначала добавить временный `CombatTraceService` (admin/debug gated), который для каждого интересующего hit пишет одну коррелируемую запись:

- monotonic server tick;
- wall-clock;
- TPS/MSPT или main-thread stall metric;
- attacker UUID/type;
- victim UUID/type/event kind;
- DamageCause;
- raw damage;
- final damage;
- cancellation state на раннем listener и итоговом MONITOR;
- `isInvulnerable`;
- `noDamageTicks`;
- `maximumNoDamageTicks`;
- `lastDamage`;
- HP before;
- HP next server tick;
- event phase;
- boss cast/shield state;
- old virtual + physical boss HP пока migration не закончена.

Repro с двумя игроками → классифицировать причину:

- event вообще не пришёл;
- кто-то cancel;
- vanilla hurt resistance;
- main-thread stall;
- custom boss manual damage race;
- сторонний plugin.

Только после классификации исправлять root cause.

Если доказан vanilla hurt resistance для event mobs, можно уменьшить `maximumNoDamageTicks` примерно до 3–4 ticks на конкретных event entities. Не ставить 0 глобально.

После фикса Combat Trace оставить как выключенный diagnostic tool и добавить regression test/manual scenario.

### BUG-2: boss virtual HP / accepted hit cancellation

Удалить authoritative virtual HP path. Это одновременно упрощает диагностику lost hits. Accepted normal hit не cancel.

### BUG-3: Wave3 3D portal — чёрно-фиолетовое пятно

Исправлять asset/model transform/depth path, не particle makeup. Обязательный screenshot test.

### BUG-4: bonus knockback у всех Wave3 mobs

Удалить общий `applyWaveThreeModifiers` knockback effect/семантику. Роль Pusher назначать ровно двум entities каждой defending pack.

### BUG-5: старые safe sectors / Tower Defense / Rift Storm продолжают жить скрыто

При V2 нельзя оставить одновременно новый Fog controller и старый `WAVE_ONE_ZONES`/riftStorm/tower state, которые могут тикать/рисовать/мутировать арену.

Удалить official references, tasks, cleanup paths и obsolete configs после миграции. Legacy code можно удалить после покрытия новыми tests.

### BUG-6: Wave6 cross-room aggro

Не решать только `mob.setTarget(null)` раз в секунду. Enforce isolation в candidate selection + target event + teleport + projectile + AoE + path boundary.

### BUG-7: боевые технические надписи

Source-audit и regression search/test: official combat code не должен содержать `готовит:`, `Метка Разлома:`, `Порталы Разлома:`, `Осколок: %`, numeric ritual countdown и аналогичные helper ActionBars.

### BUG-8: производительность fog/arrow/VFX/rings

Запрещены unbounded loops/entity spam. Добавить runtime counters в `/cmend debug perf`:

- owned entities count by kind;
- event projectiles;
- active VFX instances;
- ring displays;
- active tentacles;
- fog particle emissions/sec;
- event controller task count;
- server MSPT.

### BUG-9: нет полного all-dead reset

Текущий death handler очищает локальные states, но V2 должен централизованно определять zero living roster и вызывать wipe.

### BUG-10: current state machine W5→boss

Добавить `INTERMISSION_5`, `WAVE_6`, `PRE_BOSS_COOLDOWN`, migration/recovery tests.

## 27. Рекомендуемое разбиение серверного кода

Не обязательно совпадать названиями 1:1, но responsibilities должны быть разделены.

```text
AttemptLifecycleController
  start/intermission runes, roster live state, respawn, wipe

TransitionRuneController
  unique occupancy + 5 sec unanimity

WaveDirector
  creates/owns exactly one active WaveController

Wave1CarrierController
Wave2HuntController
Wave3PortalController
Wave4FogController
Wave5RingController
Wave6ChamberController

EventCombatScalingPolicy
EventMobDamagePolicy
TargetPressurePolicy

SafeZonePolicy
SafeZoneController
CoreRestorationService

RingGeometryPolicy
RingBoundaryController
RingDisplayController
CorePrisonerController

ChamberLayoutPolicy
ChamberMembershipService
ChamberTargetPolicy

BossV2Director
BossV2StagePolicy
BossAttackPolicy
BossAnimationTimelinePolicy

TentacleController
TentacleScalingPolicy
TentacleAttackPolicy

CombatTraceService
WaveRewardService / RewardLedger

EndRiftWorldVfxBridge
```

`CopiMineEndEvent` должен orchestration/bootstrap/listener delegation, а не хранить все mechanics fields сам.

## 28. Block mutation ownership

Новые реальные mutation types:

- emerald safe-zone floor;
- safe-zone Barrier;
- Wave5 ice prison;
- любые реальные temporary boundary blocks, если выбран такой implementation.

Использовать расширенный общий journal с записью:

```text
world
block coordinates
original BlockData
expected event BlockData/kind
generation
owner mechanic id
```

При restore менять блок обратно только если он всё ещё принадлежит ожидаемой event mutation; не перетирать чужое изменение вслепую.

При wipe/plugin disable/startup reconciliation restore all owned mutations.

## 29. Client asset/model strategy

### 29.1. Boss

Текущий procedural `RiftGuardianModel` — compatibility bridge, но V2 animation source должен соответствовать предоставленным friend assets.

Сделать explicit adapter/import pipeline. Timing markers unit-testable.

### 29.2. Tentacle

В repo сейчас нет готового tentacle renderer/model. Отдельный artist brief находится в `docs/art/end-rift-tentacle-artist-brief-ru.txt`.

Клиентский renderer должен принимать server state, а не сам решать hit/grab.

### 29.3. Portals

Resource/model assets проверить вместе с ItemDisplay transform path. Если client renderer даёт более качественный rift surface, server frame/fallback всё равно должен существовать для gameplay readability.

## 30. Tentacle gameplay synchronization

Очень важно: **client bone не является hitbox или источником истины**.

Server хранит target UUID, attack start tick, state и deterministic timeline.

Для успешного grab:

- server определяет попадание по своей геометрии/target contract;
- создаёт/использует невидимый server anchor для controlled hold;
- player прикрепляется к anchor (passenger/controlled transform) или аккуратно удерживается server-side;
- animation `grab_success` синхронизируется по тому же start tick;
- `throw` velocity применяется на server marker `THROW_RELEASE`;
- client `grab_socket` только помогает художнику/рендереру визуально совместить хват.

Не читать client bone transform обратно на сервер.

## 31. Тестовая стратегия

### 31.1. Pure policy tests

Добавить unit tests для:

- V2 state transitions;
- wipe idempotency model;
- transition rune 5-sec reset semantics;
- mob damage -4 exactly once;
- pressure caps 2/10/20;
- W4 zone count/size/capacity;
- W4 HP clamp;
- repair +40% durability math;
- W5 Ring2 resurrection window;
- prisoner drain 3 HP / floor 1 HP / no stack after floor;
- duo Ring3 activation sequencing;
- chamber count 2/3/4+;
- chamber even distribution;
- chamber scaling by local players;
- chamber target isolation;
- Skeleton arrow budgets;
- Boss HP curve;
- Boss phase thresholds;
- boss no-heavy-repeat / target rotation;
- guardian count scaling;
- total guardian HP budget split;
- temporary tentacle caps/lifetime;
- 15 sec shield-break window;
- Night Cloak deterministic persisted roll semantics;
- shard cooldown/void rescue rules.

### 31.2. Source contract tests

Добавить static/source assertions:

- no official call to `BossVirtualHealthPolicy` after migration;
- EventConfig parses Wave6;
- state machine has W6/preboss;
- reward policy accepts 6;
- no combat `sendActionBar` with banned strings;
- `EndRiftBossBarHud` does not render numeric HP/phase/cast names;
- W3 general mob modifier does not grant bonus knockback;
- protocol supports world VFX bounded types.

### 31.3. Local Paper integration

Обязательные сценарии:

**2 players full run**
- пройти W1–W6 + Boss;
- W5 trap реально оставляет одного fighter и всё ещё проходимо;
- solo chamber profile в W6;
- boss 2 permanent guardians;
- wipe on both dead.

**3 players**
- Wave6 exactly 3 chambers;
- no mechanics requiring pair in solo room.

**10 players**
- W4 zones 5→4→3;
- W6 distribution 3/3/2/2;
- 5 permanent boss guardians;
- multi-target pressure, no mass dogpile;
- event remains bounded in MSPT/entities.

**20 players stress**
- 8 permanent guardians;
- max 6 temporary;
- no runaway particles/projectiles/displays.

### 31.4. Damage bug reproduction

До удаления Combat Trace evidence сделать отдельный 2-player test, где двое одновременно бьют разные event mobs и boss. Логи должны доказывать, что accepted hits consistently alter HP.

### 31.5. Client visual manual tests

Нужен настоящий GUI client evidence для:

- Wave3 portal not black-purple blob;
- continuous beam (не точки);
- W4 fog 3 blocks visually opaque, safe interior clear;
- ring smooth rotation/collapse;
- W6 walls/chamber effects;
- boss friend animations sync chest/slam;
- tentacle grab aligns player;
- Boss HUD без technical text;
- no spell name ActionBar.

## 32. Performance budgets / guardrails

Это не абсолютные gameplay limits, а implementation guardrails:

- Ring displays: ориентир <=72 total.
- Temporary tentacles: hard cap 6; permanent max 8.
- VFX instances: bounded registry, recommended hard cap порядка 128 active End Rift instances; reject/log excess rather than leak.
- Skeleton real arrows: local chamber/global hard cap; не позволять unlimited accumulation. Должен существовать cleanup TTL.
- Fog: no per-block entity. Viewer-scoped sparse emissions and client render.
- One scheduler per mechanic family where possible, not one repeating task per particle/beam/tentacle segment.
- All long-lived repeating tasks registered in `EventTaskRegistry`.
- Client renderer performs interpolation locally and does not require 20 packets/sec per beam.

Добавить perf diagnostics и выполнить 20-player synthetic stress if possible.

## 33. Конфигурация

Перестроить `config.yml`, но сохранить bounded validation.

Добавить sections минимум:

```text
intermission.rune-hold-seconds: 5
pre-boss.cooldown-seconds: 20

combat.global-mob-damage-reduction: 4.0
combat.target-pressure...

wave-1.carrier...
wave-2.hunt...
wave-3.portals: 3
wave-3.pushers-per-pack: 2
wave-4.fog...
wave-5.rings...
wave-5.prisoner.drain-seconds: 50
wave-5.prisoner.drain-hp: 3.0
wave-5.prisoner.min-hp: 1.0
wave-6.chambers...

boss-v2.health-scaling...
boss-v2.tentacles...

rewards.wave-6...
rewards.night-cloak-chance: 0.30
shard.channel-seconds: 3
shard.cooldown-seconds: 600
shard.abyss-cooldown-seconds: 1800
```

Не сохранять legacy config keys, которые всё ещё запускают старые Tower Defense/Rift Storm official paths. Если нужны для миграции, clearly deprecated/ignored.

## 34. Очерёдность реализации для Codex

После утверждения этого design spec составить отдельный implementation plan. План должен идти примерно так, чтобы не собирать всю систему одним гигантским PR:

1. State/persistence V2 + wipe lifecycle + transition runes.
2. CombatTrace и root-cause bug investigation до больших boss rewrites.
3. Общий scaling/damage/target policies.
4. Wave1–3 + portal fix/knockback fix.
5. World-space VFX protocol/renderer foundation.
6. Wave4 + fog + restoration.
7. Wave5 rings/prisoner.
8. Wave6 chamber system/AI isolation.
9. Boss real HP + animation adapter + AI phases.
10. Tentacle system after artist asset contract is satisfied.
11. Rewards/shard/Night Cloak durable ledger.
12. HUD/text cleanup.
13. Full regression/perf/manual visual matrix.

Каждый шаг реализовывать TDD: сначала policy/contract test, потом код, затем integration verification.

## 35. Definition of Done

Работа не считается законченной только потому, что проект компилируется.

Готово только если одновременно выполняется:

- 6 новых waves соответствуют этому документу;
- переходы через 5-sec runes работают W1–W5;
- W6 → ровно 20 sec safe cooldown → Boss;
- all-dead wipe полностью возвращает к стартовым рунам/W1 и сохраняет Core resources;
- 2-player full run проходим;
- 10-player run остаётся сложным и bounded;
- W3 portal реально читаемый 3D на клиенте;
- W3 bonus knockback только у двух pushers;
- W4 fog/safe zones/repair работают и clean up;
- W5 prisoner не может умереть и duo survivor не получает 1v4;
- W6 mobs ни разу не target foreign chamber;
- Boss использует real HP, accepted hits не теряются;
- Boss animations timing совпадает с gameplay markers;
- permanent/temporary tentacles масштабируются и корректно clean up;
- все beams выглядят continuous в клиенте;
- технические spell ActionBars/HUD удалены;
- Wave1–6 rewards durable/idempotent;
- каждый official winner получает shard;
- Night Cloak roll независимый 30% и не reroll после restart;
- shard имеет зафиксированные passive/active abilities и authenticity checks;
- no stale Barrier/emerald/ice/display/projectiles/tasks после wipe/restart/disable;
- automated tests зелёные;
- local Paper test зелёный;
- реальный client visual checklist пройден;
- launcher/site/production не затронуты.

Только после выполнения Definition of Done и fresh verification можно считать End Rift Event V2 реализованным.