# CopiMine Release UI and Backend Audit Design

## Decision

Use a compact "server control room" direction across the public site, player
cabinet, and admin workspace. The interface keeps the existing routes and
application skeleton, but replaces the current release override layer with a
coherent two-theme system.

The direction is selected from the requirements already supplied by the user.
No additional visual questionnaire is required.

## Alternatives considered

### 1. Server control room (selected)

Operational, readable, and recognizably tied to CopiMine. It uses deep spruce,
fog white, mint, teal, brass, and coral; small radii; pixel assets as accents;
and restrained motion that communicates state.

### 2. Glass biomes

Translucent layers and stronger ambient gradients would look more decorative,
but would make dense admin tables and form states harder to scan and would
increase the risk of low contrast in the two themes.

### 3. Pixel terminal

A stronger retro Minecraft treatment would create a clear identity, but would
reduce readability in long Russian labels, reports, recipes, and financial
tables. Pixel styling is therefore kept for icons and small signature details.

## Current audit findings

- `release-ui.css` globally removes every background image and box shadow with
  `!important`, so page-level gradients, elevation, and focus cues cannot work.
- Public hero grids leave a large unused area above the fold on desktop.
- Public navigation compresses the theme control into unreadable text.
- Cabinet previews report a dark theme while rendering a light surface.
- The mobile admin preview is wider than the viewport and exposes the full
  sidebar instead of a drawer, making the main workspace inaccessible.
- The sign-in page places explanatory copy before the form on mobile and delays
  the primary task below the first viewport.
- Most controls are flat text rectangles. Existing segmented controls and
  selects are inconsistent, while range inputs and real switch controls are
  effectively absent.
- The backend exposes a large route surface through script-based validation but
  has little conventional unit-test coverage. Silent exception branches require
  source review to distinguish deliberate fallback behavior from hidden errors.

## Visual system

### Themes

Only light and dark themes are supported.

- Light: fog background, white work surfaces, spruce text, forest primary,
  teal secondary, brass economy, and coral danger.
- Dark: near-black spruce background, lifted forest surfaces, mint primary,
  cool teal secondary, warm brass economy, and soft coral danger.

Both themes must meet readable contrast for body text, buttons, fields, badges,
and disabled states. Theme changes update `color-scheme`, icons, browser chrome,
showcase imagery, and saved user preference together.

### Geometry and depth

- General radius: 4 px; compact controls may use 3 px; dialogs and major media
  frames may use 6 px.
- Cards remain individual repeated objects or tools, never nested decoration.
- Elevation is limited to navigation, menus, dialogs, focused tools, and hover
  feedback. Tables and page sections rely on borders and tonal separation.
- Gradients use two or three nearby colors. They are allowed in the body
  atmosphere, primary action, balance/status rail, and selected navigation.
  Text always sits on a verified solid or sufficiently opaque layer.

### Typography and icons

Use the existing system font stack for Cyrillic reliability. Keep display type
only for public page titles. Cabinet and admin headings remain compact. Reuse
Minecraft item textures for catalog identity and use the installed icon library
or familiar symbols for actions. Every unfamiliar icon-only action has a
tooltip and accessible label.

## Public site

- Rebalance the first viewport so copy, live server state, primary actions, and
  one authentic server image are visible without a large empty column.
- Keep the next section visible at common desktop and mobile heights.
- Turn the theme action into a compact two-state icon switch.
- Give shops and modpack real catalog/filter controls only where they alter the
  rendered result. Avoid decorative sliders.
- Put sign-in and registration forms before supporting information on mobile.
- Use section transitions and subtle image treatment without hiding content.

## Player cabinet

- Keep financial data, tax status, pending delivery, and reports as the first
  scanning layer.
- Use a compact navigation drawer on mobile and persistent sidebar on desktop.
- Use segmented controls for genuine view modes, switches for binary account
  preferences, and steppers/range inputs only for bounded numeric values.
- Preserve explicit confirmation for transfers, purchases, PIN changes, and
  other state-changing actions.
- Present loading, empty, success, validation, permission, and server-error
  states for every data surface.

## Admin workspace

- Replace the mobile overflowing sidebar with an accessible drawer and sticky
  command bar.
- Keep dense tables, filters, search, role-aware actions, and audit information
  visible without decorative cards.
- Standardize recipe editing, shop management, reports, players, economy, and
  plugin registry around the same form, table, dialog, and confirmation
  primitives.
- Recipe editing must use live backend data, validate bounds before submission,
  persist without a server restart, and show the active revision after save.
- Critical operations remain visually distinct and role-gated; junior admins
  never receive controls they cannot execute.

## Motion

- Page/section entrance: 180-260 ms, opacity plus no more than 8 px movement.
- Hover/focus feedback: 120-180 ms.
- Drawer/dialog transition: 180-220 ms.
- Theme transition: color and border only; images cross-fade without flashing.
- Server pulse is the signature motion and appears only on live status.
- `prefers-reduced-motion: reduce` removes nonessential movement and preserves
  immediate state feedback.

## Backend audit

Trace every represented frontend action to its FastAPI handler, authorization,
validation, persistence, audit record, and returned error contract. Focus on:

- auth, refresh, CSRF, role checks, and session invalidation;
- AR/donation separation, transfers, purchases, pending delivery, and rollback;
- reports and technical bug reports without leaking internal diagnostics;
- shop management and admin-granted deferred items;
- narcotics recipe hot reload and revision consistency;
- elections, tax configuration/payment/exemption, and president views;
- uploads/downloads, filesystem paths, RCON/systemd helpers, Discord bridge,
  database selection, plugin registry, and static routing;
- broad exception handlers that currently suppress failures.

Confirmed defects receive focused regression coverage before implementation.
Intentional optional integrations must log bounded diagnostics rather than fail
silently. Player-facing errors remain safe and actionable.

## Verification

- Run focused regression tests for every changed backend behavior.
- Run the full repository validators, backend smoke test, SQL self-test,
  security self-test, Python compilation, and JavaScript syntax checks.
- Browser-test all public pages plus representative player/admin pages in both
  themes at 1440x900, 1024x768, and 390x844.
- Check horizontal overflow, text clipping, focus visibility, touch target size,
  theme persistence, reduced motion, console errors, failed requests, and real
  control state changes.
- Capture final public, player, and admin screenshots only after those checks.
- Regenerate release artifacts and manifests only after the source tree passes.

## Acceptance criteria

- No global CSS rule suppresses all gradients, shadows, or page-level styling.
- Both themes remain readable and have matching semantic colors.
- No tested viewport has unintended horizontal scrolling or overlapping UI.
- Mobile users can reach primary forms and workspaces without traversing a full
  desktop sidebar or marketing block.
- Added switches, segments, sliders, and steppers control real state and expose
  keyboard and screen-reader semantics.
- Every visible state-changing function has a working backend path, a clear
  result state, and relevant authorization and validation.
- All automated validations pass and final screenshots show the tested build.

