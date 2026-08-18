# CopiMine Launcher v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build, test, package, and locally/staging-verify a Windows x64 CopiMine Launcher with transactional instance reconciliation, minimal `CopiMineClient` compatibility admission, generated public launcher/news contracts, and a truthful Phase 1 acceptance record.

**Architecture:** A new .NET 10 WPF/MVVM solution is split into pure Core contracts, Infrastructure boundaries, and a thin WPF shell. The existing FastAPI/static site receives additive launcher metadata and generated static assets. A separate small Java 21 Paper plugin handles the bounded `CLIENT_READY` → `CLIENT_READY_ACK` handshake after a tested grace period; it never compares mod lists or launcher identity.

**Tech Stack:** .NET SDK 10.0.204 locally with target `net10.0-windows`; WPF; CommunityToolkit.Mvvm 8.4.2; Microsoft.Extensions.Hosting 10.0.11; Serilog 4.4.0 and Serilog.Sinks.File 7.0.0; CmlLib.Core 4.0.6 after the Fabric spike; NSec.Cryptography 26.4.0 for Launcher Ed25519 verification; xUnit 2.9.3; FluentAssertions 8.10.0; WireMock.Net 2.14.0; FlaUI.Core/UIA3 5.0.0; Velopack 1.2.0/vpk 1.2.0; Python 3.13 with the existing FastAPI stack plus cryptography 50.0.0; Java 21/Paper API for the gate plugin.

## Global Constraints

- Work only in isolated branch `codex/copimine-launcher-v1`; preserve the pre-existing audit worktree and unrelated changes.
- The current GitHub `main` baseline is `a2ccda11d79d5a4343ec02817a55a5c5b0e1c19a`.
- Scope is Phase 1 Launcher, website, patch feed, `CopiMineClient` compatibility gate, local/staging verification, and acceptance evidence; do not implement End Rift Event.
- Minecraft must be exactly `1.21.1`; Fabric Loader must be exactly `0.19.3`; the Launcher is Windows 10/11 x64.
- Release signing private keys never enter source, test artifacts, the Launcher, or game files. Test keys are generated in temporary test directories. No launch-ticket key or ticket subsystem is created.
- Admission is only a bounded `copimine:client_ready` → `copimine:client_ready_ack` compatibility handshake with a protocol version and diagnostic client version. There is no launcher-only mode, ticket, device identity, nonce, heartbeat, or mod-list gate.
- `client_ready` retries only until ACK or a finite admission deadline, uses a reasonable tested grace period (target 10–15 seconds), and stops immediately after ACK; after success, no delayed callback may kick the player. Additional client-side mods and the choice of launcher are allowed.
- The Launcher must not store or transmit AuthMe passwords and must not modify the user's normal `.minecraft`.
- Managed files may be added/replaced/removed only under the signed ownership policy; user-owned files and unknown server entries remain intact.
- Every behavior follows RED → GREEN → refactor; each logical GREEN slice receives a focused test run, diff review, and meaningful commit.
- `FAIL`, `SKIP`, and `UNVERIFIED` are not acceptance; the acceptance record is generated only by the final verified release step.
- Production upload, public installer publication, key rotation, and live destructive cleanup are out of scope for this branch.

---

## Task 1: Baseline ledger, repo contract, and solution scaffold

**Files:**
- Create: `docs/releases/copimine-launcher-v1-baseline.json`
- Create: `CopiMineLauncher/CopiMineLauncher.sln`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/CopiMineLauncher.Core.csproj`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/CopiMineLauncher.Infrastructure.csproj`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.Core.Tests/CopiMineLauncher.Core.Tests.csproj`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/CopiMineLauncher.Infrastructure.Tests.csproj`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.IntegrationTests/CopiMineLauncher.IntegrationTests.csproj`
- Create: `CopiMineLauncher/Directory.Build.props`
- Create: `CopiMineLauncher/README.md`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Core.Tests/ScaffoldContractTests.cs`

**Interfaces:**
- Core targets `net10.0`; App targets `net10.0-windows` with WPF enabled; test projects reference Core/Infrastructure only as needed.
- `LauncherVersionInfo.Product` is `CopiMineLauncher`; initial version is `1.0.0`.

- [ ] Record current branch, base SHA, installed SDKs, baseline test output (`294 passed, 7 pre-existing generated-artifact failures`), and the missing generated artifact paths in `docs/releases/copimine-launcher-v1-baseline.json`.
- [ ] Write the scaffold test asserting the solution restores, Core targets .NET 10, and the product/version constants are exposed.
- [ ] Run `dotnet test CopiMineLauncher/CopiMineLauncher.sln -c Release`; it must fail first because the solution projects do not exist, then create the minimal projects and run it again.
- [ ] Add the exact package references listed in Tech Stack, with `PrivateAssets=all` for test-only packages.
- [ ] Run `dotnet restore` and the focused scaffold test; record the output.
- [ ] Run `git diff --check`, inspect the new-file diff, and commit `feat: scaffold CopiMine Launcher solution`.

## Task 2: Manifest models, canonical JSON, signature, and safe-path policy

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Manifest/LauncherManifest.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Manifest/ManifestFileEntry.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Manifest/ManifestValidator.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Manifest/ManifestSignature.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Filesystem/SafeRelativePath.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Manifest/Ed25519ManifestVerifier.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Manifest/CanonicalJson.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Core.Tests/ManifestValidationTests.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/ManifestSignatureTests.cs`

**Interfaces:**
- `ManifestValidator.Validate(LauncherManifest manifest, DateTimeOffset now)` returns a `ManifestValidationResult` containing stable error codes.
- `SafeRelativePath.Parse(string value)` returns a normalized path or rejects absolute paths, drive-qualified paths, `..`, empty segments, alternate separators, reserved device names, and paths outside the instance root.
- `IManifestSignatureVerifier.Verify(ReadOnlySpan<byte> manifestBytes, ReadOnlySpan<byte> signatureBytes, ReadOnlySpan<byte> publicKeyBytes)` returns `SignatureVerificationResult`.

- [ ] Write RED tests for exact MC/Fabric versions, lowercase SHA-256, positive size, duplicate IDs/paths, unknown ownership, unsafe URL host/scheme, malformed signature, rollback sequence, and every path traversal form.
- [ ] Run the focused tests and confirm failures are caused by missing models/implementations.
- [ ] Implement canonical `System.Text.Json` serialization with duplicate-property rejection and deterministic property ordering.
- [ ] Implement Ed25519 verification with the public key loaded from an immutable application resource; no private key API exists in production projects.
- [ ] Implement stable error codes and path normalization.
- [ ] Run both focused test projects and a mutation check that corrupts one byte, changes one path, and changes one hash; each must fail verification.
- [ ] Review the diff for secret material and commit `test: add signed manifest and path safety contracts` followed by `feat: verify signed launcher manifests` if the implementation is separate.

## Task 3: Transaction journal, download manager, ownership, rollback, and reconciler

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Updates/UpdatePlan.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Updates/UpdateOperation.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Updates/TransactionJournal.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Updates/OwnershipPolicy.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/Updates/ReconciliationResult.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Updates/TransactionalReconciler.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Updates/ResumableDownloadManager.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Updates/AtomicFileStore.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Core.Tests/UpdatePlanTests.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/TransactionalReconcilerTests.cs`

**Interfaces:**
- `ITransactionalReconciler.ReconcileAsync(LauncherManifest manifest, CancellationToken cancellationToken)` is the only public entry point for instance file updates.
- `IResumableDownloadManager.DownloadAsync(Uri source, string destination, long expectedSize, string expectedSha256, CancellationToken cancellationToken)` writes only to a temporary file and returns a verified staged path.
- `IAtomicFileStore.CommitAsync(IReadOnlyList<UpdateOperation> operations, TransactionJournal journal, CancellationToken cancellationToken)` is atomic at the operation level and durable at the journal level.

- [ ] Write RED tests for add/update/remove, user-file preservation, corrupt-file repair, partial HTTP range resume, process-close journal recovery, failed commit rollback, duplicate managed paths, and no mutation before signature validation.
- [ ] Run RED tests and capture expected failures.
- [ ] Implement update-plan calculation using manifest ownership and local inventory.
- [ ] Implement hash-verified resumable downloads with bounded retries, cancellation, and no trust in `Content-Length` alone.
- [ ] Implement journal write-before-mutation, staged temp files, atomic replacement, backup retention of the last committed file, recovery replay, and rollback.
- [ ] Run focused tests, then run the same tests with injected I/O failures at each commit step.
- [ ] Commit `feat: add transactional launcher instance reconciler`.

## Task 4: Java 21, Minecraft/Fabric provisioning, and `servers.dat`

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Provisioning/JavaProvisioner.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Provisioning/FabricProvisioner.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Provisioning/MinecraftProvisioner.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Servers/ServersDatService.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Launch/MinecraftLaunchService.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.IntegrationTests/FabricProvisioningSpikeTests.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/ServersDatServiceTests.cs`
- Modify: `thirdparty/modpack_manifest.json` only if the signed launcher import contract requires an additive field.

**Interfaces:**
- `IJavaProvisioner.EnsureJava21Async(LauncherManifest manifest, CancellationToken cancellationToken)` returns an executable path whose `java -version` output is captured.
- `IMinecraftLaunchService.LaunchAsync(LaunchRequest request, CancellationToken cancellationToken)` returns a process handle and launch evidence.
- `IServersDatService.EnsureCopiMineServerAsync(string serversDatPath, ManagedServerRecord record)` preserves all unknown NBT fields and records an atomic-write evidence entry.

- [ ] Write the CmlLib spike test against a disposable instance root, exact MC `1.21.1`, exact Fabric `0.19.3`, and a local fake asset endpoint where possible; assert the resolved version and launch arguments.
- [ ] Run the spike before adding production launch code; record the exact package/API version and any limitation in `CopiMineLauncher/README.md`.
- [ ] Write RED tests for a missing server, duplicate CopiMine server, preserved third-party server, preserved unknown NBT fields, corrupt `servers.dat`, and atomic recovery.
- [ ] Implement verified Java 21 provisioning, Minecraft/Fabric installation, launch process isolation, bounded memory configuration, and `servers.dat` merge.
- [ ] Run the focused tests and the real local launch spike; a missing clean runtime is reported as `UNVERIFIED`, never converted to PASS.
- [ ] Commit `feat: add Minecraft Fabric provisioning and server integration`.

## Task 5: Launcher self-update through Velopack

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/SelfUpdate/VelopackSelfUpdateService.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/SelfUpdate/SelfUpdatePolicy.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/SelfUpdateTests.cs`
- Create: `CopiMineLauncher/packaging/velopack.json`
- Create: `scripts/build_copimine_launcher.ps1`

**Interfaces:**
- `ISelfUpdateService.CheckAsync(CancellationToken cancellationToken)` returns `SelfUpdateStatus` without mutating the current installation.
- `ISelfUpdateService.ApplyAsync(VerifiedSelfUpdate update, CancellationToken cancellationToken)` stages and applies a Velopack update with prior-version recovery.

- [ ] Write RED tests for no-update, verified update, corrupt update, interrupted apply, previous-version restart, and feed URL allowlist.
- [ ] Run RED tests.
- [ ] Integrate Velopack startup hooks early in the App entry point and pin Velopack/vpk to `1.2.0`.
- [ ] Implement self-update staging and recovery independent of game-file reconciliation.
- [ ] Run focused tests and `powershell -File scripts/build_copimine_launcher.ps1 -Configuration Release -SkipPackaging`.
- [ ] Commit `feat: add safe Launcher self-update packaging`.

## Task 6: CopiMineClient protocol and Fabric dependency contract

**Files:**
- Modify: `CopiMineClient/gradle.properties` to make the existing project version the single human-readable client-version source.
- Modify: `CopiMineClient/src/main/resources/fabric.mod.json` or its build-generation source to declare only real required Fabric dependencies.
- Modify: `CopiMineClient/src/main/java/me/copimine/clientbridge/CopiMineClientBridge.java` and related payload/registration classes to add the one `copimine:client_ready` packet.
- Create: `CopiMineClient/src/test/java/me/copimine/clientbridge/ClientReadyPayloadTest.java`.
- Create: `tests/test_copimine_client_protocol_contract.py`.

**Interfaces:**
- `ClientReadyPayload` serializes only `protocolVersion` and `clientVersion`; `ClientReadyAck` serializes only a bounded result code and required protocol for diagnostics.
- The protocol version is the compatibility authority; human-readable client version is diagnostic only.
- The client retries `CLIENT_READY` on a bounded schedule until `CLIENT_READY_ACK` or deadline; it never starts a periodic background heartbeat.
- Fabric metadata declares exact Minecraft/Loader compatibility and only dependencies required for the CopiMineClient to function. Optional QoL/visual mods are not dependencies.

- [ ] Write RED tests for payload/ACK fields, stable protocol value, no mod-list/device/launcher fields, bounded retry/stop-on-ACK behavior, and dependency declarations.
- [ ] Run RED tests.
- [ ] Implement the minimal payload/ACK handshake and generated version source; do not add a heartbeat or mod inventory.
- [ ] Run the Fabric client test/build and Python contract test.
- [ ] Commit `feat: add minimal CopiMineClient compatibility contract`.

## Task 7: Paper client_ready gate and reconnect safety

**Files:**
- Create: `copimine-client-gate/plugin.yml`
- Create: `copimine-client-gate/build-plugin.ps1`
- Create: `copimine-client-gate/src/me/copimine/clientgate/CopimineClientGate.java`
- Create: `copimine-client-gate/src/me/copimine/clientgate/ClientReadyPayload.java`
- Create: `copimine-client-gate/src/me/copimine/clientgate/ClientReadyAck.java`
- Create: `copimine-client-gate/src/me/copimine/clientgate/ClientReadyGate.java`
- Create: `copimine-client-gate/src/me/copimine/clientgate/GateConfig.java`
- Create: `tests/test_copimine_client_gate_contract.py`

**Interfaces:**
- `ClientReadyGate.onJoin(UUID playerId)` creates a pending admission and schedules one grace timeout.
- `ClientReadyGate.onClientReady(UUID playerId, ClientReadyPayload payload)` returns/sends `ClientReadyAck`, accepts only the required protocol, and cancels the pending timeout on success.
- `ClientReadyGate.onQuit(UUID playerId)` removes pending state; `clear()` removes all state.

- [ ] Write RED tests for compatible protocol/ACK, bounded retry/stop-on-ACK, incompatible protocol/negative ACK, missing packet, 10–15 second grace behavior, successful-ready cancellation, quit cleanup, plugin-disable cleanup, reconnect race, exact diagnostic reason codes, no heartbeat, and no mod-list inspection.
- [ ] Run RED tests.
- [ ] Implement a small pending-check map keyed by the current player/session, a tested scheduler timeout, ACK result codes, exact structured diagnostics, and no periodic task after success.
- [ ] Build the plugin against the repository's Paper API/build conventions.
- [ ] Run the contract tests, plugin build, and a local Paper harness that exercises the actual join/ready/timeout decision boundary.
- [ ] Commit `feat: add CopiMineClient compatibility gate`.

## Task 8: Structured patch-note source, item resolver, and generated feed

**Files:**
- Create: `content/patches/2026-08-15-launcher-1.0.0.yaml`
- Create: `tools/patch_notes/build_patch_notes.py`
- Create: `tools/patch_notes/schema.py`
- Create: `tools/patch_notes/item_resolver.py`
- Create: `scripts/new_patch_note.ps1`
- Create: `tests/test_patch_notes_pipeline.py`
- Create: `tests/test_patch_item_texture_contract.py`
- Generate: `admin-web/frontend/assets/public-data/patches/index.json`
- Generate: `admin-web/frontend/assets/public-data/patches/copimine-launcher-1-0-0.json`
- Generate: `admin-web/frontend/news/copimine-launcher-1-0-0.html`
- Generate: `admin-web/frontend/assets/public-data/patch-items/*`

**Interfaces:**
- `build_patch_notes.py --content-dir content/patches --output-root admin-web/frontend` validates and generates all files atomically.
- `resolve_patch_item(item_id, catalog, texture_map)` returns display name, safe icon URL, and typed changes or fails the build.

- [ ] Write RED tests for valid schema, duplicate ID/slug, invalid date, unknown kind, raw HTML/script, missing human review, unknown item, missing texture, bounded summaries, newest-first sorting, and generated-file existence.
- [ ] Run RED tests.
- [ ] Implement YAML validation, safe plain-text rendering, item/catalog/resource-pack texture resolution, deterministic output, and atomic writes.
- [ ] Add the factual Launcher 1.0.0 patch source; do not claim runtime verification until those gates pass.
- [ ] Run the full patch pipeline and inspect generated JSON/HTML visually and structurally.
- [ ] Commit `feat: add structured patch notes pipeline`.

## Task 9: Public launcher/news pages and legacy route compatibility

**Files:**
- Create: `admin-web/frontend/launcher.html`
- Create: `admin-web/frontend/news.html`
- Create: `admin-web/frontend/news/` generated detail shells as needed by the static topology.
- Create: `admin-web/frontend/assets/js/public/launcher-page.js`
- Create: `admin-web/frontend/assets/js/public/news-page.js`
- Create: `admin-web/frontend/assets/js/public/patch-detail-page.js`
- Create: `admin-web/frontend/assets/js/public/launcher-data.js`
- Create: `admin-web/frontend/assets/js/public/patch-data.js`
- Create: `admin-web/frontend/assets/js/public/launcher-render.js`
- Create: `admin-web/frontend/assets/js/public/patch-render.js`
- Modify: `admin-web/frontend/assets/js/public/public-page.js`
- Modify: `admin-web/frontend/assets/css/site-redesign.css` or the repository's selected public stylesheet.
- Modify: `admin-web/frontend/mods.html` to become a compatibility shell/redirect.
- Modify: `admin-web/backend/main.py` only where static aliases or launcher metadata are required.
- Create: `admin-web/scripts/launcher_website_contract_test.py`

**Interfaces:**
- Page kinds are `public-launcher`, `public-news`, and `public-patch`.
- Launcher metadata is read from `/assets/public-data/launcher/latest.json`; news list is read from `/assets/public-data/patches/index.json`.
- The primary installer link is disabled with truthful “download temporarily unavailable” text when metadata fails validation.

- [ ] Write RED contract tests for launcher page CTA, screenshot gallery keyboard behavior, light/dark theme, mobile layout, news detail order, safe DOM text, accessible headings/time elements, no horizontal overflow, old `/mods.html` compatibility, and exact metadata fields.
- [ ] Run RED tests.
- [ ] Implement modular public pages with DOM APIs/textContent for network values, lazy screenshots, bounded patch index, safe external links, and preserved auth navigation.
- [ ] Replace normal manual ZIP CTA with Launcher CTA while retaining a clearly non-primary legacy path only if existing scripts require it.
- [ ] Run Python contracts and Node syntax checks.
- [ ] Use the in-app browser at `1440x900` and `390x844` to inspect real rendered pages, console errors, focus/lightbox behavior, and route status.
- [ ] Commit `feat: publish launcher and patch-note website pages`.

## Task 10: Launcher patch feed, UI, diagnostics, and Play flow

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/News/PatchFeedItem.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Core/News/PatchFeedValidator.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/News/PatchFeedClient.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/App.xaml`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/ViewModels/MainViewModel.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/ViewModels/LauncherStateViewModel.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Views/NewsCard.xaml`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Views/DiagnosticsView.xaml`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.Core.Tests/PatchFeedValidationTests.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.IntegrationTests/LauncherFlowTests.cs`

**Interfaces:**
- `IPatchFeedClient.GetLatestAsync(CancellationToken)` returns only validated typed entries and falls back to the last valid cached feed.
- `LauncherStateViewModel.PlayCommand` can remain available when the patch feed is unavailable, but not when the signed instance manifest is invalid.

- [ ] Write RED tests for malformed feed item skipping, unsafe URL rejection, image-size bound, cache fallback, three-card limit, progress state transitions, duplicate-mod diagnostics, and Play gating.
- [ ] Run RED tests.
- [ ] Implement typed feed/cache, WPF MVVM state machine, progress/recovery diagnostics, accessible controls, and system-browser news links.
- [ ] Run focused .NET tests and `dotnet build CopiMineLauncher/CopiMineLauncher.sln -c Release`.
- [ ] Run FlaUI/UIA3 smoke where the desktop session supports it; otherwise record the exact environment limitation as `UNVERIFIED`.
- [ ] Commit `feat: add Launcher UI and shared patch feed`.

## Task 11: Installer, manifest publication, and release build pipeline

**Files:**
- Create: `scripts/package_copimine_launcher.ps1`
- Create: `scripts/verify_copimine_launcher_release.ps1`
- Create: `scripts/publish_launcher_manifest.ps1`
- Create: `CopiMineLauncher/packaging/README.md`
- Create: `docs/releases/copimine-launcher-v1-release-evidence.json` only after the release build is verified.
- Modify: existing release pipeline only through additive launcher steps; do not alter unrelated artifact packaging.

**Interfaces:**
- `package_copimine_launcher.ps1 -Version 1.0.0 -OutputRoot <dir>` produces a self-contained x64 installer and immutable package metadata.
- `verify_copimine_launcher_release.ps1 -ArtifactRoot <dir>` verifies PE header, size, SHA-256, manifest signature, version agreement, and generated website metadata without uploading.

- [ ] Write RED release-contract tests for missing EXE, HTML returned under `.exe`, size/hash mismatch, signature mismatch, version mismatch, unsafe URL, and latest pointer published before binary.
- [ ] Run RED tests.
- [ ] Implement deterministic self-contained WPF publish, Velopack packaging, Authenticode inspection when a real certificate exists, hash/signature generation, and verification.
- [ ] Package a local `CopiMineLauncherSetup-1.0.0.exe` without publishing it.
- [ ] Redownload it from a local static test server, compare size/SHA-256/PE header, and inspect signature status.
- [ ] Commit `feat: add reproducible Launcher installer pipeline`.

## Task 12: Clean-machine install/update/rollback and local Minecraft runtime harness

**Files:**
- Create: `scripts/test_launcher_clean_machine.ps1`
- Create: `scripts/test_launcher_update_recovery.ps1`
- Create: `scripts/test_launcher_runtime_smoke.ps1`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.IntegrationTests/CleanMachineFlowTests.cs`
- Create: `docs/releases/copimine-launcher-v1-runtime-evidence.json` after execution.

**Interfaces:**
- The harness accepts an explicit disposable root and local manifest/static server URL; it never targets the user's normal `.minecraft`.
- Evidence records command, environment, artifact hashes, instance path, exact MC/Fabric/Java versions, and result per scenario.

- [ ] Write RED harness tests for clean first install, add/update/remove managed mods, preserve user files, corrupt file repair, interrupted download, process close, rollback, self-update, and server pre-add.
- [ ] Run RED tests.
- [ ] Implement disposable-root setup/cleanup with explicit path validation and no broad deletion.
- [ ] Run the first install and update/rollback tests on this Windows host using a local HTTP fixture.
- [ ] Run the real Minecraft launch against the local test server when the required server/runtime artifacts are available.
- [ ] Record any clean Windows VM result separately; a host test cannot be relabeled as VM evidence.
- [ ] Commit `test: add clean-machine and Launcher runtime gates`.

## Task 13: Three historical server regression gates

**Files:**
- Create: `tests/launcher_phase1/test_ping_regression.py`
- Create: `tests/launcher_phase1/test_bed_waypoint_respawn_regression.py`
- Create: `tests/launcher_phase1/test_auth_slowness_regression.py`
- Create: `scripts/run_launcher_regression_gates.ps1`
- Create: `docs/releases/copimine-launcher-v1-regression-evidence.json` after fresh execution.

**Interfaces:**
- Each gate emits `PASS`, `FAIL`, or `UNVERIFIED` plus raw measurements/log paths.
- Ping gate measures join latency, normal gameplay latency, server tick health, and Launcher background traffic while the game runs.
- Bed gate covers set spawn, waypoint, death respawn, relog, restart, and bed destruction.
- Auth gate covers `/login`, reconnect, repeated reconnect, delayed-task cancellation, and preservation of unrelated legitimate Slowness.

- [ ] Write RED tests against deterministic local fixtures for all three contracts.
- [ ] Run RED tests.
- [ ] Implement only launcher-related harness integration; do not rewrite unrelated gameplay logic without a reproduced failure.
- [ ] Run the gates against the local Paper/PostgreSQL runtime and capture logs, timings, and player identity used.
- [ ] Run the full existing plugin/client validators that are relevant to the touched surfaces.
- [ ] Mark production as prohibited if any result is FAIL or UNVERIFIED.
- [ ] Commit `test: add fresh Launcher release regression gates`.

## Task 14: Independent verifier, final release gate, and acceptance artifact

**Files:**
- Create: `scripts/verify_copimine_launcher_acceptance.ps1`
- Create: `docs/releases/copimine-launcher-v1-verifier.json`
- Create: `docs/releases/copimine-launcher-v1-acceptance.json` only inside the final release step after all checks pass.
- Create: `docs/releases/copimine-launcher-v1-acceptance.md`

**Interfaces:**
- `verify_copimine_launcher_acceptance.ps1 -Root <worktree> -ArtifactRoot <dir> -RequireCleanMachine` produces the requirement table `Requirement | Code evidence | Automated evidence | Runtime evidence | PASS/FAIL`.
- The acceptance generator refuses to write `status: ACCEPTED` when any required flag is false, skipped, missing, stale, or unverified.

- [ ] Write RED verifier tests for every acceptance JSON field, every required gate, stale evidence timestamps, SHA/version mismatch, and refusal on `UNVERIFIED`.
- [ ] Run RED tests.
- [ ] Implement read-only independent checks over source, generated artifacts, test reports, runtime evidence, installer hashes, manifest signatures, handshake decisions/reason codes, and website routes.
- [ ] Run all focused suites, all integration suites, all existing relevant Python/Java/Gradle validators, full .NET build/test, website/browser checks, installer checks, runtime checks, `client_ready` → `client_ready_ack` compatibility checks, and the three regression gates.
- [ ] Run `git diff --check`, inspect `git status --short`, and verify no secrets/private keys/generated temp downloads are tracked.
- [ ] Create the acceptance JSON only if every required value is fresh and PASS; otherwise create a NOT ACCEPTED evidence report without the acceptance artifact.
- [ ] Commit `docs: record CopiMine Launcher v1 acceptance evidence` only on full GREEN.

## Final verification commands

Run from the isolated worktree with the Python 3.13 environment at `D:\Desktop\Copimine\.venvs\copimine-launcher-v1`:

```powershell
dotnet restore CopiMineLauncher/CopiMineLauncher.sln
dotnet test CopiMineLauncher/CopiMineLauncher.sln -c Release --no-restore
python -m pytest -q tests/test_copimine_client_protocol_contract.py tests/test_copimine_client_gate_contract.py tests/test_patch_notes_pipeline.py tests/test_patch_item_texture_contract.py
python -m pytest -q
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build_copimine_launcher.ps1 -Configuration Release
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_copimine_launcher_release.ps1 -ArtifactRoot .\release-artifacts\launcher
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test_launcher_update_recovery.ps1 -Root .\runtime-tests\update
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_launcher_regression_gates.ps1 -EvidenceRoot .\release-evidence\regressions
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_copimine_launcher_acceptance.ps1 -Root . -ArtifactRoot .\release-artifacts\launcher -RequireCleanMachine
git diff --check
git status --short
```

The final report must state the exact command results, exit codes, artifact paths, SHA-256 values, commit list, browser screenshots/URLs, `client_ready`/ACK decisions and reason codes, clean-machine status, local server status, three regression results, and every remaining limitation. No production deployment is performed by this plan.
