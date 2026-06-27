# CopiMine Website Redesign / Security Smoke

## Route inventory

### Public

- `GET /api/public/config`
- `GET /api/public/status`
- `GET /api/public/modpack`
- `GET /api/public/president`
- `GET /api/public/president-budget`
- `GET /api/public/president-budget/history?limit=6`

### Auth

- `POST /api/auth/login`
- `GET /api/auth/csrf`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/player/login`
- `POST /api/player/register`
- `POST /api/player/refresh`

### Player cabinet / commerce

- `GET /api/player/me`
- `GET /api/player/bank`
- `POST /api/player/bank/pin`
- `POST /api/player/bank/transfer`
- `POST /api/player/election-tax/pay`
- `GET /api/player/donation/balance`
- `POST /api/player/donation/session`
- `GET /api/player/donation/session/{session_id}`
- `POST /api/player/donation/purchase-intent`
- `GET /api/player/donation/items`
- `GET /api/player/artifacts`

### Admin / operations

- `GET /api/admin/economy/treasury`
- `POST /api/admin/economy/treasury/pin`
- `GET /api/admin/donation/overview`
- `POST /api/admin/donation/add-balance`
- `POST /api/admin/donation/test-purchase`
- `POST /api/admin/donation/session/{session_id}/mark-paid`
- `POST /api/admin/donation/session/{session_id}/cancel`
- `GET /api/admin/plugin-registry`
- `GET /api/admin/plugin-registry/{plugin_id}/status`
- `GET /api/admin/plugin-registry/{plugin_id}/schema`
- `GET /api/admin/plugin-registry/{plugin_id}/config`
- `POST /api/admin/plugin-registry/{plugin_id}/validate`
- `POST /api/admin/plugin-registry/{plugin_id}/backup`
- `POST /api/admin/plugin-registry/{plugin_id}/apply`
- `POST /api/admin/plugin-registry/{plugin_id}/reload`
- `GET /api/admin/plugin-registry/{plugin_id}/audit`

## Public shell checks

1. `index.html` opens without inline script logic.
2. `assets/app.js` stays a tiny module wrapper.
3. Public homepage loads without immediately importing the legacy SPA.
4. Public status cards render only real data or honest unavailable states.
5. President treasury counter animates from backend value.
6. President card handles missing skin gracefully.
7. "Скачать моды" points to `/downloads/CopiMineMods.zip` when the modpack is available.
8. "Скопировать IP" copies the real server address and does not depend on placeholder text.

## Auth / session checks

1. Login requires cookie-auth flow, not bearer tokens in `localStorage`.
2. Refresh uses cookie + CSRF header flow.
3. Logout clears both auth and refresh cookies.
4. Mutation without CSRF returns `403`.
5. `junior_admin` can open only restricted read/admin-lite sections.
6. Full admin actions still require full admin rights.

## Commerce checks

1. Donation balance page shows fixed packs `50 / 100 / 250 / 500 / 1000`.
2. Donation shop purchase-intent never hardcodes prices on the frontend.
3. AR shop clearly states that AR and donation balance are separate currencies.
4. Treasury account is visible only to the president and admins.
5. Public history hides internal ids, raw ledger keys and secret fields.

## Regression focus after each web pass

1. No eager `import "./legacy/app-legacy.js"` in `bootstrap.js`.
2. No inline `onclick=`, `oninput=`, `onsubmit=` in HTML or bundled frontend source.
3. No refresh/access token persistence in browser storage.
4. No fake success or placeholder copy in guest, player or admin UI.
