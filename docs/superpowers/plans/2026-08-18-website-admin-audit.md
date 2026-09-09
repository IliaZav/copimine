# План: аудит публичного сайта и админки CopiMine

## Цель

Свести публичную навигацию и визуальные слои к одному понятному контракту,
сделать рабочий кабинет единственным источником admin UX, устранить зависание
гостевого входа и дать администратору безопасное объяснение, какие источники
данных подключены.

## Ограничения

- Работа только в изолированном worktree.
- Никаких production upload, Paper restart, gameplay JAR changes, world/player
  data changes или database migrations.
- Проверки backend выполняются локально на synthetic/temporary configuration.
- Секреты и полные production connection strings не попадают в UI, тестовые
  артефакты и отчёт.

## Срезы

1. **Public shell** — единый порядок ссылок, один common CSS entrypoint,
   compatibility redirect `/mods.html`, явные preview disclaimers.
2. **Cabinet boot** — CSRF warmup в фоне, чтобы гость сразу получил понятный
   переход на вход вместо ожидания сетевого таймаута.
3. **Data-source diagnostics** — одинаковое определение auth backend в startup
   checks и runtime, безопасная карточка PostgreSQL/CoreProtect/Auth DB в
   Sources view.
4. **Verification** — статические контракты, backend/frontend self-tests,
   локальный HTTP/browser smoke и diff review.

## GREEN gates

- `python -m pytest -q` для затронутых контрактов.
- Существующие public navigation/copy/launcher/admin regression scripts.
- `git diff --check` и проверка staged diff.
- Локальный browser smoke без production API/database.
