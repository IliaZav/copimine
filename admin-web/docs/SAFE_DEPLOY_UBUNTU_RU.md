# Безопасная установка CopiMine Ultimate Admin v2.1 на Ubuntu

Команды предполагают текущие пути:

- веб-админка: `/opt/copimine/admin-web`
- Minecraft сервер: `/opt/copimine/minecraft/server`
- backend: `127.0.0.1:8090`
- внешний nginx порт: `18080`

## 1. Загрузить архив с Windows

```powershell
scp .\copimine-admin-web-v2.1-discord.zip <ssh-user>@<server-host>:/tmp/copimine-admin-web-v2.1-discord.zip
```

## 2. Подготовить backup на Ubuntu

```bash
set -euo pipefail
TS="$(date +%Y%m%d-%H%M%S)"
sudo mkdir -p /opt/copimine/backups
sudo tar -czf "/opt/copimine/backups/admin-web-before-v2-$TS.tar.gz" \
  -C /opt/copimine admin-web \
  --exclude='admin-web/.venv' \
  --exclude='admin-web/backups/*.zip'
sudo cp -a /opt/copimine/admin-web/.env "/opt/copimine/backups/admin-web.env.$TS" || true
sudo cp -a /opt/copimine/admin-web/data "/opt/copimine/backups/admin-web-data.$TS" || true
```

## 3. Остановить сервисы

```bash
sudo systemctl stop copimine-admin
sudo systemctl stop copimine-discord-bot || true
```

## 4. Распаковать новую версию без перетирания секретов/data

```bash
set -euo pipefail
TS="$(date +%Y%m%d-%H%M%S)"
cd /tmp
rm -rf copimine-admin-web-v2
mkdir copimine-admin-web-v2
unzip -q /tmp/copimine-admin-web-v2.1-discord.zip -d copimine-admin-web-v2

sudo rsync -a --delete \
  --exclude='.env' \
  --exclude='data/' \
  --exclude='backups/' \
  --exclude='.venv/' \
  /tmp/copimine-admin-web-v2/opt/copimine/admin-web/ \
  /opt/copimine/admin-web/

sudo install -m 0644 /opt/copimine/admin-web/deploy/copimine-admin.service /etc/systemd/system/copimine-admin.service
sudo install -m 0644 /opt/copimine/admin-web/deploy/copimine-discord-bot.service /etc/systemd/system/copimine-discord-bot.service
sudo systemctl daemon-reload
```

## 5. Обновить зависимости и проверить код

```bash
cd /opt/copimine/admin-web
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m py_compile backend/main.py backend/discord_bot.py
python scripts/backend_smoketest.py
python scripts/security_selftest.py
chmod +x deploy/copimine-live-smoke.sh
./deploy/copimine-live-smoke.sh
```

## 6. Запустить сервисы

```bash
sudo systemctl enable copimine-admin
sudo systemctl restart copimine-admin
sudo systemctl enable copimine-discord-bot
sudo systemctl restart copimine-discord-bot
sudo nginx -t
sudo systemctl reload nginx
```

## 7. Проверить здоровье

```bash
curl -fsS http://127.0.0.1:8090/api/health
curl -fsS http://127.0.0.1:18080/api/health
systemctl status copimine-admin --no-pager -l
systemctl status copimine-discord-bot --no-pager -l
```

Если Discord `.env` ещё не заполнен, сервис будет честно писать в journal, что не хватает конфигурации, и сайт продолжит работать.

## 8. Rollback

```bash
set -euo pipefail
sudo systemctl stop copimine-discord-bot || true
sudo systemctl stop copimine-admin
sudo rm -rf /opt/copimine/admin-web
sudo mkdir -p /opt/copimine
sudo tar -xzf /opt/copimine/backups/admin-web-before-v2-YYYYMMDD-HHMMSS.tar.gz -C /opt/copimine
sudo cp -a /opt/copimine/backups/admin-web.env.YYYYMMDD-HHMMSS /opt/copimine/admin-web/.env || true
sudo cp -a /opt/copimine/backups/admin-web-data.YYYYMMDD-HHMMSS /opt/copimine/admin-web/data || true
sudo systemctl daemon-reload
sudo systemctl restart copimine-admin
sudo nginx -t && sudo systemctl reload nginx
```

Замените `YYYYMMDD-HHMMSS` на timestamp созданного backup.

## Файлы, которые нельзя перетирать

- `/opt/copimine/admin-web/.env`
- `/opt/copimine/admin-web/data/admin_users.json`
- `/opt/copimine/admin-web/data/sessions.json`
- `/opt/copimine/admin-web/data/*.jsonl`
- `/opt/copimine/admin-web/backups/`
- `/opt/copimine/minecraft/server/server.properties`
- `/opt/copimine/minecraft/server/ops.json`
- `/opt/copimine/minecraft/server/whitelist.json`
- `/opt/copimine/minecraft/server/world/playerdata/`
- `/opt/copimine/minecraft/server/logs/`
- `/opt/copimine/minecraft/server/plugins/`
