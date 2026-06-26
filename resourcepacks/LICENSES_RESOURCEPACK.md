# Resource Pack Licenses

Основные правила этого resource pack:
- URL resource pack не меняется.
- Narcotics assets добавляются только в namespace `assets/copimine/...`.
- Глобальные vanilla block textures не переопределяются.

## CopiMine Self-Made Assets

В этом pack используются собственные ассеты проекта CopiMine:
- narcotics item textures в `resourcepacks/src/assets/copimine/textures/item/narcotics/`
- narcotics overlay textures в `resourcepacks/src/assets/copimine/textures/gui/narcotics/`
- narcotics font manifest в `resourcepacks/src/assets/copimine/font/narcotics_overlay.json`
- narcotics visual manifests в `resourcepacks/src/assets/copimine/manifests/`
- shader/profile descriptor JSON в `resourcepacks/src/assets/copimine/shaders/narcotics/`
- block visual marker models/textures, если рядом не указан отдельный NOTICE

Статус:
- internal CopiMine self-made assets
- внешние proprietary assets в narcotics patch не встраивались

## Optional Client Mod Reuse

`CopiMineClient` использует адаптированные копии уже существующих self-made overlay PNG:
- source: `resourcepacks/src/assets/copimine/textures/gui/narcotics/`
- target: `CopiMineClient/src/main/resources/assets/copimineclient/textures/visuals/`
- status: self-made internal CopiMine assets reused for optional client-side rendering

## Third-Party Review

Проверка permissive sources и отказ от unsafe assets задокументированы в:
- `resourcepacks/THIRD_PARTY_NARCOTICS_VISUALS.md`

На текущем этапе сторонние visual assets в narcotics pack не встроены.

## Build And Provenance Notes

- CopiMine resource pack is built locally from `resourcepacks/src` by `resourcepacks/build-resourcepack.py`.
- Narcotics item textures, overlays, manifests, and related fallback visuals are self-generated inside the CopiMine project.
- These narcotics visuals are self-made project assets, not hotlinked files and not runtime-downloaded assets.
