# CopiMine Full Post-Narcotics Smoke Checklist

## Auth
- Зайти на сервер с `nLogin` и проверить обычный `/register`, затем `/login`.
- Убедиться, что `AuthEffects` даёт замедление до входа и снимает его сразу после входа или регистрации.
- Проверить, что `nLogin` не ломает whitelist, OP и обычный join flow.

## CopiMineClient
- Установить `CopiMineClient-0.1.0.jar` в клиент.
- Зайти на сервер с модом и проверить `/copimineclient status`.
- Проверить повторные `hello`-попытки и итоговый handshake.
- Выполнить `/copimineclient visual test GREEN_NOISE 10`.
- Проверить поведение с `F1` и `render_when_hud_hidden=true/false`.
- Зайти без `CopiMineClient` и убедиться, что серверный fallback остаётся рабочим.
- Проверить, что handshake с неправильной версией протокола отклоняется.

## Миры
- Выполнить `/cmworld status`.
- Проверить, что Overworld border = `10000`.
- Проверить, что `/cmworld status` показывает активные `world_names` для Overworld.
- Если `world_limits.overworld.world_names` пустой, убедиться, что fallback всё равно выбрал корректный Overworld.
- Попробовать телепортироваться за границу мира и убедиться, что телепорт отклоняется.
- Попробовать выйти за границу пешком или полётом и убедиться, что игрок возвращается внутрь.
- Проверить `/cmworld nether status`, затем `open`, затем `close`.
- Проверить `/cmworld end status`, затем `open`, затем `close`.
- Проверить закрытие Nether и End с игроком внутри через GUI confirm screen.
- Открыть `AdminPlus -> Миры` и проверить, что делегация в `CopiMineWorldCore` работает.
- Проверить лог: при отсутствии безопасной точки `WorldCore` не телепортирует в лаву, void или портал.

## Economy / Donation
- Поставить ATM и открыть личный счёт.
- Проверить PIN-панель: цифры стоят сеткой `123 / 456 / 789 / 0`, кнопка стирания работает, после ввода первой цифры GUI не закрывается.
- Проверить снятие `1 / 16 / 64 AR`.
- Во время снятия AR отключиться до завершения callback и потом зайти снова: AR не теряется, а доходит через pending delivery.
- Во время внесения AR в банк отключиться до завершения callback при искусственной ошибке БД или отказе операции: AR не теряется, а возвращается через pending recovery.
- Открыть ATM после перезахода и убедиться, что отложенный официальный AR доходит автоматически, если есть место.
- Проверить, что AR-баланс не меняется от `donation add/test purchase`.
- Через admin-web создать `add-balance` с confirm header.
- Убедиться, что в `donation_accounts` и `donation_balance_ledger` появились записи.
- Через admin-web создать `test purchase`.
- Убедиться, что появились записи в `donation_purchases` и `donation_item_claims`.
- Проверить, что `POST /api/player/donation/create-session` честно отвечает `development-disabled`.
- Проверить, что `POST /api/player/donation/claim` не теряет claim и не делает ложную выдачу.
- Проверить, что claim с неизвестным `item_id` не создаётся.

## Artifacts
- Открыть лавку артефактов.
- Проверить, что обычные AR-покупки работают как раньше.
- Проверить, что pending delivery не теряет предметы.
- Создать test donation claim и выполнить `/cmartifacts claim`.
- Убедиться, что донатный claim создаёт `artifact_item_instances`.
- Проверить, что полученный донатный предмет не считается suspicious и проходит обычные проверки PDC/ремонта/эффекта.
- Проверить, что claim amount больше доступных слотов не режется молча и не закрывается полностью.
- Проверить, что при сбое завершения выдачи claim не возвращается автоматически в `UNCLAIMED`, а уходит в `DELIVERY_REVIEW`.
- Проверить, что visual лавки остаётся на месте после reload/chunk reload.

## Resource Pack / Visuals
- Проверить, что resource pack всё ещё скачивается.
- Проверить, что `CopiMineResourcePack.zip` и `server.properties` содержат одинаковый `resource-pack-sha1`.
- Проверить, что audit смотрит фактическое содержимое `CopiMineResourcePack.zip`, а не только `src`.
- Проверить, что title-based overlay fallback документирован и очищается на quit/death/world change.

## Website / Admin
- Выполнить `python -m py_compile backend/main.py backend/discord_bot.py`, если web менялся.
- Проверить логин и кабинет игрока.
- Проверить `/api/player/donation/balance`, `/history`, `/items`.
- Проверить `/api/admin/donation/sessions`, `/add-balance`, `/test-purchase`.
- Проверить, что реальные SBP-вызовы отсутствуют и UI показывает development state.

## Discord
- Проверить, что бот стартует без ошибок.
- Проверить, что legacy fallback не включён.
- Проверить, что нет публикации narcotics use logs.
- Проверить, что нет утечки `voter -> candidate`.

## Regression
- Проверить, что `CopiMineNarcotics` продолжает загружаться.
- Проверить обычный котёл без full-water + fire + netherrack: он ведёт себя как обычный котёл и не принимает narcotics-варку.
- Проверить правильный rig: полный `WATER_CAULDRON`, под ним `FIRE`, под огнём `NETHERRACK`.
- Проверить, что ингредиенты начинают висеть над котлом после добавления.
- Проверить, что точный рецепт выдаёт нужный наркотик, а неправильная смесь выдаёт `zhuzevo`.
- Проверить, что после завершения варки вода и огонь исчезают.
- Проверить, что если потушить огонь под активным котлом, состояние и висящие ингредиенты очищаются.
- Проверить overdose: всегда есть darkness, weakness, instant damage, nausea III, mining fatigue на 5 минут и случайный visual.
- Проверить, что `zhuzevo` всегда даёт nausea и случайный visual/микс, но не раскрывает игроку скрытую механику в чат.
- Проверить, что выборы и экономические AR-счета не очищены.
- Проверить TAB header/footer и баннер `CopiMine`.
- Проверить, что в активных hub/ATM/election меню нет битого русского текста.
