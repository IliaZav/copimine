# CopiMine website production implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the supplied CopiMine design as a working authenticated website and administration panel on the test host, with a website-only GitHub branch.

**Architecture:** Modular FastAPI application, PostgreSQL website records, protected game adapter based on the existing backend contracts, supplied frontend with a production binding. Mail and VK are prepared without pretending unconfigured providers work.

**Tech Stack:** Python 3.12+, FastAPI, psycopg, PostgreSQL 16, vanilla JavaScript/CSS, Nginx, systemd, Postfix/OpenDKIM, Paper 26.3/Java 25 for non-event integration.

**Spec:** ../specs/2026-10-23-site-production-design.md

## Global Constraints

- Existing local game checkout, database, secrets and worlds remain unchanged.
- End Rift code, JARs, configuration and activation are excluded.
- GitHub branch contains only website sources, assets, tests and deployment files.
- Browser state cannot authorize actions or set balances/inventory.
- Demo credentials, review inbox and standalone offline HTML are never published.
- Provider credentials are absent: prepare email/VK and report exact setup steps.

## Review Focus

- Anonymous/static requests must not disclose private catalog, identities or demo data.
- CSRF, expired/revoked sessions and lower-role actors must fail before a mutation.
- Duplicate financial/game requests and stale inventory snapshots must not double-apply.
- Game/provider outages must produce actionable failure or pending states.
- Mobile admin dialogs and form failures must preserve input and keyboard access.

### Task 1: Website storage, authentication and protected API

**Files:** backend/config.py, db.py, security.py, auth.py, app.py; tests/test_auth.py.
**Interfaces:** create_app(settings) -> FastAPI; Database.transaction(); current user/session dependency; JSON errors with code/message.

- [ ] Add API integration tests for real login/logout, secure cookies, invalid CSRF, role boundaries and persistence; observe missing implementation failures.
- [ ] Implement persistent storage, password hashing, opaque sessions, challenge/outbox, RBAC, origin/CSRF and bounded attempts.
- [ ] Run the same API integration tests and commit the independently working foundation.

### Task 2: Game, finance and administration contracts

**Files:** backend/game.py, domain.py, admin.py, legacy/; tests/test_game_contracts.py, test_admin.py.
**Interfaces:** GameAdapter.health/account/catalog/action/checkout/transfer; versioned v6/v7 JSON consumed by the archive shell.

- [ ] Add failing tests for guest/player catalog filtering, idempotency, unavailable game and hierarchy.
- [ ] Reuse existing game transaction contracts with environment-selected game root; implement account/profile, catalog, support, CMS, audited admin actions and capability reporting.
- [ ] Check plugin sources/contracts and ship only six non-event runtime plugins; isolate any required plugin repairs from the website branch.
- [ ] Run focused tests and retain exact game artifact/source provenance.

### Task 3: Production frontend and UI verification

**Files:** frontend/index.html, assets/js/production.js, production.css and selected supplied modules; tests/test_static.py and browser checks outside the repository.
**Interfaces:** same-origin v6/v7 APIs, production-only bootstrap without demo identities.

- [ ] Add failing tests that reject offline HTML, demo accounts and private-state publication.
- [ ] Replace local review transports/state with authoritative API data; retain supplied layout and improve forms/loading/errors/mobile behavior.
- [ ] Exercise public, account and admin flows in desktop/mobile browsers and compare screenshots with the supplied template.

### Task 4: Mail and VK foundations

**Files:** backend/mail.py, vk.py; deploy/mail.sh; docs/PROVIDER_SETUP_RU.md; tests/test_providers.py.
**Interfaces:** durable mail outbox, server-only SMTP settings; VK availability/start/callback with state and PKCE.

- [ ] Add failing tests for missing provider configuration, expiring verification, retries and single-use OAuth state.
- [ ] Implement provider foundations and loopback SMTP/DKIM configuration; produce public DNS records and account setup instructions without publishing secrets.
- [ ] Verify local transport and unavailable-provider UX; do not claim real external delivery/login.

### Task 5: Deploy, review and publish

**Files:** deploy/install.sh, website.service, nginx.conf, .env.example, README_RU.md.

- [ ] Deploy verified code and game dependencies with server-only credentials and restrictive service permissions.
- [ ] Verify HTTPS, DB persistence, service restarts, game/plugin health, guest restrictions and real browser account/admin interactions.
- [ ] Review the final branch independently, repair important findings, run required checks and push the website-only branch.
- [ ] Report live URL, branch/commit, verified flows and precise mail/VK/user setup still required.

### Added task: BlueMap integration and safe publication

- [ ] Verify official 5.29 compatibility, exact artifact hash and generated configuration keys.
- [ ] Configure six map IDs, public min-inhabited-time=1 and radius=0; private full existing chunks. One render thread; players hidden; internal webserver disabled.
- [ ] Implement durable manual/scheduled jobs, load deferral, restart recovery, renderer completion checks and copy-based immutable content store with atomic promotion.
- [ ] Enforce server-side RBAC for every private asset and isolate public viewer configuration; test direct-path/cache/expired-session failures.
- [ ] Bind existing map page/admin controls to real job states, retain old map during refresh, expose honest empty/error states.
- [ ] Verify real renders, unchanged-chunk behavior, publication and runtime performance; browser at 1440/1024/390/360; keep screenshots outside Git.
