# Связь сайта v6 с игровым сервером

В этой папке находятся готовые расширенные плагины, адаптер и воспроизводимый сборщик. Сайт передаёт типизированное действие, а результат подтверждает подписанная квитанция плагина. Ответ RCON сам по себе не означает, что предмет выдан или наказание применено.

## Что уже собрано и проверено

Исходники трёх плагинов зафиксированы на коммите `a16dc50002f3cde94a4f0e350a18fccd4e1c8bf5` репозитория `IliaZav/copimine`. Сборка выполнена `javac 25.0.2` с Paper API `26.3.build.169-beta`. В `build-report.json` перечислены 16 исходных файлов, зависимости, изменения совместимости и предупреждения компилятора. Это проверка компиляции: действующий сервер не запускался и игровые команды при подготовке архива не выполнялись.

| Файл в dist | Размер, байт | SHA-256 |
| --- | ---: | --- |
| CopiMineUltimateAdminPlus.jar | 291662 | `d345f234120285846ada34ffe7aaddbda550db8f8cfdc8a820dd834da9048f26` |
| CopiMineArtifacts.jar | 279304 | `568a141ef79390812009a26cda4facd7b2570cbfe09296031275dfedcb9f0a35` |
| CopiMineEconomyCore.jar | 150343 | `3c136a7b608f12b826dead8885812766e5db2ad7bf219032460a18d18a10b9c4` |

Эти три JAR, `build-report.json` и `protocol-verification.json` побайтно сверены с коммитом `742ccb10f8ae52cc2a4fa663bcc0e749f9ac52c0` ветки `codex/site-v6-complete-20261017`. CRC всех JAR и пять контрольных сумм расширений совпадают. Протокол прошёл 13 проверок, включая подписи Python → Java и Java → Python и русский текст. Текущий контроль доставки, профилей и конфигурации дополнительно покрыт `tests/test_adapter.py`.

## Устройство

1. API сайта проверяет сеанс, 2FA, права, иерархию ролей и параметры, затем сохраняет операцию в своей БД.
2. Отдельный `worker` подписывает запрос к адаптеру. Адаптер работает рядом с Minecraft и пишет подписанный файл в закрытую папку плагина.
3. RCON получает только `cmultra sitebridge execute <UUID>`. Содержимое полей сайта не подставляется в консольную строку.
4. Плагин проверяет подпись, UUID и параметры, сохраняет журнал и выполняет действие в главном потоке сервера. Повтор того же UUID не создаёт повторную выдачу.
5. Адаптер публикует подписанную квитанцию и реальные снимки профиля через `/api/v6/adapter/ack` и `/api/v6/adapter/snapshots`.

Покупки ждут входа игрока и свободного места. Custom-предметы создаёт фабрика Artifacts; родная проверка привязки сохраняется. Изменение игрового баланса выполняет EconomyCore. Баланс сайта, лимиты покупок и отзыв сеансов обрабатываются в БД сайта и не отправляются в игру. Возврат за недоставленную покупку разрешён только при доказанной недоставке; неизвестный результат остаётся на разборе.

Плагин сохраняет привязки UUID из проверенных операций. Для первичного заполнения карточки после привязки аккаунта запроси обновление инвентаря в админке. Далее профили игроков в сети публикуются каждые 30 секунд, а также при входе и выходе. Период задаётся `site-bridge.profile-interval-seconds` (5–3600 секунд). На сайте повторно проверяется текущая привязка аккаунта к UUID.

## Установка на Linux

Ниже используются `/opt/copimine/site-v6` для сайта и `/opt/copimine/minecraft/server` для Minecraft. Замени путь и домен, если размещение другое. Адаптер должен работать от того же системного пользователя, что и Minecraft: плагин и адаптер читают общий ключ с правами `0600`. Веб-процесс может работать от другого пользователя — ему передаётся только отдельный ключ связи сайта с адаптером.

### 1. Подготовить закрытые файлы

Проверь имя пользователя службы Minecraft. В примере оно берётся из владельца `server.properties`:

```sh
CM_GAME_USER=$(stat -c '%U' /opt/copimine/minecraft/server/server.properties)
sudo install -d -m 0700 -o "$CM_GAME_USER" /var/lib/copimine-game
sudo -u "$CM_GAME_USER" python3 -c 'from pathlib import Path; import getpass, os; p=Path("/var/lib/copimine-game/rcon-password.txt"); fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600); f=os.fdopen(fd,"w"); f.write(getpass.getpass("Текущий пароль RCON: ")+"\n"); f.close()'
```

Введи действующий пароль RCON. Команда не выводит пароль и отказывается перезаписывать существующий файл. В `server.properties` должны быть `enable-rcon=true`, соответствующие `rcon.port` и `rcon.password`. Доступ к порту RCON с внешних интерфейсов закрой межсетевым экраном; адаптер принимает только loopback-адрес RCON. При изменении `server.properties` требуется обычный перезапуск Minecraft.

Создай два независимых ключа и конфигурации:

```sh
sudo -u "$CM_GAME_USER" python3 /opt/copimine/site-v6/integrations/game-bridge/configure.py --output /var/lib/copimine-game/bridge --spool /opt/copimine/minecraft/server/plugins/CopiMineUltimateAdminPlus/site-bridge --site-url https://copimine.ru --rcon-password-file /var/lib/copimine-game/rcon-password.txt
```

`configure.py` не обращается к сети или игре. Он создаёт новую папку и отказывается писать поверх существующей. Внутри будут `adapter.json`, `site-adapter.json`, `site-signing.key`, `spool-signing.key`, `plugin-config-fragment.yml` и `state/`.

### 2. Подключить настройки и плагины

Перенеси **три поля** из закрытого `site-adapter.json` в `/etc/copimine-site/site.json`: `adapterUrl`, `adapterSecret`, `adapterCapabilities`. Сохрани остальные настройки сайта, почты, SMS, VK и платежей. Закрой файл сайта правами `0600`, оставив чтение пользователю веб-службы. Не копируй закрытые файлы в каталог сайта, GitHub или ZIP.

Добавь содержимое `plugin-config-fragment.yml` к действующему `plugins/CopiMineUltimateAdminPlus/config.yml` как раздел `site-bridge`. Если раздел уже существует, обнови его, не создавая второй. Сохрани остальные настройки плагина. Значение `secret-file` должно быть абсолютным путём к `spool-signing.key`.

Путь `spoolPath` в адаптере обязан указывать на `site-bridge` внутри реальной папки данных Admin-плагина. Имена миров автоматически сопоставляются с основным миром, Незером и Эндом; при нестандартных именах укажи `site-bridge.worlds.overworld`, `.nether`, `.end` в конфигурации плагина.

Перед заменой останови Minecraft штатным способом, сохрани резервную копию трёх старых JAR и каталогов данных этих плагинов. Скопируй **все три** файла из `dist/` в `plugins/`, убрав только прежние JAR тех же трёх плагинов. Конфиги, БД и остальные плагины сохраняются. Затем запусти сервер. Горячая перезагрузка плагинов не используется.

### 3. Проверить локальную конфигурацию и запустить процессы

```sh
sudo -u "$CM_GAME_USER" python3 /opt/copimine/site-v6/integrations/game-bridge/adapter.py --config /var/lib/copimine-game/bridge/adapter.json --check
```

`--check` читает ключи и создаёт локальные папки/БД nonce; соединений с RCON, сайтом и игроками не делает. Успешный результат содержит `valid: true`. Для рабочего режима запусти адаптер без `--check`, а обработчик сайта — отдельным процессом:

```sh
sudo -u "$CM_GAME_USER" python3 /opt/copimine/site-v6/integrations/game-bridge/adapter.py --config /var/lib/copimine-game/bridge/adapter.json
```

```sh
cd /opt/copimine/site-v6
python server.py --data-dir /var/lib/copimine-site --config /etc/copimine-site/site.json --origin https://copimine.ru worker
```

Для автозапуска помести команду адаптера в отдельную службу systemd с пользователем Minecraft, `Restart=on-failure`, `UMask=0077` и доступом на запись к `/var/lib/copimine-game` и папке `site-bridge`. Пример службы обработчика сайта находится в `deploy/`. Не публикуй порт адаптера 8791 через прокси сайта. Если сайт расположен на другой машине, адаптер поддерживает собственные `tlsCertificate`/`tlsKey`; внешний адрес должен быть HTTPS, RCON по-прежнему остаётся локальным к Minecraft.

## Windows: локальная проверка

Готовые JAR не зависят от ОС. Python 3.11+ нужен для адаптера. На Windows используй отдельную закрытую папку, например `%USERPROFILE%\copimine-private`; убери доступ других пользователей через свойства безопасности NTFS. Доступ оставь учётной записи, от которой запущены Minecraft и адаптер. Приватные файлы в папку сайта не помещаются.

Из PowerShell в каталоге сайта, после сохранения текущего RCON-пароля в закрытом текстовом файле:

```powershell
py -3 integrations/game-bridge/configure.py --output "$env:USERPROFILE\copimine-private\bridge" --spool "D:\Desktop\Copimine\copimine-main\server\plugins\CopiMineUltimateAdminPlus\site-bridge" --site-url http://127.0.0.1:8787 --rcon-password-file "$env:USERPROFILE\copimine-private\rcon-password.txt"
py -3 integrations/game-bridge/adapter.py --config "$env:USERPROFILE\copimine-private\bridge\adapter.json" --check
```

Путь к серверу в примере обязательно замени фактическим. Для локального API используй тот же origin и `--insecure-local`; для опубликованного сайта — его HTTPS-домен. Настройки плагина и три поля сайта переносятся так же, как на Linux. Для проверки конфигурации запуск Minecraft не нужен. Рабочий адаптер запускается той же командой без `--check`.

## Проверки и повторная сборка

Проверки Python используют временные каталоги и локальные заглушки, не подключаются к действующему игровому серверу:

```sh
python -m unittest discover -s integrations/game-bridge/tests -p 'test_adapter.py' -v
```

Повторная сборка требуется только при изменении исходников или расширений. `build_plugins.py` проверяет исходные хэши и собирает в отдельную папку; действующие JAR не перезаписывает. `--classpath` указывает на JSON-файл с массивом абсолютных путей к JAR зависимостей, например `["/path/to/paper-api.jar", "/path/to/dependency.jar"]`. Включи реальные зависимости, перечисленные в отчёте. Для проверки известного исходного коммита:

```sh
python integrations/game-bridge/build_plugins.py --git-repo /path/to/copimine --output /tmp/copimine-bridge-build --javac /path/to/jdk-25/bin/javac --classpath /path/to/classpath.json
```

Список реально использованных зависимостей и их контрольные суммы смотри в `build-report.json`. `--prepare-only` создаёт проверенные изменённые исходники без компиляции. Тест двустороннего протокола запускает `tests/run_protocol_check.py`; параметры доступны через `--help`.

## Если действие остаётся в очереди

Проверь журнал API/worker, событие `adapter_ready`, запись `Typed website bridge enabled` в журнале Minecraft, совпадение ключей и доступ к закрытой папке. `bridge_pending` и `profile_publication_pending` не раскрывают секреты; они означают, что подтверждение ещё не опубликовано. Игрок для выдачи должен быть онлайн, иметь свободное место и подтверждённую привязку UUID.

Не удаляй `journal`, `receipts`, `requested`, `published` и БД очереди для повторной выдачи: они сохраняют защиту от дублей. Не меняй UUID уже принятой операции. При неизвестном исходе сначала сверь инвентарь и журналы, затем решай ситуацию через админку. Резервные копии БД сайта, данных плагинов и закрытых ключей должны храниться согласованно.
