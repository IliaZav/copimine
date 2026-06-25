# CopiMineClient Optional Visuals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapt CopiMineNarcotics visuals to an optional Fabric client mod with honest server overlay and particle fallback.

**Architecture:** The server keeps full gameplay authority and always works without a client mod. A new optional Fabric client mod advertises capabilities via a lightweight bridge, receives per-player visual commands, and renders the best available client-side effect while the server keeps resource-pack overlay and particle fallback paths.

**Tech Stack:** Paper 1.21.x plugin messaging, Java 21, Fabric Loader 0.19.3, Yarn 1.21.1+build.3, Fabric API 0.116.12+1.21.1, shared CopiMine resource pack.

---

### Task 1: Audit And Reuse Map

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md`
- Modify: `D:\Desktop\Copimine\opt\copimine\resourcepacks\LICENSES_RESOURCEPACK.md`

- [ ] Record which existing overlay PNG, font manifest, shader descriptor, and fallback mechanics are reused.
- [ ] Mark server shader descriptors as documentation/client-profile only, never server-forced.

### Task 2: Create Optional Fabric Client

**Files:**
- Create: `D:\Desktop\Copimine\CopiMineClient\*`

- [ ] Scaffold a standalone Fabric client mod project for Minecraft 1.21.1.
- [ ] Add handshake and capability reporting.
- [ ] Add client commands for status, protocol, debug, and local visual tests.
- [ ] Add HUD overlay rendering and client visual manager with timeout cleanup.

### Task 3: Add Server Bridge

**Files:**
- Create: `D:\Desktop\Copimine\opt\copimine\copimine-narcotics\src\me\copimine\clientbridge\*.java`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-narcotics\plugin.yml`

- [ ] Register plugin messaging channels and capability state tracking.
- [ ] Add `/cmclient` admin bridge commands.
- [ ] Keep bridge optional by default and preserve non-mod client support.

### Task 4: Refactor Narcotics Visual Routing

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java`
- Modify: `D:\Desktop\Copimine\opt\copimine\copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java`

- [ ] Split route selection into client-mod, server overlay, and particle fallback.
- [ ] Add new `AUTO / CLIENT_MOD / SERVER_OVERLAY / SERVER_FALLBACK` modes with compatibility mapping.
- [ ] Track player resource-pack acceptance for honest overlay routing.

### Task 5: Resource Pack And Docs

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json`
- Modify: `D:\Desktop\Copimine\opt\copimine\resourcepacks\build-resourcepack.py`
- Create: `D:\Desktop\Copimine\CopiMineClient\ASSET_LICENSES.md`
- Create: `D:\Desktop\Copimine\CopiMineClient\README_INSTALL_RU.md`
- Create: `D:\Desktop\Copimine\CopiMineClient\PROTOCOL.md`

- [ ] Keep current server overlay assets as Level 2 fallback.
- [ ] Mirror or adapt visual assets into client-mod resources.
- [ ] Document optional mod installation and protocol.

### Task 6: Validators, Smoke, And Builds

**Files:**
- Create: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineClient*.ps1`
- Create: `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_CLIENT_OPTIONAL_VISUALS_SMOKE_CHECKLIST.md`

- [ ] Add validators for client project, server bridge, visual route priority, and fallback honesty.
- [ ] Build `CopiMineClient.jar`, rebuild `CopiMineNarcotics.jar`, rebuild the resource pack, sync SHA1, and run validators.
