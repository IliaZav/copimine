# Аудит BUG_LEDGER после пересборки End Rift

Дата: 2026-11-19
Ветка: `codex/rift-guardian-merge-20261117`, аудируемый коммит `dc7a56d3`.
Источник строк: `docs/end-rift/final-26.3/BUG_LEDGER.md` (пин исходников
`a703c921`).
Действующий закон: `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md`
(далее - MASTER). Канонические спецификации перепроверены перед аудитом и не
изменялись:

* `COPIMINE_END_RIFT_WAVES_CANONICAL.md` = `6e1245b4...` (47126 Б);
* `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` = `0c781e42...` (51711 Б).

Статусы строки: CODE-MISMATCH (реальный конфликт кода и спецификации),
CODE-RISK (риск без доказанного воспроизведения), USER-REPORTED (сообщение
игрока), SOURCE-FIXED/TESTED (исходник исправлен и покрыт тестом),
NATIVE-UNVERIFIED (не проверено в живом Minecraft).

Словарь доказательств, применяемый ниже:

* **IMPLEMENTED** - путь кода есть в поставляемых исходниках.
* **AUTOMATED-TESTED** - тест гоняет реальный путь кода.
* **LIVE-PAPER-VERIFIED** - проверено на живом Paper.
* **VISUAL-VERIFIED** - подтверждено настоящим клиентом.
* **NOT VERIFIED** - ни то, ни другое.
* **BLOCKED** - проверка невозможна в текущем окружении; BLOCKED никогда не
  считается проходом.

Пятнадцать открытых строк относятся к срезу `a703c921`. Часть из них уже
перекрыта более поздним MASTER (Rift Ram вместо ритуальной сферы, единая
открытая арена седьмой волны, §17.1 "never resurrect a legitimately destroyed
guardian"). Такой конфликт документов снимается в пользу MASTER, а не
объявляется багом кода.

## 1. Сводка

| ID | Тема | Вердикт аудита | Доказательство |
|---|---|---|---|
| G01 | Интеграция/потеря работы | ЗАКРЫТО для текущего дерева | `git status`, регионный пин |
| W303 | Портал: частичное создание слоёв | ИСПРАВЛЕНО, AUTOMATED-TESTED | `spawnPortalModelVisual`, `test_end_rift_portal_layer_runtime.py` |
| W304 | Портал: ориентация ItemDisplay | НЕ ДЕФЕКТ | `portalDisplayOrigin`, `Billboard.FIXED`, новый тест |
| W403 | Волна 4: повторный/чужой fireball | ИСПРАВЛЕНО, AUTOMATED-TESTED | гейт generation+owner, `test_end_rift_wave4_objective_dispatch_once.py` |
| W606 | Волна 6: подсветка пленника | ИСПРАВЛЕНО, AUTOMATED-TESTED | `PrisonerTargetSelector`, `PrisonerTargetRayPolicyTest` |
| W608 | Волна 6: цель мага/пленник | ИСПРАВЛЕНО, AUTOMATED-TESTED | `RitualTargetPolicy`, provenance-тесты |
| W701 | Волна 7: официальные испытания | ИСПРАВЛЕНО, AUTOMATED-TESTED | `Wave7TrialRoutePolicy`, `NamedTrial` |
| W702 | Волна 7: persisted snapshot | ИСПРАВЛЕНО, AUTOMATED-TESTED | schema 5, upgrade 4->5 |
| B02 | Boss: финальный hover | IMPLEMENTED (визуал NOT VERIFIED) | `BossFinalSealAnchorPolicy`, `BossFinalSealAnchorPolicyTest` |
| B04 | Boss: магма 20 с | ИСПРАВЛЕНО | `config.yml`, `InfernoArenaLifecyclePolicy` |
| B06 | Boss: застревание/двойные таймеры | ИСПРАВЛЕНО, AUTOMATED-TESTED | `BossCastTimeline`, `BossOrientedHitboxPolicy` |
| K01 | Kagune: воскрешение защитника | ИСПРАВЛЕНО (по MASTER §17.1) | `TentacleGuardianPolicy`, `GuardianDeathLedger` |
| K03 | Kagune: спам во время неуязвимости | ИСПРАВЛЕНО | `applyTentacleGuardianDamage` |
| K04 | Kagune: геометрия щупальца | НЕ ДЕФЕКТ + новый контракт-тест | рендер 4.878 в полосе 4.5-5 |
| R01 | Recovery: DepositJournal | ИСПРАВЛЕНО, AUTOMATED-TESTED | `DepositJournalTest.java` |

Итог: 13 строк закрыты исправлением/тестом, 2 признаны не дефектом при
сохранении визуального остатка (K04, W304). Ни одна закрытая строка не
закрыта ослаблением ужесточённой проверки.

## 2. Пояснения по строкам

### G01. Интеграция/потеря незакоммиченного

Строка писалась, когда `7f722fd5` не был опубликован, а worktree содержал
20 dirty-файлов. На аудируемом дереве `git status --porcelain` показывает
только восемь ожидаемых артефактов, которые переписывает сам gate:
`admin-web/frontend/assets/public-data/modpack_snapshot.json`,
`copimine-narcotics/CopiMineNarcotics.jar`, `thirdparty/*`. Незакоммиченной
работы по End Rift нет, отдельная ветка опубликована и совпадает с `origin`.
Вердикт: для текущего дерева угрозы нет; риск остаётся историческим, так как
двадцатифайловый срез не восстанавливался.

### W303. Портал: частичное создание слоёв (CODE-RISK)

`spawnPortalModelVisual` доводит транзакцию создания трёх слоёв
(FRAME/INNER/SHARD) до конца: при отказе любого слоя уже созданные
откатываются, повтор ограничен бюджетом, нерезолвленные модели
(`EndEventDisplayItems.hasResolvableModel`) отклоняются с
`END_RIFT_PORTAL_MODEL_UNRESOLVED`. Кадры жизненного цикла, включая
`sibling-removed-during-transaction`, `reload-during-spawn`,
`exhaustion-safe-recovery`, `exhausted-loaded-sibling`, проверяются
`tests/test_end_rift_portal_layer_runtime.py`, а обязательство по девяти
наблюдаемым слоям - в `test_wave3_live_runner_checks_all_nine_observed_portal_layers`.
Вердикт: ИСПРАВЛЕНО, AUTOMATED-TESTED.

### W304. Портал: ориентация ItemDisplay (CODE-RISK неверной ориентации)

Это не дефект. `portalDisplayOrigin` строит точку показа с `portal.getYaw()` и
`portal.getPitch()` (`CopiMineEndEvent.java`, около 43930-43938). При создании
слоя выставляется `entity.setBillboard(Display.Billboard.FIXED)` (около
43746) - именно фиксированный билборд применяет yaw сущности; центрированный
его бы отбросил. Матрица создания - только перенос и масштаб
(`new AxisAngle4f()`), а вращение из `AxisAngle4f(yaw, y, z)` добавляется в
кадре анимации (около 44021-44024) как idle-кручение поверх yaw. Добавлен
контракт-тест
`tests/test_end_rift_portal_layer_runtime.py::test_portal_displays_keep_the_authored_yaw_with_a_fixed_billboard`,
фиксирующий эти четыре факта. Визуальная ориентация в настоящем клиенте -
NOT VERIFIED (см. раздел 3).

### W403. Волна 4: повторный или чужой fireball (CODE-RISK)

`handleRiftFireballImpact` и `onRiftFireballHit` пропускают попадание только
для своей сессии и поколения (`generation` + `ownedBySession`) и гасят
снаряд ровно один раз, поэтому один чужой или повторный fireball не даёт
лишних засчитанных попаданий обелиска. Покрыто
`tests/test_end_rift_wave4_objective_dispatch_once.py` и
`tests/ObeliskIntegrityPolicyTest.java`. Вердикт: ИСПРАВЛЕНО,
AUTOMATED-TESTED.

### W606. Волна 6: подсветка пленника (CODE-RISK трассы)

`PrisonerTargetSelector.computePreview` строит собственную ограниченную
камерную луч-трассу, отфильтровывает недопустимые сущности до выбора
ближайшей и больше не обрезает список (`limit`/`subList`/`256` отсутствуют).
Покрыто `tests/test_end_rift_prisoner_client_boundaries.py`,
`tests/test_end_rift_prisoner_target_eligibility.py` и клиентским
`PrisonerTargetRayPolicyTest`. Живая проверка на игроке - NOT VERIFIED.

### W608. Волна 6: цель мага и пленник (USER-REPORTED/CODE-RISK)

`RitualTargetPolicy.freeTargets` исключает пленника из свободных целей; весь
выбор цели заклинателя идёт через `ritualFreeTargets`, а происхождение
снаряда проверяет `ritualSphereProjectileProvenanceAllowed`. Покрыто
`tests/RitualTargetPolicyTest.java`,
`tests/test_end_event_ritual_chains_target_contract.py`,
`tests/test_end_event_ritual_projectile_provenance_contract.py` и
`tests/test_end_rift_wave6_visual_mechanics.py`. Живая проверка - NOT VERIFIED.

### W701. Волна 7: официальный маршрут (CODE-MISMATCH)

Живой официальный маршрут - `RealitySplitTrialController.NamedTrial`
(WARDEN -> MOBILE_MARKSMAN -> RIFT_ARCHMAGE -> PERSONAL_ECHO) через
`beginNamed` и `Wave7TrialRoutePolicy.ORDER`; `startRealitySplitTrials`
направляет в `startNamedRealitySplitTrials`. Легаси-путь
(RIFT_REFLECTION/JUGGERNAUT/RIFT_HUNTER) остаётся только для миграции и
отклоняется с `WAVE7_LEGACY_RUNTIME_CANNOT_START_NAMED_TRIALS`. Вердикт:
ИСПРАВЛЕНО, AUTOMATED-TESTED.

### W702. Волна 7: persisted snapshot (CODE-RISK)

`RealitySplitTrialSnapshot` поднят до schema 5 с апгрейдом 4->5
(`upgradeKnownNamedSchema`); легаси schema-1 и файл без версии закрываются
(fail-closed), а не восстанавливаются вслепую. Покрыто
`tests/RealitySplitTrialSnapshotTest.java` (`namedSnapshots`) и
`tests/Wave7TrialMigrationTest.java`. Вердикт: ИСПРАВЛЕНО,
AUTOMATED-TESTED.

### B02. Boss: финальный hover (CODE-RISK + дизайн)

`BossFinalSealAnchorPolicy.HOVER_HEIGHT = 2.5`;
`finalSealCoreAnchorLocation()` возвращает верх ядра + 2.5, и босс
телепортируется только если вышел за допуск 0.15. Покрыто
`tests/BossFinalSealAnchorPolicyTest.java`. Нативный hover над ядром в
клиенте - NOT VERIFIED.

### B04. Boss: длительность магмы (CODE-MISMATCH конфигурации)

`config.yml` содержит `boss.arena-inferno.duration-ticks: 400` (20 секунд при
20 TPS), значение читается в `CopiMineEndEvent.java` (около 51685) и
согласовано с `InfernoArenaLifecyclePolicy.BASELINE_DURATION_TICKS = 400`.
Пятисекундное значение 100 тиков не используется. Вердикт: ИСПРАВЛЕНО.

### B06. Boss: застревание атак, два таймера, ложная коллизия (CODE-RISK)

Единый `tick()` владеет ИИ босса и дедлайн-сторожем
`BossCastTimeline.reconcile` (событие `BOSS_CAST_RECONCILED` снимает
блокировку подвижности), а пересечение считается точным OBB-тестом
`BossOrientedHitboxPolicy` вместо повёрнутого AABB. Покрыто
`tests/BossOrientedHitboxPolicyTest.java`,
`tests/BossHitboxDedupePolicyTest.java`, `tests/BossCastTimelineTest.java`.
Вердикт: ИСПРАВЛЕНО, AUTOMATED-TESTED.

### K01. Kagune: воскрешение защитника (CODE-MISMATCH)

Строка требовала возврата каждого защитника через 90 секунд. MASTER §17.1
запрещает воскрешать законно уничтоженного защитника и имеет приоритет.
Код: `TentacleGuardianPolicy.RESPAWN_DELAY_TICKS = 1800`;
`GuardianDeathLedger.recordDeath/isDestroyed/markCocoonBroken` блокируют
повторное появление; массового `restoreTentacleGuardians` в дереве нет.
Вердикт: ИСПРАВЛЕНО относительно действующего закона; требование 90 секунд
снято как устаревшее.

### K03. Kagune: спам во время анимационной неуязвимости (CODE-RISK HIGH)

`applyTentacleGuardianDamage` отклоняет попадание целиком ДО списания HP,
пока `TentacleAnimationPolicy.shouldEnterHitRecovery` не разрешит вход в
recovery. Раньше HP списывалось до проверки окна. Вердикт: ИСПРАВЛЕНО.

### K04. Kagune: геометрия щупальца (CODE-RISK неверной геометрии)

Не дефект. Каноническая модель - "примерно 4.5-5 блоков высотой".
Авторизованная высота рига, прочитанная из
`CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json`,
равна 7.8681 единиц; рендер применяет
`EndRiftTentacleWorldScalePolicy.BASE_LENGTH_RENDER_SCALE = 0.62F`, что даёт
7.8681 * 0.62 = 4.878 блоков - внутри полосы 4.5-5. Серверная логическая
длина 6.25 - намеренный pose-safe конверт, покрывающий выставленную позу.
Добавлен контракт-тест
`tests/test_end_event_resource_visual_contract.py::test_rendered_tentacle_height_stays_inside_the_canonical_band`:
он выводит высоту из геометрии, требует попадания рендера в полосу 4.5-5 и
того, чтобы серверный конверт был не меньше рендера. Геометрия при этом не
менялась. Нативный размер на экране - NOT VERIFIED.

### R01. Recovery: DepositJournal (CODE-RISK)

Журнал депозитов: append с fsync; оборванная последняя строка игнорируется;
полная, но повреждённая запись закрывается (fail-closed); `generation <= 0`
отклоняется; записи другой генерации и легаси-записи без области (unscoped)
закрываются. Всё это покрыто `tests/DepositJournalTest.java` (fault
injection). Вердикт: ИСПРАВЛЕНО, AUTOMATED-TESTED.

## 3. BLOCKED: что нельзя проверить здесь

BLOCKED - не проход. Следующее остаётся неподтверждённым.

**Клиент (нужен реальный Minecraft 26.3 с сборкой клиента):**

* походка Echo и отсутствие зависимости скорости от FPS;
* момент взмаха/ранения только после события, затемнение кожи и свечение
  глаз с первого кадра;
* тело двойника Rift Guardian на носителе Pillager, независимость позы и
  клипов у копий, непрерывность и скорость петли двойника;
* читаемость оттенка копии;
* размер щупальца K04 и ориентация портала W304 на экране;
* нативный hover босса над ядром (B02);
* визуал волны 6: сейсмический удар, залп стрел, падение скелета, коллайдер
  колонны.

**Живая приёмка:** прогонов на 2, 5, 10, 11 и 20 игроках и изолированных
Paper-сценариев нет - на сервере 0 игроков. Не измерены MSPT/TPS, FPS клиента
и потери пакетов.

**Развёртывание в продакшн:** SSH к `server-rpgrp` / `100.108.97.11:22` и
`qwerty@90.188.115.155:2222` обрывается на `kex_exchange_identification` /
`Connection closed by remote host`. Установленный на сервере JAR и состояние
systemd не проверены.

Матрица требований остаётся 149 BLOCKED / 6 NOT RUN / 13 PASS с
`nativeVerified=false`; §12 запрещает "озеленять" нативные пункты.

## 4. Внешний отчёт и его срез

Внешний аудит делался по коммиту `f35416c4`, который является предком
`dc7a56d3` и отстаёт на 11 коммитов. Его пункты P0/P1 (урон сейсмического
удара, дождь стрел сверху, настоящее падение скелета, журналируемый
коллайдер колонны, учёт туманных мобов по каждой сущности, один broadcast
Kagune, квитанция Last Seal после `host.hit`) уже исправлены в `7957dfda` и
записаны в
`docs/end-rift/2026-11-18/WAVE6_KAGUNE_FOG_LASTSEAL_FIXES_AND_REPIN.md`.
Клиентские замечания Echo закрыты в `745eaa9e` и записаны в
`docs/end-rift/2026-11-19/ECHO_CLIENT_TICK_AND_EYE_FIXES_AND_REPIN.md`
(per-tick `EchoAnimationTickGuard`, serial baseline `prime`, безусловный
`EchoEyePassPlan` BODY).

## 5. Границы доказательств

* Все выводы выше - IMPLEMENTED / AUTOMATED-TESTED. Ни одна строка не
  объявлена LIVE-PAPER-VERIFIED или VISUAL-VERIFIED.
* Исправления не двигали `copimine-end-event` и клиентские исходники в рамках
  этого аудита: добавлены только тесты, поэтому пины артефактов не менялись.
  Кандидат плагина остаётся `2582610` Б /
  `987f31a7...`; пин клиента - `30 252 288` Б / `3205690d...`.
* Ни один ранее пройденный тест не ослаблен; два новых теста добавляют
  проверки (полоса геометрии K04 и ориентация портала W304).
