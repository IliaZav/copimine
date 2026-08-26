# End Rift Event — финальная локальная проверка

Дата: 25 августа 2026 года

Ветка: `codex/end-rift-event`

Worktree: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

## Граница безопасности

- Проверка выполнялась только в `local-runtime\end-rift-server`.
- Paper: `127.0.0.1:25566`, RCON: `127.0.0.1:25576`.
- PostgreSQL: изолированный локальный экземпляр `127.0.0.1:55433`.
- Launcher и сайт в рамках этой проверки не запускались и не изменялись; уже открытое окно лаунчера не использовалось. Production world, production DB и production deploy не затрагивались.
- После destructive-проверки Core локальный status оставлен чистым: активных event-owned сущностей нет.

## Актуальный полный прогон 25 августа 2026

- Локальный Paper/Purpur `127.0.0.1:25566` и RCON `127.0.0.1:25576`; после проверки оставлены Core и руны, но `event-mobs=0`, `boss=none`.
- Актуальный status: `state=WAVE_1`, `coreOverlay=true coreModel=830002 runes=2/2 occupied=0`, все ресурсы `64/64, 64/64, 128/128, 100/100`.
- Ванильный блок ядра не заменяется: поверх него создаётся ItemDisplay, якорь — верхняя плоскость блока; руны создаются только на твёрдом блоке и занимают его верхнюю поверхность.
- Реальный Computer Use-снимок финальной волны показал кастомные текстуры пауков и шулкеров; client log подтвердил `resourcePresent=true` для `END_RIFT_ENDERMAN_V1` и `END_RIFT_ELITE_V1`.
- Для текущей particle-only ревизии Paper log подтвердил `BOSS_PROJECTILE_SPAWN visual=particle-only pattern=rift_projectile` → `BOSS_PROJECTILE_HIT`; ItemDisplay, custom model и PNG для снаряда больше не создаются и не входят в resource pack.
- Тестовый босс выбросил в мире `minecraft:ender_pearl` (1 шт.); предмет реально поднят игроком. Настоящий `Осколок Разлома` выдан локальным `CopiMineArtifacts`, в инвентаре проверены `custom_model_data=830004`, русское имя и две строки лора.
- Команды `/cmend test music waves|boss|half|final|victory` отработали в игре; лог подтвердил пять sound id и loop values `95/123/26/39/0`.
- Многопользовательский AI-прогон подтвердил ротацию целей по трём игрокам, telegraph → flight → cast для `void_blast`/`void_mark` и возврат босса к Core. Для стресс-игроков использовался vanilla cap `2048` HP плюс Resistance 255: атрибут Minecraft не принимает миллион HP.
- Принудительный выход моба за пределы возвращал его на Core-level Y; горизонтальный leash — 20 блоков. У пауков фактически проверены `health=26.0 attack=4.0`.

Актуальные хеши установленных локальных артефактов:

| Артефакт | SHA-256 |
| --- | --- |
| End Event JAR | `E9F70F46556D69AC56EC17FC5177552435EE78E2B8547201C49729F5FCCAC122` |
| Fabric client JAR | `72A5CE4DF0B8419B1A951D8A699425B3F356A79B17B249BAB0021C6DE708BEE4` |
| Resource pack ZIP | `9D65787D380791D0E873B29F6E126E81A69D17D5BAD4B66D5AB23DB5738974AB` |
| Local pack SHA-1 | `aaa2605b2b0f2751f5eab5258173d5c6699d45be` |

Пути исходного и установленного client JAR совпадают по SHA-256; production `minecraft/server/server.properties` восстановлен на исходный боевой SHA-1 `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.

## Source/build gate

`tests\RunEndRiftEventChecks.ps1` завершился успешно:

- Python contracts: `127 passed, 14 warnings`.
- WorldCore, Artifacts, End Event и Fabric client собраны.
- Pure domain tests: все 5 `OK`.
- Durable persistence/layout tests: все 3 `OK`.
- Resource pack собран, SHA-1: `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.

Артефакты этого прогона:

| Артефакт | SHA-256 |
| --- | --- |
| WorldCore | `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99` |
| Artifacts | `A2FEB49C31FA9FBA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE` |
| End Event | `E9F70F46556D69AC56EC17FC5177552435EE78E2B8547201C49729F5FCCAC122` |
| Fabric client build (`CopiMineClient/build/libs/CopiMineClient-0.1.0.jar`) | `72A5CE4DF0B8419B1A951D8A699425B3F356A79B17B249BAB0021C6DE708BEE4` |
| Resource pack ZIP | `9D65787D380791D0E873B29F6E126E81A69D17D5BAD4B66D5AB23DB5738974AB` |

Контракт музыки отдельно проверяет, что победный OGG имеет длительность `20.000 s`; focused run: `6 passed` вместе с DOCX contract.

## Локальный Paper/PostgreSQL smoke

Команда `tests\RunEndRiftRuntimeSmoke.ps1 -ServerDir .\local-runtime\end-rift-server -LogPath .\local-runtime\end-rift-paper-start.out.log` на локальном Paper с изолированной PostgreSQL завершилась:

```text
PASS typed plugins loaded
PASS WorldCore loaded
PASS Artifacts loaded
PASS Artifacts PostgreSQL command path
PASS fresh state has no configured Core
PASS fresh state has no boss
PASS test wave refuses missing Core
PASS test boss refuses missing Core
PASS cleanup requires confirmation
PASS ritual cancel requires confirmation
PASS resource reset requires confirmation
PASS unlock requires official death
PASS client bridge status is available
End Rift isolated runtime smoke passed.
```

Smoke отдельно проверяет рабочий PostgreSQL command path Artifacts и не принимает один только факт загрузки JAR за готовность БД.

Исторический RCON status после official-victory прогона и очистки Core:

```text
state=UNCONFIGURED generation=14
core=CopiMine 8,68,-39
arena=unset gate=UNSET portal=unset
pads=0/0 roster=0 participants=0 helpers=0
visuals=coreOverlay=false coreModel=0 runes=0/0 occupied=0
wave=3 event-mobs=0 boss=none half=false final=false endUnlocked=true victory=VICTORY_COMPLETE
```

## Runtime markers

Источник основного creative/gate прогона: `local-runtime\end-rift-gate-preview-final.out.log`; исторические маркеры сохранения vanilla-блоков — `local-runtime\end-rift-paper-local-7.out.log`. После исправления очистки JAR был установлен в изолированный Paper и проверен в `local-runtime\end-rift-paper-local-final-after-fix.out.log`.

- После `/cmend core set 2`: `coreOverlay=true coreModel=830001 runes=2/2`.
- После полного внесения ресурсов: `READY_FOR_PLAYERS coreOverlay=true coreModel=830002 runes=2/2`; ресурсы отображались русскими цветными названиями.

- `CREATIVE_TEST_BOSS_ACTIVE`: `health=1200.0 maxHealth=1200.0 bossbar=true`.
- `CREATIVE_TEST_HALF`: `threshold=600.0 health=600.0`.
- `CREATIVE_TEST_FINAL_DRAIN`: `threshold=120.0 finalHealth=200.0 drainFraction=0.6`.
- `MINIBOSS_SPELL_TELEGRAPH/FLIGHT/CAST`: свежий Paper лог содержит все три заклинания (`echo_pulse`, `void_snare`, `rift_step`) в последовательности telegraph → flight → cast с частицами.
- `BOSS_SPELL_FLIGHT` и `BOSS_SPELL_CAST`: свежий лог содержит последовательность для фаз босса; `BOSS_PROJECTILE_SPAWN` и `BOSS_PROJECTILE_HIT` подтверждают настоящий летящий Snowball-снаряд с частицами.
- `SPIDER_STATS`: фактические `health=26.0 attack=4.0`; Endermite в creative-run не использовались.
- `CREATIVE_TEST_FINAL_WAVE`: `{spider=8, shulker=2, enderman=6}`.
- `CREATIVE_TEST_CLEANUP` и `CREATIVE_TEST_COMPLETE`: `success=true`, `official_phase=READY_FOR_PLAYERS`, `official_roster=0`, `endUnlocked=false`.
- `END_EVENT_GATE_SELECTION_PREVIEW`: particle-only preview; команда вернула, что vanilla blocks не заменялись, а последующий staged run обработал только snapshot-owned координаты.
- После первой точки Gate лог зафиксировал `volume=1 solidBlocks=1`; после второй точки — `volume=27 solidBlocks=27`. Это подтверждает, что preview показывает каждый заполненный блок полного bounded cuboid, включая внутренние координаты, а не только границу.
- Исторический локальный gate smoke дополнительно зафиксировал `GATE_TOP_UNCHANGED`, `GATE_MID_UNCHANGED`, `GATE_BOTTOM_STONE_UNCHANGED` до открытия.
- На текущем Paper generation 14 официальный путь зафиксировал `BOSS_REWARDS_DELIVERED`, `END_EVENT_GATE_OPENING reason=official-victory`, затем слои сверху вниз (`y=71,70,69`), по `removed=9 conflicts=0` на слой, `END_EVENT_GATE_OPENED progress=27/27` и `VICTORY_COMPLETE`.
- Перед текущим official-victory живой status показывал `event-mobs=14` и `boss=<uuid>`; после `core remove confirm` — `UNCONFIGURED`, `gate=UNSET`, `helpers=0`, `event-mobs=0`, `boss=none`, `coreOverlay=false`, `runes=0/0`. Durable `event-layout.yml` оставлен с `gate.status=UNSET` и пустым snapshot.
- Отдельный текущий-JAR smoke `core set → core remove` дополнительно зафиксировал `END_EVENT_OWNED_CLEANUP generations=all removed=4` и отсутствие визуальных сущностей после hard-boundary; перезапуск с permanent End unlock не реанимирует удалённый Core.
- `END_EVENT_OWNED_CLEANUP`: `generations=all`; старые wave entities/boss/projectiles и визуальные сущности очищаются при удалении Core.
- Финальный protocol bot текущего прогона получил локальный resource pack hash `aaa2605b2b0f2751f5eab5258173d5c6699d45be`, а tracked `minecraft/server/server.properties` восстановлен на production hash `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.
- Актуальный `END_EVENT_MUSIC_TEST` на локальном игроке подтвердил: `waves=95`, `boss=123`, `boss_half=26`, `boss_final=39`, `victory=0`; победный OGG — ровно 20 секунд.

В логах финального запуска нет `NoClassDefFoundError` и `CopiMineEndEvent failed closed`.

## Актуальная particle-only ревизия 25 августа 2026

- Сначала новые тесты были запущены в RED: старый `ItemDisplay`/model-путь и отсутствие именованных паттернов были зафиксированы как ожидаемые дефекты.
- После реализации particle-only GREEN-прогон дал `127 passed, 14 warnings`; отдельный тест на `void_mark` сначала воспроизвёл `NullPointerException`, затем после исправления порядка построения вершин дал `1 passed`.
- Все восемь паттернов теперь выбираются по имени: `void_blast`, `rift_projectile`, `void_mark`, `summon_servants`, `will_distortion`, `rift_step`, `void_snare`, `echo_pulse`. Для совместимости контракт также содержит имя `summon`.
- `rift_projectile` оставляет только невидимый Snowball-контроллер столкновения (`setInvisible=true`, `setVisibleByDefault=false`) и серверные частицы; ItemDisplay, `MODEL_RIFT_PROJECTILE`, JSON-модель и PNG удалены.
- На новом JAR локальный Paper подтвердил для boss spells `BOSS_SPELL_FLIGHT`/`BOSS_SPELL_CAST` с `visual=particle-only pattern=...` для `void_blast`, `rift_projectile`, `void_mark`, `summon_servants`, `will_distortion`; `BOSS_PROJECTILE_SPAWN` и `BOSS_PROJECTILE_HIT` прошли для невидимого контроллера.
- На новом JAR локальный wave 3 подтвердил `MINIBOSS_SPELL_FLIGHT`/`MINIBOSS_SPELL_CAST` для `echo_pulse`, `rift_step`, `void_snare`; после исправления `void_mark` в текущем рестарте `latest_restart_error_lines=0`.
- После проверки выполнены только локальные `cmend wave clear` и `cmend boss kill cleanup`; финальный status: `event-mobs=0`, `boss=none`, `coreOverlay=true coreModel=830002 runes=2/2 occupied=0`.
- Новая сборка ресурспака не содержит `assets/copimine/models/item/end_event_rift_projectile.json` и `assets/copimine/textures/item/end_event_rift_projectile.png`; SHA-1: `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.
- Computer Use `node_repl` в этом окружении вернул `failed to write kernel assets: Системе не удается найти указанный путь`; particle-only ревизия поэтому подтверждена свежим Paper log, GREEN-тестами и локальным Fabric-клиентом, запущенным напрямую на `127.0.0.1:25566`.

## Актуальная проверка occupied-руны и HP хранителей — 25 августа 2026

- Красные контракты сначала воспроизвели оба дефекта: отображение occupied-руны зависело от survival-only `padOccupants`, а элитные хранители получали `80.0/80.0` HP.
- Исправление разделяет `runeVisualOccupants` и официальный `padOccupants`: Creative-оператор может увидеть визуальное переключение, но не попадает в ритуальный roster и не запускает countdown. `runeOverlayItem` теперь выбирает occupied-модель по визуальному реестру.
- У элитных хранителей в `spawnEnderman` установлены `maxHealth=40.0` и `health=40.0`; основной босс по-прежнему имеет `1200.0` HP.
- После установки JAR `E9F70F46556D69AC56EC17FC5177552435EE78E2B8547201C49729F5FCCAC122` на локальный Paper реальный клиент `EndRiftScreen` стоял на паде `CopiMine 13,69,-39`: status показал `pads=0/2`, `runes=2/2 occupied=1`, а Paper log — `RUNE_OCCUPANCY_CHANGED ... visual=1 official=0`.
- При уходе на `CopiMine 13,69,-35` тот же клиент получил `runes=2/2 occupied=0`; после возврата на пад снова `occupied=1`. Это проверяет именно переход состояния на живом сервере, а не только статический ресурс-пак.
- Реальные кадры из Minecraft сохранены в `local-runtime\\rune-captures\\creative-idle-feet-minecraft-only.png` и `local-runtime\\rune-captures\\creative-occupied-feet-minecraft-only.png`: во втором кадре руна ярко-зелёная, полностью лежит на верхней плоскости блока и не висит в воздухе.
- Для HP выполнена локальная `cmend wave spawn 3`; RCON NBT-запрос вернул для именованных элитных сущностей `Элитный эндермен · Кандалы Пустоты ... 40.0f` и `Элитный эндермен · Импульс Эха ... 40.0f`. После этого выполнены `cmend wave clear` и проверка status: `event-mobs=0`, `boss=none`.
- Для обхода AuthMe во время GUI-проверки был обратимо перемещён только локальный `AuthMe-5.6.0.jar`; перед завершением он возвращён на место. Локальный Paper остановлен, production и launcher/site не затрагивались.

## Реальная GUI-проверка

Ниже сохранены исторические кадры предыдущей текстурной ревизии ядра/рун и мобов; они не используются как доказательство новой particle-only отрисовки снарядов.

- Fabric-клиент подключён к изолированному Paper на `127.0.0.1:25566`; локальный HTTP-сервис ресурспака временно поднят только на `127.0.0.1:8092`. Ответ ZIP: HTTP `200`, `8,411,791` байт, SHA-1 `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.
- В Minecraft после `/cmend core set 2` реально видны симметричный сине-фиолетовый Core на выбранном каменном блоке и две большие руны, лежащие на верхней поверхности блоков; ванильный блок под overlay не заменён.
- После заполнения ресурсов status стал `READY_FOR_PLAYERS`, `coreModel=830002`, `runes=2/2`. При телепортации тестового игрока на руну status показал `occupied=1`; в GUI занятая руна реально переключилась на ярко-зелёную текстуру и осталась на поверхности блока.
- Локальный `/cmend test ai` показал в GUI event-мобов и босса; отдельный свежий кадр содержит видимые фиолетовые частицы летящего заклинания. После проверки выполнен `/cmend core remove confirm`; status снова чистый, `event-mobs=0`, `boss=none`, `coreOverlay=false`, `runes=0/0`.
- Эти наблюдения сделаны в реальном окне Minecraft, а не на концепт-рендерах. Окно лаунчера не использовалось.

## Watchdog свободных рун в боевой фазе — 25 августа 2026

- RED runtime reproduction на локальном Paper: при `state=WAVE_1`, `coreOverlay=true`, `runes=2/2` удаление одной принадлежащей событию `ItemDisplay` командой `execute positioned 13.5 69 -39 run kill @e[type=minecraft:item_display,distance=..0.5]` оставляло `runes=1/2`; восстановление в боевой фазе не запускалось.
- Причина была в лишнем ограничении `maintainRitualVisuals()`: watchdog работал только в `READY_FOR_PLAYERS` и `COUNTDOWN`, хотя руны должны оставаться видимыми во всех заряженных фазах до `UNLOCKED`.
- После минимального исправления и установки нового JAR повтор того же сценария вернул `runes=2/2`; Paper записал `END_EVENT_RUNE_VISUAL_REBUILD ... phase=WAVE_1 missing=1 expected=2`, затем заново создал core/rune overlays.
- Добавлены регрессионные контракты на ремонт рун в боевых фазах и обновлён устаревший контракт арены. End Event suite: `128 passed, 14 warnings`; `RunEndRiftEventChecks.ps1`: `End Rift local checks passed`.
- Свежий кадр реального Minecraft с точным видом на свободную руну сохранён в `local-runtime\\rune-captures\\idle-exact-pad-view.png`; это игровой кадр, а не концепт. Production `server.properties` после gate восстановлен на SHA1 `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.

## Fail-closed зависимостей и recovery smoke — 25 августа 2026

- RED runtime на изолированной копии Paper: без `POSTGRES_PASSWORD` `CopiMineEconomyCore` корректно остановил PostgreSQL init, вслед за ним `CopiMineArtifacts` отключился, но End Rift продолжал вызывать `tryBootstrap()` и повторял `NoClassDefFoundError: me/copimine/artifacts/api/EventArtifactRewardService`.
- Причина устранена в `tryBootstrap()`: до загрузки typed service проверяется `isEnabled()` для `CopiMineWorldCore` и `CopiMineArtifacts`; при отключённой зависимости отменяется bootstrap task и сам End Rift отключается с одним сообщением `End Rift dependencies are unavailable; disabling to prevent partial startup.`
- GREEN negative runtime: при намеренно отсутствующем пароле получено `guard-log-count=1`, `class-loading-error-count=0`, `end-event-services-ready-count=0`; спустя ожидание новых class-loading ошибок нет.
- GREEN positive runtime с локальной PostgreSQL: `RunEndRiftRecoverySmoke.ps1` прошёл `state=UNLOCKED|UNCONFIGURED`, `endUnlocked=true`, `VICTORY_COMPLETE`, `safe unconfigured transition`; `RunEndRiftRuntimeSmoke.ps1` прошёл все typed dependency/refusal/client checks.
- Recovery smoke дополнен проверкой `NoClassDefFoundError|ClassNotFoundException` и принимает штатный переход в `UNCONFIGURED`, когда WorldCore уже открыт, а ядро отсутствует; production process и конфигурация не использовались.

## Проверка области действия кастомной музыки — 25 августа 2026

- В `CopiMineEndEvent` добавлен единый `isEventMusicPhase()`: автоматические waves/boss/final/victory-треки разрешены только для боевых фаз и `VICTORY_PROCESSING`/`VICTORY`; `UNCONFIGURED`, `COLLECTING`, `READY_FOR_PLAYERS`, `COUNTDOWN`, `UNLOCKED` не получают музыкальный трек.
- Переход или принудительный выход из обычной фазы вызывает `stopEventMusic()`; loop-task и join-sync дополнительно проверяют фазу, поэтому оставшийся активный трек гасится при выходе из ивента. Победный OGG получает отдельный короткий tail до естественного завершения после `VICTORY_PROCESSING -> UNLOCKED`.
- Чистый локальный boot после установки строгого JAR `local-runtime\CopiMineEndEvent-music-scope-strict.jar` (`SHA-256 D7C65E4535E3EE09F4B9BC0B5339B20A4351AEA7653BB217F48790C90EE937A`) оставил `state=COLLECTING`; в `local-runtime\end-rift-strict-music-start.out.log` отсутствует `END_EVENT_MUSIC track=`. Это проверяет обычную игру без автоматического event music.
- Дополнительный локальный отрицательный прогон создал test boss и вызвал 50%-фазу при сохранённом `COLLECTING`; после `cmend boss kill cleanup` в том же логе также нет `END_EVENT_MUSIC track=`. `testCombatAiMode` больше не является разрешением для автоматической музыки.
- RCON того же запуска подтвердил сохранённые локальные границы и визуалы: `core=CopiMine 8,68,-39`, `arena=[-12,65,-59]..[28,71,-19]`, `gate.pos1=29,68,-40`, `gate.pos2=29,71,-38`, `portalroom=CopiMine 31.5,68.0,-42.5`, `runes=2/2`.
- Явная команда `/cmend test music ...` оставлена только для `environment: local` и логируется отдельным `END_EVENT_MUSIC_TEST`; она не является автоматическим воспроизведением обычной игры. Локальный музыкальный контракт подтвердил все пять треков и победную длительность около 20 секунд.
- End Rift test suite после изменения: `135 passed, 14 warnings`; focused music/scene run: `10 passed`. PowerShell-сценарий локальной сцены прошёл parser check и строгую проверку Core/arena/gate/portal; ванильные текстуры не переопределяются.

## Повторная runtime-проверка области действия музыки — 26 августа 2026

- Поднят отдельный локальный PostgreSQL-кластер из `local-runtime/end-rift-pgdata` на `127.0.0.1:55433`; production/обычный локальный порт `55432` не использовался. Локальный Paper работал только на `127.0.0.1:25566`, RCON — `127.0.0.1:25576`; в логе есть `CopiMineEconomyCore PostgreSQL is ready.` и `CopiMineEndEvent services ready`.
- Отрицательный прогон: чистый boot оставил `COLLECTING`, затем заполнение ресурсов перевело состояние в `READY_FOR_PLAYERS`; в `local-runtime/end-rift-music-and-scene-20260826.out.log` нет ни одной автоматической строки `END_EVENT_MUSIC` в обычных фазах.
- Положительный прогон: два изолированных offline-игрока заняли обе руны (`RUNE_OCCUPANCY_CHANGED ... visual=2 official=2`), countdown завершился через 60 секунд, официальный state перешёл в `WAVE_1`, generation `19`, создано `10` event-mobs. В тот же момент Paper записал `END_EVENT_MUSIC track=copimine:end_rift/waves phase=WAVE_1 loopSeconds=95`.
- После проверки вызвано локальное `/cmend core remove confirm`: лог подтвердил `END_EVENT_OWNED_CLEANUP ... removed=4`; затем Core/руны восстановлены на прежнем месте, Gate получил status `RESTORED`, а `SetupEndRiftLocalScene.ps1` завершился с exit code `0`. Финальный локальный status: `COLLECTING`, `coreOverlay=true`, `runes=2/2`, `event-mobs=0`, `boss=none`.
- Итог: автоматическое event music включается только в активных боевых/победных фазах. Уже начатый короткий victory-tail после победы — единственное предусмотренное завершение трека; обычная игра его не запускает. `/cmend test music ...` остаётся отдельной local-only диагностикой и не является автоматическим воспроизведением.

## Локальная проверка spawn-станции — 26 августа 2026

- `SetupEndRiftLocalScene.ps1` повторно завершился с exit code `0` только на Paper `25566`; итоговый status: `COLLECTING`, `coreOverlay=true`, `runes=2/2`, `event-mobs=0`, `boss=none`, Gate `RESTORED`.
- Для надёжной связи с командными блоками кнопки размещаются на стене в `0,64,-17` и `3,64,-17` как `minecraft:stone_button[face=wall,facing=south,powered=false]`; командные блоки находятся непосредственно за ними в `0,64,-16` и `3,64,-16`.
- RCON Paper-проверка после `powered=true` показала у блока включения `powered:1b`, после возврата кнопки в `powered=false` — `powered:0b`; NBT команд подтверждён: `function copimine:spawn/max_on` и `function copimine:spawn/max_clear`. Это не меняет ванильные текстуры и остаётся локальной стендовой конфигурацией.
