# CopiMine Website Light/Dark Redesign Multiuser Security Smoke

## 1. Theme and shell

1. Open the public homepage in a clean browser profile and confirm the default theme is light.
2. Toggle from light to dark in the public header and confirm the entire shell recolors, not just a few cards.
3. Reload the page and confirm the selected theme persists.
4. Open the authenticated cabinet and confirm the same theme choice is already applied there.
5. Toggle the theme inside the cabinet and confirm the preference persists after another reload.
6. Watch the first paint after reload and confirm there is no flash of the wrong theme.
7. Verify desktop layout, tablet width and narrow mobile width stay readable.
8. Verify reduced-motion mode does not break layout or navigation.

## 2. Public homepage

1. Confirm the hero section renders without placeholder text.
2. Confirm the public treasury counter uses live backend data or an honest unavailable state.
3. Confirm the current president card either shows the current president or a graceful fallback without broken image chrome.
4. Confirm the public treasury history page opens and does not expose internal IDs, secrets or raw HTML.
5. Confirm the modpack section still points to the archive download and does not show fake readiness.
6. Confirm rules/help/sign-in/join sections remain readable in both light and dark themes.

## 3. Auth and session

1. Log in as a player and confirm the cabinet opens without storing access or refresh tokens in localStorage.
2. Refresh the page and confirm the session survives through cookie auth.
3. Log out and confirm the cabinet shell closes and the public shell returns.
4. Log back in and confirm the old player-specific tab state does not leak from another user session.
5. Confirm browser devtools show cookie-based auth and CSRF headers on mutations.

## 4. Player bank and treasury

1. Log in as a normal linked player and confirm only the personal bank account is visible.
2. Log in as a president or admin and confirm the personal/treasury account switcher appears.
3. Switch between personal and treasury accounts and confirm balances, ledger and PIN state change with the selected scope.
4. Confirm treasury PIN is visible only to treasury-authorized users.
5. Change a personal PIN and confirm the form clears and the status refreshes.
6. Change a treasury PIN and confirm the treasury view refreshes correctly.
7. Make a transfer from a personal account and confirm the result appears in the ledger.
8. Make a treasury transfer as an authorized user and confirm the result appears in the treasury ledger.
9. Confirm a normal player cannot reach treasury mutation actions from the UI.

## 5. Donation commerce

1. Open the donation balance page and confirm fixed packs 50 / 100 / 250 / 500 / 1000 are shown.
2. Create a mock SBP payment session and confirm a local QR image appears together with a session code and payment link.
3. Refresh the session status and confirm the page does not duplicate or lose the active session.
4. Confirm the donation balance page clearly states donation is separate from AR.
5. Open the donation shop and confirm prices come from the backend catalog.
6. Buy one donation item and confirm the site says the item must be claimed in game, not on the site.
7. Open “My donation items” and confirm claims, active items and reclaimable states are shown honestly.
8. Confirm broken or consumed items are not presented as reclaimable.

## 6. AR shop and artifacts

1. Open the AR artifacts page and confirm purchase history, pending deliveries and repair history still load.
2. Confirm the AR catalog panel shows backend-driven price, cooldown and limit data.
3. Confirm the AR page clearly states the site does not directly issue physical AR items.
4. Confirm AR and donation states stay visually separated.
5. Confirm the AR page does not imply AR is destroyed or burned during purchases or repairs.

## 7. Roles and admin shell

1. Log in as a junior admin and confirm the limited admin shell loads.
2. Confirm junior admin does not see owner-only destructive controls.
3. Log in as an admin and confirm donation, AR, economy and plugin registry sections load.
4. Confirm admin can view player PIN recovery controls only where role policy allows it.
5. Confirm owner-only or dangerous server/property/resource-pack actions remain hidden from lower roles.

## 8. Plugin registry and managed resource pack

1. Open the plugin registry section and confirm status/config/schema/validate/apply/reload/backup/audit screens load.
2. Confirm no raw arbitrary config editor is exposed.
3. Confirm managed resource-pack controls do not expose arbitrary file-write paths.
4. Confirm unsafe uploads are rejected and safe upload UI remains understandable.

## 9. Multi-user and isolation

1. Open two separate browser sessions under two different users.
2. Change theme in one session and confirm the other session keeps its own account data and session state.
3. Confirm one user cannot see another user’s donation balance, bank ledger, treasury scope or item states.
4. Confirm logout in one session does not break the still-authenticated second session.

## 10. Security sanity

1. Confirm no dead buttons remain on the main public shell, cabinet, donation, AR or treasury screens.
2. Confirm no inline-script CSP errors appear in the browser console.
3. Confirm no player-facing debug, stack trace or placeholder text appears in error states.
4. Confirm failed API calls show a human-readable error instead of raw JSON or a stack trace.
5. Confirm CSRF blocks a mutation when the token is missing or stale.
