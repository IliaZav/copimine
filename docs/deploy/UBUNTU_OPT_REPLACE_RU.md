# CopiMine: перенос на Ubuntu заменой `/opt/copimine`

Этот архив подготовлен так, чтобы можно было заменить всю папку `/opt/copimine` целиком.

## Что лежит в архиве

- Внутри архива есть верхняя папка `copimine/`.
- Её нужно распаковать в `/opt`, чтобы на сервере получилось `/opt/copimine/...`.
- В архив не включены `.git`, `__pycache__` и `*.pyc`.

## Что сделать на сервере

1. Остановить всё, что использует `/opt/copimine`.
   - Если сервер стартует через `systemd`, остановить сервис Minecraft и сервис admin-web.
   - Если что-то запущено вручную через `screen` или `tmux`, сначала корректно завершить процессы.

2. Сделать резервную копию текущей папки.

```bash
cd /opt
sudo mv copimine copimine.backup.$(date +%F-%H%M%S)
```

3. Скопировать подготовленный архив на сервер.

Пример:

```bash
scp CopiMine-opt-ubuntu-transfer-2026-06-27.zip user@server:/opt/
```

4. Распаковать архив в `/opt`.

```bash
cd /opt
sudo unzip -q CopiMine-opt-ubuntu-transfer-2026-06-27.zip
```

После распаковки должен существовать путь:

```bash
/opt/copimine
```

5. Проверить владельца и права.

Если Minecraft и backend работают не от `root`, вернуть владельца под нужного пользователя.
Пример для пользователя `minecraft`:

```bash
sudo chown -R minecraft:minecraft /opt/copimine
```

6. Убедиться, что ключевые файлы на месте.

```bash
ls /opt/copimine/minecraft/server/plugins
ls /opt/copimine/admin-web
ls /opt/copimine/resourcepacks/build
```

7. Проверить resource pack hash.

```bash
grep '^resource-pack=' /opt/copimine/minecraft/server/server.properties
grep '^resource-pack-sha1=' /opt/copimine/minecraft/server/server.properties
sha1sum /opt/copimine/resourcepacks/build/CopiMineResourcePack.zip
```

Значение `resource-pack-sha1` в `server.properties` должно совпадать с фактическим SHA1 архива resource pack.

8. Запустить сервисы обратно.

Пример:

```bash
sudo systemctl start copimine-minecraft
sudo systemctl start copimine-admin-web
```

Если имена сервисов другие, используй свои реальные unit names.

## Что проверить сразу после запуска

1. Открывается сайт.
2. Работают отдельные страницы:
   - `/`
   - `/server.html`
   - `/shops.html`
   - `/mods.html`
   - `/signin.html`
   - `/register.html`
3. Открываются страницы кабинета.
4. В `minecraft/server/plugins` лежат актуальные jar:
   - `CopiMineUltimateAdminPlus.jar`
   - `CopiMineArtifacts.jar`
   - `CopiMineEconomyCore.jar`
   - `CopiMineElectionCore.jar`
   - `CopiMineNarcotics.jar`
   - `CopiMineWorldCore.jar`
5. Сервер Minecraft запускается без ошибок загрузки плагинов.
6. Resource pack скачивается по старому URL.

## Если нужен быстрый откат

```bash
cd /opt
sudo rm -rf copimine
sudo mv copimine.backup.YYYY-MM-DD-HHMMSS copimine
```

Потом снова запустить сервисы.

## Замечание

Этот способ рассчитан именно на замену всей папки `/opt/copimine`. Если на сервере есть локальные правки, которых нет в текущем проекте, они будут потеряны при полной замене папки.
