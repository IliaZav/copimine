# Security Review: copimine-main

## Scope

Repository-wide standard security review of the CopiMine website, authentication, release pipeline, PostgreSQL and Minecraft plugins.

- Scan mode: repository
- Target kind: git_revision
- Target ID: target_sha256_2e9383bb89616bb8c3e3c5f1a2a7b94814d0aacec2b9847a1e06808101366c0d
- Revision: 856facf0c5638481a4ed19d5a2d440f39f080900
- Inventory strategy: repository
- Included paths: .
- Excluded paths: none
- Runtime or test status: Repaired release deployed and live HTTPS smoke passed.
- Artifacts reviewed: in_scope_files.txt, candidate_ledger.jsonl, threat_model.md

Limitations and exclusions:
- Native security workbench coverage stopped at 608/3038 discovery rows.
- The scan target is the pre-remediation immutable revision; fixes were implemented and verified in later commits.

### Scan Summary

| Field | Value |
| --- | --- |
| Reportable findings | 20 |
| Severity mix | high: 9, medium: 11 |
| Confidence mix | high: 20 |
| Coverage | partial |
| Validation mode | compact source validation plus targeted regression and live smoke checks |

Canonical artifacts: `scan-manifest.json`, `findings.json`, and `coverage.json`. This report is a deterministic projection of those files.

## Threat Model

Protect player identity, site sessions, release integrity, Minecraft state, backups and public service availability across the browser, Nginx, backend, PostgreSQL, plugins and server filesystem.

### Assets

- site accounts and sessions
- release archives and signing keys
- Minecraft worlds and plugin state
- PostgreSQL balances, elections and whitelist links
- backup archives and server availability

### Trust Boundaries

- public browser to HTTPS Nginx
- Nginx to loopback backend
- backend to PostgreSQL and RCON
- signed release staging to root installer
- Minecraft players and plugins to persistent state

### Attacker Capabilities

- anonymous HTTPS requests
- malicious state-changing Origin
- crafted release/archive inputs when upload authority is obtained
- concurrent session requests
- malformed Minecraft/plugin event inputs

### Security Objectives

- authenticate and authorize state changes
- preserve one-time and monetary state
- prevent untrusted code/payload execution
- bound public resource use
- retain recoverable and redacted backups

### Assumptions

- production secrets remain outside the signed payload
- the deployed domain certificate and router forwarding remain operator-controlled
- real player, browser and third-party payment tests require hosted/manual environments

## Findings

| Finding | Severity | Confidence | Detailed write-up |
| --- | --- | --- | --- |
| [Ubuntu full replacement trusts the release archive to supply the public key used to verify its own manifest](#finding-1) | high | high | inline below |
| [The signed release manifest does not authenticate the full payload that the Ubuntu installer installs.](#finding-2) | high | high | inline below |
| [The client release build downloads the Gradle distribution without an integrity pin and immediately executes it.](#finding-3) | high | high | inline below |
| [The Ubuntu release installer silently enables the insecure offline-mode public voice-chat exception and bypasses its fail-closed gate.](#finding-4) | high | high | inline below |
| [CI executes a Maven distribution downloaded without a cryptographic integrity pin](#finding-5) | high | high | inline below |
| [The privileged Ubuntu release installer verifies the manifest with a release-signing allowlist loaded from the untrusted archive itself.](#finding-6) | high | high | inline below |
| [Public reverse-proxy requests can read /api/runtime without panel authentication.](#finding-7) | high | high | inline below |
| [Windows backup archives the entire project root, including hidden runtime secrets, into an unencrypted ZIP](#finding-8) | high | high | inline below |
| [Windows rollback installs any sidecar-matching ZIP without verifying a release signature](#finding-9) | high | high | inline below |
| [Public cauldron brewing can grow the live pending-state cache beyond its nominal 10,000-state bound.](#finding-10) | medium | high | inline below |
| [The public president skin proxy permits unbounded outbound image requests for arbitrary UUIDs.](#finding-11) | medium | high | inline below |
| [The deployed Minecraft RCON listener is exposed on all interfaces without transport protection.](#finding-12) | medium | high | inline below |
| [The privileged Ubuntu unpacker accepts non-regular tar members even though it has a stronger archive validator available.](#finding-13) | medium | high | inline below |
| [The cauldron service permanently retains a lock object for every unique block key touched by a player.](#finding-14) | medium | high | inline below |
| [Recovery-code confirmation is not an atomic one-time redemption.](#finding-15) | medium | high | inline below |
| [Concurrent player refresh requests can both redeem one refresh token.](#finding-16) | medium | high | inline below |
| [Concurrent admin refresh requests can both redeem one refresh token.](#finding-17) | medium | high | inline below |
| [Unauthenticated /api/public/status can exhaust backend workers with repeated blocking probes.](#finding-18) | medium | high | inline below |
| [Windows release upload can inject shell commands through helper file names.](#finding-19) | medium | high | inline below |
| [Resource-pack manifest references can escape the build stage and read or overwrite workspace files.](#finding-20) | medium | high | inline below |

### Confidence Scale

| Label | Meaning |
| --- | --- |
| high | Direct evidence supports the finding with no material unresolved blocker. |
| medium | Evidence supports a plausible issue, but material runtime or reachability proof remains. |
| low | Evidence is incomplete and the item is retained only for explicit follow-up. |

<a id="finding-1"></a>

### [1] Ubuntu full replacement trusts the release archive to supply the public key used to verify its own manifest

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-345, CWE-347 |
| Affected lines | deploy/ubuntu/copimine_full_replace.sh:237-275, deploy/ubuntu/copimine_full_replace.sh:318-328, deploy/ubuntu/copimine_full_replace.sh:686-697, deploy/release_manifest.json:63-68 |

#### Summary

The installer requires only an archive-relative SHA256 sidecar, then reads release_manifest.json, release_manifest.sig, and release-signing.allowed from PAYLOAD_ROOT and passes the archive-supplied allowed-signers file to ssh-keygen -Y verify. No external or pinned public-key trust anchor is consulted. A party able to provide or replace the archive can include a new allowed-signers file and matching signature; the sidecar then authenticates only that attacker-supplied archive before the script stages, swaps, and starts the release.

#### Root Cause

The installer requires only an archive-relative SHA256 sidecar, then reads release_manifest.json, release_manifest.sig, and release-signing.allowed from PAYLOAD_ROOT and passes the archive-supplied allowed-signers file to ssh-keygen -Y verify. No external or pinned public-key trust anchor is consulted. A party able to provide or replace the archive can include a new allowed-signers file and matching signature; the sidecar then authenticates only that attacker-supplied archive before the script stages, swaps, and starts the release.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/ubuntu/copimine_full_replace.sh:237-275, deploy/ubuntu/copimine_full_replace.sh:318-328, deploy/ubuntu/copimine_full_replace.sh:686-697, deploy/release_manifest.json:63-68, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Verify the signing key and payload manifest against root-owned external trust anchors, not files supplied by the archive.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-2"></a>

### [2] The signed release manifest does not authenticate the full payload that the Ubuntu installer installs.

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-345, CWE-494 |
| Affected lines | deploy/ubuntu/copimine_unpack_and_verify.sh:311-320, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/ubuntu/copimine_unpack_and_verify.sh:584-611, deploy/shared/common.sh:983-1015, deploy/release_manifest.json:23-30 |

#### Summary

The manifest carries hashes for selected downloads and first-party serverPlugins, but the installer swaps in every file under PAYLOAD_ROOT and its runtime validation only compiles the backend, tests two ZIPs, and checks that plugin JARs can be listed. The shared release contract compares only modpack, resource-pack, and client-mod hashes and never iterates serverPlugins or the application/deploy tree. An attacker who can alter the archive while preserving the signed manifest can replace server plugins, backend files, or deployment/systemd inputs without invalidating the checked signature.

#### Root Cause

The manifest carries hashes for selected downloads and first-party serverPlugins, but the installer swaps in every file under PAYLOAD_ROOT and its runtime validation only compiles the backend, tests two ZIPs, and checks that plugin JARs can be listed. The shared release contract compares only modpack, resource-pack, and client-mod hashes and never iterates serverPlugins or the application/deploy tree. An attacker who can alter the archive while preserving the signed manifest can replace server plugins, backend files, or deployment/systemd inputs without invalidating the checked signature.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/ubuntu/copimine_unpack_and_verify.sh:311-320, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/ubuntu/copimine_unpack_and_verify.sh:584-611, deploy/shared/common.sh:983-1015, deploy/release_manifest.json:23-30, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Authenticate every installed payload file and reject non-regular archive members, including symlinks and hardlinks.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-3"></a>

### [3] The client release build downloads the Gradle distribution without an integrity pin and immediately executes it.

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-494 |
| Affected lines | CopiMineClient/build-client.ps1:11-15, CopiMineClient/build-client.ps1:18-21, scripts/package_full_release.ps1:250-253 |

#### Summary

When the local Gradle executable is absent, build-client.ps1 downloads the fixed-version ZIP from services.gradle.org with Invoke-WebRequest, expands the cached ZIP, and invokes its gradle.bat build. No SHA-256, signature, or other authenticity check is performed before execution. package_full_release.ps1 invokes this script while building the client artifact, so a compromised or substituted distribution can execute build logic and influence the release client JAR.

#### Root Cause

When the local Gradle executable is absent, build-client.ps1 downloads the fixed-version ZIP from services.gradle.org with Invoke-WebRequest, expands the cached ZIP, and invokes its gradle.bat build. No SHA-256, signature, or other authenticity check is performed before execution. package_full_release.ps1 invokes this script while building the client artifact, so a compromised or substituted distribution can execute build logic and influence the release client JAR.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at CopiMineClient/build-client.ps1:11-15, CopiMineClient/build-client.ps1:18-21, scripts/package_full_release.ps1:250-253, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Pin the bootstrap distribution URL to a verified cryptographic digest before execution.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-4"></a>

### [4] The Ubuntu release installer silently enables the insecure offline-mode public voice-chat exception and bypasses its fail-closed gate.

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-287, CWE-345 |
| Affected lines | deploy/ubuntu/install_release.sh:863-904, deploy/ubuntu/install_release.sh:1011-1013, deploy/templates/voicechat-server.properties:1-5, minecraft/server/server.properties:38, minecraft/server/logs/latest.log:301-302 |

#### Summary

The installer unconditionally calls enable_offline_voicechat after preflight. That function rewrites the production .env with COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT=1 and a hardcoded claim that the owner accepted public voice chat, without command-line confirmation or independent operator attestation. The managed voice-chat template binds UDP to \*, while server.properties uses online-mode=false; the runtime log records that voice chat is running in offline mode and its encryption is not secure. This release path can therefore turn on identity-spoofable and insecure public voice sessions even when the example configuration says the flag must remain disabled until the limitation is assessed.

#### Root Cause

The installer unconditionally calls enable_offline_voicechat after preflight. That function rewrites the production .env with COPIMINE_ALLOW_INSECURE_OFFLINE_VOICECHAT=1 and a hardcoded claim that the owner accepted public voice chat, without command-line confirmation or independent operator attestation. The managed voice-chat template binds UDP to \*, while server.properties uses online-mode=false; the runtime log records that voice chat is running in offline mode and its encryption is not secure. This release path can therefore turn on identity-spoofable and insecure public voice sessions even when the example configuration says the flag must remain disabled until the limitation is assessed.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/ubuntu/install_release.sh:863-904, deploy/ubuntu/install_release.sh:1011-1013, deploy/templates/voicechat-server.properties:1-5, minecraft/server/server.properties:38, minecraft/server/logs/latest.log:301-302, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Keep the offline public voice-chat exception disabled unless an operator has explicitly enabled and reviewed it.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-5"></a>

### [5] CI executes a Maven distribution downloaded without a cryptographic integrity pin

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-494, CWE-829 |
| Affected lines | .github/workflows/ci.yml:110-119, .github/workflows/ci.yml:120-165 |

#### Summary

The workflow downloads the fixed-version Maven ZIP with Invoke-WebRequest, extracts it, and only checks that mvn.cmd exists before adding its bin directory to GITHUB_PATH. It then invokes mvn dependency:go-offline and uses Maven during first-party plugin compilation. No SHA256, signature, or independently trusted digest is checked, so compromise of the download origin or artifact can execute code in the GitHub runner and influence build outputs.

#### Root Cause

The workflow downloads the fixed-version Maven ZIP with Invoke-WebRequest, extracts it, and only checks that mvn.cmd exists before adding its bin directory to GITHUB_PATH. It then invokes mvn dependency:go-offline and uses Maven during first-party plugin compilation. No SHA256, signature, or independently trusted digest is checked, so compromise of the download origin or artifact can execute code in the GitHub runner and influence build outputs.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at .github/workflows/ci.yml:110-119, .github/workflows/ci.yml:120-165, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Pin the bootstrap distribution URL to a verified cryptographic digest before execution.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-6"></a>

### [6] The privileged Ubuntu release installer verifies the manifest with a release-signing allowlist loaded from the untrusted archive itself.

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-347, CWE-494 |
| Affected lines | deploy/ubuntu/copimine_unpack_and_verify.sh:238-265, deploy/ubuntu/copimine_unpack_and_verify.sh:313-320, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/ubuntu/install_release.sh:701-713 |

#### Summary

The caller supplies the archive and either an argument or sidecar for its SHA256; no trusted key material is consulted during that check. After extraction, verify_release_signature reads both release_manifest.json and release-signing.allowed from PAYLOAD_ROOT and verifies the signature against that pair, then install_payload copies the entire payload into the live project tree. A replacement archive plus matching sidecar can therefore carry a new public key and matching signature for arbitrary backend, plugin, or deployment code and pass the purported signature gate before root installation.

#### Root Cause

The caller supplies the archive and either an argument or sidecar for its SHA256; no trusted key material is consulted during that check. After extraction, verify_release_signature reads both release_manifest.json and release-signing.allowed from PAYLOAD_ROOT and verifies the signature against that pair, then install_payload copies the entire payload into the live project tree. A replacement archive plus matching sidecar can therefore carry a new public key and matching signature for arbitrary backend, plugin, or deployment code and pass the purported signature gate before root installation.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/ubuntu/copimine_unpack_and_verify.sh:238-265, deploy/ubuntu/copimine_unpack_and_verify.sh:313-320, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/ubuntu/install_release.sh:701-713, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Apply the corresponding fail-closed boundary and cover it with a release validator and runtime smoke check.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-7"></a>

### [7] Public reverse-proxy requests can read /api/runtime without panel authentication.

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-200, CWE-862 |
| Affected lines | admin-web/backend/main.py:17599-17612, admin-web/backend/main.py:4230-4236, admin-web/deploy/nginx-copimine-admin-https.conf:51-59, admin-web/backend/deploy_runtime.py:183-198, admin-web/backend/startup_checks.py:212-248 |

#### Summary

The /api/runtime handler skips require_panel_admin whenever is_loopback_request returns true, but that helper checks only request.client.host. The public TLS Nginx location proxies every path to 127.0.0.1:8090, so an Internet request is observed by the backend as loopback and receives the runtime response. The response combines startup and managed-runtime data including absolute project, app, and environment-file paths plus release and artifact metadata.

#### Root Cause

The /api/runtime handler skips require_panel_admin whenever is_loopback_request returns true, but that helper checks only request.client.host. The public TLS Nginx location proxies every path to 127.0.0.1:8090, so an Internet request is observed by the backend as loopback and receives the runtime response. The response combines startup and managed-runtime data including absolute project, app, and environment-file paths plus release and artifact metadata.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:17599-17612, admin-web/backend/main.py:4230-4236, admin-web/deploy/nginx-copimine-admin-https.conf:51-59, admin-web/backend/deploy_runtime.py:183-198, admin-web/backend/startup_checks.py:212-248, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Require panel authentication for public requests and permit the runtime exception only for direct, unproxied loopback requests.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-8"></a>

### [8] Windows backup archives the entire project root, including hidden runtime secrets, into an unencrypted ZIP

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-200, CWE-922 |
| Affected lines | deploy/windows/backup.ps1:3-7, deploy/windows/backup.ps1:11-15 |

#### Summary

The default backup destination is a release backup directory, and Get-ChildItem -Force enumerates every project-root entry before Copy-Item -Recurse stages it for Compress-Archive. This includes hidden .env and password files when present, as well as runtime data and logs. The output is a cleartext ZIP with only a SHA256 sidecar; no redaction, encryption, or separate secret handling is applied, so possession of the backup exposes stored credentials and session material.

#### Root Cause

The default backup destination is a release backup directory, and Get-ChildItem -Force enumerates every project-root entry before Copy-Item -Recurse stages it for Compress-Archive. This includes hidden .env and password files when present, as well as runtime data and logs. The output is a cleartext ZIP with only a SHA256 sidecar; no redaction, encryption, or separate secret handling is applied, so possession of the backup exposes stored credentials and session material.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/windows/backup.ps1:3-7, deploy/windows/backup.ps1:11-15, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Redact secrets and mutable private runtime state from portable backups and keep recovery data in dedicated protected procedures.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-9"></a>

### [9] Windows rollback installs any sidecar-matching ZIP without verifying a release signature

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-347, CWE-494 |
| Affected lines | deploy/windows/rollback.sh:1-4, deploy/windows/rollback.ps1:10-16, deploy/windows/rollback.ps1:42-61, deploy/windows/rollback.ps1:71-82 |

#### Summary

The rollback entrypoint accepts a caller-selected ZIP and validates only a SHA256 sidecar located beside it. Assert-SafeZip checks path and entry types but no manifest signature, trusted release key, or expected release identity. Expand-Archive then moves the archive's copimine tree over the target root and restarts services, so an attacker who can supply the archive and sidecar can install arbitrary server or web code.

#### Root Cause

The rollback entrypoint accepts a caller-selected ZIP and validates only a SHA256 sidecar located beside it. Assert-SafeZip checks path and entry types but no manifest signature, trusted release key, or expected release identity. Expand-Archive then moves the archive's copimine tree over the target root and restarts services, so an attacker who can supply the archive and sidecar can install arbitrary server or web code.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/windows/rollback.sh:1-4, deploy/windows/rollback.ps1:10-16, deploy/windows/rollback.ps1:42-61, deploy/windows/rollback.ps1:71-82, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Apply the corresponding fail-closed boundary and cover it with a release validator and runtime smoke check.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-10"></a>

### [10] Public cauldron brewing can grow the live pending-state cache beyond its nominal 10,000-state bound.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-400, CWE-770 |
| Affected lines | copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:31-42, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:61-115, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:386-426, copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java:285-302 |

#### Summary

MAX_CACHED_STATES is enforced while loading persisted rows, but queueIngredients unconditionally cache.puts every new BlockKey and has no runtime size check or eviction. A normal player reaches tryAddIngredient from the RIGHT_CLICK_BLOCK handler and can use distinct supported cauldrons and ingredients; integrity cleanup only examines a bounded snapshot per sweep, so stale entries are not an admission control. Each pending state is also persisted, allowing sustained distinct activity to consume heap, database rows, and bounded asynchronous work.

#### Root Cause

MAX_CACHED_STATES is enforced while loading persisted rows, but queueIngredients unconditionally cache.puts every new BlockKey and has no runtime size check or eviction. A normal player reaches tryAddIngredient from the RIGHT_CLICK_BLOCK handler and can use distinct supported cauldrons and ingredients; integrity cleanup only examines a bounded snapshot per sweep, so stale entries are not an admission control. Each pending state is also persisted, allowing sustained distinct activity to consume heap, database rows, and bounded asynchronous work.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:31-42, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:61-115, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:386-426, copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java:285-302, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Enforce a hard runtime cap before accepting new state and use bounded lock storage with cleanup.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-11"></a>

### [11] The public president skin proxy permits unbounded outbound image requests for arbitrary UUIDs.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-400 |
| Affected lines | admin-web/backend/main.py:11525-11553, admin-web/deploy/nginx-copimine-admin-https.conf:51-63 |

#### Summary

The public route accepts any syntactically valid 32-36 character UUID and does not apply check_rate_limit or a server-side cache. Every request creates a new async HTTP client and can wait up to eight seconds while trying two external image services with redirects enabled. An attacker can submit many distinct UUIDs to create outbound connections and hold request resources even when no image exists.

#### Root Cause

The public route accepts any syntactically valid 32-36 character UUID and does not apply check_rate_limit or a server-side cache. Every request creates a new async HTTP client and can wait up to eight seconds while trying two external image services with redirects enabled. An attacker can submit many distinct UUIDs to create outbound connections and hold request resources even when no image exists.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:11525-11553, admin-web/deploy/nginx-copimine-admin-https.conf:51-63, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Add bounded server-side caching and per-client rate limits around public expensive work and outbound image fetches.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-12"></a>

### [12] The deployed Minecraft RCON listener is exposed on all interfaces without transport protection.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-319, CWE-668 |
| Affected lines | minecraft/server/server.properties:14, minecraft/server/server.properties:45-46, minecraft/server/server.properties:53, minecraft/server/logs/latest.log:284, deploy/shared/common.sh:611-641 |

#### Summary

server.properties enables RCON on port 25575, leaves server-ip blank, and the captured runtime log confirms RCON running on 0.0.0.0:25575. The deployment helper only copies RCON_PASSWORD into rcon.password; it never sets a loopback RCON bind or installs a firewall restriction. RCON carries the password and subsequent commands over a cleartext TCP protocol, so a reachable listener exposes the credential to interception or brute force and grants the full server command surface if authentication is obtained.

#### Root Cause

server.properties enables RCON on port 25575, leaves server-ip blank, and the captured runtime log confirms RCON running on 0.0.0.0:25575. The deployment helper only copies RCON_PASSWORD into rcon.password; it never sets a loopback RCON bind or installs a firewall restriction. RCON carries the password and subsequent commands over a cleartext TCP protocol, so a reachable listener exposes the credential to interception or brute force and grants the full server command surface if authentication is obtained.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at minecraft/server/server.properties:14, minecraft/server/server.properties:45-46, minecraft/server/server.properties:53, minecraft/server/logs/latest.log:284, deploy/shared/common.sh:611-641, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Bind RCON to loopback and keep command validation fail-closed for every production listener.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-13"></a>

### [13] The privileged Ubuntu unpacker accepts non-regular tar members even though it has a stronger archive validator available.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-20, CWE-400 |
| Affected lines | deploy/ubuntu/copimine_unpack_and_verify.sh:120-148, deploy/ubuntu/copimine_unpack_and_verify.sh:280-289, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/shared/validate_archive.py:28-54 |

#### Summary

The inline validator checks tar member names and only rejects links whose targets are unsafe; it does not require tar members to be regular files/directories and therefore permits FIFOs, device nodes, and other special entries. The next step extracts with tar as root and cp -a copies the whole payload into the replacement tree. Such an archive can hang first-install environment creation, cause validation/startup failures, or leave special nodes in the deployed tree; deploy/shared/validate_archive.py explicitly rejects non-regular members and links, but this installer does not call it.

#### Root Cause

The inline validator checks tar member names and only rejects links whose targets are unsafe; it does not require tar members to be regular files/directories and therefore permits FIFOs, device nodes, and other special entries. The next step extracts with tar as root and cp -a copies the whole payload into the replacement tree. Such an archive can hang first-install environment creation, cause validation/startup failures, or leave special nodes in the deployed tree; deploy/shared/validate_archive.py explicitly rejects non-regular members and links, but this installer does not call it.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at deploy/ubuntu/copimine_unpack_and_verify.sh:120-148, deploy/ubuntu/copimine_unpack_and_verify.sh:280-289, deploy/ubuntu/copimine_unpack_and_verify.sh:378-390, deploy/shared/validate_archive.py:28-54, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Apply the corresponding fail-closed boundary and cover it with a release validator and runtime smoke check.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-14"></a>

### [14] The cauldron service permanently retains a lock object for every unique block key touched by a player.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-400, CWE-401 |
| Affected lines | copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:238-258, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:306-317, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:514-516, copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java:305-311 |

#### Summary

onBreak invokes handleCauldronBroken for every non-cancelled block break, not only cauldrons. handleCauldronBroken calls lockFor before checking whether a cached state exists; lockFor uses computeIfAbsent on the locks map. Break, completion, and failure paths remove cache entries but never remove locks, and the map is cleared only by clearCache or shutdown. A player traversing and breaking unique blocks can therefore grow this map for the server lifetime and exhaust heap.

#### Root Cause

onBreak invokes handleCauldronBroken for every non-cancelled block break, not only cauldrons. handleCauldronBroken calls lockFor before checking whether a cached state exists; lockFor uses computeIfAbsent on the locks map. Break, completion, and failure paths remove cache entries but never remove locks, and the map is cleared only by clearCache or shutdown. A player traversing and breaking unique blocks can therefore grow this map for the server lifetime and exhaust heap.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:238-258, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:306-317, copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java:514-516, copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java:305-311, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Enforce a hard runtime cap before accepting new state and use bounded lock storage with cleanup.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-15"></a>

### [15] Recovery-code confirmation is not an atomic one-time redemption.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-362, CWE-367 |
| Affected lines | admin-web/backend/main.py:11730-11747, admin-web/backend/main.py:6495-6537 |

#### Summary

confirm_player_recovery_code_sync selects a matching code with status='PENDING', then updates that row to USED without a row lock or a status='PENDING' predicate. Two concurrent confirmations can both observe the same pending code, both change the account password and whitelist link, and both return successfully; the endpoint then issues an authentication pair for each request.

#### Root Cause

confirm_player_recovery_code_sync selects a matching code with status='PENDING', then updates that row to USED without a row lock or a status='PENDING' predicate. Two concurrent confirmations can both observe the same pending code, both change the account password and whitelist link, and both return successfully; the endpoint then issues an authentication pair for each request.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:11730-11747, admin-web/backend/main.py:6495-6537, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Consume recovery codes with a conditional single-use update keyed by the stored hash and active status.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-16"></a>

### [16] Concurrent player refresh requests can both redeem one refresh token.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-362, CWE-367 |
| Affected lines | admin-web/backend/main.py:11750-11764, admin-web/backend/main.py:4284-4305, admin-web/backend/main.py:3735-3767, admin-web/backend/main.py:3780-3789 |

#### Summary

The player refresh endpoint invokes rotate_auth_pair_from_refresh_sync. That function verifies the old token by reading its row, commits a new token through make_refresh_token/save_refresh_session, and only afterward revokes the old row through a separate transaction. There is no row lock, conditional used-state update, or transaction spanning verification, issuance, and revocation, so concurrent requests can both pass the old-row checks and receive valid replacement sessions.

#### Root Cause

The player refresh endpoint invokes rotate_auth_pair_from_refresh_sync. That function verifies the old token by reading its row, commits a new token through make_refresh_token/save_refresh_session, and only afterward revokes the old row through a separate transaction. There is no row lock, conditional used-state update, or transaction spanning verification, issuance, and revocation, so concurrent requests can both pass the old-row checks and receive valid replacement sessions.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:11750-11764, admin-web/backend/main.py:4284-4305, admin-web/backend/main.py:3735-3767, admin-web/backend/main.py:3780-3789, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Redeem refresh sessions with one conditional atomic state transition so only one concurrent request can claim the old token.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-17"></a>

### [17] Concurrent admin refresh requests can both redeem one refresh token.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-362, CWE-367 |
| Affected lines | admin-web/backend/main.py:11391-11405, admin-web/backend/main.py:4284-4288, admin-web/backend/main.py:4317-4333, admin-web/backend/main.py:3735-3767, admin-web/backend/main.py:3780-3789 |

#### Summary

The admin refresh endpoint uses the same rotate_auth_pair_from_refresh_sync flow. The old refresh session is read and accepted before a new admin token is inserted and committed, while revocation of the old jti happens afterward in another transaction. Without a lock or atomic conditional transition, simultaneous requests can both pass verification and receive valid replacement admin sessions.

#### Root Cause

The admin refresh endpoint uses the same rotate_auth_pair_from_refresh_sync flow. The old refresh session is read and accepted before a new admin token is inserted and committed, while revocation of the old jti happens afterward in another transaction. Without a lock or atomic conditional transition, simultaneous requests can both pass verification and receive valid replacement admin sessions.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:11391-11405, admin-web/backend/main.py:4284-4288, admin-web/backend/main.py:4317-4333, admin-web/backend/main.py:3735-3767, admin-web/backend/main.py:3780-3789, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Redeem refresh sessions with one conditional atomic state transition so only one concurrent request can claim the old token.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-18"></a>

### [18] Unauthenticated /api/public/status can exhaust backend workers with repeated blocking probes.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-400 |
| Affected lines | admin-web/backend/main.py:11500-11502, admin-web/backend/main.py:9715-9757, admin-web/backend/main.py:6586-6587, admin-web/backend/main.py:6591-6645, admin-web/backend/main.py:6652-6658, admin-web/deploy/nginx-copimine-admin-https.conf:51-63 |

#### Summary

The public status route has no route-level rate limit or server-side cache and sends every request to public_site_status_sync through the threadpool. Each call performs a TCP probe, optionally performs an RCON list command with a four-second socket timeout, and then queries election and treasury state. The public Nginx location exposes the route without a request-rate control, allowing repeated anonymous calls to occupy threadpool and database/network resources.

#### Root Cause

The public status route has no route-level rate limit or server-side cache and sends every request to public_site_status_sync through the threadpool. Each call performs a TCP probe, optionally performs an RCON list command with a four-second socket timeout, and then queries election and treasury state. The public Nginx location exposes the route without a request-rate control, allowing repeated anonymous calls to occupy threadpool and database/network resources.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at admin-web/backend/main.py:11500-11502, admin-web/backend/main.py:9715-9757, admin-web/backend/main.py:6586-6587, admin-web/backend/main.py:6591-6645, admin-web/backend/main.py:6652-6658, admin-web/deploy/nginx-copimine-admin-https.conf:51-63, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Add bounded server-side caching and per-client rate limits around public expensive work and outbound image fetches.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-19"></a>

### [19] Windows release upload can inject shell commands through helper file names.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-78 |
| Affected lines | scripts/windows/upload_release.ps1:29-36, scripts/windows/upload_release.ps1:54-63, scripts/windows/upload_release.ps1:149-157, scripts/windows/upload_release.ps1:242-253 |

#### Summary

Resolve-RequiredPath only checks that each local helper path exists and resolves it; it does not constrain the leaf name. The RenameScript here-string interpolates each Split-Path -Leaf result directly inside single-quoted Bash arguments, and Invoke-Ssh sends that text to the remote shell. A local helper filename containing an apostrophe and shell syntax can terminate the quoted argument and inject commands executed as the configured SSH user during remote filename normalization.

#### Root Cause

Resolve-RequiredPath only checks that each local helper path exists and resolves it; it does not constrain the leaf name. The RenameScript here-string interpolates each Split-Path -Leaf result directly inside single-quoted Bash arguments, and Invoke-Ssh sends that text to the remote shell. A local helper filename containing an apostrophe and shell syntax can terminate the quoted argument and inject commands executed as the configured SSH user during remote filename normalization.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at scripts/windows/upload_release.ps1:29-36, scripts/windows/upload_release.ps1:54-63, scripts/windows/upload_release.ps1:149-157, scripts/windows/upload_release.ps1:242-253, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Validate upload leaf names against a strict allowlist before interpolating them into helper commands.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

<a id="finding-20"></a>

### [20] Resource-pack manifest references can escape the build stage and read or overwrite workspace files.

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. |
| Category | security-control |
| CWE | CWE-22 |
| Affected lines | resourcepacks/build-resourcepack.py:163-165, resourcepacks/build-resourcepack.py:259-272, resourcepacks/build-resourcepack.py:379-408, resourcepacks/build-resourcepack.py:436-438 |

#### Summary

asset_path splits the namespace but performs no absolute-path or dot-segment validation before appending Path(relative + suffix) to the root. build_stage takes model and texture references from models_manifest.json, and directional animation generation uses the resulting paths for reads, mkdir, PNG writes, and JSON writes. A malicious release/build manifest can use namespace or relative values containing absolute components or .. to traverse outside STAGE during the resource-pack build.

#### Root Cause

asset_path splits the namespace but performs no absolute-path or dot-segment validation before appending Path(relative + suffix) to the root. build_stage takes model and texture references from models_manifest.json, and directional animation generation uses the resulting paths for reads, mkdir, PNG writes, and JSON writes. A malicious release/build manifest can use namespace or relative values containing absolute components or .. to traverse outside STAGE during the resource-pack build.

#### Validation

The candidate includes direct entrypoint, control and sink locations plus a concrete source explanation. Validation details were not recorded separately.

Validation method: Direct source trace on the immutable scanned revision, followed by targeted regression checks on the repaired checkout.

#### Dataflow

The canonical finding records the affected path at resourcepacks/build-resourcepack.py:163-165, resourcepacks/build-resourcepack.py:259-272, resourcepacks/build-resourcepack.py:379-408, resourcepacks/build-resourcepack.py:436-438, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Historical source evidence shows the security boundary was missing on the scanned revision.

The finding is closed for the deployed release only after the signed repaired bundle and runtime configuration remain installed.

#### Remediation

Reject absolute, traversal, backslash and invalid asset paths before resolving them inside the build root.

Tests:
- tests/RunCopiMineValidators.ps1: 654/654 passed
- Live HTTPS and deployment smoke checks passed where applicable

Preventive controls:
- Signed release manifest and full payload inventory
- Fail-closed runtime authentication and bounded public work

## Reviewed Surfaces

| Surface | Risk Area | Outcome | Notes |
| --- | --- | --- | --- |
| Website and authentication | public HTTPS, sessions, Origin and API resource usage | Reported | Historical candidates were validated against the repaired backend and live HTTPS smoke. Evidence: artifacts/02_discovery/candidate_ledger.jsonl |
| Release and deployment integrity | signed payloads, archives, rollback and backup handling | Reported | Trust anchors, payload inventory and backup redaction were repaired and verified. Evidence: artifacts/02_discovery/candidate_ledger.jsonl |
| Build and bootstrap supply chain | Maven and Gradle distribution integrity | Reported | Bootstrap digests are pinned. Evidence: artifacts/02_discovery/candidate_ledger.jsonl |
| Minecraft plugin runtime | RCON, cauldron state and public event paths | Reported | Runtime hardening self-tests and plugin validators passed. Evidence: artifacts/02_discovery/candidate_ledger.jsonl |
| Security worklist coverage | orchestration coverage | Needs follow-up | The native workbench reported 608 of 3038 discovery rows closed before the protocol handoff stopped. Evidence: artifacts/02_discovery/in_scope_files.txt |

## Open Questions And Follow Up

- Run a fresh native standard/deep scan on current Git tip 2796c06 when the security worker protocol is upgraded.
  - Follow-up prompt: Re-run the repository-wide Codex Security scan for copimine-main at 2796c062b18ebfaf32e402ff57373c0e4594bd89 and verify the HTTPS, release trust and polling-station controls.
- The installed orchestration exposed the older subagent protocol; the workbench stopped at 608/3038 rows and the deep-scan variant requires a newer protocol.
  - Follow-up prompt: Review deferred unit security-workbench-protocol-handoff and close its stated proof gap. Paths: .. Surfaces: remaining-worklist.
