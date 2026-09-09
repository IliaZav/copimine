# End Rift Event V2 — release evidence

Дата проверки: 2026-09-09.

## Область

Работа выполнена только в локальной ветке `codex/end-rift-event` репозитория
`IliaZav/copimine`. Production-сервер, production-база, launcher и исходники
сайта не использовались и не изменялись. Локальный Paper использует отдельные
PostgreSQL, web и resource-pack процессы.

Текущий локальный runtime:

- Paper: `26.29.99.140:25566`;
- RCON: `127.0.0.1:25576`;
- resource pack: `http://26.29.99.140:8092/CopiMineResourcePack.zip` (Radmin
  peer endpoint; the local HTTP process serves the same file);
- local website: `http://127.0.0.1:8093/`;
- PostgreSQL: `127.0.0.1:55433`, database `copimine`.

Карта не стиралась. Сохранённая пользовательская разметка runtime осталась:
Core `CopiMine 8,68,-39`, арена `CopiMine [-12,65,-59]..[28,71,-19]`,
portal room `CopiMine 31.5,68.0,-42.5`.

## Что вошло в V2

- Новый lifecycle `WAVE_1`–`WAVE_6`, межволновые перерывы, `PRE_BOSS`,
  `BOSS_CINEMATIC`, `BOSS_ACTIVE`, victory/recovery и idempotent rewards.
- Настоящее entity HP босса: стартовая проекция 5000 HP, scaling до 20000 для
  20 игроков. В официальном V2 flow legacy virtual-health authority не
  используется: V2 читает и меняет только HP живой сущности, а старый путь
  оставлен лишь для disposable local test-boss harness.
- Исправлена потеря ударов по мобам и боссу через Paper hurt-resistance:
  валидный рассчитанный final damage для owned wave mob применяется ровно один
  раз к реальному HP, затем исходное событие отменяется. Обычные правила
  отмены, shield, target и timing не отключаются.
- Добавлен Combat Trace с tick, UUID, причиной, raw/final damage, cancel state,
  no-damage state, lastDamage, HP до/после, фазой и MSPT.
- Добавлены bounded AI, target pressure, scaling по игрокам, командир сложной
  волны, скелетные стрелы, изолированные Wave 6 chambers и очистка по generation.
- Исправлены 3D Wave 3 portals, зоны, кольца, арена, boss stages и final
  strike VFX. Combat UI не подменяется техническим ActionBar-текстом.
- Между сервером и клиентом добавлен bounded world-space beam transport для
  Wave 4 safe-zone beacon, Wave 5 связей, связей обелисков, boss-cue и
  финальной сцены. Клиент рисует непрерывное ribbon-ядро с halo, движущимся
  потоком и плавным fade in/out; старые particle-lines удалены из этих путей.
- Добавлен `RIFT_OBELISKS` только для `RIFT`: scaling 1/2/3/4 по живым игрокам,
  3 HP, reflected event-owned Rift Fireball, hard cap 8, pulse radius 5,
  stagger, bounded particles и полная очистка.
- Rift Fireball не наносит урон боссу ни прямым hit, ни explosion; обычный
  player damage после этого продолжает работать.
- Добавлены server-authoritative tentacle runtime, `grab_socket`, timing
  markers и клиентские model/animation assets.
- Награды идут через Artifacts API: `rift_core_shard` гарантированно,
  `night_cloak` — независимый 30% roll на игрока с сохранением результата.
  Shard owner-binding, passive policy и Abyss Anchor покрыты тестами.

## Найденная причина пропадающего урона

В исходном Paper пути второй быстрый hit попадал в native hurt-resistance
окно entity. Bukkit/Paper учитывал `lastDamage` и `noDamageTicks`, поэтому в
event handler приходил новый final damage, но в здоровье entity применялась
только разница относительно предыдущего удара. Это выглядело как пауза в
уроне и затрагивало разные цели и босса.

Исправление локализовано в `EventRealHealthDamagePolicy`: для текущего
owned wave mob или V2 boss сервер принимает валидный hit, применяет его к
authoritative real HP ровно один раз и не меняет глобально vanilla combat.
Повторный hit, отменённый hit, EXHAUSTED multiplier и bounded cast проходят
отдельные ветки и регрессии.

## Автоматические проверки

Команда полного gate:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1
```

Результат последнего запуска:

- Fabric client Gradle build: `BUILD SUCCESSFUL`;
- resource pack собран;
- Python contracts: `437 passed, 17 warnings`;
- pure Java domain/policy tests: все перечисленные в gate тесты `OK`;
- durable persistence/layout tests: все `OK`;
- итог: `End Rift local checks passed.`

Отдельный Combat Trace live-пробник сначала воспроизвёл отсутствие записей
из-за выключенной opt-in диагностики в самом тестовом сценарии. После
исправления сценарий включает `/cmend debug trace on` перед уроном и всегда
выключает его в cleanup. Повторный реальный прогон дал:

```text
LIVE_COMBAT_TRACE_PASS wave_traces=177 player_wave=30 exact_wave=30 boss_traces=2 wave_bot=RiftTraceFinal boss_bot=RiftTraceB
```

Основные новые test classes: `BossRealHealthDamagePolicyTest`,
`EventRealHealthDamagePolicyTest`, `CombatTraceRecordTest`,
`V2BossHealthScalingPolicyTest`, `ChamberIsolationPolicyTest`,
`RiftObeliskScalingPolicyTest`, `RiftObeliskDamagePolicyTest`,
`RiftObeliskPlacementPolicyTest`, `RiftObeliskTimingPolicyTest`,
`TentacleAnimationPolicyTest`, `TentacleControllerTest`,
`ShardPassivePolicyTest` и тесты persistence/recovery.

## Live Paper evidence

Запуск локальной среды выполняется штатным скриптом:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\StartEndRiftLocalUserSession.ps1 -AdminNickname SudoKillDash9
```

Он сохраняет карту, whitelist, ops, AuthMe и локальные данные, пересобирает
актуальный pack, синхронизирует plugin jars и поднимает изолированные сервисы.

Проверенные на актуальном V2 build сценарии:

- 2 игрока: полный проход Wave 1–6, boss stages и victory — `PASS`, event
  `f0d3cda8-ea4d-49d7-8bd0-8ac6c062b1c0`;
- 3 игрока: полный проход и victory — `PASS`, event
  `eb5f408e-756b-4f20-9a46-ecd1043808ba`;
- 5 игроков: полный проход и victory — `PASS`, event
  `d47ba29c-ec7b-4f98-8938-7bea3f76e461`;
- 10 игроков: полный проход и victory — `PASS`, event
  `07d0963b-5e0b-498f-b152-7543756abf9f`; boss max HP `13500` по V2 scaling;
- 20 игроков: 4 obelisks, bounded fireballs и scaling — `PASS`;
- 5-player performance: 30 s, средний TPS `19.59`, max MSPT `6.56`, max ping
  `4 ms`;
- 20-player stress: TPS `18.86–19.73`, max MSPT `9.62`, CPU `17.17%`, ping
  `0–12 ms`;
- obelisk live: spawn/pulse/fireball/3 reflected hits/destruction/cleanup —
  `PASS`;
- 20-player obelisk load: `4/4` obelisks, fireball cap `0/8`, staggered start,
  pulse `40` ticks, radius `5`, damage `6.0`, cleanup — `PASS`;
- mob combat live: movement, real attacks, player damage и cleanup — `PASS`;
- boss real-health live: physical entity `5000/5000`, no legacy virtual marker —
  `PASS`;
- boss multi-player live: два независимых атакующих, authoritative HP delta
  равна сумме final damage, cleanup — `PASS`;
- boss multi-player live, 5 клиентов: `112` фактических атак от `5`
  независимых игроков, HP `5000 -> 4536.704`, сумма final damage
  `463.30362892150926`, две группы ударов в одном тике, cleanup `boss=none` —
  `PASS`;
- shard active live: physical ECHO_SHARD, owner-bound PostgreSQL delivery,
  normal player block interaction и real server-side teleport — `PASS`.

Последний прогон после world-space VFX обновления и пересборки JAR:

- полный gate: `437 passed, 17 warnings`, все Java domain/persistence/layout
  тесты `OK`, итог `End Rift local checks passed.`;
- Paper перезапущен через `StartEndRiftLocalUserSession.ps1`, карта и локальная
  persistence сохранены, стартовый статус `READY_FOR_PLAYERS`, 30 plugins;
- obelisk live: reflected hit `3 -> 2 -> 1 -> 0`, direct melee не снял HP,
  explosion не изменил stone-арену, boss real HP `2500 -> 2500`, cleanup
  `obelisks=0`, `fireballs=0` — `PASS`;
- пять независимых атакующих: real boss HP `5000 -> 4614.216`, сумма final
  damage `385.79070425033572`, ожидаемое HP `4614.20929574966428`,
  расхождение `0.00670425033572`, `11` same-tick групп, cleanup `boss=none` —
  `PASS`;
- Combat Trace live: `wave_traces=216`, `player_wave=63`, `exact_wave=62`,
  `boss_traces=2` — `PASS`;
- mob combat live: `moved=1275`, `attacks=46`, `player_hurt=82`,
  `player_damage_applied=109`, `ai_targets=77`, `ai_paths=51` — `PASS`;
- 20-player obelisk load: `4/4`, boss real HP `20000`, fireballs `0/8`,
  staggered, pulse `5/40`, effects `40/60`, cleanup — `PASS`;
- полный официальный десятигровой проход на текущем JAR: event
  `07d0963b-5e0b-498f-b152-7543756abf9f`, 10 независимых клиентов, Wave 1–6,
  `AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL`,
  `RIFT_OBELISKS_SPAWNED`, boss defeat и victory — `PASS`;
- в том же прогоне после victory статус показал `event-mobs=0`, `boss=none`,
  `rift-obelisks=0`, `rift-fireballs=0`, `victory=REWARDS_PENDING`; Paper log
  подтвердил `BOSS_DEFEAT_COMMITTED`, `BOSS_DEFEATED`, cleanup и memorial с
  никами всех 10 участников;
- исправлен официальный live-driver: `Wait-LogRegex` теперь читает лог сразу
  после каждого медленного `DuringWait` RCON-действия и делает финальное чтение
  перед timeout. Регрессионный контракт — `1 passed`; это закрывает ложный
  timeout, который возникал, когда маркер записывался во время обслуживания
  десяти клиентов, а старый драйвер проверял лог только до этой операции;
- visual live probe на текущем JAR: boss cue, Wave 1 wave-front, Wave 3
  layered portal (`12` tracked visuals), Wave 4 zone state, обе финальные
  сцены и cleanup, diagnostics — `PASS`; отдельный 30-секундный sampler дал
  `TPS avg=19.67`, `max MSPT=2.86`, `max ping=7 ms`;
- после победы финальный статус восстановлен как `READY_FOR_PLAYERS` с
  `endUnlocked=true`, Core `CopiMine 8,68,-39`, руны `2/2`, временные сущности
  отсутствуют. End Rift error scan последнего Paper log — `clean`.
- После visual probe локальный ресурсный прогресс был восстановлен штатными
  командами (`128/128`, `100/100`, `64/64`, `64/64`) и Paper перезапущен ещё
  раз. Restart сохранил карту, Core/арену/руны и перевёл runtime обратно в
  `READY_FOR_PLAYERS`; production не затрагивался.

При последнем локальном старте Paper также записал два внешних предупреждения,
не относящихся к End Rift: voicechat не распознал строку версии Paper 1.21.1 и
AuthMe не нашёл необязательную GeoLite2-Country.mmdb. Оба плагина продолжили
загрузку; End Rift, resource pack, RCON, PostgreSQL и web runtime стартовали
штатно. Эти сторонние предупреждения не маскируются как часть End Rift.

Команды отдельных live-проверок:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftBossMultiPlayerDamageLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftBossRealHealthLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftCombatTraceLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftObeliskLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftObeliskLoadLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftOfficialTwoPlayerLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftOfficialFivePlayerLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftOfficialTenPlayerLive.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftShardPassivesLive.ps1 -BotName ShardProbe
```

`RunEndRiftShardPassivesLive.ps1` в сохранённой пользовательской карте
проверяет active channel и явно печатает `NOT VERIFIED` для End-only passives,
если persisted portal room находится в `CopiMine`, а не в authoritative End
world. Policy/contract coverage для Strength II, Speed II, Enderman reduction,
Pearl self-damage и Abyss Anchor проходит. Это ограничение проверки не меняет
карту автоматически.

## Resource pack и визуальная проверка

Созданы/обновлены high-resolution assets для boss stages, mobs, fireball,
obelisk FULL/DAMAGED/CRITICAL/PULSE, portals, tentacle, bossbar, shard и
night cloak. Vanilla fireball и vanilla block textures глобально не заменяются;
event visuals выбираются по event id/PDC/client bridge с fallback.

Resource-pack/client asset checks подтверждают наличие файлов, JSON-моделей,
manifest, zip-содержимого, fallback и отсутствие глобального vanilla override.
Реальный screenshot из открытого Minecraft-клиента для финального V2 набора —
`NOT VERIFIED`: доступный desktop target не предоставил надёжный native
Minecraft capture в этой сессии. Поэтому source/asset/runtime evidence не
выдаётся за GUI screenshot proof.

Manual visual verification in a native Minecraft window: `NOT VERIFIED` in this
session. Автоматические проверки ассетов, клиентский build и Paper runtime
проверены; скриншот не используется как доказательство внешнего вида.

Команда live visual probe:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftBossVisualLive.ps1 -ViewerName EndRiftVisFinal -VisualBotDurationSeconds 60 -PerformanceDurationSeconds 30 -VisualTimeoutSeconds 120
```

Она подтвердила `END_RIFT_BOSS_VISUAL_LIVE_PASS`, а после неё runtime оставил
`eventMobs=0`, `boss=none`, `transientDisplays=0`, `projectiles=0`. Это
поведенческий Paper/client-bridge probe, а не screenshot. Нативная ручная
проверка изображения по-прежнему `NOT VERIFIED`, потому что CUA-сессия не
предоставила native Minecraft window.

## Cleanup, persistence и безопасность

Проверены reset, boss death/victory, Core removal, generation change, plugin
disable/recovery и stale entity paths. Runtime registry очищает mobs, boss,
displays, obelisks, fireballs, tentacles, runes, tasks и temporary mutations.
Награды и rolls сохраняются до выдачи, повторный retry не reroll-ит предметы.
После live-прогонов временные probe players удалены из whitelist; оставлены
только постоянные локальные test accounts, уже входившие в пользовательскую
настройку whitelist/ops.

## Финальные локальные артефакты

SHA-256 после последнего gate:

- `copimine-world-core/CopiMineWorldCore.jar` —
  `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99`;
- `copimine-artifacts/CopiMineArtifacts.jar` —
  `CC62AB1638C7C5C3CE975E45BFC61D880F637A6EAB1D9518B9CBEF722C34EA96`;
- `copimine-end-event/CopiMineEndEvent.jar` —
  `5E5710094266E219B208588FE1760C87CAF5455F29DCD9FFCAF3AC126D323B8B`;
- `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` —
  `BDC7760968F7DACE8C2AC3D0294C8C554773CCE6DA487B8140AD3D145A5528DC`;
- `resourcepacks/build/CopiMineResourcePack.zip` —
  `7AE745F16F7A1C78105B3BCBD49849A881A9C43036F7CE298EDEBF9975AFB31A`.

Изменение live-driver и его регрессионный контракт входят в текущий commit
ветки `codex/end-rift-event`; production deployment из этой проверки не
выполнялся.
