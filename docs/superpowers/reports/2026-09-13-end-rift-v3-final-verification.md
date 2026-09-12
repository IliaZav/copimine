# End Rift Event V3 — финальная проверка 2026-09-13

## Область работы

Проверялся только End Rift Event в репозитории `IliaZav/copimine`, ветка
`codex/end-rift-event`. Сайт, admin-web и его дизайн не менялись и в эту
проверку не входили. Реальный боевой сервер и отдельный процесс на порту
25565 не использовались.

Рабочая копия:

~~~
D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event
branch: codex/end-rift-event
gameplay/build candidate SHA: cd0720790fc958cd5dd91845e53057f66f4c12a5
remote: https://github.com/IliaZav/copimine.git
~~~

В финальной серии коммитов V3 были доведены тестовый контур, live-пробы и CI:

- live-переход Wave 4 исправлен так, чтобы боты действительно попадали в
  obelisk ring, а не в общий combat ring;
- в W3 добавлен устойчивый приём authoritative completion последнего портала;
- переход W7 -> boss больше не теряет событие `BOSS_CINEMATIC_STARTED` из-за
  сброса log cursor;
- в CI зафиксированы Paper API, Guava и SnakeYAML, чтобы проверки не зависели
  от случайного порядка jar-файлов и окружения runner;
- для reflection/live-проб добавлена проверка фактического состояния
  projectile, а не только наличия callback.

Основная V3 игровая реализация уже находилась в проверяемом baseline ветки:
реальный HP босса, семь волн, шесть boss phases, щупальца, obelisks,
projectile/client bridge, cleanup и persistence. В этой серии не добавлялись
новые сайт-компоненты и не переписывался сайт.

## Найденная причина пропадающего урона

Проблема была не в отсутствии `EntityDamageEvent`. Paper/vanilla сначала
передавал событие, но у LivingEntity оставалось стандартное hurt-resistance
окно (`noDamageTicks`, `maximumNoDamageTicks`, `lastDamage`). Поэтому быстрые
независимые удары могли выглядеть принятыми в listener, но физический HP
изменялся только на разницу с предыдущим уроном или не изменялся. Отдельно
визуальная hurt-анимация не являлась доказательством изменения HP.

Исправление локальное и authoritative:

1. Сначала сохраняется исходное состояние Combat Trace на `LOWEST`.
2. Предыдущие protection/shield listeners не обходятся: уже отменённое событие
   не «воскрешается».
3. Для принадлежащего текущему event generation wave mob или официального
   V3 boss вычисляется `finalDamage`.
4. Если удар разрешён и урон положительный, Bukkit event отменяется до native
   application, затем exact amount один раз записывается в настоящее
   `LivingEntity` HP через `setHealth`.
5. На следующем server tick trace сравнивает `healthBefore`, `healthAfter` и
   `nextTickHealth`.
6. Для shield, stale generation, невалидной entity и не-положительного урона
   остаётся отдельная причина отказа; HP не меняется.

Это не глобальный сброс `noDamageTicks`, не глобальная установка
`invulnerable=false` и не второй virtual-HP источник. Для официального V3 boss
authority — `LivingEntity.getHealth()/setHealth()` и `GENERIC_MAX_HEALTH`.

Ключевые места реализации:

- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  — event priorities, Combat Trace, mob/boss transaction, shield/source
  filters, cleanup;
- `copimine-end-event/src/me/copimine/endevent/domain/EventRealHealthDamagePolicy.java`
  — bounded real-health transaction для event mobs;
- `copimine-end-event/src/me/copimine/endevent/domain/BossRealHealthDamagePolicy.java`
  — real-health transaction босса;
- `copimine-end-event/src/me/copimine/endevent/domain/CombatTraceRecord.java`,
  `CombatTraceDiagnosis.java` и `runtime/CombatTraceService.java` — trace и
  диагностика hurt-resistance;
- `copimine-end-event/src/me/copimine/endevent/domain/RiftObelisk*` и
  `RiftFireballPolicy.java` — scaling, HP, reflection-only damage и cap.

## Что проверено автоматически

Команды запускались из корня worktree.

~~~
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftEventChecks.ps1
~~~

Результат: `PASS`.

- current Python End Rift contracts: `31 passed`;
- Java policy tests: `PASS`;
- persistence/recovery fixtures: `EventStateStoreTest`, `DepositJournalTest`,
  `EventLayoutStoreTest`, `HazardMutationJournalTest`,
  `LegacyEndRiftSnapshotDecoderTest`, `EncounterResourceScopeTest`,
  `EventTaskRegistryTest` — `PASS`;
- client/resource pack build — `PASS`;
- diff hygiene — `PASS`.

~~~
python -m pytest .\tests\test_end_event_current_contract.py .\tests\test_resourcepack_visual_contract.py .\tests\test_custom_projectile_visual_contract.py .\tests\test_custom_block_visual_cleanup_contract.py -q
~~~

Результат: `41 passed in 0.77s`.

~~~
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tests\RunCopiMineValidators.ps1
~~~

Результат: `VALIDATOR_SUMMARY total=659 passed=659 failed=0 skipped=0`.

Отдельные regression/contract проверки включают:

- real boss HP без legacy virtual-health marker;
- два и пять одновременных атакующих, same-tick damage groups;
- shielded/vulnerable boss и stale generation;
- normal player melee/projectile и Rift Fireball immunity босса;
- обелиск: direct sword/arrow не снимают HP, только reflected current
  generation projectile;
- scaling и cap обелисков;
- cleanup при reset, смерти, recovery и смене generation;
- Wave 3 portal completion, Wave 4 ring placement и W7 -> boss transition.

## CI и сборка

Последний полностью зелёный CI для игрового кода и release-artifact checks:

~~~
workflow run: 34718626995
https://github.com/IliaZav/copimine/actions/runs/34718626995
sha: cd0720790fc958cd5dd91845e53057f66f4c12a5
conclusion: success
jobs: java-plugins=success, static-and-contract=success
~~~

Успешные шаги включают compile first-party plugins, CopiMineClient release
artifact, resource pack release artifact, все source/release validators,
End Rift event gate и upload diagnostics.

Локальные SHA-256 артефактов:

~~~
CopiMineWorldCore       380793DEB02B6C51C42DAE446A39A7BF969F7DF33510989590AC50317EE90E99
CopiMineArtifacts       A9F372117B9A44EE7053E8F8655008314DB78D1D66235842D3D5C29116E9BCC9
CopiMineEndEvent        AF5425D8914EF83E3493FCD5274FD5404AA0BF504467E89E7CDAE77F854E9D49
CopiMineEconomyCore     5AF9F3D24159E2EDAE606373200CF8996E0D936D966BFD349793E7EBB7DBCD9D
CopiMineElectionCore    0802D79AF98035371B2F3440C7A782ECADEB8ACDD8EEB28AFBB821AFC61CFA2E
CopiMineNarcotics       EF2413D82F484A1087B4C8C1113A61289798DCC3152F0244390FD9BE1809A0BF
CopiMineUltimateAdmin   BD32CC7F1F27DB59DBA535B914ED9E455500EB24D15053AFE7C7F51A0208F5FC
AuthEffects             14427CD86435ED0354E8A20A1F96F14803EEB818E2CE4F2A70ED256DF3B9E063
CopiMineClient          1B07DE9FE4A2685D2F092851BFDF007E9DC7261CD2C480BE1769899C5D6DF449
Resource pack           9A5F444EA31F84EB3A5B476B3E65EE1A18B627AA2F57DA8E753E856DB634E47D
~~~

Resource pack build: SHA1 `73e44bed865225cbce39f42afa92aff4dde1e670`,
`24,147,549` bytes. В нём присутствуют модели/текстуры V3 для core, rune,
portal, wave ring, obelisk FULL/DAMAGED/CRITICAL, Rift Fireball, boss phases,
mob overlays и tentacle assets. Client catalog/JSON/asset-presence tests
прошли. Обычный vanilla fireball не переназначается глобально.

## Paper runtime: полный 2-player прогон

Скрипт:

~~~
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tests\RunEndRiftOfficialTwoPlayerLive.ps1 -BotDurationSeconds 1200 -TimeoutSeconds 1200
~~~

Изолированный Paper runtime, event id:

~~~
07a849f8-4e19-44b2-8f60-1b92866aa4b8
players=2
~~~

Фактический результат:

~~~
CURRENT_RITUAL_PASS
CURRENT_WAVE_PASS wave=1 objective=RIFT_CARRIERS
CURRENT_WAVE_PASS wave=2 objective=RIFT_HUNT
CURRENT_WAVE_PASS wave=3 objective=RIFT_GATES portals=3
CURRENT_WAVE_PASS wave=4 objective=OBELISK_ASSAULT obelisks=4
CURRENT_WAVE_PASS wave=5 objective=BLACK_FOG cycles=3
CURRENT_WAVE_PASS wave=6 objective=COLLAPSE_RINGS rings=3
CURRENT_WAVE_PASS wave=7 objective=REALITY_SPLIT chambers=local
CURRENT_OFFICIAL_PASS waves=1,2,3,4,5,6,7
stages=AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL
victory=true
~~~

В логе зафиксированы boss casts, visual cues, реальные
`BOSS_DAMAGE_ACCEPTED` transactions, переходы `OVERLOAD -> RAGE`, смерть
entity и ровно один defeat path. После финала:

~~~
state=COLLECTING
generation=814
wave=0 event-mobs=0 boss=none
rift-obelisks=0/6 assault=IDLE rift-fireballs=0
victory=NONE
~~~

Во время промежуточного victory cleanup лог также зафиксировал:

~~~
END_EVENT_OWNED_CLEANUP generations=all removed=0
END_EVENT_CORE_VISUALS_REMOVED count=2
~~~

После победы event core visuals были восстановлены для нового сбора. Полная
проверка фактического содержимого инвентаря обоих реальных игроков в native
клиенте не выполнялась; durable reward/idempotency contracts прошли.

Evidence:

~~~
artifacts/end-rift-v3-evidence/20260913-000942/logs/official-current-live.log
artifacts/end-rift-v3-evidence/20260913-000942/server/latest.log
~~~

## Damage evidence

Real boss health smoke:

~~~
LIVE_BOSS_REAL_HEALTH_PASS
status=hp-5000/5000 physical-5000/5000
attribute-unclamped=true current-health-marker=true legacy-virtual-marker=false
~~~

Combat Trace smoke:

~~~
LIVE_COMBAT_TRACE_PASS wave_traces=184 player_wave=48 exact_wave=47 boss_traces=19
~~~

Two-player same-target live probe:

~~~
events=8
before=5000
after=4977.4023
summed_final_damage=22.59712004661560
expected=4977.40287995338440
health_delta=0.00057995338440
same_tick_event_groups=2
cleanup=PASS
~~~

Five-player live probe:

~~~
events=20
before=5000
after=4943.506
summed_final_damage=56.49280011653900
expected=4943.50719988346100
health_delta=0.00119988346100
same_tick_event_groups=2
cleanup=PASS
~~~

Обе пробы прошли с ограничением `health_delta <= 0.05`. Малые отличия
объясняются тем, что Bukkit хранит entity health как float и округляет запись
после каждой транзакции; это не потерянные события. В Paper log для каждой
принятой атаки есть `accepted=true cancelled=true authority=entity-health
transaction=real-health`, `health_before` и `health_after`.

Mob combat smoke:

~~~
moved=1499 attacks=60 player_hurt=94 player_damage_applied=67
ai_targets=92 ai_paths=57
~~~

За пределами explicit End Rift targets vanilla damage path не перехватывается.

## Rift Obelisks

Paper live obelisk probe:

~~~
players=2 obelisks=4 active_before=4
reflected_hits=3
first_target_hp=2 second_target_hp=1 destroyed=true
pulse_radius=5 fireball_cap=1 real_blocks=true
~~~

Load/scaling probe:

~~~
3 players:  obelisks=4/4
10 players: obelisks=5/5
20 players: obelisks=6/6 fireballs=3/8 staggered=true
pulse_radius=5 pulse_ticks=40 hard_cap=56 arena_bound=true
cleanup projectiles=0, obelisks=0
~~~

Проверено, что direct melee/arrow/обычный fireball не снимают HP обелиска,
старое generation не проходит, а reflected current-generation Rift Fireball
снимает ровно одну единицу за попадание. Rift Fireball не наносит урон боссу и
его explosion не ломает arena blocks.

## Cleanup, recovery и производительность

Прошли `RunEndRiftDiagnosticsFailureLive.ps1 -Wave 2`,
`RunEndRiftRecoverySmoke.ps1`, AI phases live и obelisk load smoke. В runtime
diagnostics после финала видны `owned_living=0`, `projectiles=0` и отсутствие
остаточных event mobs. В локальном server log нет fatal End Rift exception.

Зафиксированы только некритичные предупреждения сторонних компонентов:
deprecated listener warnings и отсутствие GeoLite database у AuthMe. Они не
останавливают Paper и не относятся к End Rift damage/event flow.

На runtime diagnostics во время события:

~~~
avg_ping_ms=0
max_ping_ms=0
server_thread_cpu_percent примерно 0.3–3.4
particle_packets_sec bounded
gc_pause_ms=0
~~~

Полный native 20-player soak/максимальный TPS/FPS прогон не выполнен.

## Native Minecraft visual verification

`cua.getState()` не обнаружил доступной native Minecraft app surface: в
окружении видны только браузерные поверхности и Codex; процесс TLauncher не
управляется через предоставленный UI-интерфейс.

Поэтому следующие пункты имеют статус `NOT VERIFIED`:

- вход в Minecraft native client;
- скриншоты и видео Wave 1–7, boss casts, tentacle bones/socket и obelisk
  reflection;
- реальный внешний вид 3D portal, boss/tentacle model, animations, depth и
  Z-fighting на клиенте;
- фактическое звучание музыки и плавность переходов;
- native FPS/frametime на 2/3/10/20 клиентах;
- полный проход с 3, 10 и 20 реальными игроками.

Source/resource-pack/client contracts и Paper server behavior проверены; они
не заменяют ручной visual QA в клиенте.

## Файлы этой серии изменений

Изменённые tracked-файлы относительно исходной точки продолжения
`1fa552e8bebbb15633ffdabaad4c41a6eff660d4`:

~~~
.github/workflows/ci.yml
tests/LocalEndRiftMobCombatBot.js
tests/LocalEndRiftObeliskBot.js
tests/RunEndRiftEventChecks.ps1
tests/RunEndRiftObeliskLive.ps1
tests/RunEndRiftOfficialTwoPlayerLive.ps1
tests/test_end_event_current_contract.py
docs/superpowers/reports/2026-09-13-end-rift-v3-final-verification.md
~~~

На момент подготовки отчёта сайт и его исходники отсутствуют в списке
изменений.

## Git

Исходная точка этой серии:

~~~
1fa552e8bebbb15633ffdabaad4c41a6eff660d4
~~~

Коммиты тестового/CI hardening до отчёта:

~~~
6e2be4e2 test(end-rift): stabilize live reflection probes
82aa4a35 ci(end-rift): include pinned Paper API in recovery tests
dfccc075 ci(end-rift): expose gate failure diagnostics
560b2f42 ci(end-rift): install pytest for event gate
fe33966d ci(end-rift): include SnakeYAML for persistence gate
a3d5c6b0 ci(end-rift): pin SnakeYAML persistence fixture
cd072079 test(end-rift): make persistence classpath deterministic
~~~

После добавления этого отчёта он будет закоммичен отдельным docs-коммитом и
отправлен в `origin/codex/end-rift-event`. Финальный SHA отчёта и отдельный
CI run для него будут указаны в итоговом сообщении после повторной проверки
remote.

## Итоговый verdict

~~~
SOURCE/CONTRACTS: PASS
CI/build: PASS
Paper 2-player full flow: PASS
Damage real-HP regression: PASS within documented float tolerance
Obelisk reflection/scaling/cleanup: PASS
Recovery/cleanup smoke: PASS
Native client visual/audio/video gate: NOT VERIFIED
3/10/20 full native-player runs: NOT VERIFIED
RELEASE VERDICT: NOT READY FOR FINAL VISUAL RELEASE
~~~

Причина `NOT READY` — отсутствие управляемого native Minecraft UI в текущем
окружении, а не ошибка, скрытая в серверных тестах. Для выпуска нужны ещё
ручные клиентские скриншоты/видео и реальные 3/10/20-player runs.
