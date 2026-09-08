# End Rift Event V2 — MASTER PROMPT ДЛЯ CODEX

Этот файл предназначен для Codex/исполнителя, который будет реализовывать End Rift Event V2.

**Не начинать реализацию до явного одобрения владельцем `2026-09-07-end-rift-event-v2-design.md`.** После одобрения считать этот файл обязательным execution-contract. Он не заменяет design spec, а объясняет **как именно** довести её до рабочего состояния без скрытых компромиссов.

Основной gameplay/design контракт:

`docs/superpowers/specs/2026-09-07-end-rift-event-v2-design.md`

Artist contract для щупалец:

`docs/art/end-rift-tentacle-artist-brief-ru.txt`

---

# 1. ТВОЯ РОЛЬ

Ты не делаешь «пример реализации», «черновой прототип» или «примерный рефакторинг».

Твоя задача — довести ветку `codex/end-rift-event` до состояния, в котором End Rift Event V2:

- полностью соответствует design spec;
- компилируется;
- проходит новые и старые релевантные автоматические тесты;
- проходит локальный Paper integration run;
- не ломает WorldCore, Artifacts и CopiMineClient;
- не оставляет stale entities/tasks/blocks после wipe/restart;
- реально проходим 2 игроками;
- масштабируется до 20 без mob/VFX spam;
- не имеет известных «иногда урон не проходит» обходных фиксов без диагностики;
- использует настоящий HP boss;
- имеет 6 волн, 20-секундный безопасный pre-boss cooldown и Boss V2;
- не выводит технические подсказки заклинаний в бою.

Не объявляй задачу готовой, пока Definition of Done из design spec не доказан.

---

# 2. ПРАВИЛА РАБОТЫ, КОТОРЫЕ НЕЛЬЗЯ НАРУШАТЬ

1. **Сначала читай код, потом меняй.** Не создавай новый класс с названием, которое уже существует под другой ролью.
2. **TDD обязательно.** Для каждой новой policy/state/reward/scaling механики сначала напиши failing test, запусти его, убедись, что падает по ожидаемой причине, затем реализуй.
3. **Не лечи симптом.** При баге сначала найди root cause. Не ставь случайные задержки, `noDamageTicks=0`, `try/catch(Throwable){}` и не отключай проверку, чтобы тест стал зелёным.
4. **Не переписывай всё сразу.** Мигрируй вертикальными срезами. После каждого этапа build+tests должны быть зелёными.
5. **Не раздувай `CopiMineEndEvent.java`.** Новая логика должна быть разбита на контроллеры/policies/services.
6. **Не делай client authoritative.** Клиент только рисует/анимирует. Сервер решает HP, hit, target, phase, reward, grab success, teleport, death.
7. **Не создавай official artifacts вручную.** Только `EventArtifactRewardService`.
8. **Не открывай End напрямую.** Только `WorldAccessService`.
9. **Не трогай launcher/site/production deployment.**
10. **Не оставляй TODO/TBD/temporary hack в боевом path.**
11. **Не удаляй старую защиту/recovery без эквивалентной V2-замены.**
12. **Не скрывай ошибки тестов.** Если найден unrelated pre-existing failure — отдели и задокументируй, но не меняй unrelated subsystem без необходимости.
13. **Не объявляй visual bug исправленным по unit test.** Нужен реальный client screenshot/check для портала, fog, beam, tentacle alignment.
14. **Не делай unbounded loops.** Любой entity/projectile/VFX registry должен иметь hard cap и cleanup.
15. **Не делай ActionBar костылём вместо визуала.** Если механика должна читаться VFX, исправляй VFX.

---

# 3. ПЕРВЫЙ ШАГ: ОБЯЗАТЕЛЬНЫЙ REPOSITORY AUDIT ПЕРЕД ЛЮБЫМ EDIT

Перед первой строкой реализации прочитай минимум:

Server:

- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- `copimine-end-event/src/me/copimine/endevent/EventConfig.java`
- `copimine-end-event/src/me/copimine/endevent/EventSnapshot.java`
- `copimine-end-event/src/me/copimine/endevent/EventStateStore.java`
- `copimine-end-event/src/me/copimine/endevent/EventTaskRegistry.java`
- `copimine-end-event/src/me/copimine/endevent/HazardMutationJournal.java`
- `copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java`
- `copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WaveObjectivePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WaveScalingPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/WaveMechanicsPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/PortalCapturePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/EndRiftAiPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/BossDamagePolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/BossVirtualHealthPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/BossHealthScalingPolicy.java`
- `copimine-end-event/src/me/copimine/endevent/domain/BossStage.java`
- `copimine-end-event/config.yml`
- `copimine-end-event/plugin.yml`
- `copimine-end-event/build-plugin.ps1`

Client:

- `CopiMineClient/src/main/java/me/copimine/client/BridgePayload.java`
- `CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java`
- `CopiMineClient/src/main/java/me/copimine/client/EndEventPacket.java`
- `CopiMineClient/src/main/java/me/copimine/client/EndEventClientState.java`
- `CopiMineClient/src/main/java/me/copimine/client/EndRiftBossBarHud.java`
- `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModel.java`
- `CopiMineClient/src/main/java/me/copimine/client/RiftGuardianModelRenderer.java`
- все End Rift renderer mixins;
- `CopiMineClient/build.gradle`
- End Rift textures/models under `src/main/resources/assets/copimineclient`.

Artifacts/WorldCore:

- `copimine-artifacts/items.yml`
- `copimine-artifacts/src/me/copimine/artifacts/api/EventArtifactRewardService.java`
- `copimine-artifacts/src/me/copimine/artifacts/api/EventArtifactRewardRequest.java`
- `copimine-artifacts/src/me/copimine/artifacts/api/RewardIssueResult.java`
- `copimine-world-core/src/me/copimine/worldcore/api/WorldAccessService.java`

Tests:

- все `tests/*End*`, `tests/*Boss*`, `tests/*Wave*`, reward/persistence tests;
- CopiMineClient tests under `CopiMineClient/src/test`.

После чтения составь для себя карту:

```text
KEEP
MIGRATE
DELETE AFTER MIGRATION
NEW
```

Не коммить эту карту обязательно, но используй её, чтобы не оставить два параллельных official flows.

---

# 4. BASELINE ДО ИЗМЕНЕНИЙ

Перед V2-изменениями:

1. Зафиксируй текущий commit SHA.
2. Убедись, что работа идёт на `codex/end-rift-event`.
3. Проверь рабочее дерево.
4. Запусти существующие server pure tests, которые можно запустить локально.
5. Собери `CopiMineEndEvent` существующим `build-plugin.ps1`.
6. Собери `CopiMineClient` существующим `build-client.ps1`/Gradle build.
7. Сохрани baseline failures отдельно.

Если baseline уже красный, не начинай маскировать это новыми suppressions. Определи, относится ли failure к End Rift V2.

---

# 5. ОБЩИЙ ЦИКЛ ДЛЯ КАЖДОГО ЭТАПА

Для каждого этапа ниже использовать один и тот же цикл:

```text
A. Прочитать текущий flow и все callers.
B. Сформулировать 3–10 конкретных invariants.
C. Написать failing pure/contract tests.
D. Запустить только эти tests и увидеть ожидаемый RED.
E. Реализовать минимально достаточный production code.
F. Запустить targeted tests → GREEN.
G. Запустить весь End Rift relevant test suite.
H. Собрать server plugin.
I. Если менялся client — собрать client + его tests.
J. Если этап имеет runtime semantics — выполнить локальный Paper scenario.
K. Проверить cleanup/restart path.
L. Только после этого переходить дальше.
```

Если на пункте F–K найден баг — этап не закрыт.

---

# 6. ЭТАП 1 — V2 STATE MACHINE И ATTEMPT LIFECYCLE

## 6.1. Сначала tests

Добавь tests, которые доказывают:

- W1→INTERMISSION_1→W2;
- W5→INTERMISSION_5→W6;
- W6→PRE_BOSS_COOLDOWN;
- PRE_BOSS_COOLDOWN→BOSS_CINEMATIC→BOSS_ACTIVE;
- illegal W5→BOSS_ACTIVE напрямую отклоняется;
- legacy FINAL_* не достижимы новым official flow;
- `performAttemptWipe` возвращает V2 attempt в READY/START state и не стирает charged resources;
- повторный wipe idempotent;
- all-dead detection срабатывает только когда living active roster == 0;
- один умер, второй жив → no wipe;
- restart из W4/W5/W6/BOSS → safe READY_FOR_PLAYERS recovery, не resume mid-mechanic.

## 6.2. Потом production code

Изменить:

- `EventPhase`;
- `EndEventStateMachine`;
- snapshot/schema/store;
- lifecycle fields в orchestration.

Создать `AttemptLifecycleController` или эквивалент.

Он должен единолично владеть:

- attempt generation;
- living/online state official roster;
- death/respawn/reconnect semantics;
- wipe orchestration.

Не оставлять wipe logic разбросанной по Wave4/Wave5/Boss handlers.

## 6.3. Проверка

Смоделировать wipe во время:

- safe-zone emerald blocks;
- W5 ice prison;
- W6 chamber;
- boss tentacle hold.

После cleanup не должно остаться event-owned entity/task/block mutation.

---

# 7. ЭТАП 2 — ПЕРЕХОДНЫЕ РУНЫ

## Tests first

Нужны pure tests для:

- уникального соответствия player→rune;
- двое не могут занять одну руну;
- все заняты → начинается 5s hold;
- один сошёл на 4.9s → progress reset;
- reconnect/death/world change сбрасывает occupation;
- completion только один раз;
- после W4 rune positions находятся на outer perimeter;
- после W6 transition rune вообще нет.

## Production

Создать `TransitionRuneController` + pure `TransitionRunePolicy`.

Не использовать ActionBar countdown.

Renderer states:

```text
EMPTY
OCCUPIED
CHARGING
COMPLETE
```

Сервер state авторитетен, клиент/Display только отражает.

---

# 8. ЭТАП 3 — COMBAT TRACE И БАГ ПРОПАДАЮЩЕГО УРОНА

**Это сделать ДО большого rewrite boss damage.**

Создать `CombatTraceService`, выключенный по умолчанию и включаемый admin debug command.

Логировать для tracked End Rift hit:

```text
tick
wall-clock
attacker UUID/type
victim UUID/type/event-kind
damage cause
raw damage
final damage
cancelled early
cancelled final
invulnerable
noDamageTicks
maxNoDamageTicks
lastDamage
hpBefore
hpNextTick
phase
boss shield/cast state
main-thread timing/MSPT signal
```

Провести локальный reproduction:

- player A бьёт mob A;
- player B одновременно mob B;
- затем оба boss.

Не применять никакой фикс до классификации причины.

### Если причина vanilla hurt resistance

Менять только event-owned entity `maximumNoDamageTicks` и тестировать значение 3–4 ticks. Не ставить 0 глобально.

### Если причина event cancellation

Найти конкретный listener/state, который cancel. Исправить predicate, добавить regression test.

### Если причина virtual boss HP

Фикс окончательно закрывается на Boss real HP stage, но доказательство сохранить.

### Если причина main thread stall

Найти loop/entity spam, профилировать и исправить его. Не маскировать задержкой.

---

# 9. ЭТАП 4 — ОБЩИЙ SCALING / DAMAGE / TARGET PRESSURE

Создать чистые policies:

- `EventCombatScalingPolicy`;
- `EventMobDamagePolicy`;
- `TargetPressurePolicy`.

## Tests

Проверить минимум players = 2,3,5,10,20.

### Damage

Для non-boss event damage:

```text
adjusted = max(minDamage, oldEffectiveDamage - 4.0)
```

Проверить, что корректировка применяется ровно один раз.

### Pressure

Для 10 игроков не позволять одной цели получить весь pack, если есть 9 других living targets.

Старый `WaveScalingPolicy` не использовать как official V2 mob multiplication engine.

---

# 10. ЭТАП 5 — WAVE 1

Создать отдельный `Wave1CarrierController`.

## Tests

- ровно один Carrier;
- Carrier death создаёт ровно один charge state;
- charge delivery у Core увеличивает progress один раз;
- timeout переносит charge в другого living mob, а не удаляет;
- 3 deliveries = complete;
- old callback generation не может добавить четвёртый charge;
- duo не превышает pressure cap.

## Runtime

Проверить beam lifecycle:

```text
spawn carrier
→ beam visible
→ carrier death
→ beam switches to charge
→ timeout jump
→ old beam removed
```

После completion никаких carrier VFX/tasks.

---

# 11. ЭТАП 6 — WAVE 2

Создать `Wave2HuntController`.

## Tests

- один marked living target;
- при альтернативе target не повторяется сразу;
- death target → rotate immediately;
- mobs pressure cap соблюдён;
- 3 cycles или configured completion;
- old mark VFX removed on rotation.

Убрать технический timer ActionBar.

---

# 12. ЭТАП 7 — WAVE 3 И PORTAL BUG

Создать/переписать `Wave3PortalController`.

## Обязательные правила

- ровно 3 portals;
- sequential capture;
- reuse `PortalCapturePolicy` 5 sec, если tests подтверждают корректность;
- ровно 2 `PORTAL_PUSHER` на defending pack;
- bonus knockback только у этих двух.

## Tests

- portal count ==3 для 2/10/20;
- next portal locked до completion current;
- exactly 2 pushers;
- common defender has no bonus knockback;
- pusher role cleanup after despawn;
- portal capture no double complete.

## Visual bug procedure

Не считать исправленным, пока не проверены:

1. CMD/model JSON для 830007/830008/830009.
2. Texture paths.
3. ItemDisplay transform origin/scale.
4. Frame depth vs inner depth.
5. No z-fighting.
6. Screenshot real client.

Если portal всё ещё выглядит как чёрно-фиолетовый комок — не переходить к W4.

---

# 13. ЭТАП 8 — WORLD-SPACE VFX FOUNDATION

До Fog/Rings/Boss создать client/server базу, чтобы не писать particle hacks в каждой wave.

## Server

Создать typed bridge `EndRiftWorldVfxBridge`.

Packet/state должен иметь bounded fields:

```text
eventId
generation
instanceId
type
source anchor
target anchor
style
width
lifetime
state/version
```

## Client

Создать минимум:

- `EndRiftWorldVfxManager`;
- `EndRiftWorldVfxRenderer`;
- `RiftBeamRenderer`;
- `GroundDecalRenderer`;
- `RiftTrailRenderer`.

Bump protocol version, если protocol contract реально расширяется несовместимо.

## Tests

- malformed/oversized packet rejected;
- stale generation ignored;
- remove cleans instance;
- lifetime expiration;
- disconnect/world change clear;
- entity anchor missing → safe fade/remove, no crash;
- hard cap on active VFX.

## Beam acceptance

Beam должен быть continuous ribbon/core+halo, а не цепочкой точек.

---

# 14. ЭТАП 9 — WAVE 4 BLACK FOG

Создать `Wave4FogController`, `SafeZoneController`, `SafeZonePolicy`.

## Pure tests

Zone series:

```text
players=2: 1/1/1, sizes 3/2/1
players=10: 5/4/3, sizes 3/2/1
players=20: 5/4/3, sizes 3/2/1
```

Capacity 2 UUID per zone.

HP clamp:

```text
20 -> 4
5 -> 4
4 -> 4
3 -> 3
```

Debuffs:

- Blindness 15s;
- Slowness III 4s;
- Wither I 4s.

Safe occupant gets configured healing, no clamp.

## Block safety

Перед первым emerald/barrier mutation написать tests journal restore semantics.

Проверить:

- emerald restores original exact BlockData;
- barrier restores exact original;
- wipe mid-fog restores;
- disable mid-fog restores/reconciles at next startup;
- third player cannot join locked full zone.

## Fog performance

Запустить `/cmend debug perf` или equivalent metrics.

Запрещено:

```text
for every block in arena
  for every player
    every tick
      spawn many particles
```

Fog lasts exactly 3 sec after 4 sec safe-zone warning.

Mobs frozen during all 3 sec.

---

# 15. ЭТАП 10 — CORE RESTORATION

Создать `CoreRestorationService`.

## Tests

- full heal living participants;
- pending heal for player still on death screen;
- durability repair = ceil(maxDurability*0.40);
- no over-repair below damage 0;
- armor/offhand/main inventory covered;
- no recursive repair inside container item;
- only event-owned negatives removed.

No technical text with 40%.

---

# 16. ЭТАП 11 — WAVE 5 RINGS

Разделить минимум:

- `RingGeometryPolicy`;
- `RingBoundaryController`;
- `RingDisplayController`;
- `Wave5RingController`;
- `CorePrisonerController`.

## Ring rendering

- 3 rings;
- 20–24 BlockDisplay each;
- <=72 recommended total;
- animation through interpolated transformations, not teleport every tick;
- passage server-stable while ring keeps rotating visually.

## Boundary tests

- mob cannot path/teleport/projectile through locked ring;
- player cannot bypass via pearl if passage locked;
- opened passage accepted;
- no invisible Barrier wall unless intentionally journaled and visually explained.

## Ring2 tests

- first guard death opens ~10 sec window;
- second within window → complete;
- timeout → first restores 30–40% HP;
- no full double reset;
- idempotent death callback.

## Prisoner tests

- first rune occupant becomes prisoner exactly once;
- movement/use blocked;
- event targets exclude prisoner;
- drain every 50 sec;
- drain 3 HP;
- floor 1 HP;
- at floor no new strength stack;
- successful drain gives exactly one stack;
- elite death releases/restores/heals prisoner;
- wipe releases/restores prisoner;
- restart reconciliation cannot leave ice.

## Duo acceptance

Сделать отдельный integration scenario:

```text
2 enter W5
1 becomes prisoner
1 fighter remains
only 1 Guard full-pressure at once
Guard1 → Guard2 → Guard3 → Elite is survivable
```

Если оставшийся игрок одновременно получает полноценные атаки от 3 guards+elite — реализация не принята.

---

# 17. ЭТАП 12 — WAVE 6 CHAMBERS

Разделить:

- `ChamberLayoutPolicy`;
- `ChamberMembershipService`;
- `ChamberTargetPolicy`;
- `Wave6ChamberController`;
- 4 отдельные room AI policies/controllers.

## Layout tests

```text
2 players -> 2 rooms
3 -> 3
4+ -> 4
```

Distribution diff <=1.

10 → 3/3/2/2.

## Isolation tests — обязательны

Пока boundary locked:

- foreign player not candidate;
- foreign target event cancelled;
- projectile crossing blocked/removed;
- AoE only room-local;
- Enderman teleport outside cancelled;
- path watchdog clamps.

После passage и физического crossing chamberId меняется и новая room может target.

## Room scaling

Scaling input = room player count, не total roster.

Solo room никогда не запускает pair-only mechanic `RIFT_CHAINS`.

## AI tests

Каждая room должна иметь deterministic condition→intent rules. Запрещено заменить design на random spell rotation.

### Chamber A

Проверить material/phased role switch и solo serial pressure.

### Chamber B

Проверить arrow budgets и short per-player hit cooldown.

### Chamber C

Проверить role target distribution и serial solo combo.

### Chamber D

Проверить Chains only >=2 players и Singularity/Repulsion/PhaseCrush predicates.

---

# 18. ЭТАП 13 — 20-SECOND PRE-BOSS SAFE WINDOW

Tests:

- W6 complete → PRE_BOSS_COOLDOWN exactly configured 20s;
- no hostile event mob/hazard can deal damage;
- no transition rune;
- rewards issue during window idempotently;
- deadline → BOSS_CINEMATIC once;
- restart in cooldown follows documented recovery path.

No numeric countdown UI.

---

# 19. ЭТАП 14 — BOSS REAL HP MIGRATION

Это критический этап.

## Сначала tests/source contracts

Добавить tests/assertions:

- official boss path не вызывает `BossVirtualHealthPolicy.applyHit`;
- accepted hit не cancel unless explicit shield/cinematic;
- BossBar ratio from real entity HP;
- HP curve 2/3/4/5/8/10/15/20;
- stage thresholds as percentage of real max HP, если V2 policy так определена;
- shielded hit cancelled + visible shield cue request.

## Production

1. Установить `GENERIC_MAX_HEALTH` target.
2. Установить real current health.
3. Удалить authoritative virtual subtraction.
4. Normal damage проходит Paper pipeline.
5. BossBar обновляется из entity health.
6. Перед spawn проверить server max-health ceiling.
7. Если ceiling < required max — fail start с admin log, не clamp/virtual fallback.

## Damage bug regression

Повторить Combat Trace 2-player test на boss.

Accepted hit обязан уменьшать real HP.

---

# 20. ЭТАП 15 — BOSS ANIMATION ADAPTER

Не продолжать строить attack animation только синусами внутри `RiftGuardianModel`.

Сначала проверить реальные friend geometry/animation assets и их format.

Составить mapping:

```text
IDLE -> Idle
MOVE -> Running2
MELEE -> Swipe2
HURT -> Hurt2
DEATH -> Dying2
CHEST_BEAM -> udar_iz_grudi
GROUND_SLAM -> udar_po_zemle2
```

Создать `BossAnimationTimelinePolicy`.

Hard markers:

- chest release around 2.0s;
- slam impact around 5.5s.

Server damage/projectile release должен запускаться по server timeline marker, а client animation синхронно отражать этот marker.

Не доверять client elapsed time как authority.

Hurt animation throttled visually, damage never throttled.

---

# 21. ЭТАП 16 — BOSS AI DIRECTOR

Создать `BossV2Director` + pure `BossAttackPolicy`.

Input snapshot минимум:

```text
phase
boss-target distance
cluster metrics
stationary duration
recent targets
previous heavy attack
cooldowns
shield state
active mechanic pressure
```

Output = intent/attack enum.

## Tests

- close → melee preferred;
- retreating/distant → chest beam;
- clustered → slam where legal;
- bad position → flank;
- same heavy not repeated;
- recent target rotation;
- 2-player lower multi-target pressure;
- rage shortens cooldown but not telegraph;
- shield phase disables normal roaming.

Не выбирать heavy просто random из list.

---

# 22. ЭТАП 17 — TENTACLES

До этого этапа проверить наличие artist deliverables из `docs/art/end-rift-tentacle-artist-brief-ru.txt`.

Если модели ещё нет:

- реализовать server state/policies/tests и renderer interface;
- не рисовать временную уродливую «финальную» модель и не заявлять визуальную часть готовой;
- явно оставить artist asset blocker в verification report.

## Server classes

- `TentacleScalingPolicy`;
- `TentacleAttackPolicy`;
- `TentacleController`.

## Permanent count tests

```text
2 -> 2
3-4 -> 3
5-7 -> 4
8-10 -> 5
11-15 -> 6
16-20 -> 8
```

Total guardian HP budget разделяется на count, а не full HP × count.

## Temporary caps

```text
2-4 -> 2
5-8 -> 3
9-12 -> 4
13-16 -> 5
17-20 -> 6
```

Lifetime <=10s.

## Grab server authority

Server decides hit. Client grab_socket only visual.

State sequence:

```text
TELEGRAPH
CONTACT decision
HOLD anchor
THROW_RELEASE marker
server velocity
RECOVERY
```

Test disconnect/death/world change during HOLD: anchor/player state cleaned.

## Shield window

Все permanent dead simultaneously:

- respawn timers frozen;
- temporary spawn frozen;
- boss shield off;
- exact ~15 sec vulnerable window;
- if boss lives, controlled guardian cycle returns.

---

# 23. ЭТАП 18 — НАГРАДЫ И DURABLE LEDGER

Персональный ledger, не только `waveRewardsIssued={N}`.

Создать/расширить `WaveRewardService`/`RewardLedger`.

## Tests

- partial delivery: player A delivered, B failed → retry only B;
- restart preserves delivered state;
- W1–W6 idempotent;
- late helper does not receive official reward if not roster;
- boss reward per official participant;
- duplicate death/victory callback cannot double-issue.

## Night Cloak

Roll outcome persist **before** issue.

Tests:

```text
roll=true -> retry remains true
roll=false -> retry never rerolls
restart -> same outcome
```

Only if true call Artifacts API.

If `night_cloak` artifact definition does not exist and its gameplay behavior is not specified elsewhere, create only the catalog/reward boundary necessary for official item if design owner has approved item semantics; do not invent unrelated powers.

---

# 24. ЭТАП 19 — RIFT CORE SHARD

Сначала обновить item definition presentation/cooldowns согласно design.

Все abilities требуют authentic owner-bound artifact check.

## Active teleport tests

- right-click ground starts 3s channel;
- move > allowed radius cancels;
- damage cancels;
- world/logout cancels;
- success → safe Core point;
- success cooldown 600s durable;
- failed channel only short anti-spam;
- disabled during active End Rift attempt.

## Passive tests

- pearl self-damage zero;
- Enderman damage x0.5, aggro unchanged;
- End world authentic owner → Strength II + Speed II;
- leaving End/removing shard cleans only owned effect;
- Abyss Anchor 1800s durable cooldown;
- void rescue → Core safe point + 2 HP;
- disabled during active End Rift attempt;
- fake same-name item does nothing.

Определи и протестируй priority с Totem. Не оставляй оба handlers одновременно выдавать спасение.

---

# 25. ЭТАП 20 — УДАЛЕНИЕ ТЕХНИЧЕСКОГО ТЕКСТА

Сделать repository/source search по строкам и call sites.

Запрещено в official combat surface:

```text
готовит:
Метка Разлома:
Порталы Разлома:
Пульс Ядра:
Шторм Разлома:
Осколок:
Ритуал:
Приговор Разлома
```

Проверить не только literal strings, но и generic methods, которые получают spell displayName.

Boss HUD:

- только `СТРАЖ РАЗЛОМА`;
- graphical bar;
- no numeric HP;
- no phase;
- no cast state.

Добавить source/renderer tests, чтобы это не вернулось.

---

# 26. ЭТАП 21 — УДАЛЕНИЕ LEGACY OFFICIAL PATHS

Только после того, как V2 replacements зелёные:

Удалить/отключить official flow для:

- old W1 safe sectors;
- Tower Defense official W4;
- Rift Storm official W5;
- old final-drain/final-wave boss sequence;
- virtual boss health;
- W3 blanket knockback;
- obsolete tasks/fields/config keys.

Перед удалением найди все references.

После удаления search должен доказать, что старый official path не reachable.

Не оставлять одновременно два scheduler, которые оба считают себя Wave4 controller.

---

# 27. КАК ФИКСИТЬ ЛЮБОЙ НОВЫЙ БАГ

Всегда этот порядок:

1. Reproduce.
2. Упростить reproduction.
3. Собрать evidence/log/state snapshot.
4. Найти first point where actual diverges from expected.
5. Добавить failing regression test, если возможно.
6. Исправить root cause.
7. Targeted test.
8. Full relevant suite.
9. Runtime reproduction повторно.
10. Проверить, что cleanup/restart не сломан.

Запрещены ответы типа:

```text
"похоже race, добавил 5 тиков задержки"
"возможно Minecraft баг, поставил invulnerable false"
"на всякий случай catch Exception"
"чтобы точно работало, отключил проверку generation"
```

Generation checks, idempotency и ownership нельзя убирать ради прохождения happy path.

---

# 28. ОБЯЗАТЕЛЬНЫЕ FAILURE-INJECTION TESTS

Искусственно проверить:

- state save fail во время transition;
- plugin disable во время fog;
- plugin disable во время ice prison;
- disconnect prisoner;
- disconnect held-by-tentacle player;
- projectile owner disappears;
- client does not support VFX protocol;
- Artifacts returns pending;
- Artifacts duplicate key returns already-issued;
- WorldCore unlock retry;
- stale callback from previous generation fires;
- entity already removed before cleanup;
- block was externally changed before journal restore.

В каждом случае не должно быть dupe/unlock twice/stale block/task leak.

---

# 29. PERFORMANCE VERIFICATION

Добавить/расширить `/cmend debug perf`.

Снимать:

```text
owned entities by kind
active event mobs
projectiles
ring displays
active VFX instances
permanent tentacles
temporary tentacles
fog emissions/sec
scheduler tasks
MSPT/TPS
```

Stress scenarios:

- 10 players W4 fog;
- 10 players W6 Skeleton ultimate;
- 20 players boss final with 8 permanent + up to 6 temporary tentacles.

Если MSPT заметно растёт из-за конкретной VFX mechanic, оптимизировать before Done.

Не скрывать perf проблему снижением server view distance или отключением mechanic.

---

# 30. MANUAL VISUAL ACCEPTANCE CHECKLIST

Unit tests не закрывают этот раздел.

На реальном клиенте проверить и сохранить screenshots/video evidence:

1. W1 Carrier beam continuous.
2. W2 mark readable без text timer.
3. W3 portal объёмный, не чёрно-фиолетовая клякса.
4. W4 3x3/2x2/1x1 emerald zones.
5. W4 Barrier действительно не пускает третьего.
6. Fog снаружи плотный, внутри safe-zone чистый.
7. Fog визуально примерно до 3 blocks height.
8. Core restoration green beams.
9. W5 rings плавно вращаются и оставляют fixed gap.
10. W5 prisoner ice читается, игрок не clip через блоки критично.
11. Guard→Elite beams continuous.
12. W6 walls/chambers не позволяют видеть/агрить всё сразу некорректно.
13. Boss model friend animations visibly match attacks.
14. Chest beam release совпадает примерно с 2.0s animation marker.
15. Ground slam impact совпадает примерно с 5.5s marker.
16. Tentacle grab реально охватывает torso, grab_socket не смещён.
17. Throw release визуально совпадает с реальным полётом игрока.
18. Boss HUD без цифр/phase/cast.
19. Нет старых technical ActionBars.
20. После wipe/restart нет визуального мусора.

Если один из пунктов явно плох — работа не завершена.

---

# 31. FULL GAMEPLAY ACCEPTANCE MATRIX

## 2 players

Обязательно полный run от стартовых рун до victory.

Особо проверить:

- W4 одна safe zone на двоих;
- W5 один prisoner + один fighter;
- final Ring3 solo pressure serial;
- W6 exactly 2 rooms;
- solo room pair-only ability excluded;
- boss permanent tentacles =2;
- full wipe при смерти обоих;
- boss kill и rewards обоим.

## 3 players

- W6 exactly 3 rooms;
- room with 1 player remains fair;
- pair mechanics only where >=2 local players.

## 10 players

- W4 5→4→3 zones;
- W6 3/3/2/2;
- boss permanent tentacles=5;
- target pressure distributed;
- no 50-mob spam.

## 20 players

- bounded mob caps;
- W6 room-local scaling;
- boss permanent=8;
- temporary<=6;
- stable cleanup/performance.

---

# 32. BUILD / VERIFICATION ORDER ПОСЛЕ КАЖДОГО MAJOR MILESTONE

Минимум:

1. Targeted Java tests.
2. All relevant root `tests` for End Rift/Boss/Wave/reward/persistence.
3. `copimine-end-event/build-plugin.ps1`.
4. `CopiMineClient/build-client.ps1` если client менялся.
5. Artifact-related contract tests если менялся shard/cloak.
6. Local Paper smoke/integration scenario.
7. Source search for banned legacy/combat strings.
8. Git diff self-review.

Не считать `javac succeeded` достаточной проверкой runtime behavior.

---

# 33. SELF-REVIEW ПЕРЕД КАЖДЫМ КОММИТОМ

Проверь diff как reviewer:

- нет ли ещё одного giant responsibility в `CopiMineEndEvent`;
- нет ли duplicated state;
- нет ли task, который не зарегистрирован;
- нет ли entity без event/generation ownership;
- нет ли block mutation вне journal;
- нет ли async Bukkit API call;
- нет ли unbounded collection;
- нет ли reward без idempotency key;
- нет ли client state, который влияет на gameplay authority;
- нет ли hardcoded player count, ломаюшего 2/20;
- нет ли случайного spell selection вместо intent policy;
- нет ли technical HUD text;
- нет ли accepted boss hit cancellation;
- нет ли `TODO`, `FIXME`, commented-out old implementation.

Если есть — исправить до commit.

---

# 34. КАК ДЕЛАТЬ КОММИТЫ

Коммиты должны быть небольшими по смыслу, например:

```text
refactor(end-rift): add V2 attempt lifecycle
feat(end-rift): add transition rune controller
test(end-rift): add combat trace regression coverage
feat(end-rift): implement rift carrier wave
feat(end-rift): rebuild portal wave and pusher roles
feat(client): add End Rift world VFX protocol
feat(end-rift): implement black fog wave
feat(end-rift): implement collapse rings and prisoner
feat(end-rift): add isolated chamber wave
refactor(end-rift): migrate boss to real health
feat(end-rift): add Boss V2 intent director
feat(end-rift): add guardian tentacles
feat(end-rift): make wave rewards per-player durable
feat(artifacts): update Rift Core Shard event artifact
chore(end-rift): remove legacy official mechanics
```

Не делай один гигантский commit «implement v2» на десятки тысяч строк, если среда позволяет нормальную историю.

---

# 35. ФИНАЛЬНЫЙ RELEASE-GATE

Перед фразой «готово» выполни финальный аудит.

Проверить автоматически или вручную каждый пункт:

```text
[ ] W1 работает
[ ] W2 работает
[ ] W3 работает
[ ] W3 exactly 2 pushers
[ ] W3 portal visual accepted
[ ] W4 работает
[ ] W4 fog 4s warning + 3s fog
[ ] W4 clamp/debuff/heal correct
[ ] restoration heals + repairs 40%
[ ] W5 Ring1 works
[ ] W5 Ring2 revive window works
[ ] W5 prisoner cannot die from drain
[ ] W5 duo survivor flow fair
[ ] W6 chamber count 2/3/4 correct
[ ] W6 room isolation proven
[ ] PRE_BOSS exactly 20s safe
[ ] Boss real HP
[ ] Damage bug regression passes
[ ] Boss phase AI intent based
[ ] Animation timing sync
[ ] permanent tentacle scaling correct
[ ] temporary caps/lifetime correct
[ ] 15s shield break window correct
[ ] all beams continuous client-side
[ ] technical combat text removed
[ ] W1-W6 reward ledger idempotent
[ ] shard to every official winner
[ ] cloak roll persisted and 30% independent
[ ] shard active/passives/authenticity correct
[ ] all-dead wipe works
[ ] restart reconciliation works
[ ] no stale emerald/barrier/ice
[ ] no stale entities/projectiles/VFX/tasks
[ ] server build green
[ ] client build green
[ ] relevant automated tests green
[ ] 2-player full run passed
[ ] 10-player scenario passed
[ ] 20-player stress acceptable
[ ] launcher/site/production untouched
```

Если какой-то пункт не проверен, пиши `NOT VERIFIED`, а не `DONE`.

---

# 36. ЧТО ДОЛЖНО БЫТЬ В ФИНАЛЬНОМ ОТЧЁТЕ CODEX

Финальный ответ владельцу должен содержать не рекламное описание, а доказательства:

1. Какие major subsystems созданы/изменены.
2. Какие legacy paths удалены.
3. Root cause бага пропадающего damage и конкретный fix.
4. Реальный boss HP path и max-health validation.
5. Test commands + результат.
6. Local Paper scenarios + результат.
7. Client visual checks + какие screenshots/video проверены.
8. Perf counters на 10/20-player scenarios.
9. Reward idempotency/restart evidence.
10. Список оставшихся ограничений. Если artist tentacle asset ещё не готов — сказать это прямо.
11. Exact commit SHAs последней реализации/verification.

Нельзя завершать отчёт словами «должно работать» или «по идее работает». Либо есть evidence, либо пункт помечается как непроверенный.

---

# 37. ГЛАВНОЕ ПРАВИЛО

Если реализация кажется проще, чем написано в design spec, это не повод выкинуть механику.

Если реализация оказывается сложнее, это не повод сделать невидимый shortcut.

В сложном месте:

```text
прочитать существующий flow
→ изолировать policy/state
→ написать regression test
→ реализовать server-authoritative решение
→ добавить bounded client visual
→ проверить cleanup
→ проверить restart
→ проверить duo
→ проверить 10/20 scaling
```

И только после этого двигаться дальше.