# CopiMine: первый запуск и проверка админом

Эта инструкция написана для человека, который впервые зашёл в проект и имеет статус админа/OP.

Для полного приёмочного теста открой также `FULL_MANUAL_TEST_MATRIX.md`. Там есть таблицы по всем функциям с пустой колонкой “Фактический результат”, чтобы админ мог пройти проект строка за строкой и отметить реальное поведение.

Финальный технический отчёт лежит в `FINAL_AUDIT_REPORT.md`: там перечислено, что проверялось, что было исправлено и какие проверки прошли.

## Что добавлено и как это работает

### 1. Основная админка CopiMineUltimateAdminPlus

`CopiMineUltimateAdminPlus.jar` - главный плагин сервера.

Он отвечает за:
- банк и AR;
- PIN для банковских операций;
- PostgreSQL-данные;
- выборы, роли, ЦИК, президентские инструменты;
- админские GUI;
- player tools;
- официальный bridge для других CopiMine-плагинов.

Важно: другие CopiMine-плагины не должны сами менять баланс игрока. Они обращаются к bridge основного плагина.

### 2. Артефакты CopiMineArtifacts

`CopiMineArtifacts.jar` - отдельный плагин артефактов.

Он работает так:
- открывает лавки артефактов;
- продаёт оружие, броню и инструменты;
- содержит V4-каталог из 12 товаров: 4 боевых артефакта, 4 защитных артефакта и 4 рабочих инструмента;
- показывает чёрный рынок как отдельную скрытую вкладку внутри лавки только в день открытия рынка;
- списывает AR только через bridge `CopiMineUltimateAdminPlus`;
- если инвентарь полный, создаёт pending delivery;
- ремонтирует только официальные PDC-предметы;
- fake item с похожим названием не работает;
- каталог и лавки кешируются, чтобы не читать PostgreSQL на каждый клик.

Если `CopiMineUltimateAdminPlus` не загружен, `CopiMineArtifacts` не должен стартовать.

### 3. RP-чёрный рынок CopiMineNarcotics

`CopiMineNarcotics.jar` - игровой RP-плагин вымышленной контрабанды.

Это не реальные вещества и не реальные инструкции. Предметы существуют только как игровые RP-предметы.

Как работает:
- всего настроено 25 уникальных вымышленных предметов;
- крафтов нет;
- админская выдача предметов отключена;
- получить предмет можно только на чёрном рынке;
- чёрный рынок появляется на 1 игровые сутки внутри каждого 20-дневного окна;
- конкретный день открытия разный, игроки не видят таймер и не могут нормально просчитать расписание;
- одновременно продаётся только 5 случайных предметов;
- при новом открытии ассортимент меняется;
- в обычной лавке вкладка чёрного рынка большую часть времени вообще не видна;
- покупка идёт за много AR через официальный bank bridge;
- PIN вводится кнопками в GUI, не командой в чат;
- предметы не имеют описаний эффектов, только название;
- эффекты игровые: potion shader/effects, частицы и звуки, без title/actionbar-надписей.

### 4. Сайт в Minecraft UI стиле

Веб-панель переделана в более плотный Minecraft UI:
- меньше AI-градиентов и стеклянных карточек;
- компактная боковая навигация;
- блоковая сетка;
- item icons;
- таблицы и статусные панели;
- раздел Artifacts для админов;
- блок Artifacts в кабинете игрока.

### 5. Оптимизация

В проекте уже подготовлен стек:
- Chunky;
- SeeMore;
- FarmControl;
- EntityClearer;
- GrimAC;
- Paper/Purpur/Pufferfish настройки;
- TAB/CoreProtect настройки;
- валидаторы MSPT/TPS.

Новый чёрный рынок не добавляет every-tick задач, не сканирует мир, не ходит в БД напрямую и работает только по событиям игрока.

## Как перенести на Ubuntu

Перед первым запуском обязательно проверь `/opt/copimine/admin-web/.env`.

В нём должен быть настоящий пароль PostgreSQL:

```text
POSTGRES_PASSWORD=твой_реальный_пароль
```

Нельзя оставлять пустое значение или `CHANGE_ME`. Если `POSTGRES_PASSWORD` не задан, `CopiMineUltimateAdminPlus` не включится, а `CopiMineArtifacts` и `CopiMineNarcotics` специально остановятся, чтобы не было рассинхрона банка, PIN и bridge.

1. Останови сервисы на Ubuntu:

```bash
sudo systemctl stop minecraft
sudo systemctl stop copimine-admin
sudo systemctl stop copimine-discord-bot
```

2. Сделай backup старой папки:

```bash
sudo cp -a /opt/copimine /opt/copimine.backup-$(date +%Y%m%d-%H%M%S)
```

3. Замени папку `/opt/copimine` содержимым из `D:\Desktop\Copimine\opt\copimine`.

Итог на Ubuntu должен быть таким:

```text
/opt/copimine/admin-web
/opt/copimine/copimine-admin-plugin
/opt/copimine/copimine-artifacts
/opt/copimine/copimine-narcotics
/opt/copimine/db
/opt/copimine/minecraft
/opt/copimine/tests
```

4. Проверь jar в `/opt/copimine/minecraft/server/plugins`:

```text
CopiMineUltimateAdminPlus.jar
CopiMineArtifacts.jar
CopiMineNarcotics.jar
Chunky-Bukkit-1.4.40.jar
SeeMore-1.0.2.jar
FarmControl-1.3.0.jar
EntityClearer.jar
GrimAC.jar
```

5. Выставь владельца:

```bash
sudo chown -R minecraft:minecraft /opt/copimine
```

6. Запусти сервисы:

```bash
sudo systemctl start minecraft
sudo systemctl start copimine-admin
sudo systemctl start copimine-discord-bot
```

7. Открой логи Minecraft:

```bash
journalctl -u minecraft -f
```

В логах должны быть включены:

```text
CopiMineUltimateAdminPlus
CopiMineArtifacts
CopiMineNarcotics
```

Также проверь, что нет ошибок bridge, PostgreSQL и PIN.

## Первый вход на сервер

1. Зайди на сервер под админским ником.
2. Убедись, что у тебя OP или нужные LuckPerms-права.
3. Выполни:

```text
/plugins
```

В списке должны быть зелёными:

```text
CopiMineUltimateAdminPlus
CopiMineArtifacts
CopiMineNarcotics
Chunky
SeeMore
FarmControl
EntityClearer
GrimAC
LuckPerms
Vault
ProtocolLib
PlaceholderAPI
```

## Проверка основной GUI-админки

1. Открой главное меню:

```text
/cmultra
```

Быстрый вход в банк и банкоматы:

```text
/cmbank
```

2. Проверь разделы:
- игроки;
- экономика/AR;
- выборы;
- ЦИК;
- polling stations;
- startup/readiness;
- performance/optimization.

3. В player tools проверь:
- heal/feed для тестового игрока;
- snapshot inventory;
- player timeline;
- AR sync.

4. В election GUI проверь:
- старт/этапы выборов;
- заявки;
- бюллетени;
- подсчёт;
- назначение президента;
- emergency tools.

## Проверка банка и AR

1. Убедись, что у тестового игрока есть банковский PIN.
2. Проверь баланс через сайт или GUI.
3. Сделай тестовую передачу AR.
4. Проверь, что операция появилась в ledger.

Важно: PIN нельзя писать в публичный чат. В новых GUI PIN вводится кнопками.

## Проверка артефактов

### Создать лавку

1. Встань перед блоком, который будет лавкой.
2. Выполни:

```text
/cmartifacts shop create test_shop
```

3. Правый клик по лавке должен открыть GUI артефактов.

### Купить артефакт

1. Открой лавку.
2. Выбери предмет.
3. Введи PIN в GUI.
4. Проверь:
- AR списались;
- предмет выдан;
- покупка появилась в админке сайта;
- fake item с похожим названием не проходит.

### Pending delivery

1. Заполни инвентарь игрока.
2. Купи артефакт.
3. Предмет не должен потеряться.
4. Выполни:

```text
/cmartifacts claim
```

5. Предмет должен выдаться, когда появится свободное место.

### Ремонт

1. Возьми официальный артефакт.
2. Выполни:

```text
/cmartifacts repair
```

3. Проверь, что ремонт идёт за AR и только для настоящего PDC-предмета.

## Проверка чёрного рынка

### Открыть рынок

Игрок выполняет:

```text
/cmnarcotics market
```

Если рынок закрыт, обычный игрок увидит только, что рынок скрыт.

Админ может открыть:

```text
/cmnarcotics menu
```

Если рынок закрыт, админ увидит, через сколько игровых суток будет следующее окно.

### Как работает расписание

- окно планирования: 20 игровых суток;
- внутри каждого окна случайно выбирается 1 день открытия;
- игроки не видят точный таймер;
- открытие не приходится на один и тот же день цикла;
- при новом открытии выбираются другие 5 предметов.

### Купить предмет

Когда рынок открыт:

1. Выполни:

```text
/cmnarcotics market
```

2. В GUI будет 5 случайных товаров.
3. Нажми на товар.
4. Введи PIN кнопками.
5. Нажми зелёную кнопку покупки.
6. Проверь:
- AR списались;
- предмет появился в инвентаре;
- у предмета есть только название;
- описания эффектов нет;
- при правом клике появляются игровые эффекты, частицы и звук;
- никаких title/actionbar-надписей нет.

### Если рынок закрыт во время теста

В тестовом окружении админ может посмотреть в `/cmnarcotics menu`, сколько игровых суток осталось, и временно промотать время:

```text
/time add 24000
```

Повтори команду нужное количество раз только на тестовом сервере.

### Конфискация

Если нужно изъять RP-контрабанду у игрока:

```text
/cmnarcotics confiscate <ник>
```

Это удалит только официальные PDC-предметы `CopiMineNarcotics`.

## Проверка сайта

1. Открой сайт панели.
2. Войди как админ.
3. Проверь:
- dashboard;
- players;
- economy;
- artifacts;
- elections;
- polling stations;
- performance/optimization.

4. В разделе Artifacts должны быть:
- bridge health;
- каталог;
- лавки;
- покупки;
- pending delivery;
- ремонты;
- suspicious events.

5. Войди как игрок.
6. Проверь кабинет игрока:
- баланс;
- transfer;
- мои Artifacts покупки;
- pending delivery;
- repairs.

Обычный игрок не должен видеть технические UUID, bank tx id и idempotency keys.

## Быстрый smoke после переноса

В игре:

```text
/plugins
/cmultra
/cmartifacts
/cmnarcotics market
```

На Ubuntu:

```bash
cd /opt/copimine
powershell -ExecutionPolicy Bypass -File tests/ValidateCopiMineNarcoticsPlugin.ps1
```

Если PowerShell на Ubuntu не установлен, достаточно проверить сборку до переноса на Windows и затем смотреть live logs.

## Что считать готовым

Проект готов к первому боевому тесту, если:
- сервер стартует;
- сайт открывается;
- админ входит на сайт;
- игрок входит в кабинет;
- `CopiMineUltimateAdminPlus`, `CopiMineArtifacts`, `CopiMineNarcotics` включены;
- Artifacts bridge ready;
- PostgreSQL ready;
- PIN ready;
- покупка артефакта списывает AR;
- pending delivery не теряет предмет;
- чёрный рынок скрывается/появляется по расписанию;
- чёрный рынок продаёт только 5 случайных предметов;
- предметы чёрного рынка покупаются за AR через PIN GUI;
- крафтов RP-контрабанды нет;
- MSPT/TPS не проседают после открытия GUI и покупки.
