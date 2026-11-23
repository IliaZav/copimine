# Giant center-core chest beam — aim-lock geometry, re-pin, deploy

Дата: 2026-11-23
Ветка: `codex/end-rift-live-playtest-repair`
Коммит: `3b4a7d69` (parent `68ed465b`)
Артефакт: `CopiMineEndEvent-26.3.jar`, SHA-256 `211c0859c51b3e5495efbcad75bbb489fe469c15640d8499a0dbd1fa07a4d868`, 2 637 385 Б
Предыдущий артефакт: `11e6864d…`, 2 636 135 Б
Прод: `copimine-game.service`, `/opt/copimine-game/minecraft/server/plugins/CopiMineEndEvent.jar`

## Дефект

Спецификация §7.1.3 и §9H.02 требуют, чтобы читаемый телеграф и наносящий
урон луч были ОДНОЙ геометрией; §9A.04 и §7.2 прямо запрещают «фальшивую линию
прицела» и молчаливый переприцел после видимого замка. Прежний код в
`telegraphBossSpell` рисовал предупреждение в мейн-таргет босса, а
`startChestBeam` заново выбирал 1–3 полосы из живых позиций в момент выстрела.
Игрок видел одно направление, урон приходил по другому.

## Исправление (`3b4a7d69`)

- Новые поля `bossChestBeamLockedAims`, `bossChestBeamLockedChest`,
  `bossChestBeamLockedGeneration`.
- `lockChestBeamAims(boss)` замораживает неизменяемый снимок полос и точку
  груди ОДИН раз при принятии каста, штампует generation и пишет
  `BOSS_CHEST_BEAM_AIM_LOCK`.
- `telegraphBossSpell`: если `stageBossForChestBeam` не смог поставить босса на
  Core — отказ и возврат резерва (`chest-beam-core-stage-refused`); при успехе
  снимок фиксируется, тело разворачивается к основной полосе, предупреждение
  рисует `renderChestBeamLaneRails` по замороженному снимку.
- `startChestBeam` переиспользует замороженный снимок; `selectLaneIndices`
  остаётся только защитным фолбэком, поэтому выход игрока в момент заряда
  сохраняет объявленную полосу, а шагнувший из неё — реально безопасен.
- `finishChestBeam` / `abortChestBeam` / `resetEventSafely` очищают снимок.
- Тестовые спутники: расширен runtime-probe `test_boss_telegraph_runtime.py`,
  добавлены guards `test_lanes_and_origin_are_frozen_at_aim_lock` и
  `test_fire_reuses_the_snapshot_and_never_retargets`, обновлён
  `test_wave4_reset_checkpoint.py`.
- Re-pin: jar `211c0859…` в `tools/minecraft-26.3/server-plugins.lock.json`
  (с `11e6864d…`, добавленным в начало `replaceSha256`) и
  `docs/end-rift/final-26.3/OWN_PLUGIN_INVENTORY.json`; сдвинуты индексные
  ассерты `tests/test_minecraft_migration_server_plugins.py`.

## Доказательства

| Уровень | Доказательство |
|---|---|
| CODE-IMPLEMENTED | `CopiMineEndEvent.java`: `lockChestBeamAims`, заморозка снимка, `renderChestBeamLaneRails` из снимка в телеграфе и в charge/fire |
| AUTOMATED-TESTED | `test_boss_telegraph_runtime.py`, `test_end_rift_chest_beam_core_stage_guard.py` (6), `ChestBeamPolicyTest` (323 проверки); полный `tests` — `3800 passed, 11 skipped, 2 subtests passed`; гейт `RunEndRiftEventChecks.ps1` — PASS |
| LIVE-PAPER-VERIFIED | прод: jar `211c0859…` на диске; лог `[CopiMineEndEvent] Enabling … 0.1.0`, `Done (44.442s)`; rcon `/cmend debug invariants` → `END_RIFT_INVARIANTS failures=0 last=none countersNonNegative=true`; `/cmend debug status` → `submitted=58 accepted=58 written=58 dropped=0 failures=0` |
| VISUAL-VERIFIED | **NOT VERIFIED** — нет F2/видео владельца в подписанном клиенте 26.3 |
| MULTIPLAYER-RUNTIME | **BLOCKED** — нет реального боя 2/5/10/11/20 игроков |

## Остаточные ограничения

- Попадание/уклонение `chest_beam` в живом бою не проверено: 0 игроков онлайн,
  невозможно честно воспроизвести урон/додж.
- README/владелец: визуальная оценка «AAA» по-прежнему только через F2 в
  подписанном клиенте; автоматический тест не может её заменить.
