# End Rift HUD art directions (2026-11-21)

Every major bar was authored twice and compared before shipping.

- Direction **a** (shipped in the client jar): dark forged obsidian and
  steel with dull bone inlays and restrained purple embers.
- Direction **b** (archived alternative): pale bone and iron with warm
  ember accents; kept so the owner can compare after the playtest.

The filenames ending in `_a`/`_b` are the editable preview exports.
The shipped copies live under
`CopiMineClient/src/main/resources/assets/copimineclient/textures/gui`
and are produced by `scripts/minecraft/GenerateEndRiftHudRedesign.py`.

Pairs cover the Ram rail and seven-plate atlas, the Guardian phase
overlay and portrait aura, and all four Wave 7 frames and portraits
(12 pairs, 24 PNGs). The Guardian state glyphs are silhouette-first and
identical in both directions, so they are not duplicated here.

To rebuild:

```
python scripts/minecraft/GenerateEndRiftHudRedesign.py
```

The generator is deterministic: rebuilding into a scratch directory
produces byte-identical PNGs, so the client jar pin stays reproducible.
It clears each health recess last, after every river, crack and inlay,
so the renderer's real HP fill is never hidden behind the art.
