# End Rift HUD and 3D presentation redesign (2026-11-21)

Scope: the End Rift event only. This record covers C1–C4 of the 2026-11-20
mandate, the honest state of C5, and the exact evidence that exists. It does
not claim any visual result that was only compiled, unit-tested, or rendered
synthetically.

Status vocabulary: SOURCE-VERIFIED / AUTOMATED-TESTED /
LIVE-PAPER-VERIFIED / VISUAL-VERIFIED / PERFORMANCE-VERIFIED /
CLIENT-PUBLISHED / SERVER-INSTALLED / PLAYTEST CANDIDATE / NOT VERIFIED.

## 1. Selected direction and the archived alternative

Every major bar was authored twice from the same deterministic source:

- **Direction a — shipped.** Dark forged obsidian and steel, dull bone inlays,
  restrained purple embers, high contrast at GUI scale 1–4.
- **Direction b — archived.** Pale bone and iron with warm ember accents. It is
  kept, unpinned, under `docs/end-rift/2026-11-21/art-directions/` so the owner
  can compare it after the playtest without blocking this release.

The archived previews are `*_a.png` / `*_b.png` pairs; each pair is byte
distinct. The client jar ships the direction-a copies only.

## 2. C1 — Wave 6 Rift Ram: the chained, armoured seal

`RiftRamBossBarHud` is rebuilt around two independent readouts:

1. the Ravager's **real, server-authoritative HP** on a separate track;
2. **seven armour plates**, one per accepted Ram/pillar collision
   (four normal pillars = impacts 1–4, three monumental final-tower hits =
   impacts 5–7).

A plate is intact until the server publishes the durable visual
`END_RIFT_RAM_ARMOR<n>_V1` with `n >= plate + 1`. The client never infers a
break from a started animation, a visual-only collision, a duplicate packet or
client prediction. A lower count on the same entity is treated as a reordered
or stale snapshot and ignored — rewinding would replay a break and its sound.

### Geometry

| Part | Value |
|---|---|
| Bar | 300 × 56 logical pixels, safe-scaled to the GUI width |
| Plate atlas | `ram_seal_plates.png`, 256 × 128, 8 columns × 4 rows of 32 × 32 |
| Plate 0 | x = 16, step 40, y = 4 |
| HP track | (18, 40) → (282, 52), transparent recess in the rail |
| Atlas rows | 0 intact, 1 impact flash/crack, 2 shattered shards, 3 empty socket + snapped chain |
| Atlas column 7 | engraved Ram emblem |

The seven intact cells are deliberately different sprites (brace orientation
and per-plate ember marks), so the row does not read as one copied stamp.

### Accepted-collision sequence

| Time (ordinary plate) | Stage |
|---|---|
| 0 ms | local metal slam; plate flashes and cracks (variant 1) |
| 120 ms | plate crack sound; fracture grows (variant 2) |
| 350 ms | chain snap/fall sound; socket empties and drops up to 7 px (variant 3) |
| 500 ms | socket settles; the chain stays visibly snapped |

The final three plates use a slower, heavier sequence: 170 ms crack,
470 ms shatter, 700 ms drop up to 9 px, 150 ms stagger, lower-pitched sounds,
and one 900 ms release band/ring confined to the bar when the seventh impact
lands. Reduced motion jumps straight to the empty-socket state and skips the
release, flashes and heartbeat.

All sounds are local (`level.playLocalSound`) with vanilla `ANVIL_LAND` /
`ANVIL_BREAK`; they are never broadcast to the server, so twenty viewers do not
create twenty global sound events. The `plateSoundStage` guard makes each stage
play exactly once per accepted collision.

### Lifecycle

- A late join, reconnect or resource reload reconstructs the committed plate
  pose from the snapshot and sets all stages to "already played" without
  replaying historic sounds.
- When no Ram visual is present (wave ended, generation rotated, entity gone),
  the per-viewer state is reset.
- Logout and world change call `RiftRamBossBarHud.clearState()` from
  `CopiMineClient`.
- The counter reads `ПЕЧАТИ n/7`; the HP fill turns from purple to hot red once
  all seven plates are gone. The plates never replace or fake HP.

## 3. C2 — Rift Guardian: phase-aware presentation

The authored v3 mouth frame and the real 128 × 128 head portrait (rendered
from the supplied 37-cube model UV) are preserved. Phase identity is added
around them instead of repainting the creature:

| Layer | Asset | Contract |
|---|---|---|
| Phase metal/obsidian/cracks | `end_rift_guardian_phase_overlay.png`, 360 × 840 | six cells of 360 × 140, one per phase; the HP recess stays transparent |
| Portrait aura | `end_rift_guardian_portrait_aura.png`, 384 × 64 | six 64 × 64 auras; centre transparent so the head stays the identity |
| State glyphs | `end_rift_guardian_state_glyphs.png`, 96 × 16 | six 16 × 16 silhouettes, distinguishable by shape, not only hue |

The six canonical thresholds and labels are unchanged:

| HP | Phase | Label | Glyph |
|---|---|---|---|
| 100–80 | AWAKENING | ПРОБУЖДЕНИЕ | 0 |
| 80–60 | HUNT | ОХОТА | 1 |
| 60–45 | RIFT | РАЗЛОМ | 2 |
| 45–30 | OVERLOAD | ПЕРЕГРУЗКА | 3 |
| 30–20 | RAGE | ЯРОСТЬ | 4 |
| 20–0 | LAST_SEAL | ПОСЛЕДНЯЯ ПЕЧАТЬ | 5 |

Motion, all wall-clock driven and FPS independent:

- interpolated trailing damage chip (700 ms) over the real fill;
- one phase-transition sweep (900 ms) triggered **only** by a change of the
  server phase id, never by render-call count; a late join starts stateful with
  no replay;
- low-HP heartbeat at ≤ 25 %;
- five restrained End-energy veins drifting along the frame (4200 ms period)
  that never cross the HP recess;
- a breathing glyph backing in RAGE, LAST_SEAL or an executing cast;
- an optional one-pixel portrait bob, disabled by reduced motion.

Precedence: frame (drawn under the dynamic layers) → fill/chip/notches/markers
→ transition sweep → frame front → phase overlay → veins → portrait → aura →
labels. The frame's recess is transparent (the alpha test requires > 99.5 % of
the recess to be transparent), so no layer hides the health readout.

Honest semantics: the current `BossDamagePolicy` never reduces or blocks normal
phase damage (`multiplier = 1.0` except explicit lifecycle immunity). The HUD
therefore shows real HP only and deliberately does **not** invent a
"УЯЗВИМ"/blocked badge that the server has not sent. If the owner wants an
explicit blocked-state badge for the cocoon, it needs a new server→client field
and is recorded as work not done here.

## 4. C3 — Wave 7: four distinct miniboss bars

Four frames and four portraits were redrawn as one family with distinct
identities. The v2 files are named `wave7v2_*` so the historical `wave7_*`
artifact contract cannot silently mask a regression.

| Style | Identity | Accent |
|---|---|---|
| `bone` | skeleton marksman, bone/quiver, cracked violet marrow | `#9A62FF` |
| `sculk` | Warden, asymmetric sculk sensor ring, cyan/black ribs | `#37E0D0` |
| `rune` | Archmage, runic stone ring and deliberate glow | `#F34CDB` |
| `mirror` | personal Echo, fractured muted mirror | `#BFE9FF` |

Each 256 × 32 frame keeps its track recess transparent, so the coloured,
ten-segment fill is drawn **after** the plate and is fully visible at 100, 50,
25, 1 and 0 %. Portraits are 128 × 128 and at least 12 % painted. Title
ownership is unchanged: the Warden remains `Хранитель Бездны`, never
`Хранитель Разлома`.

Per-bar visual states added in the renderer: trailing damage chip, 170 ms hit
flash, 620 ms critical pulse, and a 1100 ms `ПЕЧАТЬ ПАЛА` defeat hold. State is
keyed by `BossEvent#getId()`, the real bar identity, so two marksmen alive at the
same time (or publishing the same title during a joint volley) cannot share a
chip, flash or defeat state. Reduced motion removes the hit flash and the
critical pulse; the static defeat label remains. Logout and world change call
`Wave7MinibossBossBarHud.clearState()`.

Concurrent marksmen are owned by the server's per-entity bar policy; the client
paints each bar where the vanilla stack places it. No client-side stacking
logic was reintroduced.

## 5. C4 — icon-set audit

- The Ram's seven-plate readout and `ПЕЧАТИ n/7` counter are new.
- The Guardian's six state glyphs form a single silhouette-first set used by
  both the phase label and the atlas.
- Wave 7 keeps one authored portrait per identity and no generic icon.
- The existing ability row was audited, not extended: none of the retired Ritual
  Sphere powers (five mages, prisoner, walls, separate Wave 7 chambers) were
  restored to fill the row. The server still owns cooldowns and readiness.
- All three HUDs clear their per-viewer state on logout, world change and boss
  disappearance.

No new screens were added that could overlap chat, hotbar or debug text; the
bars use the vanilla boss-bar anchor and width-safe scaling.

## 6. C5 — 3D effects: what is real and what is not

Already real in source and covered by `tests/test_end_rift_c5_effects_contract.py`:

- phase-3 geyser uses native Paper `GEYSER_PLUME` / `GEYSER_BASE` in a finite
  burst (5 + 24 components), not a thousand `FLAME`/`LAVA` points;
- the warned footprint, 8–12 block visible column, 1.9 block radius, 7 damage
  and reversible world restoration are pinned;
- inferno core/crown and Kagune splinter/final seal use authored geometry JSON,
  not recoloured vanilla projectiles;
- Wave 6 bone rain is a real gravity-driven second attack with a distinct
  triple-shot fan, a physical fall and a post-fall AI.

Not done in this increment: a new authored volumetric geyser vortex model,
new Kagune rig/emergence animation, and a unique tower-skeleton model. They are
larger 3D/rig work than the HUD files and were not faked. Echo live behaviour,
five decoys, Last Seal grab, geyser/inferno spectacle, skeleton combat identity
and all animation timing still require client F2 evidence.

## 7. Editable sources and compiled PNGs

Everything shipped is produced by
`scripts/minecraft/GenerateEndRiftHudRedesign.py` (Pillow, deterministic seeds,
no network). Running it rewrites both the shipped GUI textures and the archived
direction previews. A test rebuilds every file into a temporary directory and
requires byte identity.

| Shipped PNG | Size (B) | SHA-256 |
|---|---|---|
| `ram_seal_rail.png` | 974 | `78a32432fbc62d96c2d42393f81209f9d7fd318defc3179b254ee0126405cddb` |
| `ram_seal_plates.png` | 3583 | `62ade07f323db4946fb995b5b146ed80f5fe37d952de7be42b29d8d443829ba1` |
| `end_rift_guardian_phase_overlay.png` | 7301 | `8d446e5c82369c44195357fae7af0b47a546829ab2129adaed8e8223c67896f1` |
| `end_rift_guardian_portrait_aura.png` | 1129 | `23a86f75e9100b4f665d8412937a506cfef3279460db2ab18302bcdeebb98285` |
| `end_rift_guardian_state_glyphs.png` | 515 | `1b8161b8574061f509cc1b5b9c8ee76db5dbea556fa448e55f4ccde42c05937b` |
| `wave7v2_bossbar_bone.png` | 599 | `9b556f753bfa10e6cb471f31b3743c1b3e7b79de4c5f712e1dd6229994cd4e55` |
| `wave7v2_bossbar_sculk.png` | 635 | `0bdff647e78b6caace10b2b1375c0169f1566ec4aa9b6482a2a1989a6cc031f6` |
| `wave7v2_bossbar_rune.png` | 711 | `856a404670c2e2aa377d71609106c786917266f167c4a9c30c587af9f9cc133b` |
| `wave7v2_bossbar_mirror.png` | 546 | `41c6adefa79ea0e8a1ce51dfd231a9a0151af50deaaf6b139576c9563362af25` |
| `wave7v2_portrait_marksman.png` | 880 | `06e69b1efcb7adfeea7ad199e5a5e36a8d88a742f4136b4a5a2dd1a0cb799e14` |
| `wave7v2_portrait_warden.png` | 995 | `4f986415a0db9bb81c83fed141c1edf063d2a82e9308fef3418e28ce44c8c659` |
| `wave7v2_portrait_archmage.png` | 1491 | `054ccc13c3ded757dee43a8e2ef4cef53cc71b55b028598abb974e2a776a3d13` |
| `wave7v2_portrait_echo.png` | 939 | `b747e06ea59bd410b183ed1bd5f554f75d06648994e789572633b824d71ee28e` |

Archived: 24 PNG previews (12 `_a`/`_b` pairs) plus
`art-directions/README.md`.

## 8. Automated evidence

| Claim | Status |
|---|---|
| Every shipped and archived PNG is reproducible from the generator | AUTOMATED-TESTED (`tests/test_end_rift_hud_redesign_assets.py::test_the_generator_reproduces_every_shipped_and_archived_png`) |
| Two material directions exist for every major bar | AUTOMATED-TESTED (`test_two_visual_directions_are_archived_for_every_major_bar`) |
| Seven plates × four distinct break stages, plus emblem | AUTOMATED-TESTED (`test_ram_rail_and_seven_plate_atlas_are_real_art`) |
| Plates advance only from an accepted count; duplicates/stale ignored; sounds local | AUTOMATED-TESTED (`test_ram_hud_drives_plates_only_from_accepted_impact_count`) |
| Final-tower escalation and one release payoff | SOURCE-VERIFIED + AUTOMATED-TESTED source guard |
| Wave 7 frames show the real fill at 100/50/25/1/0 % | AUTOMATED-TESTED (`test_wave7_frames_show_the_real_fill_at_every_health_level`) |
| Four Wave 7 frames/portraits are redrawn and distinct | AUTOMATED-TESTED |
| Six Guardian overlays/auras/glyphs are distinct and never cover HP | AUTOMATED-TESTED (`test_guardian_six_phase_overlays_never_cover_the_health_track`) |
| Thresholds/labels are unchanged and state is added | AUTOMATED-TESTED |
| Reduced motion is persisted and honoured | AUTOMATED-TESTED |
| Per-viewer state clears on logout and world change | AUTOMATED-TESTED (`test_per_viewer_animation_state_is_cleared_on_logout_and_world_change`) |
| C5 native geyser, warned footprint, authored meshes, bone rain | AUTOMATED-TESTED (`tests/test_end_rift_c5_effects_contract.py`) |
| The client compiles | AUTOMATED-TESTED: Gradle `compileJava`, `BUILD SUCCESSFUL` |
| A player sees the chained bar break, the Guardian sweep and the Wave 7 fill | **NOT VERIFIED** — needs a real client and F2 screenshots |
| Animation is smooth at low and high FPS, and the sound mix is pleasant | **NOT VERIFIED** — needs a live client and performance capture |
| Geyser/inferno/Kagune/skeleton look right in 3D | **NOT VERIFIED** — no new rig/model was made in this increment |

## 9. Owner playtest route

1. Fresh client from the hash-pinned URL; confirm the mod version in the log.
2. `/cmend begin` with two accounts, one at 30 FPS and one uncapped.
3. Ram: reach collision count 0 → 7 with F2 at every increment; capture the
   final three hits in slow motion; disconnect/reconnect mid-break and confirm
   the pose is right with no replayed sound; send one duplicate impact (test
   harness) and confirm no second break.
4. Guardian: F2 at each of the six bands, during a phase transition, at ≤ 10 %
   HP, and after a reconnect. Compare the glyphs in grayscale.
5. Wave 7: F2 of all four bars at full/near-death HP, two marksmen at once, a
   miniboss death, and a stale-bar check after the wave ends.
6. Phase 3: F2 of one warned footprint and the eruption from four directions.
7. Phase 4: F2 of the inferno with a safe cell in view.
8. Wave 6: F2 of the tower skeleton's triple shot, bone rain and physical fall.