# Review of the stopped DeepSeek overnight work

Reviewed on 2026-11-10 before the user's authorized final deployment.
The original integration checkout remains at f8ef41af. The boss worktree gained:

- b7c69df4: committed the existing boss work plus spider web BlockDisplay /
  impact presentation, translucent TAB background, and refreshed artifact pins.
- fddfeb66: rebuilt/restaged the legacy client using the pinned Java compiler.

To distinguish inherited work from genuinely new changes, reconstructed our
previous exported patch on f8ef41af in a temporary Git index. The resulting
snapshot tree is 0c5170400838db12a35b166bc7b63fdbc079279d. Compared with HEAD,
the added overnight work touches 20 files, including 135 changed adapter lines.
It is not 75 files of new boss work: most of b7c69df4 preserves our prior changes.

## Reviewed and retained

- Spider web: COBWEB BlockDisplay with centred 0.55-block tumbling transform,
  ItemDisplay fallback on spawn rejection. Generic Display lifecycle permits
  both types. Projectile validation, ownership and expiry remain authoritative.
- Accepted web root: bounded cobweb particles and sounds after the existing
  legality/protection gate. The new helper does not implement the claimed
  screen vignette; no new vignette is advertised as verified.
- TAB: neutral slate at 0x8C alpha in both client trees, paired layout tests.
- Legacy distribution: rebuilt with pinned Temurin 21.0.12.1; metadata and
  archive refreshed together. This remains separate from the active 26.3 mod.

The web/WorldGuard/source-model group passed 15 checks with the revised portrait.
Active client JUnit and server bridge builds are independently rerun. This is
source/automated evidence, not native gameplay acceptance.

## Defect found and corrected

The checked-in GeneratedBossAnimationPoses fingerprint was produced under a
PowerShell host with a different default source-text encoding. PowerShell 7
-Check rejected it while Windows PowerShell 5.1 accepted it; numeric pose tables
were unchanged. All generator text inputs and the comparison now explicitly
use UTF-8. Regeneration restores the stable source digest
0038745892de8a69653a5fd64eca4b0d98158c8574c465e4bca948b17233748f.
Both actual hosts pass -Check, and a behavioral Windows PowerShell regression
compares newly generated Java bytes with the committed table.

## Latest user requirements

The old illustrative skull is superseded by an exact 37-cube UV-rendered head
portrait from the supplied geometry/skin. A separate transparent bone frame
surrounds the real health track. The original boss geometry, texture and seven
clips are preserved. New portrait renderer is a build-time authoring tool only.

The user explicitly stopped DeepSeek and assigned final build/deployment to
Codex. The earlier release-owner coordination hold is resolved.
