/* Navigation search only: typing can open/highlight a screen, NEVER execute an action. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,A=CM71,W=CM5,E=M.esc,D=CM73Domain,H=M.handlers;
const queryKey='copimine.admin.search.query.v1';
let query=(()=>{try{return sessionStorage.getItem(queryKey)||'';}catch{return '';}})(),timer=0,keepFocus=false,lastUrl='',matches=[],notice='';
const aliases={
 overview:'обзор;главная;рабочий стол;сводка;состояние проекта;итоги;начало;dashboard;home;summary;overview;старт',
 statistics:'аналитика;статистика;посещения;посетители;популярный предмет;самый популярный товар;воронка;конверсия;продажи;популярность;отчеты;отчёты;метрики;графики;динамика;аналитика сайта;сколько продали;трафик;analytics;conversion;sales;stats',
 performance:'tps;mspt;тпс;мспт;память;оперативная память;ram;cpu;производительность;лаг;лаги;нагрузка;время тика;ресурсы сервера;частота кадров сервера;тормозит;почему лагает;performance',
 monitor:'мониторинг;состояние сервера;сервер;tps;mspt;память;оперативная память;ram;cpu;нагрузка;лаг;лаги;миры;чанки;entities;сущности;постгрес;postgres;база;бд;ошибки плагинов;перезапуски;аптайм;тачки;uptime;monitoring;performance;игровой сервер;здоровье сервера;онлайн;диск;таблица миров;падения;краши;restarts;errors',
 releases:'релизы;релиз;обновление;обновления;деплой;deploy;выкатить;выкатка;откат;rollback;версии плагинов;плагины;plugins;jar;коммит;commit;github;гитхаб;ci;сборка;совместимость;установка;установить;бэкап;резервная копия;заготовка;стейджинг;staging;sha256;чексумма;новая версия;update;release',
 players:'игрок;игроки;аккаунты;ник;никнейм;профиль игрока;uuid;учетные записи;учётные записи;users;player;players;поиск игрока;найти игрока;кто играет;список игроков;карточка игрока;баланс игрока;игровой ник',
 recovery:'восстановление;потерял доступ;восстановить аккаунт;восстановить доступ;recovery;recover;забыл пароль;проблемы со входом;не могу войти;сброс доступа;помочь игроку;вернуть аккаунт',
 requests:'обращения;репорты;заявки;жалобы;тикеты;тикет;поддержка;вопросы игроков;апелляции;appeal;support;tickets;report;инциденты;проблемы с покупкой;помощь',
 moderation:'модерация;наказания;сроки наказаний;активные баны;нарушения;ограничения;бан лист;разбан;санкции;punishments;bans;unban;кто в бане;забаненные;предупреждения;муты',
 inventories:'инвентари;инвентарь;снимок инвентаря;ячейки;слоты;содержимое сумки;снаряжение;броня;левая рука;хотбар;inventory;slots;armor;hotbar;что в руках;эндер сундук;enderchest',
 investigations:'расследования;coreprotect;корпротект;проверка действий;кто сломал;кто поставил;грифер;гриферство;история блоков;проверка построек;жалоба на грифера;история изменений блоков',
 anticheat:'античит;grim;grimac;чит;читы;нарушения движения;проверки античита;anticheat;cheat;alerts;софт;подозрения',
 whitelist:'вайтлист;белый список;доступ на сервер;разрешенные игроки;пропуск;whitelist;allowlist;кого пускать',
 bank:'банк;экономика;ар;ar;счета;счета игроков;игровой баланс;игровые деньги;банковские счета;bank;economy;money;accounts;валюта;казна',
 shop:'каталог;магазин;предметы;товары;артефакты;цены;цена предмета;лимиты;тираж;лимит покупок;catalog;shop;items;price;supply;добавить товар;скрыть предмет;текстура предмета',
 finance:'покупки;заказы;донаты;платежи;транзакции;возвраты;чеки;квитанции;финансы;балансы сайта;orders;payments;refund;donation;ledger;выдача;очередь выдачи;оплата;пополнение',
 elections:'выборы;кандидат;кандидаты;президент;дебаты;цик;голосование;голоса;итоги;срок президента;election;votes;кампания;бюллетень',
 laws:'законы;закон;налог;налоги;указы;постановления;конституция;казна;treasury;laws;tax;штраф;правила экономики',
 world:'карта;карты;миры;обычный мир;незер;энд;край;обновить карту;таймер карты;частота обновления;маркеры;иконки игроков;map;world;nether;end;зоны;территории;снимок карты',
 points:'точки;метки;банкоматы;банкомат;лавки;торговые точки;координаты лавки;места;atm;waypoints;markers;точки интереса',
 recipes:'рецепты;скрытые рецепты;котел;ингредиенты;варка;формулы;смеси;книги рецептов;recipes;brewing;cauldron;крафт',
 system:'службы;сервер;запуск сервера;стоп сервер;выключить сервер;остановить сервер;перезапуск;рестарт;systemd;rcon;server;start;stop;restart;служебные команды;состояние служб;подключения',
 logs:'логи;лог;журнал сервера;консоль;ошибки;предупреждения;стектрейс;stacktrace;console;logs;error;warning;что упало',
 audit:'аудит;история действий;журнал действий;кто изменил;кто выдал;действия команды;оператор;audit;history;лог админов;кто что делал',
 sources:'источники;плагины;версии плагинов;сборки;jar;реестр;источник каталога;синхронизация;конфигурации;plugins;registry;sources;откуда данные',
 jobs:'очередь;задания;задачи;выдача;ожидает выдачи;повтор задачи;воркер;worker;queue;jobs;delivery;фоновая задача;зависла выдача',
 backups:'резервные копии;бэкап;бекап;копия базы;сохранить копию;backup;restore backup;экспорт проекта;дамп;pg_dump;восстановить базу',
 admins:'команда;администраторы;админы;владелец;сотрудники;доступ команды;персонал;owner;staff;admins;roles;кто имеет доступ;права команды;модераторы',
 security:'безопасность;права;доступ;сессии;сеансы;cookie;куки;csrf;totp;2fa;mfa;матрица прав;permissions;security;защита аккаунта;входы;история входов;блокировка входа',
 content:'контент;страницы;галерея;скриншоты;фото;картинки;публикации;баннер;фон;вопросы ответы;faq;cms;content;gallery;screenshots;текст главной;редактор сайта',
 settings:'настройки;параметры;название сайта;ip;адрес сервера;версия minecraft;settings;config;preferences;домен;логотип',
 maintenance:'техработы;технические работы;обслуживание;закрыть сайт;открыть сайт;ремонт сайта;maintenance;offline mode;заглушка',
 mapart:'картины;мэп-арт;mapart;картина в мире;загрузка картин;авторы картин;координаты картины;пиксель-арт'
};
const tabsAliases={profile:'профиль;карточка;основное;инвентари;баланс;об игроке',actions:'действия;команды;выдать;наказать;что можно сделать',sanctions:'наказания;баны;муты;ограничения;санкции;сроки',finance:'покупки;покупки и донаты;платежи;заказы;лимиты;баланс',activity:'история;активность;журнал игрока;что с ним делали',messages:'чат;сообщения;переписка;что писал;чат игрока;история чата;логи чата;команды игрока;какие команды вводил;логи команд;чат и команды;прочитать чат;скачать сообщения',logins:'входы;история входов;логины;попытки входа;ip;устройства;кто заходил',access:'доступ;роль;права;двойная защита;сессии'};
// Вкладки внутри разделов: [раздел, sub, название, алиасы].
const subtabs=[
 ['statistics','overview','Статистика · Обзор','обзор статистики;сводка аналитики;главные цифры'],
 ['statistics','audience','Статистика · Аудитория','аудитория;посетители;уникальные;новые и вернувшиеся'],
 ['statistics','sources','Статистика · Источники','источники трафика;откуда пришли;referrer;реферал'],
 ['statistics','pages','Статистика · Страницы','страницы;просмотры страниц;популярные страницы'],
 ['statistics','events','Статистика · События','события;действия на сайте;event'],
 ['statistics','consents','Статистика · Согласия','согласия;куки;cookie consent;политика'],
 ['statistics','economy','Статистика · Экономика','экономика сайта;продажи;выручка;доход'],
 ['players','list','Все игроки','список игроков;каталог игроков;таблица игроков;поиск ника'],
 ['shop','catalog','Каталог предметов','товары;список предметов;цены;витрина'],
 ['shop','materials','Материалы Minecraft','материалы;предметы minecraft;id предметов;список материалов'],
 ['shop','delivery','Выдача покупок','выдача;очередь выдачи;что выдать;доставка покупок'],
 ['finance','accounts','Счета','счета;балансы игроков;кошельки;лицевые счета'],
 ['finance','orders','Заказы','заказы;заявки на покупку;очередь заказов'],
 ['finance','payments','Платежи','платежи;оплаты;транзакции;поступления'],
 ['finance','ledger','Проводки','проводки;леджер;движение денег;история операций'],
 ['world','map','Карта','карта;bluemap;миры на карте;маркеры'],
 ['world','events','События мира','события;энд-ивент;мирные события;расписание'],
 ['world','elections','Выборы','выборы;кандидаты;голосование;президент'],
 ['world','laws','Законы','законы;указы;налоги;конституция'],
 ['world','points','Точки','точки;банкоматы;лавки;метки;waypoints'],
 ['content','pages','Страницы сайта','страницы;редактор страниц;cms;черновики'],
 ['content','faq','Вопросы и ответы','faq;вопросы;ответы;частые вопросы'],
 ['content','gallery','Галерея','галерея;скриншоты;фото;карусель;кадры'],
 ['content','settings','Настройки и ссылки','настройки контента;ссылки;футер;меню'],
 ['content','navigation','Навигация','навигация;меню сайта;пункты меню'],
 ['content','downloads','Загрузки','загрузки;моды;лаунчер;файлы;сборка'],
 ['admins','team','Команда','команда;сотрудники;персонал;состав'],
 ['admins','roles','Роли и права','роли;права;матрица прав;permissions'],
 ['admins','audit','Журнал','журнал;аудит;кто что делал;история действий'],
 ['admins','easter','Пасхалки','пасхалки;easter eggs;секреты'],
 ['system','status','Состояние сервера','состояние;сервер;статус;здоровье'],
 ['system','logs','Логи','логи;логи сервера;консоль;ошибки'],
 ['system','connections','Подключения','подключения;кто подключён;ссылки;сервисы'],
 ['moderation','sanctions','Санкции','санкции;наказания;баны;муты'],
 ['recipes','editor','Рецепты · Редактор','рецепты;скрытые рецепты;редактор рецептов'],
 ['recipes','events','Рецепты · События','события рецептов;ивенты рецептов']
];
const special=[
 ['pin.reset','Сбросить PIN','recovery','пин;pin;сброс пин;сбросить пин;сброс pin;сбросить pin;забыл пин;банковский пин;код банка;reset pin;bank pin'],
 ['password.reset','Сбросить пароль сайта','recovery','пароль;сброс пароля;сбросить пароль;новый пароль;пароль сайта;отозвать пароль;reset password;password reset;forgot password;забыл пароль'],
 ['sessions.revoke','Завершить сеансы','recovery','отозвать сеансы;завершить сеансы;выйти везде;все сессии;разлогинить;сброс сессий;украли куки;logout all;revoke sessions;разлогинить везде'],
 ['login.unlock','Снять блокировку попыток','recovery','попытки входа;слишком много попыток;разблокировать вход;сброс попыток;снять ограничение входа;rate limit;login unlock;не пускает на сайт'],
 ['mfa.reset','Сбросить второй фактор','recovery','сброс totp;сброс 2fa;потерял телефон;аутентификатор;сброс второго фактора;reset mfa;коды не подходят'],
 ['minecraft.relink','Проверить привязку Minecraft','recovery','перепривязка;потерял майнкрафт;привязка майнкрафт;смена uuid;игровой аккаунт;relink minecraft;ник не привязан'],
 ['private','Закрытые предметы','shop?scope=private','скрытые;скрытые предметы;закрытые;закрытые предметы;админские предметы;награды;hidden items;private items'],
 ['transferFeePercent','Комиссия переводов','bank?sub=transfers','комиссия;комиссия перевода;процент перевода;transfer fee;fee;налог на перевод'],
 ['gallery','Галерея проекта','content?sub=gallery','фотографии;загрузить фото;добавить скриншот;добавить фото;галерея проекта;порядок кадров;карусель;carousel;gallery;кадры'],
 ['logins.all','История входов игрока','players?sub=logins','входы;логины;попытки входа;кто заходил в аккаунт;ip входа;устройства входа;история входов']
];
const actionAliases={
 'ban':'бан;забанить;бан игрока;заблокировать игрока;ban player;блокировка;заблокировать аккаунт',
 'mute':'мут;замутить;заглушить;отключить чат;выдать мут;mute;ограничить общение;запрет чата',
 'kick':'кик;кикнуть;выгнать;отключить от сервера;выкинуть из игры;kick;отключить игрока',
 'warn':'варн;предупреждение;выдать предупреждение;выговор;warn;warning;предупредить',
 'freeze':'заморозка;заморозить;фриз;обездвижить;freeze;задержать игрока',
 'unfreeze':'разморозить;снять заморозку;unfreeze;отпустить игрока',
 'jail':'тюрьма;посадить в тюрьму;заключить;jail;арест',
 'unjail':'освободить из тюрьмы;выпустить;unjail;снять тюрьму',
 'give':'выдать;выдача предмета;дать предмет;выдать предмет;give item;предмет игроку;положить в инвентарь;подкинуть предмет',
 'inventory.remove':'изъять предмет;убрать предмет из инвентаря;удалить предмет;забрать предмет;remove item;очистить ячейку;изъять из сумки',
 'ender.remove':'эндер сундук;эндерсундук;изъять из эндер сундука;ender chest;enderchest;забрать из сундука',
 'inventory.restore':'восстановить инвентарь;откат инвентаря;вернуть вещи;restore inventory;снимок назад',
 'heal':'хил;вылечить;здоровье;восстановить здоровье;heal;полное здоровье',
 'feed':'еда;голод;накормить;сытость;feed;восполнить голод',
 'extinguish':'потушить;снять огонь;extinguish;горит',
 'effect.clear':'убрать эффекты;очистить эффекты;снять зелья;clear effects;снять бафы',
 'tp.coords':'тп;телепорт;телепортация;тп координаты;телепорт на координаты;tp coords;teleport;переместить на координаты',
 'tp.spawn':'спавн;тп на спавн;вернуть на спавн;spawn;телепорт на спавн',
 'tp.bed':'кровать;возрождение;тп кровать;точка возрождения;respawn;bed;вернуть к кровати',
 'tp.random':'случайный телепорт;тп рядом;переместить случайно;tp random;рандомный тп',
 'tp.up10':'поднять на 10 блоков;тп вверх;tp up',
 'gamemode':'режим игры;гамемод;креатив;выживание;наблюдатель;приключение;gamemode;сменить режим',
 'fly':'полёт;флай;fly;разрешить полёт;включить полёт',
 'god':'неуязвимость;годмод;god;бессмертие;включить год',
 'money.adjust':'баланс;изменить баланс;выдать ар;снять ар;начислить;деньги игрока;set balance;поправить баланс;баланс сайта',
 'purchase.limit':'личный лимит;лимит игрока;ограничить покупки;лимит предмета;purchase limit;лимит покупок',
 'sessions.revoke':'завершить сессии;разлогинить;выйти везде;revoke sessions;сброс сеансов',
 'fun.title':'заголовок;титр;сообщение на экране;title;показать текст',
 'fun.sound':'звук;проиграть звук;sound;воспроизвести звук',
 'fun.firework':'фейерверк;салют;firework;запустить фейерверк',
 'fun.glow':'подсветить;свечение;glow;подсветка игрока',
 'fun.amethyst':'аметист;звон аметиста;amethyst',
 'fun.anvil':'наковальня;звук наковальни;anvil',
 'inventory.armor-off':'снять броню;убрать броню;armor off;раздеть',
 'fun.bee':'пчела;звук пчелы;bee',
 'fun.bees':'пчёлы;рой;bees',
 'fun.blind':'слепота;ослепить;blind;не видит',
 'effects.burn5':'поджечь;огонь;гореть 5 секунд;burn;подпалить',
 'check-remind':'напомнить о проверке;проверка;напоминание проверки;check remind;ss;скриншот проверка',
 'check-stop':'завершить проверку;закончить проверку;check stop',
 'check-stop-return':'завершить проверку и вернуть;вернуть после проверки;check return',
 'fun.chickens':'курицы;куры;chickens;курятник',
 'fun.chorus':'хорус;эффект хоруса;chorus;телепорт хоруса',
 'inventory.clear':'очистить инвентарь;стереть инвентарь;clear inventory;обнулить вещи',
 'fun.clock':'отсчёт;таймер;clock;обратный отсчёт',
 'fun.cobweb':'паутина;cobweb;в паутине',
 'fun.confetti':'конфетти;confetti;праздник',
 'fun.confuse':'дезориентация;confuse;спутать',
 'fun.dark':'затемнение;темнота;dark;слепота экрана',
 'fun.explosion':'взрыв;эффект взрыва;explosion;бум',
 'fun.fakeban':'шуточный бан;фейковый бан;fakeban;экран блокировки',
 'fun.fakecredits':'титры;шуточные титры;credits',
 'fun.fakeop':'шуточный оператор;fakeop;выдал оператора',
 'fun.fireworkfake':'звук фейерверка;firework sound',
 'fun.fortune':'печенье судьбы;предсказание;fortune;печенька',
 'fun.fsb':'шуточная проверка;фсб;fsb',
 'effects.god10':'защитные эффекты;год на 10 секунд;god10;резист',
 'fun.horn':'козий рог;рог;horn',
 'inventory.lock':'закрыть инвентарь;блокировка инвентаря;lock inventory;запрет инвентаря',
 'ban.ip':'бан ip;заблокировать ip;ban ip;айпи бан',
 'fun.jumpscare':'скример;внезапный звук;jumpscare;испугать',
 'fun.knockback':'оттолкнуть;knockback;толчок',
 'fun.launch':'подбросить;launch;запустить вверх',
 'fun.levitate':'левитация;levitate;поднять',
 'fun.lightning':'молния;lightning;удар молнии',
 'player.message':'напомнить о правилах;сообщение игроку;message;предупреждение в чат',
 'fun.nausea':'тошнота;nausea;мутит',
 'nearby':'окружение;сведения об окружении;nearby;кто рядом;что вокруг',
 'fun.note':'ноты;note;мелодия',
 'fun.panic':'тревога;сигнал тревоги;panic;sos',
 'fun.paperwork':'форма;шуточная форма;paperwork;бумажка',
 'fun.phantom':'фантом;звук фантома;phantom',
 'fun.potato':'картофелина;шуточная картошка;potato',
 'fun.powdersnow':'рыхлый снег;порошковый снег;powder snow',
 'fun.pumpkin':'тыква;надеть тыкву;pumpkin',
 'fun.rainbow':'радуга;радужный заголовок;rainbow',
 'inventory.repair-hand':'починить предмет;ремонт в руке;repair;починить инструмент',
 'fun.scare':'пугающий звук;scare;страшилка',
 'inventory.shuffle-hotbar':'перемешать хотбар;перемешать панель;shuffle hotbar;бардак в инвентаре',
 'fun.slime':'слизь;прыжок слизи;slime',
 'effects.slow10':'замедлить;замедление;slow;слоу на 10 секунд',
 'inventory.snapshot':'снимок инвентаря;обновить снимки;snapshot;получить инвентарь',
 'fun.sneeze':'чихание;чих;sneeze',
 'fun.snowball':'снежок;snowball;кинуть снежок',
 'fun.snowcloud':'снежное облако;snow cloud',
 'fun.sparkle':'искры;sparkle;блеск',
 'fun.spin':'повернуть;spin;крутануть игрока',
 'fun.spy':'подзорная труба;spy;звук трубы',
 'fun.squid':'спрут;squid;кальмар',
 'fun.taxpaper':'налоговая бумага;taxpaper;квитанция шутка',
 'fun.tinyquest':'мини-задание;квест;tinyquest',
 'fun.totem':'тотем;эффект тотема;totem',
 'fun.warden':'варден;звук вардена;warden',
 'fun.wind':'ветер;порыв ветра;wind',
 'db.optimize':'оптимизация базы;обслуживание базы;db optimize;вакуум',
 'db.checkpoint':'контрольная точка базы;checkpoint;чекпоинт',
 'players.snapshot-all':'снимки всех;снимки всех игроков;snapshot all',
 'players.heal-all':'вылечить всех;heal all;хил всех',
 'players.feed-all':'накормить всех;feed all;еда всем',
 'players.unfreeze-all':'разморозить всех;unfreeze all',
 'players.remind-checks':'напомнить о проверках всем;remind all',
 'players.save-all':'сохранить всех;save all;сейв всех',
 'players.night-vision-all':'ночное зрение всем;night vision all;нв всем',
 'players.clear-negative-effects-all':'снять отрицательные эффекты у всех;clear effects all;очистить дебафы всем',
 'world.reset-objects':'сбросить реестр объектов;reset objects;сброс объектов мира',
 'world.clear-floating-texts':'очистить надписи;floating texts;убрать голограммы',
 'game.money.credit':'начислить игровой баланс;economycore credit;игровые деньги начислить',
 'game.audit.ar':'аудит экономики;audit ar;проверка экономики',
 'game.audit.elections':'аудит выборов;audit elections;проверка выборов',
 'rpguard.status':'состояние ограничений команд;rpguard;ограничения команд',
 'rpguard.test':'проверить ограничение команды;rpguard test',
 'legacy-votes.status':'состояние прежних выборов;legacy votes;старые выборы',
 'legacy-votes.reset-sidebar':'убрать прежние панели выборов;reset sidebar;старая панель',
 'legacy-votes.clear-glow':'убрать подсветку игроков;clear glow;снять свечение',
 'legacy-votes.unlock':'разблокировать прежние выборы;legacy unlock',
 'election-seal.status':'состояние защиты печати;election seal;печать выборов',
 'game.money.adjust':'изменить игровой баланс;economycore adjust;правка игрового баланса'
};
function entries(){
 const target=X.player()?.id||'';
 const rows=A.adminEntries.map(e=>({id:e[0],title:e[1],url:'#admin/'+e[0],aliases:[e[1],...(aliases[e[0]]||'').split(';')],icon:e[3]}));
 for(const [id,title,route,a]of special)rows.push({id,title,url:'#admin/'+route,aliases:[title,...a.split(';')],icon:'search'});
 for(const [id,a]of Object.entries(tabsAliases))rows.push({id:'tab.'+id,title:'Игрок · '+a.split(';')[0],url:'#admin/players?player='+encodeURIComponent(target)+'&sub='+id,aliases:['игрок '+a.split(';')[0],...a.split(';')],icon:'users'});
 for(const [area,sub,title,a]of subtabs)rows.push({id:area+'.'+sub,title,url:'#admin/'+area+'?sub='+sub,aliases:[title,...a.split(';')],icon:'chevron-right'});
 for(const a of X.actions.filter(a=>a.scope!=='server'))rows.push({id:a.id,title:a.label,url:'#admin/players?player='+encodeURIComponent(target)+'&sub=actions',aliases:[a.label,a.name||'',a.id,...(actionAliases[a.id]||'').split(';')],icon:a.icon||'sliders'});
 for(const p of M.state.players)rows.push({id:p.id,title:p.displayName||p.name,url:'#admin/players?player='+encodeURIComponent(p.id),aliases:[p.name,p.displayName||'',p.minecraftName||'',p.minecraftUuid||'',p.id],icon:'user',avatar:W.head(p,28)});
 for(const p of M.state.catalog)rows.push({id:p.id,title:p.name,url:'#admin/shop?scope='+(p.visibility==='public'?'public':'private'),aliases:[p.name,p.id,p.adminSubtitle||'',...(p.aliases||[])],icon:'package'});
 return rows;
}
function search(q){return entries().map(e=>({...e,score:D.rank(q,e.aliases)})).filter(e=>Number.isFinite(e.score)).sort((a,b)=>a.score-b.score).filter((e,i,rows)=>rows.findIndex(x=>x.id===e.id&&x.url===e.url)===i).slice(0,5);}
function status(){const box=document.getElementById('cm73-search-results');if(!box)return;box.hidden=!query.trim();box.innerHTML=notice?`<p role="status">${E(notice)}</p>`:matches.length?matches.map((m,i)=>`<button type="button" data-action="v73-search-go" data-index="${i}">${m.avatar||icon(m.icon)}<span>${E(m.title)}</span>${icon('arrow-right')}</button>`).join(''):`<p role="status">Совпадений нет. Попробуй название функции или ник.</p>`;}
function go(row){if(!row)return;const url=row.url+(row.url.includes('?')?'&':'?')+'focus='+encodeURIComponent(row.id);lastUrl=url;notice='Открыто: '+row.title;M.navTo(url.slice(1));status();highlight(row.id);}
function highlight(key){setTimeout(()=>{
 const main=document.querySelector('main');if(!main)return;
 let node=[...main.querySelectorAll('[data-search-target]')].find(n=>n.dataset.searchTarget===key)||[...main.querySelectorAll('[data-id]')].find(n=>n.dataset.id===key)||main.querySelector('[name="'+CSS.escape(key)+'"]')||main.querySelector('h1');
 if(!node)return;
 const group=node.closest('.v6-action-group');if(group){document.querySelectorAll('.v6-action-group').forEach(g=>g.hidden=g!==group);document.querySelectorAll('[data-action="v71-action-category"]').forEach((b,i)=>{const on=[...document.querySelectorAll('.v6-action-group')].indexOf(group)===i;b.classList.toggle('active',on);b.setAttribute('aria-pressed',String(on));});}
 document.querySelectorAll('.cm73-search-highlight').forEach(n=>n.classList.remove('cm73-search-highlight'));node.classList.add('cm73-search-highlight');node.scrollIntoView({block:'center',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});
 },120);}
window.CM73Search={box:()=>`<div class="cm73-global-search"><label class="input-wrap">${icon('search')}<input type="text" id="cm73-search-input" value="${E(query)}" placeholder="Игрок, предмет или действие" aria-label="Поиск по панели" autocomplete="off" maxlength="100"><button type="button" data-action="v73-search-clear" aria-label="Очистить поиск" ${query?'':'hidden'}>${icon('x')}</button><kbd>Ctrl K</kbd></label><div id="cm73-search-results" class="cm73-search-results" hidden></div></div>`,search,aliases};
H['admin-search']=H['admin-command']=()=>{if(X.canAdmin()){keepFocus=true;document.getElementById('cm73-search-input')?.focus();}};
H['v73-search-clear']=()=>{query='';notice='';matches=[];clearTimeout(timer);try{sessionStorage.removeItem(queryKey);}catch{}const input=document.getElementById('cm73-search-input');if(input){input.value='';input.focus();}document.querySelector('[data-action="v73-search-clear"]')?.setAttribute('hidden','');status();document.querySelectorAll('.cm73-search-highlight').forEach(n=>n.classList.remove('cm73-search-highlight'));};
H['v73-search-go']=b=>{keepFocus=false;go(matches[Number(b.dataset.index)]);document.getElementById('cm73-search-results').hidden=true;};
document.addEventListener('pointerdown',e=>{if(!e.target.closest('.cm73-global-search')){keepFocus=false;const box=document.getElementById('cm73-search-results');if(box)box.hidden=true;}});
V.after.push((page)=>{if(page!=='admin'){query='';keepFocus=false;return;}const input=document.getElementById('cm73-search-input');if(!input)return;
 input.addEventListener('input',()=>{query=input.value;notice='';keepFocus=true;try{query?sessionStorage.setItem(queryKey,query):sessionStorage.removeItem(queryKey);}catch{}clearTimeout(timer);document.querySelector('[data-action="v73-search-clear"]').hidden=!query;matches=search(query);status();
  if(query.trim().length>=3&&matches.length&&matches[0].score<2)timer=setTimeout(()=>{const m=matches[0];if(m){const url=m.url+(m.url.includes('?')?'&':'?')+'focus='+encodeURIComponent(m.id);if(location.hash!==url)go(m);else highlight(m.id);}},420);
 });
 input.addEventListener('keydown',e=>{if(e.key==='Enter'&&matches[0]){e.preventDefault();clearTimeout(timer);go(matches[0]);}if(e.key==='Escape'){document.getElementById('cm73-search-results').hidden=true;input.blur();keepFocus=false;}if(e.key==='Tab')keepFocus=false;});
 if(keepFocus){input.focus({preventScroll:true});input.setSelectionRange(query.length,query.length);status();}
 const focus=M.UI.params.get('focus');if(focus)highlight(focus);
});
})();
