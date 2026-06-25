# Проверка производительности CopiMine на 50 игроков

## Целевая конфигурация

- `max-players=50`
- `view-distance=8`
- `simulation-distance=6`

`simulation-distance=6` выбран как безопасный старт. Поднимать до `8` стоит только после живого stress test, если MSPT остаётся стабильным.

## Что уже должно быть включено

- `spark` установлен на сервер.
- `CoreProtect` установлен отдельно и не пишет в CopiMine PostgreSQL.
- Основные CopiMine runtime уже переведены на async DB там, где это критично.

## Команды smoke/stress

- `/spark profiler start`
- воспроизвести нагрузку 10-15 минут
- `/spark profiler stop`
- `/spark healthreport`
- `/mspt`

## Минимальный ручной тест

1. 5-10 игроков или ботов заходят одновременно.
2. Несколько игроков открывают:
   - банк/ATM;
   - выборы/участки;
   - лавку артефактов.
3. 2-3 игрока используют narcotics visuals fallback/client path.
4. Несколько игроков ставят и ломают блоки под CoreProtect logging.
5. 2-3 игрока запускают эмоции через Emotecraft.
6. Проверить:
   - MSPT;
   - `spark healthreport`;
   - отсутствие burst-лагов на открытии GUI.

## Расширенный тест на 50 игроков

1. 50 игроков/ботов распределены по миру.
2. 10 игроков активно двигаются между чанками.
3. 10 игроков открывают GUI.
4. 5 игроков используют Emotecraft.
5. 5 игроков получают client/fallback visuals CopiMine.
6. 5 игроков одновременно ломают/ставят блоки в зоне CoreProtect.
7. 5 игроков тестируют банк и переводы.

## Что считать нормой

- Средний MSPT: `<= 10`.
- Короткие пики допустимы, если не становятся постоянными.
- Если `MSPT > 10` держится стабильно:
  - первым делом смотри `spark profiler`;
  - проверь full scans и main-thread SQL;
  - проверь частоту GUI refresh/live panels;
  - при необходимости снижай `simulation-distance` обратно до `6` или оставляй её не выше `6`.

## Что смотреть в spark

- тяжёлые scheduled tasks;
- inventory/open GUI handlers;
- chunk load spikes;
- database waits;
- excessive entity ticking;
- CoreProtect burst logging;
- web/admin polling spam, если панель открыта и часто обновляется.

## Честный вывод для релиза

- Конфигурация может быть подготовлена под 50 игроков заранее.
- Подтверждение `MSPT <= 10` возможно только после живого stress test.
- Если live test не проводился, в релизном отчёте так и нужно писать.
