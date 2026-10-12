# Authentication and required client mod policy for Minecraft 26.3

Проверено: 2026-10-11. Это исследование конфигурации и официальной документации; оно не является native-проверкой логина и не меняет публичный сервер.

## Источник CI

GitHub — основной репозиторий и единственный обязательный источник CI/review acceptance. Все GitHub Actions jobs должны пройти на точном проверяемом SHA. GitLab остаётся резервным зеркалом того же SHA; его pipeline может дать дополнительную диагностику, но не блокирует принятие checkpoint.

## AuthMe: подтверждённая проблема и её границы

Migration lock закрепляет AuthMe 6.0.1 Paper с SHA-256 `7704335e9e73a634d9d926344f77897f4c74f78f82453a59f5aa0f8d2722450a`. В официальном release 6.0.1 команда AuthMe описала и исправила конкретную уязвимость: на Paper/Folia в offline-mode, если включён pre-join login/register dialog, промежуточное состояние диалога было привязано к offline UUID. Подключение второго клиента с тем же ником могло разделить состояние первой сессии, прочитать или сбросить его. Исправление привязало состояние к конкретному соединению и добавило блокировку занятого ника во время входа.

Есть отдельный фактический разрыв кандидата и старого runtime inventory: в checkout всё ещё отслеживается `minecraft/server/plugins/AuthMe-5.6.0.jar`, а migration lock описывает отдельный кандидат `AuthMe-6.0.1-Paper.jar`. Это не доказывает, что старая версия запущена на публичном сервере, но означает, что текущую локальную runtime-проверку нельзя считать проверкой 6.0.1. Перед AuthMe acceptance нужно сверить и запустить именно JAR из migration lock на изолированной копии базы.

Это подтверждает, что у AuthMe была реальная проблема, и её нельзя списывать на слухи. Но это не подтверждает общее утверждение, что текущий AuthMe 6.0.1 уязвим: точный описанный путь закрыт в версии, которую фиксирует кандидат. GitHub Security сейчас не показывает опубликованных advisories, но репозиторий не публикует SECURITY.md; отсутствие advisory не является независимым аудитом безопасности.

В отслеживаемом `minecraft/server/plugins/AuthMe/config.yml` pre-join параметр явно не задан, поэтому по одному этому файлу нельзя утверждать, был ли новый dialog включён в фактическом runtime после обновления конфигурации. Уязвимость всё равно исправлена в закреплённой версии; перед публичным запуском нужно сверить реально загруженный JAR и runtime config.

Текущий tracked config использует BCrypt с cost 12, допускает SHA256 как legacy hash, требует пароль длиной от 12 символов, ограничивает сессию одним входом, закрывает чат для неавторизованных игроков и оставляет им только login/register команды. Включены captcha после 5 неверных попыток и временная блокировка IP после 10 попыток на 480 минут. Эти настройки полезны, но не заменяют проверку реального запуска, входа, восстановления аккаунта и изоляции backend.

В 6.0.1 upstream также добавил ARGON2ID как рекомендуемый вариант хеширования. Это сначала нужно испытать на копии auth database: сохранить backup, проверить вход и автоматическую смену hash для существующих BCrypt и SHA256 записей, и лишь после этого менять production-конфигурацию. Для такого перехода legacyHashes должен включать каждый алгоритм, который действительно встречается в базе.

В локальном loopback test runtime AuthMe/AuthEffects намеренно выключены по прежней просьбе пользователя. Поэтому текущий Paper запуск не доказывает ни качество AuthMe, ни готовность альтернативы к production.

## Альтернативы, найденные в официальных репозиториях

| Кандидат | Что подходит CopiMine | Текущая оценка |
| --- | --- | --- |
| AuthMe-Freemium | Поддерживает mixed/offline login, premium verification, Argon2 и импорт; заявляет `provides: AuthMe` и прежний Java API, поэтому потенциально меньше всего меняет интеграцию CopiMine. | Репозиторий описывает стабильные теги `v*`, но текущая страница Releases показывает только prerelease `dev-build`. Готовый стабильный выпуск для Paper 26.3 не подтверждён. Это подходящий изолированный экспериментальный кандидат, но не готовая замена для публичного сервера. Он остаётся fork AuthMe и разделяет часть upstream-кода. |
| OpeNLogin-Reborn | Заявляет Argon2 по умолчанию, ограничение попыток, импорт AuthMe и поддержку offline-mode. | Официальная страница Releases пуста. Совместимость с Paper 26.3 не подтверждена; API отличается от AuthMe и потребует адаптировать интеграции AuthEffects и административного сайта. Нужны проверяемый release, source review, build, миграция базы и runtime тест. |
| NexAuth | Отдельный современный плагин для Paper/Velocity; заявляет offline login, premium auto-login, TOTP и хранилище аккаунтов. | Последний видимый release `0.0.1-beta4` всё ещё помечен beta и документирует тесты до Paper 26.1.2. Нет подтверждённой поддержки 26.3 и интеграций CopiMine; это кандидат для стенда, не готовая замена. |
| NeoAuthMe | Есть стабильный tag 6.1.0; заявленная поддержка Paper 1.21.11+. | Это fork AuthMe, а не независимая реализация. В release notes нет доказательства, что он безопаснее AuthMe 6.0.1; совместимость с Paper 26.3 и локальными интеграциями ещё не проверялась. |

**Вывод:** по состоянию на дату проверки не найден альтернативный вариант, который одновременно имеет зрелый стабильный release, проверенную совместимость с Paper 26.3 и готовые CopiMine integrations. Это не доказывает, что AuthMe в целом безопасен: подтверждена конкретная исправленная проблема, но независимого аудита 6.0.1 здесь нет. AuthMe 6.0.1 остаётся текущим migration candidate до реального auth-теста; Argon2ID следует проверить на копии базы с проверкой миграции старых хешей. AuthMe-Freemium и NexAuth подходят для изолированных сравнительных стендов, когда будет зафиксирован стабильный релиз; production-переключение и база не менялись.

Для offline-mode с лицензированными и offline/cracked игроками остаётся важным разделение аутентификации и имени. Если Mojang-профиль и offline игрок используют один ник, сервер без premium identity verification не может считать одно только совпадение ника подтверждением владельца. У AuthMe premium bypass выполняет такую проверку; для прямого offline-mode документация требует PacketEvents, а при proxy нужен соответствующий AuthMe proxy flow и закрытый для прямого доступа backend. Premium bypass — отдельная настройка; без него licensed игроки тоже могут входить через обычную пару `/register` и `/login`, но это не подтверждает их Mojang identity.

## Обязательная установка CopiMineClient

Официальные механизмы решают разные задачи:

| Механизм | Что он действительно обеспечивает | Ограничение |
| --- | --- | --- |
| Fabric `depends` в `fabric.mod.json` | Fabric Loader откажется запускать этот мод при отсутствии или несовместимой версии обязательной зависимости. Для профиля можно закрепить Minecraft, Fabric Loader и CopiMineClient. | Проверка действует только когда клиент запускается через Fabric Loader. Она не заставляет подключаться через Fabric и не даёт Paper доказательства, какой JAR лежит у игрока. |
| Paper `require-resource-pack=true` | Отклоняет игрока, который не принимает обязательный resource pack. | Требует pack, а не CopiMineClient; не подтверждает его ID или версию. |
| `online-mode=true` / подписанные профили | Проверяет официальную Minecraft identity и signed profile согласно серверному режиму. | Не допускает обычные cracked/offline аккаунты; не проверяет CopiMineClient. Нельзя включить это как решение задачи mixed login. |
| Профиль или launcher с закреплённым модом | Упрощает выдачу и запуск известного набора Minecraft/Fabric/клиентского JAR. | Игрок может обойти свой launcher и подключиться другим клиентом; сам по себе launcher не даёт серверу доверенной аттестации. |
| Текущий Paper challenge | Проверяет mod ID, точную версию, протокол и живой ответ при входе; отсекает vanilla-клиент и ошибочно установленную версию. | Это собственная проверка совместимости по открытым plugin messages, не криптографическое доказательство JAR и не защита от клиента, который намеренно подделывает ответ. |

Для CopiMine выбран последний механизм, потому что он работает при offline-mode и сохраняет вход лицензированных и offline игроков через выбранную auth-конфигурацию. Профиль Fabric дополнительно не запускается без нужной версии мода. У Paper нет общей встроенной настройки, которая сверяет локальный Fabric JAR на клиенте.

Текущая реализация в рабочем дереве переводит CopiMineClient в обязательный клиентский мод. `copimine-narcotics/config.yml` задаёт только `required_client_mod_id: copimineclient`, точную версию `0.1.2+26.3` и timeout; версия сверяется с `tools/minecraft-26.3/profile.lock.json`. Все другие внешние gameplay/graphics mods из `client-mods.lock.json` остаются вне серверной проверки.

Paper отправляет отдельный `client_mod_challenge` с одноразовым случайным ID для соединения. Клиент отвечает тем же ID, протоколом 3, mod ID и версией, прочитанной из Fabric metadata загруженного JAR. Сервер сравнивает каждый элемент с настройкой и deadline, затем подтверждает сессию. Vanilla-клиент без канала, неверная версия, некорректный ответ или timeout ведут к отключению; пока проверка идёт, блокируются движение, взаимодействия, инвентарь и чат. Разрешён только authentication allowlist: `/login`, `/l`, `/register`, `/reg`, `/2fa`, `/premium` и `/freemium`.

Сервер не проверяет Iris, OptiFine, Sodium, Emotecraft, HUD, карты или голосовой чат. Отдельно игроку нужны Minecraft `26.3` и Fabric Loader `>=0.19.5`. Fabric API `0.162.0+26.3` вложен внутрь JAR CopiMineClient как Jar-in-Jar; установить в `mods` требуется только CopiMineClient. Этот API не считается отдельной обязательной модификацией в обычном профиле. При подготовке профиля для необязательных модов режим `DependenciesOnly` по-прежнему может установить отдельный Fabric API; обычная установка убирает отдельный экземпляр и сохраняет его резервную копию, чтобы не создать конфликт с вложенной копией.

Это практическая проверка совместимости для обычного vanilla-клиента и неправильного выпуска CopiMineClient, но не криптографическая аттестация. Challenge не содержит секрета; модифицированный клиент может повторить открытый протокол и заявить тот же ID/версию. У Paper plugin messaging нет общего механизма доверенного доказательства локальных JAR, поэтому нельзя обещать защиту от намеренной подделки одной проверкой, запущенной на машине игрока. Действительная блокировка требует доверенного launcher/identity flow с серверной проверкой этого допуска.

Автоматические client Gradle tests, contract/timeout/abuse checks и полный Python suite прошли. Paper 26.3 candidate запущен; его startup log подтверждает включённый обязательный bridge. Локальный профиль `D:\.minecraft\versions\CopiMine` установлен из lock и read-only validation прошёл. Нативного соединения этим профилем пока нет: Computer Use видит `apps: []`, Remote Desktop Commander сообщает, что машина online, но доступные инструменты не показывают Minecraft window или управляемую экранную поверхность. Поэтому сам факт входа с правильным модом, отключение vanilla-клиента и отключение неправильной версии ещё не проверены в реальном окне игры; migration остаётся **IN_PROGRESS**, `nativeVerified=false`.

## Официальные источники

- [AuthMe 6.0.1 release notes](https://github.com/AuthMe/AuthMeReloaded/releases/tag/6.0.1)
- [AuthMe GitHub Security](https://github.com/AuthMe/AuthMeReloaded/security)
- [AuthMe configuration reference](https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/config.md)
- [AuthMe-Freemium repository](https://github.com/Doom2615/AuthMe-Freemium) and [releases](https://github.com/Doom2615/AuthMe-Freemium/releases)
- [OpeNLogin-Reborn repository](https://github.com/OlympiTech/OpeNLogin-reborn) and [releases](https://github.com/OlympiTech/OpeNLogin-reborn/releases)
- [NexAuth repository](https://github.com/XreatLabs/NexAuth) and [releases](https://github.com/XreatLabs/NexAuth/releases)
- [NeoAuthMe 6.1.0 release notes](https://github.com/t7n9/NeoAuthMe/releases/tag/v6.1.0)
- [Paper plugin messaging documentation](https://docs.papermc.io/paper/dev/plugin-messaging/)
- [Paper resource-pack requirement](https://docs.papermc.io/paper/reference/server-properties/#require-resource-pack)
- [Fabric networking documentation](https://docs.fabricmc.net/develop/networking)
- [Fabric Loom dependency inclusion](https://docs.fabricmc.net/develop/loom/)
- [Fabric Loader `fabric.mod.json` dependencies](https://docs.fabricmc.net/develop/loader/fabric-mod-json)
- [Fabric Loader nested JAR documentation](https://docs.fabricmc.net/develop/loader/)
- [Paper server properties](https://docs.papermc.io/paper/reference/server-properties/)
