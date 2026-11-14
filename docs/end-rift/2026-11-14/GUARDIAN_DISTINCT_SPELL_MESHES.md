# Distinct Guardian spell volumes

CODE/ASSETS IMPLEMENTED; native appearance and behavior remain NOT VERIFIED.
No tests were run or added under the latest user instruction.

The previous eight-mesh table reused a sphere for a floor rune, an orb for the
Inferno floor crown, a shard for summoning and a single lance for a three-lane
rack. Added four separate low-poly cuboid meshes on the existing standalone atlas:

| Selector | Mesh | Cuboids | Motion |
|---|---|---|---|
| 830208 | void_rune | 17 | horizontal sigil; rotation around vertical normal |
| 830209 | inferno_crown | 20 | upright eight-prong crown, hot tips and rock base |
| 830210 | summon_arch | 13 | upright stepped arch, inward lintels and luminous ribs |
| 830211 | arrow_rack | 12 | three parallel textured shafts and separate tips |

The server shape-selector map, client selector routing and cached mesh loader
now agree. The deterministic generator produces twelve standalone meshes. No
texture repainting, skin replacement, bone/pivot changes, giant AI proxies or
new gameplay/collision authority were introduced. Existing private JSON glob
remains -text for exact LF packaging. Each new mesh stays below the 32-cuboid cap.
The client keeps floor/arch/rack orientation instead of rolling all spells around
Z. Existing orb/projectile presentation is unchanged.

Compilation: server compileEndEventJava and modern-client compileJava succeeded;
see BOSS_RELEASE_ADMISSION_AND_CAST_TARGETS.md for logs. Asset generator was run
to WRITE the four meshes; no --check/test task/native scene was run.

DeepSeek must inspect winding/UV/material bounds, model placement, floor height,
scale, each animation frame and cleanup in a real client. Existing eight-mesh-only
assertions require extension to the twelve authored models. The new server and
client must be built/pinned together before installation; an older client cannot
resolve selectors 830208-830211. Current released/pinned/installed 555565c6 remains
unchanged. This source commit is not a new verified release candidate.
