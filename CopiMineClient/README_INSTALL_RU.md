# CopiMineClient

`CopiMineClient` — необязательный Fabric-клиент для визуалов `CopiMineNarcotics`.

Что он делает:
- принимает команды от сервера по каналу `copimine:client_bridge`;
- при первом запуске выгружает встроенные shaderpack ZIP в `.minecraft/shaderpacks/CopiMine`;
- если установлен Iris и его runtime доступен, временно включает нужный CopiMine shaderpack ZIP во время эффекта и потом возвращает предыдущий shaderpack;
- если Iris недоступен или конкретный ZIP не подходит для Iris runtime, использует встроенный post-process fallback;
- без клиента сервер всё равно работает, просто остаётся server overlay или particle fallback.

Что важно:
- Iris не обязателен для входа на сервер;
- OptiFine не требуется;
- внешний shaderpack вручную скачивать не нужно;
- `white_sharp_1_2.zip` остаётся запасным встроенным Iris shaderpack для ручной проверки и случайных визуалов;
- в сборку также включены `ctr_vcr.zip`, `lsd_shader.zip` и `trippy_shaderpack.zip`;
- если у игрока уже включён свой Iris shaderpack, CopiMineClient может временно подменить его только если это разрешено в `config/copimineclient.properties`, после чего возвращает прежнее состояние.

Установка:
1. Установить Fabric Loader для Minecraft `1.21.1`.
2. Положить `CopiMineClient-<version>.jar` в папку `mods`.
3. При желании поставить Iris. Без Iris клиент всё равно запустится.
4. Зайти на сервер CopiMine.

Проверка:
- `/copimineclient status`
- `/copimineclient protocol`
- `/copimineclient shader status`
- `/copimineclient shader test acid_shaders 20`
- `/copimineclient shader test ctr_vcr 20`
- `/copimineclient shader test lsd_shader 20`
- `/copimineclient shader test trippy_shaderpack 20`
- `/copimineclient shader test white_sharp_1_2 20`
- `/copimineclient shader restore`
- `/copimineclient visual test DESATURATE 20`

Совместимость: CopiMineClient не назначает собственные горячие клавиши и не конфликтует с Emotecraft; канал связи используется только `copimine:client_bridge`.
