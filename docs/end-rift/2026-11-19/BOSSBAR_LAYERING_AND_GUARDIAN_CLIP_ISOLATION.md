# Wave 7 bar layering and Guardian clip isolation — client repin and production publish

> Superseded on 2026-11-21 by `docs/end-rift/2026-11-21/HUD_REDESIGN.md`.
> This record stays as written for the 2026-11-19 artifact.

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`), Wave 7 miniboss HUD and
Rift Guardian model state.
Supersedes the client pin in
`docs/end-rift/2026-11-19/PRODUCTION_DEPLOYMENT.md`; that record stays as
written, this file carries the current client artifact.

Status vocabulary: SOURCE-VERIFIED / AUTOMATED-TESTED / LIVE-PAPER-VERIFIED /
VISUAL-VERIFIED / PERFORMANCE-VERIFIED / DEPLOYED / NOT VERIFIED.

## 1. Defects

### 1.1 The Wave 7 miniboss bar hid its own health fill

`Wave7MinibossBossBarHud.render` blitted the authored frame **after** the
track, the health fill, the segments and the notches. The shipped
`wave7_bossbar_*.png` art is fully opaque in the bar interior
(`x[14..242) y[12..20)`, 1824/1824 pixels with alpha 255), so the frame
covered every pixel of the readout. In game the bar showed artwork and a title
but no health at all.

Repair: blit the frame first as the plate, then the track, the fill, the
segments, the notches and finally the portrait and title.

### 1.2 A full Guardian clip map reset every live body mid-blend

`RiftGuardianModel.clipFor` kept a bounded `Map<String, ClipState>` but, when
the bound was reached, called `clips.clear()`. One more distinct body key
therefore reset every live Guardian and decoy to the identity pose, snapping
animations that were mid-blend.

Repair: the map is an access-ordered `LinkedHashMap<>(16, 0.75F, true)`; when
the bound is reached exactly one key is removed — the coldest — and the key
currently being posed (`activeClipKey`) is never chosen as the victim.

## 2. Evidence

| Claim | Status |
|---|---|
| The frame is blitted before the health fill, once, and the portrait stays above both | AUTOMATED-TESTED (`tests/test_wave7_miniboss_bossbar.py::test_the_frame_is_drawn_before_the_health_fill`) |
| The committed bar art is byte-identical to the real generator output | AUTOMATED-TESTED (`tests/test_wave7_miniboss_bossbar.py::test_the_generator_reproduces_the_committed_art`) |
| The clip map evicts only the coldest non-active body and keeps the active clip | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py`) |
| The client compiles and all client unit tests pass on Temurin 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle --offline clean test jar`, `BUILD SUCCESSFUL` |
| A second clean build produces the same size and SHA-512 | AUTOMATED-TESTED: two clean `gradle clean test jar` runs agree byte for byte |
| The End Rift local release gate passes without `-SkipBuilds` | AUTOMATED-TESTED: `tests/RunEndRiftEventChecks.ps1`, exit 0, `End Rift current local checks passed.` |
| The whole repository suite passes on this tree | AUTOMATED-TESTED: `python -m pytest -q`, `3711 passed, 23 skipped, 0 failed` in 767.27 s |
| A player sees the health fill in the live Wave 7 bar | NOT VERIFIED — needs a real client in the encounter |
| No Guardian animation visibly snaps when many decoys are present | NOT VERIFIED — needs a real client |

## 3. Re-pin

Only two client render classes and their tests moved; no
`copimine-end-event` source changed.

* previous: `30252288` bytes,
  `3205690d5482a373b33e7034337ddf16647517860130afea72816349b0f20cc23a0c4b9bb55f2b5b5b4929e37f09882e1d32581086ad0dd88cf2fdd912182097`;
* current: `30252497` bytes,
  `379aebb2c529fec01e6ca012b88532098762386babcde2a990a8f0a31bf97a88bbe48c84153eedbea7c81abbd8decc390cca536edd4a48a120dcafde40f8ac7b`;
* current SHA-256: `0ef922d0f81c2be40a454744af40115d01dbae290833751b641a566c4f0f436b`;
* current SHA-1: `001d350faf4c3a75e8112646afc0ca99e7ebc4b5`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.
The deployed production plugin already matches it, so no plugin redeploy and no
server restart were required for this client-only change.

## 4. Publication

The immutable client asset is published at:

`https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-0ef922d0f81c2be4.jar`

Verified over HTTPS: `30252497` bytes, SHA-256
`0ef922d0f81c2be40a454744af40115d01dbae290833751b641a566c4f0f436b`,
SHA-512
`379aebb2c529fec01e6ca012b88532098762386babcde2a990a8f0a31bf97a88bbe48c84153eedbea7c81abbd8decc390cca536edd4a48a120dcafde40f8ac7b`.

The local Prism instance `Copimine` was updated in `minecraft/mods`; the
migration receipt and `TLauncherAdditional.json` carry the new SHA-512, SHA-1,
size and path. Old files are in
`copimine-mod-backups/20261119-client-379aebb2`.

Rollback: restore the previous client from that backup, or republish the
previous immutable asset
`https://copimine.ru/assets/CopiMineClient-0.1.4+26.3-fbb3e3e5dad74fa4.jar`
(`30252288` bytes, SHA-256 `fbb3e3e5dad74fa4e10a493e0f91fa7b76d25f78beed64cee76be5dcc361780a`).
The server runtime pin `client_bridge.required_client_mod_version` stays
`0.1.4+26.3` in both directions.

## 5. Boundaries

Deployment status: **PRODUCTION-PUBLISHED, STARTUP-OBSERVED**. The installed
byte identity was verified at the origin and over HTTPS; the production service
is `active`, `cmend debug invariants` reports `failures=0`, and RCON reports
`0/50` players. No live Wave 7 encounter, no real Guardian/decoy render pass,
no client FPS measurement and no multi-player load test were performed.
