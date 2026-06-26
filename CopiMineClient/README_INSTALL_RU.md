# CopiMineClient

`CopiMineClient` — необязательный Fabric-клиент для визуальных эффектов `CopiMineNarcotics`.

Что он делает:
- рисует fullscreen overlay и shader-like эффекты прямо на клиенте;
- принимает команды от сервера по каналу `copimine:client_bridge`;
- подтверждает запуск эффекта через `visual_ack`;
- сообщает о завершении эффекта через `visual_finished`;
- отправляет heartbeat, чтобы сервер понимал, что клиент всё ещё доступен.

Что он не делает:
- не требует Iris;
- не требует OptiFine;
- не требует отдельного shaderpack;
- не является обязательным по умолчанию;
- не даёт игровых преимуществ.

Совместимость:
- `CopiMineClient` можно ставить вместе с Emotecraft;
- мод не занимает стандартное колесо эмоций Emotecraft;
- мод не переключает и не ломает Iris shaderpack, если он уже включён у игрока.
- текущая сборка использует HUD callback из зафиксированной Fabric API проекта; для перехода на новый HUD registry нужен отдельный апгрейд зависимости.

Как установить:
1. Установить Fabric Loader для Minecraft `1.21.1`.
2. Положить `CopiMineClient-<version>.jar` в папку `mods`.
3. Для полного клиентского набора положить рядом:
   - `fabric-api-0.116.12+1.21.1.jar`
   - `emotecraft-for-MC1.21.1-2.4.12-fabric.jar`
4. Запустить клиент с Fabric.
5. Зайти на сервер CopiMine.

Проверка:
- `/copimineclient status`
- `/copimineclient protocol`
- `/copimineclient visual test GREEN_NOISE 10 1.0`

Что будет без мода:
- сервер не кикает игрока по умолчанию;
- `CopiMineNarcotics` остаётся рабочим;
- сервер переключается на resource-pack overlay или particle fallback.

Важно:
- `CopiMineClient` хранит клиентские visual assets внутри собственного jar;
- серверный resource pack остаётся общим и нужен для предметов, моделей и fallback overlay;
- true Iris/OptiFine shader runtime сервер не заявляет и не пытается принудительно включать.
