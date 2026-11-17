# Unwired policy triage (2026-11-17)

Every top-level class under `copimine-end-event/src` was scanned for cross-file
references. Nine domain policies are referenced only inside their own file, i.e.
nothing in the running plugin calls them. This document records, for each one,
whether it is **superseded by a wired implementation** or a **genuine gap**, with
the evidence that was actually inspected.

Classification vocabulary follows the master contract: `IMPLEMENTED` (code exists
and is reachable), `AUTOMATED-TESTED` (behavioural test executed), `SUPERSEDED`
(a newer wired owner performs the contract's job), `DEAD` (the mechanic was
removed from the product).

## Summary

| Policy | Class | Evidence |
|---|---|---|
| `LimboDummyPolicy` | **DEAD** | Wave 7 Limbo removed entirely by `ea0d77f4` |
| `BlackFogCurtainPolicy` | **SUPERSEDED** | Client fog mixin + `END_FOG_STATE`/`WAVE5_FOG_V1` |
| `ChamberScalingPolicy` | **SUPERSEDED** | `RitualSphereScalingPolicy.forPlayers` |
| `EventCombatScalingPolicy` | **SUPERSEDED** | `PressureBudgetController` |
| `EventMobDamagePolicy` | **SUPERSEDED** | `GENERIC_ATTACK_DAMAGE` attribute authoring |
| `TargetPressurePolicy` | **SUPERSEDED** | `WaveCombatCoordinator.planSquads` |
| `BlackFogDamagePolicy` | **SUPERSEDED** | Native owned Wither/Slowness leases |
| `CarrierSlownessLeasePolicy` | **SUPERSEDED** | Native `wave1_carrier_slowness` modifier |
| `KaguneRingCastPolicy` | **SUPERSEDED** | `LastSealRingRuntime` per-cast hit ledger |

No policy in this list represents an unimplemented contract requirement. Each is
either a retired mechanic or a pure-function duplicate of a wired owner. They are
left in place (with their historical tests) rather than deleted, matching the
repository rule that historical and superseded source stays for traceability.
`SmallKagunePolicy` is a documented shim that explicitly delegates to the wired
`SmallKaguneSpawnPolicy`, and its javadoc already states `SUPERSEDED`.

## Evidence detail

### LimboDummyPolicy — DEAD

Commit `ea0d77f4` ("End Rift: playtest repair pass") removed the Limbo mirror
from Wave 7 as a user-reported defect. `git log -S 'test limbo'` shows that
command disappearing in `ea0d77f4`. The current command surface for
`/cmend test` is `run, wave, scene, boss, teleport, visuals, music, diagnostics`,
so there is no rehearsal command left to attach the policy to. Keeping the policy
would resurrect a mechanic the user rejected.

### BlackFogCurtainPolicy — SUPERSEDED

The class built a server-side 96 x 8 particle perimeter. It was wired in
`7318a0ef` and removed in `17f0c3a2`. Wave 5 black fog is now a client fog pass:
`EndEventBlackFogManager` consumes the `END_FOG_STATE` payload with visual id
`WAVE5_FOG_V1`, and `EndEventBlackFogMixin` rewrites `BackgroundRenderer` fog
start/end/colour. The server sends that packet in
`sendCurrentBlackFogPacket(...)` and no longer draws a particle wall. The
contract explicitly wants volumetric fog that the old particle ring could not
produce.

### ChamberScalingPolicy — SUPERSEDED

Wave 6 scaling is owned by `RitualSphereScalingPolicy`. It is called from six
sites in the plugin (`forPlayers(...)`, `GUARDS_PER_CASTER`, `pressureMobsForPlayers`),
including the guard-slot ladder and per-volley projectile counts.
`ChamberScalingPolicy` duplicates a subset of the same roster maths and has no
caller.

### EventCombatScalingPolicy — SUPERSEDED

Bounded simultaneous pressure is owned by `PressureBudgetController`
(15 references), which publishes `MIN_PLAYERS = 2`, `MAX_PLAYERS = 20` and
`HARD_CAP = 56` and is consumed through `profileForPlayers`. The retired policy
declared the same bounds but nothing read them.

### EventMobDamagePolicy — SUPERSEDED

Never wired: `git log -S` finds no commit that referenced it from
`CopiMineEndEvent.java`. Ordinary and elite event mobs get their damage from the
`GENERIC_ATTACK_DAMAGE` attribute at authoring time (11 sites), so a separate
one-pass "reduce by 4, floor at 1" adjustment has no caller and no contract
obligation: the master contract has no event-mob flat damage-reduction rule.

### TargetPressurePolicy — SUPERSEDED

Least-loaded target assignment is owned by `WaveCombatCoordinator`. The plugin
calls `planSquads(...)` in the wave-tick planning pass; the coordinator keeps
`MAX_MELEE_ATTACKERS_PER_PLAYER = 2` and distributes fighters by current load.
`TargetPressurePolicy.distribute(...)` implements the same least-loaded idea but
is never invoked.

### BlackFogDamagePolicy — SUPERSEDED

Wave 5 exposure is delivered as a native owned effect, not a direct damage call.
`applyOwnedBlackFogEffect(player, PotionEffectType.WITHER, witherAmplifier)` runs
with amplifier 2 for 11+ players and 1 otherwise, and the source states
explicitly "Only native owned Wither causes exposure damage: no second
direct-hit path." `BlackFogEffectLeasePolicy` owns the lease and release of those
Wither/Slowness effects. A second direct-damage path capped at 2 HP is therefore
both unused and contrary to the single-owner rule.

### CarrierSlownessLeasePolicy — SUPERSEDED

Wave 1 carrier Slowness is a native attribute modifier named
`wave1_carrier_slowness`, reconciled by `updateCurrentCarrierSlowness(...)` and
using `CARRIER_SLOWNESS_AMPLIFIER = 1` (Slowness II), which is the user-approved
level. The retired policy modelled a `-0.30` movement-speed multiplier, a
mechanism the product does not use.

### KaguneRingCastPolicy — SUPERSEDED

The once-per-cast damage claim for the three-row Kagune ring lives in
`LastSealRingRuntime.hitPlayers`. The runtime adds a player on first accepted
contact and refuses further contacts for that player for the rest of the cast,
and removes the entry again if the damage call is refused, so a lethal or absent
player never consumes the hit. That is exactly the ledger contract 20.3 requires,
and it is wired through the ring host. `KaguneRingCastPolicy` is a second copy of
the same idea that only a comment mentions.

## Verification performed

- `git grep`/cross-file reference scan over all plugin sources.
- `tests/RunEndRiftEventChecks.ps1 -JavaHome <pinned Temurin 25.0.4.1>`:
  passed, including the Java suites for boss hits, cast ownership and Kagune
  recovery (127 passed, 2 skipped) and accepted wave hits (27 passed).
- Focused runtime probes for the P0/P1 owners:
  `test_rift_fracture_centers_runtime`, `test_rift_fracture_restore_runtime`,
  `test_end_rift_fracture_wiring`, `test_cocoon_fall_adapter_runtime`,
  `test_end_rift_cocoon_fall_wiring`, `test_end_rift_inferno_front_runtime`,
  `test_end_rift_inferno_phase_ownership`, `test_inferno_runtime_cadence`
  — 55 passed.

## Not verified

Nothing here is a native visual or live-player claim. Visual behaviour of the fog,
ring rows, fractures and Inferno remains VISUALLY-VERIFIED = no (needs a real
client with F2 evidence). Player-count acceptance remains NOT VERIFIED.