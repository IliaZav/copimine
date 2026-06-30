# CopiMineClient

`CopiMineClient` — необязательный Fabric-клиент для визуальных эффектов `CopiMineNarcotics`.

Что он делает:
- принимает команды от сервера по каналу `copimine:client_bridge`;
- включает клиентские post-process эффекты и поверх них лёгкие overlay-слои;
- при первом запуске выгружает встроенные optional shaderpack ZIP в `.minecraft/shaderpacks/CopiMine`;
- не требует Iris или OptiFine для основной работы.

Что он не делает:
- не обязателен для входа на сервер по умолчанию;
- не даёт игровых преимуществ;
- не подменяет чужой активный Iris shaderpack;
- не ломает игру, если серверный модуль визуалов отключён.

Установка:
1. Установить Fabric Loader для Minecraft `1.21.1`.
2. Поместить `CopiMineClient-<version>.jar` в папку `mods`.
3. Запустить клиент с Fabric.
4. Зайти на сервер CopiMine.

Проверка:
- `/copimineclient status`
- `/copimineclient protocol`
- `/copimineclient visual test DESATURATE 20`

Важно:
- без мода сервер остаётся рабочим и использует server overlay или particle fallback;
- основной красивый путь — клиентский post-process runtime внутри `CopiMineClient`;
- если у игрока уже включён Iris shaderpack, CopiMineClient не пытается его перехватить и остаётся в своём безопасном режиме;
- optional shaderpack ZIP выгружаются локально только как дополнительные файлы для владельца клиента, а не как обязательная часть сервера.
