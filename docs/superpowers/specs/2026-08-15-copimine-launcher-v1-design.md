# CopiMine Launcher v1 Design

**Status:** Approved for implementation by the user on 2026-08-15.

**Scope:** Complete Phase 1 from `D:\Downloads\01_FINAL_copimine_launcher_site_news_luna_prompt_v3.md`: the Windows Launcher, signed instance/update contracts, launch-ticket admission, public launcher/download/news pages, shared patch feed, packaging, local/staging verification, and release evidence. End Rift Event is explicitly out of scope for this branch and will not be implemented.

## Outcome

The release must provide a Windows 10/11 x64 installer that creates and maintains a dedicated CopiMine Minecraft instance without changing the user's ordinary `.minecraft`. The installed instance must use Minecraft `1.21.1`, Fabric Loader `0.19.3`, managed Java 21, the signed CopiMine file set, and the `mc.copimine.ru` server entry. Updates must be crash-safe, resumable, hash-verified, signature-verified, and reversible.

The public user path is:

```text
copimine.ru/launcher.html
  -> signed installer download
  -> Launcher verifies and reconciles a dedicated instance
  -> Launcher requests a short-lived launch ticket when admission is enabled
  -> Minecraft starts with the managed instance
  -> server validates the one-shot ticket in ENFORCE mode
  -> Launcher and copimine.ru/news.html show the same generated patch feed
```

## Non-goals and explicit boundaries

- Do not implement End Rift Event, a boss plugin, Core, runes, waves, or Rift Shard.
- Do not deploy to production, enable production ENFORCE, upload an installer, or publish mutable metadata from this branch.
- Do not use a mod-list `HELLO`, heartbeat, capability comparison, or required-mod-version gate as launcher admission.
- Do not store AuthMe passwords, private signing keys, or server credentials in the Launcher.
- Do not delete or rewrite user-owned files in the dedicated instance; only managed paths may be reconciled.
- Do not replace the existing Java `CopiMineClient` with the Windows Launcher. The Java client remains a managed game mod artifact.

## Repository integration

The current repository is a Java/Paper/FastAPI/static-site project. The new Windows solution lives in a separate top-level `CopiMineLauncher/` directory and is not mixed into Java plugin source. Website and backend changes remain under the existing `admin-web/` conventions. Generated public contracts are checked in under `admin-web/frontend/assets/public-data/` according to the repository's existing static-data policy.

The implementation branch is `codex/copimine-launcher-v1`, based on the current GitHub `main` commit. Existing audit-branch changes are preserved outside this worktree.

## Architecture

### Launcher projects

```text
CopiMineLauncher/
  CopiMineLauncher.sln
  src/
    CopiMineLauncher.Core/             pure contracts, state machine, policies
    CopiMineLauncher.Infrastructure/   HTTP, crypto, filesystem, downloads, NBT
    CopiMineLauncher.App/              .NET 10 WPF/MVVM shell
  tests/
    CopiMineLauncher.Core.Tests/
    CopiMineLauncher.Infrastructure.Tests/
    CopiMineLauncher.IntegrationTests/
  packaging/
  README.md
```

The Core project contains deterministic logic that can run without WPF, a real Minecraft installation, or a server. Infrastructure owns all filesystem, network, process, and cryptographic boundaries. The WPF project only composes services, ViewModels, and views.

### State machine

The Launcher exposes one observable state machine:

```text
Idle
 -> CheckingSelfUpdate
 -> FetchingManifest
 -> VerifyingManifest
 -> PlanningChanges
 -> Downloading
 -> Staging
 -> Committing
 -> Ready
 -> RequestingLaunchTicket (when mode is WARN/ENFORCE)
 -> Launching
 -> Running
```

Any failure enters `RecoverableFailure` with a diagnostic code and recovery action. A failed commit never presents success. A process restart replays the transaction journal and either resumes an uncommitted plan or rolls back to the last committed snapshot.

## Signed manifest and file ownership

The signed instance manifest is canonical UTF-8 JSON. The signature is Ed25519 over the exact manifest bytes; the Launcher contains only the release public key. The private key remains in the release/backend environment and is never packaged into the Launcher or game files.

The manifest contains:

- `schemaVersion`, `product`, `channel`, `sequence`, `launcherVersion`;
- exact `minecraftVersion: "1.21.1"` and `fabricLoaderVersion: "0.19.3"`;
- Java runtime metadata and verified archive hash;
- managed file entries with stable `id`, safe relative `path`, `kind`, `version`, `url`, `sizeBytes`, and lowercase SHA-256;
- server identity (`mc.copimine.ru`, port, display name);
- resource-pack/config metadata;
- `publicKeyId` and signature metadata.

Before any local file mutation the reconciler must verify: JSON schema, signature, sequence rollback policy, URL allowlist, safe relative path, size bound, SHA-256 format, duplicate IDs/paths, and ownership rules.

Ownership is explicit:

- `managed`: the reconciler may add, replace, or remove only when the prior file is also managed and obsolete;
- `user`: never delete or overwrite;
- `merge`: update only the Launcher-owned key/record using a format-aware merger, preserving unknown user data.

## Transactional update and recovery

The update plan is persisted before downloading. Each file is downloaded to a unique temporary path with resumable HTTP ranges where supported, then hash-verified. The reconciler writes a journal containing the old path/hash, staged path/hash, and intended operation. Commit uses same-volume atomic replacement and records a durable committed sequence. On interruption, restart inspects the journal, validates staged files, completes a safe commit or restores the last committed snapshot. A corrupt or partial file is never treated as a valid installed file.

The reconciler must pass contracts for new-file add, changed-file update, managed obsolete-file removal, user-file preservation, corrupt-file repair, partial-download recovery, process-close recovery, rollback after failed commit, path traversal rejection, duplicate manifest entries, and sequence rollback protection.

## Minecraft, Fabric, Java, and servers.dat

The implementation must spike and pin the exact CmlLib.Core version that launches Minecraft `1.21.1` with Fabric Loader `0.19.3`. If the spike proves unsuitable, the infrastructure layer owns a tested equivalent and documents the decision.

Java 21 is installed into the dedicated instance only after archive size/hash/signature verification. Minecraft/Fabric files are provisioned through the same verified manifest boundary.

The `servers.dat` updater reads the existing NBT, preserves every unrelated server and field, adds exactly one CopiMine server record if absent, and updates only the managed record if its address/display metadata changes. It is idempotent and writes through a temporary file plus atomic replacement.

The Launcher never edits the user's normal `.minecraft`. The instance path is deterministic, configurable, and displayed in diagnostics.

## Launcher self-update

Velopack is used only for updating the Launcher executable. Game files, Java, and mods are updated by the signed manifest reconciler. Self-update metadata is independently verified, staged outside the running application, and applied only after the Launcher has a known-good previous version. A failed update starts the prior version and records a recoverable diagnostic.

## Launch-ticket admission

Admission modes are `OFF`, `WARN`, and `ENFORCE`. OFF/WARN are allowed for local and staged rollout only. The production-ready implementation includes a complete ENFORCE path.

The Launcher authenticates through the existing site/account flow without receiving or storing an AuthMe password. The backend issues a ticket only after the account/device flow has identified the player and verified the requested launcher build/manifest identity. The ticket is signed server-side with an Ed25519 private key held only by the backend. The Launcher receives the signed ticket and passes it only to the current Minecraft launch.

Ticket claims include:

```json
{
  "schemaVersion": 1,
  "ticketId": "nonce",
  "playerId": "site-account-or-minecraft-uuid",
  "playerName": "optional-display-name",
  "buildId": "signed-manifest-identity",
  "launcherVersion": "1.0.0",
  "issuedAtUtc": "...",
  "expiresAtUtc": "...",
  "audience": "mc.copimine.ru",
  "nonce": "unique-value"
}
```

The backend persists ticket IDs/nonces as one-shot records and atomically consumes them. The server-side gate verifies signature, audience, expiry, player identity, build identity, and one-shot state. Replays, expired tickets, tickets for another player, malformed tickets, and missing tickets in ENFORCE are rejected with stable diagnostic codes. The gate does not inspect the mod list.

Backend or database failure must not create a silent server-wide lockout. The gate logs a precise diagnostic, exposes health/metrics, and supports an explicit administrative emergency mode transition to WARN/OFF. ENFORCE remains fail-closed for an individual unauthorised login when healthy verification is possible; emergency mode is visible and auditable.

The Paper gate is a separate module (`copimine-launcher-gate`) with Java 21 unit/contract tests and a local Paper integration harness. Existing CopiMineClientBridge HELLO/heartbeat remains a visual capability protocol only and cannot make admission decisions.

## Website and patch contracts

Authoring source is Git-controlled YAML under `content/patches/<yyyy-mm-dd>-<version>.yaml`. `tools/patch_notes/build_patch_notes.py` validates the schema and generates:

```text
admin-web/frontend/assets/public-data/launcher/latest.json
admin-web/frontend/assets/public-data/patches/index.json
admin-web/frontend/assets/public-data/patches/<slug>.json
admin-web/frontend/news/<slug>.html
```

Launcher metadata includes version, architecture, filename, HTTPS download URL, positive size, lowercase 64-hex SHA-256, release-notes URL, and signature metadata. The generated news index is bounded and newest-first. Detail pages render general, technical, bugfix, item, and real known-issue sections in that order.

Item entries use `itemId` as authority. The generator resolves display name and texture from the current artifact catalog/resource-pack source and fails the build when either is missing. No raw network HTML is injected into the site or Launcher; public text is rendered with DOM `textContent` or generated safe HTML.

The public site adds:

- `/launcher.html` as the primary download/install page with real screenshots, requirements, current version, verified metadata, and failure-safe download behavior;
- `/news.html` and `/news/<slug>.html` for the generated patch feed;
- shared patch-feed consumption in the native WPF Launcher with safe `https://copimine.ru/news/` links;
- `/mods.html` compatibility redirect/shell that no longer presents manual ZIP installation as the normal CTA;
- modular public JavaScript initializers and preserved authentication/navigation behavior.

## Testing and evidence

Every behavior is developed RED → GREEN → refactor. The test pyramid includes:

- Core unit tests for schema, canonical signature bytes, paths, ownership, update plans, journal recovery, rollback, servers.dat, ticket claims, expiry, replay, identity, and modes;
- HTTP integration tests using a local test server for manifests, range downloads, corrupt responses, patch feeds, ticket issuance, and outage behavior;
- Python contract tests for generated YAML/JSON/HTML and backend routes;
- Java plugin tests and local Paper integration for ticket ENFORCE and existing AuthMe compatibility;
- WPF/UI smoke tests through stable ViewModel seams and FlaUI/UIA3 where available;
- browser desktop `1440x900` and mobile `390x844` checks for launcher/news/legacy routes, screenshots, accessibility, no overflow, console errors, and exact download metadata;
- clean Windows x64 install/update/rollback tests;
- local Minecraft server launch/join tests;
- fresh ping, bed waypoint/respawn, and AuthEffects authorization-slowness regression gates;
- independent requirement-to-evidence verifier.

No acceptance record is created until every required result is `PASS`; `SKIP` and `UNVERIFIED` are release failures.

## Release and operations boundary

Release order is: build and test artifacts, verify hashes/signatures, redownload public binaries in staging, publish immutable versioned files, publish mutable metadata last, run browser/download smoke, and only then prepare acceptance evidence. Production upload and ENFORCE activation are separate operations and remain prohibited until explicitly requested after the full local/staging gate.

The final acceptance file is created only by the verified release step:

```text
docs/releases/copimine-launcher-v1-acceptance.json
```

It must contain schemaVersion 2, exact git SHA/tag, installer and manifest hashes, website flags, exact Minecraft/Fabric versions, local runtime result, clean-machine result, and all three regression flags. No placeholder or manually asserted `ACCEPTED` value is permitted.
