# Resource Pack Licenses

Основные правила этого resource pack:
- URL resource pack не меняется;
- narcotics assets добавляются только в namespace `assets/copimine/...`;
- глобальные vanilla block textures не переопределяются.

## CopiMine self-made assets

В этом pack используются self-made assets проекта CopiMine:
- narcotics item textures в `resourcepacks/src/assets/copimine/textures/item/narcotics/`
- narcotics overlay textures в `resourcepacks/src/assets/copimine/textures/gui/narcotics/`
- narcotics font manifest в `resourcepacks/src/assets/copimine/font/narcotics_overlay.json`
- narcotics visual manifests в `resourcepacks/src/assets/copimine/manifests/`
- shader/profile descriptor JSON в `resourcepacks/src/assets/copimine/shaders/narcotics/`
- block visual marker models/textures, если рядом не указан отдельный NOTICE

Статус:
- internal CopiMine self-made assets
- внешние proprietary assets в narcotics patch не встраивались

## Optional client mod reuse

`CopiMineClient` использует адаптированные копии уже существующих self-made overlay PNG:
- source: `resourcepacks/src/assets/copimine/textures/gui/narcotics/`
- target: `CopiMineClient/src/main/resources/assets/copimineclient/textures/visuals/`
- status: self-made internal CopiMine assets reused for optional client-side rendering

## Third-party review

Проверка permissive sources и отказ от unsafe assets задокументированы в:
- `resourcepacks/THIRD_PARTY_NARCOTICS_VISUALS.md`

На текущем этапе сторонние visual assets в narcotics pack не встроены.
