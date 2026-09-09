# CopiMine visual prototype redesign — design specification

Date: 2026-09-09  
Status: approved visual direction, awaiting written-spec review before implementation  
Repository: `IliaZav/copimine`  
Source branch: `codex/site-launcher-audit`  
Source commit inspected: `87ae7728b21e53867a59c4976804971e2970f2be`  
Source tree: `24cd9fe16f8fbd05a11acce81a027914edb3e3e2`

## 1. Goal

Build a standalone, browser-openable visual prototype of the entire current CopiMine website so the owner can review and approve the redesign before the production frontend is changed.

The prototype must be attractive enough to serve as a visual implementation reference, not a wireframe. It must include finished desktop and responsive mobile layouts, real interactions between prototype pages, and polished motion. The owner should be able to unpack the prototype, open `index.html` locally, navigate through it, and review the whole visual system without a backend, build step, web server, login, database, or external dependency.

After visual approval, these prototype files will be handed to the implementation agent/Codex as the target UI. Production integration is a later task.

## 2. Non-goals

This prototype must NOT:

- modify the production files under `admin-web/frontend`;
- change backend APIs, database schemas, authentication, permissions, shop behavior, election behavior, launcher publishing, CMS logic, or deployment;
- invent production data and present it as real;
- use screenshots as a source of real player names, prices, balances, candidates, online counts, president data, news, event data, launcher versions, checksums, or other dynamic values;
- restore the old modpack page as a first-class product;
- require npm, a development server, a CDN, an API, or internet access to open;
- copy a generic Minecraft server template or use noisy gamer/pixel UI as the primary style.

## 3. Current product structure that the prototype must respect

The source of truth is `codex/site-launcher-audit`, not `main` and not the previous redesign package.

Current top-level public frontend routes include:

- `index.html` — home;
- `server.html` — server/status;
- `elections.html` — elections;
- `shops.html` — public shop;
- `cart.html` — cart;
- `launcher.html` — actual Launcher page;
- `news.html` — news index;
- `events.html` — events;
- `signin.html` — sign in;
- `register.html` — registration;
- `404.html`;
- `error.html`;
- `mods.html` — legacy compatibility/redirect surface, not a redesign target as a modpack product;
- `launcher-link.html` and `launcher-feed-index.html` — launcher-related utility surfaces;
- `preview-player.html` and `preview-admin.html` — existing preview surfaces.

`news/` contains individual launcher/news articles, including versioned release articles such as `copimine-launcher-1-0-0.html`, `copimine-launcher-1-0-1.html`, and later versions. The redesign therefore needs both a news index template and an article-detail template.

`cabinet/` is a multi-route application, not one page. The current branch contains separate surfaces including `dashboard.html`, `cabinet.html`, `balance.html`, `bank.html`, `economy.html`, `donation-balance.html`, `donation-items.html`, `donation-shop.html`, `admins.html`, `anticheat.html`, `artifacts.html`, `audit.html`, `demoted.html`, and additional current cabinet routes. The prototype must establish one coherent cabinet/admin shell and provide a tailored visual state for every current distinct cabinet route when the implementation plan inventories the directory.

## 4. Prototype location and isolation

Implementation will be isolated from production under:

`design-prototypes/copimine-minimal-2026-09-09/`

No existing production page is edited during the visual-prototype phase.

Proposed structure:

```text
design-prototypes/copimine-minimal-2026-09-09/
  index.html
  server.html
  elections.html
  shops.html
  product.html
  cart.html
  launcher.html
  news.html
  events.html
  signin.html
  register.html
  404.html
  error.html
  news/
    article.html
  cabinet/
    ...mirrored current cabinet page names...
  admin/
    overview.html
  assets/
    css/
      tokens.css
      base.css
      components.css
      public.css
      commerce.css
      auth.css
      cabinet.css
      animations.css
      responsive.css
    js/
      mock-data.js
      shell.js
      interactions.js
      animations.js
    media/
      landscapes/
      products/
      avatars/
      icons/
  README.md
```

The final implementation plan may merge CSS files if that improves maintainability, but the conceptual boundaries above must remain clear.

## 5. Approved visual direction

The approved direction is **Landscape Minimal + Editorial**.

It combines:

1. a cinematic, restrained landscape-led opening for public pages;
2. editorial typography and whitespace for long-form/public content;
3. a clean functional application shell for cabinet/admin pages.

### Visual personality

- premium but not luxury-for-luxury's-sake;
- calm, confident, atmospheric;
- recognizably related to Minecraft without looking like a Minecraft UI mod;
- less card-within-card, fewer borders, fewer badges;
- large deliberate typography;
- real hierarchy based on spacing and scale;
- one dominant visual idea per screen.

### Color system

Primary dark surface: graphite/forest-black around `#0B0D0C`.  
Warm light surface: around `#F4F2EC`.  
Primary text on light: near-black.  
Primary text on dark: warm white.  
Accent: muted moss/forest green around `#7E9F72` to `#8BAF79`.  
Success/online may use a somewhat brighter green, but neon green must not become the global accent.  
Secondary text: neutral gray-green.  
Error/danger: restrained brick/red, reserved for destructive/error semantics.

Exact tokens may be tuned during visual implementation, but contrast must remain accessible.

### Typography

Use a modern sans/grotesk family that can be shipped locally or safely fall back to system fonts. Body text must never use a pixel font. A pixel/monospace accent may be used only for tiny metadata such as version/IP labels if it materially improves character.

Headings should feel editorial: large, compact line height, strong hierarchy, no excessive uppercase everywhere.

### Geometry

- primary page max width roughly 1180–1280 px;
- large hero sections with controlled full-bleed imagery;
- card radius approximately 12–16 px;
- compact controls may use 8–12 px radius;
- borders subtle and sparse;
- shadows soft and rare;
- generous vertical spacing;
- minimum interactive target 44 px.

## 6. Background imagery

The public visual identity uses realistic cinematic landscape imagery as atmosphere.

Preference order:

1. find a high-quality, legally usable real landscape photograph that fits the page;
2. if a suitable photo cannot be found, generate a photorealistic landscape;
3. use a local neutral gradient/texture fallback if neither is available.

All chosen/generated images must be copied into `assets/media/landscapes/`, optimized for web, and referenced locally. No hotlinks are allowed because the prototype must work from `file://` while offline.

Preferred scenes: mountain valleys, evergreen forests, rivers/lakes, stone bridges, settlement/castle silhouettes, mist, sunrise/sunset, restrained cinematic light. The imagery may evoke the feeling of a Minecraft world, but it should not look like generic fan art, a wallpaper dump, or a complex fantasy illustration.

A dark/gradient overlay must protect text readability. Image focal points must survive responsive cropping.

Do not put a different giant photo behind every content block. Use imagery as a controlled rhythm: hero, occasional wide separator, auth split panel, or cabinet banner where appropriate.

## 7. Shared public shell

### Header

Desktop:

- sticky/floating header;
- `COPIMINE` wordmark left;
- primary navigation: Главная, Сервер, Выборы, Лавки, Launcher, Новости, Ивенты;
- account action on the right;
- cart appears contextually for commerce, not as visual clutter everywhere;
- current route indicated with a quiet underline/pill transition.

Mobile:

- compact wordmark + menu trigger + contextual action;
- accessible drawer/sheet navigation;
- no compressed 7-link single row.

Header begins transparent/low-contrast over hero imagery and becomes a readable solid/blurred surface after scrolling.

### Footer

Minimal footer with brand, core navigation, server/IP utility, legal/help links that already exist or are later mapped from production. It should not become a multi-column sitemap wall.

## 8. Page designs

### 8.1 Home — `index.html`

Purpose: explain CopiMine to a new player first, then surface live project systems.

Order:

1. cinematic hero: `COPIMINE`, concise value proposition such as “Мир, которым управляют игроки”, one primary CTA to start/play/download Launcher, secondary copy-IP action, compact server status;
2. “Что такое CopiMine” editorial statement;
3. three core systems: economy, elections/governance, player trade/community;
4. current-state strip: online / president / treasury / election state, visually compact;
5. newest news story + two secondary stories;
6. nearest/current event;
7. Launcher CTA;
8. simple join flow/footer.

Avoid duplicating the entire server dashboard on home.

### 8.2 Server — `server.html`

Purpose: operational status and connection details.

Hero: landscape + “Состояние сервера”, copy IP, online/version status.

Content:

- compact status metrics;
- current player list/avatars state;
- president/treasury summary if the real production page exposes them;
- treasury/history or server history panel where supported;
- rules/access/help links;
- clear loading, empty, offline, partial-data states in the visual language.

### 8.3 Elections — `elections.html`

Purpose: make CopiMine governance a distinctive feature.

Content:

- current election phase as the main visual object;
- candidates in spacious cards/list rows;
- vote/result visualization where the current state allows it;
- three-stage process: applications → debates → in-game voting;
- current president/term summary;
- no fake candidate names in production integration.

### 8.4 Shops — `shops.html`

Purpose: a clear marketplace, not a noisy game shop.

Content:

- compact hero/intro;
- search and category/filter bar;
- clear distinction between AR and Donation catalog/balance semantics;
- clean responsive product grid;
- image, item name, seller/source if applicable, price, availability, primary cart action;
- cart indicator animates only when state changes.

### 8.5 Product detail — prototype-only `product.html`

The current production route may render products within the shop rather than a dedicated detail page, but the prototype includes a product-detail state so the design system covers longer product information and purchase decisions.

Content: image, name, price, balance type, seller/source, description, details, availability, CTA, related items.

If production integration ultimately has no distinct product-detail route, this state becomes a modal/drawer/detail component instead of adding an unnecessary public route.

### 8.6 Cart — `cart.html`

Desktop: item list left, sticky order summary right.  
Mobile: single flow, summary below items or sticky bottom CTA when safe.

Support two balance/currency semantics without pretending they are one total. Quantity/removal actions receive subtle motion feedback. Empty-cart state is designed, not an afterthought.

### 8.7 Launcher — `launcher.html`

Launcher is a first-class product and replaces the old “download modpack” concept.

Hero:

- `CopiMine Launcher`;
- concise reason to use it;
- primary “Скачать Launcher” action;
- current platform/version/update metadata region;
- optional secondary “Что нового” action.

Below:

- why Launcher / what it handles;
- latest release/version state;
- simple installation steps;
- system requirements/help;
- latest launcher news/release notes;
- troubleshooting links/states.

The visual prototype may use labeled demo metadata, but production integration must consume the actual launcher data/release pipeline already present on this branch. The old `/downloads/CopiMineMods.zip` concept must not be visually presented as the Launcher.

`mods.html` remains a legacy compatibility surface; its redesign is a minimal redirect/transition page to Launcher, not a new Modpack product.

### 8.8 News — `news.html`

Editorial layout:

- one featured/most recent story;
- chronological feed below;
- restrained category/version metadata;
- strong dates and readable excerpts;
- no uniform wall of identical cards.

### 8.9 News article — `news/article.html` template

A focused reading surface shared by current `news/*.html` pages:

- breadcrumb/back link;
- title, date/version metadata;
- optional lead image;
- readable article body width;
- release notes/changelog blocks if relevant;
- previous/next or “all news” navigation.

All existing versioned news routes can later use this one visual template with their real content.

### 8.10 Events — `events.html`

- current/upcoming event prominent;
- date/status and participation CTA;
- upcoming list/timeline;
- completed events visually separated;
- empty “no upcoming events” state still attractive and useful.

### 8.11 Sign in — `signin.html`

Calm split or centered layout with one atmospheric local image panel, minimal form, clear validation, password recovery link where supported, and registration path. Avoid decorative form cards inside multiple containers.

### 8.12 Registration — `register.html`

Same auth family as sign-in. Clear field hierarchy, rule acceptance where required, password affordances, inline validation, existing constraints preserved during later integration.

### 8.13 404 and error — `404.html`, `error.html`

Minimal branded states with useful recovery actions. They use the design system rather than looking like separate diagnostics pages. Error semantics remain obvious and accessible.

### 8.14 Launcher utility pages

`launcher-link.html` and `launcher-feed-index.html` remain lightweight utility surfaces. They receive the same typography/tokens and a simple purpose-specific layout; they must not be inflated into marketing pages.

### 8.15 Cabinet/player application

The cabinet uses a shared application shell:

Desktop:

- slim left sidebar;
- top context bar/user control;
- page title + contextual action;
- content area optimized for forms, tables, ledgers, lists, and status panels.

Mobile:

- sidebar becomes drawer;
- key actions remain visible;
- tables become responsive lists/cards only where table readability would otherwise fail.

`dashboard.html` is a composed overview; specialized pages such as balance, bank, economy, donation shop/items/balance, admins, anticheat, artifacts, audit and other current routes each get content suited to their purpose rather than the same four fake KPI cards.

The visual system is shared with the public site but cabinet pages use less photography and more functional spacing.

### 8.16 Admin surfaces

Admin mode is not a disconnected “enterprise dashboard theme”. It uses the same tokens and typography with denser components.

Patterns:

- sidebar grouped by domain;
- status/KPI row only where meaningful;
- searchable/filterable tables;
- clear primary vs destructive actions;
- audit/log streams;
- moderation/management forms;
- confirmation modal for destructive actions;
- strong role/state indicators without badge overload.

`preview-admin.html` can be used as one comparison target, while the real current cabinet/admin route set drives the actual prototype states.

## 9. Motion and interaction system

Motion must make the site feel polished, not busy.

### Global motion rules

- transform/opacity are preferred for animation;
- avoid expensive perpetual layout animation;
- typical microinteraction duration: 140–220 ms;
- panel/dropdown transition: 180–280 ms;
- section reveal: 350–650 ms depending on distance;
- easing should feel soft and controlled, not bouncy unless used for a tiny success cue.

### Required interactions

- header surface transition after scroll;
- active-nav indicator transition;
- hero image very slow scale/parallax effect with a small movement range;
- reveal-on-scroll for major sections;
- stagger for small groups, capped so long grids do not animate forever;
- button hover, focus, active/press states;
- cards lift/contrast subtly on hover when clickable;
- online indicator soft pulse;
- copy-IP success feedback;
- cart count/add feedback;
- filters/dropdowns/drawers/modals animate in/out;
- form validation/error/success transitions;
- loading skeleton shimmer or restrained pulse;
- optional count/progress interpolation where useful;
- cabinet sidebar/drawer transition.

No scroll-jacking, no mandatory cursor effects, no excessive tilt, no constant particle field, no content hidden behind long intro animation.

### Reduced motion

`@media (prefers-reduced-motion: reduce)` must remove parallax, large reveals, skeleton shimmer and nonessential transforms while preserving state changes instantly/near-instantly.

## 10. Responsive behavior

Design mobile intentionally rather than shrinking desktop.

Target checks at minimum:

- ~1440 px desktop;
- ~1024 px small desktop/tablet landscape;
- ~768 px tablet;
- ~390 px phone;
- ~360 px narrow phone.

Principles:

- hero type scales with `clamp()`;
- landscape crops retain focal point;
- grids collapse progressively;
- navigation becomes a drawer;
- sticky commerce/admin controls must never cover content;
- tables switch pattern based on semantic readability, not blindly;
- no horizontal body overflow;
- controls remain at least 44 px usable size.

## 11. Accessibility baseline

Even though this is a visual prototype, it must establish production-quality patterns:

- semantic heading order;
- keyboard reachable navigation and interactive elements;
- visible `:focus-visible` treatment;
- sufficient contrast on image overlays and muted text;
- labels for form fields;
- buttons vs links used correctly;
- `aria-expanded`/dialog semantics for prototype drawers/modals where applicable;
- no color-only status meaning;
- alt text for meaningful imagery and empty alt for decorative imagery;
- reduced-motion support.

## 12. Demo-data architecture

The prototype is offline and therefore needs representative content. All prototype values must come from one clearly labeled file:

`assets/js/mock-data.js`

This file must begin with a prominent comment explaining that the values are DEMO ONLY and must never be copied into production data sources.

Demo data may include sample online counts, player names, balances, candidates, products, prices, news/events and Launcher metadata solely to make layouts reviewable.

Every visual number/name shown by the static prototype must be traceable to this demo-data module or to static editorial placeholder copy.

No screenshot is a data source.

## 13. Production integration boundary — mandatory

When this approved visual prototype is later integrated into `admin-web/frontend`, the implementation agent must:

- keep existing API/database/CMS/release sources of truth;
- map the new components onto existing response data;
- preserve IDs/contracts required by current JavaScript unless deliberately migrated with corresponding code/tests;
- preserve authentication, permissions and role gating;
- preserve cart/order semantics;
- preserve election logic;
- preserve current Launcher behavior and publishing/download pipeline;
- preserve current news/events sources;
- implement loading, empty, error, offline and unauthorized states around real responses;
- remove/ignore the prototype demo data.

Specifically, real player names, avatars/skins, product names, product images/textures, prices, stock/availability, balances, president, candidates, votes, treasury values/history, online players/count, server status/version, news, events, Launcher versions/download links/checksums/platform information and admin metrics MUST come from the actual project data/API/config/release system.

If a field is absent from the real system, the integration must omit or redesign that field rather than silently hardcode the prototype value.

## 14. Local prototype navigation

All prototype navigation must use relative links and work from `file://`.

The prototype should include:

- functioning desktop/mobile nav;
- links between home/public pages;
- shop → product → cart journey;
- auth page cross-links;
- public → cabinet preview path;
- cabinet sidebar paths;
- a clear way to open the admin preview;
- back paths from 404/error/demo states.

Interactions that would require a server (login submit, checkout, real download, moderation writes) must be visibly simulated without issuing network requests.

## 15. Visual QA and acceptance criteria

The prototype is ready for owner review only when:

1. every distinct current public page type is represented;
2. Launcher is a Launcher, not a relabeled modpack download;
3. news index and news detail are represented;
4. events are represented;
5. commerce journey is represented;
6. auth states are represented;
7. all current distinct cabinet/admin route types have a coherent page/state in the shared shell;
8. 404/error states are represented;
9. all pages open locally without console-breaking missing dependencies;
10. all local links needed for review work;
11. desktop and phone layouts are deliberate and free of overflow;
12. motion is visible but restrained;
13. `prefers-reduced-motion` works;
14. focus states are visible;
15. no production file is modified;
16. no screenshot-derived value is treated as production truth;
17. all mock data is visibly centralized and marked demo-only;
18. no required background image is hotlinked;
19. the visual language is consistent across public, commerce, auth, cabinet and admin surfaces;
20. the owner can hand the prototype folder to a later implementation agent as an unambiguous visual target.

## 16. Handoff after visual approval

After the owner approves the local prototype, a separate production-integration specification/plan will map each approved prototype component to the current `codex/site-launcher-audit` frontend and real API/data sources.

The prototype itself is not deployed and is not used as a substitute backend.

## 17. Final design decision

Proceed with **Landscape Minimal + Editorial** as one unified CopiMine redesign. Public pages are atmospheric and spacious; commerce is clear and product-led; auth is calm; cabinet/admin are denser but visually related. Use local realistic landscape imagery sparingly, muted forest-green accents, modern typography, polished restrained animation, and an explicit separation between prototype demo values and production data.
