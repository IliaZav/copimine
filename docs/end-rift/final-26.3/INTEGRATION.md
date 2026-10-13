# Сохранение веток и старт новой разработки

На дату 13.10.2026:
- рабочая база Minecraft 26.3: codex/minecraft-26-3-migration-candidate-20261010, SHA a703c921610b9291a9c47cfb2fb0a7a536a1cb2c;
- старая опубликованная End Rift: codex/end-rift-event, SHA 7f722fd5e657a3d0b7307087dd936f5451791c3e;
- локальная End Rift worktree: D:/Desktop/Copimine/copimine-main/.worktrees/end-rift-event, 20 изменённых tracked файлов и многочисленные untracked результаты/ассеты;
- локальная миграционная worktree: .worktrees/minecraft-26-3-migration-candidate, несвязанные локальные файлы server.properties, .serena/ и диагностика;
- GitHub CI на SHA a703c921 — успешные static-and-contract, migration-postgres-contract и java-plugins; GitLab mirror совпадает по SHA. Не считать CI proof фактической игровой 7-волновой сцены.
- На изолированном Paper лог 2026-10-13 подтверждает включение AuthMe/ AuthEffects и 39 записей Artifacts, но не полный login test и не nativeVerified=true.

## Безопасное начало
1. Проверь фактические новые HEAD, все worktree, индекс/статус, diff и незакоммиченные источники. Не доверяй датированному чекпоинту как актуальному.
2. Сделай snapshot/патч локальных изменений End Rift безопасным недеструктивным способом, включая untracked assets. Выбери НОВУЮ целевую ветку от принятого Minecraft 26.3 baseline. Не переключай/не reset существующие ветки пока там работает Codex.
3. Разбирай существующие 20 файлов по смыслу; перенеси только нужные fixes волны/испытаний в новой Java25/Paper26.3/Fabric26.3 архитектуре. Не копируй слепо старые Mixin с Yarn1.21.1 или невалидные Java imports.
4. Базовые тесты, чистая сборка плагинов/клиента/ресурспака и совместимость установленного тестового профиля. Запуск локального клиента только с реальным согласованным JAR и lock; игры других пользователей не менять.
5. Не удаляй historic bug ledger или документы. Новая спецификация имеет приоритет для дизайна; старые отчёты сохраняются как доказательства.
6. Дальше: волны целиком → босс → Kagune → аудит. Каждый этап отдельными маленькими проверяемыми коммитами и настоящими F2.

## Поведение при конфликте
Не объявлять «готово» по имени коммита. Разделять design accepted, code implemented, automated pass, live Paper protocol, actual Minecraft 26.3 client, public production acceptance.
