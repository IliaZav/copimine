# End Rift wave-mob visual follow-up — 2026-09-16

## Result

The wave skeleton and elite skeleton now have a dedicated long-form client rig
and a shared dark-purple texture contract. The ordinary and elite variants are
selected only for server-bound event UUIDs; vanilla skeleton entities keep
their normal renderer path and gameplay hitbox.

The same clean surface rules were applied to the wave Enderman, elite, and
spider atlases: opaque texels, a compact purple palette, and sparse symmetric
accents instead of semitransparent guide pixels or seeded noise.

## What was changed

- `CopiMineClient/tools/generate_end_rift_texture_atlases.py`
  - removed semitransparent atlas seams and interpolated noise colours;
  - made the generated event atlases fully opaque;
  - moved wave skeleton, elite, Enderman, elite, and spider surfaces into the
    supplied dark-purple visual family;
  - replaced the small seeded sigil polyline with a symmetric diamond/cross.
- `CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModel.java`
  - added a long skeleton bind pose with explicitly attached upper/lower arm
    and leg parts;
  - lower segments overlap their parent segments at each joint, so animation
    cannot expose a UV transparency gap;
  - elite shoulder plates are render-only model parts and do not change the
    entity collision shape;
  - idle limb pulse is bounded and remains inside the vanilla model contract.
- `CopiMineClient/src/main/java/me/copimine/client/RiftEventSkeletonModelRenderer.java`
  - owns separate ordinary and elite model instances.
- `CopiMineClient/src/main/java/me/copimine/client/mixin/LivingEntityRendererMixin.java`
  - performs a scoped model swap for `END_RIFT_SKELETON_V1` and
    `END_RIFT_ELITE_SKELETON_V1` only when the renderer is a skeleton renderer;
  - restores the selector at the render boundary, so the shared vanilla model
    is not mutated across entity renders.
- `CopiMineClient/tools/render_wave_mob_visual_proof.py`
  - renders a deterministic review board from the supplied references, the
    generated 64×32 UV sheets, and the source-rig bind-pose schematic.
- `thirdparty/` and `admin-web/frontend/assets/public-data/`
  - refreshed the distributed client JAR, modpack ZIP, checksums, and snapshot
    after the client build.

## Visual proof

`artifacts/end-rift-v3-evidence/wave-mob-reference-style-proof-20260916.png`

The proof board is deliberately labelled static source/artifact proof. It
shows the exact supplied references beside the generated atlases and the
continuous child-part geometry. It is not a native Minecraft screenshot.

## Verification

| Check | Result |
| --- | --- |
| `python -m pytest -q tests/test_end_event_wave_mob_visual_contract.py tests/test_end_event_current_contract.py` | 98 passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File CopiMineClient/build-client.ps1` | `BUILD SUCCESSFUL` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftEventChecks.ps1` | passed: 135 Python contracts, all listed pure-Java policy tests, client/server builds, resource pack, packaging, and hash gate |
| Source-built/staged client JAR SHA-256 | `63eaabc15e576b305591996df1fa7f2088b8cbfd31e7b98608592ac9a72cf4c9` |
| Staged `thirdparty/CopiMineMods.zip` SHA-256 | `4b44585f6c10077679ed4429999412ffa3de9913da43f1a693f1945d1445e771` |
| Static proof PNG SHA-256 | `9f99598fbf3a813bf27911eccad9c59e100d09ad6a32f773398f709b75343726` |

## Native capture boundary

On 2026-09-16 the Computer Use surface was queried after the source build and
returned `apps: []`. No native Minecraft window was available to that control
surface, so a fresh in-game screenshot or 15-second flight video cannot be
honestly asserted for this change. The existing native artifacts from earlier
runtime work are retained as historical evidence, but are not presented as a
capture of these newly rebuilt wave-mob assets.
