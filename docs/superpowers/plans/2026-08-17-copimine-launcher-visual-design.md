# CopiMine Launcher visual system Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a tested Minecraft-atmosphere visual refresh for the native WPF Launcher, loader/update states, news cards, and installer using every supplied visual asset and verified screenshots.

**Architecture:** Normalize the archive assets into a single LauncherVisuals catalog, expose shared WPF theme resources from `App.xaml`, and keep presentation-only background/overlay controls separate from the existing runtime/reconciler behavior. A UI-thread GIF controller will drive the supplied splash and horizontal-logo animations with a static PNG fallback; the existing single-window navigation, manifest/update pipeline, binding, launch, cosmetics, and diagnostics remain the source of truth.

**Tech Stack:** .NET 10 WPF, C#, XAML, xUnit/FluentAssertions, PowerShell release scripts, Velopack, supplied PNG/GIF/WebP artwork converted to WPF-compatible PNG where required.

## Global Constraints

- Work only in isolated worktree `D:\Desktop\Copimine\copimine-main\.worktrees\site-launcher-audit` on branch `codex/site-launcher-audit`.
- Use visual assets from `D:\Downloads\Telegram Desktop\COPIMINE Launcher.rar`; do not generate replacement artwork.
- WebP source files may be converted to PNG only for WPF compatibility; retain source WebP files as content/source evidence.
- GIF animations must run without blocking the UI, stop on unload/close, and fall back to a static PNG if decoding fails.
- Intermediate fade/scale/stagger/shimmer animations must change visible state, keep input available, and have a reduced-motion/static path.
- User-facing copy must be short, human and action-oriented; no template/AI-style filler text.
- Preserve single-window navigation: settings and skins replace current content and the existing back button returns home.
- Do not change production website, Paper/Minecraft service, production world, production database, AuthMe, or player data.
- Do not change manifest/binding/admission behavior as part of the visual slice.
- Every logical green slice follows failing test -> implementation -> focused tests -> diff review -> commit.
- `passed` in visual QA is allowed only for the final screenshot set after all observed layout defects are corrected.

---

### Task 1: Add a deterministic visual-asset catalog and archive contract

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherVisualAssetCatalog.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherVisualAssetCatalogTests.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/launcher-home-background.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/launcher-home-source.webp`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/update-background.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/update-background-source.webp`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/splash.gif`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/loading-emblem.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/copimine-logo.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/copimine-logo-animated.gif`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/copimine-icon.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/installer-banner.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-01.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-02.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-03.png`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-01-source.webp`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-02-source.webp`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/news-03-source.webp`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/Assets/copimine.ico` only if the supplied icon conversion is required and verified against the existing shortcut contract.

**Interfaces:**
- `internal static class LauncherVisualAssetCatalog` exposes constants for the normalized asset paths and `GetNewsArtwork(int index)`, returning `news-01.png`, `news-02.png`, `news-03.png`, and then `launcher-home-background.png` for additional cards.
- `LauncherVisualAssetCatalog.RequiredSourceAssets` returns the exact 12 source positions from the archive; `DerivedDisplayAssets` returns WPF-converted PNGs. Tests use these lists rather than duplicating path strings.

- [ ] **Step 1: Write the failing asset contract test**

  Add tests that resolve the app project asset directory from the test output/repository, assert all 12 source positions and every derived display file exists and is non-empty, verify PNG/GIF signatures, verify the converted PNG dimensions (`2560x1440`, `1920x1080`, `1024x1024`, `1066x600`, `1200x700`, `1600x600`), and assert `GetNewsArtwork(0..2)` returns three distinct file names.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run:

  ```powershell
  dotnet test CopiMineLauncher/tests/CopiMineLauncher.App.Tests/CopiMineLauncher.App.Tests.csproj -c Release --filter FullyQualifiedName~LauncherVisualAssetCatalogTests
  ```

  Expected: FAIL because the catalog and normalized assets do not yet exist.

- [ ] **Step 3: Normalize the supplied assets**

  Copy the supplied PNG/GIF files from the extracted archive into the exact paths above. Retain `launcher_home.webp`, `update_background.webp`, and `news_01.webp`/`news_02.webp`/`news_03.webp` as the normalized `*-source.webp` files. Convert the WebP files to PNG without resizing, using the existing local image tooling. Copy the archive’s actual `launcher_home_master.png` as the WPF home background and the supplied horizontal logo/icon files under normalized names.

- [ ] **Step 4: Add the catalog and WPF build items**

  Implement the constants and `RequiredAssets`; declare PNG/GIF files as WPF `Resource` items and source WebP files as `Content` with `CopyToOutputDirectory=PreserveNewest`. Do not include WebP files as WPF `Resource` items because WPF cannot decode them natively.

- [ ] **Step 5: Run the focused tests and build**

  Run the focused test again, then:

  ```powershell
  dotnet build CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj -c Release
  ```

  Expected: PASS and all normalized assets present in the build output.

- [ ] **Step 6: Review and commit**

  Run `git diff --check`, inspect `git status --short`, stage only this task’s files, and commit:

  ```powershell
  git add CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj CopiMineLauncher/src/CopiMineLauncher.App/LauncherVisualAssetCatalog.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherVisualAssetCatalogTests.cs
  git commit -m "feat: add launcher visual asset catalog"
  ```

### Task 2: Implement tested GIF playback and fallback

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/AnimatedGifImage.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/GifFrameSequence.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/GifFrameSequenceTests.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj`

**Interfaces:**
- `internal sealed class GifFrameSequence : IDisposable` loads a GIF URI with `BitmapDecoder`, exposes `IReadOnlyList<BitmapSource> Frames`, `IReadOnlyList<TimeSpan> Durations`, `int FrameCount`, and `BitmapSource GetFrame(int index)`.
- `public sealed class AnimatedGifImage : Image` exposes dependency properties `Uri? GifSource`, `ImageSource? FallbackSource`, `bool IsPlaying`; it starts on `Loaded`, advances via `DispatcherTimer`, stops/disposes on `Unloaded`, and exposes internal `int CurrentFrameIndex` for same-assembly tests.

- [ ] **Step 1: Write failing frame-sequence and lifecycle tests**

  Test that the supplied splash has more than one frame, every duration is positive and clamped to a safe interval, `GetFrame` wraps indexes, a missing/invalid GIF selects the fallback path, and calling stop/dispose twice is safe. Add a test that advancing a sequence changes the current frame index.

- [ ] **Step 2: Run tests to verify red**

  ```powershell
  dotnet test CopiMineLauncher/tests/CopiMineLauncher.App.Tests/CopiMineLauncher.App.Tests.csproj -c Release --filter FullyQualifiedName~GifFrameSequenceTests
  ```

  Expected: FAIL because the controller types do not exist.

- [ ] **Step 3: Implement the minimal decoder/controller**

  Use WPF `BitmapDecoder` with `BitmapCacheOption.OnLoad`, freeze loaded frames, read `BitmapFrame.Metadata` for delay when available, clamp invalid delays to 20–500 ms, and drive only a UI-thread `DispatcherTimer`. On load failure, stop the timer and assign the fallback image. On `Unloaded` and `Dispose`, detach the timer and release the frame sequence.

- [ ] **Step 4: Run focused tests and a two-frame smoke probe**

  Run the focused tests and a small local App test that loads `splash.gif`, waits for two timer ticks on the WPF dispatcher, and asserts the frame index changed. Expected: PASS with no unhandled dispatcher exception.

- [ ] **Step 5: Review and commit**

  Run `git diff --check`, stage only the controller/tests, and commit:

  ```powershell
  git add CopiMineLauncher/src/CopiMineLauncher.App/AnimatedGifImage.cs CopiMineLauncher/src/CopiMineLauncher.App/GifFrameSequence.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/GifFrameSequenceTests.cs
  git commit -m "feat: add nonblocking launcher gif playback"
  ```

### Task 3: Centralize the CopiMine theme and refresh the single-window shell

**Files:**
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/App.xaml`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherThemeContractTests.cs`

**Interfaces:**
- App resources provide `LauncherColors`, `LauncherButton`, `LauncherPrimaryButton`, `LauncherCard`, `LauncherTextBox`, `LauncherComboBox`, `LauncherProgressBar`, and `BooleanToVisibilityConverter`.
- MainWindow continues to expose the existing `HomeView`, `ScreenView`, `ScreenContent`, and navigation event handlers; visual changes do not change `LauncherScreenNavigation` behavior.

- [ ] **Step 1: Write failing XAML/theme contract tests**

  Read the XAML files as text and assert the app references the shared theme keys, `launcher-home-background.png`, `copimine-logo.png`, and the folder icon button; assert settings and skins keep a back action and no extra `Window` is introduced. Assert no old single flat `#0b141c` home-only background is the only root visual.

- [ ] **Step 2: Run the contract test to verify red**

  ```powershell
  dotnet test CopiMineLauncher/tests/CopiMineLauncher.App.Tests/CopiMineLauncher.App.Tests.csproj -c Release --filter FullyQualifiedName~LauncherThemeContractTests
  ```

  Expected: FAIL because the common resource keys and background are not present.

- [ ] **Step 3: Add the shared resources**

  Move the common palette/button/card/textbox/combobox/progress definitions into `App.xaml` with the dark green Minecraft palette from the spec. Keep styles with the same keys only where existing screen-specific templates need them, and update those screens to use shared resource keys so Home, Settings, Skins and Minecraft Defaults do not drift.

- [ ] **Step 4: Build the new Home composition**

  Add a full-window `Image`/overlay host using `launcher-home-background.png` with `UniformToFill`, add a translucent dark overlay, constrain content with a stable two-column grid, and keep the existing folder icon button and commands. Use the supplied horizontal PNG logo in the header. Give all action buttons a common min height, padding and spacing so text never touches borders.

- [ ] **Step 5: Run the contract test and build**

  Run the focused test and `dotnet build CopiMineLauncher/CopiMineLauncher.sln -c Release`. Expected: PASS with no XAML parse errors.

- [ ] **Step 6: Review and commit**

  Inspect the XAML diff for unrelated functional changes, run `git diff --check`, then commit:

  ```powershell
  git add CopiMineLauncher/src/CopiMineLauncher.App/App.xaml CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherThemeContractTests.cs
  git commit -m "feat: apply shared minecraft launcher theme"
  ```

### Task 4: Add splash and update/reconcile visual states

**Files:**
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherLoadingOverlay.xaml`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherLoadingOverlay.xaml.cs`
- Create: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherMotion.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs`
- Modify: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherViewModelTests.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherLoadingOverlayContractTests.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherMotionTests.cs`

**Interfaces:**
- `LauncherLoadingOverlay` is a `UserControl` with dependency property `bool IsSplashVisible`, `bool IsOperationVisible`, `string Stage`, `double Progress`, `bool IsIndeterminate`, and `string ProgressLabel`.
- `LauncherMotion` exposes `TimeSpan ShortTransition = 180ms`, `TimeSpan MediumTransition = 320ms`, `bool ReducedMotion`, and `double GetOpacityAt(double progress)`; it contains no network/runtime side effects.
- `LauncherViewModel` adds `bool IsInitializing`; it is true before the first `InitializeAsync` operation and false in a `finally` block; existing `IsBusy`, `LoadingStage`, `ProgressPercent`, and `ProgressLabel` remain the operation bindings.

- [ ] **Step 1: Write failing state and overlay contract tests**

  Add a ViewModel test that `InitializeAsync` enters initialization and always clears it after both success and a feed failure. Add `LauncherMotionTests` for the 180/320 ms bounds, reduced-motion opacity, and monotonic progress interpolation. Add XAML contract tests for `update-background.png`, `loading-emblem.png`, `splash.gif`, `copimine-logo-animated.gif`, progress text, fade/scale/shimmer storyboards, and a non-opaque overlay card.

- [ ] **Step 2: Run the focused tests to verify red**

  ```powershell
  dotnet test CopiMineLauncher/tests/CopiMineLauncher.App.Tests/CopiMineLauncher.App.Tests.csproj -c Release --filter "FullyQualifiedName~LauncherViewModelTests|FullyQualifiedName~LauncherLoadingOverlayContractTests"
  ```

  Expected: FAIL because initialization state and overlay do not exist.

- [ ] **Step 3: Implement the overlay and initialization binding**

  Place the overlay above the Home content in the same Window, not as a second Window. Use `AnimatedGifImage` for splash and animated logo, a static emblem, the update background, a dark scrim, and a centered card containing stage/progress/status. Add one-shot fade/scale transitions, a restrained indeterminate shimmer and a monotonic determinate progress animation; use `LauncherMotion.ReducedMotion` to skip movement while preserving content. Keep text and progress visible at every stage. When `IsInitializing` is true, show splash; when `IsBusy` is true after initialization, show operation overlay; when complete or failed, return to the normal shell and leave the existing diagnostic panel/status available.

- [ ] **Step 4: Run tests and build**

  Run the focused tests and the full solution build. Expected: PASS; a failed initialization must not leave the splash permanently visible, and motion tests must prove intermediate state changes without waiting on network/runtime work.

- [ ] **Step 5: Review and commit**

  Review overlay z-order and bindings in XAML, run `git diff --check`, and commit:

  ```powershell
  git add CopiMineLauncher/src/CopiMineLauncher.App/LauncherLoadingOverlay.xaml CopiMineLauncher/src/CopiMineLauncher.App/LauncherLoadingOverlay.xaml.cs CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherViewModelTests.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherLoadingOverlayContractTests.cs
  git commit -m "feat: add launcher splash and update overlay"
  ```

### Task 5: Render artwork in news cards and keep fallback behavior

**Files:**
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs`
- Modify: `CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml`
- Modify: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherViewModelTests.cs`
- Create: `CopiMineLauncher/tests/CopiMineLauncher.App.Tests/PatchCardArtworkTests.cs`

**Interfaces:**
- `PatchFeedCardViewModel` adds `string ArtworkPath` and `bool UsesRemoteThumbnail`; local artwork is selected deterministically by card index before an optional remote thumbnail is shown.

- [ ] **Step 1: Write failing artwork tests**

  Assert that the first three cards map to `news-01.png`, `news-02.png`, and `news-03.png`, that an empty feed does not throw, and that a card with no remote thumbnail still has a local artwork path.

- [ ] **Step 2: Run focused tests to verify red**

  ```powershell
  dotnet test CopiMineLauncher/tests/CopiMineLauncher.App.Tests/CopiMineLauncher.App.Tests.csproj -c Release --filter FullyQualifiedName~PatchCardArtworkTests
  ```

  Expected: FAIL because `ArtworkPath` and indexed mapping do not exist.

- [ ] **Step 3: Implement deterministic local artwork**

  Build `PatchCards` with an index and resolve the three local PNG resources through `LauncherVisualAssetCatalog`. Add a fixed-height image strip/crop to each news card with a dark text scrim; keep title, version, date, summary and «Подробнее» readable when the remote feed is unavailable.

- [ ] **Step 4: Run tests and build**

  Run focused tests, the app build, and existing patch-feed tests. Expected: PASS without network access.

- [ ] **Step 5: Review and commit**

  ```powershell
  git add CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml CopiMineLauncher/tests/CopiMineLauncher.App.Tests/LauncherViewModelTests.cs CopiMineLauncher/tests/CopiMineLauncher.App.Tests/PatchCardArtworkTests.cs
  git commit -m "feat: add artwork to launcher news cards"
  ```

### Task 6: Use supplied artwork in the installer pipeline

**Files:**
- Modify: `scripts/prepare_copimine_installer_assets.ps1`
- Modify: `scripts/build_copimine_launcher.ps1`
- Modify: `CopiMineLauncher/packaging/installer-welcome.txt`
- Modify: `CopiMineLauncher/packaging/installer-readme.txt`
- Modify: `CopiMineLauncher/packaging/installer-conclusion.txt`
- Create: `scripts/tests/launcher_visual_packaging_contract_test.ps1`

**Interfaces:**
- `prepare_copimine_installer_assets.ps1` accepts mandatory `-SourceLogo` and `-SourceBanner`, writes `installer-banner.bmp` and `installer-logo.bmp`, and returns `BANNER_OUTPUT`/`LOGO_OUTPUT` paths.
- `build_copimine_launcher.ps1` passes `Assets/LauncherVisuals/copimine-logo.png` and `Assets/LauncherVisuals/installer-banner.png` to the asset preparation script while retaining the selectable MSI installation location.

- [ ] **Step 1: Write the failing packaging contract**

  Add a PowerShell test that reads both scripts and fails unless `SourceBanner` is accepted, the supplied normalized banner is passed, the generated banner/logo are checked, and the Velopack call still contains `--msi`, `--instLocation Either`, `--msiBanner`, `--msiLogo`, and the Desktop shortcut.

- [ ] **Step 2: Run the contract to verify red**

  ```powershell
  pwsh -NoProfile -File scripts/tests/launcher_visual_packaging_contract_test.ps1
  ```

  Expected: FAIL because the current script generates a banner only from the old logo.

- [ ] **Step 3: Implement supplied-banner packaging**

  Load the supplied banner with `System.Drawing`, crop/cover it into the existing Velopack banner dimensions, apply the dark readability overlay, and place the supplied CopiMine logo over it. Use the supplied logo for the splash/logo panel as well. Keep all path checks narrow and do not introduce destructive operations outside `artifacts/launcher/$Configuration`.

- [ ] **Step 4: Run packaging contract and script-level smoke checks**

  Run the contract test and `prepare_copimine_installer_assets.ps1` against a temporary artifact folder. Inspect the generated BMP dimensions and confirm both files are non-empty. Do not upload or install on production.

- [ ] **Step 5: Review and commit**

  ```powershell
  git add scripts/prepare_copimine_installer_assets.ps1 scripts/build_copimine_launcher.ps1 scripts/tests/launcher_visual_packaging_contract_test.ps1 CopiMineLauncher/packaging/installer-welcome.txt CopiMineLauncher/packaging/installer-readme.txt CopiMineLauncher/packaging/installer-conclusion.txt
  git commit -m "feat: brand launcher installer with supplied artwork"
  ```

### Task 7: Local build, runtime screenshots, and iterative visual QA

**Files:**
- Create: `artifacts/design-qa/launcher-visuals/design-qa.md`
- Create: `artifacts/design-qa/launcher-visuals/home.png`
- Create: `artifacts/design-qa/launcher-visuals/splash.png`
- Create: `artifacts/design-qa/launcher-visuals/splash-frame-a.png`
- Create: `artifacts/design-qa/launcher-visuals/splash-frame-b.png`
- Create: `artifacts/design-qa/launcher-visuals/update.png`
- Create: `artifacts/design-qa/launcher-visuals/update-mid.png`
- Create: `artifacts/design-qa/launcher-visuals/update-complete.png`
- Create: `artifacts/design-qa/launcher-visuals/failure.png`
- Create: `artifacts/design-qa/launcher-visuals/installer.png`
- Create: `scripts/run_copimine_launcher_visual_qa.ps1`

**Interfaces:**
- The QA script builds the local app, launches it with a loopback/staging-safe configuration, captures deterministic states, and writes an evidence manifest without contacting production endpoints.
- `design-qa.md` lists each screenshot, the visual checks performed, GIF frame-change evidence, build/test commands, and ends with exactly `final result: passed` only if all checks are green.

- [ ] **Step 1: Run the full automated suite before screenshots**

  ```powershell
  dotnet test CopiMineLauncher/CopiMineLauncher.sln -c Release
  dotnet build CopiMineLauncher/CopiMineLauncher.sln -c Release
  git diff --check
  ```

  Expected: PASS and no uncommitted code changes from prior slices.

- [ ] **Step 2: Capture the first local screenshot set**

  Use the local desktop/WPF runtime to capture Home, splash, update/reconcile, failure/diagnostic, and installer states. Verify the app uses the loopback test feed or cached fixtures, not production binding or server state. Record the observed version, window size, and asset paths.

- [ ] **Step 3: Inspect every screenshot for concrete defects**

  Check image crop/proportion, text contrast, button spacing, card alignment, progress readability, no clipped controls, no layout jump, no stale “Модпак”/AI-like filler text, and consistent palette across Settings/Skins. Compare splash frame A/B and update start/mid/complete captures to prove GIF and intermediate transitions advance. Confirm a button can receive input while a non-blocking transition is running.

- [ ] **Step 4: Fix and repeat until the set is clean**

  If any screenshot shows a defect, modify the smallest responsible XAML/style/controller file, rerun the affected test, rebuild, recapture the complete set, and repeat the inspection. Do not mark the QA document passed while any screenshot remains defective.

- [ ] **Step 5: Run installer visual smoke**

  Build only the local release artifact with the existing staging/offline inputs, verify the banner is present in installer assets, confirm the MSI still exposes a folder/disk selection, and capture the welcome/install screen. Do not publish it to the live server.

- [ ] **Step 6: Write evidence and commit**

  Write `design-qa.md` with exact commands/results and `final result: passed`, run `git diff --check`, and commit:

  ```powershell
  git add artifacts/design-qa/launcher-visuals scripts/run_copimine_launcher_visual_qa.ps1
  git commit -m "test: verify launcher visuals with runtime screenshots"
  ```

### Task 8: Independent release-gate verification and final handoff

**Files:**
- Modify only if evidence requires: `artifacts/design-qa/launcher-visuals/design-qa.md`
- Create: `artifacts/launcher-visual-release-gate.txt`

- [ ] **Step 1: Verify all slice commits and clean scope**

  Run `git log --oneline -8`, `git status --short`, and inspect `git diff HEAD~N --stat` for unrelated files. The extracted `$designRef/` directory must remain untracked or be explicitly excluded; it must not be accidentally included in a commit.

- [ ] **Step 2: Run final automated and packaging checks**

  Run the complete solution test suite, Release build, visual asset contract, GIF tests, installer contract, `git diff --check`, and the local release packaging/verification scripts with staging-safe inputs.

- [ ] **Step 3: Record exact evidence**

  `artifacts/launcher-visual-release-gate.txt` must include commit IDs, test commands and pass counts, output asset paths, screenshot paths, GIF frame-change result, installer asset result, and explicit lines:

  ```text
  Production world modified: NO
  Production database modified: NO
  Production player data modified: NO
  Production Paper restarted: NO
  Production gameplay JAR replaced: NO
  ```

- [ ] **Step 4: Final diff review and commit**

  If the gate is green, commit only the evidence file and any final QA correction:

  ```powershell
  git add artifacts/launcher-visual-release-gate.txt artifacts/design-qa/launcher-visuals/design-qa.md
  git commit -m "test: record launcher visual release gate"
  ```
