# CustomSkinLoader Modpack Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the absent/legacy skin-loader entry in the CopiMine Fabric client pack with the user-provided CustomSkinLoader Fabric 14.26.1 JAR for Minecraft 1.21.1.

**Architecture:** Treat `thirdparty/client-mods` and the explicit PowerShell/Bash modpack file lists as the source of truth. The generated ZIP, checksums, public snapshot, and manifests must be regenerated from that source, while the existing server plugins and unrelated working-tree changes remain untouched.

**Tech Stack:** Fabric client modpack, PowerShell, Bash, JSON manifests, SHA-256/SHA-1 checksums, repository PowerShell validators.

## Global Constraints

- Keep the user-provided JAR byte-for-byte unchanged.
- Keep Minecraft `1.21.1` and Fabric loader compatibility.
- Do not add external client dependencies.
- Do not run the security scan in this release pass; deploy only after focused
  verification. A world/database wipe requires a reviewed manifest, backup, and
  explicit confirmation immediately before destructive execution.
- Preserve pre-existing unrelated generated and server configuration changes.

---

### Task 1: Add the regression contract

**Files:**
- Modify: `tests/ValidateCopiMineWebModpackDownloadFoundation.ps1`

- [x] Add the CustomSkinLoader JAR to the required ZIP entries and assert its manifest name/version.
- [x] Run the focused validator and confirm it fails because the current archive did not contain the new entry.

### Task 2: Replace the modpack source entry

**Files:**
- Create: `thirdparty/client-mods/CustomSkinLoader_Fabric-14.26.1.jar`
- Modify: `scripts/thirdparty/build_modpack.ps1`
- Modify: `scripts/thirdparty/build_modpack.sh`
- Modify: `thirdparty/checksums.txt`
- Modify: `thirdparty/modpack_manifest.json`
- Modify: `thirdparty/README_RU.txt`
- Modify: `thirdparty/thirdparty_manifest.json`

- [x] Copy the provided JAR without re-packing or modifying it.
- [x] Add the pinned SHA-256 and explicit build-list entry in both platform scripts.
- [x] Document the client-only CustomSkinLoader component and its GPL-3.0-only license/source.

### Task 3: Regenerate and verify distributable metadata

**Files:**
- Modify: `thirdparty/CopiMineMods.zip`
- Modify: `thirdparty/CopiMineMods.sha1`
- Modify: `thirdparty/CopiMineMods.sha256`
- Modify: `admin-web/frontend/assets/public-data/modpack_snapshot.json`

- [x] Run the repository modpack builder.
- [x] Verify the ZIP contains CustomSkinLoader exactly once, contains no TL Skin entry, and matches both sidecar hashes.
- [x] Run the focused modpack validator; the full validator suite is intentionally not run per the user's latest instruction.

### Task 4: Make cauldron brewing shared and world-only

Files:
- Modify: copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java
- Modify: copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java
- Modify: tests/test_requested_gameplay_fixes.py
- Modify: tests/ValidateCopiMineNarcoticsBrewingOutputOutbox.ps1
- Modify: tests/ValidateCopiMineNarcoticsWrongMixDamageAttribution.ps1

- [x] Accept safe vanilla ingredients in any order for the first three clicks,
  resolve immediately to Zhuzevo when no recipe prefix remains, and wait only
  while at least one recipe can still match.
- [x] Remove cauldron-output owner checks and inventory mailbox delivery.
- [x] Drop both narcotics and Zhuzevo beside the cauldron, allow any player to
  pick them up, and close the durable row without an owner predicate.
- [x] Apply a non-block-destroying wrong-mix effect that damages players inside
  six blocks for 14-20 health (7-10 hearts).
- [x] Run focused gameplay tests, the brewing output validator, and a clean
  plugin compilation.

### Task 5: Release preparation and destructive reset gate

- [ ] Package the verified server release and upload/install it on server-rpgrp.
- [ ] Produce the exact pre-wipe manifest: active worlds, database tables/row
  counts, whitelist rows, and website account rows.
- [ ] Create and verify a rollback backup, then request/receive final
  confirmation immediately before deleting worlds and game-state rows.
