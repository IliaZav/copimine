# CopiMine: полная замена `/opt/copimine` на Ubuntu

Инструкция для архива вида `CopiMine-opt-ubuntu-transfer-YYYY-MM-DD.tar.gz`.

Архив собран так, чтобы его можно было распаковать прямо в `/opt`.
После распаковки должен появиться путь `/opt/copimine`.

## Что лежит в архиве

- Внутри архива есть верхняя папка `copimine/`.
- Включены миры, плагины, сайт, resource pack, `admin-web/data`, `.env`, база в `db/`, клиентский мод и текущие runtime-файлы проекта.
- Не включены служебные dev-каталоги и Windows-мусор:
  - `.git`
  - `admin-web/.venv`
  - `admin-web/backups`
  - `CopiMineClient/.gradle`
  - `CopiMineClient/.gradle-dist`
  - `__pycache__`
  - `*.pyc`
  - `minecraft/server/logs`
  - `minecraft/server/crash-reports`
  - `minecraft/server/debug`

## 1. Загрузить архив на сервер

Пример с Windows:

```powershell
scp .\CopiMine-opt-ubuntu-transfer-2026-06-28.tar.gz user@server:/root/
scp .\CopiMine-opt-ubuntu-transfer-2026-06-28.tar.gz.sha256 user@server:/root/
```

## 2. Остановить процессы

```bash
sudo systemctl stop minecraft || true
sudo systemctl stop copimine-admin || true
sudo systemctl stop copimine-discord-bot || true
```

Если Minecraft запущен не через `systemd`, а вручную через `screen` или `tmux`, сначала останови его штатно.

## 3. Сделать резервную копию текущей папки

```bash
cd /opt
TS="$(date +%F-%H%M%S)"
sudo mv copimine "copimine.backup.$TS"
```

## 4. Распаковать новый архив

```bash
cd /opt
sudo tar -xzf /root/CopiMine-opt-ubuntu-transfer-2026-06-28.tar.gz
```

Проверка:

```bash
test -d /opt/copimine
ls /opt/copimine
```

## 5. Назначить владельца

Подставь реального системного пользователя, под которым у тебя работают Minecraft и web.
Пример для пользователя `qwerty`:

```bash
sudo chown -R qwerty:qwerty /opt/copimine
```

## 6. Пересобрать Linux venv для сайта

Windows-venv в архив не включён, поэтому на Ubuntu его нужно создать заново.

```bash
cd /opt/copimine/admin-web
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
deactivate
```

## 7. Поставить или обновить systemd unit для сайта

```bash
sudo install -m 0644 /opt/copimine/admin-web/deploy/copimine-admin.service /etc/systemd/system/copimine-admin.service
sudo install -m 0644 /opt/copimine/admin-web/deploy/copimine-discord-bot.service /etc/systemd/system/copimine-discord-bot.service
sudo systemctl daemon-reload
```

Если на сервере service user не `qwerty`, сначала поправь строку `User=` в обоих unit-файлах.

## 8. Проверить ключевые файлы

```bash
ls /opt/copimine/minecraft/server/plugins
ls /opt/copimine/admin-web
ls /opt/copimine/resourcepacks/build
ls /opt/copimine/CopiMineClient
```

## 9. Проверить resource pack

```bash
grep '^resource-pack=' /opt/copimine/minecraft/server/server.properties
grep '^resource-pack-sha1=' /opt/copimine/minecraft/server/server.properties
sha1sum /opt/copimine/resourcepacks/build/CopiMineResourcePack.zip
```

`resource-pack-sha1` в `server.properties` должен совпасть с фактическим SHA1 zip-файла.

## 10. Запустить обратно

```bash
sudo systemctl enable copimine-admin
sudo systemctl restart copimine-admin
sudo systemctl enable copimine-discord-bot
sudo systemctl restart copimine-discord-bot
sudo systemctl start minecraft || true
```

Если Minecraft у тебя называется не `minecraft`, подставь реальный unit name.

## 11. Быстрая проверка после запуска

```bash
curl -fsS http://127.0.0.1:8090/api/health
systemctl status copimine-admin --no-pager -l
systemctl status copimine-discord-bot --no-pager -l
systemctl status minecraft --no-pager -l || true
```

Плюс вручную:

1. Открыть сайт.
2. Проверить `/`, `/server.html`, `/shops.html`, `/mods.html`, `/signin.html`, `/register.html`.
3. Проверить вход в кабинет.
4. Проверить, что в `minecraft/server/plugins` лежат актуальные jar.
5. Проверить, что Minecraft стартует без ошибок загрузки плагинов.
6. Проверить, что resource pack скачивается по старому URL.

## Быстрый откат

```bash
sudo systemctl stop minecraft || true
sudo systemctl stop copimine-admin || true
sudo systemctl stop copimine-discord-bot || true
cd /opt
sudo rm -rf copimine
sudo mv copimine.backup.YYYY-MM-DD-HHMMSS copimine
sudo systemctl daemon-reload
sudo systemctl restart copimine-admin || true
sudo systemctl restart copimine-discord-bot || true
sudo systemctl start minecraft || true
```

## Что важно помнить

- Полная замена `/opt/copimine` перезапишет все локальные файлы внутри этой папки.
- Если на сервере были ручные правки, которых нет в текущем архиве, они будут потеряны.
- Перед запуском сайта на Ubuntu обязательно заново создать `admin-web/.venv`.
