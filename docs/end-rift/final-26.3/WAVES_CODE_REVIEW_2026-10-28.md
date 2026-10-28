# Волны 1–7: проверка кода 28 октября 2026

База проверки: `6a3a918ca5050d155a447011a638f1a3cd3ac20b`. Работа ведётся отдельно от запущенной задачи DeepSeek, в ветке `codex/waves-spec-code-20261028`. Последнее уточнение пользователя ограничивает этот проход кодом и автоматическими тестами: Computer Use и развёртывание исключены.

Нормативные источники прочитаны полностью из `D:\Downloads`:

- `COPIMINE_END_RIFT_WAVES_CANONICAL.md`, revision `2026-10-27.1`, SHA-256 `6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf`.
- `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md`, revision `2026-10-27.1`, SHA-256 `0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50`.

## Исправленные воспроизведённые ошибки

1. Rift Step, Void Snare и Echo Pulse накладывали последствия даже при отменённом уроне. Общий обработчик теперь ожидает настоящий результат `Player.damage`, проверяет отмену, событие/поколение и допустимость цели перед последствиями. Полностью заблокированный удар не накладывает эффекты; урон, поглощённый absorption, остаётся законным попаданием. Echo Pulse учитывает укрытие.
2. Warden применял отбрасывание после отменённого удара и считал попаданием совпадение только в горизонтальной плоскости. Теперь проверяются перекрытие реальных высот тел и линия видимости; запись попадания и импульс возникают после принятого урона.
3. Луч Архимага накладывал слепоту после отмены урона или смены поколения внутри обработчика попадания. Последствие использует тот же проверенный результат урона.
4. Wave 2 игнорировала отказ синхронного сохранения завершённой метки. Теперь официальный проход переходит в `RECOVERY_REQUIRED` и не назначает следующую метку. Граница 44.999/45.000 секунды и обычный успешный путь сохранены.

Регрессии исполняют извлечённые реальные production-методы в управляемом Java-окружении. До исправления восемь сценариев общих атак, пять сценариев Warden, два сценария луча и сценарий отказа записи воспроизвели ошибки. Новые проверки включены в `RunEndRiftEventChecks.ps1`.

## Проверенные контракты волн

| Волна | Основные инварианты | Исполняемые проверки |
|---|---|---|
| 1 | Три отдельные доставки настоящего заряда; владелец, перенос/потеря, восстановление и аренда Slowness; личная презентация | `RiftCarrierPolicyTest`, `RiftCarrierCustodyPolicyTest`, `RiftCarrierRecoverySnapshotTest`, `test_wave1_carrier_slowness_lease.py`, `test_wave_carrier_marker_adapter.py` |
| 2 | Полные 45-секундные периоды, ротация, сохранение врагов, ограниченный резерв, материализация фантома и fencing старых callback | `WaveTwoHuntPolicyTest`, `WaveTwoStagePolicyTest`, `test_wave_two_phantom_callback_fence.py`, `test_wave_two_phantom_retire_order.py`, новая регрессия checkpoint |
| 3 | Три последовательных портала, непрерывное время захвата, атомарные слои и Giant второго портала | `test_wave3_capture_clock.py`, `test_wave3_active_sequence.py`, `test_wave3_portal_spawn_runtime_contract.py`, `test_wave3_portal_elite_runtime_contract.py`, `test_end_rift_portal_layer_runtime.py` |
| 4 | Масштабирование обелисков, три HP, принадлежность отражённого снаряда, геометрия предупреждения и очистка | `ObeliskIntegrityPolicyTest`, `RiftObeliskScalingPolicyTest`, `RiftObeliskTimingPolicyTest`, `test_wave4_special_attack_fairness.py`, `test_wave4_warning_geometry.py`, `test_wave4_obelisk_collapse.py` |
| 5 | Три цикла 40/50/60 секунд; безопасная вместимость, запрет опасной фазы без зон, journal восстановления и независимое владение AI | `test_wave5_black_fog_adapter.py`, `test_wave5_ai_freeze_ownership.py`, `test_wave_coordinator_fog_lifecycle.py` |
| 6 | Пять уникальных магов, 5/10/15 стражей, Q/W/E/R, одна большая атака, физические снаряды в кристалл, посадка и два залпа наковален | `Wave6RitualRuntimeAdapterTest`, `Wave6CrystalLifecycleTest`, `test_wave6_channel_admission_runtime.py`, `test_wave6_crystal_projectile_adapter.py`, `test_wave6_anchor_cancel_fall_protection.py`, `test_wave6_anvil_single_target_columns.py` |
| 7 | Четыре именованных испытания, изоляция, личный Echo, возврат с исходным сроком, принятые попадания Warden/Архимага и один handoff | `test_wave7_admission_runtime_adapter.py`, `test_wave7_named_runtime.py`, `test_wave7_echo_official_combat_runtime.py`, `test_wave7_return_admission_adapter.py`, `test_wave7_return_preboss_adapter.py`, новые регрессии урона |
| Общие переходы | Последовательность волн, 10 секунд рун, здоровье/ремонт, один 40-секундный переход и идемпотентность | `EndRiftEncounterCoordinatorTest`, `TransitionRuneControllerTest`, `PreBossTransitionControllerTest`, `PostWaveRecoveryServiceTest` |

Это доказательства кода и автоматических контрактов. Они не подтверждают рендеринг, звук или прохождение живыми игроками. `nativeVerified=false` сохранён.

## Сборка и проверка

- Новые production-регрессии: **27 passed**.
- Полный набор чистых Java-политик из выпускного gate: **182 PASS**, включая общий координатор семи волн.
- Закреплённые JDK 25.0.4.1 и Gradle 9.8.1: сборка итоговых исходников и повторная принудительная сборка успешны; JAR одинаковый.
- EndEvent: **1966351** байт, SHA-256 `c9ca37ebab60b629be5e04e945524ecb5c3b8ec0bb61362e3deb10dbd94a14b5`.
- Lock, проверка упаковки и inventory синхронизированы; предыдущий кандидат сохранён в обоих списках допустимых обновлений.
- Расширенный Python-набор и итоговый GitHub CI должны оцениваться по фактическим логам конечного коммита. Результат предыдущего CI не является доказательством для новых исходников.

## Границы этого прохода

База, приватные настройки и пользовательские миры не менялись. Существующие данные сохранены. После уточнения пользователя Computer Use не вызывается, задача DeepSeek не управляется, новая версия на серверы не устанавливается. Изолированная ветка не перезаписывает checkout или ветку работающей задачи DeepSeek.
