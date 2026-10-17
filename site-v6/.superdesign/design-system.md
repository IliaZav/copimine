# CopiMine v6 design system

Continue the existing CopiMine v5 visual direction. This is an iteration, not a new brand.

## Foundation

- Existing system sans-serif stack and Minecraft bitmap assets.
- Dark forest surfaces; retain the existing background, surface, border and green accent tokens.
- Secondary text: #91a397.
- Selected controls use both a visible border and surface fill.
- Tabular figures for balances, quantities, metrics and timers.
- Spacing follows the existing 4/8 px rhythm. Use 16–24 px for related module gaps.
- Corners are generally 8–12 px; reserve larger corners for the map and cookie banner.

## Product rules

- One player profile contains both inventories.
- Ender chest: 9 columns × 3 rows; do not substitute a main-inventory snapshot.
- Administrative actions are grouped by the task. Show target and reason.
- Separate AR, CM and rubles in both layout and calculations.
- Cart quantity updates in place; preserve keyboard focus.
- Donation inputs and consent remain mounted when changing amount or payment method.
- The map leads the layout; settings follow it on narrow screens.
- Use real map imagery and explicit dates/status, never fabricated activity.
- Search updates only its own dialog, supports keyboard navigation and clear empty states.
- Analytics must show the available data and meaningful empty/error states.

## Motion and access

- Interaction responses around 150–220 ms.
- No autonomous decorative movement or full-page animation loops.
- Visible focus, readable labels, touch targets around 40–44 px.
- Respect reduced motion and the site's motion preference.
- All imagery remains local. Do not upload private project material to design services.

## Files

- assets/v6.css: functional layouts.
- assets/css/polish-v6.css: refinement and responsive/accessibility adjustments.
- docs/design-v6.md: decisions and evidence.

