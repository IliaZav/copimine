# Website and admin UI audit — design contract

## Goal

Make the public CopiMine site and the working cabinet read as one product:
one navigation model, one visual entry point for public styles, one real admin
surface, and explicit data-source status. Remove vague template copy without
changing game rules, player data, payment providers, or production storage.

## Baseline findings

- The live public pages still show different navigation contracts: `/server.html`
  and `/shops.html` expose `Модпак`, while `/index.html`, `/launcher.html`, and
  `/news.html` expose `Лаунчер`/`Новости`.
- The public HTML loads `assets/style.css`, which already imports the common
  public tokens/theme/release layers, and then imports those same layers again
  from each page. This creates duplicate stylesheet requests and contributes to
  visual drift.
- The live unauthenticated cabinet spends the first part of boot on a blocking
  CSRF request and can remain on `Подготавливаем кабинет...` before eventually
  redirecting. A guest needs an immediate, readable route to sign-in.
- `preview-admin.html` is a static sample with hard-coded counts and inert
  controls. It is not the authenticated admin application and must not be
  presented as a second working admin route.
- The backend has a PostgreSQL primary path, a local SQLite auth/fallback path,
  CoreProtect SQLite, and an admin-plugin SQLite file. The UI exposes these
  sources in different places but does not give one compact, non-secret status
  view.

## Approved direction

### Public shell

- Canonical order: `Главная`, `Сервер`, `Выборы`, `Лавки`, `Лаунчер`, `Новости`,
  `Войти`, `Регистрация`, theme control.
- Every real public page uses the same label and href for the Launcher entry.
- `/mods.html` remains a compatibility route only and redirects before painting
  legacy content. Legacy hash routes go to `/launcher.html`.
- `style.css` is the single common public stylesheet entry point. Page-specific
  styles remain explicit; common token/theme/release imports are not repeated in
  HTML.

### Cabinet and admin

- `/cabinet/cabinet.html` and its hash routes are the only working cabinet/admin
  application.
- `preview-admin.html` and `preview-player.html` become clearly labelled
  non-operational previews with a direct link to the real sign-in/cabinet flow;
  their sample numbers and fake actions are not treated as live data.
- Cabinet boot starts the auth check immediately. CSRF warm-up may run in the
  background, but must not block the first auth decision. Any remaining network
  failure ends in a visible retry/sign-in state within a bounded timeout.
- The Sources view gets one database summary card with safe values only:
  PostgreSQL state/database/schema, selected auth backend, legacy admin SQLite
  path, and CoreProtect path/status. Passwords, tokens, and connection strings
  are never rendered.

### Visual and copy rules

- Keep the existing Minecraft landscape and item textures, but use a restrained
  control-room layout: one clear hero, one primary action, shorter section copy,
  consistent card spacing, visible focus, readable status colors, and a mobile
  nav that opens one menu only.
- Use direct Russian copy. Remove filler such as “здесь собраны”, “комплексная
  платформа”, “бесшовный”, “погрузитесь”, and operational release jargon from
  public-facing prose when it does not help a player decide or act.
- Do not remove useful technical facts such as Minecraft/Fabric versions,
  installer hashes, or a concrete error reason.

## Scope and safety

- Local repository and isolated worktree only. No production upload, nginx
  reload, Paper restart, plugin replacement, world write, player-data write, or
  database migration is part of this change.
- Backend checks use synthetic/local fixtures. Production DB is not copied or
  mutated.
- Existing element IDs, cabinet hash routes, auth/session endpoints, Launcher
  API contracts, and dangerous-action confirmation headers remain compatible.

## Acceptance gates

1. Static contract proves canonical public navigation and no duplicate common
   stylesheet imports.
2. Static contract proves compatibility routes and preview labelling.
3. A focused regression test proves cabinet boot does not await CSRF warm-up and
   has a bounded failure path.
4. Frontend-to-FastAPI route contract, JavaScript self-test, auth accessibility,
   public navigation, Launcher/news, and relevant pytest tests pass.
5. Local browser smoke covers public pages, `/mods.html`, unauthenticated
   cabinet, preview routes, light/dark theme, and mobile navigation without
   console errors.
6. Database report lists configured source classes and states without secrets;
   it does not claim production connectivity unless a read-only live check is
   actually performed.
