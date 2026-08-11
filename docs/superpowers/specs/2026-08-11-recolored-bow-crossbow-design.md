# Recolored bow and crossbow textures with custom projectiles

## Scope

Update only the CopiMine resource-pack art for the custom bow and crossbow
artifacts. Do not change gameplay handlers, item IDs, vanilla namespace files,
the world, the website, brewing, shops, or the production server.

## Design

- Use the 1.21.1 vanilla bow, crossbow, and loaded-crossbow PNGs as immutable
  shape references.
- Generate crisp 32x32 PNGs by nearest-neighbour scaling after a deterministic
  pixel-palette transform.
- Keep the existing custom model-data mappings and the existing pull-state
  predicate thresholds.
- Keep the vanilla bow body and string geometry in every pull frame, then draw
  a small explicit custom arrow over the vanilla projectile coordinates. The
  teleport bow arrow is cyan/blue; the trail bow arrow is stone-gray.
- Apply the same treatment to the crossbow base and pull frames. The charged
  explosive-crossbow state uses the vanilla loaded-crossbow silhouette,
  recolored as a custom bolt, with the existing TNT accent animated on top.
- Keep the vanilla `assets/minecraft` textures untouched. Custom files remain
  under `assets/copimine`.

## Verification

The resource-pack tests must verify:

1. every custom bow/crossbow state exists, is 32x32 RGBA, and is referenced by
   the expected model;
2. the three bow pull frames retain a visible recolored custom projectile and
   keep the string/body alpha geometry;
3. vanilla source files are unchanged;
4. the charged TNT/bolt atlas retains valid animation metadata, frame bounds,
   and the loaded-crossbow projectile geometry;
5. the generated ZIP contains all custom animation assets.

The local stack must be started after the build, and its Minecraft endpoint
must accept a connection. No production deployment is part of this change.
