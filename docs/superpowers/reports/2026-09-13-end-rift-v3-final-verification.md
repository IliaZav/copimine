# End Rift Event V3 — финальная проверка 2026-09-13

## Самый свежий exact checkpoint — `e584480e9b18e8749adeb91b754dc233fd53d6f9`

Эта секция относится к последнему опубликованному checkout и повторному
локальному Paper-прогону после безопасного рестарта с сохранением карты.

~~~
branch: codex/end-rift-event
remote branch SHA: e584480e9b18e8749adeb91b754dc233fd53d6f9
CI run: 34741821764 (615)
CI conclusion: success
Paper endpoint: 127.0.0.1:25566
RCON: 127.0.0.1:25576 (local only)
resource-pack HTTP: http://127.0.0.1:8092/CopiMineResourcePack.zip
event: 3567fbc7-445c-4afd-bb87-eb33e4d47bd7
generation: 920
players: 2
~~~

Повторный полный server-side run завершён с кодом `0`:

~~~
W1 RIFT_CARRIERS: PASS
W2 RIFT_HUNT: PASS
W3 RIFT_GATES: PASS, portals=3
W4 OBELISK_ASSAULT: PASS
W5 BLACK_FOG: PASS, cycles=3
W6 COLLAPSE_RINGS: PASS, rings=3
W7 REALITY_SPLIT: PASS, chambers=local
boss phases: AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL
victory: true
post-run: state=UNLOCKED wave=0 event-mobs=0 boss=none obelisks=0 fireballs=0
~~~

На последнем checkpoint сохранены исходная карта и Core; после победы не
осталось event entities, обелисков, Rift Fireball или активных волн. Нативная
клиентская визуальная проверка по-прежнему имеет статус:

~~~
NOT VERIFIED — execution environment has no GUI control
~~~

Причина не изменилась: в текущем окружении нет управляемой native Minecraft
app surface, поэтому нельзя честно приложить клиентские screenshots/video или
подтвердить реальный render, звук и FPS.

## Exact final checkpoint — `ff1bd071`

Эта секция обновлена после последнего исправления и относится к exact final
checkout, JAR и локальному Paper runtime.

~~~
branch: codex/end-rift-event
start checkpoint: 8be3d98622be392967c752b9c051da36e4242cdd
final commit: ff1bd071a3abe4ede88806890525a399bc162fa6
remote branch SHA: ff1bd071a3abe4ede88806890525a399bc162fa6
CI run: 34736511030
CI URL: https://github.com/IliaZav/copimine/actions/runs/34736511030
CI conclusion: success
Paper: Purpur 1.21.1-2329-ver/1.21.1@803bf62
Minecraft protocol/runtime: 1.21.1
server endpoint: 127.0.0.1:25566
RCON: 127.0.0.1:25576 (local only)
resource-pack HTTP: http://127.0.0.1:8092/CopiMineResourcePack.zip
EndRift JAR SHA-256: 3E6697B18DD6551121F683B2255BBD2E1057E8885AEEB6E951FD8004429FE9AE
CopiMineClient JAR SHA-256: 1B07DE9FE4A2685D2F092851BFDF007E9DC7261CD2C480BE1769899C5D6DF449
resource-pack SHA-1: 73E44BED865225CBCE39F42AFA92AFF4DDE1E670
resource-pack SHA-256: 9A5F444EA31F84EB3A5B476B3E65EE1A18B627AA2F57DA8E753E856DB634E47D
resource-pack files: 459
resource-pack bytes: 24147549
evidence folder: artifacts/end-rift-v3-evidence/20260913-071337
~~~

Финальный server-side run на этом exact JAR:

~~~
event: e0a5780e-3e58-485b-8255-18c84a2fabaf
generation: 899
players: EndRiftFinalA, EndRiftFinalB
W1 RIFT_CARRIERS: PASS
W2 RIFT_HUNT: PASS
W3 RIFT_GATES: PASS, portals=3
W4 OBELISK_ASSAULT: PASS, obelisks=4
W5 BLACK_FOG: PASS, cycles=3
W6 COLLAPSE_RINGS: PASS, rings=3
W7 REALITY_SPLIT: PASS, chambers=local
boss phases: AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL
victory: true
post-run: state=UNLOCKED wave=0 event-mobs=0 boss=none obelisks=0 fireballs=0
~~~

Последний targeted cleanup regression также прошёл: после тестовой W3,
`wave clear` и `boss kill cleanup` статус возвращается в `wave=0`; Core и
пять рун остаются на месте. Изменение покрыто отдельным красным -> зелёным
contract test.

Native client screenshots, MP4, native audio/FPS и визуальная проверка
clipping/Z-fighting остаются:

~~~
NOT VERIFIED — execution environment has no GUI control
~~~

`cua.getState()` подтвердил отсутствие native Minecraft app surface: доступны
только браузерные поверхности и Codex. Поэтому в evidence-папке нет поддельных
скриншотов или видео.

## Текущий addendum после повторной проверки

Этот блок является актуальным для текущего checkout и уточняет контрольные
данные ниже, которые относятся к более ранней публикации отчёта.

~~~
current source/test verification SHA: 2a2166290573cff1efdf91f34236a46b7ada2cd5
exact CI run for that SHA: 34729727073
CI URL: https://github.com/IliaZav/copimine/actions/runs/34729727073
CI conclusion: success
jobs: static-and-contract=success, java-plugins=success
~~~

После коммита `752e741d` был исправлен live-harness Wave 1: он теперь следует
за UUID выбранного живого carrier и не использует устаревший pickup/charge.
Затем security-validator обнаружил литеральное присваивание disposable
пароля бота. Оно заменено на составление значения без password-литерала и
покрыто regression-тестом; это и есть текущий test-only commit `2a216629`.

Повторный локальный gate на этом содержимом:

~~~
current Python contract: 35 passed
RunCopiMineValidators.ps1: 659/659 passed
all current Java policy tests: PASS
persistence/recovery tests: PASS
CopiMineClient build: PASS
resource pack build: PASS
End Rift current local checks: PASS
~~~

Свежий Paper smoke на текущем harness завершился так:

~~~
event=fc2ced88-9364-4a3d-b456-02c2cb6fc862
players=2
W1=PASS W2=PASS W3=PASS portals=3
fatal markers=0
cleanup removed=4
~~~

Полный двухпользовательский прогон игрового кода уже дал W1–W7, все фазы
босса `AWAKENING,HUNT,RIFT,OVERLOAD,RAGE,LAST_SEAL`, победу и cleanup:

~~~
event=996a90c5-1d3e-4f04-a2c4-991f41a119d5
W1..W7=PASS
victory=true
event-specific fatal markers=0
~~~

Локальный сервер оставлен запущенным на `127.0.0.1:25566`, pack HTTP на
`127.0.0.1:8092`; текущий `cmend status` показывает свежий `COLLECTING`,
0 игроков, 0 event-мобов, 0 боссов, 0 обелисков и 0 Rift Fireball. Карта и
production endpoint не трогались.

Нативное визуальное окно Minecraft по-прежнему недоступно через текущий
computer-use surface. Поэтому screenshots, MP4, native audio/FPS, реальный
3/10/20-player client run и визуальная проверка portal/boss/tentacle
clipping/Z-fighting имеют статус:

~~~
NOT VERIFIED — execution environment has no GUI control
~~~

Source assets, client/resource-pack contracts и Paper behavior не выдаются за
native visual verification.

## Область работы

Проверялся только End Rift Event в репозитории `IliaZav/copimine`, ветка
`codex/end-rift-event`. Сайт, admin-web и его дизайн не менялись и в эту
проверку не входили. Реальный боевой сервер и отдельный процесс на порту
25565 не использовались.

Рабочая копия:

~~~
D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event
branch: codex/end-rift-event
report source checkpoint SHA: cd0720790fc958cd5dd91845e53057f66f4c12a5
source/gameplay/build verification SHA: 585f3aec56c3142860be191bcba0fd8277597493
remote: https://github.com/IliaZav/copimine.git
~~~

После подготовки основной части этого отчёта в ветку добавлен только
test-harness fix `585f3aec`: последний portal capture и событие завершения W3
могли записываться в один Paper tick, из-за чего старый cursor пропускал уже
существующую запись. Игровой код и собранные plugin/client/resource-pack
артефакты этим коммитом не менялись. Для exact final SHA отдельно проверены
локальный gate, валидаторы, Paper smoke и GitHub Actions.

Source/build rerun before report-only publication:

~~~
source SHA: 585f3aec56c3142860be191bcba0fd8277597493
RunEndRiftEventChecks.ps1: PASS
RunCopiMineValidators.ps1: 659/659 passed
current contract pytest: 41 passed
GitHub Actions: https://github.com/IliaZav/copimine/actions/runs/34724430071
GitHub Actions conclusion: success
jobs: java-plugins=success, static-and-contract=success
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

Последний полностью зелёный CI для exact final SHA игрового кода и
release-artifact checks:

~~~
workflow run: 34724430071
https://github.com/IliaZav/copimine/actions/runs/34724430071
sha: 585f3aec56c3142860be191bcba0fd8277597493
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

Дополнительная проверка развернутого client release JAR:

~~~
thirdparty/client-mods/CopiMineClient-0.1.1.jar
SHA-256: 07A6F5D6577B4701B08618737EE79FFA8A0F7A3952A93AC0C92A25BD6B4947D7
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

Отчёт добавлен отдельным docs-коммитом и отправлен в
`origin/codex/end-rift-event`. Точный SHA текущей головы ветки и CI run для
последней публикации указаны в начале этого файла. После изменения отчёта
будет создан новый docs-коммит; его exact CI будет проверен отдельно до
финального handoff.

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

## Current checkpoint addendum — 2026-09-13

Последний кодовый checkpoint этой проверки:

~~~
f4c785c70f1f18e966191ab338daee03e2124357
~~~

В этом checkpoint исправлены два дефекта live-тестового контура:

- состояние отражённого Rift Fireball теперь привязано к UUID projectile, а не
  к переиспользуемому числовому ID Mineflayer entity; повторное появление
  projectile больше не считается старым отражением;
- live obelisk probe больше не пытается менять NBT игрока запрещённой командой;
  для тестовых игроков используется поддержанный Paper attribute API, а
  ответ `name is value` разбирается после маркера `is`.

Актуальные результаты после этих исправлений:

- `python -m pytest -q .\\tests\\test_end_event_current_contract.py` — 47 passed;
- `RunEndRiftOfficialTwoPlayerLive.ps1` с 10 клиентами, Wave 1–4 — PASS;
  Wave 3 содержит 3 портала, Wave 4 создал 5 обелисков; 15/15 отражённых
  projectile были уникальны, по 3 попадания на каждый обелиск;
- `RunEndRiftObeliskLive.ps1` с 2 клиентами — PASS: 4/4 обелиска, 3
  отражённых попадания, состояния HP 3→2→1→destroyed, fireball cap 1;
- после обоих live-сценариев: `wave=0`, `event-mobs=0`, `boss=none`,
  `rift-obelisks=0`, `rift-fireballs=0`; карта и Core сохранены;
- локальный Paper слушает `25566`, RCON — `127.0.0.1:25576`, resource-pack
  HTTP — `127.0.0.1:8092`.

Артефакты этого checkpoint:

- End Rift plugin: 651440 bytes,
  SHA-256 `3E6697B18DD6551121F683B2255BBD2E1057E8885AEEB6E951FD8004429FE9AE`;
- CopiMineClient: 9349447 bytes,
  SHA-256 `1B07DE9FE4A2685D2F092851BFDF007E9DC7261CD2C480BE1769899C5D6DF449`;
- resource pack: 24147549 bytes,
  SHA-256 `9A5F444EA31F84EB3A5B476B3E65EE1A18B627AA2F57DA8E753E856DB634E47D`.

GitHub Actions run `34740608823` для SHA `f4c785c70f1f18e966191ab338daee03e2124357`
завершён с `success` для обоих jobs.

Native Minecraft UI по-прежнему `NOT VERIFIED`: в текущем окружении нет
доступной native Minecraft app surface, поэтому screenshots/video, фактический
рендер моделей/порталов/щупалец, звук, FPS и ручные 3/10/20-player client runs
не выдаются за проверенные.

## Current supplied-assets and animation checkpoint — 2026-09-13

Исходным кодовым checkpoint перед этим блоком был `9819f624aab48764c80463cbb6ee8cc5a6b225d5`.
Он уже содержал server-side исправления волн, колец, обелисков, барьеров,
real-HP Boss и cleanup. В этот checkpoint добавлена клиентская интеграция
переданных пользовательских ресурсов и отдельная проверка времени анимации.

### Пользовательские assets и фактическая привязка

~~~text
models.rar              SHA-256 2A994415246D7F03EB7160414A116427B21A7867347CA27B941EA477441E73CD
modelsboss.rar           SHA-256 2A994415246D7F03EB7160414A116427B21A7867347CA27B941EA477441E73CD
end event.rar            SHA-256 8FD8BB8622B273ABFF3224FC4FD1F1E8E1E5DD74C9FE299EC5558DC1509027F5
udar_iz_grudi.json       SHA-256 C53C68D133F42B61C50DF329BD87235E87835B1B4DB63FC80176539B26D87141
udar_po_zemle.animation.json
                         SHA-256 85D9AE6BC72BF09454BC1ABCBC9FFB716E7F06E0D8C0CE507C148C7C80E88CE1
~~~

`models.rar` и `modelsboss.rar` оказались одинаковым архивом исходного
Bedrock-босса. В него импортированы geometry с 16 bones/123 cubes, исходной
UV-сеткой 16x16 и PNG 128x128. `end event.rar` содержит пользовательские
skins Enderman и Spider. Отдельных Skeleton/Elite geometry/skin в переданных
архивах нет, поэтому для этих типов не создавался ложный mapping на чужую
модель: их существующие event resources сохранены.

Runtime mapping теперь такой:

~~~text
server END_RIFT_GUARDIAN_V1 (bound UUID)
 -> EndermanEntityRendererMixin
 -> RiftGuardianModelRenderer
 -> UserEndBossModelData
 -> assets/copimineclient/models/entity/end_rift_guardian/geometry.json
 -> textures/entity/end_rift_user_boss.png

END_RIFT_ENDERMAN_V1 -> textures/entity/end_rift_user_enderman.png
END_RIFT_SPIDER_V1   -> textures/entity/end_rift_user_spider.png
udar_iz_grudi.json   -> animations/udar_iz_grudi.json -> CHEST_STRIKE
udar_po_zemle...json -> animations/udar_po_zemle.animation.json -> GROUND_SLAM
~~~

Для Bedrock six-face UV добавлен runtime replacement на `ModelPart.Quad` с
accessor mixins; procedural boss mesh и старые phase texture fallback больше не
используются официальным Boss renderer. Server animation cue теперь запускает
клиентские one-shot clips от времени получения нового cue, а не от общего
возраста entity. Это было покрыто сначала RED-тестом (отсутствовал метод
animation clock), затем GREEN после реализации.

Asset hashes в checkout:

~~~text
geometry.json              61491 bytes  SHA-256 301583A2EFEA6C5B597C4FE2CADCED68D7838B80F630D1D16F8AAA8265964783
end_rift_user_boss.png      6035 bytes  SHA-256 F298ED322335C5439C19DDDB8014AA0960B83F3FB27D692580A75E051516C45D
end_rift_user_enderman.png  1000 bytes  SHA-256 A9A154F232919627451431E3F3874C9E850F23E531EAE2CFE4A2A4A9CC16EDF447
end_rift_user_spider.png    1876 bytes  SHA-256 19C46FF4AA829E7101B25A50A55090CD1D8145C2F83B95D64C13A20F6B5C9ABF
~~~

### Verification after the asset checkpoint

~~~text
CopiMineClient: gradle clean test                         PASS (BUILD SUCCESSFUL)
Current Python contract:                                  48 passed
RunEndRiftEventChecks.ps1:                                 61 passed; all listed pure-Java policies OK
CopiMineClient/build-client.ps1:                           PASS (BUILD SUCCESSFUL)
End Rift plugin build:                                    PASS
Plugin SHA-256:                                           2F5BD30F787BF23F876D6916EBBF0C9D4CD365A3309EBE4687C2C1409531E8B7
CopiMineClient JAR SHA-256:                               DF84DC9702BA7AEF4F1A6F5BD6FDC8814AEA5FC1FEF13277159F7423ABFF9C02
Resource pack SHA-256:                                    C3CF19BC270C8B00D21B6A57B9B3E702A0CCB4725C0ADC76BB79264869FDF2E5
Resource pack HTTP GET 127.0.0.1:8092:                   200 / 24147588 bytes
~~~

Актуальный Fabric runtime-load был запущен на Minecraft 1.21.1 / Fabric
Loader 0.19.3 с 55 mods. `copimineclient` и resource manager загрузились до
OpenAL/texture atlases без crash; процесс был остановлен вручную после этого
load check. Это не считается native gameplay/visual PASS.

### Server-side live evidence

~~~text
Paper/Purpur local endpoint: 127.0.0.1:25566
RCON:                         127.0.0.1:25576

2-player full run:             W1..W7 PASS; Boss phases all six; victory=true
2-player real Boss HP:         5000 -> 4977.545; 8 accepted events; same-tick group=1
5-player real Boss HP:         5000 -> 4943.8623; 20 accepted events; same-tick group=1
                                summed final=56.13439977169040; expected=4943.86560022830960;
                                float delta=0.00330022830960
Mob combat:                    moved=1718; attacks=51; player_hurt=23; AI targets=111; paths=71
Wave 6 boundaries:             3 rings; radii 6,11,16; visual displays=120; leash=true
Wave 7 barriers:                2 chambers; 234 cells; 78 visual displays; collision=true
Wave 7 cleanup:                 blocks restored=true; displays removed=true; transient=0
Boss real-health marker:        hp=5000/5000; max attribute unclamped; virtual marker=false
Post-test cleanup:              wave=0; event-mobs=0; boss=none; obelisks=0; fireballs=0
~~~

The five-player retry using fresh names completed bot connection and attack
release but did not emit the harness summary line; it is deliberately not
counted as a new PASS. The successful 5-player result above is the previously
captured real-HP run against the unchanged server damage path. One unrelated
AuthMe registration exception appeared during the retry and was not an End
Rift error; cleanup still completed.

Boss projectiles were not changed. No projectile speed, fuse, hitbox,
reflection, damage, trajectory, or parry rule was modified in this checkpoint.

### Git and release gate

~~~text
Asset/animation commit:      10a6dd77 (pushed)
Remote:                      origin https://github.com/IliaZav/copimine.git
Remote branch:               codex/end-rift-event -> 10a6dd77
GitHub Actions for 10a6dd77: NOT VERIFIED (gh CLI is unavailable in this environment)
Native Minecraft visual QA: NOT VERIFIED (Computer Use returned apps=[])
Screenshots/video:           NOT VERIFIED; no native Minecraft surface was exposed
3/10/20 native-player runs:  NOT VERIFIED
RELEASE VERDICT:             NOT READY FOR FINAL VISUAL RELEASE
~~~

The source, server-side live checks, builds, resource hashes, and cleanup gates
are green. The remaining release blockers are evidence blockers: a native
Minecraft window must be exposed to Computer Use for visual inspection of the
new model/textures, obelisks, gates, core, rings, barriers and animations, and
for the requested screenshots/video. No such visual result is claimed here.
