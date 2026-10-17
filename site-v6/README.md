# CopiMine Studio v5

## Открыть на рабочем ПК

Распакуй ZIP и открой `CopiMine_Prototype_v5.html` или `OPEN_DEMO.bat`. Интернет, Python и доступ к домашнему ПК не нужны. Все изображения, иконки и скрипты внутри HTML.

Внизу есть панель проверки: «Игрок», «Админка», «Экспорт», «Сброс». Данные v5 хранятся отдельно от v4. Не вводи настоящие пароли.

## Что проверить

- Магазин: 31 публичный предмет, поиск, AR/CM, корзина. Компас телепортации удалён.
- Админка / Игроки: выбор игрока, импорт скина PNG, инвентарь, история, помощь со входом.
- Журналы: действия и технические логи, фильтры, импорт, пауза. Файлы для проверки в `examples/`.
- Рецепты: создай черновик или импортируй `examples/recipe.json`; выбор из 1617 материалов и предметов CopiMine.
- Поиск функций: `инвинтарь`, `bydtynfhm`, `PIN`, `казна`, `логи`.

## Границы прототипа

Каталог — снимок ветки `codex/site-launcher-audit`, не выгрузк живой БД. Карта построена из присланного архива: 1024×1024 блока. Игроки Demo, инвентари и примеры логов — тестовые. Вместо них можно импортировать свои данные. До импорта показаны стандартные скины.

Почта, SMS, VK, платежи, баны, выдача и сброс PIN на сервере не подключены. Для почты/SMS добавлен отдельный серверный адаптер; по умолчанию только предпросмотр.

608 из 1617 материалов уже имеют русские названия. Остальные не переведены наугад: есть поиск по EN/ID и импорт `ru_ru.json`.

## Build and optional HTTP mode

- Rebuild offline HTML: `python build.py --output CopiMine_Prototype_v5.html` (stdlib only).
- Optional local HTTP: `START.bat` or `python serve.py`. Bound to 127.0.0.1 only. No home-PC scanning by default.
- Unit tests: `node --test tests/*.test.cjs` and `python -m unittest discover -s tests -p 'test_*.py'`.
- Sources and limitations: `docs/SOURCES.md`, `docs/INTEGRATIONS.md`.
- Codex implementation contract: `docs/CODEX_HANDOFF.md`.
- Coverage and QA: `docs/ADMIN_COVERAGE.md`, `docs/QA.md`.

This is NOT a production admin panel. The demo dock intentionally permits local role switching. Do not deploy this directory or its SQLite/fixtures to a public web root.
