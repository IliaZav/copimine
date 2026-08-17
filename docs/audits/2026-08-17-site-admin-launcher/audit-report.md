# Аудит сайта, Launcher и админ-панели — 2026-08-17

## Область

Проверен изолированный checkout `codex/site-launcher-audit` и локальный synthetic backend на `127.0.0.1:8091`. Production Minecraft, мир, player data, AuthMe и production database в этот цикл не подключались.

## Что было найдено и исправлено

| Область | До | После | Проверка |
| --- | --- | --- | --- |
| Главная | Две CTA для загрузки: Launcher и старый модпак | Одна CTA «Скачать Launcher» | `site_launcher_audit_contract_test.py`, browser DOM |
| Кабинет | 28 старых ссылок «Модпак» | Все header-ссылки ведут на `/launcher.html` и называются «Лаунчер» | navigation contract |
| Админ-навигация | «Лавки» дублировались в нескольких местах | Разделы разделены на «Каталоги» и «Артефакты» | static contract + browser DOM |
| Homepage data | Повторные запросы и лишние fallback-загрузчики | Один прямой агрегирующий запрос без повторной загрузки modpack | frontend runtime selftest |
| Launcher metadata | Публичный JSON ссылался на устаревший 1.0.0 и отсутствующие installer-файлы | Metadata 1.0.1 совпадает с EXE/MSI и SHA-256 | release verifier |
| Mods admin | Имена `.jar` с `+` отклонялись | `+` разрешён; в админке видны все 8 managed-модов | 10 control-plane tests + browser DOM |
| Новости | Публичная статика была видна, но пустой control-state показывал администратору 0 записей | Админ-редактор читает generated static feed и показывает 2 опубликованные записи | control-plane tests + browser DOM |
| Восстановление аккаунта | Форма не запрашивала login сайта | Пользователь явно вводит login сайта; коллизия в `site_accounts` или admin directory даёт 409 до расходования кода | 3 behavior tests |

## Локальные контракты

- `GET /api/public/launcher`: `200`, версия `1.0.1`, 8 managed-модов.
- `GET /api/public/news`: `200`, 2 patch notes.
- `/launcher/stable/instance-manifest.json`: `200`, 4049 bytes.
- Installer EXE: 1,407,227,839 bytes, SHA-256 `5c0b7a82b28a0389249b97cca9e488ecf6792c11b6f5cb9051a1f776744a9487`.
- Installer MSI: 1,394,643,544 bytes, SHA-256 `a3f4d229b8cfed2f7733f4d82d84db510d315699d34366ddad87f68668aaf751`.
- Manifest release: `2026.08.17.4`, sequence `8`, Minecraft `1.21.1`, Fabric Loader `0.19.3`.

## Базы данных

Репозиторий декларирует PostgreSQL как primary storage для production auth/game data (`POSTGRES_*`, `COPIMINE_AUTH_STORAGE=postgresql`). SQLite остаётся локальным compatibility backend для synthetic/audit запуска и отдельных legacy helper paths. Текущий local browser audit запускался только на disposable SQLite; production connection string и production DB не использовались.

## Визуальные доказательства

- Главная до/после: `home-before.png`, `home-after.png`.
- Launcher до/после: `launcher-before-viewport.png`, `launcher-after-viewport.png`.
- Админка до/после: `admin-real-before.png`, `admin-after.png`.

## Ограничения

Этот файл фиксирует локальный GREEN-цикл. Read-only production web audit, upload static release и внешний HTTP/hash smoke test выполняются отдельным следующим gate. Gameplay regressions (ping, bed waypoint, authorization slowness) на production не выполнялись и не будут выполняться на живых игроках; без local/staging evidence они остаются `UNVERIFIED`.
