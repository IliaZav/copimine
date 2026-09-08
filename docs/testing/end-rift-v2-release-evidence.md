# End Rift Event V2 — release evidence

Дата проверки: 2026-09-08.

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
  20 игроков. Старый virtual-health код остался только в изолированном
  disposable regression harness и не используется официальным flow.
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
- Python contracts: `397 passed, 17 warnings`;
- pure Java domain/policy tests: все перечисленные в gate тесты `OK`;
- durable persistence/layout tests: все `OK`;
- итог: `End Rift local checks passed.`

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

Проверенные ранее на актуальном V2 build сценарии:

- 2 игрока: полный проход Wave 1–6, boss stages и victory — `PASS`;
- 3 игрока: полный проход и victory — `PASS`;
- 5 игроков: полный проход и victory — `PASS`;
- 10 игроков: полный проход и victory — `PASS`;
- 20 игроков: 4 obelisks, bounded fireballs и scaling — `PASS`;
- 5-player performance: 30 s, средний TPS `19.59`, max MSPT `6.56`, max ping
  `4 ms`;
- 20-player stress: TPS `18.86–19.73`, max MSPT `9.62`, CPU `17.17%`, ping
  `0–12 ms`;
- obelisk live: spawn/pulse/fireball/3 reflected hits/destruction/cleanup —
  `PASS`;
- mob combat live: movement, real attacks, player damage и cleanup — `PASS`;
- boss multi-player live: два независимых атакующих и same-tick groups —
  `PASS`;
- shard active live: physical ECHO_SHARD, owner-bound PostgreSQL delivery,
  normal player block interaction и real server-side teleport — `PASS`.

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
  `993C0A20BE1C8848BECFA5ACD171183C8B6CBBA43BE0DE437CCDA48FE664F75E`;
- `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` —
  `D2B6DFBE7E0D6B655282B8869E3F83395DD47BBE987A5A70456288D6688121EA`;
- `resourcepacks/build/CopiMineResourcePack.zip` —
  `7AE745F16F7A1C78105B3BCBD49849A881A9C43036F7CE298EDEBF9975AFB31A`.

До push этот файл и весь код должны попасть в один commit ветки
`codex/end-rift-event`; production deployment из этой проверки не выполнялся.
