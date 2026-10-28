# CopiMine: переход на Minecraft 26.3

Документ описывает миграционный кандидат Minecraft 26.3, его воспроизводимую сборку, проверки и критерии приёмки. Игровые волны и redesign поведения мобов остаются вне этого этапа.

## Текущий статус

`tools/minecraft-26.3/profile.lock.json` и `server-plugins.lock.json` — источник точных версий и контрольных сумм. До подтверждения настоящего запуска Paper и Fabric клиента, входа в игру, проверки resource pack и снимков экрана статус остаётся `migration-candidate`, а `nativeVerified` — `false`. Успешные unit-тесты, сборка, CI или запуск одного только сервера этот статус не повышают.

Локальный тестовый сервер изолирован от сети: Paper привязан только к `127.0.0.1`, использует отдельную PostgreSQL БД и для тестов работает с `online-mode=false`. Это позволяет подключить лицензированный или offline-клиент с того же компьютера; такая конфигурация не делает сервер публичным и не должна открываться наружу напрямую. Публичный вход требует отдельно проверенной границы proxy/auth и не входит в эту локальную настройку.

## Зафиксированная сборочная база

| Компонент | Закреплённая версия или источник |
| --- | --- |
| Minecraft | 26.3, Mojang manifest и client JAR по SHA-1 в `profile.lock.json` |
| Paper | build 205 stable; server JAR 55 242 379 bytes with SHA-256 pinned in `profile.lock.json`; Paper API 26.3.build.205-stable is pinned there by exact URL, size, and SHA-256 |
| Java сборки | Temurin 25; Grim compatibility patch собирается Java 21 |
| Gradle | 9.8.1, distribution SHA-256 закреплён в lock |
| Fabric | Loader 0.19.5, Loom 1.19.1, Fabric API 0.164.0+26.3 (вложен в CopiMineClient) |
| CopiMineClient | `CopiMineClient-0.1.3+26.3.jar`, 15 025 264 байт; SHA-512 `67665f71488f9a563a9cc3a5c33fc7fd0ff80a11ea9753147f21b03031d7fcc93fb48830bd505d3d5d73e067587f0f05a87b384e386a78665bddbf45d117f7c4`, собран на Temurin 25.0.4.1+1 / Gradle 9.8.1 |
| Gradle зависимости | SHA-256 manifest и описание происхождения в `GRADLE_DEPENDENCY_VERIFICATION.md` |
| Серверные плагины | Lock с URL, версией, размером, SHA-256 и SHA-512 в `server-plugins.lock.json` |

Версии `candidate` означают, что артефакт подлинно закреплён и подготовлен, но ещё не прошёл полный реальный Minecraft acceptance. Они не являются гарантией совместимости конкретного плагина с работающим сервером.

## Файлы и ответственность

- `profile.lock.json` фиксирует Paper, client, Fabric и resource pack.
- `client-mods.lock.json` фиксирует внешние моды для профиля клиента.
- `server-plugins.lock.json` фиксирует полный набор модулей и неизменённых upstream-плагинов.
- `java-plugin-compile-dependencies.lock.json` фиксирует compile-only зависимости общих Java плагинов.
- `PrepareMigrationCandidates.ps1` строит и сверяет миграционные артефакты.
- `install_migration_server_plugins.py` безопасно stage/install-проверяет серверные плагины и inventory.
- `InstallMigrationProfile.ps1` устанавливает профиль и отдельно проверяет его установленное состояние через `-ValidateOnly`.
- `StartMigrationTestServer.ps1` запускает только принадлежащий миграции runtime и изолированную PostgreSQL конфигурацию.
- `RunJavaPluginCi.ps1` строит custom Java-плагины и запускает их компиляционные и policy-регрессии.
- `NATIVE_ACCEPTANCE_SIGNING.md` описывает сбор реального evidence, подпись и проверку доверительной границы.
- `MIGRATION_PROGRESS.md` — append-only журнал фактических тестов, review, CI и блокеров с датами.
- `MIGRATION_RUNBOOK.md` — пошаговая сборка, установка в локальный клиент, проверки артефактов и границы acceptance.
- `CI_ACCEPTANCE.md` — доказательства exact-SHA локальных и удалённых проверок, review и оставшиеся migration gates.
- `DATABASES.md` — карта изолированной PostgreSQL БД и миграционной схемы.

`unchangedBaseline` сохраняет исходные версии и хэши для аудита и отката. В runtime попадают только кандидаты с `candidate.runtimeEligible: true`; внешние файлы должны включать `26.3` в `gameVersions` (или иметь явное `approvedMinecraftVersion: "26.3"`), а локальные сборки — указывать `apiVersion: "26.3"` либо такое же явное одобрение. Текущий набор содержит 29 плагинов: 19 модулей и 10 допущенных кандидатов из baseline. `AuthMe` и другие записи, которые заменяются модулем, остаются под управлением `modules`. `FarmControl 1.3.0` и `SeeMore 1.0.2` сохранены как исторические данные, но не допускаются в кандидатный runtime, поскольку их release metadata не объявляет Minecraft 26.3.

## Клиентские моды

Lock фиксирует 13 client mod artifacts: Fabric API `0.164.0+26.3` вложен в отдельный CopiMineClient JAR, а остальные 12 закреплённых JAR устанавливаются отдельно и остаются необязательными. При обычной установке отдельный Fabric API не добавляется в `mods`; `DependenciesOnly` может установить его отдельно для подготовки профиля до CopiMineClient. Этот режим теперь отклоняется, если CopiMineClient уже установлен, чтобы не пометить готовый профиль неполным. Версии, source release IDs, URL и хэши закреплены в `client-mods.lock.json`; версия страницы релиза может отличаться от `fabric.mod.json`.

| Мод | Версия Minecraft 26.3 |
| --- | --- |
| Fabric API | 0.164.0+26.3 |
| AppleSkin | 3.0.10+mc26.3 |
| BetterF3 | 20.0.0 |
| Cloth Config | 26.3.159+fabric |
| Emotecraft | 3.5.0-a.build.171 |
| FPS Reducer | 26.3-2.18 |
| Iris | 1.11.7+mc26.3 |
| JourneyMap | 26.3-6.0.10+fabric |
| Armor Durability HUD | 8.1.1.6 |
| Player Animation Library | 1.2.8+mc.26.3 |
| Sodium | 0.9.2+mc26.3 |
| Simple Voice Chat | 2.6.27+26.3 |
| CustomSkinLoader | 15.1 |

Sodium `0.9.3-beta.1` уже опубликован для 26.3, но текущий Iris `1.11.7` объявляет обязательную точную зависимость от Sodium `0.9.2`. Поэтому lock держит последний совместимый release; обновление Sodium возможно после публикации Iris, который принимает новую версию.

## Подготовка кандидата

Запускайте из чистого migration checkout с Python 3.13.16, Java 25 и Java 21. Скрипт сверяет закреплённые версии Java и хеши исходных и собранных файлов; не заменяйте результаты локальными JAR неизвестного происхождения.

```powershell
py -3.13 -m pytest -q -rs tests/test_minecraft_26_3_paper.py tests/test_minecraft_migration_profile.py tests/test_minecraft_migration_server_plugins.py
py -3.13 .\scripts\minecraft\install_migration_server_plugins.py --stage-only
```

Для сборки нужны Temurin Java 25 и Java 21. В чистом checkout эти каталоги пока не существуют. Сначала скачайте и проверьте закреплённые архивы Temurin; helper проверяет SHA-256 и точный размер до распаковки. Скрипт ниже рассчитан на пустые целевые каталоги. Если точные версии Temurin уже установлены в другом месте, пропустите установку и используйте переменные окружения, описанные ниже. Для воспроизводимой установки с проверенными байтами используйте закреплённые архивы.

Если Java уже установлена в другом месте, её корневой каталог должен содержать `bin\java.exe`, а версия должна быть `25.0.4.1` или `21.0.12.1` соответственно. Одного совпадения `java --version` достаточно для выбора версии, но не для проверки байтов; для полностью закреплённого toolchain проверьте архивы и используйте установку ниже.

```powershell
. .\scripts\minecraft\DownloadPinnedArtifact.ps1
$ToolchainRoot = Join-Path $PWD 'build/minecraft-26.3/toolchain'
New-Item -ItemType Directory -Path $ToolchainRoot -Force | Out-Null

function Install-CopiminePinnedJdk {
  param(
    [int]$Major,
    [string]$Version,
    [string]$Url,
    [string]$ArchiveName,
    [string]$ArchiveHash,
    [long]$ArchiveSize
  )
  $Destination = Join-Path $ToolchainRoot ("jdk-$Version+1")
  if (Test-Path -LiteralPath $Destination) { throw "JDK destination already exists: $Destination" }
  $Archive = Join-Path $ToolchainRoot $ArchiveName
  $ExtractRoot = Join-Path $ToolchainRoot ('.jdk-extract-' + [guid]::NewGuid().ToString('N'))
  try {
    Save-PinnedArtifact -Uri $Url -Destination $Archive -Algorithm SHA256 `
      -ExpectedHash $ArchiveHash -ExpectedSize $ArchiveSize -MaximumBytes $ArchiveSize
    New-Item -ItemType Directory -Path $ExtractRoot -ErrorAction Stop | Out-Null
    Expand-Archive -LiteralPath $Archive -DestinationPath $ExtractRoot -ErrorAction Stop
    $JavaFiles = @(Get-ChildItem -LiteralPath $ExtractRoot -Filter java.exe -File -Recurse |
      Where-Object { $_.Directory.Name -eq 'bin' })
    if ($JavaFiles.Count -ne 1) { throw "Expected one Temurin $Major java.exe; found $($JavaFiles.Count)." }
    $Java = $JavaFiles[0].FullName
    $VersionOutput = (& $Java --version | Out-String)
    if ($LASTEXITCODE -ne 0 -or $VersionOutput -notmatch ('(?m)^openjdk ' + [regex]::Escape($Version) + '(?:\s|$)')) {
      throw "Temurin $Major did not report pinned version ${Version}: $VersionOutput"
    }
    $JdkHome = Split-Path -Parent (Split-Path -Parent $Java)
    Move-Item -LiteralPath $JdkHome -Destination $Destination -ErrorAction Stop
  } finally {
    if (Test-Path -LiteralPath $ExtractRoot) {
      Remove-Item -LiteralPath $ExtractRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
  }
}

Install-CopiminePinnedJdk -Major 21 -Version '21.0.12.1' `
  -Url 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip' `
  -ArchiveName 'temurin-21.0.12.1.zip' `
  -ArchiveHash 'f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e' `
  -ArchiveSize 205073461
Install-CopiminePinnedJdk -Major 25 -Version '25.0.4.1' `
  -Url 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip' `
  -ArchiveName 'temurin-25.0.4.1.zip' `
  -ArchiveHash '00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283' `
  -ArchiveSize 141167264
```

Архивы и версии совпадают с Windows GitLab migration job; GitHub Actions использует точный Temurin 25.0.4.1 для сборки закреплённого клиентского JAR. Версия Java 25 должна совпадать с профилем, поскольку CI сравнивает байтовый SHA-512 пересобранного JAR с lock. После установки проверьте вывод `java --version` из каталогов `jdk-21.0.12.1+1` и `jdk-25.0.4.1+1`.

По умолчанию полная сборка использует каталоги из pinned toolchain. Для тех же закреплённых версий, установленных в другом месте, задайте необязательные `COPIMINE_JAVA25_HOME` и `COPIMINE_JAVA21_HOME` как абсолютные пути к корням JDK.

```powershell
if ($env:COPIMINE_JAVA25_HOME) {
  $BuildJava25Home = (Resolve-Path $env:COPIMINE_JAVA25_HOME).Path
} else {
  $BuildJava25Home = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-25.0.4.1+1').Path
}
if ($env:COPIMINE_JAVA21_HOME) {
  $GrimPatchJava21Home = (Resolve-Path $env:COPIMINE_JAVA21_HOME).Path
} else {
  $GrimPatchJava21Home = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-21.0.12.1+1').Path
}
$BuildJava25Version = (& (Join-Path $BuildJava25Home 'bin/java.exe') --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $BuildJava25Version -notmatch '(?m)^openjdk 25\.0\.4\.1(?:\s|$)') {
  throw "Minecraft 26.3 build requires pinned Temurin 25.0.4.1: $BuildJava25Version"
}
$GrimPatchJava21Version = (& (Join-Path $GrimPatchJava21Home 'bin/java.exe') --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $GrimPatchJava21Version -notmatch '(?m)^openjdk 21\.0\.12\.1(?:\s|$)') {
  throw "The Grim compatibility patch requires pinned Temurin 21.0.12.1: $GrimPatchJava21Version"
}
$Python313 = (py -3.13 -c 'import sys; print(sys.executable)').Trim()
.\scripts\minecraft\PrepareMigrationCandidates.ps1 `
  -BuildJavaHome $BuildJava25Home `
  -GrimPatchJavaHome $GrimPatchJava21Home `
  -PythonExe $Python313
```

Профиль установленного Minecraft клиента можно проверить без записи:

```powershell
.\scripts\minecraft\InstallMigrationProfile.ps1 `
  -ProfileDirectory 'D:\.minecraft\versions\CopiMine' `
  -ValidateOnly
```

`-ValidateOnly` не чинит профиль автоматически. После любой установки повторите проверку SHA-512 каждого внешнего JAR и CopiMineClient по lock.

Проверка `-ValidateOnly` выполняется без записи: сверяет 12 отдельно установленных внешних JAR по размеру, SHA-512 и `fabric.mod.json`, CopiMineClient (внутри которого находится Fabric API), SHA-256 resource pack, его включение в `options.txt` и receipt. Для неё должны быть доступны локальные source-build артефакты CopiMineClient и pack; этот режим не скачивает моды и не исправляет файлы. Чтобы проверить только JSON определения профиля и закреплённые источники внешних модов без собранных клиентских артефактов, используйте `-ValidateDefinitionOnly`.

Lock клиента хранит Fabric mod id, версию релиза источника и точную версию из `fabric.mod.json` каждого внешнего JAR. Подготовка кандидата и установщик сравнивают ID и внутреннюю версию метаданных с lock. При обычной установке одна старая копия закреплённого мода под другим именем побайтно копируется в `migration-backups/client-mods/<timestamp-id>/`; содержимое исходного JAR включается в журнал отката и удаляется из `mods` только перед атомарной установкой закреплённого файла. При ошибке транзакция восстанавливает исходный JAR. `-ValidateOnly` сообщает о конфликте без изменений; несколько конфликтных JAR с одним mod id и файлы-ссылки блокируют установку.

## Тестовые слои

Запускайте слои отдельно, чтобы видеть, что именно доказано:

```powershell
# Python contract suite на CI-совместимом Python
py -3.13 -m pytest -q -rs tests

# Сборка и тесты client-порта на закреплённом Gradle/Java 25
# Запускать через PrepareMigrationCandidates.ps1 либо migration CI workflow.

# Общий Java plugin gate на закреплённом Java 21
if ($env:COPIMINE_JAVA21_HOME) {
  $GrimPatchJava21Home = (Resolve-Path $env:COPIMINE_JAVA21_HOME).Path
} else {
  $GrimPatchJava21Home = (Resolve-Path '.\build\minecraft-26.3\toolchain\jdk-21.0.12.1+1').Path
}
$Python313 = (py -3.13 -c 'import sys; print(sys.executable)').Trim()
.\scripts\minecraft\RunJavaPluginCi.ps1 `
  -JavaHome $GrimPatchJava21Home `
  -WorkspaceRoot $PWD.Path `
  -TemporaryDirectory $env:TEMP `
  -PythonExe $Python313

# Локальная схема и миграция на выделенном loopback PostgreSQL
py -3.13 -m pytest -q -rs tests/test_minecraft_26_3_custom_plugin_database_contracts.py
```

GitHub Actions — основной и обязательный acceptance pipeline для точного source SHA; все его jobs, включая PostgreSQL integration test с выделенным временным PostgreSQL service, должны завершиться успешно, а `skipped` не считается PASS. GitLab хранит зеркало того же SHA; его CI остаётся дополнительной диагностикой и не блокирует приёмку.

GitHub Actions проверяет `--require-evidence-for-accepted-locks` на любой ветке. Для обычного кандидата с `runtime-candidates` и `nativeVerified=false` подпись не требуется; если в lock-файлах появляется принятое состояние, GitHub gate требует подписанное evidence. Если native acceptance файлы добавлены, GitHub сверяет подпись, доверенный ключ и исходный commit SHA. GitLab pipeline может выполнять те же проверки как зеркало, но его identity gate, очередь или статус не заменяют и не блокируют GitHub acceptance.

## Перенос старой схемы кандидатов

ElectionCore сначала переносит значения старых колонок uuid и name в канонические player_uuid и player_name. После копирования он удаляет ограничения PRIMARY KEY/UNIQUE и самостоятельные уникальные индексы, зависящие от uuid или name, включая индексы-выражения вроде `UNIQUE(lower(name))`, затем снимает NOT NULL с этих двух compatibility-колонок. Зависимости индексов проверяются через PostgreSQL catalog `pg_depend`, а не только по `indkey`, поскольку expression indexes имеют нулевой номер атрибута в этом массиве. Новые записи AdminPlus содержат только канонические поля, поэтому сохранение старых ограничений иначе могло бы блокировать кандидата в следующем голосовании.

До создания уникального индекса (election_id, player_uuid) пустые legacy-идентификаторы помечаются как неактивные, а повторные строки одного игрока и выборов сохраняются в candidate_migration_duplicate_archive. Их голоса и администраторские корректировки суммируются в сохраняемой строке. Если внешний ключ зависит от старого ключа uuid/name, PostgreSQL не разрешит удалить его без каскада; вся транзакция откатится, чтобы не повредить зависимые данные. Такой случай требует отдельной миграции этой зависимости перед повторным запуском.

Проверка схемы использует изолированный PostgreSQL copimine_migration_test: тест создаёт старую таблицу с PRIMARY KEY(uuid), UNIQUE(name) и NOT NULL, запускает реальный DDL из ElectionCore, проверяет перенос старой строки и выполняет новую каноническую вставку того же игрока в другое голосование. Отдельный контракт проверяет, что периодическая AR-сверка не запускает два прохода одновременно и освобождает guard после каждого завершения.

## Изменения в обработке продаж AR

`transferToAccount` одной банковской транзакцией уменьшает баланс покупателя и увеличивает баланс целевого счёта. Запись покупки теперь сохраняет ID этого перевода как уже завершённый доход. Фоновый обработчик не создаёт вторую банковскую операцию: старые `PENDING` строки с `bank_tx_id` переводятся в `CREDITED`, а строки без подтверждённого ID уходят на ручную проверку.

Если БД вернула неоднозначный результат записи покупки, plugin не возвращает деньги немедленно. Покупка и её доставляемый экземпляр сверяются повторно; перед решением о возврате сверщик ждёт минимальный интервал, берёт такой же PostgreSQL advisory lock, как операция записи, и проверяет факт commit в ограниченном lock timeout. Немедленный возврат остаётся только для двух явных quota-ошибок, которые возникают до commit.

Экономический bridge сохраняет время перевода в миллисекундах, а общий plugin clock возвращает секунды; политика восстановления явно приводит текущее время к миллисекундам и откладывает автоматический возврат при будущем timestamp или переполнении.

## Условия полной приёмки

AR-сверка запускается scheduler каждые 200 тиков. Атомарный guard разрешает только один активный проход и всегда освобождается в finally: это защищает cursor, pending recovery state и счётчик повторного сканирования, если запрос к БД длится дольше интервала запуска.

Миграцию можно считать принятой только после всех пунктов:

1. Точная сборка из чистого checkout, без неучтённых generated или локальных файлов.
2. Полные автоматические тесты и review изменённого кода, без незакрытых actionable findings.
3. Все обязательные jobs GitHub Actions успешно завершились для проверяемого source SHA; GitLab ref должен зеркалировать этот SHA, а статус GitLab CI не является условием приёмки.
4. Два штатных запуска полного locked plugin inventory на Paper 26.3 и фактическое состояние всех записей плагинов.
5. Запуск профиля CopiMine на Minecraft 26.3, подключение к локальному серверу, проверка ресурс-пака, клиентского bridge и нужных визуальных эффектов.
6. Свежие серверные/клиентские логи, inventory и скриншоты F2, сверенные с lock и связанные с проверяемым SHA.
7. Подписанная native acceptance запись, где `sourceCommit` указывает на проверенный source commit. Любое изменение кода после него требует нового запуска, evidence и подписи.

Полная процедура, включая AuthMe, полный inventory и подпись, описана в `NATIVE_ACCEPTANCE_SIGNING.md`. Решения по AuthMe и обязательности CopiMineClient собраны в `AUTH_AND_CLIENT_MOD_ENFORCEMENT.md`. Не включайте private key или runtime DB credentials в Git. Перед фиксацией acceptance просмотрите raw-логи и screenshots вне Git; затем сохраните в `artifacts/minecraft-26.3/native-acceptance/` все проверенные файлы, на которые ссылается `acceptance.json`, вместе с `acceptance.json` и `acceptance.sig`. Это закрытый набор evidence для CI: отсутствующие или лишние файлы отклоняются. Native evidence и любой публичный login/proxy security setup не имитируются unit-тестами.
