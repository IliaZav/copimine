# CopiMineClient Optional Visuals Smoke Checklist

## Без клиентского мода

1. Игрок заходит без `CopiMineClient`.
2. Сервер не кикает игрока по умолчанию.
3. `/cmclient check <player>` показывает, что клиент не обнаружен.
4. Употребление RP-предмета работает.
5. Если visuals включены и resource pack принят, виден server overlay fallback.
6. Если resource pack не принят, остаются particles/potion fallback.

## С клиентским модом

1. Установить `CopiMineClient-<version>.jar` в `mods`.
2. Зайти на сервер.
3. Подождать до 10 секунд и проверить, что handshake retry сработал.
4. `/cmclient check <player>` показывает handshake и capabilities.
5. `/copimineclient status` показывает `hello sent`, число `attempts` и `render_when_hud_hidden`.
6. `/copimineclient visual test GREEN_NOISE 10` показывает overlay-картинку, а не только текст.
7. `/cmclient visualtest <player> DESATURATE 20` запускает client-side эффект.
8. `/copimineclient visual clear` очищает локальные эффекты.

## F1 / hidden HUD

1. При активном визуале нажать `F1`.
2. По умолчанию visual должен остаться видимым, потому что `render_when_hud_hidden=true`.
3. Если вручную переключить `render_when_hud_hidden=false` в client config и перезапустить клиент, visual должен скрываться вместе с HUD.

## Интенсивность

1. Запустить локальный тест с заметным эффектом.
2. Проверить, что слабая интенсивность почти незаметна.
3. Проверить, что полная интенсивность заметно усиливает alpha/tint/jitter.

## Переходы маршрута

1. Включить `visuals.enabled=true`.
2. Установить `visuals.mode=AUTO`.
3. Проверить приоритет:
   - сначала `CLIENT_MOD_VISUAL`;
   - затем `SERVER_RESOURCE_PACK_OVERLAY`;
   - затем `SERVER_PARTICLE_FALLBACK`.
4. Отключить client bridge и убедиться, что fallback остаётся рабочим.

## Очистка

1. Проверить quit.
2. Проверить death/respawn.
3. Проверить смену мира.
4. Проверить окончание таймера эффекта.
5. Проверить `/cmclient fallbacktest <player> CHAOS 10`.

## Resource pack

1. Проверить, что URL ресурспака не изменился.
2. Проверить, что `CopiMineResourcePack.zip` содержит `narcotics_overlay.json` и overlay PNG.
3. Проверить, что SHA1 в `server.properties` совпадает с фактическим zip.
