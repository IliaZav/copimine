# CopiMine Launcher control plane and CMS design

**Status:** Approved for implementation under the previously approved CopiMine Launcher v1 architecture. This slice adds the web control plane; it does not deploy to production.

**Scope:** Give the CopiMine site a safe administrative surface for Launcher releases, managed client mods, patch notes, and distribution statistics. Keep the existing native Launcher contracts compatible and make local/staging distribution reproducible.

## Outcome

An administrator can open a dedicated **Launcher** section in the cabinet and:

- see the current published release, manifest health, managed mods, package status, and distribution statistics;
- upload a client mod, edit its metadata, replace its binary, or remove it from the next draft release;
- validate a draft before publication;
- publish an immutable signed instance release and matching public metadata/news contracts;
- roll back the public Launcher pointer to a previous immutable release;
- edit and publish normal patch notes in a dedicated CMS news editor.

The native Launcher continues to consume:

```text
GET /launcher/stable/instance-manifest.json
GET /launcher/stable/instance-manifest.sig
GET /launcher/files/<sha256>
GET /assets/public-data/launcher/latest.json
GET /assets/public-data/patches/index.json
GET /assets/public-data/patches/<slug>.json
```

## Safety and deployment boundaries

- The control plane uses `COPIMINE_LAUNCHER_CONTROL_DIR` (default `admin-web/data/launcher-control`) for state, uploaded binaries, immutable releases, telemetry, and audit JSONL.
- It does not create or migrate tables, update PostgreSQL, touch Minecraft worlds, touch player data, touch AuthMe, or restart Paper.
- Existing admin authentication, role checks, CSRF, and sensitive-action confirmation remain mandatory.
- A published release is immutable. Editing a mod or news item creates/changes a draft only.
- Publishing is allowed only to a configured distribution root (`COPIMINE_LAUNCHER_PUBLIC_ROOT`) and requires a server-side Ed25519 key (`COPIMINE_MANIFEST_PRIVATE_KEY_HEX` or a protected key file) plus its public-key id. The private key is never returned by an API, copied into static output, or sent to the Launcher.
- If signing or required artifacts are missing, validation returns a structured failure and publication is refused.
- Production upload, nginx changes, Paper restart, gameplay JAR replacement, and live game admission changes remain outside this slice.

## Components

### Backend control module

Create `admin-web/backend/launcher_control.py` with pure validation plus atomic filesystem persistence. It owns:

- safe component ids, versions, filenames, relative paths, URLs, SHA-256 and size limits;
- draft/current/previous release state;
- mod binaries addressed by content hash;
- patch-note records and generated public contracts;
- download/telemetry counters and JSONL audit records;
- release validation, signing, publication, and rollback.

The module may read the existing `artifacts/launcher/Release/instance-current/instance-manifest.json` (or the configured manifest path) to seed the first draft. It preserves non-mod manifest entries and replaces only the managed mod set. It never trusts a browser-supplied checksum: the backend hashes the uploaded bytes itself.

### Backend HTTP surface

Admin endpoints use `require_admin`, CSRF, rate limits, and `X-Copimine-Confirm` for mutations:

```text
GET    /api/admin/launcher
GET    /api/admin/launcher/mods
POST   /api/admin/launcher/mods/upload
PATCH  /api/admin/launcher/mods/{component_id}
DELETE /api/admin/launcher/mods/{component_id}
POST   /api/admin/launcher/release/validate
POST   /api/admin/launcher/release/publish
POST   /api/admin/launcher/release/rollback
GET    /api/admin/launcher/news
PUT    /api/admin/launcher/news/{slug}
DELETE /api/admin/launcher/news/{slug}
POST   /api/admin/launcher/news/{slug}/publish
GET    /api/admin/launcher/stats
```

Public endpoints are read-only:

```text
GET  /api/public/launcher
GET  /api/public/news
GET  /api/public/news/{slug}
POST /api/launcher/telemetry
GET  /launcher/files/{sha256}
```

The telemetry endpoint accepts only bounded event names (`launch`, `reconcile_success`, `reconcile_failure`, `game_exit`), Launcher version, manifest sequence, and diagnostic code. It does not accept player passwords, device ids, hardware ids, mod lists, or raw paths.

### Public contract generation

Publication writes contracts atomically and in this order:

1. immutable content-addressed binaries and release directory;
2. signed `instance-manifest.json` and detached JSON signature;
3. immutable patch detail JSON and escaped HTML detail page;
4. mutable `latest.json` and patch index last.

The generated public files stay schema-compatible with `InstanceManifestDocument`, `PatchFeedParser`, and the current public website. The Launcher public page and news page first try the public API and retain the static contracts as an outage fallback.

### Admin UI

Add a dedicated **Launcher** navigation item and page module rather than mixing release operations into generic server controls. The page has four compact panels:

1. release health and actions (current version, sequence, signature readiness, validate/publish/rollback);
2. managed mods table (component, version, required flag, size/hash, draft status, edit/remove/upload);
3. distribution statistics (installer downloads, manifest/file requests, reconcile successes/failures, recent diagnostic codes);
4. release history and audit trail.

Add a dedicated **Новости** CMS route with fields for title, version, slug, publication time, up to three summary bullets, general/technical/fix change groups, and item-aware entries. The editor uses DOM-safe rendering, clear empty/error states, and the existing cabinet design tokens. It must not use inline event handlers or raw `innerHTML` for user-controlled content.

### Browser and Figma use

The coded cabinet is the source of truth. Product Design audit is performed against a local/staging browser capture at desktop and mobile sizes. Figma is optional documentation only; no external Figma file is created unless a connected target file is explicitly available and useful. The implementation must not depend on Figma availability.

## Error handling and rollback

- Invalid upload: reject before persistence with `LAUNCHER_MOD_INVALID` and field-level details.
- Duplicate component/path: reject with `LAUNCHER_MOD_DUPLICATE`.
- Missing source binary, unsigned manifest, missing key, or mismatched pinned public-key id: `LAUNCHER_RELEASE_NOT_READY` with a stable reason code.
- Failed publication: leave the previous public pointer untouched and retain the draft for repair.
- Rollback: select only an immutable prior release, verify its manifest/signature/file inventory, atomically switch the public pointer, and record the actor/release ids in control-plane audit JSONL.
- Download failures and client diagnostics are counted without exposing raw exception text or secrets.

## Tests and acceptance evidence

TDD slices cover:

- path/id/version/hash/size validation and traversal rejection;
- atomic state writes and recovery from interrupted writes;
- draft mod add/replace/remove and preservation of non-mod manifest files;
- news validation, escaping, item texture contract, and public feed generation;
- signing fail-closed behavior and successful ephemeral-key staging;
- publish ordering, immutable release retention, and rollback;
- admin endpoint auth/CSRF/confirmation/rate-limit behavior;
- public API/static fallback compatibility with the native Launcher;
- download and telemetry statistics aggregation;
- browser desktop/mobile UI smoke and no-console-error checks.

No acceptance record is created in this slice. It is created only after the full Launcher release gate from `2026-08-15-copimine-launcher-v1-design.md`, including clean-machine installation, update/rollback, Minecraft launch, local server, and the three protected regression gates.

