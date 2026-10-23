# CopiMine website production design

The user supplied CopiMine_Site_v7_3_2_Visual (9).zip and authorized implementation,
UI improvements, deployment to the freshly cleared test host, and a GitHub branch
containing only the website. They subsequently asked to prepare email and VK
integration foundations and explain account/DNS setup in the final report because
no provider accounts or application credentials exist yet.

The existing local game checkout and the End Rift work owned by another agent
must remain untouched. No local database, environment file, world, or existing
checkout may be deleted or overwritten. The website lives in a separate Git
repository directory and publishes a new root branch into IliaZav/copimine.

## Approaches

1. Publish the archive's localhost review server: rejected because it contains
   demonstration credentials and simulated game/financial state.
2. Replace the frontend and game integration completely: unnecessary loss of the
   supplied design and existing plugin transaction contracts.
3. Preserve the supplied visual shell; implement production authentication and
   APIs, using the existing website backend's game contracts where appropriate.
   This is the implementer's choice because the user asked to continue execution
   and preserve the template as the basis, rather than discuss alternatives.

## Architecture

FastAPI serves the supplied frontend and same-origin versioned APIs. PostgreSQL
stores website identities, opaque sessions, one-time verification challenges,
outbox messages, settings, support requests and append-only administrative audit.
Unit/integration tests may use a separate SQLite database; production requires
PostgreSQL. Financial balances and Minecraft inventory are never supplied by a
browser workspace or copied from demonstration fixtures.

An adapter uses existing CopiMine contracts for verified Minecraft identities,
economy, catalog and game actions. Runtime configuration, plugin JARs and database
credentials live outside the website repository on the server. A fresh game
runtime may run the six non-event first-party plugins and their dependencies;
End Rift code, JARs, configuration and activation are excluded from this delivery.
An unavailable game operation returns an explicit failure/pending state, never a
simulated success. Item identifiers, quantities, actor hierarchy, request keys and
inventory versions are checked server-side before any mutation.

## Product and access

Preserve the photographic home page, graphite surfaces, green actions, full-height
admin sidebar, gallery and three separate map dimensions. Improve loading/error/
empty states, keyboard focus, form feedback, mobile dialogs and discoverability.
Guests receive no player records, private catalog, secrets, review inbox or demo
accounts. Players see their own account, purchases, transfers, support and settings.
Admin operations require server-issued roles; owner-only recovery checks hierarchy
and fresh authentication. Session cookies are HttpOnly/Secure in production and
mutations require a matching origin and CSRF token. Requests have finite size and
time limits, login and verification attempts are rate limited, and secrets are
excluded from logs and Git.

## Integrations and deployment

Prepare a loopback-only Postfix/OpenDKIM sending transport, retrying website outbox
and DNS instructions. Leave external delivery explicitly unverified until domain
records and a real recipient are configured. Prepare VK ID OAuth authorization
code/PKCE with single-use state; expose availability without fake VK login.
Provider secrets belong only in the server environment. Payments remain disabled
unless a real supported payment provider is configured.

Deploy HTTPS at copimine.ru with a restricted systemd website service and Nginx.
Retain SSH, network and firewall access. Verify DB persistence, session/RBAC/CSRF,
private-data boundaries, game readiness, service restart and browser desktop/mobile
flows against the deployed API. Publish only website sources, assets, tests and
deployment documentation to codex/site-v7-production-20261023.

## Evidence boundary

Automated API tests, live Paper/plugin health, browser interactions and native
Minecraft acceptance are separate claims. Do not claim email delivery, VK login,
payments or native Minecraft gameplay without direct evidence for that layer.
