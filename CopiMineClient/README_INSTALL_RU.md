# CopiMineClient

`CopiMineClient` — необязательный Fabric-клиент для красивых visual-эффектов CopiMineNarcotics.

Что он даёт:
- client-side fullscreen overlay / shader-like эффекты без зависимости от Iris и OptiFine;
- handshake с сервером CopiMine;
- локальные команды диагностики и visual test.

Что он не делает:
- не является обязательным по умолчанию;
- не даёт преимущества в геймплее;
- не нужен для базовой работы сервера;
- не подменяет и не навязывает внешний shaderpack.

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
- сервер остаётся рабочим и без этого мода;
- если мод отсутствует, сервер использует resource-pack overlay или particle fallback;
- Iris и OptiFine не требуются и не используются как обязательная зависимость;
- если у игрока уже включён Iris shaderpack, CopiMineClient не заменяет его и не вмешивается в его pipeline: мод рисует свои эффекты как отдельный fullscreen HUD overlay поверх обычного рендера;
- при активном Iris shaderpack маршрут не меняется: CopiMineClient остаётся в режиме fullscreen HUD overlay и лишь снижает прозрачность своих эффектов, чтобы они не давили поверх уже включённого shaderpack.
