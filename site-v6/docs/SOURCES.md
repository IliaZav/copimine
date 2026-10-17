# Source inventory / v5

## Catalogue, not a live database
Repository IliaZav/copimine; branch codex/site-launcher-audit; file copimine-artifacts/items.yml; blob 8d564579d1b10dd44edce6b409e8951abf93bd28. Reviewed 2026-10-16 through the connected repository.
41 source records: 20 public AR, 11 public CM, 8 private notes and 2 administrator-only records. Removed compass is absent. Public visibility is an explicit website policy. Display names with profanity/slang are normalized short editorial aliases; IDs, selected prices and effects remain traceable. Full source effect descriptions must be checked again against the deployed plugin before launch.
Other branch end-rift-26-3-integrated-final has a different night_cloak record and still contains the old compass. Do not merge these catalogues implicitly. This preview selects the catalogue branch matching the requested new items and compass removal, not an assertion that this is the live server state.

JSON: data/catalog-current.json. Manifest: sources/catalog-source.json. SQLite: data/prototype.sqlite, public_products view. SQL and seed are review fixtures, not a production backup.

## Images and game assets
- Logo/wordmark and world.webp: original user materials from the supplied CopiMine launcher archive. The wordmark and mobile C are separate. No synthetic scenery.
- Official client/resource-pack assets came via the repository Actions artifact minecraft-26.3-event-test-inputs, run 38771187407, artifact 11992575996. The JAR and server executables are NOT redistributed here.
- Five current branch item sprites fetched separately from admin-web/frontend/assets/item-textures: gravedigger_contract, signal_bell, berserker_heart, zhilorez_pickaxe, night_cloak. These avoid outdated event-reward art.
- assets/materials contains 1617 item representations. 833 block icons are projections of the original cuboid model elements and texture UVs, rendered by scripts/render_block_icons.py. Other applicable items use original flat sprites/first animation frames. Special non-cuboid meshes without a reliable icon are enumerated in sources/materials-without-flat-sprite.json; they are not silently fabricated.
- assets/gui/inventory.png: original Minecraft player inventory GUI, displayed with actual 41 equipment/storage/offhand coordinates. No screenshot of another player's inventory is passed off as data.
- assets/skins: default Minecraft skin textures. Default skin != resolved skin of a real account. PNG upload creates matching head/front/back. Head coordinates and all skin imports stay offline.
- No font files are included. UI uses system fonts and local SVG icons; see licenses/.

Minecraft assets remain the property of their rightsholders. This is a project-specific local review bundle, not a standalone texture-pack licence grant. Review public redistribution requirements before production.

## World
User archive copimine-map-preview.tar(1).gz, SHA256 d2a955c51ee1938249045a05968a4344c9f426c4417e5e46d0188acb842261e2. Exactly four Anvil regions / 4096 chunks, X/Z -512..511, 1024 square blocks. DataVersion 3955 is recorded from source data, not changed to the current client version.
The image is built from surface blocks and original block textures. No fake JourneyMap zoom, no cave/ore scan and no online-player positions. It is a saved snapshot, not the entire server world and not proof of current server state. Original region files are excluded from the distributable archive.

## Localization and private data
608 Russian material labels are bundled; the rest retain official English and namespaced IDs. The picker accepts a complete official ru_ru.json import. No machine-invented translation of unverified identifiers. Unknown log actions retain their source text rather than receiving a false translation.
Demo accounts, inventory snapshots, programmes, payments and log examples are artificial. Imported user data is explicitly marked as imported. Nothing here reads real account passwords, live inventories or private database records.
