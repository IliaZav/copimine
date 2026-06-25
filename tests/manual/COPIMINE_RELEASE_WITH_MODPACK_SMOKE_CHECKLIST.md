# CopiMine release smoke: CoreProtect + Emotecraft + modpack

## Third-party staging

1. Проверить `thirdparty/client-mods`:
   - `CopiMineClient-0.1.0.jar`
   - `emotecraft-for-MC1.21.1-2.4.12-fabric.jar`
   - `fabric-api-0.116.12+1.21.1.jar`
2. Проверить `thirdparty/server-plugins`:
   - `CoreProtect-CE-23.0.jar`
   - `emotecraft-2.4.12-bukkit.jar`
3. Проверить `thirdparty/CopiMineMods.zip` и `thirdparty/CopiMineMods.sha1`.

## Website

1. Открыть публичную главную страницу.
2. Проверить кнопку скачивания модов.
3. Скачать `CopiMineMods.zip`.
4. Убедиться, что архив не содержит server plugin jar.

## CoreProtect

1. Убедиться, что `CoreProtect-CE-23.0.jar` стоит в `minecraft/server/plugins`.
2. После старта сервера проверить `/co i`.
3. Поставить и сломать тестовый блок.
4. Проверить lookup/rollback/restore.

## Emotecraft + CopiMineClient

1. Запустить Fabric 1.21.1 с:
   - `CopiMineClient`
   - `Emotecraft fabric`
   - `Fabric API`
2. Проверить, что Emotecraft колесо эмоций открывается.
3. Проверить `/copimineclient status`.
4. Проверить локальный visual test CopiMineClient.
5. Проверить, что Emotecraft эмоции и CopiMineClient visuals работают вместе.
6. Если используется Iris, проверить, что CopiMineClient не ломает shaderpack и не требует его отключения.

## Performance

1. Проверить `server.properties`:
   - `max-players=50`
   - `view-distance=8`
   - `simulation-distance=6`
2. Прогнать `/spark profiler start`, затем нагрузку 10-15 минут.
3. Проверить `/spark healthreport` и `/mspt`.
4. Зафиксировать, подтверждён ли `MSPT <= 10`.
