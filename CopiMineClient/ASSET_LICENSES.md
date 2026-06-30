# CopiMineClient Asset Licenses

Client overlay textures in `src/main/resources/assets/copimineclient/textures/visuals` are adapted from CopiMine self-made fallback assets:
- source: `resourcepacks/src/assets/copimine/textures/gui/narcotics/`
- status: internal CopiMine self-made assets
- hotlinking: none
- runtime external downloads: none

Utility textures such as `noise.png`, `vignette.png`, and `scanlines.png` are local CopiMine helper assets for fallback rendering.

Bundled ZIP shaderpacks in `src/main/resources/assets/copimineclient/shaderpacks` are local project assets supplied by the project owner for CopiMineClient runtime testing and bundled distribution:
- `acid_shaders.zip`
- `crucify.zip`
- `ctr_vcr.zip` (normalized from `CTR-VCR_v1.4.4.zip`)
- `cursed_metamorphopsia.zip`
- `jelly_world.zip`
- `lsd_shader.zip` (normalized from `LSD_Sshader_v1_3.zip`)
- `nms_1_6.zip`
- `trippy_shaderpack.zip` (normalized from `Trippy-Shaderpack`)
- `white_sharp_1_2.zip`

Runtime note:
- when Iris runtime switching is available, CopiMineClient uses built-in compatible ZIP shaderpacks as the primary live route;
- when a bundled ZIP is not Iris-compatible, CopiMineClient falls back to its internal post-process runtime instead of pretending the ZIP was enabled;
- `jelly_world.zip` is exported together with the rest of the packs, but remains a fallback-only vanilla shader override asset because it does not contain an Iris-compatible root `shaders/` directory;
- no external shaderpack download is required from the player.
