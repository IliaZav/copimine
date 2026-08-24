# End Rift Event — финальная локальная проверка

Дата: 24 августа 2026 года

Ветка: `codex/end-rift-event`

Worktree: `D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event`

## Граница безопасности

- Проверка выполнялась только в `local-runtime\end-rift-server`.
- Paper: `127.0.0.1:25566`, RCON: `127.0.0.1:25576`.
- PostgreSQL: изолированный локальный экземпляр `127.0.0.1:55433`.
- Launcher, сайт, production world, production DB и production deploy не запускались и не изменялись.
- После destructive-проверки Core локальный status оставлен чистым: активных event-owned сущностей нет.

## Source/build gate

`tests\RunEndRiftEventChecks.ps1` завершился успешно:

- Python contracts: `115 passed, 14 warnings`.
- WorldCore, Artifacts, End Event и Fabric client собраны.
- Pure domain tests: все 5 `OK`.
- Durable persistence/layout tests: все 3 `OK`.
- Resource pack собран, SHA-1: `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.

Артефакты этого прогона:

| Артефакт | SHA-256 |
| --- | --- |
| WorldCore | `380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99` |
| Artifacts | `A2FEB49C31FA9FBA61D9D1DA655E52F888BE41FEC10F904E9FC437F0618BD7DE` |
| End Event | `0FDCFFBC2550F486F745A76BCD1C05C04DC32412B5EF5F9B330D6F24AB953603` |
| Fabric client | `0CD49DBE1517EB2B7BC04B40990C707AF9AEF96F4E67E3432F62F4AB955AD9D6` |
| Resource pack ZIP | `9D65787D380791D0E873B29F6E126E81A69D17D5BAD4B66D5AB23DB5738974AB` |

Контракт музыки отдельно проверяет, что победный OGG имеет длительность `20.000 s`; focused run: `6 passed` вместе с DOCX contract.

## Локальный Paper/PostgreSQL smoke

Команда `tests\RunEndRiftRuntimeSmoke.ps1` на свежем локальном запуске завершилась:

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
PASS End Event services ready
PASS EconomyCore PostgreSQL ready
End Rift isolated runtime smoke passed.
```

Первый стартовый health-снимок Artifacts закономерно появляется раньше async PostgreSQL init (`ready=false`), поэтому smoke выполняет реальный `/cmartifacts reload` и проверяет успешный PostgreSQL command path плюс `CopiMineEconomyCore PostgreSQL is ready.`. Это проверяет рабочую БД, а не случайный порядок двух startup-логов.

Финальный RCON status после очистки:

```text
state=UNCONFIGURED generation=2
core=CopiMine 8,68,-32
gate=RESTORED
pads=0/0 roster=0 participants=0
visuals=coreOverlay=false coreModel=0 runes=0/0 occupied=0
wave=0 event-mobs=0 boss=none half=false final=false endUnlocked=false victory=NONE
```

## Runtime markers

Источник creative/gate/cleanup evidence: `local-runtime\end-rift-paper-local-7.out.log`. Источник финального pack/music smoke: `local-runtime\end-rift-paper-local-final.out.log`.

- `CREATIVE_TEST_BOSS_ACTIVE`: `health=1200.0 maxHealth=1200.0 bossbar=true`.
- `CREATIVE_TEST_HALF`: `threshold=600.0 health=600.0`.
- `CREATIVE_TEST_FINAL_DRAIN`: `threshold=120.0 finalHealth=200.0 drainFraction=0.6`.
- `MINIBOSS_SPELL_TELEGRAPH/FLIGHT/CAST`: `8 / 3 / 3`; все три мини-босс-заклинания прошли видимый flight phase с частицами.
- `BOSS_SPELL_FLIGHT` и `BOSS_SPELL_CAST`: по `11`; projectiles прошли telegraph → flight → cast.
- `SPIDER_STATS`: фактические `health=26.0 attack=4.0`; Endermite в creative-run не использовались.
- `CREATIVE_TEST_FINAL_WAVE`: `{spider=8, shulker=2, enderman=6}`.
- `CREATIVE_TEST_CLEANUP` и `CREATIVE_TEST_COMPLETE`: `success=true`, `official_phase=READY_FOR_PLAYERS`, `official_roster=0`, `endUnlocked=false`.
- `END_EVENT_GATE_SELECTION_PREVIEW`: particle-only preview; vanilla blocks оставались неизменными.
- `GATE_TOP_UNCHANGED`, `GATE_MID_UNCHANGED`, `GATE_BOTTOM_STONE_UNCHANGED`: исходные блоки до открытия не заменялись.
- `END_EVENT_GATE_LAYER`: 3 слоя обработаны сверху вниз; после теста gate восстановлен.
- `END_EVENT_OWNED_CLEANUP`: `generations=all removed=4`; старые wave entities/boss/projectiles очищаются при удалении Core.
- Финальный protocol bot получил resource pack hash `aaa2605b2b0f2751f5eab5258173d5c6699d45be`.
- `END_EVENT_MUSIC_TEST`: `track=copimine:end_rift/victory loopSeconds=0` на локальном игроке; победный файл — ровно 20 секунд.

В логах финального запуска нет `NoClassDefFoundError` и `CopiMineEndEvent failed closed`.
