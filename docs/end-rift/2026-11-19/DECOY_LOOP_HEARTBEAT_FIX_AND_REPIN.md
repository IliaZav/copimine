# Echo decoys: a loop phase is carried only across a cast rollover

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`), decoy actor clock in
`EndEventClientState`.
Spec: `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` sections 15.3 and
15.5; companion `COPIMINE_END_RIFT_WAVES_CANONICAL.md`.
Supersedes the client pin in
`docs/end-rift/2026-11-19/DECOY_LOOP_ROLLOVER_FIX_AND_REPIN.md`; that record
stays as written, this file carries the current artifact.

Status vocabulary: IMPLEMENTED / AUTOMATED-TESTED / LIVE-PAPER-VERIFIED /
VISUAL-VERIFIED / NOT VERIFIED.

## 1. Defect - the first phase fix double-counted between renewals

`DECOY_LOOP_ROLLOVER_FIX_AND_REPIN.md` made a decoy's looping clock continuous
across the ten-second wire cap, but it carried the played time on **every** cue
for the same action. The server does not only re-send at the cap:

* `CopiMineEndEvent.tickEchoDecoyActors` runs every ten ticks and calls
  `sendGuardianActorFrame(actor, action, old.startTick(), 200,
  eventTickCounter - old.startTick())`;
* so within one 200-tick window the same cast is heartbeated every ten ticks
  with an **advancing** `elapsed`, and only at the window edge does the cast
  advance with `elapsed = 0`.

`GuardianActorFrame.elapsedAt` already advances by the time since the last
frame was received, so adding the previous segment to `loopPhaseMillis` on a
same-cast heartbeat counted the same played time twice. Between renewals the
idle breath and the run cycle then ran up to roughly twice real speed, which is
its own broken-animation tell.

## 2. Repair

The phase is carried only when the cast actually changes:

```java
long loopPhase = old == null ? 0L : old.loopPhaseMillis();
if (old != null && frame.cast() != old.frame().cast()
        && frame.action() == old.frame().action()
        && loopsGuardianAction(frame.action())) {
    loopPhase = old.loopPhaseMillis() + old.frame().elapsedAt(now - old.receivedAt());
} else if (old != null && frame.action() != old.frame().action()) {
    loopPhase = 0L;
}
```

and the renderer clock is unchanged:

```java
actor.loopPhaseMillis() + actor.frame().elapsedAt(now - actor.receivedAt());
```

Consequences:

* a **same-cast heartbeat** keeps the previous phase, so the frame's own
  `elapsed` drives the clock exactly once;
* a **cast rollover** adds exactly one full segment, because the segment is
  measured from the last received frame (heartbeat or previous rollover);
* a genuinely new action still drops the carried phase, so one-shot clips keep
  their authored timing;
* a repeated one-shot cast of the same action still restarts;
* the duplicate-frame guard is unchanged.

`GuardianActorFrame`, the wire cap and the 200-tick renewal are untouched; only
the client clock is corrected.

## 3. Evidence

| Claim | Status |
|---|---|
| A same-cast heartbeat does not double-count the loop clock | AUTOMATED-TESTED (`EndEventClientStateTest::sameCastHeartbeatsDoNotDoubleCountTheLoopClock`) |
| A renewed idle/run cue continues instead of resetting to frame zero | AUTOMATED-TESTED (`EndEventClientStateTest::aLoopingDecoyCueRenewedUnderANewCastKeepsItsPlayedPhase`) |
| Two consecutive renewals keep accumulating | AUTOMATED-TESTED (same test, second cast) |
| A new action drops the carried phase | AUTOMATED-TESTED (same test, `HURT` cast) |
| A repeated one-shot cast is not treated as a loop renewal | AUTOMATED-TESTED (`EndEventClientStateTest::aOneShotCueIsNeverTreatedAsALoopRollover`) |
| The client source gates the carry on a cast change | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py::test_a_renewed_looping_decoy_cue_keeps_its_played_phase`) |
| The client compiles and its unit tests pass on pinned JDK 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL`, `EndEventClientStateTest` tests=15 failures=0 |
| A decoy's idle breath and run cycle visibly never pop or speed up in the live game | NOT VERIFIED - needs a real client |

## 4. Re-pin

Only client sources and one client unit test moved; no `copimine-end-event`
source changed.

* previous: `30252249` bytes,
  `4432593280be7a7a0131aafb705bbcef531f8f686703556eaad0452e8759a8a27f5f3a9e27a1d354be66c593e06af2cc6c5c9d17442009927db1bbbec808a95d`;
* current: `30252288` bytes,
  `3205690d5482a373b33e7034337ddf16647517860130afea72816349b0f20cc23a0c4b9bb55f2b5b5b4929e37f09882e1d32581086ad0dd88cf2fdd912182097`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.