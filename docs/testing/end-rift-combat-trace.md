# End Rift combat-trace finding

## Scope

This finding was produced against the local-only Paper runtime in the End Rift
worktree. It neither connects to nor modifies a production service.

## Reproduction

`CombatTraceRecord` records the damage event at `LOWEST`, its final state at
`MONITOR`, and the affected living entity's health on the next server tick. A
record contains server tick, attacker and victim UUIDs, damage cause, raw and
final damage, cancellation before and after listeners, no-damage state,
`lastDamage`, health before and after, phase, cast/shield state, and MSPT.

The pre-repair trace showed that ordinary Paper hurt-resistance was accepting a
new player hit at the event boundary while applying only the difference from an
earlier, stronger hit. For example, the event reported final damage `2.440`,
while the next-tick health delta was `0.358`. This is native `lastDamage` /
no-damage-window coalescing, not a lost packet, a player identity failure, or
an event cancellation. It made rapid attacks against owned wave mobs look as
though their damage intermittently stopped.

## Repair

For an owned current-attempt wave mob, a direct player attack or a player-owned
projectile is now applied exactly once through
`EventRealHealthDamagePolicy`: the already-calculated Bukkit final damage is
subtracted from the entity's real Bukkit health, clamped at zero, and the
original damage event is cancelled. Earlier cancellation remains authoritative:
the handler uses `ignoreCancelled = true` and does not revive any rejected hit.

The repair deliberately does **not** change `noDamageTicks`, `lastDamage`,
hit timing, target rules, shield rules, or protection cancellation. It avoids
the native coalescing path only for event-owned wave mobs, where a valid player
hit has the explicit event invariant of being applied exactly once.

Boss combat is covered separately by the V2 real-health path. The official
boss uses the entity's real `Health` and `MAX_HEALTH`; the old virtual-health
probe remains only as a disposable compatibility regression and is not a
hidden fallback for the official flow.

## Evidence

On the repaired local runtime:

- `RunEndRiftCombatTraceLive.ps1` reported `wave_traces=484`, including
  `player_wave=100` and `exact_wave=97`; the remaining records are lethal or
  intentionally bounded by remaining real health. The probe also recorded
  `boss_traces=2` from independent packet-driven boss hits.
- `RunEndRiftMobCombatLive.ps1` reported `moved=4498`, `attacks=48`,
  `player_hurt=67` and `player_damage_applied=96`, proving that the repaired
  mobs move and apply real damage instead of merely invoking an event handler.
- `RunEndRiftBossMultiPlayerDamageLive.ps1` reported 144 events from two
  independent attackers, including 14 same-tick groups; its legacy disposable
  virtual-health checkpoint changed from `5000` to `4415.31206673384` by the
  summed final damage. This is retained for regression arithmetic only.
- `RunEndRiftBossRealHealthLive.ps1` reported a V2 boss with physical
  `5000/5000`, an unclamped attribute and no legacy virtual-health marker.
- The live probes completed cleanup with no event boss or wave mobs remaining;
  the official scenarios additionally verified the V2 stages and victory
  path.
