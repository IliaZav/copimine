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

Required next slice: actual automatic selection -> bounded admitted actor ->
Guardian visual binding -> warned/locked authored attack -> accepted collision
and damage -> expiry/death/phase cleanup. Use adapter execution tests as well as
policy tests. Preserve the 50 HP, cap five, cooldown, phase ownership and
independent decoy/attack-replica roles from master section 15.

No production or installed-client changes are authorized by this audit.
