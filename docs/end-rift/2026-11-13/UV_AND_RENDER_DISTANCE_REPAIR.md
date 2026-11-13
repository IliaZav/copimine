# Review follow-up: raw client model UVs and projectile render bounds

CodeRabbit reviewed commits ae8b735c..375a3ee2 and raised **1 minor issue**:
arrow meshes used texture-pixel UVs instead of Minecraft's 0..16 model units.
The issue was reproduced and confirmed across all seven generated spell meshes,
including the older bolt, breath and void-burst assets. The resource-pack
migration normalized these models, but Fabric embeds the raw JSON directly;
the required client alone therefore received the broken coordinates.

The generator now converts atlas pixels to model units. All seven meshes were
regenerated without editing their textures, geometry or silhouettes. Regression
first failed on the old web model, then passed after generation: **14 passed**.
The check enforces 0..16 and samples each face's texture-cell center to ensure
it does not address the transparent atlas row. No migration assertion was
weakened; the pack builder recognizes already normalized models.

The pinned Minecraft 26.3 client bytecode was also inspected with javap:
`Display.shouldRenderAtSqrDistance` compares against
`(getViewRange() * 64 * getViewScale())^2`. The three projectile display factories
incorrectly passed 48 as if it meant blocks. They now pass 48/64, bounding the
base range to 48 blocks. This change is limited to spell/arrow/web displays;
other arena geometry is outside this repair. The real arrow-owner probe now
checks that value as well as spawn, follow, fallback and cleanup behavior.

Focused UV, visual-owner and existing elite-flight tests: **19 passed**.
Wave-AI/fog adapter fixtures were updated for the new production dependency and
model-tag hook; the original assertions remain enabled: **74 passed** including
the asset group. The first full run collected the old fixtures before those
edits: **3535 passed, 13 skipped, 53 setup errors**, not a full pass. Those errors
were the four AI-role cases and 49 fog-lifecycle cases; a fresh full run is
required and its result must be recorded separately.

Logs: `C:/Temp/cm-20261113-combat-arrow-review.ndjson`,
`C:/Temp/cm-20261113-spell-uv-red.log`,
`C:/Temp/cm-20261113-spell-uv-green.log`,
`C:/Temp/cm-display-bytecode-20261113.txt`,
`C:/Temp/cm-20261113-fixture-green.log`,
`C:/Temp/cm-20261113-arrow-limits.log`.

Native model appearance, gameplay, FPS/MSPT and the full official encounter
remain NOT VERIFIED. A mathematically corrected UV map is not a screenshot.
