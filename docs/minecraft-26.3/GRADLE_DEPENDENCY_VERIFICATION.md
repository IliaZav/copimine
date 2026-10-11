# Minecraft 26.3 Gradle dependency verification

This record covers the migration's Paper plugin and Fabric client Gradle roots. Both manifests set `verify-metadata=true` and lock external artifact bytes with SHA-256. Fabric Loom's plugin marker, implementation JAR, and module metadata are included.

## Checksum provenance

[`GRADLE_DEPENDENCY_PROVENANCE.json`](GRADLE_DEPENDENCY_PROVENANCE.json) records the source URL, method, and SHA-256 for every external artifact entry in both manifests. The report contains 531 artifacts: 138 server artifacts and 393 client artifacts.

- 440 recorded hashes match the publisher's `.sha256` sidecar fetched over HTTPS.
- 91 recorded hashes match a direct HTTPS download of the exact artifact, hashed locally.
- Six common parent POM sidecars from the Paper Maven mirror differed from the locked bytes. Direct Maven Central downloads matched the lock for all six. Maven Central is the first repository in the server build; strict verification will reject the Paper mirror's different POM bytes if repository fallback selects them.
- The Paper API JAR hash also matches `profile.lock.json`. The Fabric Loom 1.17.21 JAR, module metadata, and plugin marker POM match the SHA-256 sidecars published by Fabric Maven.

The sidecars and artifacts are served by the publishers' repositories. A separate HTTPS retrieval confirms the lock contents and catches stale or altered cached bytes, but it is not a third-party signature or cross-operator attestation. Gradle documents that bootstrapping verification metadata trusts the repositories used at bootstrap. This residual trust boundary is recorded rather than described as cryptographic provenance.

The Minecraft merged JAR and POM are generated locally by Fabric Loom. Their checksum changed between isolated project-cache roots, so the client manifest has one narrow trusted-artifact rule for `net.minecraft:minecraft-merged-<hex>:26.3` matching only the corresponding JAR and POM filenames. No external dependency group or other Minecraft version is covered by that rule. Gradle documents that locally produced artifacts can vary and do not fit fixed checksum verification.

## Validation evidence

- `python -m pytest -q tests/test_minecraft_gradle_dependency_verification.py`: 6 passed. The contract checks strict mode, strong checksums, Loom marker and implementation coverage, the narrow local-artifact rule, per-artifact provenance coverage, Paper's independent profile pin, and known official sidecar digests.
- Full CI-aligned Python regression on Python 3.13.16: `py -3.13 -m pytest -q tests` — 1,347 passed, 4 skipped, 88 warnings in 147.98 seconds. The default Python 3.14 interpreter lacks FastAPI and could not collect two unrelated admin-backend modules; that attempt was superseded by the successful CI-pinned interpreter run.
- Server: Gradle 9.8.1 with Temurin 25.0.2, a fresh Gradle user home and project cache, `--rerun-tasks migrationPlugins --continue`: build successful, 25 tasks executed in 38 seconds. Log: `D:\Temp\minecraft-26-3-gradle-verification-20261009\server-final-clean-build.log`.
- Client: Gradle 9.8.1 with Temurin 25.0.2, a fresh Gradle user home and project cache, `--rerun-tasks test jar`: build successful, 6 tasks executed in 1 minute 43 seconds. After provenance annotations were added, the same isolated cache passed `--offline --rerun-tasks test jar` in 15 seconds. Log: `D:\Temp\minecraft-26-3-gradle-verification-20261009\client-final-offline-build.log`.
- Tamper check: one byte in the isolated cached `fabric-loom-1.17.21.jar` was changed. Gradle rejected the build before `:help`; the exact cached JAR was restored and its SHA-256 again matched the manifest (`89c08938d865621172e3fb31036081399b2731a4971f599b42724b2509ff5b64`). Log: `D:\Temp\minecraft-26-3-gradle-verify-build-20261009\fabric-loom-tamper-build.log`.
- Independent review confirmed the trusted-artifact exception is narrow and the provenance evidence covers every external artifact. A follow-up review found two test-contract gaps (the alternate mirror host/digest and exact Maven path); the test now checks the Paper host, lowercase SHA-256, inequality with the lock, and the exact sidecar URL derived from group/module/version/artifact. The reviewer confirmed both gaps are closed.
- CodeRabbit CLI 0.8.2 reviewed the 10 dependency-verification files in an isolated worktree against the same base commit and reported 0 issues. The review used the free CLI allowance because this GitHub repository is not connected to an accessible CodeRabbit organization.

This is one migration hardening checkpoint. A fresh post-fix Codex Security diff scan is still pending. Full migration acceptance, dual-platform CI, and native Minecraft verification remain separate gates.
