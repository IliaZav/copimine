# CopiMineClient

`CopiMineClient` — обязательный Fabric-мод CopiMine для Minecraft `26.3`. При входе сервер принимает только ответ, в котором клиент сообщает mod ID `copimineclient` и точную версию `0.1.1+26.3`. Остальные игровые и графические моды сервер не требует. Протокол проверяет сообщённые значения, но не доказывает, что клиент использует неизменённый JAR мода.

Для запуска нужны Minecraft `26.3`, Fabric Loader `0.19.5` или новее и только один отдельный JAR — CopiMineClient. Совместимый Fabric API `0.162.0+26.3` вложен в JAR CopiMineClient как Jar-in-Jar, поэтому отдельно устанавливать его не нужно. Другие моды, включая графические и игровые, необязательны; их дополнительные библиотеки устанавливаются только если они нужны выбранным игроком модам. Iris и OptiFine не нужны.

Артефакт для этого профиля создаётся миграционной сборкой `scripts/minecraft/PrepareMigrationCandidates.ps1` из проекта `tools/minecraft-26.3/client` и проверяется по `tools/minecraft-26.3/profile.lock.json`. Корневой `CopiMineClient/build-client.ps1` пока собирает прежний проект для Minecraft 1.21.1; его JAR не подходит для сервера 26.3.

Что он делает:
- принимает команды от сервера по каналу `copimine:client_bridge`;
- при первом запуске выгружает встроенные shaderpack ZIP в `.minecraft/shaderpacks/CopiMine`;
- если установлен Iris и его runtime доступен, временно включает нужный CopiMine shaderpack ZIP во время эффекта и потом возвращает предыдущий shaderpack;
- если Iris недоступен или конкретный ZIP не подходит для Iris runtime, использует встроенный post-process fallback;
- после подключения сервер отправляет короткую проверку; клиент должен сообщить требуемые mod ID и версию. Клиент без ответа отключается по тайм-ауту, а неверный ответ отклоняется;
- до успешной проверки ответа нельзя двигаться, использовать блоки или чат; из команд доступны только authentication allowlist: `/login`, `/l`, `/register`, `/reg`, `/2fa`, `/premium` и `/freemium`;
- если нужный мод установлен, но отдельный визуальный runtime недоступен, сервер может использовать предусмотренный fallback для конкретного эффекта.

Что важно:
- Iris не обязателен для входа на сервер;
- OptiFine не требуется;
- внешний shaderpack вручную скачивать не нужно;
- `white_sharp_1_2.zip` остаётся запасным встроенным Iris shaderpack для ручной проверки и случайных визуалов;
- в сборку также включены `ctr_vcr.zip`, `lsd_shader.zip` и `trippy_shaderpack.zip`;
- если у игрока уже включён свой Iris shaderpack, CopiMineClient может временно подменить его только если это разрешено в `config/copimineclient.properties`, после чего возвращает прежнее состояние.
- AppleSkin, BetterF3, Emotecraft, JourneyMap, Sodium, Simple Voice Chat и другие дополнительные моды остаются на усмотрение игрока и не участвуют в серверной проверке.

Установка:
1. Установить Fabric Loader `0.19.5` или новее для Minecraft `26.3`.
2. Положить `CopiMineClient-0.1.1+26.3.jar` в папку `mods`.
3. При желании установить другие моды и нужные им отдельные библиотеки. Fabric API отдельно не нужен.
4. Запустить именно Minecraft `26.3` и зайти на сервер CopiMine.

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
