# CopiMine End Rift — итоговый отчёт о ремонте, приёмке и выпуске

- **Дата:** 22 ноября 2026
- **Источник задания:** разбор плейтеста 21 ноября — `C:\Temp\copimine-live-playtest-audit-20261122\playtest-audit.md` (авторитетный список наблюдений и «что остаётся проверить»)
- **Ветка:** `codex/end-rift-live-playtest-repair`
- **Проверенный и выпущенный код-коммит:** `62905562` (родительская линия — `c4be236b`, `73642619`, `2da1a18a`, `83fa01cd`, `4012a715`, …); артефактная линия VFX/AI/финала — см. раздел 10
- **Канонические спецификации (перечитаны полностью, хэши сверены):**
  - `CM-END-RIFT-WAVES-CANONICAL` rev `2026-10-27.1`, 47126 Б, SHA-256 `6E1245B44A449A0EF1BAB9193606398660C68F6BB2DE7D7BB6082CC8CDE50CAF`
  - `CM-END-RIFT-RIFT-GUARDIAN-CANONICAL` rev `2026-10-27.1`, 51711 Б, SHA-256 `0C781E42E274A3F27762E0B568CC1D8FFD7DFD8623E5A0F1DEB65EA73A94AE50`

> **Главное ограничение правдивости:** живого игрового прогона Minecraft, скриншотов F2 и настоящего клиента у агента нет. Всё, что относится к нативному рендеру и реальному бою, помечено как **НЕ ПРОВЕРЕНО / ЗАБЛОКИРОВАНО**, а `nativeVerified` остаётся `false`. Это осознанное состояние приёмки, а не «пройдено».

---

## 0. Кратко

- Ремонт по всем 14 строкам разбора 21 ноября выполнен на уровне **исходников и автотестов**; часть изменений уже была в базовой линии, часть внесена этой веткой.
- Собранные кандидаты **End Rift Event** и **Narcotics**, а также **клиент** и **ресурс-пак** совпадают с локальными сборками по SHA-256 и SHA-512.
- CI на выпущенном код-коммите **зелёный целиком** (3 задания, включая Java-гейт).
- Прод обновлён: End Rift Event → `8ceac981…`, Narcotics → `ec2ef8d5…`, клиент опубликован как `CopiMineClient-0.1.4+26.3-cd2a0ba953cc8f5a.jar`; после рестарта `cmend debug invariants failures=0`, служба `active`.
- Событие на проде по-прежнему в состоянии `RECOVERY_REQUIRED` (причина `INVALID_OR_MISSING_WAVE5_RESTART_CHECKPOINT`) — это предусмотренный путь «нужно вмешательство администратора»; точная команда для снятия приведена в разделе 7. Агент её **не выполнял** (решение владельца).

---

## 1. Артефакты и контрольные суммы

| Артефакт | Размер, Б | SHA-256 | SHA-512 |
|---|---:|---|---|
| `CopiMineEndEvent-26.3.jar` | 2623301 | `0aa1a51c4c0819938ce10f786cb350bd446aced1ad2e083f5984ee73b632f029` | `51278c92421992e2669c268a465818bfcf22c0b59b62e3adb187483511cb27b9396326d068b8fb927f816fedfbf22f98757d0f470c92265560db52f6e4f6297b` |
| `CopiMineNarcotics-26.3.jar` | 379018 | `ec2ef8d50c780d2faf52469bd52d20724d8be9c5f8c23a76c5c9d459782e35a6` | `3328ef927d8d2b3c703c194038f8c4ff3541653cd574bff0aec8ee65f516d3e2ef9f135f16a05e940aefbcfe31a2a63e4edc6f621ba0c781b3554068a3a614b2` |
| `CopiMineClient-0.1.4+26.3.jar` | 30360209 | `cd2a0ba953cc8f5a9e2866e4c6c01612cfbd749c8431ac52e6e4fdbbe995ffd99` | `a399de1f3b5bcc5dc56164ea9f9acf28b43a9c4c8102913aa713ddcce146a3651d1ed9d5f094d4c365579a659cbde224a2c4f38fdc897d07ba8b688566e83382` |
| `CopiMineResourcePack-26.3.zip` | 34373458 | `98d7950558e7da38e37b641d340b7fe89dc0310cbce6ad0671be62432d627176` | `3fcc01e531ef23495c00aedaafdebd1b9b4e1c51fcf5bc565662a82f19c30ab6ecdbc6ea79e4036d1ebce76fecbfe8f2bbccbff4ffddeb28862f214f67f4a5c3` |

Публичные имена и расположение:

| Что | Где |
|---|---|
| Серверный плагин события | `/opt/copimine-game/minecraft/server/plugins/CopiMineEndEvent.jar` (установлен 22.11.2026 18:15 +07, SHA-256 `e5634097…`) |
| Мост клиента | `/opt/copimine-game/minecraft/server/plugins/CopiMineNarcotics.jar` (установлен, SHA-256 `ec2ef8d5…`) |
| Клиент | `https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-cd2a0ba953cc8f5a.jar` (отдаёт SHA-256 `cd2a0ba9…`) |
| Ресурс-пак | `https://copimine.ru/assets/CopiMineResourcePack-26.3-98d7950558e7da38.zip` (отдаёт SHA-256 `98d79505…`, SHA-1 `03951b4e43b5564b4d45ea71b07235997ff2bbd7`) |

Пины в `tools/minecraft-26.3/server-plugins.lock.json` и `tools/minecraft-26.3/profile.lock.json` совпадают с собранными артефактами по SHA-256 и SHA-512; во всех записях `nativeVerified: false`.

---

## 2. Релизные ворота

| Ворота | Результат | Доказательство |
|---|---|---|
| Полный локальный прогон Python | `3748 passed, 19 skipped, 2 subtests passed`, exit 0, ≈1141 с | `C:\Users\zavod\AppData\Local\Temp\opencode\ptfinal.log` |
| Java-гейт, чистые политики (249 тестов) | 249/249 OK | локальный прогон сбо́рки того же состава, что и CI-шаг «Current pure Java policies» |
| Java-гейт, persistence/recovery (8 тестов) | 8/8 OK; Paper API `1.21.1` совпал с пином `b8df3e7f…` | локальный прогон шага «Current persistence and recovery» |
| `git diff --check` | чисто | `-c core.whitespace=cr-at-eol diff --check` |
| Совпадение хэшей кандидатов | SHA-256 и SHA-512 совпадают с lock-файлами и со стейджем на хосте | `sha256sum -c SHA256SUMS` на хосте — все `OK` |
| CI на выпущенном коммите | **полный успех всех 3 заданий** | run `44960918762`, https://github.com/IliaZav/copimine/actions/runs/44960918762 |

Промежуточный красный прогон `44955023110` (коммит `c4be236b`) упал на шаге «Run shared Java plugin release gate» в тесте `PortalPresentationPolicyTest`: тест держал жёстко зашитый старый дедлайн сворачивания портала **600 мс**, тогда как `ccec3cd0` осознанно увеличил `PortalPresentationPolicy.COLLAPSE_MILLIS` до **1250 мс**. Исправление `62905562` переводит тест на чтение константы политики (сам комментарий теста обещал «никогда не зашивать длительность жёстко»). Локально подтверждено: 249 + 8 Java-тестов после правки зелёные.

---

## 3. Развёртывание на прод

- **Хост:** `qwerty@90.188.117.117:2222`, служба `copimine-game.service`, каталог `/opt/copimine-game/minecraft/server`, Java 25.
- **Скрипт развёртывания:** `/root/deploy-end-rift-20261122.sh` (LF, SHA-256 `328b0b71e01fc998034034d1269d582ee313044b80816cd10e2002dc7753d17d`, `bash -n` OK). Стейдж: `/tmp/copimine-stage-8ceac981`.
- **Состояние до:** 0 игроков онлайн; EndEvent `c327507a…`; Narcotics `a0a9248c…`; служба `active`; `cmend debug invariants failures=0`.

Что сделал скрипт:

1. Проверил `SHA256SUMS` стейджа, версию обязательного клиентского мода в конфиге моста, наличие ресурс-пака, отсутствие игроков, допустимое состояние события (`COLLECTING/COMPLETED/WAITING/IDLE/RECOVERY_REQUIRED`, `wave=0`).
2. Снял бэкап: `/opt/copimine-backups/end-rift-20261122-8ceac981a86f` (прежние `CopiMineEndEvent.jar` `c327507a…`, `CopiMineNarcotics.jar` `a0a9248c…`, `server.properties`, `pg_dump` базы `copimine` + список записей, `plugin-data.tar.gz` со состояниями обоих плагинов, `SHA256SUMS`).
3. Остановил службу, атомарно подменил оба jar-а, переписал `resource-pack`/`resource-pack-sha1` в `server.properties`, запустил службу.
4. Дождался `cmend debug invariants` → `failures=0`.

Результат (`deploy_exit=0`):

- Установлены: EndEvent `8ceac981a86f8e4752f5f6139b15419cc1bfb751fda0af1b9163ca79044ebd4a`, Narcotics `ec2ef8d50c780d2faf52469bd52d20724d8be9c5f8c23a76c5c9d459782e35a6`.
- Публичные адреса клиента и пака отвечают и отдают ожидаемые SHA-256 (`cd2a0ba9…`, `98d79505…`).
- `server.properties`: `resource-pack=https\://copimine.ru/assets/CopiMineResourcePack-26.3-98d7950558e7da38.zip`, `resource-pack-sha1=03951b4e43b5564b4d45ea71b07235997ff2bbd7`.
- Служба `active`; `END_RIFT_INVARIANTS failures=0 last=none countersNonNegative=true entityCount=2 tasks=0 bossProxies=0 wave7Blocks=0`.
- В логе загрузки: `Loading server plugin CopiMineEndEvent v0.1.0`, затем ожидаемое `WAVE5_RESTART_RECOVERY_REQUIRED … reason=INVALID_OR_MISSING_WAVE5_RESTART_CHECKPOINT` (см. раздел 7).

Регенеративные «грязные» бинарники (thirdparty, `CopiMineNarcotics.jar`, `modpack_snapshot.json`, `CopiMineClient-0.1.1.jar`) в коммиты **не** включались.

---

## 4. Соответствие разбору плейтеста 21 ноября

Обозначения статуса: **SOURCE** — правка кода в ветке; **TEST** — автоматический тест в той же линии; **LIVE** — нативная/живая проверка (у агента отсутствует → `BLOCKED`).

| Система | Наблюдение 21.11 | Что сделано | Автотест | Живая проверка |
|---|---|---|---|---|
| **Волна 1** | Транзакция подбора заряда | Взяты исправления `12e2400a` (подбор заряда), последующие `35f96189`, `82e7d0c0` | `tests/test_*wave1*` в полном прогоне | Пройти три доставки на Paper вдвоём, проверить отказ при полном инвентаре — **BLOCKED** |
| **Волна 2** | Зацикливание «нет допустимого наблюдателя»; 71 приложение за refresh | `8bf38fa6` — один подписанный снапшот наблюдателей за refresh; `7e716267` — первый отмеченный ставится на безопасную точку; `c3028228` — анимация исходной модели без подъёма головы; выход из неудачной постановки фантома и бюджет появления (W202) | `tests/test_wave2_server_client_layout_wire.py`, `test_waves2_4_mechanics.py` и др. | Реальное оживление трёх групп, появление/исчезновение фигур, источники летающих объектов, музыка, полный цикл двух игроков — **BLOCKED** |
| **Волна 3** | Отсутствующий `org.bukkit.Color` у частицы; сворачивание портала 0,6 с | Типизированная эмиссия; `ccec3cd0` — читаемый финал сворачивания **0,6 → 1,25 с**; `fadf14f7` — тест авторской границы сворачивания; `4012a715` — граница ауры и тяжёлый удар Rift Giant по серверному радиусу | `test_wave3_portal_fold_timeline.py`, `test_wave3_portal_elite_runtime_contract.py`, `PortalPresentationPolicyTest` (после `62905562`) | Длительность на сервере, аура/ИИ гиганта, снаряды элит — **BLOCKED** |
| **Волна 4** | Отдельного подтверждённого наблюдения нет; механика обелисков не менялась | Изменений нет (осознанно) | полный прогон | Официальное прохождение и очистка — **BLOCKED** |
| **Волна 5** | Объёмная дымка вне укрытия | `01261813` — укрытия представлены как «чистые дымовые камеры»: пол проверяется по активной арене и временному изумрудному следу, тёмные стены/потолок рендерятся без коллизии | `test_wave5_fog_chambers.py`, `test_wave5_uniform_volume.py` | F2 из одной точки с одинаковой камерой; урон/статус отдельно от клиента; FPS с Iris и без — **BLOCKED** |
| **Волна 6** | Тело стрелка в колонне; `RIFT_RAM:tick-exception:IllegalStateException` | `12e2400a` — высота/коллайдер стрелка; `97fec1d5` — броня Тарана и ограниченный бой расходуемого Стража; отмена обычного урона до 7-го принятого столкновения | `test_boss_ambush_major_cast.py` и профильные | Реальная высота ног, все семь ударов, отсутствие остаточной полосы и повторного скелета — **BLOCKED** |
| **Волна 7** | Запрос запуска получил `invalid-runtime-context` после срыва теста Тарана | `a4f49dca` — старт готовых пробных волн с очисткой временного контекста (`invalid-runtime-context` не мешает отдельной пробе) | профильные Python-тесты | Официальный переход 6→7, один открытый зал, четыре испытания, восстановление и передача в бой со Стражем — **BLOCKED** |
| **Страж 1–2** | Для тестового Стража источники атак ограничены оператором | Ограничение источников сохранено; официальный бой через тестовую команду не сбрасывается | профильные | Обычный урон меча/стрелы/артефакта на обеих ветках; новые паттерны ИИ и заклинания — **BLOCKED** |
| **Страж 3** | Внешний шаг спирали и сигнал на полу используют радиус/высоту серверной проверки гейзера | Сохранено (правка была базовой) | `RiftFracture*PolicyTest` | Синхрон круга, высокого гейзера и попадания — **BLOCKED** |
| **Страж 4** | Пол сохраняет четыре прохода и кольцо; проходы исключаются до подготовки журнала | Сохранено; ранее «висевшие» невосстановленные ячейки закрыты журналом | `HazardMutationJournalTest` и профильные | Все проходы и восстановление при прерывании/рестарте — **BLOCKED** |
| **Страж 5** | Эхо и его урон не менялись | Изменений нет | `Echo*Test` | Копии и реальные повторные атаки на совместимом клиенте — **BLOCKED** |
| **Страж 6** | Пробный босс в `RECOVERY_REQUIRED` останавливал цикл Кагуне; у засады не было срока блокировки слота | `97fec1d5` — ограниченный бой расходуемого Стража и конечный срок; завершающие состояния очищаются после клипа даже при выходе зрителей | `test_boss_ambush_major_cast.py`, `Tentacle*PolicyTest` | Попадания с разных сторон по носителю и урон малых/постоянных/одноразовых Кагуне; полная постановка финала — **BLOCKED** |
| **Босс-бары** | Постоянные пульсации; клиентский HUD не скрывался | `b0aa35f0` — убраны постоянные пульсации, сохранён цвет фазы и одноразовая реакция на смену фазы; HUD скрывается после unbind или через 4 с без свежего состояния | `test_end_rift_hud_redesign_assets.py`, `BossHitFeedbackPolicyTest` | Наблюдение после официального и тестового поражения; вариант без клиентского мода; босс-бары минибоссов — **BLOCKED** |

Правки линии закреплены сборкой и пинами: `83fa01cd` (клиент), `2da1a18a` (EndEvent + мост), `73642619` (проверка пинов и истории замен), `d31f4e6c` (шесть тестовых харнессов под ремонт, включая `test_waves2_4_mechanics.py`, `test_guardian_respawn_recovery_adapter.py`, `test_small_kagune_strike_adapter.py`), `dba683b7` (размер клиента 30 360 209 и SHA-512 в `docs/minecraft-26.3/README.md` + `OWN_PLUGIN_INVENTORY.json`), `c4be236b` (перенос заменённого кандидата во второй assert lock-теста), `62905562` (дедлайн сворачивания портала из константы политики).

---

## 5. Что НЕ проверено / ЗАБЛОКИРОВАНО

- **Нативный клиент и живой бой (все строки колонки «что остаётся проверить»).** Нет доступного живого Paper+Fabric клиента → это **BLOCKED**, а не «пройдено». Соответственно `nativeVerified=false` и в lock-файлах, и в приёмке.
- **W611 (Волна 6, захват пленника).** Без V4-подписанного моста (`COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE`) sandbox-capture прерывается — `CLIENT-BLOCKED`.
- **W708 (Волна 7, PERSONAL_ECHO).** Требует V4-подписанного клиента, иначе предстартовый fail-closed `WAVE7_NAMED_ECHO_PREFLIGHT_FAILED` — `CLIENT-BLOCKED`.
- **K03 (Кагуне).** Спам в окне анимационной неуязвимости всё ещё может уменьшать HP (`tentacleDamageWindowOpen` проверяет только активный бой, `applyTentacleGuardianDamage` списывает HP до `shouldEnterHitRecovery`) — `CODE-RISK HIGH`, требуется проверка через полный адаптер.
- **R03 / приёмка 26.3.** Старые F2 и часть прогонов могли быть на другом клиенте — нативная приёмка 26.3 по-прежнему не завершена.
- Остальные строки исторического `BUG_LEDGER.md` со статусом `NATIVE-UNVERIFIED` (B05, часть K/W) остаются таковыми.

---

## 6. Текущее состояние прода (после деплоя и сброса)

До деплоя событие стояло в предусмотренном `RECOVERY_REQUIRED`: сохранённый чекпоинт тумана Волны 5 относился к другому поколению (`wave5-fog.generation=420` против `generation=422`), поэтому сборка корректно отказывалась угадывать состояние. Новый кандидат `e5634097…` установлен 22.11.2026 в 18:15 +07 при 0 игроков онлайн; после проверки загрузки, инвариантов и нулевого прогресса владелец разрешил сброс, и `resetEventSafely` перевёл событие в `COLLECTING`.

Фактическое состояние после сброса и контрольного рестарта:

```
state=COLLECTING event=9d8cff21-644a-4582-8099-52293650efa5 generation=422
wave=0  roster=0 participants=0  event-mobs=0  endUnlocked=false
resources 0/64, 0/64, 0/100, 0/128
Recovery reason: (пусто)
END_RIFT_INVARIANTS failures=0 countersNonNegative=true entityCount=2 tasks=0 bossProxies=0 wave7Blocks=0
```

Сброс переживает рестарт: после повторного запуска служба поднялась без ошибки «Wave 5 recovery checkpoint refused», плагин залогировал `CopiMineEndEvent loaded in local mode; persistent phase=COLLECTING`. **Мир и данные игроков не затронуты**; участники снова вносят ресурсы и запускают ритуал.

Откат: предыдущий JAR и `server.properties` лежат в `/opt/copimine-backups/end-rift-20261122T111544Z-e5634097` (`SHA256SUMS` внутри); более ранняя полная точка — `/opt/copimine-backups/end-rift-20261122-8ceac981a86f`.

---

## 7. Оставшиеся дефекты и риски

| ID | Суть | Статус |
|---|---|---|
| K03 | Урон Кагуне в окне неуязвимости | `CODE-RISK HIGH` — нужен прогон полного адаптера |
| W611 | Захват пленника Волны 6 без V4-подписи | `CLIENT-BLOCKED` |
| W708 | PERSONAL_ECHO Волны 7 без V4-подписи | `CLIENT-BLOCKED` |
| R03 | Нативная приёмка 26.3 | `NATIVE-UNVERIFIED` |
| R04 | `resetEventSafely`/`removeCore` не очищали `resourceContributors`: после сброса и рестарта `participants=1` при пустом `roster` (влияло только на счётчик в `cmend status` и строку имён в объявлении победы) | `CODE-FIXED` — оба сброса очищают леджер вместе с ростером; добавлен регрессионный тест (§10.11) |
| B05 | Модель/UV/хитбокс/щиты/боссбар Стража | `NATIVE-UNVERIFIED` |
| Разное | Все визуальные требования по строкам плейтеста | `BLOCKED` (нет графического клиента) |

---

## 8. Чеклист для живого теста владельцу

1. Выполнить `/cmend reset confirm`; убедиться, что `cmend status` показывает `state=COLLECTING`, `recoveryReason` пуст.
2. Вдвоём: собрать ресурсы, запустить ритуал, пройти три доставки зарядов (Волна 1) — проверить отказ при полном инвентаре.
3. Волна 2: убедиться, что все три группы реально оживают, фигуры появляются/исчезают по правилам, музыка и цикл двух игроков проходят.
4. Волна 3: замерить финал сворачивания портала (≈1,25 с), проверить ауру и тяжёлый удар Rift Giant, снаряды элит.
5. Волна 4: официально пройти и очистить обелиски.
6. Волна 5: снять F2 из одной точки с одинаковой камерой (с Iris и без), отдельно проверить урон/статус без клиента.
7. Волна 6: проверить высоту ног стрелка, все семь ударов, отсутствие остаточной полосы и повторного скелета.
8. Волна 7: официальный переход 6→7, один открытый зал, четыре испытания, восстановление и передача в бой.
9. Страж: урон меча/стрелы/артефакта на обеих ветках боя; паттерны ИИ и заклинания; синхрон круга, гейзера и попадания; все проходы пола и восстановление при рестарте; копии Эхо; попадания по носителю и урон всех трёх ролей Кагуне; босс-бары после поражения и без клиентского мода.

Результаты этого прогона нужно приложить к приёмке и только тогда снять пометку «НЕ ПРОВЕРЕНО» с соответствующих строк.

---

## 9. Повторный прогон гейта и деплой кандидата `e5634097…`

**Гейт.** Полный `tests/RunEndRiftEventChecks.ps1` прогнан из корня репозитория под `pwsh` 7.7.0 и пином Temurin 25.0.4.1; завершение — `End Rift current local checks passed.`, exit 0. Первый запуск этого сеанса упал на `EncounterGenerationPreflightTest`, потому что процесс был запущен не из корня репозитория (java-тесты читают путь относительно CWD); после смены рабочего каталога тот же гейт прошёл полностью. Это ошибка окружения, а не исходников.

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 606 380 байт, SHA-256 `e563409798d6c97d81ad0e6da14a78794246b2cbf826dd1cfc69f6797991569c` — совпадает с `tools/minecraft-26.3/server-plugins.lock.json`; `nativeVerified: false` сохраняется.

**Деплой (22.11.2026, 18:15 +07).** 0 игроков онлайн; заменён только `CopiMineEndEvent.jar` (`8ceac981…` → `e5634097…`); Narcotics `ec2ef8d5…`, ресурс-пак и клиент не менялись. Бэкап: `/opt/copimine-backups/end-rift-20261122T111544Z-e5634097`. После рестарта в логе: `Loading server plugin CopiMineEndEvent v0.1.0`, `CopiMineEndEvent services ready; phase=COLLECTING`; `END_RIFT_INVARIANTS failures=0`.

**Публичные артефакты.** `CopiMineResourcePack-26.3-98d7950558e7da38.zip` — HTTP 200, 34 373 458 байт; `CopiMineClient-0.1.4+26.3-cd2a0ba953cc8f5a.jar` — HTTP 200, 30 360 209 байт. Содержимое в этом прогоне не перекачивалось и не перехешировалось: проверены доступность и размер, а не повторно SHA-256.

**Что это НЕ доказывает.** Живой бой, нативная визуализация, F2-кадры и приёмка игроками этим прогоном не проверены; `nativeVerified` остаётся `false`.

---

## 10. Итерация 22.11.2026 (вечер): кинематографичные заклинания, ИИ целей, масштаб финала

### 10.1 Артефакт и пины

| Артефакт | Размер, Б | SHA-256 | SHA-512 |
|---|---:|---|---|
| `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar` | 2623301 | `0aa1a51c4c0819938ce10f786cb350bd446aced1ad2e083f5984ee73b632f029` | `51278c92421992e2669c268a465818bfcf22c0b59b62e3adb187483511cb27b9396326d068b8fb927f816fedfbf22f98757d0f470c92265560db52f6e4f6297b` |

**Провенанс.** Артефакт собран **тем же** путём, что и релизный кандидат CI: `scripts/minecraft/BuildMigrationPlugins.ps1` → Gradle 9.8.1 (`build/minecraft-26.3/toolchain/gradle-9.8.1`, SHA-256 дистрибутива `dce76f55…` сверен), задача `migrationPlugins --rerun-tasks`, `JAVA_HOME` = пин Temurin 25.0.4.1. Это опровергает прежнюю гипотезу о «другом артефакте»: предыдущая разница в размере была не расхождением пайплайнов, а тем, что сравнивался устаревший кандидат из более раннего источника. Прямой упаковщик `copimine-end-event/build-plugin.ps1` больше **не** публикует кандидата (это закреплено тестом `test_end_event_build_artifact_ownership.py`), поэтому у пина ровно один владелец — Gradle.

Обновлены: `tools/minecraft-26.3/server-plugins.lock.json` (кандидат, `size`, `sha512`, `ea91e7f1…` препендент в `replaceSha256`), `docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json` (`candidateSha256`), `tests/test_minecraft_migration_server_plugins.py` (два пина и порядок истории замен).

### 10.2 Что сделано по заклинаниям (порядок владельца: эффекты → ИИ → финал)

**Эффекты.** Авторский вид получили последние заклинания без композиции — контактный набор и Кагуне: `melee_swipe`, `chest_strike`, `ground_slam`, `ambush_grab`, `small_kagune`, `kagune_stab`, `kagune_eruption` (таблица композиций: 27 записей). Раньше эти id шли по общему пути каста и рисовали стандартную вспышку из двенадцати частиц вместо собственного силуэта. `kagune_stab → RIFT_LANCE`, `kagune_eruption → GEYSER` добавлены в геометрию, так что у каждого `BossSpell` есть тело; множество без меша (`{SWIPE, DEFEAT}`) не изменилось.

**Честная палитра частиц.** Новый чистый `domain/BossSpellParticlePalette` (`Payload {NONE, FLOAT, INTEGER, DUST, BLOCK}`, `Weight {DENSE, SPARSE}`, `resolve`, `isDistinct`, `nodeRank`) переводит авторские `primaryParticle`/`accentParticle` в реальный `Particle` с корректной полезной нагрузкой, которой требует Bukkit (пыль, `Float`, `Integer`, `BlockData`). Запрещённые на ярких узлах одиночные эффекты (`FLASH`, `SONIC_BOOM`, `SWEEP_ATTACK`) понижаются в ранге, поэтому силуэт не «вспыхивает» целиком и не скрывает атаку. Неизвестное или пустое имя → пыль в оттенке заклинания (прежнее поведение сохранено).

**Рендерер.** `BossSpellPresentationEffects.frame(...)` стал 8-аргументным: применяет авторский `vortexTurns` как жёсткий поворот всего силуэта (по `progress`), что превращает телеграф в реальную спираль, и выбирает яркую частицу по авторской подписи. `spawn(...)` учитывает нагрузку, `impact(...)` получил композицию. Стоимость не выросла: узел на кадр заменён один-к-одному, число точек и потолок 32 зрителя те же.

**ИИ.** `BossTargetSelector` переписан: `MAX_CANDIDATES = 4`, `choose(snapshot, currentTarget, cursor)`, `ranked(...)` с детерминированным поворотом равных оценок по курсору решения. `BossBrain.decide(...)` принимает курсор и перебирает ограниченные альтернативы, если лучшая цель не даёт ни одной кастабельной способности. Это закрывает наблюдение «босс стоит в пустой ветке приоритетов»: дальняя цель больше не блокирует весь набор атак, а один и тот же игрок больше не забирает все неоднозначные решения.

**Финал (LAST_SEAL).** Новый чистый `domain/LastSealArenaRitualPolicy` описывает арена-масштабный ритуал: сходящаяся печать на полу (32 точки, радиус 4.5–9.0), 8 шипов Кагуне из пола (до 5 блоков), 4 вертикальные колонны, замыкающие пол и верхний знак в клетку, и контр-вращающийся знак над боссом (20 точек, радиус 6.5±0.6, высота 7). Тяжёлый слой перерисовывается раз в 4 тика: 88 точек на «должный» тик, 22 в среднем — не больше, чем прежний локальный ореол (45 точек каждые 2 тика). Форма не зависит от числа игроков: 2 и 20 видят одно и то же. Локальный ореол сохранён, плюс одноразовый всплеск вознесения при входе в фазу (три расходящихся кольца, луч до босса, `FLASH`, рык дракона и визг wither).

### 10.3 Ворота и тесты

- **Полный гейт зелёный:** `pwsh -NoProfile -File tests\RunEndRiftEventChecks.ps1 -SkipBuilds -JavaHome <пин Temurin 25.0.4.1>` из корня репозитория, финальная строка `End Rift current local checks passed.`, exit 0.
- Новые проверки: `tests/LastSealArenaRitualPolicyTest.java` — `PASS (154048 checks)`; `tests/BossSpellParticlePaletteTest.java` — `PASS (200 checks)`; `tests/test_last_seal_arena_ritual_wiring.py` — 4 passed; `BossSpellVisualCompositionPolicyTest` — `PASS (396 checks)`; `BossTargetPolicyTest` — OK (включая `testEqualScoreTiesRotateFairly`); `TentacleAnimationPolicyTest` — OK. Все зарегистрированы в `tests/RunEndRiftEventChecks.ps1`.
- Пины артефактов: отдельный прогон `test_end_event_26_3_candidate_tracks_and_installs_the_real_target_jar`, `test_every_pinned_first_party_build_artifact_present_on_disk_matches_its_lock`, `test_26_3_plugin_lock_pins_current_luckperms_and_reproducible_first_party_candidates`, `test_own_plugin_inventory_contract.py` (4 теста), `test_end_event_build_artifact_ownership.py`, `test_end_event_candidate_reproducibility.py` — **13 passed**.

### 10.4 Уровни доказанности (обязательно к прочтению)

| Уровень | Что покрыто здесь |
|---|---|
| **CODE-IMPLEMENTED** | Все правки этого раздела: композиции, палитра, рендерер, ИИ целей, политика финала, проводка в монолите |
| **AUTOMATED-TESTED** | Полный локальный гейт (все Python-наборы, чистые Java-политики, persistence/recovery, гигиена диффа) + 13 тестов пинов |
| **PAPER-RUNTIME-OBSERVED** | Да. Артефакт `0aa1a51c…` установлен на прод 22.11.2026 02:05 MSK, сервис `active`, `END_RIFT_INVARIANTS failures=0`. Тест-босс доведён до `LAST_SEAL`; в логе `BOSS_LAST_SEAL_VISUALS_STARTED` и `BOSS_LAST_SEAL_ASCENSION`; после `cmend boss kill cleanup` состояние вернулось к `AWAKENING`, `boss=none`, инварианты `failures=0` |
| **NATIVE-CLIENT-VISUALLY-VERIFIED** | **НЕ ПРОВЕРЕНО.** Ни один кадр F2, ни один клип не снят агентом. Факт «выглядит как AAA» не заявляется |
| **OFFICIAL-ACCEPTED** | **НЕТ.** Приёмка визуала — за владельцем |

### 10.5 Развёртывание на прод и наблюдение живой логики (22.11.2026)

**Выпуск.** Код `5ab4665a` запушен в `origin` (`refs/heads/codex/end-rift-live-playtest-repair` = `5ab4665ab41c41c55098069b620f2ca3754ae0ef`). Артефакт `0aa1a51c…` (`CopiMineEndEvent-26.3.jar`, 2 623 301 Б) загружен в `/tmp/copimine-stage-0aa1a51c4c08`, `sha256sum -c SHA256SUMS` → `OK`. Деплой-скрипт (`deploy-end-rift-vfx.sh`, предшественник `/root/deploy-end-rift-20261122.sh`) прошёл preflight: рабочий каталог совпал, старый jar был `e5634097…`, Narcotics `ec2ef8d5…`, `required_client_mod_version: 0.1.4+26.3`, 0 игроков онлайн, `state=COLLECTING`, `wave=0`. Бэкап: `/opt/copimine-backups/end-rift-20261122-vfx-0aa1a51c4c08` (старый jar, `server.properties`, `SHA256SUMS`, `pg_dump -Fc copimine` и liste, `plugin-data.tar.gz`).

**Результат установки.** `/opt/copimine-game/minecraft/server/plugins/CopiMineEndEvent.jar` = `0aa1a51c4c0819938ce10f786cb350bd446aced1ad2e083f5984ee73b632f029`; `systemctl is-active copimine-game.service` = `active`; в `latest.log` — `Loading server plugin CopiMineEndEvent v0.1.0`; `cmend debug invariants` → `failures=0 last=none countersNonNegative=true`.

**Проверка финальной фазы в живом Paper.** Тест-босс (`cmend boss spawn`) доведён до `LAST_SEAL` через реальный переход по урону (`cmend boss damage 19000`), а не через принудительную команду фазы. В `latest.log`:
- `BOSS_LAST_SEAL_VISUALS_STARTED event=… boss=b16c37ef-… generation=422`
- `BOSS_LAST_SEAL_ASCENSION event=… boss=b16c37ef-…`
- `BOSS_LAST_SEAL_CORE_PIN event=… location=CopiMine 8,71,-39`

`cmend debug invariants` во время активной фазы: `failures=0 countersNonNegative=true entityCount=3 tasks=1 bossProxies=11`. **Ошибок и исключений в логе нет** (единственное WARN — не связанный с End Rift `AuthMe GeoLite2`).

**Очистка.** `cmend boss kill cleanup` → «Boss удалён как test cleanup; victory не вызвана»; `invariants` → `entityCount=2 tasks=0 bossProxies=0`; `cmend status` → `boss=none`, `bossPhase=AWAKENING`, `wave=0`. Прод возвращён в исходное `COLLECTING`-состояние.

**Что это доказывает.** Проводка финальной фазы, всплеск вознесения и очистка выполняются на живом сервере без ошибок и без утечки сущностей/задач. **Что это НЕ доказывает.** Как это выглядит на экране (читаемость телеграфов, «кинематографичность», довольство игроков) — не проверено; для этого нужен F2/клип владельца в подписанном клиенте.

### 10.6 Дополнительная итерация: чистка багов, командная цель ИИ, ударная волна (22.11.2026, ночь)

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 624 913 Б, SHA-256 `95da1854a4684bfd6090453aa50abb272b4842e2247ef76e8f17a0a84a311c7c`, SHA-512 `ee55ff741eec25ae290157b2225a44387069e147512b9893887ec43d3e8eae996a99e6588e7d567b2468a2f5b1df541d85a4530bc1896b1728189e94506b80ff`. Собран тем же Gradle-путём (`migrationPlugins`, `JAVA_HOME` = Temurin 25.0.4.1), `BUILD SUCCESSFUL`. Пины `3f601464…` и `0aa1a51c…` препендены в `replaceSha256` (сначала новый).

**Исправленный баг R04 (сброс не чистил вкладчиков).** `resetEventSafely` очищал `participantUuids`, но не `resourceContributors`. После `/cmend reset confirm` и рестарта `cmend status` показывал `participants=1` при `roster=0`: устаревший вкладчик считался участником, хотя архивный ростер был пуст. Теперь леджер вкладчиков очищается вместе с участниками, поэтому обе метрики согласованы (0/0).

**ИИ целей: командное давление.** `BossTargetSelector.secondaries(ranked, primary, max)` возвращает до двух **различных** вторичных целей (без primary), и `BossBrain.Decision` теперь несёт `supports`. Когда выбранная главная цель умирает/выходит/покидает арену внутри окна решения, `currentBrainTarget` сначала пробует вторичные цели этого решения, и только затем делает общий перебор. Это закрывает провал, когда командная комбинация «схлопывалась» к случайному игроку при выпадении главной цели. Общее число целей ограничено (2), поэтому 20 игроков не получают одно «групповое» заклинание на всю пати. Тест `testGroupComboSecondariesAreDistinctAndBounded` в `BossTargetPolicyTest`.

**Заклинания: ударная волна выхода.** `BossSpellPresentationEffects.impact` теперь на первых трёх кадрах релиза рисует ограниченное расширяющееся кольцо (фиксированные 18 точек, потолок `MAX_VIEWERS=32`, как у всех слоёв). Кольцо использует горячий `impactColor` композиции, поэтому релиз читается и «ударил сюда», и «дальше безопасно» — читаемость зоны поражения без роста стоимости.

**Командное давление на реальном заклинании.** Вторичные цели из `Decision.supports` теперь питают живой путь `VOID_MARK`: пометка дополнительно падает на до двух **различных** вторичных игроков решения (только реальные combat-цели в том же мире). Число зон ограничено существующим потолком `MAX_ACTIVE_VOID_MARKS=2`, поэтому 20 игроков не получают неограниченный стек зон от одного каста. `currentBossBrainSupports` очищается во всех путях сброса/переназначения цели (сброс события, melee-swipe, фури-`strike`), чтобы устаревшие вторичные цели не «протекали» в несвязанные касты.

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: правка сброса, вторичные цели, кольцо удара. AUTOMATED-TESTED: полный локальный гейт повторно прогоняется на новом артефакте. PAPER-RUNTIME-OBSERVED: ожидается после переустановки артефакта на прод. NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ**, остаётся за владельцем.

### 10.7 §7 — фирменный «луч из груди по центру Ядра» (GIANT CENTER-CORE CHEST BEAM)

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 632 888 Б, SHA-256 `74223c7de41b6d1066d874787b336f7245ce3aa3b062c33cf76ef8fa2c6155d6`, SHA-512 `4b9e3fb15073c09d0a707073bb7e2254808f9dc341e65ad461f7fabec765b5e23b7015a41202f1d38f0eab075c0e56df9161b9b11cd4286d5ac74e55d7337407`. Собран тем же Gradle-путём (`migrationPlugins`, `JAVA_HOME` = Temurin 25.0.4.1), `BUILD SUCCESSFUL`. Пин `95da1854…` препенден в `replaceSha256` (сначала новый).

**Что это.** Спецификация §7 прямо требует «гигантский луч/ядерный залп из реального анимированного гнезда в груди» как авторскую сигнатуру, а не сферический AoE. Реализовано переиспользованием уже существующей машинерии контактного удара (`executeContactBossAbility` → `startChestBeam`), без нового клиентского меша: геометрия использует готовую `Shape.RIFT_LANCE`.

**Чистая политика `ChestBeamPolicy` (домен, без Bukkit).** Считает:
- число лучей по размеру группы: соло — 1, до 8 игроков — 2, больше — 3 (потолок `MAX_LANES=3`), чтобы 20 игроков не получали неограниченный залп;
- фазы замаха: `chargingAt` 50 тиков → `damagesAt` 18 тиков → `aftermathAt` 20 тиков, всего `TOTAL_TICKS=88`, кулдаун `COOLDOWN_TICKS=600`;
- `aimAt`/`along`/`perpendicular`/`beamHits` — точная серверная геометрия луча/капсулы (`BEAM_RANGE=26.0`, `BEAM_RADIUS=1.7`), урон `DAMAGE=16.0`, отброс `KNOCKBACK=0.85`, подброс `LIFT=0.22`;
- `selectLaneIndices` выбирает **различные** непересекающиеся дорожки по детерминированному seed, поэтому луч не «прилипает» к убегающему игроку;
- `mayCast`/`onCooldown` — гейт частоты.

**Фазы и честность.** Прицел фиксируется **до** выстрела (lock перед impact), урон наносится по точной серверной линии/капсуле, окно урона одно и ограниченное; твёрдое укрытие между Ядром и целью останавливает попадание; игрок вне объявленной дорожки не получает урона. Отмена/смерть/рестарт вызывают `abortChestBeam` + `clearWorldVfxBeamsByPrefix("boss-chest-beam-")`, поэтому «старый» луч не переживает смену фазы или поколения.

**§7.1.1 — захват Ядра.** Перед замахом Страж ограниченно (один безопасный пункт в пределах нескольких блоков от Ядра) перемещается к Ядру через `findSafeCombatLocation`, с не-damaging пульсом и звуком маяка. Если легальной точки нет — луч кастуется с текущей позиции (всё ещё честная, уворачиваемая дорожка), а не отменяется.

**Гейты.** `BossSpell.CHEST_BEAM` добавлен в пул фаз RIFT/OVERLOAD/RAGE (не в LAST_SEAL); `ArenaInfernoPolicy.allowsConcurrent` → `false` (никогда одновременно с летальным фронтом огня); `BossCastMobilityPolicy.allowsLocomotion` → `false` (Страж «вкапывается» на Ядре). Авторская композиция `BossSpellVisualCompositionPolicy` (`0xE040FF`, `END_ROD`/`DUST_MAGENTA`, `vortexTurns=3.5`, рёв дракона) доводит число авторских заклинаний до 28.

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: `ChestBeamPolicy`, enum/ability/geometry/composition/gates, живой путь `startChestBeam`/`renderChestBeamStage`/`finishChestBeam`/`abortChestBeam`, захват Ядра, очистка. AUTOMATED-TESTED: новый `ChestBeamPolicyTest` (313 проверок) + `BossSpellVisualCompositionPolicyTest` (411), `EndRiftAiPolicyTest`, `ArenaInfernoPolicyTest`, `BossPhaseGatingTest`, `BossSpellParticlePaletteTest`, `BossContactStrikePolicyTest` — все PASS; полный локальный гейт `RunEndRiftEventChecks.ps1` → `End Rift current local checks passed`. PAPER-RUNTIME-OBSERVED: ожидается после переустановки артефакта на прод (`cmend boss spell chest_beam`). NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ**, остаётся за владельцем (нужен F2/клип в подписанном клиенте).

### 10.8 Правка после первого прод-прогона §7: `chest_beam` не был доступен в тест-команде

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 632 983 Б, SHA-256 `c2572528270e22e204cff420d078b8e1a075b5bead0f414dc22f6ff932240ffc`, SHA-512 `1b7b1a3510cd32e98a0c601d0b428ff00a0422d733839a31233358b92090b62f7b1d0fd77a675d4773c54ee05b6f483da50a7508416ca43936c86e5449a22a33`. Пин `74223c7d…` препенден в `replaceSha256` (сначала новый).

**Найденный при живом прогоне баг.** После установки `74223c7d…` на прод живой прогон `cmend boss spell chest_beam` вместо каста вернул справку команды: парсер `/cmend boss spell` не знал идентификатор `chest_beam` (его не было ни в списке допустимых, ни в `switch`). То есть заклинание существовало в домене и в боевом контроллере, но **не было подключено к тест-команде**, поэтому владелец не смог бы его вызвать вручную. Это исправлено: `chest_beam` добавлен и в список, и в `switch` (`EndRiftAiPolicy.BossSpell.CHEST_BEAM`), справка обновлена.

**Повторный прод-прогон (после этой правки).** `cmend boss spell chest_beam` → `Boss spell test started: chest_beam`; `cmend debug invariants` → `failures=0`; `entityCount` возвращается к базовому после `cmend boss kill cleanup`, `bossProxies=0`, `tasks=0`.

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: подключение `chest_beam` к `/cmend boss spell`. AUTOMATED-TESTED: полный локальный гейт повторно зелёный на новом артефакте; пины lock/inventory/migration обновлены. PAPER-RUNTIME-OBSERVED: `cmend boss spell chest_beam` принят и отыгран на проде, инварианты без ошибок, тела и задачи не утекли. NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ** — как выглядит луч и как его уворачивает живой игрок, не проверено; нужен F2/клип владельца в подписанном клиенте.

### 10.9 Итерация «читаемость §7 и честный payload частиц» (22.11.2026, ночь)

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 634 674 Б, SHA-256 `9609e83f98650de0dc9a5964dce0f036441eb90906d13babac8094dd2f76494c`, SHA-512 `f21df4344006cd338c1e38d5197f449474d3827114b4fd67c7d632dde51dc6a92b6fb48f75636fde6b82833188f0634d3307dc5482ad4a0b51a7925d7711b71e`. Предыдущие кандидаты `27d239b3…` и `1504e248…` препендены в `replaceSha256` (сначала новые).

**Баг §7 (читаемость фирменного ультимейта).** Первые 50 тиков луча (0–49) — стадии заряда и захвата прицела — рисовали **ничего**: визуал луча существовал только в окне урона (тики 50–67) и в затухании. То есть игрок не видел захваченную полосу, пока луч уже наносил урон — ровно тот запрещённый сценарий «непредотвратимого попадания» и условие отказа «луч появляется после фиксации прицела». Исправлено: стадия заряда теперь рисует сжимающееся ядро в груди и **две контрастные кромки-рельса** на каждой захваченной полосе. Рельсы выводятся из той же `ChestBeamPolicy.Aim`, которой сервер наносит урон, поэтому нарисованное предупреждение не может быть уже или шире реального попадания. Рельсы обновляются и во время выстрела, чтобы граница опасности не исчезала посреди луча; стоимость ограничена (два пакета на полосу, максимум шесть при трёх полосах, раз в девять тиков — внутри 650 мс жизни луча). `ChestBeamPolicyTest` получил пробу геометрии рельсов (кромки лежат ровно на границе капсулы, сохраняют зафиксированное направление, устойчивы к вертикальной полосе).

**Баг частиц (латентный, но настоящий).** `BossSpellParticlePalette` объявляет, какой payload требует каждая частица. `FLASH` был объявлен payload-free (`NONE`) наравне с `SONIC_BOOM`/`SWEEP_ATTACK`, но Paper отдаёт для `Particle.FLASH` data-класс `org.bukkit.Color`, а не `Void`. Рендер, который бы заспавнил такую сигнатуру «голой», бросает `missing required data class` и роняет тик кадра — именно та опасность, против которой и написан контракт палитры (и которая уже один раз ломала волновые тики). Сейчас дефект латентен (политика ранга узлов запрещает `FLASH` на узлах силуэта), но объявление было неверным и упало бы, как только любой путь его нарисует. Добавлен payload `COLOR`, `FLASH` теперь резолвится в него, рендер подаёт `Color.fromRGB(signature.rgb())`. Проба фиксирует исправленный контракт и подтверждает, что действительно без-payload одноразовые частицы по-прежнему объявляют `NONE`. Все прочие имена сверены с реальным data-классом Paper и уже совпадали.

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: предупреждающие рельсы/ядро луча и payload `COLOR` для `FLASH`. AUTOMATED-TESTED: `ChestBeamPolicyTest` и `BossSpellParticlePaletteTest` расширены; пины lock/inventory/migration обновлены; полный локальный гейт зелёный. PAPER-RUNTIME-OBSERVED: `9609e83f…` установлен на прод, сервис `active`, `END_RIFT_INVARIANTS failures=0`, живой прогон `chest_beam`/`kagune_stab`/`kagune_eruption` принят (гейт цели/арены, 0 игроков), тела/задачи не утекли. NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ** — смотреть должны владелец в живом клиенте (F2/клип). Проверка payload — ND-JAVAP: data-класс `Particle.FLASH` = `org.bukkit.Color` (reflection против `paper-api-26.3.build.205-stable.jar`).

### 10.10 Итерация «живой краш фазы 6 и статический страж частиц» (22.11.2026, ночь)

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 634 677 Б, SHA-256 `b7251e5c723e870938ed4d65f17a96a1f506de9f5318bf6244ac7301bda26c6d`, SHA-512 `c4d3861c49acda8f1e009f2b95187880f27d9f0e06275a209ef6ab06cd7bacfb2a8c63e2558e742ae9f2ff344e188ddfa0525c705f12b15c3a4630745818f740`. Предыдущие кандидаты `9609e83f…` и `27d239b3…` препендены в `replaceSha256` (сначала новые).

**Настоящий живой баг фазы 6 (не латентный).** Всплеск вознесения при входе в `LAST_SEAL` (`spawnLastSealAscensionBurst`) спавнил `Particle.FLASH` **напрямую, без payload** — `viewer.spawnParticle(Particle.FLASH, …)`. Paper объявляет data-класс `FLASH` как `org.bukkit.Color`, поэтому голый спавн бросает `IllegalArgumentException: missing required data class`, и это исключение уносит **весь тик**. То есть ровно в тот кадр, когда арена масштабируется и владелец видит вход в шестую фазу (фаза, которая важнее всего), серверный тик падал. Это тот же самый класс ошибки, что уже один раз убивал прогон Wave 3 («the Wave 3 gate sweep died on Particle.FLASH this way»), и он был **подтверждён reflection-пробой** против `paper-api-26.3.build.205-stable.jar`, а не догадкой. Исправлено: всплеск теперь идёт через типизированный помощник `spawnTypedParticle`, который подаёт требуемый `Color` и не бросает на структурированных носителях.

**Долговечный страж против всего класса ошибки.** Динамические пробы (`test_end_rift_typed_particle_payloads`) проверяют общий адаптер, но не видят call-site, который его обходит. Добавлен статический тест `tests/test_end_rift_bare_typed_particle_guard.py`: он сканирует **весь** исходник плагина на литеральные вызовы `spawnParticle(Particle.<NAME>, …)` и падает, если частица с не-`Void` data-классом вызвана через «голую» 7-параметровую перегрузку (шесть запятых верхнего уровня) вместо типизированной. Набор типизированных частиц зафиксирован и сверен reflection-пробой (`FLASH`, `DRAGON_BREATH`, `SCULK_CHARGE`, `DUST`, `DUST_COLOR_TRANSITION`, `BLOCK`, `BLOCK_CRUMBLE`, `FALLING_DUST`, `BLOCK_MARKER`, `ITEM`, `ITEM_SLIME`, `ITEM_SNOWBALL`), плюс есть само-проба, что сканер ловит именно форму бага. После правки сканирование чистое по всему плагину. Тест встроен в полный гейт (шаг «Boss accepted hits, cast ownership and Kagune recovery»).

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: перенаправление `FLASH` в типизированный помощник на входе в фазу 6; статический страж частиц. AUTOMATED-TESTED: новый страж зелёный по всему плагину; пины lock/inventory/migration обновлены; полный локальный гейт зелёный. PAPER-RUNTIME-OBSERVED: `b7251e5c…` установлен на прод, сервис `active`, `END_RIFT_INVARIANTS failures=0`, живой прогон `chest_beam`/`kagune_stab`/`kagune_eruption` принят (гейт цели/арены, 0 игроков), тела/задачи не утекли. NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ** — вход в фазу 6 и вид всплеска вознесения должен подтвердить владелец в подписанном клиенте (F2/клип).

### 10.11 Итерация «закрытие R04: леджер депозиторов чистится во всех сбросах» (22.11.2026, ночь)

**Артефакт.** Без изменения jar: правка только тестов и документации. Действующий прод-кандидат остаётся `b7251e5c…` (2 634 677 Б).

**Дефект R04.** Леджер `resourceContributors` (кто вносил ресурсы в Ядро) в полных сбросах очищался не везде. После `resetEventSafely` (админский `/cmend reset confirm`) и `removeCore` (`/cmend core remove`) поле оставалось заполненным, тогда как `participantUuids` уже очищался. Следующий `cmend status` считал устаревшего депозитора участником и показывал `participants=1` при пустом `roster`. Влияло только на счётчик статуса и строку имён в объявлении победы — не на боевую логику. Исправление (очистка `resourceContributors` рядом с `participantUuids` во всех трёх полных сбросах: `resetEventSafely`, `removeCore` и `configureCore`) было внесено в рабочее дерево в предыдущем цикле, но не имело регрессионного теста и всё ещё значилось как «в следующем цикле».

**Регрессионный тест.** Добавлен `tests/test_end_rift_reset_clears_depositor_ledger.py`: он статически берёт тела методов `resetEventSafely` и `removeCore` (по объявлению с типом возврата, чтобы вызовы не путались с объявлением) и падает, если в любом из них `participantUuids.clear();` есть, а `resourceContributors.clear();` нет. Есть само-проба, что сканер действительно нашёл настоящее тело метода. Тест встроен в полный гейт (шаг «Current Python contract»).

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: очистка леджера во всех полных сбросах. AUTOMATED-TESTED: новый тест зелёный; встроен в гейт; полный локальный гейт зелёный. PAPER-RUNTIME-OBSERVED: не требуется — поведение не изменилось относительно уже развёрнутого `b7251e5c…`; статус после сброса на проде не проверялся в этой под-итерации (нет живого сброса под координацией владельца). NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕ ПРИМЕНИМО** (не визуальный дефект).

### 10.12 Итерация «Залп Печати: копья действительно летят в более чем одного игрока» (22.11.2026, ночь)

**Артефакт.** `build/minecraft-26.3/plugins/jars/CopiMineEndEvent-26.3.jar`, 2 635 389 Б, SHA-256 `34a045f4cab5da8310a2e589f2bc6952b331623518f9e288463952861facdccd`, SHA-512 `2c9f978051d8a7300940ae1d9fa697be4107c824ca64dd785d268af019f32dfeb42c447fb7be83234c91f05c5f49a31cb623e0d1db2377a19a486edc7e8bfd7c`. Предыдущий кандидат `b7251e5c…` (и далее) препенден в `replaceSha256` (сначала новые); блок индексов пина сдвинут на +1.

**Дефект (невыполненное требование военного времени).** §6 канонических волн и §9G.03 промпта требуют, чтобы **Залп Печати** (`Seal Lance Volley`) — фирменное заклинание копья фазы 6 (`KAGUNE_STAB`) — летел **в более чем одного игрока** («2–3 допустимые цели получают видимое предупреждение сверху/с земли; зафиксировать прицел и выпустить короткий залп»). Фактически диспетчер брал **ровно одну** цель (`currentBrainTarget`) и создавал **ровно один** конструкт-носитель, поэтому залп никогда не мог поразить больше одного игрока, а обещание группового давления было мёртвым кодом. Одиночная цель/одиночное копьё противоречит и §5 «не навсегда запирать одного игрока».

**Исправление (ограниченное, в духе «одна крупная каст-стадия за раз»).**
- Добавлен ограниченный ладдер целей `lanceVolleyTargets(boss, primary)`: ведёт мозговой primary, затем уже проранжированные **различимые** secondary из `currentBossBrainSupports` (сохраняют задуманную форму группового комбо), затем ближайшие оставшиеся допустимые участники. Цели всегда различимы и законно поражаемы.
- Число дорожек — `clamp(живая группа, 1, MAX_LANCE_LANES=3)`: соло остаётся честно одно копьё, дуо получает две, крупная группа — три (потолок для 20 игроков). `MAX_LANCE_LANES` вынесен константой.
- Каждая дорожка — **свой** `LastSealConstructRuntime` со своим носителем, контактным реестром и геометрией (`newConstructLane()`), но весь залп остаётся **одной крупной каст-стадией**: `isKaguneRingCastActive()`/гейты отказа идут через `hasActiveConstructLane()`, а `tick`/`clear`/`consumeFall` итерируют список дорожек. Бюджет-резервация одна: `finished` освобождает её **только** когда не осталось активных дорожек, поэтому первый упавший в цель луч не открывает слот для новой каст-стадии.
- Ориентация копья и «не пробивать сквозь стену/Ядро» остаются в прежнем, уже проверенном `LastSealConstructRuntime` (класс для одиночного/эруптивного случая не переписывался). `KAGUNE_ERUPTION` сохраняет один якорь (он сам выращивает следы на товарищей).
- Активная/устаревшая дорожка снимается по `done()` (отменена и носитель полностью удалён), чтобы список оставался ограниченным.

**Долговечный страж.** Добавлен `tests/test_end_rift_seal_lance_volley_guard.py` (5 тестов): потолок `MAX_LANCE_LANES = 3` и клам `clamp(party,1,MAX)`; диспетчер `KAGUNE_STAB` строит залп через `lanceVolleyTargets` и стартует по дорожке на цель; различимость и законность целей; залп остаётся одной крупной каст-стадией (нет старого одиночного поля `lastSealConstruct`); у рантайма есть ограниченный `done()`. Тест встроен в полный гейт (шаг «Current Python contract»).

**Уровни доказанности (эта под-итерация).** CODE-IMPLEMENTED: многодорожечный залп по различимым целям с потолком 3, единая каст-стадия и единая резервация. AUTOMATED-TESTED: новый страж зелёный; полный локальный гейт зелёный (145 passed, 2 skipped); пины lock/inventory/migration и локальный фикстур обновлены; Gradle-сборка `BUILD SUCCESSFUL`. PAPER-RUNTIME-OBSERVED: новый кандидат `34a045f4…` развёрнут на проде, сервис `active`, живой зонд `chest_beam`/`kagune_stab`/`kagune_eruption` принят, тела/задачи не утекли (см. финальный отчёт). NATIVE-CLIENT-VISUALLY-VERIFIED / OFFICIAL-ACCEPTED: **НЕТ** — одновременный залп по более чем одному игроку и вид двух-трёх копий должен подтвердить владелец на реальной группе (F2/клип); в одиночку/на 0 игроков это поведение неотличимо от одиночного копья и потому не может быть визуально принято агентом.
