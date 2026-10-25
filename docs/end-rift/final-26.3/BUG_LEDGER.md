# Реестр проблем End Rift — аудит 13.10.2026

Статусы: CODE-MISMATCH (проверенное расхождение с утверждённой механикой), CODE-RISK (вероятный баг по конкретному пути, требуется reproduction), USER-REPORTED (исторически увидено игроком, актуальную сборку надо перепроверить), SOURCE-FIXED/TESTED (прошли исправления/контракты, но не обязательно Minecraft), NATIVE-UNVERIFIED (нет полноценного игрового доказательства).
Состояние относительно source SHA a703c921. Дата сообщения о баге не тождественна дате актуального дефекта.

| ID | Участок | Наблюдение / риск | Доказательство | Статус |
|---|---|---|---|---|
| G01 | Интеграция | End Rift опубликован на 7f722fd5, локальная worktree содержит 20 dirty файлов | git worktree/status 13.10 | CODE-RISK: потеря незакоммиченного |
| G02 | Сборка 26.3 | Геймплейные циклы не подтверждены реальным клиентом на финальном SHA | ledger, signed native acceptance отсутствует | NATIVE-UNVERIFIED |
| G03 | Общая архитектура | Official и test адаптеры ранее имели разные цели/переходы, Wave 7 мог стать обычным mob spawn | WAVES_REPAIR_LEDGER A1/C2 | SOURCE-FIXED/TESTED, регрессия обязательна |
| G04 | Core | При начале волны оставалась старая пульсация/запрещённая sweeping wave | жалобы пользователя | USER-REPORTED |
| G05 | Руна | Неправильный следующий старт, двойные интервалы и прежние 20с перед боссом | старый END_RIFT_EVENT_GUIDE_RU против нового PreBossTransitionController=40_000 | SOURCE-FIXED/TESTED, native check |
| G06 | Мобы | Стояли без AI/агро, не атаковали, не показывали красную hurt-вспышку | WAVES_REPAIR_LEDGER B1/B2, жалобы | SOURCE-FIXED/TESTED, NATIVE-UNVERIFIED |
| W101 | Волна 1 | Слишком тяжёлый старт, стрелявшие скелеты, запрещённые сектора | config wave-1.skeletons=0 на 13.10 | ранее USER-REPORTED; частично исправлено |
| W102 | Волна 1 | Слабый/отсутствующий луч, нет подсветки носителя, выпадение tiny paper | журнал 07.10 о transform/beam и баги игрока | SOURCE-FIXED/TESTED; NATIVE-UNVERIFIED |
| W103 | Волна 1 | Устаревший Wave Front/ring и повторная доставка/loot | WAVES_REPAIR_LEDGER C1 и пользователь | USER-REPORTED, regression |
| W201 | Волна 2 | Метка игрока без полноценного shader, недостаточная читаемость | reports + checkpoint 07.10 | USER-REPORTED, NATIVE-UNVERIFIED |
| W202 | Волна 2 | Неверный зачёт охоты при смерти/quit на deadline | checkpoint 07.10 | SOURCE-FIXED/TESTED |
| W301 | Волна 3 | Маленькие некрасивые 3D ворота, плохие кадры/ориентация | личная жалоба игрока | USER-REPORTED |
| W302 | Волна 3 | Не растёт полоса захвата/не закрывается портал/сразу активны все | WAVES_REPAIR_LEDGER C2, checkpoint 07.10 | SOURCE-FIXED/TESTED, NATIVE-UNVERIFIED |
| W303 | Волна 3 | FRAME/INNER/SHARD создаются частично без fail-closed | CopiMineEndEvent.spawnPortalModelVisual / spawnPortalObjectiveVisuals | CODE-RISK |
| W304 | Волна 3 | portal.getYaw сохраняется в Location, но ItemDisplay Transformation задаёт identity quaternion | spawnPortalModelLayer | CODE-RISK: проверить неверную ориентацию |
| W401 | Волна 4 | Обелиски отображались обычными блоками, UV/текстуры не работают | жалоба; MODEL_RIFT_OBELISK_FULL/DAMAGED/CRITICAL есть | USER-REPORTED, NATIVE-UNVERIFIED |
| W402 | Волна 4 | Критическое состояние/масштаб, нет отражения, голодание спецатаки | checkpoint 07.10 и WAVE4_SEQUENCE_REPAIR | SOURCE-FIXED/TESTED, visual pending |
| W403 | Волна 4 | Один чужой или повторный fireball может дать не один обелиск hit | историческое security review | CODE-RISK, test identity validation |
| W501 | Волна 5 | Отсутствовали fog и safe green zones, плохая видимость | жалобы игрока | USER-REPORTED, NATIVE-UNVERIFIED |
| W502 | Волна 5 | Fog начинается без успешного safe-zone journal | WAVES_REPAIR_LEDGER D | SOURCE-FIXED/TESTED, failure injection |
| W503 | Волна 5 | Мобы остаются frozen/noAI после fog | checkpoint 07.10 и жалобы | SOURCE-FIXED/TESTED, native check |
| W601 | Волна 6 | Сфера лежит на Core и закрывает весь обзор пленнику | жалобы игрока; presentation policy в коде есть | USER-REPORTED |
| W602 | Волна 6 | Пять магов смотрят не туда, руки вперёд, нет cast/лучей | жалобы игрока; caster-hand tests исторически | USER-REPORTED |
| W603 | Волна 6 | Нет мобов и способностей защитников, недостаточно HP | пользовательские скрины/тесты | USER-REPORTED |
| W604 | Волна 6 | Иконки лежат на инвентаре, неверные подсказки клавиш | предыдущая жалоба, современный Q/W/E/R | USER-REPORTED |
| W605 | Волна 6 | СОЮЗНИК НЕ ПОДСВЕЧИВАЕТСЯ при наведении | прямое наблюдение игрока; PrinterTargetSelector/Outline mixins есть | USER-REPORTED, NATIVE-UNVERIFIED |
| W606 | Волна 6 | Подсветка зависит от камеры, sphere collider, server eligibility и лимита 256 entities | PrisonerTargetSelector.computePreview/outlineColor; ritualPrisonerTargetEligibility | CODE-RISK: проверить трассу |
| W607 | Волна 6 | Нет управляемой команды убить одного мага/стража или повторить stage | жалоба пользователя | USER-REPORTED, проверить current help |
| W608 | Волна 6 | Маг→проектайл напрямую вместо маг→сфера→проектайл→свободный игрок; повреждение пленника | история код-ревью 16.09 | USER-REPORTED/CODE-RISK, проверить текущий runtime |
| W701 | Волна 7 | Вместо Echo/Marksman/Archmage всё ещё Reflection/Juggernaut/Hunter | RealitySplitTrialController, CopiMineEndEvent | CODE-MISMATCH, обязательно исправить |
| W702 | Волна 7 | Старые роли прописаны в persisted snapshot, переименование приведёт к порче restore | RealitySplitTrialSnapshot | CODE-RISK, нужна версия schema |
| W703 | Волна 7 | Уродливые/неполные стены; проходы сверху, у Core, сквозь диагональ; projectile bypass | жалобы пользователя, старые barrier tests | USER-REPORTED, NATIVE-UNVERIFIED |
| W704 | Волна 7 | Смерть/bed/rejoin/restart может дублировать предметы/расширять 120с return window | WAVE7_RETURN_* и ledger | SOURCE-FIXED/TESTED частично, native restart pending |
| B01 | Boss | Гуляет/парит в обычных фазах вместо нормального grounded combat | пользовательские жалобы | USER-REPORTED |
| B02 | Boss | Последняя фаза принудительно закреплена у Core, реальный hover над ним не доказан | pinBossToFinalSealCore / BossAbilityId | CODE-RISK + дизайн |
| B03 | Boss | Не реализована/не проверена дальняя атака на 5 залпов с третьей фазы и farthest-fireball | user-approved design, тестов реального боя нет | USER-REPORTED / acceptance gap |
| B04 | Boss | Магма задумана 20с, конфигурация задаёт 100 тиков (5с при 20 TPS) | config.yml boss.arena-inferno | CODE-MISMATCH конфигурации, проверить usage |
| B05 | Boss | Модель, неверная UV, scale, хитбокс, shields, bossbar | пользовательские скрины, старые code reviews | USER-REPORTED, NATIVE-UNVERIFIED |
| B06 | Boss | Атаки застревают, два независимых schedulers, false collision rotated AABB | исторический code review | CODE-RISK, исследовать актуальный |
| K01 | Kagune | **НОВОЕ ТРЕБОВАНИЕ: вернуть каждого защитника через 90 секунд, а не 40 секунд или никогда**; прежний код после 15 секунд массово восстанавливает guardian-слоты и сбрасывает cooldown | TentacleGuardianPolicy.RESPAWN_DELAY_TICKS=800; CopiMineEndEvent.restoreTentacleGuardians | CODE-MISMATCH, обязательное исправление |
| K02 | Kagune | Захват разваливает модель/сегменты падают | жалоба по F2/игре | USER-REPORTED |
| K03 | Kagune | Спам во время анимационной неуязвимости всё ещё может уменьшить HP: tentacleDamageWindowOpen() проверяет только активный бой, а applyTentacleGuardianDamage() сначала списывает HP, затем проверяет shouldEnterHitRecovery() | CopiMineEndEvent.onTentacleGuardianDamage/applyTentacleGuardianDamage | CODE-RISK HIGH, проверка через полный адаптер |
| K04 | Kagune | Щупальца слишком маленькие; 6.25 блоков из старого guide против модели 4.5–5 | новый пользовательский дизайн + config hitbox-height=6.25 | CODE-RISK неверной геометрии |
| K05 | Kagune | Слишком мало HP, требуется около 3× старого баланса | запрос пользователя; config budget, temp health=20 | USER-REPORTED, баланс через реальные прогоны |
| R01 | Recovery | Torn DepositJournal, generation=0, partial append и unsafe restart | прежний журнал ошибки и аудит; новые тесты | CODE-RISK, повторить fault injection |
| R02 | Recovery | Задача старой попытки очищает новые entities/effects после restart | EncounterResourceScope, WAVES_REPAIR_LEDGER U16 | SOURCE-FIXED/TESTED, повторить |
| R03 | Профиль клиента | Старые F2, тесты и CI могут быть с 1.21.1 или старым 26.3 client | незавершённая native 26.3 приёмка | NATIVE-UNVERIFIED |

При исполнении: каждую строку проверить в новом HEAD. Закрывать только конкретным тестом/логом/скрином на соответствующем SHA; нельзя писать «поправлено» по факту наличия класса. Не исправлять уже исправленный баг второй раз, если новый regression проходит.

## Продолжение исходного аудита 13.10 — дополнительно проверено по Java-коду

| ID | Степень | Ошибка/причина | Обязательная проверка |
|---|---|---|---|
| K06 | CODE-MISMATCH | tickCurrentTentacles() вызывает restoreTentacleGuardians() после 15 секунд all-dead window; этот метод вызывает tentacleGuardianRespawnAt.clear() и переполняет все guardian HP. Результат: **досрочный общий респавн**, независимо от 90 секунд. | Один slot killed, all killed, t=15/89/90, реальные сроки; без reset на 15с |
| K07 | CODE-MISMATCH | reconcileGuardianShieldOrbit() всегда создаёт четыре щита через List.of(0,1,2,3). Реальные livingTentacleGuardianSlots() не используются. | 0/1/2/5/6 guardian живы => столько же моделей щитов и корректные UUID |
| K08 | CODE-RISK HIGH | tentacleGuardianRespawnAt/HP/damageWindowStartedTick в оперативной памяти; objectiveProgressSnapshot() кодирует W6/W7/pre-boss без явного снимка guardian timers в LAST_SEAL. | Перезапуск в финальной фазе, 30/60/89с после kill, не терять прогресс/HP, fail-closed |
| K09 | CODE-RISK HIGH | onTentacleGuardianDamage(ignoreCancelled=false) протоколирует отмену удара, но затем вручную наносит урон даже без проверки previous event.isCancelled(). | WorldGuard deny, чужой/отменённый удар, стрелы, без нелегального урона |
| K10 | CODE-RISK HIGH | permanentTarget вычисляется каждый tick от активных игроков; trimTentacles может менять набор слотов при quit/rejoin. | Изменение группы во время cooldown не меняет dead slots, сроки и boss shielding |
| K11 | CODE-MISMATCH | EventConfig.tentacleGuardianTuning() отвергает respawn delay свыше 1600, так что просто поставить 1800 в YAML не позволит запускать plugin. | Чистая загрузка config c 1800, неверные границы отклоняются безопасно |
| P01 | CODE-RISK HIGH | spawnPortalModelVisual() допускает частичную FRAME/INNER/SHARD модель; PortalPresentationPolicy.shouldRebuild() допускает лишь первую попытку, а попытка помечается ещё до успешного появления. | Инъекция двух последовательных неудач создания слоя; ограниченный retry и полный rollback без ghost displays |
| B07 | CODE-MISMATCH | arenaInferno() использует 100 тактов (5с) в отдельной константе, игнорируя желаемые 20 секунд. | Реальный таймер 20с, начало/cleanup, никакого двойного источника config |
| B08 | CODE-MISMATCH | Магма использует safeLane по координатным секторам, сообщение «безопасные сектора — пять секунд», урон и Weakness, без полной согласованной steam/ignite/armor механики. | 20с, честный телеграф, нет секторов, ограниченный огонь/броня, жерла, восстановление блоков |
| W705 | CODE-MISMATCH | Wave7AdmissionPolicy содержит WARDEN/ECHO/MARKSMAN/ARCHMAGE, но реальный RealitySplitTrialController использует RIFT_REFLECTION/JUGGERNAUT/RIFT_HUNTER. | Настоящие четыре trial mobs/players, room combat и snapshot migration |
| W706 | CODE-RISK / DESIGN | Wave7AdmissionPolicy.defaultLayout(2) определяет лишь WARDEN/ECHO. Двое игроков не должны зависнуть без других участников, когда задумано четыре испытания. | Реальный полный сценарий с 2 и 5–6 участниками, последовательная допустимая маршрутизация |
| W609 | CODE-RISK | PrisonerTargetSelector ограничивает выбор MAX_QUERY_ENTITIES=256, рендер подсветки зависит от outline Mixins,eligibility и сферы. | Реальный клиент, циановый союзник при наведении, 256+ entities, F1/F5/GUI |
| W610 | Wave 6 | ClearLag 1.14.4 `mob-spawn-limiter` отменял спавны ивента (`hostile-limit-per-4-chunks: 30`, reason=CUSTOM не входил в `exempt-spawn-reasons`) → `END_RIFT_SPAWN_CELL_REFUSED` и недобор pressure pack | live A/B + `debug-mode: true`: `Mob spawn limit reached (hostile)` в ту же секунду, что и отказ; фикс: общий `spawnLiveRingEntity` + шаг лаунчера `Protect-MigrationEventSpawnsFromClearLag` (идемпотентно добавляет `- CUSTOM`), контракт `test_clearlag_exemption_is_idempotent_and_scoped_to_existing_configs` | SOURCE-FIXED/TESTED + live (gen 34/38/43/44/45) |
| W611 | Wave 6 | Захват пленника (`attemptRitualPrisonerCapture`) фильтрует участников по `isRitualPrisonerBridgeReady` → без V4-подписанного моста sandbox-capture прерывается (`prepareSandboxRitualCapture`=false) | live gen 38; у ботов нет V4-подписи | CLIENT-BLOCKED (нужен `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE`) |
| W707 | Wave 7 | `isNamedWave7TrialActor` классифицировал evoker fangs (`RIFT_ARCHMAGE_PROJECTILE`) как защищённого trial-актора → ложный `WAVE7_NAMED_TRIAL_ACTOR_REMOVAL_RECOVERY_REQUIRED cause=DESPAWN` через ~20 тиков | live gen 39/40 (диагностика: type=EVOKER_FANGS, kind=RIFT_SPLIT_OBJECTIVE); фикс — исключение `isNamedTrialProjectile`; полный проход волны gen 43 | SOURCE-FIXED/TESTED + live |
| W708 | Wave 7 | Layout с PERSONAL_ECHO требует V4-мост (`isNamedEchoClientReady`) → предстартовый fail-closed `WAVE7_NAMED_ECHO_PREFLIGHT_FAILED` для неподписанных клиентов | live gen 41/42; layout случайный (`Wave7RoomLayoutPolicy.select`) | CLIENT-BLOCKED (нужен V4-подписанный клиент) |

Исторические жалобы и подтверждённые кодовые ошибки различать. Эти строки внесены как результаты read-only ревью, а не как исправленные тестами или проверенные в Minecraft. Дополнительный подробный контракт — KAGUNE_90S_ACCEPTANCE.md.