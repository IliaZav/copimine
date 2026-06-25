# CopiMine Admin: интеграция будущих Minecraft/Discord плагинов

## Базовые правила безопасности

- Плагины отправляют события в backend через `POST /api/plugin/events`.
- Авторизация только через заголовок `X-API-Key: <PLUGIN_API_KEY>`.
- Значение `PLUGIN_API_KEY` хранится только в `.env` панели и конфиге плагина на сервере.
- Backend не доверяет событию как факту модерации: оно попадает в единый журнал и показывается админу как источник данных.
- Экономика/Ары в панели строго read-only. Не добавляйте API для выдачи, списания, перевода, обнуления или изменения баланса.
- Любые игровые write-операции должны идти только через отдельные backend allowlist-шаблоны, а не через произвольный shell/SQL.

## Event ingestion API

Endpoint:

```http
POST /api/plugin/events
X-API-Key: CHANGE_ME
Content-Type: application/json
```

Пример события:

```json
{
  "source": "CopiMineElections",
  "event_type": "vote_cast",
  "actor": "PlayerName",
  "target": "candidate_uuid_or_name",
  "world": "CopiMine",
  "x": 120,
  "y": 70,
  "z": -44,
  "severity": "info",
  "tags": ["elections", "vote"],
  "metadata": {
    "electionId": "president-2026-05",
    "station": "spawn",
    "method": "plugin"
  }
}
```

Поддерживаемые базовые поля:

- `source`: имя плагина или адаптера.
- `event_type`: расширяемый тип события.
- `actor`: игрок, админ или системный источник.
- `target`: цель действия.
- `world`, `x`, `y`, `z`: координаты, если применимо.
- `item`, `block`: предмет или блок, если применимо.
- `severity`: `debug`, `info`, `warning`, `error`, `critical`.
- `tags`: короткие метки для фильтрации.
- `metadata`: JSON с данными конкретного плагина.
- `timestamp`: Unix time, если событие произошло раньше отправки.

## Рекомендуемые типы событий

Выборы:

- `election_created`
- `candidate_applied`
- `candidate_approved`
- `vote_cast`
- `vote_revoked`
- `vote_invalidated`
- `election_stage_changed`
- `election_finished`

Экономика:

- `economy_snapshot_created`
- `ares_changed`
- `ares_suspicious_change`
- `valuable_item_seen`

Игроки и расследования:

- `player_join`
- `player_quit`
- `admin_action`
- `rcon_action`
- `inventory_snapshot_created`
- `inventory_diff_detected`
- `container_opened`
- `block_changed`

Discord:

- `discord_application_created`
- `discord_report_created`
- `discord_action`
- `discord_reply_sent`

## Discord applications/reports API

Future site forms or Minecraft plugins can create moderation objects without touching existing game databases.

Admin-authenticated site endpoints:

- `POST /api/applications` creates a player application and queues Discord publication.
- `GET /api/applications` lists stored applications.
- `PATCH /api/applications/{id}` changes status and queues Discord message update.
- `POST /api/reports` creates a report and queues Discord publication.
- `GET /api/reports` lists stored reports.
- `PATCH /api/reports/{id}` changes report status and queues Discord message update.

Discord bot endpoints use only `X-API-Key` with `DISCORD_BOT_API_KEY` or the existing plugin key:

- `GET /api/discord/outbox?status=pending,update_pending`
- `PATCH /api/discord/outbox/{id}`
- `POST /api/discord/actions`
- `PATCH /api/discord/applications/{id}/status`
- `PATCH /api/discord/reports/{id}/status`
- `POST /api/discord/applications/{id}/replies`
- `POST /api/discord/reports/{id}/replies`

Application event shape:

```json
{
  "player": "PlayerName",
  "uuid": "optional-player-uuid",
  "age": "18",
  "experience": "RP/SMP",
  "why": "why the player wants to join",
  "discord_user_id": "optional-discord-user-id"
}
```

Report event shape:

```json
{
  "reporter": "PlayerName",
  "target": "TargetName",
  "message": "report text",
  "severity": "normal",
  "world": "world",
  "x": 0,
  "y": 64,
  "z": 0
}
```

The bot must never receive arbitrary shell commands. All Discord button actions are mapped to known application/report status transitions and are written to `data/audit_log.jsonl`.

## Adapter pattern

Новый источник данных подключайте отдельным адаптером:

- read-only по умолчанию;
- читает только свой JSON/SQLite/YAML/API;
- возвращает capabilities, например `players`, `inventory`, `economy`, `elections`, `logs`, `block_logs`;
- при отсутствии источника возвращает понятный статус `not_connected`, а UI показывает, какой источник нужен;
- не выполняет произвольный SQL из frontend;
- SQLite открывает read-only, если это база игрового плагина.

Рекомендуемая структура:

```text
backend/adapters/
backend/services/
backend/modules/elections/
backend/modules/economy/
backend/modules/discord/
```

Текущий v2.0 оставляет совместимость с монолитным `backend/main.py`, но API и registry уже сделаны так, чтобы адаптеры можно было вынести без изменения frontend-контракта.

## Проверка

Локально:

```bash
cd /opt/copimine/admin-web
. .venv/bin/activate
python scripts/backend_smoketest.py
python scripts/security_selftest.py
```

После подключения плагина:

```bash
curl -sS -X POST http://127.0.0.1:8090/api/plugin/events \
  -H "X-API-Key: CHANGE_ME" \
  -H "Content-Type: application/json" \
  -d '{"source":"TestPlugin","event_type":"smoke_test","severity":"info","metadata":{"ok":true}}'
```
