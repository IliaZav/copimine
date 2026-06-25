# CopiMine Narcotics Visual Asset Search

Date: 2026-06-24

## Search summary

Before embedding narcotics visual assets, the project was checked for:

- compatibility with vanilla and Paper 1.21.1 resource-pack workflow;
- whether a safe path exists without OptiFine, Iris, or other required client mods;
- redistribution-safe licensing;
- whether existing self-made CopiMine overlay assets can be reused honestly for an optional client mod.

## Reviewed sources

Compatibility references:

- Fabric Documentation: Networking
  - https://docs.fabricmc.net/develop/networking
- Fabric Documentation: Creating a Project
  - https://docs.fabricmc.net/develop/getting-started/creating-a-project
- FabricMC fabric-example-mod template
  - https://github.com/FabricMC/fabric-example-mod
- Minecraft Wiki: Pack format
  - https://minecraft.fandom.com/wiki/Pack_format

Permissive asset candidates reviewed:

- OpenGameArt: 700+ Noise Textures
  - https://opengameart.org/content/700-noise-textures
  - Author or uploader: Screaming Brain Studios
  - License: CC0
- OpenGameArt: rain
  - https://opengameart.org/content/rain-1
  - Author: gmason
  - License: CC0
- OpenGameArt: CC0 Textures
  - https://opengameart.org/content/cc0-textures-1
  - License collection page with CC0-only entries

## Decision

No third-party narcotics overlay, font, or shader asset was embedded in this client-bridge patch.

Reasons:

- the CopiMineNarcotics baseline must remain honest and fully playable in fallback mode;
- the server must not require client shader mods;
- the optional `CopiMineClient` mod reuses existing self-made CopiMine overlays and does not need external art to become functional;
- Paper cannot force Iris/OptiFine shaderpacks and therefore must not claim true server-side shaders.
- Paper server cannot reliably force true client post-processing shaders, so shader descriptors stay documentation/client-profile only and the live server path resolves to client-mod visuals, resource-pack overlay, or particle fallback.

## Embedded assets

This patch uses only self-made assets for narcotics:

- item textures in `src/assets/copimine/textures/item/narcotics/`;
- server fallback overlay PNG files in `src/assets/copimine/textures/gui/narcotics/`;
- font provider manifest in `src/assets/copimine/font/narcotics_overlay.json`;
- shader/profile descriptor JSON files in `src/assets/copimine/shaders/narcotics/`;
- optional client mod copies/adaptations in `CopiMineClient/src/main/resources/assets/copimineclient/textures/visuals/`.

## Restrictions

- No hotlinking
- No runtime external downloads
- No random third-party packs without explicit license review
- No false claim of live server-side shader support
- Server overlay mode is asset-backed and admin-gated
- Client mod visuals are optional and do not require Iris or OptiFine
