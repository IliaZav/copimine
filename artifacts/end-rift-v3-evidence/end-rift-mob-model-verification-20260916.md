# End Rift mob model verification — 2026-09-16

This report records the final source/artifact check for the dedicated End Rift
mob visual roles. It is intentionally separate from native Minecraft QA: the
assembled PNGs below prove the UV sheets and the intended silhouettes read
together, while the Java tests prove the model tree and renderer routing.

## Visual evidence

The complete review board is [end-rift-mob-model-board-20260916.png](./end-rift-mob-model-board-20260916.png).
The board contains one assembled preview for every runtime role and labels
the native gameplay envelope next to the render-only model. Individual previews
are in [model-previews](./model-previews/), with the generated
[manifest](./end-rift-mob-model-preview-manifest.json) describing the same
13-entry matrix.

The preview renderer is deterministic and uses the checked-in runtime UV
atlases. It is labelled `Static assembled previews from runtime UV atlases`;
it must not be read as a native Minecraft screenshot.

`STATIC_ASSEMBLED_PREVIEW_ONLY=true`
`NATIVE_MINECRAFT_RENDER_VERIFIED=false`

## Role matrix

| Family | Roles | Native gameplay envelope |
|---|---|---|
| Enderman | ordinary, elite, wave guardian, ritual guard, Wave 6 ritual caster | `0.6 x 2.9` |
| Skeleton | ordinary, elite, wave guardian, ritual guard | `0.6 x 1.99` |
| Spider | ordinary, elite, wave guardian, ritual guard | `1.4 x 0.9` |

The Wave 6 ritual caster is a dedicated `RITUAL_CASTER` model variant with
raised channeling arms and a focus core. It remains an Enderman entity for
gameplay; the added focus, horns, seals, mantles, ribs, carapace and other
parts are render-only model parts. No visual part changes Bukkit collision or
the server-side damage envelope.

## Implementation checkpoints

- `b3dc328a` — separate End Rift visual roles and server/client IDs;
- `8b32bdac` — add dedicated Enderman, Skeleton and Spider role model trees,
  renderer instances and Java model tests;
- `8e4690f1` — publish the client JAR/modpack and refresh distribution
  checksums;
- `3603c4a0` — refresh the third-party manifest to the exact staged artifact
  digests;
- this checkpoint — align preview geometry labels with the actual custom
  Skeleton and ritual-caster renderer IDs and regenerate the board/manifest.

## Verification results

The full local End Rift gate completed with exit code 0:

- Python contracts: `148 passed, 53 warnings`;
- all pure Java policy tests: passed;
- all persistence/recovery tests: passed;
- Fabric client build: `BUILD SUCCESSFUL`;
- resource pack build and artifact/hash checks: passed;
- `git diff --check`: passed.

The warnings are existing Pillow and Java deprecation warnings; none are test
failures.

The UV validator accepted all 13 opaque 64x32 runtime mob atlases. The staged
client JAR contains all seven dedicated wave/role texture entries and the
variant model/renderer classes.

## Current artifact digests

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `CopiMineClient/build/libs/CopiMineClient-0.1.1.jar` | 9,453,629 | `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce` |
| `thirdparty/client-mods/CopiMineClient-0.1.1.jar` | 9,453,629 | `c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce` |
| `minecraft/server/plugins/CopiMineEndEvent.jar` | 845,440 | `11716255ebfae5626c7f534fe97bdda9820e0bc9e32f71f003415e798c887a02` |
| `thirdparty/CopiMineMods.zip` | 21,651,501 | `8c582afbd4f19267bf8645ec304f5c83d9ecbe5465f1351818e095d2a1ed3b7d` |
| `resourcepacks/build/CopiMineResourcePack.zip` | 24,150,260 | `34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9` |
| `end-rift-mob-model-board-20260916.png` | 169,391 | `4bdad83ac846ed49ff4b9336f65d0aa022aa78498dfc75e1236efb589d31a7e7` |

## Native QA boundary

The local server/profile preparation reached `READY_FOR_PLAYERS`, and the
current client JAR was synchronized by SHA-256. A fresh native Minecraft
acceptance capture for this exact head is still `NOT VERIFIED`: the Computer
Use surface returned `apps: []` and a browser request-header-policy error, so
there was no controllable Minecraft `javaw` window from which to take a
current screenshot or record the requested flight video. Historical native
captures are kept separate and are not claimed as evidence for these rebuilt
mob assets.

No production upload or installation was performed. An obsolete local
diagnostic plugin JAR was moved recoverably to
`local-runtime/stale-plugin-quarantine/`; it was not deleted and is not part
of the distributed artifacts.
