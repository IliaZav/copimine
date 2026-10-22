# Runbook миграции на Minecraft 26.3

Эта инструкция относится только к migration checkpoint. Она не запускает и не принимает Waves 1–7 или Rift Guardian. Пока настоящий Fabric-клиент не подключился к настоящему Paper runtime и не сохранены логи и скриншоты, статус в lock должен оставаться `migration-candidate`, `nativeVerified=false`.

## Зафиксированные компоненты

- Minecraft Java 26.3; Mojang manifest и client JAR сверяются с SHA-1 из `tools/minecraft-26.3/profile.lock.json`.
- Paper 26.3 build 205 stable сверяется с размером и SHA-256 из того же lock; `paper-api` 26.3.build.205-stable имеет отдельные точные URL, размер и SHA-256.
- Сборка Paper-плагинов и клиента требует Java 25; независимый Grim compatibility patch собирается Java 21.
- Fabric Loader 0.19.5, Loom 1.19.1 и Fabric API 0.164.0+26.3.
- Client mod lock содержит 13 артефактов, включая Fabric API, который вложен в CopiMineClient; отдельно в профиль устанавливаются 12 внешних JAR плюс CopiMineClient 0.1.3+26.3. Все артефакты закреплены SHA-512, размером, URL и Fabric metadata в `client-mods.lock.json`.
- Мигрированный ресурс-пак имеет format 97.1 и закреплён SHA-256. Текстуры, копируемые из item atlas в block atlas, приводятся к нижней границе размера, кратной 16, Lanczos-фильтром. Это сохраняет mipmap-уровни; исходные item-текстуры не меняются.

## Подготовка воспроизводимой сборки

Из корня чистого migration checkout используйте закреплённые каталоги Java из локального toolchain. Для чистого checkout сначала выполните проверенную по размеру и SHA-256 установку Temurin из раздела «Подготовка кандидата» в `README.md`; команды ниже используют полученные версии 21.0.12.1 и 25.0.4.1. Не пропускайте проверку Java, если повторно используете уже существующие каталоги toolchain.

```powershell
$BuildJava25 = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-25.0.4.1+1').Path
$GrimJava21 = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-21.0.12.1+1').Path
$Python313 = (py -3.13 -c 'import sys; print(sys.executable)').Trim()

& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\PrepareMigrationCandidates.ps1 `
  -BuildJavaHome $BuildJava25 `
  -GrimPatchJavaHome $GrimJava21 `
  -PythonExe $Python313
```

Скрипт сверяет источники и собранные файлы по lock, скачивает закреплённые upstream-моды, проверяет Fabric metadata, собирает custom Paper plugins и CopiMineClient, затем строит resource pack. Миграционный клиент собирается проектом `tools/minecraft-26.3/client`: он использует общие исходные ресурсы из `CopiMineClient/src/main/resources`, но формирует отдельный артефакт `0.1.3+26.3` с метаданными из migration lock. Корневой `CopiMineClient/build-client.ps1` остаётся сборкой прежнего проекта 1.21.1 и не создаёт JAR для Paper 26.3. Не подменяйте JAR вручную в `build/minecraft-26.3`.

Не выпускайте Minecraft 26.3 сервер через legacy `scripts/package_full_release.ps1` или его Windows install/update wrappers: они собирают клиент 1.21.1. Старый упаковщик завершится до создания release output, если обнаружит migration profile lock либо YAML-конфигурацию, требующую CopiMineClient `+26.3`. Для проверки используется `scripts/minecraft/inspect_legacy_release_client_bridge.py` с PyYAML SafeLoader: комментарии, block scalars, anchors/merge aliases и экранированные ключи интерпретируются по YAML-семантике; malformed YAML, дублированные ключи и неподдерживаемая структура закрывают упаковку с ошибкой. `PrepareMigrationCandidates.ps1` формирует и проверяет candidate артефакты 26.3, но ещё не создаёт production release bundle.

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

`-ValidateOnly` выполняет read-only проверку фактически установленного профиля: сверяет 12 отдельно установленных внешних JAR по размеру, SHA-512 и `fabric.mod.json`, CopiMineClient (с вложенным Fabric API), resource pack, включение pack в `options.txt` и migration receipt. Режим не скачивает и не заменяет файлы; ему требуются проверенные локальные source-build JAR клиента и resource pack. `-ValidateDefinitionOnly` проверяет launcher JSON профиля и allowlist закреплённых URL/ID внешних модов, не требуя собранных клиентских артефактов и не меняя файлы.

Установщик включает мигрированный pack в `options.txt`, сохраняет прежние копии заменённых JAR в `migration-backups/client-mods`, атомарно ставит артефакты и пишет `copimine-migration-receipt.json`. Обычная локальная установка обратима через резервные копии; не удаляйте каталог `migration-backups`, пока версия не принята.

## Локальный Paper runtime

`StartMigrationTestServer.ps1` использует отдельный PostgreSQL `copimine_migration_test` со schema `copimine_migration_26_3_candidate` на `127.0.0.1:55434`. Paper listener читается из `server.properties` и проверяется перед запуском: новый runtime создаётся с `server-port=25566`, а существующий `local-runtime/end-rift-server-26.3` сейчас настроен на `25567`. Не подставляйте фиксированный Minecraft port вместо значения файла. Для удобства проверки `online-mode=false` разрешён только в этом loopback runtime; это допускает offline/cracked и licensed клиент с той же машины. Настройка не делает сервер доступным извне и не является публичной схемой аутентификации.

Обычный запуск проверяет и переиспользует уже созданный кластер; при необходимости он может запустить только этот остановленный кластер после проверки его владельца и identity. `InitializeMigrationPostgres.ps1` предназначен только для явно новой пустой тестовой папки данных и откажет при обнаружении существующих или частичных данных. `-ValidateOnly` не запускает службы и не записывает в runtime или БД.

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
$GrimJava21 = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-21.0.12.1+1').Path
$Python313 = (py -3.13 -c 'import sys; print(sys.executable)').Trim()
& powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\minecraft\RunJavaPluginCi.ps1 `
  -JavaHome $GrimJava21 `
  -WorkspaceRoot $PWD.Path `
  -TemporaryDirectory $env:TEMP `
  -PythonExe $Python313
```

Fabric-клиентские unit-тесты выполняются сборочной процедурой на закреплённых Gradle и Java 25. PostgreSQL-интеграция создаёт случайную схему в специально выделенной тестовой базе, проверяет DDL/переносы и удаляет только эту временную схему.

Для публикации кандидата GitHub Actions должен пройти на точном source commit SHA; датированная candidate-ветка получает собственный GitHub pipeline. GitLab хранит зеркало того же SHA, а его CI служит дополнительной диагностикой и не блокирует checkpoint. Локальный запуск не заменяет GitHub результат. Открытая PR/MR остаётся обзорной и не сливается до проверки владельцем проекта.

## Критерий native acceptance

После сборки сервер и клиент запускаются из указанных lock-профилей. Игрок подключается к Paper, проверяются resource pack, моды, серверные плагины и заявленные migrations. В evidence для того же source SHA сохраняются серверный и клиентский startup logs, активный профиль и pack, скриншоты из Minecraft (F2) и подписанная запись, описанная в `NATIVE_ACCEPTANCE_SIGNING.md`.

Если desktop bridge недоступен, реальный вход или скриншот не подтверждён, `nativeVerified` нельзя переключать в `true`. Сборка, API metadata, unit-тесты и CI сами по себе этого критерия не закрывают.
