# CopiMineClient Visual Runtime Smoke

## Без CopiMineClient
1. Зайти на сервер без `CopiMineClient`.
2. Убедиться, что resource pack загружается как обычно.
3. Выполнить `/cmclient check <игрок>` и увидеть `missing-client` или аналогичный fallback hint.
4. Выполнить `/cmclient visualtest <игрок> GREEN_NOISE 10 1.0`.
5. Убедиться, что игрок не кикается, а срабатывает `SERVER_RESOURCE_PACK_OVERLAY` или `SERVER_PARTICLE_FALLBACK`.
6. Проверить, что в логах нет ошибок bridge/protocol.

## С CopiMineClient
1. Положить `CopiMineClient-0.1.0.jar` в папку `mods`.
2. Запустить Minecraft `1.21.1` с Fabric.
3. Выполнить `/copimineclient status` и убедиться, что есть `protocol=2`, `helloSent=yes`, `helloConfirmed=yes`.
4. Выполнить `/cmclient check <игрок>` и увидеть готовый client route.
5. Выполнить `/cmclient visualtest <игрок> GREEN_NOISE 10 1.0`.
6. Убедиться, что эффект виден только этому игроку.
7. Через 10 секунд убедиться, что эффект сам очищается.
8. Проверить `/cmclient sessions` и убедиться, что зависших client sessions нет.

## Ошибочные сценарии
1. Выполнить `/cmclient visualtest <игрок> UNKNOWN 10 1.0` и убедиться, что unknown effect не ломает bridge.
2. Проверить, что при отсутствии `visual_ack` сервер уходит в fallback, а не зависает.
3. Проверить disconnect во время эффекта: клиент очищает локальный visual, сервер очищает session.
4. Проверить смену мира во время эффекта: overlay/fallback очищаются.
5. Проверить смерть во время эффекта: overlay/fallback очищаются.
6. Проверить, что resource pack может быть отклонён, а particle fallback всё равно остаётся рабочим.
