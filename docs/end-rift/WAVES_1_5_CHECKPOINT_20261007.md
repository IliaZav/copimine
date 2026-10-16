# Волны 1–5: исправления и начало переноса на Minecraft 26.3

Ветка: [codex/end-rift-event](https://github.com/IliaZav/copimine/tree/codex/end-rift-event).
PR: [#3](https://github.com/IliaZav/copimine/pull/3).
Исходный HEAD этого набора: `e287b2935b9f3af1e54cd61315b1c87493871557`.

Последнее прямое требование пользователя имеет приоритет над прежней
последовательностью V3: сейчас дорабатываются волны 1–5. В четвёртой волне
не показывается будущая траектория снаряда. Параллельно начинается отдельная
кандидатная сборка 26.3. Незавершённый следующий этап W7 сохранён отдельно;
перепроектирование Guardian/Kagune не входит в этот набор.

## Исправления и границы доказательств

| Волна | Причина / изменение | Исполненная регрессия | Minecraft |
|---|---|---|---|
| 1 | Носитель имел обычное преследование. Добавлен ограниченный отход к ближайшему сопровождению, одна цель движения на 1.5 секунды и окно обычного боя перед следующим отходом. Используются отслеживаемые мобы, существующая безопасная геометрия и ограниченный pathfinder; телепорт не добавлен. | `test_wave1_carrier_retreat.py`: сопровождение, кэш направления, предел перемещения, пауза, обычные мобы, смена носителя/поколения. | Читаемость и баланс в клиенте ещё не приняты. |
| 1 | Выпавший заряд наследовал маленький ground-transform бумаги. Добавлен собственный transform. Убраны второй дублирующий луч к Core и лишние частицы, существующий текстурный луч стал тоньше. При выборе и падении заряда есть отдельные пространственные звуки. | Asset regression сначала воспроизвела отсутствие transform; существующие проверки маркера/ownership и manifest проходят. | Все ракурсы носителя, заряда и полного инвентаря требуют новой съёмки. |
| 2 | Истечение таймера проверялось раньше допустимости отмеченного участника: смерть/выход/уход с арены на границе срока могли засчитать охоту. Проверка участия выполняется первой. Успешная охота имеет отдельный звук. | Production adapter в `test_end_rift_waves2_4_mechanics.py`: три ошибочных deadline-сценария воспроизведены, замена не получает ложное завершение и получает полное окно. | Естественный бой с 2 и 5–6 игроками ещё не принят. |
| 3 | Прогресс повторно терял весь промежуток после grace на каждом heartbeat. Дробная потеря отдельно округлялась на каждом коротком шаге. Теперь вычитается разница накопленной потери; шаг опроса не меняет скорость. | `test_wave3_capture_clock.py`: обычный/дробный шаг, повторное время, возврат и обратные часы. Оба дефекта наблюдались до исправления. | Реальный capture с переходами через границу требует проверки. |
| 3 | Следующий портал становился активным до окончания закрытия предыдущего. Общий выбор активного портала теперь ждёт окончания collapse; его используют capture, защитники и визуалы. Есть однократные звуки открытия/закрытия. | `test_wave3_active_sequence.py`: окно закрытия и отсутствующий closing receipt. | Новый authored model уже присутствовал в baseline; этот набор не выдаёт его за заново созданный. Новые кадры открытия/обеих сторон/закрытия отсутствуют. |
| 4 | По прямому пожеланию пользователя будущая траектория не отображается. Предупреждение остаётся у короны; точка locked aim не отправляется в этот cue. | `test_wave4_warning_geometry.py` проверяет локальную форму; существующие тесты реального warning/release, fair reservation и ровно одного отражённого попадания сохранены. | Скорость/читаемость реального снаряда и отражение требуют приёмки. |
| 4 | Collapse задавал произвольную высоту 3 блока и мог увеличивать critical model. Теперь сохраняется начальный transform конкретного обелиска и относительно него выполняется схлопывание. Cache очищается вместе с обелиском/волной. Принятое отражение получает отдельный звук только после consume. | `test_wave4_obelisk_collapse.py`: скачок воспроизведён до исправления; проверяются исходный размер, повторный tick и завершение. | Full/damaged/critical/collapse со всех требуемых ракурсов ещё не сняты. |
| 5 | Снятие fog-freeze включало AI/awareness даже для независимо удерживаемого моба; повторное снятие также переписывало состояние. Теперь заморозка имеет собственную generation lease, отменяет принадлежащую ей специальную атаку и возвращает только захваченные флаги. Общий leash уважает оставшееся удержание. | `test_wave5_ai_freeze_ownership.py`, `test_wave_navigation_adapter.py`: предыдущее удержание, повторная заморозка/разморозка, stale generation, owned cast/dash, duplicate sounds и leash. | Намеренная неподвижность, переходы и бой после тумана ещё не приняты в клиенте. |
| 5 | Переходы тумана получили отдельные ограниченные слои vanilla sounds. Вход в safe zone имеет звук с cooldown; unsafe damage имеет короткий сигнал. Музыка события плавно приглушается до 30% и возвращается; пользовательские настройки не записываются. | Клиентские `WaveMusicMixPolicyTest`; pinned 1.21.1 SoundSystem binding проверен сборкой и разбором реального bytecode. | Применение mixin в запущенном клиенте и слуховая приёмка не выполнены. |

Расписание трёх циклов fog, reachable safe-zone gate, отсутствие принудительного
переноса игроков, три charge/Core доставки, три охоты, три portal capture,
200-tick rune holds и сохранение фактического текущего инвентаря остаются
действующими. Тесты этих путей сохранены. Новые действия используют общий
официальный/test gameplay adapter, отдельного боевого движка нет.

## Звуки и ресурсы

Новые внешние SFX не импортированы. Используются vanilla
`BLOCK_AMETHYST_BLOCK_RESONATE`, `BLOCK_AMETHYST_BLOCK_BREAK`,
`BLOCK_AMETHYST_BLOCK_CHIME`, `BLOCK_BEACON_ACTIVATE`,
`BLOCK_RESPAWN_ANCHOR_DEPLETE`, `BLOCK_NOTE_BLOCK_CHIME`,
`ENTITY_ITEM_PICKUP`, `ITEM_SHIELD_BLOCK`, `BLOCK_GLASS_PLACE`,
`BLOCK_CONDUIT_AMBIENT_SHORT`, `BLOCK_SCULK_SENSOR_CLICKING` с ограниченными
громкостями/высотой и пространственным audience filter. Уже включённые
музыкальные дорожки wave_1…wave_5 и их источники/лицензии сохранены.
Качество звука не доказано компиляцией.

## Minecraft 26.3: выполненный первый этап

Манифест Mojang на 2026-10-07 указывает 26.3 как latest release, а
26.4-snapshot-3 как snapshot. Это проверено по официальному API.
Paper 26.3 build 159 опубликован в канале **BETA**, Purpur 2644 отмечен
experimental. Это кандидат для испытаний, а не подтверждение готовности
всего сервера к обновлению.

- `tools/minecraft-26.3/profile.lock.json`: точные версии, download URLs и
  checksums Mojang, Paper API/server и Gradle. Java minimum 25.
- `scripts/minecraft/BuildMigrationPlugins.ps1`: отдельная сборка всех
  восьми собственных plugins против проверенного SHA-256 Paper API
  `26.3.build.159-beta`. Результаты идут в `build/minecraft-26.3/plugins/jars`.
  Существующие server plugins и миры не заменяются этим инструментом.
- Исправлены удалённые `GENERIC_*` constants в Artifacts и EndEvent:
  typed aliases сохраняют прежние значения и поддерживают enum 1.21.1 и
  registry-backed API 26.3. Post-W7 recovery использует ту же совместимость;
  порядок/receipt/ремонт и боевые параметры не изменены.
- `resourcepacks/build-migration-pack.py`: отдельный кандидат ресурспака
  формата **97.1**, прочитанного из pinned Mojang client. Созданы 31 item
  definition / 71 model binding. Неизвестный CMD возвращается к настоящему
  vanilla fallback; луки, арбалет, часы и compass/lodestone сохраняют native
  state tree целевой версии. Авторские модели/текстуры не заменяются ради
  хешей. Старый pack/source остаётся отдельным.
- Клиентский порт ещё не выполнен. Для 26.3 зафиксированы Fabric Loader
  0.19.5, Loom 1.17.21 и Fabric API 0.162.0+26.3. Изменение только номера
  версии существующего 1.21.1 JAR не используется как миграция.
- Сторонние AuthMe, ProtocolLib, Grim, WorldEdit/WorldGuard, Emotecraft,
  Voice Chat и остальные plugins/mods ещё не прошли runtime compatibility
  matrix. Восьми собственных компиляций недостаточно для вывода о них.

Первичные источники: [Minecraft 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3),
[Paper 26.3](https://papermc.io/news/26-3/),
[Java requirement](https://docs.papermc.io/paper/getting-started/),
[Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html).

## Проверки и оставшаяся приёмка

Повторный полный pytest после исправления замечания CodeRabbit дал
**1161 PASS / 1 SKIP / 88 warnings** за 247.64 секунды, exit 0.
Сборка восьми собственных plugins 26.3 прошла; точный Paper API SHA-256
проверен самой задачей. Migration pack собран, 12 focused compatibility
tests прошли. Сборки текущих 1.21.1 plugins, Fabric client, resource pack и
modpack выполнены. Окончательный registered gate с повторной сборкой всех
восьми текущих plugins, клиента, pack и modpack завершился exit 0:
`End Rift current local checks passed.` `git diff --check` также прошёл.

CodeRabbit проверил публичный снимок изменений и сообщил одно замечание:
элита после тумана могла выбирать цель и применять запланированный cast,
несмотря на сохранённый независимый NoAI hold. Исполняемый regression
сначала упал на реальном `tickMiniBosses`; проверки hold добавлены в выбор,
windup, release, flight/impact и dash. 43 focused tests прошли; полный gate
повторно прошёл. Проверки captive targeting не ослаблены.

Codex Security завершил отдельный immutable diff scan
`d3159cfd-10dd-41f4-934f-b1104ce45ae7` без подтверждённых уязвимостей.
Его scope — исходный публичный снимок: 19 source inventory items,
дополнительные Gradle файлы и verification/docs. Последующие cast-hold/PS5
исправления и подготовка нового runtime/profile в этот снимок не входят.
Это source review, не доказательство Minecraft acceptance.

Профиль пользователя `Copimine` проверен по SHA-1 vanilla client 26.3,
Fabric Loader 0.19.5 и Java major 25. В него установлены и сверены по SHA-512
шесть подходящих external mods: Fabric API, Emotecraft, PlayerAnimationLib,
Simple Voice Chat, Sodium и Iris; адаптированный pack 97.1 установлен и
сверен по SHA-256. Три проверки installer отказа на неверном клиенте/loader
прошли. Полный CopiMineClient port пока не готов: старый JAR 1.21.1 туда не
подставлен. Source manifests/installer находятся в `tools/minecraft-26.3`
и `scripts/minecraft/InstallMigrationProfile.ps1`.

Частные RED/GREEN/build/test receipts находятся в
`artifacts/end-rift-waves/20261007/waves1-5/`. Это локальные доказательства;
их наличие не означает NATIVE_VERIFIED. Публичные сведения о commit/review/CI
добавляются в основной ledger после публикации.

Для visual acceptance нужны новые F2/видео каждой W1–5 на matching
server/client/pack: carrier/token/Core; три hunt transitions; portal
front/back/side и capture/close; obelisk integrity/reflect/collapse без
trajectory line; fog warning/safe-zone/danger/freeze/resume. Отдельно нужны
слуховая проверка, 2-player и 5–6-player normal-health runs, particles-minimal,
shader on/off, hurt feedback, смерть/quit/restart/10 повторов, отсутствие
дубликатов предметов/stale packets/tasks и измеренная производительность.
Ни этот документ, ни зелёная сборка не объявляют этот перечень выполненным.
