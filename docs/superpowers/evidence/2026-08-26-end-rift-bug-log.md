# CopiMine End Rift — журнал багов и проверок

Дата среза: 2026-08-27. Область: только `codex/end-rift-event`, локальный
Paper/PostgreSQL и локальный Fabric-клиент. Боевой `minecraft/server`, боевой
мир/БД, лаунчер и сайт в изменения не входили.

Статус `исправлено` означает, что есть исходный/regression-контракт и свежая
локальная проверка. Старые отчёты из предыдущих запусков не считаются заменой
свежему runtime-доказательству.

| ID | Симптом | Причина и исправление | Проверка |
|---|---|---|---|
| E-001 | У ядра не было текстуры или она оказывалась выше/ниже блока | Core оставлен настоящим выбранным ванильным блоком; визуал привязан `ItemDisplay` к точной верхней грани | `coreOverlay=true`, `coreModel=830001`; resource-pack/client contracts |
| E-002 | Руны выглядели как бумага, были маленькими или висели | Используются собственные 32×32 модели/текстуры, полный верхний слой pad-блока и точная координата пола | texture-quality, visual-regressions, live GUI evidence |
| E-003 | Свободные руны пропадали или не переключались при игроке | Добавлен rebuild отсутствующих display и раздельные `runeVisualOccupants`/official `padOccupants`; occupied-модель меняется только для стоящего игрока | `runes=2/2`, `occupied=0/1`; creative markers и live client |
| E-004 | В волнах оставались эндермиты | Волновой состав переведён на `SPIDER`; эндермит не используется в конфиге/спавне | fresh creative log: wave 1/2/final compositions; no `endermite` contract |
| E-005 | Волновые мобы и босс уходили дальше 20 блоков или по Y | Общий leash ограничивает горизонталь 20 блоками, сохраняет уровень Core и переводит запрос на Core в верхнюю грань блока | AI/containment contracts и fresh Paper spell/wave run |
| E-006 | У босса/мини-боссов был слабый или случайный AI | Единый контроллер целей, память последних целей, cooldown/telegraph и отдельное заклинание элита | `MINIBOSS_SPELL_*`, `BOSS_SPELL_*` markers; AI contracts |
| E-007 | Заклинания были неразличимыми или текст оставался английским | Русские имена в `EndRiftAiPolicy`; каждый летящий spell имеет собственный particle pattern и bounded flight | fresh Paper: telegraph → flight → cast для всех проверенных spell IDs |
| E-008 | После удаления ядра оставались старые мобы/боссы/снаряды/визуалы | Hard-boundary cleanup удаляет все event-owned роли и unbind-ит клиентские visual bindings | runtime-invariants, visual cleanup и `event-mobs=0`, `boss=none` |
| E-009 | Граница арены/проход/портал не соответствовали стенду | Локальная сцена пересоздаётся на фиксированных координатах; граница 20×20×3, ворота 3×4 без крыши, портал в отдельной каменной комнате | `RunEndRiftLocalSceneSmoke.ps1`, batch scene output |
| E-010 | Музыка могла звучать вне активного ивента | Music routing ограничен active event phases; test-only combat не включает обычную музыку | music scope contracts и локальный startup/runtime checks |
| E-011 | При входе оставалось замедление на 8 минут | AuthEffects теперь снимает только собственный auth-lock эффект и не удаляет чужую Slowness; AuthMe login/register paths остаются доступны | fresh protocol login: `Successful login!`; auth slowness contract |
| E-012 | Точка возрождения на кровати не сохранялась | Локальный стенд делегирует bed respawn ванильной механике через EssentialsXSpawn | batch output `Configured EssentialsXSpawn to delegate player bed respawn to vanilla`; bed contracts |
| E-013 | Локальный запуск падал на «unexpectedly large server.properties» или мог задеть production template | Launcher ограничивает только isolated local file, проверяет tracked production hash и пропускает tracked property при pack sync | fresh `CopiMine-local-start.bat` exit 0; tracked SHA unchanged |
| E-014 | Creative full-run не запускался при уже открытом Энде | `WorldCore` хранит постоянный факт открытия Энда, а idle event остаётся `COLLECTING`; precondition ошибочно запрещал `endUnlocked` | RED reproduced in GUI; GREEN fresh `CREATIVE_TEST_START` → `CREATIVE_TEST_COMPLETE` |
| E-015 | После disposable full-run в status оставался `helpers=1` | `finishCreativeTest` очищал mobs/boss, но не весь transient AI/helper state; добавлен `clearCombatAiState()` | regression contract GREEN; свежий post-fix full-run 26.08.2026 завершён `CREATIVE_TEST_COMPLETE success=true`, итоговый `cmend status`: `helpers=0`, `event-mobs=0`, `boss=none` |
| E-016 | Нельзя было доказать, что в runtime лежит весь актуальный набор и что лавка не пуста | Источник проекта и manifest не были сведены в одну completion-проверку | 30 source JAR = 30 manifest JAR = 30 loaded Bukkit plugins; shop API: 26 AR + 9 donation, all enabled/valid |
| E-017 | Полный pytest после проверки текстур снова делал production-шаблон dirty | В `test_end_event_visual_regressions.py` и полном `RunEndRiftEventChecks.ps1` builder вызывался без безопасного режима; добавлены `--skip-server-properties`/`-SkipServerProperties`, wrapper и AST-регрессионные контракты | fresh full event checks: `142 passed`; full project suite: `486 passed`; safe pack build оставил production SHA-256 неизменным |
| E-018 | В новой волне мобы складывались на ядре вместо спавна по арене | `coreLocation()` — верхняя грань блока; `safeSpawnLocation()` проверял пол на один блок выше пола арены и при неудаче возвращал ядро. Спавн переведён на floor-level anchor, неудача больше не даёт fallback на ядро | TDD RED на elevated-Core spawn contract → GREEN; Paper runtime wave 1: `event-mobs=10`, все проверенные позиции `y=68`; namespaced teleport из арены на `100,100,100` вернул моба к `[0.5,68,-39.5]`; waves 2/3/final: `event-mobs=13`, Endermite probe пуст; cleanup: `event-mobs=0`, `boss=none` |

## Источники доказательств

- Исходный набор плагинов: `minecraft/server/plugins` и `deploy/plugin_versions.json`.
- Регрессионные контракты: `tests/test_end_event_*.py`, включая
  `tests/test_end_event_creative_run_contract.py` и этот completion audit.
- Локальная сцена: `tests/RunEndRiftLocalSceneSmoke.ps1`.
- Живой прогон: `local-runtime/end-rift-server/logs/latest.log` с маркерами
  `CREATIVE_TEST_*`, `MINIBOSS_SPELL_*` и `BOSS_SPELL_*`.
- Локальный ресурс-пак: `resourcepacks/build/CopiMineResourcePack.zip`;
  vanilla texture namespace не переопределяется.
