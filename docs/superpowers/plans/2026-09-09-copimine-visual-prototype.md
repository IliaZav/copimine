# CopiMine visual prototype — implementation plan

Date: 2026-09-09
Design spec: `docs/superpowers/specs/2026-09-09-copimine-visual-prototype-design.md`

## Goal

Build a complete standalone offline visual prototype under `design-prototypes/copimine-minimal-2026-09-09/`, without modifying production frontend files. Package it as a ZIP for owner review.

## Task 1 — Inventory and isolated skeleton

- Mirror every current top-level public frontend route needed for visual review.
- Mirror all current cabinet HTML route filenames from `admin-web/frontend/cabinet/`.
- Represent current versioned news pages through one shared article design, with local files for the discovered routes where practical.
- Create an isolated prototype root and README.

## Task 2 — Local visual assets

- Create/gather local photorealistic CopiMine landscape images for hero/auth/launcher/commerce contexts.
- Store all required imagery under `assets/media/` so the prototype works from `file://` with no network.
- Add small local product/avatar/icon visuals as SVG/CSS assets where useful.

## Task 3 — Design system and interactions

- Implement tokens, typography, spacing, layout, components, public shell and cabinet shell.
- Implement responsive navigation, sticky/scrolled header, drawers, copy-IP feedback, reveal-on-scroll, cart feedback, modals/dropdowns, form feedback and subtle microinteractions.
- Implement `prefers-reduced-motion` and visible focus states.
- Centralize all illustrative values in `assets/js/mock-data.js` and label them DEMO ONLY.

## Task 4 — Public pages

Build polished standalone pages for:

- Home
- Server
- Elections
- Shops
- Product detail visual state
- Cart
- Launcher
- News index
- News article template(s)
- Events
- Sign in
- Register
- 404
- Error
- `mods.html` legacy transition to Launcher
- Launcher utility pages
- Player/admin preview entry pages

## Task 5 — Cabinet/admin route family

Build the shared cabinet/admin shell and tailored visual states for all current cabinet routes:

`admins`, `anticheat`, `artifacts`, `audit`, `balance`, `bank`, `cabinet`, `dashboard`, `demoted`, `donation-balance`, `donation-items`, `donation-shop`, `economy`, `elections`, `history`, `inventories`, `investigations`, `link`, `logs`, `players`, `purchases`, `requests`, `security`, `server`, `settings`, `sources`, `stats`, `support`, `transfer`.

Use purpose-appropriate dashboard, ledger, table, catalog, moderation, security, source, log, form and empty-state patterns instead of duplicating one generic dashboard.

## Task 6 — Offline navigation and demo behavior

- All href/src references are relative.
- No API or backend calls.
- Demo form submits/downloads/checkout/admin writes are intercepted and represented visually.
- Public, commerce, auth, cabinet and admin preview journeys are all navigable locally.

## Task 7 — Automated static validation

Create and run a local validation script that checks:

- every HTML page has `lang=ru`, viewport and title;
- all local `href`, `src`, stylesheet and script references resolve;
- no required external HTTP(S) dependency remains;
- no body-horizontal-overflow-prone obvious path mistakes are introduced;
- `mock-data.js` is present and labeled DEMO ONLY;
- required public and cabinet route files exist.

Run JavaScript syntax checks when Node is available.

## Task 8 — Visual QA

- Render representative desktop pages: home, server, elections, shops, launcher, cabinet dashboard, admin/audit.
- Render representative phone pages around 390 px width.
- Inspect screenshots for hierarchy, cropping, contrast, spacing, overflow, navigation and content density.
- Fix issues and re-render representative pages.

## Task 9 — Final verification and packaging

- Re-run static validation after all fixes.
- Test ZIP integrity.
- Verify entry point is `index.html` and the archive is self-contained.
- Deliver the ZIP in chat with a short usage note.

## Completion criteria

The build is complete only if it is visually coherent, opens offline, includes every current public/cabinet route type required by the approved spec, uses local assets only, has responsive and reduced-motion behavior, passes static reference checks, and the produced ZIP passes archive integrity testing.
