# End Rift wave-mob visual follow-up — 2026-09-16

## Result

The wave skeleton and elite skeleton now have a dedicated long-form client rig
and a shared dark-purple texture contract. The ordinary and elite variants are
selected only for server-bound event UUIDs; vanilla skeleton entities keep
their normal renderer path and gameplay hitbox. Wave 6 ritual casters now have
their own raised-arm client pose and a server-side state machine: they channel
while guards are alive, keep channeling while exposed after the guards fall,
and wake into combat only after an accepted hit.

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
- `copimine-end-event/src/me/copimine/endevent/domain/RitualCasterTacticsPolicy.java`
  - defines the guarded-channel, exposed-channel, and awakened-attack states;
  - gives caster slots 0–5 six distinct attack identities.
- `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
  - freezes caster AI and player targeting while the guard gate is active;
  - persists the awakened flag in entity PDC after the first authoritative hit;
  - dispatches six explicit attacks: sphere barrage, rift mark, reverse pull,
    control swap, void lance, and rift spikes;
  - publishes the dedicated `END_RIFT_RITUAL_CASTER_V1` visual id.
- `CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java`
  - adds a dedicated caster variant with raised arms and a pulsing focus part.
- `CopiMineClient/src/main/java/me/copimine/client/EndermanRendererSelection.java`
  - binds `END_RIFT_RITUAL_CASTER_V1` to the dedicated caster model and atlas.
- `CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_ritual_caster.png`
  - adds the opaque 64×32 raised-arm channel atlas.
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
  - refreshed the distributed client JAR, modpack ZIP, checksums, snapshot, and
    third-party manifest after the client build.

## Visual proof

`artifacts/end-rift-v3-evidence/wave-mob-reference-style-proof-20260916.png`

The proof board is deliberately labelled static source/artifact proof. It
shows the exact supplied references beside the generated atlases, the
continuous child-part geometry, and the passive raised-arm caster pose with a
channel sphere. It is not a native Minecraft screenshot.

## Verification

| Check | Result |
| --- | --- |
| `python -m pytest -q tests/test_wave6_ritual_caster_behavior_contract.py tests/test_end_event_wave_mob_visual_contract.py tests/test_end_event_current_contract.py tests/test_end_event_wave6_wave7_boundaries_contract.py` | 121 passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File CopiMineClient/build-client.ps1` | `BUILD SUCCESSFUL` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunEndRiftEventChecks.ps1` | passed: 144 Python contracts, all listed pure-Java policy tests including `RitualCasterTacticsPolicyTest`, client/server builds, resource pack, packaging, and hash gate |
| Source-built/staged client JAR SHA-256 | `301e026d20dc50254fac1c9df1283efcb0896d3beec4d79d1c184a7b7640a0b3` |
| Staged `thirdparty/CopiMineMods.zip` SHA-256 | `93d3f9cc6aca40e731f9a095666c3818b32fc35d74f731cd86b8103ccd8a89b0` |
| Static proof PNG SHA-256 | `729950c71e38d612fa4d04dda1264f527804f1e55083976aa7ac886076a32f7e` |

## Native capture boundary

On 2026-09-16 the Computer Use surface was queried after the source build and
returned `apps: []`. No native Minecraft window was available to that control
surface, so a fresh in-game screenshot or 15-second flight video cannot be
honestly asserted for this change. The existing native artifacts from earlier
runtime work are retained as historical evidence, but are not presented as a
capture of these newly rebuilt wave-mob assets.
