# CopiMineNarcotics Textures And Visuals Smoke Checklist

## Startup

1. Сервер стартует без ошибок `CopiMineNarcotics`.
2. В логах нет ошибок PostgreSQL, зависаний main thread и утечки секретов.
3. Загружается общий `CopiMineResourcePack.zip`.
4. `CopiMineUltimateAdminPlus`, `CopiMineEconomyCore`, `CopiMineElectionCore`, `CopiMineArtifacts` продолжают стартовать без регрессий.

## Commands

1. `/cmnarcotics selfcheck` показывает режим текстур, режим визуалов, наличие item models, item textures, overlays, shader placeholders и SHA1 pack.
2. `/cmnarcotics visuals status` честно показывает:
   overlay supported=`false/true`;
   shader supported=`false/true`;
   fallback-only runtime=`true/false`.
3. `/cmnarcotics give <player> feta` выдаёт официальный предмет.
4. `/cmnarcotics give <player> all` выдаёт весь набор.
5. `/cmnarcotics info <player>` показывает текущее состояние игрока.
6. `/cmnarcotics setweight <id> <value>` меняет вес передоза для выбранного предмета.
7. `/cmnarcotics setthreshold <value>` меняет общий порог передоза.
8. `/cmnarcotics setwindow <seconds>` меняет окно накопления веса.
9. `/cmnarcotics setduration <seconds>` меняет override-длительность эффектов.
10. `/cmnarcotics texture mode vanilla` переключает выдачу без `CustomModelData`.
11. `/cmnarcotics texture mode custom` включает выдачу с `CustomModelData`.
12. `/cmnarcotics texture migrate online` меняет только официальные PDC-предметы у игроков онлайн.
13. `/cmnarcotics texture migrate nearby` меняет только официальные PDC-предметы поблизости.
14. `/cmnarcotics visuals enable` включает visual runtime.
15. `/cmnarcotics visuals disable` выключает visual runtime.
16. `/cmnarcotics visuals enable <effectId>` включает отдельный эффект.
17. `/cmnarcotics visuals disable <effectId>` выключает отдельный эффект.
18. `/cmnarcotics visuals mode fallback` ставит безопасный режим по умолчанию.
19. `/cmnarcotics visuals mode overlay` не ломает игру и честно уходит в fallback, если live overlay path не поддержан.
20. `/cmnarcotics visuals mode shader` не ломает игру и честно уходит в fallback, если live shader path не поддержан.
21. `/cmnarcotics visuals test <player> <effectId> [seconds]` накладывает только локальный тестовый визуал на выбранного игрока.
22. `/cmnarcotics clearoverdose <player>` снимает активную передозировку.
23. `/cmnarcotics reset confirm` очищает только narcotics-таблицы и не блокирует main thread.

## Brewing

1. Обычный полный `WATER_CAULDRON` принимает ингредиенты.
2. Порядок ингредиентов не важен.
3. Рецепт `feta` собирается корректно.
4. Рецепт `kola` собирается корректно.
5. Рецепт `girion` собирается корректно.
6. Рецепт `sbp` собирается корректно.
7. Рецепт `sos` собирается корректно.
8. Рецепт `drun` собирается корректно.
9. Рецепт `chups` собирается корректно.
10. Рецепт `borshevik` собирается корректно.
11. Неверная смесь выдаёт `Жужево`.
12. После завершения варки котёл сбрасывается.
13. При ломании котла ингредиенты выпадают обратно.
14. При потере воды состояние котла очищается.

## Official Items

1. Обычный предмет того же материала не срабатывает как наркотик.
2. Переименованный предмет без PDC не срабатывает.
3. Официальный предмет с неверным `CustomModelData` не принимается в custom mode.
4. В vanilla mode официальный предмет остаётся валидным без `CustomModelData`.
5. `copimine_item_type=RP_NARCOTIC` реально стоит на готовых предметах.

## Consumption And Overdose

1. Употребление срабатывает только по `RIGHT_CLICK_AIR`.
2. Клик по сундуку, бочке, шалкеру, эндер-сундуку, котлу и другим interactable blocks не тратит предмет.
3. Работает cooldown.
4. Hidden overdose scale игроку нигде не показывается.
5. Три применения `chups` в окне безопасны, четвёртое вызывает передозировку.
6. После передозировки накопленный hidden scale сбрасывается в `0`.
7. `Жужево` всегда запускает передозировку.
8. `sbp` даёт инверсию движения только на время эффекта.
9. Во время активной передозировки молоко не снимает эффект, если опция включена.
10. `/cmnarcotics clearoverdose <player>` снимает инверсию и активные эффекты.

## Storage And Processing Guards

1. Готовый предмет можно держать в инвентаре игрока.
2. Готовый предмет можно класть в сундук.
3. Готовый предмет можно класть в бочку.
4. Готовый предмет можно класть в шалкер.
5. Готовый предмет можно класть в эндер-сундук.
6. Готовый предмет можно выбрасывать и подбирать обратно.
7. Готовый предмет нельзя класть в player crafting grid.
8. Готовый предмет нельзя класть в crafting table.
9. Готовый предмет нельзя класть в crafter или autocrafter.
10. Готовый предмет нельзя класть в furnace, smoker или blast furnace.
11. Готовый предмет нельзя класть в brewing stand.
12. Готовый предмет нельзя класть в smithing table, anvil, grindstone или stonecutter.
13. Готовый предмет нельзя отправить через hopper, dispenser или dropper в processing inventory.
14. Shift-click, hotbar swap, number key, collect-to-cursor и drag по верхним слотам не обходят блокировку.
15. Обычные ингредиенты этими ограничениями не ломаются.

## Visuals

1. По умолчанию `visuals.enabled=false`.
2. По умолчанию реальный runtime работает только в `FALLBACK`.
3. Без явной команды администратора overlay и shader не активируются.
4. Fallback-частицы и вспомогательные эффекты работают только во время употребления или передоза.
5. В обычной игре вне употребления и передоза ничего не висит.
6. Visual session очищается по таймеру.
7. Visual session очищается при выходе игрока.
8. Visual session очищается после смерти игрока.
9. Visual session очищается при выключении плагина.
10. Visual session очищается при смене мира.

## Resource Pack

1. В zip есть модели `feta`, `kola`, `girion`, `sbp`, `sos`, `drun`, `chups`, `borshevik`, `zhuzevo`.
2. В zip есть `narcotics_items_manifest.json`.
3. В zip есть `narcotics_visuals_manifest.json`.
4. В zip есть item textures в `assets/copimine/textures/item/narcotics/`.
5. В zip есть overlay textures в `assets/copimine/textures/gui/narcotics/`.
6. В zip есть shader placeholder json в `assets/copimine/shaders/narcotics/`.
7. В `server.properties` SHA1 совпадает с собранным zip.
8. URL ресурспака не изменился.
9. Ванильные текстуры глобально не переопределены.
10. Если overlay/shader runtime реально не поддержан, статус команд и документация это честно показывают как fallback-only.
