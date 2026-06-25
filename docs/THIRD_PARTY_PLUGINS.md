# Сторонние решения CopiMine

## Что добавлено

### CoreProtect Community Edition
- Назначение: rollback/restore разрушений, lookup по игроку, anti-grief аудит.
- Официальные источники:
  - Hangar: https://hangar.papermc.io/CORE/CoreProtect
  - GitHub: https://github.com/PlayPro/CoreProtect
- Лицензия: Artistic-2.0.
- Совместимость, зафиксированная в релизе: `CoreProtect-CE-23.0.jar` в staging и локально на сервере.
- Куда класть:
  - staging: `thirdparty/server-plugins/CoreProtect-CE-23.0.jar`
  - сервер: `minecraft/server/plugins/CoreProtect-CE-23.0.jar`
- Почему выбран:
  - готовый и широко используемый rollback plugin;
  - не требует писать свой anti-grief runtime;
  - поддерживает inspect, lookup, rollback, restore;
  - хранит данные отдельно и не должен встраиваться в PostgreSQL таблицы CopiMine.
- Важно:
  - не смешивать CoreProtect DB с CopiMine PostgreSQL;
  - rollback по protected blocks делать только осознанно админом;
  - API CoreProtect в CopiMine на этом этапе не встраивается.

### Emotecraft
- Назначение: готовая система эмоций, без переписывания в CopiMine.
- Официальный источник:
  - Modrinth project: https://modrinth.com/project/pZ2wrerK
- Лицензия: `GPL-3.0-or-later`.
- Зафиксированные версии для `Minecraft 1.21.1`:
  - client Fabric: `emotecraft-for-MC1.21.1-2.4.12-fabric.jar`
  - server Paper/Bukkit: `emotecraft-2.4.12-bukkit.jar`
- Куда класть:
  - staging client: `thirdparty/client-mods/emotecraft-for-MC1.21.1-2.4.12-fabric.jar`
  - staging server: `thirdparty/server-plugins/emotecraft-2.4.12-bukkit.jar`
  - сервер: `minecraft/server/plugins/emotecraft-2.4.12-bukkit.jar`
- Важно:
  - Emotecraft не встраивается в `CopiMineClient`;
  - `CopiMineClient` не должен иметь hard dependency на Emotecraft;
  - серверный plugin и клиентский mod идут как отдельные артефакты.

### Fabric API
- Назначение: обязательная библиотека для `CopiMineClient` и Fabric-версии Emotecraft.
- Официальный источник:
  - Modrinth project: https://modrinth.com/project/P7dR8mSH
- Лицензия: `Apache-2.0`.
- Зафиксированная версия для `Minecraft 1.21.1`: `fabric-api-0.116.12+1.21.1.jar`
- Куда класть:
  - staging: `thirdparty/client-mods/fabric-api-0.116.12+1.21.1.jar`
  - в клиентский modpack zip: да
  - на Paper сервер: нет

## Клиентский архив для сайта

- Публичный архив: `thirdparty/CopiMineMods.zip`
- Внутри:
  - `mods/CopiMineClient-0.1.0.jar`
  - `mods/emotecraft-for-MC1.21.1-2.4.12-fabric.jar`
  - `mods/fabric-api-0.116.12+1.21.1.jar`
  - `README_RU.txt`
  - `checksums.txt`
  - `modpack_manifest.json`
- Важно:
  - серверные jar туда не входят;
  - `CoreProtect` и `Emotecraft bukkit` не раздаются игроку;
  - архив должен скачиваться только с сайта CopiMine.

## Совместимость CopiMineClient с Iris и Emotecraft

- `CopiMineClient` не зависит от Iris.
- `CopiMineClient` не пытается управлять Iris shaderpack-ами.
- `CopiMineClient` не перехватывает модель игрока и не заменяет emote animation runtime.
- `CopiMineClient` использует только свой канал `copimine:client_bridge` и HUD/fullscreen overlay routing.
- Emotecraft остаётся отдельным модом, который можно ставить рядом.

## Что не делаем напрямую

- Не скачиваем jar с mirror/mediafire/неофициальных архивов.
- Не встраиваем CoreProtect таблицы в CopiMine PostgreSQL.
- Не добавляем server plugin jar в клиентский modpack.
- Не делаем CopiMineClient зависимым от Emotecraft, Iris или OptiFine.
