# CoreProtect: быстрые команды для администрации

## Базовые команды

- `/co i`
  - режим inspect: ПКМ/ЛКМ по блоку показывает, кто ставил или ломал блок.
- `/co lookup u:<player> t:<time> r:<radius>`
  - поиск действий игрока за время и в радиусе.
- `/co rollback u:<player> t:<time> r:<radius>`
  - откат действий игрока.
- `/co restore u:<player> t:<time> r:<radius>`
  - обратное восстановление после rollback.
- `/co purge t:<time>`
  - ручная чистка старых логов по retention policy.

## Рабочие примеры

- Проверить последние 5 минут вокруг точки:
  - `/co lookup t:5m r:20`
- Проверить конкретного игрока:
  - `/co lookup u:PlayerName t:30m r:50`
- Откатить локальный grief:
  - `/co rollback u:PlayerName t:10m r:15`
- Вернуть обратно, если откат был слишком широким:
  - `/co restore u:PlayerName t:10m r:15`

## Осторожность

- Перед rollback сначала делай `lookup` или inspect.
- Не откатывай служебные блоки вслепую:
  - участки ЦИК;
  - банкоматы;
  - налоговые;
  - лавки артефактов;
  - другие protected blocks CopiMine.
- Если rollback затронул protected block, после рестарта чанка или ручного repair проверь:
  - label/text display;
  - visual overlay;
  - связанный DB state CopiMine.

## Хранение данных

- CoreProtect хранит свои данные отдельно.
- Для CopiMine рекомендовано отдельное SQLite/MySQL storage CoreProtect, без смешивания с основным PostgreSQL runtime проекта.
