# End Rift Event — финальная локальная проверка

Дата: 24 августа 2026 года

Ветка: `codex/end-rift-event`

Worktree: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

## Граница безопасности

- Проверка выполнялась только в `local-runtime\end-rift-server`.
- Paper: `127.0.0.1:25566`, RCON: `127.0.0.1:25576`.
- PostgreSQL: изолированный локальный экземпляр `127.0.0.1:55433`.
- Launcher и сайт в рамках этой проверки не запускались и не изменялись; уже открытое окно лаунчера не использовалось. Production world, production DB и production deploy не затрагивались.
- После destructive-проверки Core локальный status оставлен чистым: активных event-owned сущностей нет.

## Source/build gate

`tests\RunEndRiftEventChecks.ps1` завершился успешно:

- Python contracts: `120 passed, 14 warnings`.
- WorldCore, Artifacts, End Event и Fabric client собраны.
- Pure domain tests: все 5 `OK`.
- Durable persistence/layout tests: все 3 `OK`.
- Resource pack собран, SHA-1: `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.

Артефакты этого прогона:

| Артефакт | SHA-256 |
| --- | --- |
| WorldCore | `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99` |
| Artifacts | `A2FEB49C31FA9FBA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE` |
| End Event | `C6D4054E0BDDAC06D44126DBE0503BD9B27A28D7039EF82C9138D830DDA63914` |
| Fabric client build (`CopiMineClient/build/libs/CopiMineClient-0.1.0.jar`) | `0CD49DBE1517EB2B7BC04B40990C707AF9AEF96F4E67E3432F62F4AB955AD9D6` |
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

Финальный RCON status после текущего official-victory прогона и очистки Core:

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
- Финальный protocol bot получил resource pack hash `aaa2605b2b0f2751f5eab5258173d5c6699d45be`; tracked `minecraft/server/server.properties` после gate восстановлен на исходный production hash `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.
- `END_EVENT_MUSIC_TEST`: `track=copimine:end_rift/victory loopSeconds=0` на локальном игроке; победный файл — ровно 20 секунд.

В логах финального запуска нет `NoClassDefFoundError` и `CopiMineEndEvent failed closed`.

## Реальная GUI-проверка

- Fabric-клиент подключён к изолированному Paper на `127.0.0.1:25566`; локальный HTTP-сервис ресурспака временно поднят только на `127.0.0.1:8092`. Ответ ZIP: HTTP `200`, `8,411,791` байт, SHA-1 `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.
- В Minecraft после `/cmend core set 2` реально видны симметричный сине-фиолетовый Core на выбранном каменном блоке и две большие руны, лежащие на верхней поверхности блоков; ванильный блок под overlay не заменён.
- После заполнения ресурсов status стал `READY_FOR_PLAYERS`, `coreModel=830002`, `runes=2/2`. При телепортации тестового игрока на руну status показал `occupied=1`; в GUI занятая руна реально переключилась на ярко-зелёную текстуру и осталась на поверхности блока.
- Локальный `/cmend test ai` показал в GUI event-мобов и босса; отдельный свежий кадр содержит видимые фиолетовые частицы летящего заклинания. После проверки выполнен `/cmend core remove confirm`; status снова чистый, `event-mobs=0`, `boss=none`, `coreOverlay=false`, `runes=0/0`.
- Эти наблюдения сделаны в реальном окне Minecraft, а не на концепт-рендерах. Окно лаунчера не использовалось.
