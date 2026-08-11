# Recolored Bow/Crossbow Resource Pack Plan

> **For the implementation agent:** execute this plan task by task and keep the scope limited to resource-pack generation, models, tests, and local verification. Do not modify gameplay handlers, brewing, shops, ATM logic, the production server, or the world.

**Goal:** Replace the hand-drawn bow/crossbow art with deterministic 32x32 nearest-neighbor recolors of the local vanilla 1.21.1 frames, while preserving custom model predicates, custom artifact identities, and visible custom projectiles during use.

**Scope:** `resourcepacks/` and focused resource-pack tests only. Vanilla files under `src/assets/minecraft/` must remain untouched. Existing TNT animation remains only for the charged explosive-crossbow state; bow pull frames and the loaded crossbow use explicit recolored custom projectiles.

## Task 1: Add failing source/artifact contract tests

Files:

- Add `tests/test_recolored_vanilla_bow_crossbow_contract.py`.
- Use the local `ServerRP.jar` vanilla item frames as the required reference source.

Steps:

1. Assert the reference frames exist for bow base/pulling and crossbow standby/pulling.
2. Assert the generator exposes a deterministic vanilla-recolor path, explicit bow projectile coordinates, and the loaded-crossbow reference.
3. Assert generated custom frames are RGBA 32x32, retain animation differences, and keep recolored bow projectiles visible.
4. Assert crossbow base/pulling alpha geometry matches its vanilla reference while its palette differs.
5. Assert the charged explosive-crossbow atlas retains the loaded-crossbow bolt geometry, TNT accent, and valid animation metadata.
6. Assert no vanilla item texture is created or changed in the packaged source tree.
7. Run only this new test and the existing bow/crossbow contract test; record the expected failure before implementation.

## Task 2: Implement deterministic recolor generation

File:

- Modify `resourcepacks/generate_new_item_textures.py`.

Steps:

1. Load reference PNGs from `resourcepacks/reference/vanilla/item/` and keep the reference directory outside the packaged `src` tree.
2. Add palette-preserving-alpha nearest-neighbor recoloring helpers.
3. Preserve the bow/string geometry and recolor the explicit vanilla projectile coordinates with the per-artifact arrow palette.
4. Generate teleport bow and cobblestone-trail bow base/pulling frames from recolored vanilla bow frames with their custom arrows.
5. Generate explosive crossbow standby/pulling frames from recolored vanilla crossbow frames.
6. Build charged explosive crossbow states from the recolored vanilla loaded-crossbow frame, keeping the custom bolt and existing TNT accent/animation.
7. Preserve existing filenames, model references, CMD values, animation frame counts, and 32x32 output size.
8. Run the focused tests and fix only resource-pack issues they expose.

## Task 3: Build and verify the local release surface

Files/commands:

- `resourcepacks/build-resourcepack.py`
- `scripts/local-stack.ps1`

Steps:

1. Build the resource pack with the project Python environment.
2. Run focused tests, then the full `tests` suite and the PowerShell resource-pack validator.
3. Run `git diff --check` and inspect the diff/status to ensure unrelated pre-existing changes are not altered.
4. Restart only the local stack, verify Minecraft/RCON/website status, and issue a harmless RCON `list` probe so the refused connection is resolved.
5. Verify the built ZIP contains the custom models/textures and does not contain `assets/minecraft/textures/item` overrides.
6. Report exact pass/fail counts, local endpoint status, ZIP hash, and any unverified manual visual behavior. Do not deploy to production.
