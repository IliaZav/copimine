# Authored-texture review — 2026-11-14

Scope: every authored PNG reachable from the three roots that feed the shipped
client and pack. Read-only; the review measured, it did not repaint.

| Root | Meaning | PNGs |
|---|---|---:|
| `resourcepacks/src` | shipped CopiMine pack (`assets/copimine/…`) | 118 |
| `tools/minecraft-26.3/client/src/main/resources` | active Fabric client mod resources | 1 |
| `CopiMineClient/src/main/resources` | legacy client tree, still embedded by the build | 84 |
| **total** | | **203** |

`tools/minecraft-26.3/client/build.gradle` shows why the "active client" root holds
almost nothing: line 33 adds
`CopiMineClient/src/main/resources` as a resource source set and line 41 copies
`resourcepacks/src` into the jar. So the jar carries **both** trees, and any sheet
that exists in both must be changed in both or the two copies drift.

Reproduce from the repository root: `python scripts/minecraft/texture_review.py`.

## Result 1 — no dead or corrupt sheets

`0` fully transparent PNGs and `0` unreadable files across all 203. Every sheet has
real content.

## Result 2 — non-power-of-two is not a defect here

14 pack sheets and 9 legacy sheets are NPOT (`1024x205` logo, `128x26` tab strip,
`2172x724` boss-bar frame v3, `1312x1199` and `1254x1254` atlases/sources, the HD
item sheets). Modern Minecraft samples NPOT textures directly, so none of these is a
bug. Recorded only so a later reader does not "fix" them.

## Result 3 — one real defect: the full-stage obelisk sheet disagrees with its siblings

The script reports five same-named families whose members disagree on size. Four of
them are benign, and saying so is the point of listing them:

| Family | Members | Verdict |
|---|---|---|
| `pack/item/end_event_rift_fireball` | `32x32` + `_hd` `1254x1254` | benign — a particle-precision sheet beside its HD source |
| `client-legacy/entity/end_event_rift_fireball` | `32x32` + `_hd` `1254x1254` | benign — same pair in the legacy tree |
| `client-legacy/gui/end_rift_bossbar_frame` | `256x32` frame + `_v3` `2172x724` | benign — two authored revisions of one bar, not two states of one art |
| `pack/item/end_event_rift_obelisk` | three stages `1254x1254`, `full_hd` **`1240x1269`**, `pulse_hd` `1254x1254` | **real defect** — the four stages share one model and one UV box |
| `client-legacy/entity/end_event_rift_obelisk` | the same four stages plus their `32x32` particle pairs | **real defect** — the identical legacy copy of the sheet above |

Only the obelisk family is an actual mismatch: the other four pair a
deliberately different asset with its sibling, and nothing samples them through a
common UV box.

The four obelisk stages are drawn through the **same** model with identical
normalised `[0,0,16,16]` UV boxes (verified in
`resourcepacks/src/assets/copimine/models/item/end_event_rift_obelisk_*.json`), so a
size mismatch is not a sampling failure — it stretches one stage's art against the
others and the seal visibly shifts proportion when the obelisk advances a stage.

| Stage | Sheet (pack `item/`, and the identical legacy `entity/` copy) | Size |
|---|---|---:|
| full | `end_event_rift_obelisk_full_hd.png` | **1240x1269** |
| damaged | `end_event_rift_obelisk_damaged_hd.png` | 1254x1254 |
| critical | `end_event_rift_obelisk_critical_hd.png` | 1254x1254 |
| pulse | `end_event_rift_obelisk_pulse_hd.png` | 1254x1254 |

The full-stage sheet has a different pixel aspect ratio from its siblings.
These dimensions alone do not prove that the rendered obelisk is stretched:
that depends on the authored UV coordinates, atlas mapping and geometry.

## Why this was not changed in this increment

Resampling the complete sheet to `1254x1254` while preserving normalized UVs
would preserve the same sampled artwork proportions on the model; it is not
a demonstrated remedy for model distortion. First identify the incorrect UV
region or artwork proportions using actual before/after client frames, then
apply the matching source-art or UV correction across the coupled assets.

The objective is explicit that a visual requirement cannot be closed by a mechanical
check: "an unavailable native check is BLOCKED, never a pass". Repainting authored art
and then asserting it is better without a client frame to compare is exactly that
mistake. So the measurement is recorded here and the repaint is left for a build that
can show before/after frames. It is **not** claimed as fixed.

## Verification status

| Item | Status |
|---|---|
| All 203 authored PNGs measured (size, mode, opacity) | **DONE** (script output above) |
| Fully transparent / corrupt sheets | none found |
| Family-level size disagreement, all roots | **5 families reported**: the four obelisk stages are the only real mismatch; the other three pair a deliberately different asset with its sibling |
| Obelisk repaint implemented | **NOT DONE** — deferred pending visual before/after |
| Repainted sheets verified on screen | **BLOCKED** — needs a real client with F2 capture |
