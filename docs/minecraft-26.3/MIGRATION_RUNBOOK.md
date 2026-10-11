# Runbook миграции на Minecraft 26.3

Эта инструкция относится только к migration checkpoint. Она не запускает и не принимает Waves 1–7 или Rift Guardian. Пока настоящий Fabric-клиент не подключился к настоящему Paper runtime и не сохранены логи и скриншоты, статус в lock должен оставаться `migration-candidate`, `nativeVerified=false`.

## Зафиксированные компоненты

- Minecraft Java 26.3; Mojang manifest и client JAR сверяются с SHA-1 из `tools/minecraft-26.3/profile.lock.json`.
- Paper 26.3 build 169 beta сверяется с размером и SHA-256 из того же lock.
- Сборка Paper-плагинов и клиента требует Java 25; независимый Grim compatibility patch собирается Java 21.
- Fabric Loader 0.19.5, Loom 1.17.21 и Fabric API 0.162.0+26.3.
- Fabric-профиль содержит 13 внешних модов и CopiMineClient 0.1.1+26.3. Каждый внешний JAR закреплён SHA-512, размером, URL и Fabric metadata в `client-mods.lock.json`.
- Мигрированный ресурс-пак имеет format 97.1 и закреплён SHA-256. Текстуры, копируемые из item atlas в block atlas, приводятся к нижней границе размера, кратной 16, Lanczos-фильтром. Это сохраняет mipmap-уровни; исходные item-текстуры не меняются.

## Подготовка воспроизводимой сборки

Из корня чистого migration checkout используйте закреплённые каталоги Java из локального toolchain. Пути ниже показаны для PowerShell; в чистом checkout сначала подготовьте toolchain по `README.md`.

```powershell
$BuildJava25 = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-25.0.4.1+1').Path
$GrimJava21 = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-21.0.12.1+1').Path

& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\PrepareMigrationCandidates.ps1 `
  -BuildJavaHome $BuildJava25 `
  -GrimPatchJavaHome $GrimJava21 `
  -PythonExe python
```

Скрипт сверяет источники и собранные файлы по lock, скачивает закреплённые upstream-моды, проверяет Fabric metadata, собирает custom Paper plugins и CopiMineClient, затем строит resource pack. Не подменяйте JAR вручную в `build/minecraft-26.3`.

## Установка в локальный Minecraft-профиль

Установщик рассчитан на копию Minecraft 26.3 с Fabric Loader 0.19.5 и Java 25. Для профиля пользователя выполните:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\InstallMigrationProfile.ps1 `
  -ProfileDirectory 'D:\.minecraft\versions\CopiMine'

& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\InstallMigrationProfile.ps1 `
  -ProfileDirectory 'D:\.minecraft\versions\CopiMine' `
  -ValidateOnly
```

`-ValidateOnly` выполняет read-only проверку фактически установленного профиля: сверяет 13 внешних JAR по размеру, SHA-512 и `fabric.mod.json`, CopiMineClient, resource pack, включение pack в `options.txt` и migration receipt. Режим не скачивает и не заменяет файлы; ему требуются проверенные локальные source-build JAR клиента и resource pack. `-ValidateDefinitionOnly` проверяет launcher JSON профиля и allowlist закреплённых URL/ID внешних модов, не требуя собранных клиентских артефактов и не меняя файлы.

Установщик включает мигрированный pack в `options.txt`, сохраняет прежние копии заменённых JAR в `migration-backups/client-mods`, атомарно ставит артефакты и пишет `copimine-migration-receipt.json`. Обычная локальная установка обратима через резервные копии; не удаляйте каталог `migration-backups`, пока версия не принята.

## Локальный Paper runtime

`StartMigrationTestServer.ps1` использует отдельный PostgreSQL `copimine_migration_test`, schema `copimine_migration_26_3_candidate`, порт 55434 и listener `127.0.0.1:25568`. Для удобства проверки разрешён `online-mode=false` только в этом loopback runtime; это допускает offline/cracked и licensed клиент с той же машины. Настройка не делает сервер доступным извне и не является публичной схемой аутентификации.

Paper runtime, успешно достигший строки `Done`, подтверждает запуск JAR, загрузку и включение плагинов, но не полную проверку их команд и игровых сценариев. FarmControl 1.3.0 и SeeMore 1.0.2 включились в наблюдавшемся запуске, однако их upstream metadata не заявляет поддержку 26.3; до проверки команд и поведения они остаются `requires-runtime-validation`.

## Проверки перед публикацией

Локальный набор фокусных миграционных контрактов:

```powershell
python -m pytest -q -rs `
  tests/test_minecraft_26_3_pack.py `
  tests/test_minecraft_migration_profile.py `
  tests/test_minecraft_migration_server_plugins.py `
  tests/test_ci_release_job_contract.py
```

Полный Java plugin gate:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\RunJavaPluginCi.ps1 `
  -JavaHome $GrimJava21 `
  -WorkspaceRoot $PWD.Path `
  -TemporaryDirectory $env:TEMP `
  -PythonExe python
```

Fabric-клиентские unit-тесты выполняются сборочной процедурой на закреплённых Gradle и Java 25. PostgreSQL-интеграция создаёт случайную схему в специально выделенной тестовой базе, проверяет DDL/переносы и удаляет только эту временную схему.

Для публикации кандидата GitHub Actions и GitLab CI проверяются отдельно на одном source commit SHA. Датированная candidate-ветка должна получить собственный pipeline; результаты основной ветки или локальный запуск не заменяют эти два результата. Открытая PR/MR остаётся обзорной и не сливается до проверки владельцем проекта.

## Критерий native acceptance

После сборки сервер и клиент запускаются из указанных lock-профилей. Игрок подключается к Paper, проверяются resource pack, моды, серверные плагины и заявленные migrations. В evidence для того же source SHA сохраняются серверный и клиентский startup logs, активный профиль и pack, скриншоты из Minecraft (F2) и подписанная запись, описанная в `NATIVE_ACCEPTANCE_SIGNING.md`.

Если desktop bridge недоступен, реальный вход или скриншот не подтверждён, `nativeVerified` нельзя переключать в `true`. Сборка, API metadata, unit-тесты и CI сами по себе этого критерия не закрывают.
