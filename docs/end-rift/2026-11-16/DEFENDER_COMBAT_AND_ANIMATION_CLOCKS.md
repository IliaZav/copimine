# Directed guardian combat and independent animation windows

Start: `59950b7c`. Source commits: `d142b302`, `dac38cea`, `5965a9dc`,
`c103f30a`. Target branch `origin/codex/rift-guardian-polish-20261110`.
User assigns test suites/gameplay to DeepSeek. No local suite or Minecraft
playtest was launched. The November 11 master contract supersedes old guardian
respawn/grab behavior.

## Actual integration repaired

- Large permanent defenders previously scheduled TELEGRAPH_GRAB, then dealt an
  instantaneous legacy strike with fixed damage/weak push. The authored group
  pressure policy was not connected to that path. They now own directed
  windup/contact/recovery, frozen facing, a real contact marker, miss handling
  and cooldown. The reused SMALL_* identifiers are **animation-window IDs**:
  actor kind, guardian HP, slot/death ledger and shield ownership stay PERMANENT.
- The server limits concurrently preparing/striking defenders per target with
  `KaguneGuardianFightPolicy.mayAttackTarget`; accepted heavy contact gives that
  target a 60-tick protection from another guardian heavy hit. Held participants
  are excluded. Damage uses the existing bounded party-scaled fight policy and
  the guardian's owned physical damage carrier, respecting accepted outcomes.
- Heavy contact checks actual range, locked facing and block LOS. Range is
  bounded by the imported rig length plus player allowance, rather than the
  old eight-block grab reach. A miss consumes the cast. A safe shove corridor is
  checked in half-block samples; the five-to-six-block policy value is treated
  as **distance**, not directly passed as velocity. The initial conversion uses
  distance * 0.165 horizontal impulse with lift 0.32, reduced near obstruction.
  Actual displacement under player motion/physics remains unmeasured.
- Once committed, this attack is not replaced by click-driven flinches; actual
  HP damage and red health feedback remain. Legacy saved TELEGRAPH_GRAB states
  recover into a fresh warned cycle rather than executing an old immediate hit.
- Small Kagune avoid held participants and fence post-damage feedback against
  changed encounter, generation, boss, actor and target state.
- Client `sameClip()` treated windup, strike and recovery as aliases of `trow`,
  retaining the already elapsed windup clock/duration for the strike. Different
  retimed windows now get independent clocks. Their matching endpoint pairs
  bypass a redundant blend, preserving the tick-4 contact pose. Same-window
  heartbeats still preserve time; all original bones/pivots/UVs remain intact.
- Grounded boss pursuit excludes held targets consistently in assignment,
  retained-focus validation and candidate selection. Invalid native targets
  clear old paths before the normal request cadence; refused target clears no
  longer leave navigation chasing a held/offline participant.
- Pressure counts are sampled once per target-selection call, and ordinary
  temporary counts once per trim pass. No per-candidate controller snapshot or
  repeated whole-state recount remains in those loops. No FPS/MSPT claim follows.
- Pressure generation initialization also occurs on a restored strike, so the
  next fresh target selection cannot erase its newly accepted-contact lease.

## Build, review and artifacts

Pinned JDK 25 / Gradle 9.8.1 plugin/bridge and clean client JAR builds succeeded.
`git diff --check` is clean. Archive CRC/entry inspection confirms protocol,
models, audio and new dispatcher/clock methods in actual candidate JARs.
CodeRabbit completed with zero issues for `59950b7c..d142b302` (three files).
Later navigation/counting/restoration follow-ups were manually reviewed and
compiled; do not expand that immutable independent-review claim to them.

Candidate: `artifacts/20261116-defender-combat-candidate/manifest.json`.

| File | Bytes | SHA-256 |
|---|---:|---|
| CopiMineEndEvent.jar | 2562871 | de443f35bde0d6d2c25ebbee6b956e64bb0c57d4c780ce5f1be188b2bcfc3019 |
| CopiMineNarcotics.jar | 378217 | 4609582cdc08db60e64122b510e7a264a001baeef88fd10f905bee054be4f9a0 |
| CopiMineClient-0.1.4+26.3.jar | 30247649 | 3421122bd26b21477fc47ed7e09c2216ea0a95c1b3becea546f0170a41436e50 |
| Converted pack | 34373458 | 98d7950558e7da38e37b641d340b7fe89dc0310cbce6ad0671be62432d627176 |

## Remaining acceptance for DeepSeek

Exercise all defender stages and tick-4 contact, changing position/facing/LOS,
simultaneous eight-defender selection, per-target pressure, canceled damage,
shield/protection hooks, actor/boss replacement during damage, blocked shove
corridors, late target control, death/recovery and legacy snapshot adoption.
Inspect continuous small/large attack windows in Minecraft; test 2/5/10/11/20
participants and measure actual knockback, performance and all-phase gameplay.
Prior measured GitHub CI remained red (see preceding ring report); no current
green CI or complete six-phase/native acceptance is claimed.
