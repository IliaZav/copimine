# CopiMine site and admin UI audit — 2026-08-18

Status: `LOCAL_SCOPE_VERIFIED`

This is a site/admin audit report, not the full CopiMine Launcher v1 release
acceptance record. No production deployment was performed.

## What was fixed

- Unified the public header order and Launcher link across real public routes.
- Removed repeated `tokens.css`, `themes.css` and `release-ui.css` HTML imports;
  `assets/style.css` is now the common entry point.
- Kept `/mods.html` as a compatibility redirect to `/launcher.html`.
- Marked `preview-admin.html` and `preview-player.html` as static previews with
  `noindex` and a direct link to the working sign-in/cabinet flow.
- Added consistent public shell spacing, action heights and preview notice
  styling in `assets/css/ui-audit.css`.
- Changed cabinet startup so CSRF warm-up runs in the background and cannot keep
  a guest on `Подготавливаем кабинет...` before the auth decision.
- Added a safe Sources database summary: PostgreSQL, auth backend, auth DB,
  admin-plugin DB and CoreProtect. Passwords are not rendered.
- Aligned startup diagnostics with runtime auth selection: without an explicit
  PostgreSQL password the local fallback is reported as SQLite.

## Evidence

- Focused site/DB contracts: `18 passed` after the GREEN commit.
- Launcher/public/site-admin pytest set: `93 passed, 21 warnings` (the warnings
  are FastAPI lifecycle deprecations).
- Public navigation regression: `OK`.
- Frontend ↔ FastAPI contract: `routes=249 references=167 direct_calls=167`.
- Frontend runtime self-test: `OK`.
- Auth accessibility regression: `OK`.
- Security self-test: `OK`.
- SQL self-test: `OK`.
- Disposable backend smoke: `OK`; it used a temporary server tree and SQLite.
- Disposable backend security regression: `OK`.
- Disposable Launcher control-plane smoke: `OK`; upload, signed release,
  public manifest, news, telemetry and rollback were exercised.
- Staged site contract: `Site/Launcher audit contracts OK`.
- Installer release verification: `RELEASE_VERIFY=PASS`.

## Release artifact evidence

- Staged site: `artifacts/launcher/Release/site`
- Installer: `artifacts/launcher/Release/packages/CopiMineLauncherSetup-1.0.2.exe`
- Installer SHA-256:
  `335448539db2e776cad5376e7a8ff9802230774520df2d0fbd3216c41b8304c8`
- Installer size: `1420324614` bytes
- MSI: `artifacts/launcher/Release/packages/CopiMineLauncherSetup-1.0.2.msi`
- MSI SHA-256:
  `acd1edde7ed0b51e5a0e1ac5e1ed55e7a83b09838b69a0f89ff95168c80260fd`
- Launcher metadata version: `1.0.2`
- Staged HTTP checks returned `200` for metadata, EXE, MSI, manifest and
  signature.

## Browser evidence

Screenshots are kept under
`artifacts/design-audit/production-20260818` and
`artifacts/design-audit/local-20260818`.

- Local public navigation matched the canonical eight-link sequence on 11
  routes.
- Local Launcher rendered release `1.0.2` and direct Russian copy.
- Preview admin showed the non-operational warning and working-cabinet link.
- Unauthenticated cabinet reached `signin.html` in about 2.2 seconds and no
  longer showed the boot banner.
- Light and dark theme screenshots were captured with no browser console
  errors. Static fallback warnings for missing `/api` are expected on the
  plain local file server; the real backend smoke covered the API contract.

## Database scope

The repository configuration contains these source classes:

1. PostgreSQL — primary V4 site/auth/economy/audit path when explicitly
   configured.
2. SQLite auth DB — local fallback (`COPIMINE_AUTH_DB`).
3. CoreProtect SQLite — `plugins/CoreProtect/database.db`.
4. CopiMineUltimateAdmin SQLite — `plugins/CopiMineUltimateAdmin/` fallback.

The live production connection was not probed in this audit. Therefore live
DB connectivity remains `UNVERIFIED`, and no production database was copied,
migrated or written.

## Remaining limitations

- The default repository-only Launcher audit expects generated static download
  artifacts; the staged release path was verified instead. The stale ignored
  source publish folder still contains the older 1.0.1 binaries.
- Browser automation could not resize the in-app viewport through its exposed
  API, so runtime mobile-width interaction is `UNVERIFIED`; CSS/mobile
  contracts and desktop navigation checks pass.
- No production site upload, nginx reload, Paper restart, gameplay JAR change,
  production world/player-data write or production DB write was performed.
- Full Launcher v1 acceptance remains outside this site/admin slice until the
  separate clean-machine, Minecraft/local-server and protected regression
  gates are run.
