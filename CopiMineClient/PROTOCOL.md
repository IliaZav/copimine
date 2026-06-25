# CopiMineClient Protocol v2

Канал:
- `copimine:client_bridge`

Версия протокола:
- `2`

Идея:
- сервер выбирает конкретного игрока, конкретный `effectId`, длительность и intensity;
- `CopiMineClient` запускает visual только у этого игрока;
- если клиента нет или он не подтверждает запуск, сервер автоматически уходит в fallback.

## Client -> Server

`hello`
- `protocol`
- `sessionId`
- `clientVersion`
- `supportedEffects[]`
- `clientVisuals=true`
- `clientOverlay=true`
- `clientShaderLike=true`
- `trueIrisShader=false`

`heartbeat`
- `protocol`
- `sessionId`

`visual_ack`
- `protocol`
- `sessionId`
- `seq`
- `effectId`
- `status`

Allowed statuses:
- `STARTED`
- `STOPPED`
- `CLEARED`

`visual_finished`
- `protocol`
- `sessionId`
- `seq`
- `effectId`
- `reason`

`visual_error`
- `protocol`
- `sessionId`
- `seq`
- `effectId`
- `reason`

## Server -> Client

`ping`
- `protocol`
- `sessionId`

`visual_start`
- `protocol`
- `sessionId`
- `seq`
- `effectId`
- `durationSeconds`
- `intensity`
- `clearPolicy=REPLACE_ALL_FULLSCREEN`
- `source`

`visual_stop`
- `protocol`
- `sessionId`
- `seq`
- `effectId`
- `reason`

`visual_clear_all`
- `protocol`
- `sessionId`
- `seq`
- `reason`

## ACK lifecycle

1. Сервер отправляет `visual_start`.
2. Клиент запускает visual.
3. Клиент отвечает `visual_ack STARTED`.
4. После окончания времени клиент отвечает `visual_finished`.
5. Если сервер отправляет `visual_stop`, клиент отвечает `visual_ack STOPPED`.
6. Если сервер отправляет `visual_clear_all`, клиент отвечает `visual_ack CLEARED`.

## Retry / fallback

- если сервер не получает `visual_ack` за 2 секунды, он повторяет команду;
- максимум 3 попытки;
- после этого сервер помечает client route как failed и переключается на fallback;
- fallback не требует CopiMineClient.

## Heartbeat

- клиент отправляет heartbeat каждые 10 секунд;
- если heartbeat устарел, сервер перестаёт считать клиента доступным для следующего visual route.

## Ограничения

- протокол не зависит от Iris, OptiFine или отдельного shaderpack;
- Paper-сервер не force-включает true client shaders;
- fullscreen shader-like visuals реализуются внутри `CopiMineClient.jar`.
