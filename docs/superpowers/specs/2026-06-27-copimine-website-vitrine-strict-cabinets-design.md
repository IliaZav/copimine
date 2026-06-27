# CopiMine Website Redesign

Date: 2026-06-27

Topic: `Серверная витрина + кабинетная строгость`

## 1. Subject

CopiMine is a Minecraft server site with two very different jobs:

- public pages should work like a premium server showcase;
- authenticated pages should work like a disciplined operating console.

Audience:

- new players looking for the server, rules, mods, and entry points;
- existing players using bank, shops, and history pages;
- admins and junior admins working through operational panels.

Single job of the redesign:

- send each person into the correct flow quickly, without one giant mixed dashboard and without generic “AI site” patterns.

## 2. Design Direction

Chosen direction: `Серверная витрина + кабинетная строгость`.

This means:

- public pages feel branded, spacious, memorable, and deliberate;
- cabinets feel more exact, denser, and more controlled;
- both layers share one brand system, but they should not feel like the same product surface.

The site must not look like:

- a generic SaaS dashboard;
- a Minecraft fan wiki;
- a one-page landing where every system is stacked on the homepage;
- a neon dark template with random glow everywhere.

## 3. Visual Thesis

The public face of CopiMine should feel like a clean exhibition wall for a private server project: quiet warm light, large heavy type, one strong green action color, and branded art used as the signature element.

The cabinet side should feel like stepping behind that wall into a control room: more compact, more structured, more status-oriented, but still visually tied to the same brand.

The signature element is the CopiMine brand pair:

- square logo used as the “system seal”;
- wide title banner used as the public hero anchor.

## 4. Token System

### Color

- `#f4f1e6` — base page field
- `#fbfaf6` — card and panel surface
- `#173221` — primary ink
- `#2fd66b` — action accent
- `#7f8f84` — quiet support text
- `#dfe8dc` — borders and soft separators

Optional support tones:

- `#edf5ef` — pale green wash for hero media and status emphasis
- `#0e1a14` — deepest text for cabinet emphasis only

### Type

- Display role: dense, wide grotesk with high weight for hero titles and section heads
- Body role: neutral humanist sans for reading and interface copy
- Utility role: narrower uppercase face for labels, route chips, meta rows, and statuses

Type rules:

- hero text is bold, compressed, and minimal;
- support text stays short and literal;
- labels are uppercase only when they are truly structural;
- no faux-marketing filler.

### Shape and spacing

- large radii for public surfaces
- slightly tighter radii for cabinet surfaces
- generous outer spacing on public pages
- tighter vertical rhythm inside cabinets

## 5. Layout Concept

### Public pages

Public pages are separate route-first screens, not one mega-homepage.

Each public page follows this structure:

1. compact top navigation
2. thesis hero
3. one or two supporting sections only
4. direct actions to the next page

ASCII wireframe:

```text
+-----------------------------------------------------------+
| logo | nav nav nav nav                     theme / login   |
+-----------------------------------------------------------+
| hero copy                         | hero media / summary  |
| headline                          | branded card          |
| short text                        | route details         |
| primary actions                   | one status/action     |
+-----------------------------------------------------------+
| supporting section: process / route / catalog preview     |
+-----------------------------------------------------------+
| second section: cards or page-specific details            |
+-----------------------------------------------------------+
```

### Cabinets

Cabinets are stricter:

- persistent sidebar
- concise topbar
- information grouped into operational panels
- visual hierarchy driven by tasks, not decoration

ASCII wireframe:

```text
+----------------------+------------------------------------+
| brand seal           | page title        actions/status   |
| nav group            +------------------------------------+
| nav items            | metrics / summary                  |
| nav items            | panels / tables / actions          |
| footer status        | panels / tables / actions          |
+----------------------+------------------------------------+
```

## 6. Page Roles

### `index.html`

Job:

- orient the visitor;
- push them into the correct route.

Must contain:

- short route map;
- server entry;
- shops entry;
- mods entry;
- sign-in entry.

Must not contain:

- giant mixed server dashboard;
- full bank data;
- full catalogs;
- admin-like detail blocks.

### `server.html`

Job:

- show server state and core public status.

Must contain:

- address;
- online state;
- player presence summary;
- president and treasury summary if public;
- resource pack or client notice only if relevant.

### `shops.html`

Job:

- explain that AR and donation are separate;
- route player into the correct shop flow.

Must contain:

- AR shop preview;
- donation shop preview;
- purchase flow explanation;
- explicit separation of balances and issuance.

### `mods.html`

Job:

- act as a product page for the client archive.

Must contain:

- download CTA;
- archive size;
- SHA1;
- loader and Minecraft version;
- exact bundled file list;
- notes about optional graphics mods.

It must work even when the live API is unavailable by using a static snapshot generated from the current bundled archive.

### `signin.html` and `register.html`

Job:

- do exactly one thing each.

Rules:

- one centered auth card;
- no role switcher;
- no “choose player/admin” surface;
- no explanatory marketing block around the form;
- short human text only.

## 7. Cabinet Rules

All cabinet pages should inherit one base shell but visually split by context:

- player pages use warmer and calmer surfaces;
- admin pages use more structured, more exact surfaces;
- junior admin pages inherit admin structure but get slightly softer emphasis.

Rules:

- sidebar branding uses the square CopiMine logo instead of `CM`;
- topbar stays narrow and operational;
- cards should not look like homepage showcase cards;
- no decorative hero sections inside cabinets unless the page truly needs them.

## 8. Copy Rules

The redesign must remove text that sounds machine-generated, over-explains the system, or talks like a product brochure.

Allowed voice:

- direct;
- literal;
- short;
- operational.

Forbidden patterns:

- “всё собрано в одном месте”
- “только реальные данные сервера”
- “никаких лишних блоков”
- “каноническая точка”
- generic safety claims that do not help the user act

Preferred style:

- name the action;
- name the destination;
- say what the page is for;
- stop.

## 9. Motion

Motion is allowed, but only in one coordinated layer:

- soft page-load rise;
- gentle hover lift on public cards;
- restrained glow pulse around branded accents;
- reduced-motion fallback must stay intact.

Cabinets should use less motion than public pages.

## 10. Real Aesthetic Risk

The chosen risk is to let public pages stay bright, spacious, and almost editorial instead of hiding the site inside a safer dark gaming template.

Why this risk is justified:

- it separates CopiMine from generic “server landing pages”;
- it makes the brand art carry the identity instead of random neon decoration;
- it makes the cabinet transition feel clearer and more intentional.

## 11. Implementation Boundaries

This redesign pass is frontend-first.

It may change:

- public HTML files;
- cabinet shell markup;
- shared CSS token/theme/layout files;
- public frontend JS renderers where route structure or static fallbacks need support.

It should avoid unnecessary backend rewrites unless a page literally cannot function without a small supporting endpoint or static data bridge.

## 12. Planned Build Order

1. refine shared design tokens and public/cabinet split
2. finish public pages
3. finish auth pages
4. normalize cabinet shell and branding
5. tighten responsive behavior on mobile
6. run browser screenshots and iterate

## 13. Self-Review

Placeholder scan:

- no TBD items remain

Consistency scan:

- public pages and cabinets have different roles but one shared brand system

Scope scan:

- focused on website redesign only, not a full backend rewrite

Ambiguity scan:

- the main visual direction is explicit: public showcase plus strict cabinet surfaces
