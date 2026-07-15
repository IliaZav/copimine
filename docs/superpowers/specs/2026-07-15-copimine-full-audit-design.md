# CopiMine Full Plugin, Commerce, Items, Elections, and Web Audit

## Goal

Make the replaceable `opt/copimine` tree operationally coherent for Ubuntu Server:

- restore the in-game artifact shop when PostgreSQL is installed;
- map the supplied custom item textures to the exact AR and donation catalog ids;
- remove the protected-block `ItemDisplay` strip/ghost behavior without touching TAB;
- verify and repair all first-party plugins and the web backend, with special attention to elections, economy, custom items, complaints, and bug-report messaging;
- finish the admin-hub storefront tab and refresh the web styles without breaking existing routes or auth contracts;
- produce reproducible builds, validators, checksums, and a Windows-to-Ubuntu full-replacement workflow.

## Scope

### First-party plugins

Audit and repair source plus distributable artifacts for:

- `CopiMineUltimateAdminPlus`;
- `CopiMineArtifacts`;
- `CopiMineEconomyCore`;
- `CopiMineElectionCore`;
- `CopiMineNarcotics`;
- `CopiMineWorldCore`.

TAB is a protected compatibility surface. Its existing configuration and behavior are read, tested, and preserved. Only packaging metadata or checksums may change when required by a reproducible release.

### Custom items and textures

`D:\Downloads\Telegram Desktop\Items Textures.rar` is the authoritative source for custom item textures. The mapping must be explicit and machine-validated:

- `No_Donate` assets map only to AR catalog items;
- `Donate` assets map only to donation catalog items;
- item id, base material, custom model data, model path, texture path, and optional animation frames must agree across `items.yml`, Java item creation, `models_manifest.json`, generated vanilla overrides, and the built resource pack;
- no donation item may remain at `custom_model_data: 0` when its supplied asset is intended to be rendered;
- animated compass/clock assets are assembled with correct Minecraft animation metadata;
- shield base/pattern assets are handled deliberately and do not create a second item mapping;
- existing narcotics, block marker, and TAB assets remain distinct from the supplied item archive.

For every catalog item, audit:

- creation and PDC identity fields;
- owner binding and anti-fake/anti-duplicate checks;
- material/model-data compatibility;
- damage, repair, consumption, death, loss, reclaim, and delivery transitions;
- event handlers, cooldowns, effect bounds, protected-block checks, and inventory edge cases;
- admin/test commands and persistence paths.

### Economy and elections

Trace each state-changing path from command/API/event entrypoint to PostgreSQL transaction and audit record. Verify:

- AR and donation balances cannot cross-spend or cross-credit;
- idempotency, row locking, constraints, rollback, and failed-delivery recovery hold under retries;
- no SQL is assembled from untrusted values;
- elections preserve ballot secrecy, chair/curator authorization, candidate lifecycle, seals, counting, emergency paths, and public output;
- election tables are not writable through generic admin database/editor paths;
- all economy/election player-facing errors are safe, localized, and actionable.

### Tax-clock exemption

The donation artifact `vremya_platit_nalogi_clock` grants the activating player a real-time tax exemption for three calendar months. The expiration is stored as an absolute UTC timestamp and is checked server-side for every tax calculation and tax-office view. Repeated clicks while the same exemption is active return the existing expiration and do not extend it indefinitely through the artifact cooldown.

The president's tax-receipts GUI includes active tax-clock exemptions for their entire validity period, even when the player has not made a normal tax payment. Exemption rows are explicitly marked `TAX_CLOCK_EXEMPTION`, show zero received AR, and display the expiration time; they are never presented as ordinary paid tax. Expired exemptions are excluded from active views and retained only as auditable history if the schema supports it.

The exemption record is durable across plugin reloads and restarts, scoped to the active tax/term where applicable, and protected by an idempotency key based on the player and persistent artifact instance. Tax-clock activation fails closed with a clear player message if ElectionCore is unavailable or persistence fails.

### Complaints and bug reports

Audit the player complaint command and its `a` variant, including command parsing, permissions, rate limits, persistence, duplicate handling, target/player isolation, admin notifications, and audit logging. Trace the complete "bug found" message path:

1. player submits a report;
2. backend/plugin validates and stores it;
3. player receives the result message;
4. admin receives the actionable notification;
5. retries do not duplicate the report or leak internal details.

### Web backend and frontend

Review all first-party FastAPI modules, routes, auth/session/refresh/CSRF/rate-limit handling, PostgreSQL/SQLite selection, plugin registry, uploads/downloads, RCON/systemd helpers, Discord bridge, commerce APIs, error handling, startup checks, and static asset routing. Fix confirmed defects and complete existing stubs/placeholders within those boundaries.

The frontend changes use a shared token/shell system:

- deep spruce `#0E1616` for operational surfaces;
- fog `#F4F7F2` for readable light surfaces;
- mint `#6DF3B1` for success;
- brass `#E4BB63` for economy;
- coral `#FF7A66` for failures and warnings;
- steel `#A5B4C7` for secondary data.

The visual structure is a compact server control room: clear AR/donation split, a server-pulse rail, dense scannable tables, real item icons, keyboard-visible focus, mobile layout, and reduced-motion support. No route contract, TAB surface, auth boundary, or API meaning is changed just for styling.

The public home and admin hub both get a distinct storefront entry. The admin hub main page receives a dedicated `Лавки` tab/section backed by live catalog status; existing cabinet shop pages remain reachable.

### Deployment

All first-party sources, built plugin jars, resource-pack build output, web backend/frontend, tests, and Ubuntu deployment scripts remain under `opt/copimine`. Release manifests and checksums are regenerated only after the final artifacts are verified. The replacement workflow must validate the archive, stop services in a safe order, preserve external PostgreSQL data, install the new tree, restore permissions, and perform a health check.

## Root-cause fixes

### Artifact shop database failure

Create one canonical configuration resolution path for Java and web runtimes. It accepts `COPIMINE_ENV_FILE`, the deployed `/opt/copimine/admin-web/.env`, and explicitly documented systemd environment propagation, with deterministic precedence and a non-secret diagnostic describing the selected source and missing key. The artifact plugin must connect to the existing PostgreSQL database and schema, run the same safe schema migration, load the catalog, and report a bounded actionable error when the connection is unavailable.

Do not silently replace the authoritative economy database with a new local database or a fake purchase path.

### Protected-block visual strips

The artifact shop block itself remains the visual anchor. World `ItemDisplay` overlays are disabled by default and are not spawned or repaired when disabled. Startup, chunk, close, delete, and plugin-disable paths remove only plugin-owned displays identified by PDC, and stale database rows are marked inactive as part of cleanup. TAB textures and font providers are untouched.

### Texture contract

The resource-pack builder owns generation of base-material override files and validates every `(material, custom_model_data)` pair. Java catalog values and generated manifests are checked in tests before a jar or pack is considered releasable.

## Testing strategy

Use TDD for each behavior change:

1. add one focused failing validator/test for the behavior;
2. run it and record the expected failure;
3. implement the smallest root-cause fix;
4. rerun focused tests, then the owning plugin/backend suite;
5. refactor only while green.

Required validation groups:

- PostgreSQL env discovery, schema readiness, shop startup, purchase success/failure, and rollback;
- exact custom item roster, texture/model existence, unique model data, Java/manifest parity, animation metadata, and built zip contents;
- no active protected-block overlay strips after disable/chunk reload/delete;
- AR/donation ownership, loss/reclaim, anti-duplicate, and effect contracts;
- election security and public/private output paths;
- complaint command and `/a` command behavior plus player/admin bug-report messages;
- backend auth, CSRF, role checks, rate limits, safe errors, path/upload/download handling, registry allowlists, and no secret leakage;
- admin hub shop tab and public shop pages at desktop/mobile sizes;
- Ubuntu release structure, hashes, service files, and full replacement dry-run.

Because the user explicitly cancelled the Codex Security workspace scan, the security result will be reported as a manual source-to-sink audit with explicit inspected surfaces and remaining unverified runtime conditions; it will not be described as exhaustive Codex Security coverage.

## Acceptance criteria

- `/cmartifacts` opens and completes an AR purchase against the existing PostgreSQL database on the deployed layout;
- the supplied archive's AR and donation textures render on the correct custom items and no item id is mapped to another item's texture;
- the custom block visual strip/ghost behavior is gone after reload, chunk transitions, deletion, and plugin restart;
- all first-party plugin focused tests pass, including elections, economy, complaints, and bug-report messaging;
- activating the tax clock persists a three-calendar-month UTC expiration, makes the player's due tax zero during that period, and keeps the player visible in the president's GUI as an explicit exemption row;
- backend routes and security self-tests pass without stack traces, tokens, SQL errors, or internal paths exposed to players;
- public site and admin hub have the new style, a working dedicated shop entry, responsive layout, and preserved TAB behavior;
- release artifacts under `opt/copimine` are internally consistent and can be copied to Ubuntu with the generated full-replacement script;
- the final response includes exact Windows commands for collecting server output and the Ubuntu replacement command sequence.
