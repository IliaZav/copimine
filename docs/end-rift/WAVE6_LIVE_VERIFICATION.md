# Wave 6 live verification — 2026-11-09

Server: Paper 26.3 local runtime, port 25566, RCON 25576, arena `Arena`
Player observed: `ArenaBot` (survival)
Plugin on disk: CopiMineEndEvent.jar 1 992 711 B, sha256
`732e26554a0c06ca770bbe1a83d88b6f16aab26e57ace3122f1ec51cb3353d46`
(built and deployed from this worktree, hashes identical)

## VERIFIED IN MINECRAFT

`cmend wave spawn 6` on a live client produced:

- `owned=12`: one Ravager plus 11 temporary displays.
- `temporaryDisplays=11`: the five armour sections, the active pillar and
  the encounter's other display layers.
- `ownedLiving=1`, `mobile=1`, `aiEnabled=1`: the Ravager is alive, its AI
  is running and it moves between samples (10,100,0 -> 9.27,100,3.40 ->
  11,100,1), so it is chasing rather than frozen on its spawn tick.
- Eight `block_display` entities resolved to real armour positions at
  y = 100.0 .. 103.3 around the Ravager, not stacked at the spawn point.
- Melee landed on the player repeatedly:
  `WAVE_MOB_DAMAGE attacker=RAVAGER:... wave=6 victim=ArenaBot
  cause=ENTITY_ATTACK`. The `final=0.0` values in that run are the
  adventure/creative Artefact, not a plugin defect: the same attacks in
  survival are the ones the gate below multiplies.
- `tps=20.00/20.00/20.00`, `mspt=0.53-0.64` with the encounter live.

## VERIFIED IN CODE (not yet photographed at each of the six stages)

- `RiftRamVisualPolicy` declares exactly six visual ids,
  `END_RIFT_RAM_ARMOR0_V1` .. `END_RIFT_RAM_ARMOR5_V1`, and
  `visualForArmorStage(int)` clamps 0..5 (letter: 8.4 "SIX armor states").
- The server publishes `"END_RIFT_RAM_ARMOR" + riftRam.impacts() + "_V1"`,
  so the armour stage IS the accepted pillar-impact count.
- `RiftRamPolicy.damageMultiplier` returns 0.20 / 0.35 / 0.50 / 0.65 /
  0.80 / 1.00 for 0..5 impacts — the table in 8.5 verbatim.
- `onRiftRamArmorDamage` applies that multiplier, carries the
  `BLOCK_ANVIL_HIT` metallic cue plus a CRIT spark burst on every armoured
  hit, and clamps damage so the Ravager cannot die before the fifth pillar.
- `RiftRamPolicy.healthFloor` is a FLOOR, not healing: it returns 0 once
  five impacts are accepted, so the exposed Ravager can be killed.
- `RiftRamEncounter.impact()` requires an ACTIVE charge, refuses a second
  impact from one charge (`banked`), refuses any impact past five, removes
  exactly one armour section (`p.section()==impacts`), releases
  `3 + impacts % 3` armour shards and `2 + impacts % 3` Endermites under
  the encounter-wide cap, then stuns.
- `updateDisplays()` animates the next pillar rising over 20 ticks.
- `nextPillarTick = tick + 35` keeps only one pillar alive at a time and
  refuses a pillar after the final impact
  (`data.put` guard `pillar-after-final-impact`).

## NOT VERIFIED

- Each of the six armour stages photographed in sequence.
- A charge actually reaching and breaking a pillar in live play.
- The seismic stomp observed as particles in a real client.
- Anything requiring more than one player (2/5/10/11/20 scaling).
