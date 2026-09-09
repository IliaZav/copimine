# Launcher Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a branded, folder-selecting CopiMine Windows installer and persistently configurable Minecraft first-run defaults.

**Architecture:** Keep Velopack as the installer/update engine. Add deterministic build-time installer assets and text, and add a small `.copimine/minecraft-default-settings.json` contract consumed by the existing options reconciler. Present the choices in the current single-window WPF navigation flow on first start.

**Tech Stack:** .NET 10 WPF, C# records and atomic JSON files, Velopack v1.2.0, PowerShell packaging, pytest contract tests, xUnit/FluentAssertions.

## Global Constraints

- Do not change production server, production world, production database, player data, or Paper gameplay JAR.
- Preserve the existing Velopack `current` layout and launcher data outside the mutable game instance.
- Existing player values in `options.txt` always win over selected defaults.
- RAM and resolution remain Launcher settings; they are not written as Minecraft defaults.
- No credentials, access tokens, or editable instance paths are stored in the new defaults file.
- Do not stage or commit the pre-existing `minecraft/server/server.properties` or Grim patch JAR changes.

### Task 1: Defaults contract and options application

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Launch/MinecraftDefaultSettings.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Launch/MinecraftSettingsDefaults.cs`
- Test: `CopiMineLauncher/tests/CopiMineLauncher.Infrastructure.Tests/MinecraftSettingsDefaultsTests.cs`

**Interfaces:**
- `MinecraftDefaultSettings(bool UseRussianLanguage = true, bool DisableNarrator = true, bool SetMasterVolumeToFifteenPercent = true)`.
- `MinecraftDefaultSettingsStore.Save(string instanceRoot, MinecraftDefaultSettings settings)`.
- `MinecraftDefaultSettingsStore.Load(string instanceRoot)` returns nullable settings.
- `MinecraftDefaultSettingsStore.IsConfigured(string instanceRoot)` returns whether a valid selection exists.
- `MinecraftSettingsDefaults.EnsureDefaults(string instanceRoot)` consumes the stored selection and remains compatible when it is missing.

- [ ] Add tests for a selected subset, existing-option preservation, idempotence, and malformed/missing selection fallback.
- [ ] Run the focused infrastructure test and observe RED before production edits.
- [ ] Implement a schema-versioned JSON file under `.copimine`, with UTF-8, directory creation, and temp-file replacement.
- [ ] Make options application select only missing keys and keep the original default behavior when no selection is stored.
- [ ] Run focused tests and inspect the diff.
- [ ] Commit the green defaults slice with `feat: persist minecraft first-run defaults`.

### Task 2: First-run WPF screen and settings binding

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/MinecraftDefaultsWindow.xaml`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/MinecraftDefaultsWindow.xaml.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherScreenNavigation.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherSettingsWindow.xaml`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherSettings.cs` only if a stable settings seam is needed.

**Interfaces:**
- The current `MainWindow.ShowScreen` path hosts the new screen; no additional top-level window is created.
- The view exposes three boolean selections and a `Save` action that calls `MinecraftDefaultSettingsStore.Save` for `InstancePath`.
- `LauncherViewModel` exposes `HasMinecraftDefaultsSelection` and reloads the selection after save.

- [ ] Add a testable view-model/store seam for first-run detection before XAML wiring.
- [ ] Run the focused App test and observe RED.
- [ ] Add the single-window first-run screen with all three checkboxes checked by default, direct Russian copy, and green primary action.
- [ ] Open it after initialization when no valid selection exists; make the action save and return home.
- [ ] Add the same three checkboxes to the existing settings screen so the choice can be changed without a reinstall.
- [ ] Run App tests and build the WPF project.
- [ ] Commit the green UI slice with `feat: add minecraft defaults setup screen`.

### Task 3: Branded Velopack installer

**Files:**
- Create: `scripts/prepare_copimine_installer_assets.ps1`
- Create: `CopiMineLauncher/packaging/installer-welcome.txt`
- Create: `CopiMineLauncher/packaging/installer-readme.txt`
- Create: `CopiMineLauncher/packaging/installer-conclusion.txt`
- Modify: `scripts/build_copimine_launcher.ps1`
- Modify: `CopiMineLauncher/packaging/launcher-install-contract.json`
- Modify: `tests/test_launcher_packaging_contract.py`

**Interfaces:**
- Asset script outputs `artifacts/launcher/<Configuration>/installer-assets/installer-banner.bmp` at 493×58 and `installer-logo.bmp` at 493×312.
- Build script invokes the asset script and passes `--icon`, `--splashImage`, `--splashProgressColor`, `--msiBanner`, `--msiLogo`, installer text paths, and `--shortcuts Desktop,StartMenuRoot` to `vpk pack`.

- [ ] Add failing contract assertions for asset generation, branding flags, and defaults metadata.
- [ ] Run the Python contract tests and observe RED.
- [ ] Generate bitmap assets from the existing CopiMine logo using Windows System.Drawing with a deterministic navy/teal layout.
- [ ] Add concise installer text that names the chosen-root layout and first-run defaults screen.
- [ ] Wire all paths through the build script with exact existence checks before `vpk pack`.
- [ ] Run Python tests and a `-SkipPackaging` publish check.
- [ ] Commit the green packaging slice with `feat: brand launcher installer package`.

### Task 4: Release and clean-install verification

**Files:**
- Modify only generated files under `artifacts/launcher/Release`.
- Do not modify production files.

- [ ] Build the Release publish and installer from the staged local manifest.
- [ ] Verify EXE/MSI/portable artifacts, file sizes, SHA-256, and `git diff --check`.
- [ ] Install the MSI into a fresh exact local test directory, verify the chosen root and desktop shortcut, and launch the installed app.
- [ ] Complete the first-run defaults screen and verify `.copimine/minecraft-default-settings.json` plus the three `options.txt` keys.
- [ ] Re-run after changing in-game options and verify no overwrite.
- [ ] Run affected .NET tests, packaging pytest, and the full repository test suite proportionate to the change.
- [ ] Review the final diff and commit only scoped source/docs changes; leave existing unrelated modifications untouched.
