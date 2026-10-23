# Sound Cue Matrix — End Rift Waves 1–7

Source: `docs/end-rift/final-26.3/SOUND_CUE_MATRIX.json` at `d44c8f51be00f3b6d911135f7722a155cbf20c13`.

All gameplay cues are server-emitted vanilla `Sound` enum values. The matrix deliberately adds no unlicensed music and no duplicate client playback. `nativeAudioVerified` remains **false**: the table below proves source wiring and limits, not audible quality.

## Requirements

| ID | Scope | Status |
|---|---|---|
| SND-01 | Known key/category/volume/pitch/fallback | PASS (static contract) |
| SND-02 | Warning before commit; no success on cancel | PASS (static contract) |
| SND-03 | One server cue per event, dedup, no client duplicate | PASS (static contract) |
| SND-04 | Marked/prisoner/room/eligible audience | PASS (static contract) |
| SND-05 | Real source position and event audience radius | PASS (static contract) |
| SND-06 | Cooldown, dedup, bounded cues, telegraph priority | PASS (static contract) |
| SND-07 | No owned cue loop after death/quit/world/wave/reset | PASS (static contract) |
| SND-08 | User category/volume settings preserved | PASS (static contract) |
| SND-09 | Distinct cues for Waves 1–7, both anvil volleys, four Wave 7 trials | PASS (static contract) |
| SND-10 | Real game audio recording | BLOCKED — no authenticated native capture |

Automated check: `tests/test_end_rift_sound_cue_matrix.py` (4 tests).

## Cue table

| Cue | Wave / trial | Event | Stage | Sound | Audience | Volume / pitch | Cooldown |
|---|---|---|---|---|---|---|---:|
| W1-CARRIER-SELECTED | 1 | carrier-selected | completion | BLOCK_AMETHYST_BLOCK_RESONATE | event audience | 0.55 / 1.25 | 20 ticks |
| W1-CARRIER-DROP | 1 | carrier-drop | failure | BLOCK_AMETHYST_BLOCK_BREAK | event audience | 0.55 / 1.25 | 20 ticks |
| W2-MARK-START | 2 | mark-start | telegraph | ENTITY_ENDERMAN_STARE | marked player | 0.80 / 1.35 | one-shot |
| W2-HUNT-COMPLETE | 2 | hunt-complete | completion | BLOCK_BEACON_ACTIVATE | event audience | 0.55 / 1.25 | 20 ticks |
| W3-PORTAL-OPEN | 3 | portal-open | telegraph | BLOCK_AMETHYST_BLOCK_RESONATE | event audience | 0.55 / 1.25 | 20 ticks |
| W3-PORTAL-CLOSE | 3 | portal-close | completion | BLOCK_RESPAWN_ANCHOR_DEPLETE | event audience | 0.55 / 0.80 | 20 ticks |
| W4-OBELISK-HIT | 4 | obelisk-hit | impact | BLOCK_AMETHYST_BLOCK_BREAK | event audience | 0.55 / 1.25 | 20 ticks |
| W5-FOG-ARRIVAL | 5 | fog-arrival | telegraph | BLOCK_RESPAWN_ANCHOR_DEPLETE | event audience | 0.55 / 0.55 | 20 ticks |
| W5-FOG-SAFE-ZONE | 5 | fog-safe-zone | ambient | BLOCK_CONDUIT_AMBIENT_SHORT | sheltered player | 0.25 / 1.35 | 40 ticks |
| W6-CASTER-DEATH | 6 | caster-death | completion | BLOCK_AMETHYST_BLOCK_CHIME | event audience | 0.80 / 0.77 | one-shot |
| W6-SHIELD-HIT | 6 | caster-shield-hit | failure | BLOCK_METAL_HIT | event audience | 0.75 / 1.35 | one-shot |
| W6-SHIELD-BREAK | 6 | caster-shield-break | completion | BLOCK_GLASS_BREAK | event audience | 0.80 / 1.35 | one-shot |
| W6-GUARD-ABILITY | 6 | guard-ability | telegraph | BLOCK_BEACON_POWER_SELECT | event audience | 0.65 / 1.15 | one-shot |
| W6-ANCHOR-WARNING | 6 | gravity-anchor-warning | telegraph | BLOCK_BEACON_AMBIENT | event audience | 0.80 / 0.70 | one-shot |
| W6-ANCHOR-IMPACT | 6 | gravity-anchor-impact | impact | ENTITY_GENERIC_EXPLODE | event audience | 0.70 / 1.15 | one-shot |
| W6-ANVIL-WARNING | 6 | anvil-volley-warning | telegraph | BLOCK_ANVIL_PLACE | event audience | 0.70 / 0.75 | one-shot |
| W6-ANVIL-WARNING-2 | 6 | anvil-volley-warning-second | telegraph | BLOCK_ANVIL_PLACE | event audience | 0.70 / 1.05 | one-shot |
| W6-ANVIL-VOLLEY-1-IMPACT | 6 | anvil-volley-first-impact | impact | BLOCK_ANVIL_LAND | event audience | 0.70 / 0.95 | one-shot |
| W6-ANVIL-VOLLEY-2-IMPACT | 6 | anvil-volley-second-impact | impact | BLOCK_ANVIL_LAND | event audience | 0.70 / 0.95 | one-shot |
| W6-SPELL-TELEGRAPH | 6 | major-spell-telegraph | telegraph | BLOCK_RESPAWN_ANCHOR_CHARGE | event audience | 0.80 / 0.70 | one-shot |
| W6-SPELL-ACTIVE | 6 | major-spell-active | ambient | BLOCK_CHAIN_HIT | event audience | 0.55 / 1.15 | 13 ticks |
| W6-SOUL-BRAND | 6 | soul-brand | telegraph | BLOCK_AMETHYST_BLOCK_CHIME | brand target | 0.65 / 1.40 | 20 ticks |
| W6-RELEASE | 6 | prisoner-release | completion | BLOCK_RESPAWN_ANCHOR_DEPLETE | event audience | 0.80 / 1.25 | one-shot |
| W7-WARDEN-WARNING | 7 / Warden | warden-warning | telegraph | ENTITY_WARDEN_SONIC_CHARGE | trial target | 0.75 / 0.65 | one-shot |
| W7-ECHO-TELEPORT | 7 / Echo | echo-reposition | telegraph | ENTITY_ENDERMAN_TELEPORT | echo target | 0.65 / 1.40 | one-shot |
| W7-MARKSMAN-WARNING | 7 / Marksman | marksman-warning | telegraph | BLOCK_BEACON_POWER_SELECT | trial target | 0.70 / 0.80 | one-shot |
| W7-ARCHMAGE-WARNING | 7 / Archmage | archmage-warning | telegraph | ENTITY_EVOKER_PREPARE_ATTACK | trial target | 0.70 / 0.80 | one-shot |
| W7-TRIAL-COMPLETE | 7 | trial-complete | completion | BLOCK_BEACON_ACTIVATE | room owners | 0.70 / 1.20 | one-shot |
| BOSS-HIT-UNKNOWN-FALLBACK | 7 | unknown-boss-cue | impact | BLOCK_RESPAWN_ANCHOR_CHARGE | event audience | 0.80 / 1.00 | one-shot |

## Manual audio checklist

- [ ] Wave start, end, refusal and interruption are distinguishable in a real client.
- [ ] Mark start is audible only to the marked player; no hidden-ally sound leak.
- [ ] Portal open/capture/close and fog warning/onset/safe-zone/end are readable.
- [ ] Obelisk accepted reflection and rejected hit do not sound like success.
- [ ] Gravity Anchor warning precedes explosion by the full readable window.
- [ ] Both Heavenly Judgment volleys have distinct warning and impact cues.
- [ ] Caster death/unlock and player Q/W/E/R valid/invalid feedback are distinct.
- [ ] All four Wave 7 trials have recognizable telegraphs; room win is audible.
- [ ] No music/loop continues after death, quit, world change, reset or restart.
- [ ] Client category/volume settings are respected; no forced global volume.
- [ ] Capture a real F2/video+audio clip and update `nativeAudioVerified` only after review.
