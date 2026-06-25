# CopiMine Ultimate Admin

Веб-админка для Minecraft RP/SMP сервера CopiMine / ServerRP.

Текущая версия сохраняет простую архитектуру:

- backend: Python FastAPI;
- frontend: обычный HTML/CSS/JS без сборки;
- backend слушает `127.0.0.1:8090`;
- nginx проксирует внешний доступ через порт `18080`;
- реальные секреты и data-файлы остаются только в `.env` и `data/`.

## Что есть в v2.1

- Dashboard с live-поллингом, health, портами, systemd-сервисами и индикаторами источников.
- Разделы: Dashboard, Игроки, Инвентари, Логи, Расследования, Экономика/Ары, Выборы, Discord, Управление сервером, Источники данных, Настройки, Безопасность, Аудит.
- Таблицы с поиском, сортировкой, пагинацией и экспортом CSV/JSON на стороне frontend.
- Login по Minecraft-нику, session token, logout, auto-expire, rate limit на вход.
- Audit log в `data/audit_log.jsonl`.
- Event ingestion API `POST /api/plugin/events` для будущих плагинов.
- Registry источников данных и capabilities.
- Read-only аналитика inventory/economy/elections с честными сообщениями, если источника нет.
- Inventory snapshot/history/diff в отдельных файлах панели.
- Economy snapshots в отдельных файлах панели.
- Discord backend API и отдельный сервис `copimine-discord-bot.service`.
- Безопасный RCON command center через allowlist.

## Жёсткие ограничения

- `.env` не перезаписывается установщиком.
- `data/admin_users.json`, `data/sessions.json`, игровые логи, playerdata, плагины и реальные конфиги Minecraft не удаляются установкой.
- Экономика/Ары строго read-only: нет выдачи, списания, перевода, удаления, создания или изменения баланса.
- Секреты не отдаются во frontend.
- Discord token, RCON password, secret keys и API keys должны храниться только в `.env`.

## Установка зависимостей

```bash
cd /opt/copimine/admin-web
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Если `.env` отсутствует на новой установке:

```bash
cp .env.example .env
chmod 600 .env
```

Для production обновления не копируйте `.env.example` поверх существующего `.env`.

## Запуск backend

```bash
cd /opt/copimine/admin-web
. .venv/bin/activate
uvicorn backend.main:app --host 127.0.0.1 --port 8090 --proxy-headers
```

Systemd unit:

```bash
sudo install -m 0644 deploy/copimine-admin.service /etc/systemd/system/copimine-admin.service
sudo systemctl daemon-reload
sudo systemctl enable --now copimine-admin
```

## Discord bot

Заполните в `.env` только на сервере:

```text
DISCORD_BOT_TOKEN=CHANGE_ME
DISCORD_GUILD_ID=CHANGE_ME
DISCORD_APPLICATIONS_CHANNEL_ID=CHANGE_ME
DISCORD_REPORTS_CHANNEL_ID=CHANGE_ME
DISCORD_ADMIN_ROLE_ID=CHANGE_ME
DISCORD_ADMIN_ALLOWLIST=
DISCORD_BOT_API_KEY=CHANGE_ME
ADMIN_PUBLIC_BASE_URL=http://admin.copimine.ru:18080
BACKEND_INTERNAL_BASE_URL=http://127.0.0.1:8090
```

Discord module now uses PostgreSQL-first persistence for runtime state, outbox/status snapshots, notification log and bridge events, while legacy JSON files in `data/` remain only as compatibility mirrors and fallback payload caches. It publishes embeds with buttons, creates threads when Discord permissions allow it, and syncs button actions back to backend audit log and V4 sync tables. Channel names `┋заявки` and `┋жалобы` are only fallback hints; channel IDs from `.env` are the primary routing method.

Systemd unit:

```bash
sudo install -m 0644 deploy/copimine-discord-bot.service /etc/systemd/system/copimine-discord-bot.service
sudo systemctl daemon-reload
sudo systemctl enable --now copimine-discord-bot
```

Если Discord-переменные не настроены, бот честно пишет это в journal, а сайт продолжает работать.

## Проверки

```bash
cd /opt/copimine/admin-web
. .venv/bin/activate
python -m py_compile backend/main.py backend/discord_bot.py
python scripts/backend_smoketest.py
python scripts/security_selftest.py
./deploy/copimine-live-smoke.sh
curl -fsS http://127.0.0.1:8090/api/health
```

## Документация

- `docs/PLUGIN_INTEGRATION_RU.md` — как будущим Minecraft-плагинам отправлять события и подключаться через adapters.
- `docs/SAFE_DEPLOY_UBUNTU_RU.md` — безопасный deploy/rollback на Ubuntu.
