# Phase 5 follow-up audit — unresolved production wiring

Read-only findings at baseline `97b8e43f` and the cocoon-repair working tree.
These are defects to reproduce and repair, not implementation claims.

1. `summonEchoDecoys` is called only by `summonServants`, dispatched by
   `SUMMON_SERVANTS`. `BossPhasePolicy.spellPool(RAGE)` excludes that spell.
   The normal phase-five selection therefore cannot summon the new decoys.
2. `startEchoAttackReplicas` runs at entry to RAGE, but the only invocation of
   `tickEchoAttackReplicas` is inside `pinBossToFinalSealCore`, beyond its
   LAST_SEAL-only phase gate. Replicas never advance during RAGE.
3. A replica start records a random UUID, target point, timeline and damage
   ledger. It does not spawn/publish a visible model. The ticking method has
   damage calls and no telegraph/model/animation presentation. Moving that
   ticking call alone would enable invisible damage and is insufficient.
4. Decoys are spawned as ordinary `PILLAGER` / `EVENT_KIND_ELITE` actors. The
   summon method does not bind the Guardian model/texture/animation identity.
   Their classification, damage admission, target acquisition and visuals need
   end-to-end verification before claiming that they are Guardian replicas.
5. The entry call passes `VOID_BLAST`, but `EchoAttackReplicaPolicy.timeline`
   accepts only `MELEE_SWIPE`, `CHEST_STRIKE`, and `GROUND_SLAM`. Every entry
   replica is therefore refused before even recording a timeline.
6. The current timeline does not use the real authored 26/40/110-tick contact
   markers. A visible copied animation must not be wired to its existing
   generic short strike window without correcting that timing first.
7. Replica damage currently uses direct `Player.damage` and marks its ledger
   before the accepted outcome is known. The completed adapter must use the
   event's accepted-damage/target/world/cover route and record actual accepted
   hits. The target-loss policy also needs a real runtime cancellation branch.

Required next slice: actual automatic selection -> bounded admitted actor ->
Guardian visual binding -> warned/locked authored attack -> accepted collision
and damage -> expiry/death/phase cleanup. Use adapter execution tests as well as
policy tests. Preserve the 50 HP, cap five, cooldown, phase ownership and
independent decoy/attack-replica roles from master section 15.

## Implementation sequence for the next slice

- Execute automatic RAGE selection in a harness, including an eligible decoy
  cast and its real cooldown/cap refusal; do not merely test list membership.
- Give decoys and attack echoes separate event kinds, actor records and cleanup
  ownership. Neither may acquire the real boss identity, victory path or HUD.
- Bind the original Guardian family through signed entity presentation, with
  per-entity phase/animation clocks and restrained spectral treatment. Verify
  that generic bindings do not replace the main boss's model, bar or clock.
- Start Type A only from a successfully committed supported originating attack.
  Lock valid spawn position, aim and target; use the corresponding real contact
  marker and preserve readable delay, explosion warning and collapse.
- Tick both types in the general boss loop, with explicit RAGE and encounter
  generation fences. Advance them exactly once per server tick.
- Exercise cancelled/blocked damage, wrong world/roster, target disconnect,
  failed/partial spawn, duplicate callbacks, expiry, phase exit and repeated
  cleanup. An unpresentable attack must not become invisible real damage.

No production or installed-client changes are authorized by this audit.
