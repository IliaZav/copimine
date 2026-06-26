# CopiMine Donation / AR Shop / Plugin Registry Design

**Date:** 2026-06-25

**Status:** Approved design scope for current implementation pass

## 1. Scope

Этот проход не переписывает проект с нуля. Он аккуратно расширяет уже существующий foundation.

В scope текущего pass входят только три блока:

1. Donation foundation hardening.
2. AR shop foundation.
3. Backend plugin registry foundation.

## 2. Explicit Goals

### 2.1. Donation foundation

Нужно довести уже существующие таблицы и сервисы:

- `donation_accounts`
- `donation_balance_ledger`
- `donation_payment_sessions`
- `donation_purchases`
- `donation_item_claims`

Нужный результат:

- donation balance работает стабильно и идемпотентно;
- поддерживаются только фиксированные пакеты `50 / 100 / 250 / 500 / 1000`;
- `MOCK_SBP` сессии работают end-to-end;
- локальная генерация QR работает без внешних QR API;
- `mark-paid` не начисляет баланс дважды;
- admin top-up работает через audit и allowlist;
- purchase-intent берёт цену только из backend catalog;
- claim lifecycle доведён до безопасного антидюп-состояния;
- owner-bound выдача и reclaim после внешней потери работают стабильно.

### 2.2. AR shop foundation

Нужно добавить отдельную foundation-ветку для AR-лавки:

- отдельный AR catalog/source branch;
- AR товары полностью отделены от donation items;
- покупка и claim идут через `CopiMineArtifacts`;
- ремонт разрешён только для AR items;
- `Mending`, `anvil`, `grindstone`, `smithing`, `crafting` и другие functional blocks не должны обходить правила ремонта;
- `AR` и `donation_balance` не смешиваются ни в БД, ни в API, ни в GUI.

### 2.3. Backend plugin registry foundation

Нужно добавить backend foundation для управления плагинами без raw file access:

- registry manifest / allowlist;
- endpoints:
  - `status`
  - `config`
  - `schema`
  - `validate`
  - `apply`
  - `reload`
  - `backup`
  - `audit`
- только allowlisted plugin IDs;
- только allowlisted config keys;
- schema validation перед apply;
- backup before apply;
- audit every change;
- без arbitrary file write;
- без raw-config editor.

## 3. Explicit Non-Goals

Сейчас не делаем:

- большой frontend redesign;
- JWT / dashboard overhaul;
- новую визуальную admin panel с графиками;
- donation provider integration beyond `MOCK_SBP`;
- большой UI/UX restyle сайта;
- новые игровые системы вне donation / AR shop / registry;
- переписывание ElectionCore / Narcotics / WorldCore, если это не требуется для минимальной интеграции.

Разрешены только минимальные frontend/backend правки, без которых commerce-flow физически не работает.

## 4. Ownership Boundaries

### 4.1. EconomyCore

`CopiMineEconomyCore` остаётся единственным владельцем:

- donation balance;
- donation balance ledger;
- payment sessions;
- payment provider abstraction;
- donation purchases;
- donation claims;
- admin top-up;
- idempotency по money-flow;
- purchase audit.

`EconomyCore` не владеет физическими `ItemStack`, PDC, reclaim-логикой и anti-fake для игровых предметов.

### 4.2. Artifacts

`CopiMineArtifacts` остаётся единственным владельцем:

- item catalog runtime;
- `artifact_item_instances`;
- physical issuance;
- owner-bound PDC contract;
- reclaim;
- anti-dupe / anti-fake / anti-stale-instance;
- gameplay effects;
- AR items runtime;
- repair policy.

`Artifacts` не владеет donation balance и не должен напрямую мутировать donation money-flow мимо `EconomyCore`.

### 4.3. admin-web

`admin-web` является канонической точкой:

- оплаты;
- покупки donation items;
- просмотра donation balance/history;
- admin top-up;
- admin mock payment handling;
- plugin registry API.

`admin-web` сейчас не становится местом большого визуального редизайна.

### 4.4. UltimateAdminPlus

`CopiMineUltimateAdminPlus` остаётся hub/delegator.

Он не должен становиться вторым владельцем:

- donation logic;
- AR shop logic;
- plugin registry logic.

## 5. Money Separation Rules

### 5.1. Donation currency

- `Donation` — отдельная валюта.
- Курс фиксирован: `1 реальный рубль = 1 donation unit`.
- `AR` нельзя покупать за реальные деньги.
- `AR` и `Donation` не смешиваются:
  - ни в SQL;
  - ни в ledger;
  - ни в GUI;
  - ни в API response;
  - ни в admin actions.

### 5.2. Fixed top-up packs

Разрешены только:

- `50`
- `100`
- `250`
- `500`
- `1000`

Любая create-session логика должна принимать только amount из allowlist.

## 6. Donation Catalog / Instance / Effect Contract

### 6.1. Donation catalog

Donation items нельзя реализовывать набором `if/else` по display name.

Нужен единый catalog layer со стабильным `item_id`.

Каждый donation catalog entry обязан содержать минимум:

- `item_id`
- `display_name`
- `base_material`
- `price_donation`
- `enabled`
- `source = DONATION_SHOP`
- `owner_bound = true`
- `reclaim_policy = LOSS_ONLY`
- `consume_policy`
- `effect_profile_id`
- `cooldown_seconds`
- `proc_chance`
- `max_stack`
- `repairable`
- `custom_texture_mode_allowed`
- `catalog_version`
- `updated_at`

### 6.2. PDC contract for donation items

Каждый официальный donation item обязан нести PDC:

- `copimine_item_type = DONATION_SHOP_ITEM`
- `catalog_item_id`
- `unique_item_id`
- `owner_uuid`
- `owner_name`
- `source = DONATION_SHOP`
- `bound = true`

Gameplay logic никогда не доверяет:

- display name;
- lore;
- material.

Источник истины:

- PDC;
- `unique_item_id`;
- `owner_uuid`;
- DB status;
- cooldown;
- protection/PvP checks.

### 6.3. Instance model

Каждый физически выданный donation item получает строку в `artifact_item_instances`:

- `unique_item_id`
- `item_id`
- `owner_uuid`
- `purchase_id`
- `status`
- `durability_state`
- `created_at`
- `updated_at`

Статусы instance:

- `ACTIVE`
- `LOST_RECLAIMABLE`
- `REPLACED_AFTER_LOSS`
- `BROKEN`
- `CONSUMED`
- `DELETED_AS_INVALID`

`CLAIM_PENDING` живёт не в `artifact_item_instances`, а в `donation_item_claims`.

## 7. Donation Purchase / Claim / Reclaim Workflow

### 7.1. Payment sessions

Payment sessions имеют статусы:

- `CREATED`
- `PENDING`
- `PAID`
- `CANCELLED`
- `EXPIRED`

Правила:

- баланс меняется только после `PAID`;
- повторный `mark-paid` по той же сессии не начисляет баланс второй раз;
- повторный webhook в будущем тоже не должен начислять второй раз;
- каждая операция top-up обязана иметь idempotency semantics.

### 7.2. Purchases

`donation_purchases` используют статусы:

- `PAID`
- `CLAIM_PENDING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

Покупка:

- выполняется только на сайте;
- frontend передаёт только `item_id`;
- backend берёт цену из catalog;
- `EconomyCore` атомарно:
  - проверяет баланс;
  - списывает donation balance;
  - пишет ledger;
  - создаёт purchase;
  - создаёт claim со статусом `UNCLAIMED`.

Покупка не создаёт `ItemStack` сразу.

### 7.3. Claims

`donation_item_claims` используют статусы:

- `UNCLAIMED`
- `RESERVED`
- `DELIVERING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

Claim выполняется только в игре через `CopiMineArtifacts`.

Безопасный lifecycle:

- `UNCLAIMED -> RESERVED -> DELIVERING -> CLAIMED`

Перед физической выдачей нужно проверить:

- owner UUID;
- claim status;
- inventory space;
- отсутствие второго `ACTIVE` instance по тому же entitlement;
- валидность catalog item.

Только после этого:

- создаётся `unique_item_id`;
- создаётся `artifact_item_instance`;
- создаётся `ItemStack` с корректным PDC;
- предмет физически выдаётся игроку;
- claim финализируется как `CLAIMED`.

Failure rules:

- если физическая выдача не началась, claim можно безопасно вернуть в `UNCLAIMED`;
- если предмет уже мог появиться у игрока, но завершение сломалось, статус становится `DELIVERY_REVIEW`;
- `DELIVERY_REVIEW` не повторяется автоматически и не auto-release назад.

### 7.4. Reclaim

Reclaim выполняется только в игре через отдельный экран:

- `Вернуть утерянные предметы`

Показывать только items со статусом:

- `LOST_RECLAIMABLE`

Правила reclaim:

- возврат не массовый;
- игрок выбирает конкретный item;
- если уже есть `ACTIVE` экземпляр по тому же entitlement, reclaim запрещён;
- старый instance переводится в `REPLACED_AFTER_LOSS`;
- создаётся новый `unique_item_id`;
- создаётся новый `ACTIVE` instance;
- новый предмет выдаётся игроку;
- пишется audit.

Если старый предмет всплывает после reclaim, он должен уходить в invalid path:

- `DELETED_AS_INVALID`

### 7.5. What counts as reclaimable loss

Внешней потерей считаются:

- death;
- lava;
- fire;
- void;
- despawn;
- server cleanup;
- баг удаления.

Такие случаи переводят instance в:

- `LOST_RECLAIMABLE`

### 7.6. What is not reclaimable

Бесплатный reclaim запрещён для:

- `BROKEN`
- `CONSUMED`

Если предмет сломался по durability или был израсходован своей механикой, это не внешняя потеря.

## 8. AR Shop Foundation

### 8.1. Separate AR branch

AR shop обязан быть отдельной веткой catalog/runtime:

- отдельный `source = AR_SHOP`;
- отдельные цены;
- отдельные purchase rules;
- отдельный claim flow;
- отдельные repair rules.

AR item не является donation item, даже если визуально или технически похож.

### 8.2. AR separation rules

`AR` и `Donation` не смешиваются:

- AR catalog не покупается за donation balance;
- donation catalog не покупается за AR;
- ledger не смешивается;
- history не смешивается;
- API endpoints не смешивают валюты;
- in-game GUI явно показывает, где AR, а где Donation.

### 8.3. AR repair policy

Ремонт разрешён только для AR items и только по правилам `Artifacts`.

Нужно запретить обход через:

- `Mending`
- anvil
- grindstone
- smithing table
- crafting table
- другие functional/processing blocks, если они могут обойти durability contract

### 8.4. AR item contract

AR items должны использовать такой же строгий источник истины:

- PDC;
- `unique_item_id`;
- `owner_uuid`, если предмет owner-bound;
- DB status;
- catalog item id.

Никакая AR логика не должна опираться только на display name/material.

## 9. In-Game UX Split

### 9.1. Donation root GUI

Donation root GUI должен иметь максимум 5 основных кнопок:

- `Каталог`
- `Донат-баланс`
- `Пополнить`
- `Мои покупки`
- `Вернуть утерянные предметы`

### 9.2. Catalog behavior

Каталог показывает:

- предмет;
- цену;
- эффект;
- cooldown;
- текущий статус.

Если предмет не куплен:

- клик не покупает в игре;
- игрок получает приватную ссылку на страницу товара на сайте.

Если предмет куплен, но не выдан:

- показывать `Можно забрать`.

Если у игрока уже есть `ACTIVE` экземпляр:

- показывать `Уже у тебя`.

### 9.3. Top-up GUI

Раздел `Пополнить`:

- показывает fixed packs `50 / 100 / 250 / 500 / 1000`;
- создаёт `payment_session`;
- пытается показать QR через локальный renderer;
- если render недоступен, показывает fallback:
  - ссылку на сайт оплаты;
  - session code.

### 9.4. Purchase split

- пополнение можно стартовать в игре и на сайте;
- покупка donation item выполняется только на сайте;
- claim/reclaim выполняются только в игре.

## 10. Website Scope for This Pass

### 10.1. Player cabinet minimal commerce scope

Сейчас на сайте достаточно минимально рабочего commerce-flow без редизайна.

Нужны 4 player screens:

- `Донат-баланс`
- `Донатная лавка`
- `Мои донат-предметы`
- `История`

### 10.2. Canonical payment point

Сайт является канонической точкой:

- оплаты;
- покупки donation items;
- просмотра history;
- статуса payment sessions.

После покупки игрок должен видеть:

- `Заберите предмет в игре`

### 10.3. Minimal frontend glue only

Разрешены только минимальные frontend правки:

- wiring существующих endpoints;
- working top-up flow;
- working donation shop flow;
- working purchase-intent flow;
- working history/claims rendering.

Большой visual redesign сейчас запрещён.

## 11. Admin-Web Scope for This Pass

### 11.1. Donation admin sections

В admin-web нужны рабочие разделы:

- `Donation Balance`
- `Mock SBP Sessions`
- `Donation Shop`
- `Claims`
- `Provider Settings`

`Provider Settings` показывает:

- текущий provider: `MOCK_SBP`;
- future-provider slot;
- без показа секретов.

### 11.2. Plugin registry sections

Plugin registry в backend должен предоставлять foundation для будущей admin UI.

Нужны backend endpoints для:

- registry list;
- status;
- config;
- schema;
- validate;
- apply;
- reload;
- backup;
- audit.

Frontend может пока использовать это минимально или не полностью, если backend готов и безопасен.

## 12. Plugin Registry Safety Contract

### 12.1. Allowlist only

Разрешены только:

- allowlisted plugin IDs;
- allowlisted config keys.

Никакой arbitrary path editing недопустим.

### 12.2. Apply flow

Любое `apply` обязано:

1. Проверить plugin id по allowlist.
2. Проверить keys по allowlist.
3. Провалидировать payload по schema.
4. Создать backup.
5. Записать audit.
6. Только потом применить конфиг.
7. Выполнить controlled reload, если он разрешён для конкретного плагина.

### 12.3. Forbidden behavior

Запрещено:

- raw-config editing;
- arbitrary file write;
- arbitrary plugin reload;
- доступ к секретам через registry API;
- запись неallowlisted ключей;
- обход schema validation.

## 13. Canonical Tables and Statuses

### 13.1. Canonical tables

Канонические таблицы для этого pass:

- `donation_accounts`
- `donation_balance_ledger`
- `donation_payment_sessions`
- `donation_purchases`
- `donation_item_claims`
- `artifact_item_instances`

Catalog может оставаться runtime/config-owned, но должен иметь стабильный `catalog_version`.

### 13.2. Status matrix

Payment sessions:

- `CREATED`
- `PENDING`
- `PAID`
- `CANCELLED`
- `EXPIRED`

Purchases:

- `PAID`
- `CLAIM_PENDING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

Claims:

- `UNCLAIMED`
- `RESERVED`
- `DELIVERING`
- `CLAIMED`
- `DELIVERY_REVIEW`
- `CANCELLED`

Instances:

- `ACTIVE`
- `LOST_RECLAIMABLE`
- `REPLACED_AFTER_LOSS`
- `BROKEN`
- `CONSUMED`
- `DELETED_AS_INVALID`

## 14. Security and Idempotency Requirements

### 14.1. Idempotency

Идемпотентными должны быть:

- mark-paid;
- top-up credit;
- purchase-intent;
- claim transition;
- reclaim transition;
- admin test purchase, если он остаётся.

### 14.2. SQL and transaction safety

- все SQL параметризованные;
- state-changing операции используют row lock или эквивалентную atomic transition;
- на один entitlement нельзя получить два `ACTIVE` экземпляра;
- повторный `mark-paid` не даёт двойного баланса;
- старый предмет после reclaim не должен снова стать валидным.

### 14.3. Auth / CSRF / rate limit

Все state-changing endpoints требуют:

- auth;
- CSRF;
- role check;
- rate limit там, где есть risk surface.

Rate limit обязателен минимум для:

- create session;
- purchase-intent;
- admin mark-paid;
- admin top-up;
- admin test purchase.

### 14.4. Audit rules

Audit писать на:

- create payment session;
- mark paid;
- cancel session;
- add balance;
- purchase;
- claim reserved;
- claim delivered;
- delivery review;
- reclaim issued;
- invalid/fake item cleanup;
- plugin registry apply;
- plugin registry reload;
- plugin registry backup.

Нельзя логировать:

- QR payload secrets;
- provider secrets;
- private tokens.

## 15. Minimal API Surface for This Pass

### 15.1. Player donation endpoints

Ожидаемый минимальный surface:

- `GET /api/player/donation/balance`
- `GET /api/player/donation/history`
- `GET /api/player/donation/packs`
- `POST /api/player/donation/sbp/session`
- `GET /api/player/donation/sbp/session/{sessionId}`
- `GET /api/player/donation/sbp/session/{sessionId}/qr.png`
- `GET /api/player/shop/donation-items`
- `GET /api/player/shop/donation-items/{itemId}`
- `GET /api/player/shop/owned`
- `POST /api/player/shop/purchase-intent`

### 15.2. Admin donation endpoints

- `GET /api/admin/donation/overview`
- `GET /api/admin/donation/sessions`
- `POST /api/admin/donation/add-balance`
- `POST /api/admin/donation/test-purchase`
- `POST /api/admin/donation/sbp/session/{sessionId}/mark-paid`
- `POST /api/admin/donation/sbp/session/{sessionId}/cancel`
- `GET /api/admin/shop/donation-items`

### 15.3. Plugin registry endpoints

Minimal backend registry surface:

- `GET /api/admin/plugins/registry`
- `GET /api/admin/plugins/{pluginId}/status`
- `GET /api/admin/plugins/{pluginId}/config`
- `GET /api/admin/plugins/{pluginId}/schema`
- `POST /api/admin/plugins/{pluginId}/validate`
- `POST /api/admin/plugins/{pluginId}/apply`
- `POST /api/admin/plugins/{pluginId}/reload`
- `POST /api/admin/plugins/{pluginId}/backup`
- `GET /api/admin/plugins/{pluginId}/audit`

Имена можно уточнить в implementation plan, но контракты должны остаться такими по смыслу.

## 16. Delivery Rules for This Pass

### 16.1. Allowed structural work

Разрешено:

- усиливать существующие сервисы;
- добавлять таблицы/индексы/миграции, если они нужны для donation / AR / registry;
- минимально рефакторить backend/service layer ради ясных границ;
- минимально править frontend для рабочих flow.

### 16.2. Forbidden structural work

Запрещено в этом pass:

- переписывать весь `main.py` ради красоты;
- переписывать весь `app.js` ради нового дизайна;
- строить новую admin panel с нуля;
- смешивать donation и AR economics;
- уводить `Artifacts` в автономную экономику;
- добавлять небезопасный config editor вместо registry.

## 17. Done Criteria

Pass считается готовым, когда:

1. Donation top-up flow работает end-to-end на `MOCK_SBP`.
2. Fixed packs `50/100/250/500/1000` соблюдаются в API и GUI.
3. `mark-paid` идемпотентен.
4. Purchase-intent списывает только donation balance и создаёт purchase/claim.
5. Claim lifecycle безопасно проходит до `CLAIMED` или `DELIVERY_REVIEW`.
6. Reclaim работает только для `LOST_RECLAIMABLE`.
7. Donation items owner-bound и проходят anti-dupe/anti-fake checks.
8. AR shop отделён от donation balance и имеет свои purchase/repair rules.
9. Plugin registry backend работает только по allowlist и schema.
10. Минимальные player/admin web flows рабочие без большого redesign.

## 18. Next Step

После подтверждения этого spec:

1. Пишется detailed implementation plan.
2. Только потом начинается код.

