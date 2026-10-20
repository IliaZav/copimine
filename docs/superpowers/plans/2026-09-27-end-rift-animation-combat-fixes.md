# End Rift Animation and Combat Fixes

**Goal:** Correct the user-reported End Rift mob issues from the latest Minecraft captures: incomplete skeleton variants, broken/disassembled tentacle grab animation, missing tentacle hurt flash, undersized/slow tentacle, unreachable or ineffective tentacle damage, and incorrect Netherite item pose.

**Constraints:** Preserve the dirty worktree and supplied source assets. Do not change production servers. User has authorized replacing the local client/plugin artifacts and restarting the local Minecraft/server processes when needed. Do not claim visual completion without fresh in-game evidence. Keep the work restricted to requested client visuals and local End Rift combat behavior.

## Work sequence

1. Trace current skeleton UV/role selection and Netherite model generation against the installed client/resource-pack artifacts; identify stale or incomplete inputs before editing.
2. Trace the tentacle rig, imported clip sampling, render scale, and server interaction carrier through the exact grab/release state transition that produces the detached segments.
3. Identify the authoritative grab timing and throw-damage path. Set requested throw damage to 10 health points (5 hearts), and ensure the enlarged visible tentacle has a matching damageable hit area.
4. Make minimal source fixes, rebuild the client/resource pack and local plugin only where affected, then install/restart the already-authorized local runtime.
5. Record build/runtime evidence separately from visual verification. If the desktop bridge cannot produce fresh captures, leave clear exact screenshot checks for the user to perform.

## Verification boundaries

- Do not edit unrelated assets or rewrite supplied textures to mask a UV/model problem.
- Never infer a successful visual repair from compilation or server logs alone.
- Do not run automated tests unless the user explicitly requests tests; inspect existing tests/contracts and use build plus local runtime evidence where useful.
