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
- Реальный Computer Use-снимок босса показал красно-золотую текстуру `END_RIFT_GUARDIAN_V1`, bossbar `1200/1200` и частицы. Снимок летящего `rift_projectile` показал поток частиц и летящий снаряд; Paper log подтвердил `BOSS_PROJECTILE_SPAWN model=830006` → `BOSS_PROJECTILE_HIT`.
- Тестовый босс выбросил в мире `minecraft:ender_pearl` (1 шт.); предмет реально поднят игроком. Настоящий `Осколок Разлома` выдан локальным `CopiMineArtifacts`, в инвентаре проверены `custom_model_data=830004`, русское имя и две строки лора.
- Команды `/cmend test music waves|boss|half|final|victory` отработали в игре; лог подтвердил пять sound id и loop values `95/123/26/39/0`.
- Многопользовательский AI-прогон подтвердил ротацию целей по трём игрокам, telegraph → flight → cast для `void_blast`/`void_mark` и возврат босса к Core. Для стресс-игроков использовался vanilla cap `2048` HP плюс Resistance 255: атрибут Minecraft не принимает миллион HP.
- Принудительный выход моба за пределы возвращал его на Core-level Y; горизонтальный leash — 20 блоков. У пауков фактически проверены `health=26.0 attack=4.0`.

Актуальные хеши установленных локальных артефактов:

| Артефакт | SHA-256 |
| --- | --- |
| End Event JAR | `6BB6383E72D9CA694BCF38623413DCC0325B367B4AF8543FAB0CC375F18C6AE1` |
| Fabric client JAR | `72A5CE4DF0B8419B1A951D8A699425B3F356A79B17B249BAB0021C6DE708BEE4` |
| Resource pack ZIP | `AF86BDB441C41A166E3A11692B86239701570AD59040F0A84DAF64810D2A0F7F` |
| Local pack SHA-1 | `5cd3beec73f9a7eb55f7d1831f56912f73ff7e9d` |

Пути исходного и установленного client JAR совпадают по SHA-256; production `minecraft/server/server.properties` восстановлен на исходный боевой SHA-1 `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.

## Source/build gate

`tests\RunEndRiftEventChecks.ps1` завершился успешно:

- Python contracts: `123 passed, 14 warnings`.
- WorldCore, Artifacts, End Event и Fabric client собраны.
- Pure domain tests: все 5 `OK`.
- Durable persistence/layout tests: все 3 `OK`.
- Resource pack собран, SHA-1: `5cd3beec73f9a7eb55f7d1831f56912f73ff7e9d`.

Артефакты этого прогона:

| Артефакт | SHA-256 |
| --- | --- |
| WorldCore | `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99` |
| Artifacts | `A2FEB49C31FA9FBA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE` |
| End Event | `6BB6383E72D9CA694BCF38623413DCC0325B367B4AF8543FAB0CC375F18C6AE1` |
| Fabric client build (`CopiMineClient/build/libs/CopiMineClient-0.1.0.jar`) | `72A5CE4DF0B8419B1A951D8A699425B3F356A79B17B249BAB0021C6DE708BEE4` |
| Resource pack ZIP | `AF86BDB441C41A166E3A11692B86239701570AD59040F0A84DAF64810D2A0F7F` |

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
- Финальный protocol bot исторического прогона получил старый локальный resource pack hash; актуальный локальный hash — `5cd3beec73f9a7eb55f7d1831f56912f73ff7e9d`, а tracked `minecraft/server/server.properties` восстановлен на production hash `e4fb6d8b39d4f3175f39da18c5457b180b4ff13f`.
- Актуальный `END_EVENT_MUSIC_TEST` на локальном игроке подтвердил: `waves=95`, `boss=123`, `boss_half=26`, `boss_final=39`, `victory=0`; победный OGG — ровно 20 секунд.

В логах финального запуска нет `NoClassDefFoundError` и `CopiMineEndEvent failed closed`.

## Реальная GUI-проверка

- Fabric-клиент подключён к изолированному Paper на `127.0.0.1:25566`; локальный HTTP-сервис ресурспака временно поднят только на `127.0.0.1:8092`. Ответ ZIP: HTTP `200`, `8,411,791` байт, SHA-1 `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.
- В Minecraft после `/cmend core set 2` реально видны симметричный сине-фиолетовый Core на выбранном каменном блоке и две большие руны, лежащие на верхней поверхности блоков; ванильный блок под overlay не заменён.
- После заполнения ресурсов status стал `READY_FOR_PLAYERS`, `coreModel=830002`, `runes=2/2`. При телепортации тестового игрока на руну status показал `occupied=1`; в GUI занятая руна реально переключилась на ярко-зелёную текстуру и осталась на поверхности блока.
- Локальный `/cmend test ai` показал в GUI event-мобов и босса; отдельный свежий кадр содержит видимые фиолетовые частицы летящего заклинания. После проверки выполнен `/cmend core remove confirm`; status снова чистый, `event-mobs=0`, `boss=none`, `coreOverlay=false`, `runes=0/0`.
- Эти наблюдения сделаны в реальном окне Minecraft, а не на концепт-рендерах. Окно лаунчера не использовалось.
