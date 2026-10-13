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
| K01 | Kagune | Защитники снова рождаются через 40с вместо окончательной гибели | TentacleGuardianPolicy.RESPAWN_DELAY_TICKS=800, tentacleGuardianRespawnAt, ensurePermanentTentacles | CODE-MISMATCH |
| K02 | Kagune | Захват разваливает модель/сегменты падают | жалоба по F2/игре | USER-REPORTED |
| K03 | Kagune | Спам по закрытому damage window всё ещё наносил урон и ставил HIT_RECOVERY | жалоба; damage handler/state guard есть | USER-REPORTED, повторить live |
| K04 | Kagune | Щупальца слишком маленькие; 6.25 блоков из старого guide против модели 4.5–5 | новый пользовательский дизайн + config hitbox-height=6.25 | CODE-RISK неверной геометрии |
| K05 | Kagune | Слишком мало HP, требуется около 3× старого баланса | запрос пользователя; config budget, temp health=20 | USER-REPORTED, баланс через реальные прогоны |
| R01 | Recovery | Torn DepositJournal, generation=0, partial append и unsafe restart | прежний журнал ошибки и аудит; новые тесты | CODE-RISK, повторить fault injection |
| R02 | Recovery | Задача старой попытки очищает новые entities/effects после restart | EncounterResourceScope, WAVES_REPAIR_LEDGER U16 | SOURCE-FIXED/TESTED, повторить |
| R03 | Профиль клиента | Старые F2, тесты и CI могут быть с 1.21.1 или старым 26.3 client | незавершённая native 26.3 приёмка | NATIVE-UNVERIFIED |

При исполнении: каждую строку проверить в новом HEAD. Закрывать только конкретным тестом/логом/скрином на соответствующем SHA; нельзя писать «поправлено» по факту наличия класса. Не исправлять уже исправленный баг второй раз, если новый regression проходит.
