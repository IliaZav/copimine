# KAGUNE: 90-SECOND RESPAWN AND DEEP SOURCE REVIEW

Approved 2026-10-13. This document supersedes "defensive tentacles never respawn" and the older 40-second policy.

## Precise gameplay rule

1. Each defensive Kagune owns a stable integer slot tied to an eventId, generation and active Rift Guardian in LAST_SEAL.
2. A LEGAL killing hit triggers ONE death transaction, one death animation and one individual countdown of **90 seconds of ACTUAL ACTIVE COMBAT TIME**. At nominal 20 TPS the equivalent is 1800 ticks; low TPS must not stretch the user's ninety real seconds. Server downtime/paused encounter time does not count.
3. At five seconds before the EFFECTIVE respawn time show a distinct telegraph at the original safe slot location. The telegraph is not itself a damaging, targetable, respawned guardian.
4. At the due time, if the boss is alive in LAST_SEAL and the slot is still dead, create exactly one guardian at the correct location with full configured HP and valid server hitbox. If spawning fails, use a bounded generation-aware retry; no fake living-state publication.
5. When the LAST living guardian dies, remove all shield displays immediately and grant the boss at least 15 seconds of vulnerability. A pending older slot that becomes due in this grace window must be deferred until the grace ends. After grace, boss stays vulnerable so long as truly zero guardians are alive. On actual successful respawn, the associated shield may return.
6. Shield displays count = actual living defender slots count, with stable slot identities. Never use four fixed shields when two or five defenders are alive. Do not show shields for DYING, removed or invalid slots.
7. A player leaving or reconnecting must not erase a dead slot or accelerate its timer. Freeze encounter scaling at LAST_SEAL entry, or implement an equivalent bounded stable roster contract, so defender count and HP don't churn on repeated quit/rejoin.
8. Persist slot ownership, health, one-time death receipt, remaining active duration, protected all-dead grace, warning state and generation. On restart after 30/60/89 seconds already elapsed, preserve the remaining active duration. If old snapshots cannot be understood safely, enter RECOVERY_REQUIRED without duplicating entities or losing real player assets.
9. Cancel all scheduled respawns, visual warnings and future effects on boss death, event reset, abort, world unload, generation change and plugin disable. No guardian may appear for a completed fight.
10. Temporary ambush Kagune are separate: telegraph, escape chance, grab/miss, hold, throw, retract and cleanup. They do NOT get a 90-second guardian resurrection timer.
11. All hits pass authoritative animation vulnerability, phase, event ownership, attack type and prior-cancellation checks before HP is reduced. Attacking during HIT_RECOVERY, DYING or any closed vulnerability window must do no damage and cause no new stun/recovery animation.

## Exact source of the old bug

CopiMineEndEvent.tickCurrentTentacles starts a 300-tick all-dead damage window and calls restoreTentacleGuardians() at its end. That function clears tentacleGuardianRespawnAt and restores all defenders to full HP. This forcibly bypasses the user's 90s rule. Refactor the state machine and schedule; never only change RESPAWN_DELAY_TICKS.

CopiMineEndEvent.reconcileGuardianShieldOrbit uses List.of(0,1,2,3) despite livingTentacleGuardianSlots(). Replace static four-shield rendering with real live-slot reconciliation.

TentacleGuardianPolicy.RESPAWN_DELAY_TICKS=800 and EventConfig.tentacleGuardianTuning() upper bound 1600 prevent 1800 ticks. Update the source, actual config.yml, parser bounds and regression tests consistently, while using an active-clock policy that remains 90 real seconds under low TPS.

The guardian health/respawn maps and damage window are in-memory. EventSnapshot.objectiveProgressSnapshot() does not explicitly encode LAST_SEAL slot states. Investigate full restart behavior; write and test safe schema evolution, not a blind serialization change.

The current tentacleDamageWindowOpen() means BOSS_ACTIVE, not necessarily a legitimate animated hit state. applyTentacleGuardianDamage subtracts HP before shouldEnterHitRecovery(). A spammed hit can therefore drain HP without a new hit reaction. onTentacleGuardianDamage(ignoreCancelled=false) logs already-cancelled hits but may still manually reduce the slot ledger. Reproduce via real event adapter/WorldGuard before fixing root causes.

tickCurrentTentacles recalculates permanent target number from active participants each tick. Ensure scaling transitions cannot erase death timers or create duplicated slots after disconnect.

## Other newly identified event bugs/gaps

### Boss inferno is not the approved design
CopiMineEndEvent.arenaInferno uses ARENA_INFERNO_DURATION_TICKS=100 and a 20-tick interval, explicitly says "safe sectors - five seconds", arranges fixed coordinate sectors and applies damage/Weakness. The approved spell should last about 20 seconds and use readable telegraphs, bounded ignition/armor durability effects and actual steam vents, without sector mechanics. Validate hazard journal rollback, no unsafe arena edits, natural counterplay and all failure paths.

### Portal visual resilience
spawnPortalModelVisual accepts incomplete frame/inner/shard layer sets. refreshPortalObjectiveVisuals marks a rebuild attempt used before successful creation; PortalPresentationPolicy.shouldRebuild only permits attempts==0. Simulate twice-failed layer creation and verify retry or atomic rollback with no ghost entities. Native F2 must verify orientation and 3D texture from front/rear/left/right/top.

### Wave 7 old gameplay runtime
Wave7AdmissionPolicy knows Warden/Echo/Marksman/Archmage but RealitySplitTrialController still uses Reflection/Juggernaut/Hunter. Named Java policy tests alone cannot certify actual Wave 7 content. Introduce versioned snapshot migration and a genuine runtime integration test. Two-player defaultLayout currently has Warden/Echo only; establish a playable official two-player progression without requiring four players simultaneously.

### Sixth-wave target selection risk
PrisonerTargetSelector queries a maximum of 256 candidate entities before line/eligibility filtering. Under crowded conditions an eligible target may be omitted. Trace server eligibility, client packet/state, ray through sphere visual, and outline Mixins in a real 26.3 client. A code class existing is not visible cyan ally highlight proof.

## Non-negotiable tests

- Kill guardian A at t=0; no spawn at 89.9s active, warning 85s, exact one appearance at >=90s.
- Kill guardian B at t=30s; it respawns no sooner than t=120s.
- Kill last guardian while another one's timer is due inside protected 15s window. Boss vulnerability continues >=15s; any guardian respawn is delayed correctly and warning aligns with EFFECTIVE due.
- One living defender produces exactly one visible shield; two produce two; zero produce zero; 5-6 player scaling uses real slots. All shield displays clean up on death and abort.
- Restart after 30, 60 and 89s elapsed; remaining active time and HP must not be reset or duplicated. Simulated 10 TPS must not double real waiting time.
- Kill boss exactly at due time; no late warning or new guardian. Exit/rejoin/restart/canceled event and old generation cannot create anything.
- Rapid melee and projectiles during blocked animation state do not drain HP, create HIT_RECOVERY or stop grab. Previously cancelled hits (WorldGuard) cannot modify shield ledger.
- Show real 90s countdown and respawn in full real Paper 26.3, matching pinned Fabric client and resource pack. F2 from all angles, before/after shield, legitimate hits and grab/throw video. Do not mark NATIVE VERIFIED based on policy tests or source-only review.
- Complete native 2-player and 5-6-player boss fights to ensure the repeating shield mechanic is still winnable.

Implementation destination is a NEW safe End Rift feature branch/worktree based on the accepted Minecraft 26.3 branch. Do not change Java in this documentation branch. The existing dirty codex/end-rift-event worktree must be preserved. Keep the documentation PR as a draft until integration is intentionally approved.
