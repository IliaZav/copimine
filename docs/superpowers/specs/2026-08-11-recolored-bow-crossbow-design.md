# Recolored bow and crossbow textures

## Scope

Update only the CopiMine resource-pack art for the custom bow and crossbow
artifacts. Do not change gameplay handlers, item IDs, vanilla namespace files,
the world, the website, brewing, shops, or the production server.

## Design

- Use the 1.21.1 vanilla bow and crossbow PNGs as immutable shape references.
- Generate crisp 32x32 PNGs by nearest-neighbour scaling after a deterministic
  pixel-palette transform.
- Keep the existing custom model-data mappings and the existing pull-state
  predicate thresholds.
- Remove projectile pixels from custom bow pull frames so no custom arrow is
  baked into the bow art.
- Apply the same treatment to the crossbow base and pull frames. Keep the
  existing TNT visual only in the charged explosive-crossbow state, where it
  is animated independently; do not add an arrow to that state.
- Keep the vanilla `assets/minecraft` textures untouched. Custom files remain
  under `assets/copimine`.

## Verification

The resource-pack tests must verify:

1. every custom bow/crossbow state exists, is 32x32 RGBA, and is referenced by
   the expected model;
2. the three bow pull frames do not contain the removed projectile mask;
3. vanilla source files are unchanged;
4. the charged TNT atlas retains valid animation metadata and frame bounds;
5. the generated ZIP contains all custom animation assets.

The local stack must be started after the build, and its Minecraft endpoint
must accept a connection. No production deployment is part of this change.
