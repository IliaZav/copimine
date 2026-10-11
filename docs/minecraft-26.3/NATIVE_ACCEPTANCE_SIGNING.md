# Подпись native acceptance evidence

## Доверительная граница

`acceptance.json` содержит заявления о запуске Minecraft и хеши сохранённых логов, inventory и скриншотов. Локальная проверка структуры и хешей сама по себе не подтверждает автора этих заявлений. Поэтому принятие требует отдельной подписи Ed25519 в `acceptance.sig`.

Подпись проверяется по точным байтам RFC 8785 canonical JSON с фиксированным контекстом `CopiMine Minecraft 26.3 native acceptance v1`. В запись включаются SHA-256 locks Paper/plugin/client mods и хеши кандидатов; валидатор повторно сверяет эти locks, кандидаты и сохранённые файлы. Подпись идентифицирует оператора, который её создал. Она не заменяет настоящий запуск и визуальную проверку Minecraft.

Проверка серверного лога засчитывает только строки точного формата Paper `Server thread/INFO`, а загрузку плагина — только запись его собственного logger-префикса `[PluginName] Enabling PluginName v…`. Это отсеивает обычные сообщения плагина, содержащие похожий текст, но сам лог остаётся обычным файлом: плагин с прямой записью в stdout или изменённый файл может подделать и такой формат. Поэтому оператор должен лично наблюдать оба запуска полного runtime, сохранить их исходный лог и подписывать evidence только после этой проверки. Подпись фиксирует ответственность оператора, а не доказывает происхождение строк из Paper.

Проверочный public key берётся только из переменной окружения `MIGRATION_ACCEPTANCE_PUBLIC_KEY`. Валидатор не читает ключ из checkout, acceptance JSON или signature-файла. При отсутствии или некорректном значении CI завершается ошибкой. Private key хранится вне checkout, не передаётся CI и не публикуется.

Политика повторного использования: подписанная приёмка использует schema 4 и содержит `sourceCommit` — точный SHA коммита с кодом, lock-файлами и workflow, который прошёл проверку. Запись и подпись фиксируются следующим отдельным коммитом, меняющим только `artifacts/minecraft-26.3/native-acceptance/`; оба CI проверяют родительский SHA и отклоняют такой evidence-коммит, если он содержит любые другие изменения. Так доказательство нельзя повторно использовать после правки исходников. В `client.modInventory` сохраняются имена, размеры и SHA-512 фактических JAR активного профиля; валидатор сравнивает их с lock и staged-кандидатами. Замена проверочного ключа прекращает доверие к прежней подписи; для ротации нужно создать новый key, подписать текущую evidence повторно и обновить переменную на обеих платформах.

## Подготовка ключа

Запускайте команды из проверенного checkout после просмотра diff signing-инструмента:

```powershell
py -3.13 .\scripts\minecraft\sign_native_acceptance.py keygen
```

Команда создаёт Ed25519 private key по умолчанию в `%LOCALAPPDATA%\CopiMine\minecraft-26.3\native-acceptance-ed25519-private.pem` на Windows. На POSIX-платформах используется `$XDG_CONFIG_HOME/CopiMine/minecraft-26.3/native-acceptance-ed25519-private.pem`, либо `~/.config/CopiMine/minecraft-26.3/native-acceptance-ed25519-private.pem`, если `XDG_CONFIG_HOME` не задан; заданный `XDG_CONFIG_HOME` должен быть абсолютным. Команда устанавливает защиту до записи ключа и выводит public hex. На Windows файл создаётся эксклюзивно с защищённым ACL только для текущего пользователя. Перед каждой подписью signer заново задаёт защищённый DACL с единственной разрешающей записью для текущего пользователя, поэтому явно выданный доступ другим локальным группам удаляется. На POSIX-платформах перед чтением ключу задаётся режим `0600`. Команда откажется перезаписывать существующий файл. Храните резервную копию private key в защищённом месте; не добавляйте её в репозиторий и не прикрепляйте к CI.

Для определения SID keygen и signer запускают `whoami.exe` из Windows system directory, полученного через `GetSystemDirectoryW`, и задают его как рабочую директорию. DACL устанавливается Windows security API из защищённого security descriptor. Имена helper-программ в checkout или в `PATH` не используются.

Сохраните выведенный `MIGRATION_ACCEPTANCE_PUBLIC_KEY` как repository variable GitHub в **Settings → Secrets and variables → Actions → Variables** и как project CI/CD variable в GitLab в **Settings → CI/CD → Variables**. Значение публичное, поэтому его не нужно маскировать. Оно должно устанавливаться только через административные настройки проекта, а не в файле коммита. Для pipeline на миграционной ветке GitLab variable должна быть доступна этой ветке.

## Порядок проверки и фиксации evidence

Не меняйте статус кандидата на `migration-accepted` до настоящих проверок. Сначала установите полный plugin lock без `--unauthenticated-test-runtime`, проверьте активную инвентаризацию и запустите Paper в отдельном loopback-режиме с полным набором плагинов:

```powershell
py -3.13 .\scripts\minecraft\install_migration_server_plugins.py
.\scripts\minecraft\StartMigrationTestServer.ps1 -FullPluginSet
```

`-FullPluginSet` использует тот же безопасный локальный bind `127.0.0.1`, `online-mode=false` и `enable-rcon=false`, требует ровно один JAR AuthMe и проверяет полный inventory/receipt по lock. Перед запуском скрипт задаёт для Java-плагинов путь к `local-runtime/end-rift-server-26.3/migration-isolated-postgres.env`; отдельный PostgreSQL-кластер кандидата слушает только `127.0.0.1:55434`; файл должен быть создан локальным DB-тестовым окружением и не должен быть ссылкой. Если его нет, запускатель завершится с ошибкой вместо старта набора плагинов без конфигурации БД. Этот режим проверяет запуск полного набора; он не требует пароля, не доказывает регистрацию/вход через AuthMe и не предназначен для внешнего подключения. Обычный запуск без параметра также использует изолированную DB-конфигурацию, но оставляет AuthMe выключенным для удобного локального подключения.

Для acceptance дважды запустите и штатно остановите сервер с `-FullPluginSet`, чтобы `server.log` содержал два успешных старта полного набора. Каждый успешный старт в этом acceptance-логе обязан показывать загрузку каждого плагина из lock; запуск без AuthMe не засчитывается. Для проверки клиента сначала переключите каталог плагинов в локальный режим без AuthMe/AuthEffects, иначе запускатель отклонит запуск при установленном AuthMe. Сделайте необходимые игровые проверки и F2 screenshots; сохраните этот серверный лог отдельно: acceptance `server.log` должен содержать только старты полного plugin set.

```powershell
py -3.13 .\scripts\minecraft\install_migration_server_plugins.py --unauthenticated-test-runtime
.\scripts\minecraft\StartMigrationTestServer.ps1
``` После клиентской проверки штатно остановите сервер и восстановите полный locked plugin inventory и receipt:

```powershell
py -3.13 .\scripts\minecraft\install_migration_server_plugins.py
```

Пока lock остаётся в `runtime-candidates` с `nativeVerified=false`, снимите точные JAR-байты полного runtime:

```powershell
py -3.13 .\scripts\minecraft\capture_native_plugin_snapshot.py --candidate
```

Этот режим требует candidate-статус, сверяет полный inventory и installer receipt с lock, включая AuthMe, и сохраняет снимки только в `build/minecraft-26.3/native-plugin-candidate/`; он не изменяет каталог принятой evidence и не повышает статус или `nativeVerified`. Если AuthMe отсутствует в тестовом runtime, снимок завершается отказом. После проверки всех кандидатов и клиента обновите оба lock до `migration-accepted` и только для фактически проверенных модулей укажите `nativeVerified=true`. Затем выполните обычный `capture_native_plugin_snapshot.py` без `--candidate`, чтобы записать финальные файлы в `artifacts/minecraft-26.3/native-acceptance/`, сформируйте настоящий `acceptance.json` с сохранёнными логами/inventory/screenshots и подпишите:

```powershell
py -3.13 .\scripts\minecraft\sign_native_acceptance.py sign
```

Команда проверяет canonical JSON, подписывает запись внешним ключом, запускает локальный acceptance validator и сохраняет `acceptance.sig` только при успешной проверке. Любое изменение записи или её привязанного файла после подписи требует повторной проверки и подписи.

## CI и ротация

GitHub Actions читает public key из repository variable. GitLab CI получает project variable в окружение job. Оба CI должны использовать одинаковое значение и выполнять обязательный native acceptance gate. На GitLab задайте `ci_pipeline_variables_minimum_override_role=no_one_allowed` в Settings → CI/CD → Variables (Minimum role to use pipeline variables): GitLab pipeline variables имеют приоритет над project/group variables и иначе могут подменить проверочный ключ. Перед приёмкой проверяйте это значение в настройках проекта. Проверки должны быть обязательными для принятия migration checkpoint, а изменение самих workflow и validator должно проходить доверенное code review.

Ключевая ротация — одноэтапная: создайте новый private key, замените public key variables на GitHub и GitLab, переподпишите актуальную evidence и повторно запустите оба pipeline. Старые подписи намеренно перестанут проходить; текущая миграционная приёмка должна быть подписана ключом, который настроен на обеих платформах.
