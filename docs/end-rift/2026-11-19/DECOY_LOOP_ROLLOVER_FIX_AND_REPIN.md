# Echo decoys: a renewed looping cue keeps its phase

Date: 2026-11-19
Scope: the 26.3 client (`tools/minecraft-26.3/client`), decoy actor clock in
`EndEventClientState`.
Spec: `docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md` sections 15.3 and
15.5; companion `COPIMINE_END_RIFT_WAVES_CANONICAL.md`.
Supersedes the client pin in
`docs/end-rift/2026-11-19/GUARDIAN_PER_BODY_CLIP_STATE_FIX_AND_REPIN.md`; that
record stays as written, this file carries the current artifact.

Status vocabulary: IMPLEMENTED / AUTOMATED-TESTED / LIVE-PAPER-VERIFIED /
VISUAL-VERIFIED / NOT VERIFIED.

## 1. Defect - a decoy's idle or run cycle popped every ten seconds

`GuardianActorFrame` is a bounded cue: the constructor and parser reject an
elapsed value above the duration, and the duration is capped at 10 000 ms. A
decoy therefore cannot be driven by one endless cue. The server renews the
looping locomotion cue under the next cast every 200 ticks
(`CopiMineEndEvent.tickEchoDecoyActors`, `duration = 200`), sending
`elapsed = 0` with the new cast.

The client stored exactly one `(frame, receivedAt)` per body and computed

    elapsed = frame.elapsedAt(now - receivedAt)

so the renewal reset the clock to zero. `UserEndBossAnimationPlayer.sample`
wraps a looping clip with `seconds % lengthSeconds`, so the rig jumped from
`elapsed % length` back to `0` on every renewal:

* `IDLE_BREATH` is a 4.0 s clip, so the 10 s renewal popped from 2.0 s of the
  breath cycle back to its first frame;
* `RUN` is a 2.0417 s cycle, so the foot phase jumped at every renewal.

Every decoy did this once per ten seconds. Contract 15.3 forbids a decoy
"immediately reveal[ing] itself through obviously broken animations"; a
periodic full-body pop is exactly the kind of defect that clause exists for,
even though it does not by itself identify the real boss.

## 2. Repair

`EndEventClientState.GuardianActorBinding` now carries a `loopPhaseMillis`
value, and the two authored looping actions are named once:

```java
private static boolean loopsGuardianAction(Action action) {
    return action == Action.IDLE_BREATH || action == Action.RUN;
}
```

When a cue arrives for the same action with a newer cast, the client carries
the time already played:

```java
loopPhase = old.loopPhaseMillis() + old.frame().elapsedAt(now - old.receivedAt());
```

and the renderer clock returns

```java
actor.loopPhaseMillis() + actor.frame().elapsedAt(now - actor.receivedAt());
```

The carried phase is added only for `IDLE_BREATH` and `RUN`:

* a genuine new action (`HURT`, a spell, `GROUND_SLAM`) still starts at zero,
  so one-shot clips keep their authored timing;
* a repeated one-shot cast of the same action still restarts, because that is a
  new cast rather than a loop renewal;
* the duplicate-frame guard is unchanged, so a heartbeat that is behind the
  local clock neither rewinds nor double-counts.

`GuardianActorFrame` and the server adapter are untouched: the wire format,
the 10 s cap and the 200-tick renewal all stay as they are. Only the client
clock is made continuous.

## 3. Evidence

| Claim | Status |
|---|---|
| A renewed idle/run cue continues instead of resetting to frame zero | AUTOMATED-TESTED (`EndEventClientStateTest::aLoopingDecoyCueRenewedUnderANewCastKeepsItsPlayedPhase`) |
| Two consecutive renewals keep accumulating | AUTOMATED-TESTED (same test, second cast) |
| A new action drops the carried phase | AUTOMATED-TESTED (same test, `HURT` cast) |
| A repeated one-shot cast is not treated as a loop renewal | AUTOMATED-TESTED (`EndEventClientStateTest::aOneShotCueIsNeverTreatedAsALoopRollover`) |
| The client source keeps the loop rule and the carried phase | AUTOMATED-TESTED (`tests/test_end_rift_echo_decoy_render_dispatch.py::test_a_renewed_looping_decoy_cue_keeps_its_played_phase`) |
| The client compiles and its unit tests pass on pinned JDK 25.0.4.1 / Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL`, `EndEventClientStateTest` tests=14 failures=0 |
| A decoy's idle breath and run cycle visibly never pop in the live game | NOT VERIFIED - needs a real client |

## 4. Re-pin

Only client sources and one client unit test moved; no `copimine-end-event`
source changed.

* previous: `30252055` bytes,
  `63db43fcd4a8503658f3a14f03f9c45d76c2aed7b62cc8b301b820ac620bbc4b8605e02c1ffefb46f1be229aa319015814e165edce75e9686279b63da2247ed9`;
* current: `30252249` bytes,
  `4432593280be7a7a0131aafb705bbcef531f8f686703556eaad0452e8759a8a27f5f3a9e27a1d354be66c593e06af2cc6c5c9d17442009927db1bbbec808a95d`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the new size and digest.

The plugin candidate is unchanged: `2582610` bytes /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.