# Guardian mouth HUD art

Created with the built-in image generator on 2026-11-10 using the actual
`end_rift_user_boss.png` atlas and the previous HUD frame as references.
The original skeletal model, geometry, UV and seven supplied clips remain intact.

## Generation direction

Create an original transparent pixel-art boss health-bar frame for Minecraft.
Base the materials on the supplied Guardian skin: ivory skeletal bone, dark
organic flesh and deep violet rift energy. Put a recognizable open fanged
mouth on the left, with layered teeth and a dark throat. Extend a straight
ornate horizontal track from the mouth, framed by bone and restrained purple
cracks. Keep the central health track genuinely transparent, with no baked
health fill, text, letters, numbers or white backdrop. Preserve a coherent
silhouette and detailed deliberate pixel edges at small HUD size. Avoid a
rectangular opaque background. The mouth and track form one creature-themed
object rather than an unrelated icon beside a generic bar.

## Output and integration

- Generated output copied verbatim; no image editor or resampling step.
- Asset: `CopiMineClient/src/main/resources/assets/copimineclient/textures/gui/end_rift_bossbar_guardian_mouth_v2.png`.
- Actual RGBA canvas: 2172 x 724, displayed at 360 x 120 with proportional
  scaling for narrow viewports.
- Health track: local HUD x=78..332, y=56..70. Automated alpha coverage exceeds
  99.5 percent transparent in that interior region.
- True HP, phase, shield/cast state and damage chip are rendered separately.
- Native gameplay readability and visual acceptance remain unverified.

## Revision 3: exact source-model portrait, 2026-11-10

Latest user direction replaces the illustrative skull with the actual model's
face. `render_guardian_head_portrait.py` renders all 37 cubes of source bone
`head`, using the same reflected X/Y coordinates, Z/Y/X bind rotation order,
cube pivots and individual UV faces as the active Chameleon renderer. It samples
`end_rift_user_boss.png` directly onto a 128x128 transparent pixel canvas.
The portrait and its manifest record the original geometry/skin hashes and the
actual generated image hash. Original geometry and texture are unchanged.

A separate imagegen-authored symmetric bone/violet frame contains no substitute
face. Its generation prompt requested a transparent 3:1 frame only, no head,
no skull, no eyes, no text, no health fill; an unobstructed straight opening at
x6..94%, y44..60%, ivory hooks and restrained purple cracks. The generated
2172x724 output was copied verbatim as `end_rift_bossbar_frame_v3.png`.
The exact source portrait is rendered independently above the centre of the
track, so generative art cannot change its identity. Health interior x30..330,
y92..101 in a 360px HUD; alpha inspection confirms the interior is transparent.

These are source-derived static assets, not native gameplay screenshots.
