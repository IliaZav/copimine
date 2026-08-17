# CopiMine Launcher installer and first-run defaults

## Goal

Give players a branded Windows installer with a real installation-directory choice and a short first-run screen for Minecraft defaults, while keeping the existing Velopack update layout and mutable game instance safe across updates.

## Design

The release remains a Velopack package. The build script supplies the CopiMine icon, a generated banner/logo pair, a progress color, installer welcome/readme/conclusion text, and desktop/Start Menu shortcuts. Velopack's MSI remains the component that owns installation and uninstallation, so its selected directory is the single source of truth. The launcher resolves the game instance as `<selected-root>\Minecraft`.

The MSI does not own Minecraft's `options.txt`; it only installs the launcher. On the first launcher start, an in-app setup screen presents three checked options: Russian language, narrator disabled, and master volume at 15%. The screen writes a small versioned selection file in the instance's `.copimine` directory. This avoids credentials, avoids a second window, and works for both MSI and portable/upgrade layouts.

`MinecraftSettingsDefaults` reads the saved selection. It adds only selected keys that are missing from `options.txt`; it never replaces an existing player value. A missing selection file preserves the current compatibility behavior and enables all three defaults, so existing installs are upgraded safely. Once the first-run screen saves, later launches do not reapply or reset the player's choices.

## Visual direction

The installer uses the existing CopiMine identity: deep navy background, teal/green accent, compact sans-serif copy, and the supplied CopiMine mark. The launcher first-run screen uses the same card, spacing, button, and checkbox tokens as the existing settings screen. Copy is direct and user-facing; no internal architecture or AI-generated filler is shown.

## Failure handling

- A non-writable instance path produces a normal launcher diagnostic and leaves the old options file intact.
- A malformed defaults file is treated as absent; compatibility defaults are used and the next successful save repairs it.
- Existing `options.txt` values win over installer defaults.
- Installer asset generation fails the build before packaging if the source logo or required output dimensions are unavailable.

## Acceptance

- The packaging contract and tests prove folder-selecting MSI, branding flags, shortcut locations, and defaults metadata.
- Infrastructure tests prove all checked/unchecked combinations, preservation of existing options, malformed/missing selection compatibility, and idempotence.
- A release build produces signed/runtime-compatible Velopack artifacts and a branded MSI/Setup pair.
- A clean local install verifies the chosen root, `Minecraft` child, first-run selection file, and `options.txt` values without touching production.
