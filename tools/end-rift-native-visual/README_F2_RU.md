# Съёмка End Rift через Minecraft F2

`CaptureEndRiftF2.ps1` подготавливает локальную сцену End Rift и делает пять PNG штатной клавишей F2: общий вид, три элитных моба и готовые щупальца. Скрипт проверяет ветку, локальный сервер и RCON на `127.0.0.1`, хэши JAR, активный профиль Minecraft и подключённого игрока камеры. Для каждого исходного PNG он сохраняет SHA-256 в `manifest.json`.

Во время съёмки камера переключается от первого лица, HUD скрывается, мобам отключается ИИ, а общие имена убираются с кадра. Серверное время и временное ночное зрение восстанавливаются после съёмки, как и исходные перспектива и HUD игрока. Для этого в локальном `config.json` укажите фактические `InitialPerspective` (`first-person`, `third-person-back` или `third-person-front`) и `HudVisibleAtStart` (`true` или `false`). Скрипт использует полночь с ночным зрением, чтобы скелет не загорался при съёмке.

## Настройка

1. Скопируйте `config.example.json` в `config.json` и укажите пути к checkout, локальному серверу и профилю Minecraft. В `CameraPlayer` укажите имя уже подключённого игрока, которым будет управляться камера. Также укажите его текущую перспективу и состояние HUD.
2. Запустите изолированный тестовый сервер и Minecraft 1.21.1/Fabric с собранным `CopiMineClient`.
3. Подключите игрока камеры к `127.0.0.1:25566` и запустите из корня checkout:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\end-rift-native-visual\CaptureEndRiftF2.ps1
```

Можно указать отдельную новую папку внутри `artifacts`, чтобы сохранить ещё один набор:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\end-rift-native-visual\CaptureEndRiftF2.ps1 -OutputDirectory artifacts\end-rift-native-visual\review-2
```

Скрипт не принимает другие ветки, серверы, RCON-порты или профили клиента и не перезаписывает уже существующий кадр или манифест. Подготовка шоурума заменяет сцену только на указанном изолированном сервере. `manifest.json` оставляет визуальную оценку в статусе `PENDING`: для PASS нужно открыть и осмотреть оригинальные PNG, потому что наличие кадра само по себе не подтверждает правильность текстур и модели.
