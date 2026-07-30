# Website and Plugin Audit Design

## Goal

Make the CopiMine administration website predictable, readable, and resilient in the paths players and administrators use most: authentication, elections, commerce, lost-item recovery, and account actions. Then audit the Minecraft plugins for failure paths that can disrupt gameplay.

## Scope

- Inspect public pages and authenticated cabinet routes in a real browser.
- Verify frontend-to-backend contracts, validation, error feedback, and state transitions.
- Repair defects discovered by reproducible tests or source review.
- Improve only UI areas whose usability, accessibility, responsive layout, or error handling is demonstrably weak.
- Audit all plugin source modules after the website work, prioritising Elections and Shop/LostItems.

## Explicit non-goals

- Do not connect or activate a real payment provider.
- Do not change worlds, player data, or production server state.
- Do not replace the site visual identity wholesale; improvements must preserve the existing CopiMine cabinet structure and language.

## UX and reliability rules

- A valid user action must end with a specific success state or an actionable business explanation. A generic error code is never normal control flow.
- Buttons that mutate data must disable while the request is active and recover cleanly after a failed request.
- Route, permission, and field validation must agree between browser and backend.
- Status, destructive actions, and confirmations must remain understandable without colour alone and usable on narrow screens.
- Election state changes stay manual where the election rules require manual control; the UI must explain unmet candidate or phase requirements.
- Lost-item recovery remains an item-preservation mechanism, not a payment or inventory duplication mechanism.

## Visual direction

The site remains a dark, game-adjacent control cabinet. The audit favours clearer hierarchy over decorative redesign: compact page titles, distinct state badges, readable empty states, predictable action placement, keyboard-visible focus, and responsive panels that do not hide essential election or recovery controls.

## Evidence required before completion

- Targeted automated regression tests for each changed logic path.
- Existing backend and frontend contract suites pass.
- Java plugins compile successfully.
- Browser inspection covers public pages, login boundary, and all accessible non-destructive cabinet flows.
- A source-level audit records and resolves high-confidence failure paths in election and commerce plugins; other plugins receive a build and risky-pattern scan.
