# Покрытие действий администрации v6

Реестр содержит 118 типизированных действий, из которых три выполняются в транзакции сайта. Дополнительно мост обрабатывает служебные inventory.refresh, purchase.deliver и sanction.revoke. Один закрытый обработчик доступен только через разрешённый административный API; публичная поставка определений содержит 117 записей.

Источник: `copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java`, commit `a16dc50002f3cde94a4f0e350a18fccd4e1c8bf5`. Полный JSON: data/admin-actions.json. Наличие формы означает поддержку заданного контракта; фактическое исполнение подтверждает квитанция игрового плагина. Администратор не обязан находиться в игре.

## Все действия

| ID | Действие | Область | Разрешение | Исполнение |
|---|---|---|---|---|
| ban | Блокировка игрока | Игрок | players.moderate | Игровой мост |
| mute | Ограничить общение | Игрок | players.moderate | Игровой мост |
| kick | Отключить от сервера | Игрок | players.moderate | Игровой мост |
| warn | Предупреждение | Игрок | players.moderate | Игровой мост |
| freeze | Заморозить игрока | Игрок | players.moderate | Игровой мост |
| unfreeze | Снять заморозку | Игрок | players.moderate | Игровой мост |
| jail | Заключить в тюрьму | Игрок | players.moderate | Игровой мост |
| unjail | Освободить | Игрок | players.moderate | Игровой мост |
| give | Выдать предмет | Игрок | inventory.manage | Игровой мост |
| inventory.remove | Изъять предмет из инвентаря | Игрок | inventory.manage | Игровой мост |
| ender.remove | Изъять предмет из эндер-сундука | Игрок | inventory.manage | Игровой мост |
| inventory.restore | Восстановить снимок | Игрок | inventory.manage | Игровой мост |
| heal | Вылечить | Игрок | players.manage | Игровой мост |
| feed | Восполнить сытость | Игрок | players.manage | Игровой мост |
| extinguish | Потушить | Игрок | players.manage | Игровой мост |
| effect.clear | Очистить эффекты | Игрок | players.manage | Игровой мост |
| tp.coords | Телепортировать по координатам | Игрок | players.manage | Игровой мост |
| tp.spawn | Телепортировать на спавн | Игрок | players.manage | Игровой мост |
| tp.bed | Вернуть к кровати | Игрок | players.manage | Игровой мост |
| gamemode | Изменить режим игры | Игрок | players.manage | Игровой мост |
| fly | Управлять полётом | Игрок | players.manage | Игровой мост |
| god | Управлять неуязвимостью | Игрок | players.manage | Игровой мост |
| money.adjust | Изменить баланс | Игрок | economy.manage | Транзакция сайта |
| purchase.limit | Настроить личный лимит покупок | Игрок | economy.manage | Транзакция сайта |
| sessions.revoke | Завершить сессии сайта | Игрок | sessions.manage | Транзакция сайта |
| fun.title | Показать заголовок | Игрок | players.fun | Игровой мост |
| fun.sound | Воспроизвести звук | Игрок | players.fun | Игровой мост |
| fun.firework | Запустить фейерверк | Игрок | players.fun | Игровой мост |
| fun.glow | Подсветить игрока | Игрок | players.fun | Игровой мост |
| fun.amethyst | Аметистовый звон | Игрок | players.fun | Игровой мост |
| fun.anvil | Звук наковальни | Игрок | players.fun | Игровой мост |
| inventory.armor-off | Снять броню | Игрок | inventory.manage | Игровой мост |
| fun.bee | Звук пчелы | Игрок | players.fun | Игровой мост |
| fun.bees | Пчёлы | Игрок | players.fun | Игровой мост |
| fun.blind | Слепота | Игрок | players.fun | Игровой мост |
| effects.burn5 | Поджечь на 5 секунд | Игрок | players.manage | Игровой мост |
| check-remind | Напомнить о проверке | Игрок | players.moderate | Игровой мост |
| check-stop | Завершить проверку | Игрок | players.moderate | Игровой мост |
| check-stop-return | Завершить проверку и вернуть игрока | Игрок | players.moderate | Игровой мост |
| fun.chickens | Курицы | Игрок | players.fun | Игровой мост |
| fun.chorus | Эффект хоруса | Игрок | players.fun | Игровой мост |
| inventory.clear | Очистить инвентарь | Игрок | inventory.manage | Игровой мост |
| fun.clock | Обратный отсчёт | Игрок | players.fun | Игровой мост |
| fun.cobweb | Паутина | Игрок | players.fun | Игровой мост |
| fun.confetti | Конфетти | Игрок | players.fun | Игровой мост |
| fun.confuse | Дезориентация | Игрок | players.fun | Игровой мост |
| fun.dark | Затемнение | Игрок | players.fun | Игровой мост |
| fun.explosion | Безопасный эффект взрыва | Игрок | players.fun | Игровой мост |
| fun.fakeban | Шуточный экран блокировки | Игрок | players.fun | Игровой мост |
| fun.fakecredits | Шуточные титры | Игрок | players.fun | Игровой мост |
| fun.fakeop | Шуточное сообщение об операторе | Игрок | players.fun | Игровой мост |
| fun.fireworkfake | Звук фейерверка | Игрок | players.fun | Игровой мост |
| fun.fortune | Печенье судьбы | Игрок | players.fun | Игровой мост |
| fun.fsb | Шуточная проверка | Игрок | players.fun | Игровой мост |
| effects.god10 | Защитные эффекты на 10 секунд | Игрок | players.manage | Игровой мост |
| fun.horn | Козий рог | Игрок | players.fun | Игровой мост |
| inventory.lock | Временно закрыть инвентарь | Игрок | inventory.manage | Игровой мост |
| ban.ip | Заблокировать игровой IP | Игрок | players.manage | Игровой мост |
| fun.jumpscare | Внезапный звук | Игрок | players.fun | Игровой мост |
| fun.knockback | Оттолкнуть | Игрок | players.fun | Игровой мост |
| fun.launch | Подбросить | Игрок | players.fun | Игровой мост |
| fun.levitate | Левитация | Игрок | players.fun | Игровой мост |
| fun.lightning | Эффект молнии | Игрок | players.fun | Игровой мост |
| player.message | Напомнить о правилах | Игрок | players.manage | Игровой мост |
| fun.nausea | Тошнота | Игрок | players.fun | Игровой мост |
| nearby | Сведения об окружении | Игрок | players.manage | Игровой мост |
| fun.note | Ноты | Игрок | players.fun | Игровой мост |
| fun.panic | Сигнал тревоги | Игрок | players.fun | Игровой мост |
| fun.paperwork | Шуточная форма | Игрок | players.fun | Игровой мост |
| fun.phantom | Звук фантома | Игрок | players.fun | Игровой мост |
| fun.potato | Выдать шуточную картофелину | Игрок | players.fun | Игровой мост |
| fun.powdersnow | Рыхлый снег | Игрок | players.fun | Игровой мост |
| fun.pumpkin | Надеть тыкву | Игрок | players.fun | Игровой мост |
| fun.rainbow | Радужный заголовок | Игрок | players.fun | Игровой мост |
| tp.random | Случайно переместить рядом | Игрок | players.manage | Игровой мост |
| inventory.repair-hand | Починить предмет в руке | Игрок | inventory.manage | Игровой мост |
| fun.scare | Пугающий звук | Игрок | players.fun | Игровой мост |
| inventory.shuffle-hotbar | Перемешать панель быстрого доступа | Игрок | inventory.manage | Игровой мост |
| fun.slime | Прыжок слизи | Игрок | players.fun | Игровой мост |
| effects.slow10 | Замедлить на 10 секунд | Игрок | players.manage | Игровой мост |
| inventory.snapshot | Получить снимок инвентаря | Игрок | inventory.manage | Игровой мост |
| fun.sneeze | Чихание панды | Игрок | players.fun | Игровой мост |
| fun.snowball | Снежок | Игрок | players.fun | Игровой мост |
| fun.snowcloud | Снежное облако | Игрок | players.fun | Игровой мост |
| fun.sparkle | Искры | Игрок | players.fun | Игровой мост |
| fun.spin | Повернуть игрока | Игрок | players.fun | Игровой мост |
| fun.spy | Звук подзорной трубы | Игрок | players.fun | Игровой мост |
| fun.squid | Эффект спрута | Игрок | players.fun | Игровой мост |
| fun.taxpaper | Шуточная налоговая бумага | Игрок | players.fun | Игровой мост |
| fun.tinyquest | Мини-задание | Игрок | players.fun | Игровой мост |
| fun.totem | Эффект тотема | Игрок | players.fun | Игровой мост |
| tp.up10 | Переместить на 10 блоков вверх | Игрок | players.manage | Игровой мост |
| fun.warden | Звук вардена | Игрок | players.fun | Игровой мост |
| fun.wind | Порыв ветра | Игрок | players.fun | Игровой мост |
| db.optimize | Обслуживание игровой базы данных | Сервер | server.manage | Игровой мост |
| db.checkpoint | Контрольная точка игровой базы | Сервер | server.manage | Игровой мост |
| players.snapshot-all | Снимки всех игроков | Сервер | server.manage | Игровой мост |
| players.heal-all | Вылечить всех игроков | Сервер | server.manage | Игровой мост |
| players.feed-all | Накормить всех игроков | Сервер | server.manage | Игровой мост |
| players.unfreeze-all | Снять заморозку со всех | Сервер | server.manage | Игровой мост |
| players.remind-checks | Напомнить о проверках всем | Сервер | server.manage | Игровой мост |
| players.save-all | Сохранить всех игроков | Сервер | server.manage | Игровой мост |
| players.night-vision-all | Ночное зрение всем | Сервер | server.manage | Игровой мост |
| players.clear-negative-effects-all | Снять отрицательные эффекты у всех | Сервер | server.manage | Игровой мост |
| world.reset-objects | Сбросить реестр мировых объектов | Сервер | users.manage | Игровой мост |
| world.clear-floating-texts | Очистить связанные плавающие надписи | Сервер | users.manage | Игровой мост |
| game.money.credit | Начислить игровой баланс (EconomyCore) | Игрок | economy.manage | Игровой мост |
| game.audit.ar | Аудит экономики игрового сервера | Сервер | audit.read | Игровой мост |
| game.audit.elections | Аудит выборов игрового сервера | Сервер | audit.read | Игровой мост |
| game.audit.restricted | Закрытый аудит игрового плагина | Сервер | server.manage | Игровой мост |
| rpguard.status | Состояние ограничений команд | Сервер | audit.read | Игровой мост |
| rpguard.test | Проверить ограничение команды | Сервер | server.manage | Игровой мост |
| legacy-votes.status | Состояние прежних выборов | Сервер | audit.read | Игровой мост |
| legacy-votes.reset-sidebar | Убрать прежние панели выборов | Сервер | server.manage | Игровой мост |
| legacy-votes.clear-glow | Убрать подсветку игроков | Сервер | server.manage | Игровой мост |
| legacy-votes.unlock | Закрыть и разблокировать прежние выборы | Сервер | users.manage | Игровой мост |
| election-seal.status | Состояние защиты печати | Сервер | audit.read | Игровой мост |
| game.money.adjust | Изменить игровой баланс (EconomyCore) | Игрок | economy.manage | Игровой мост |

## Исключения и замены

| Исходный обработчик | Причина |
|---|---|
| tpto | Телепортация к администратору требует его присутствия. |
| tphere | Нужны координаты администратора в игре. |
| swap | Обмен местами включает самого администратора. |
| check-start | Проверку с участием администратора начинают в игре. |
| arsync | Старый обработчик перенесён в EconomyCore; на сайте есть отдельные действия экономики. |
| givebook | Старое меню делегирует действие ElectionCore и не содержит действующего обработчика. |
| giveballot | Старое меню делегирует действие ElectionCore и не содержит действующего обработчика. |
| inv | Игровое меню заменено снимком и действиями над слотами на сайте. |
| ender | Игровое меню заменено эндер-сундуком и действиями над слотами на сайте. |

## Подтверждение и ограничения

Сроки и отмена ограничений хранятся на сервере. Пока плагин не подтвердил применение, интерфейс показывает ожидание. Изменение роли, баланса, лимита, слота и блокировки требует полномочий, причины и свежего подтверждения входа. Выдача идёт по проверенному UUID; API не принимает свободный текст команды.
