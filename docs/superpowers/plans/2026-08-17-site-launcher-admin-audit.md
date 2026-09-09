# CopiMine site and Launcher audit plan

## Scope

This slice covers the public website, the real admin cabinet runtime, and the
static Launcher distribution contract. The Minecraft/Paper process, production
world, player data, AuthMe state, and production databases stay outside the
change set.

## Delivery slices

1. Remove duplicated public calls-to-action and replace every cabinet
   `Модпак` link with the canonical `Лаунчер` route. Make the two shop areas
   distinguishable in the admin navigation and remove the duplicate shop
   search entry.
2. Keep the existing API-first Launcher/news implementation, but align the
   published metadata, installer files, signed instance manifest, and managed
   files. Improve the cabinet status/readability styles without rewriting the
   established layout system.
3. Add contract tests for navigation, route compatibility, installer
   availability/hash, and manifest file completeness. Correct only stale test
   expectations where the repository source of truth already differs.
4. Run the local FastAPI site with synthetic SQLite/admin data, exercise public
   and admin routes through the browser, verify download bytes and hashes, and
   capture before/after UI evidence.
5. Audit production web infrastructure read-only, then publish only the
   validated static site/Launcher distribution through an exact versioned web
   release. Verify HTTP status, content hashes, and nginx configuration without
   restarting or modifying the game service.

## Release gate

No production web upload is accepted while a local route, installer/hash,
manifest, or admin navigation contract is failing. Any production game,
database, player-data, or Paper change is explicitly out of scope.
