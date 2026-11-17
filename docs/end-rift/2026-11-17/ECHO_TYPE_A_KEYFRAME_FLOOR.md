# Echo Type A: the retimed contact keyframe is a floor, not a shortcut

**Date:** 2026-11-17
**Scope:** `copimine-end-event` Phase 5 Echo Type A
(`domain/EchoAttackReplicaPolicy`, `domain/EchoReplicaPresentationPolicy`).
**Status:** IMPLEMENTED; AUTOMATED-TESTED (`EchoAttackReplicaPolicyTest`,
`test_end_rift_echo_replica_presentation.py`, `test_end_rift_attack_replica_wiring.py`,
`test_end_rift_echo_runtime_wiring.py`). LIVE-PAPER and VISUAL remain BLOCKED.

## 1. What the guardian branch changed, and what it broke

The guardian rebuild made the copy strike on the **retimed contact keyframe of the
family it copies**, through the shared
`me.copimine.protocol.BossAnimationPlayback.combatTicks(...)` mapping. That part is
correct and stays: master contract 15.2 Type A says a copy repeats the specific
boss attack with an "actual family/keyframe strike".

The regression was in the `Timeline` invariant. The wave 7 tip clamped the strike
to the telegraph floor:

```
hitTick = Math.max(telegraphLockTick + MIN_TELEGRAPH_TICKS, hitTick);
```

The guardian branch weakened that to `telegraphLockTick + 1L`. The retimed
MELEE_SWIPE marker is 12 combat ticks (authored 26 at the 2.25x contact rate), so
the copy's own dodge window collapsed to 12 ticks against a documented floor of
`MIN_TELEGRAPH_TICKS = 16`. `EchoAttackReplicaPolicyTest` failed on exactly those
two claims:

```
FAIL the strike is visible before it lands
FAIL the delay really is a delay
```

That is a real behavioural regression, not test staleness: a copy whose warning is
shorter than the authored minimum is the unreadable telegraph contract 23.3
forbids.

## 2. The fix (policy, not test)

Both floors are restored and the authored keyframe is treated as a **floor**:

- `Timeline` compact constructor: `hitTick = max(telegraphLockTick + MIN_TELEGRAPH_TICKS, hitTick)`
  (the lock already sits at or after arrival, so one floor covers both).
- `timeline(...)`: `lock = arrival + ARRIVAL_TICKS`, then
  `hit = lock + max(MIN_TELEGRAPH_TICKS, marker)`.

Resulting per-family timelines (verified by execution):

| family | authored marker/clip | retimed marker/clip | lock | hit | expiry |
| --- | --- | --- | --- | --- | --- |
| MELEE_SWIPE | 26 / 60 | 12 / 27 | arrival+6 | lock+16 | 57 |
| CHEST_STRIKE | 40 / 81 | 23 / 47 | arrival+6 | lock+23 | 68 |
| GROUND_SLAM | 110 / 181 | 37 / 61 | arrival+6 | lock+37 | 82 |

Every family now moves the strike **toward** its authored keyframe and never earlier
than the telegraph floor, so the copy keeps both the "same keyframe" claim and the
"readable warning" claim.

## 3. Stale source-extraction tests updated (not forced)

Master prompt line 145 requires stale source-extraction tests to be updated rather
than forcing old source strings. These were updated to equal-or-stronger claims
about the current adapter, and none of them was deleted or relaxed:

- `tests/test_end_rift_attack_replica_wiring.py`
  - `test_replicas_appear_when_rage_begins` now reads the real
    `executeContactBossAbility(...)` body and proves both entry gates
    (`RAGE && isContactBossAbility(spell)` and the chest-strike gate) hand over
    `spell.name()`.
  - `test_aim_locks_and_never_homes` now proves the aim freezes at
    `timeline.telegraphLockTick()`, that the strike axis comes from the frozen
    `lock.aim()`, that a melee swipe resolves inside the locked front arc, and that
    `connects` **and** the once-only ledger gate the damage.
- `tests/test_end_rift_echo_runtime_wiring.py`
  - the once-per-tick fence is asserted as the early-out tail (`... ) return;`), not
    as a bare `if`;
  - both damage branches are asserted to pass the live-copy guard
    (`echoReplicaContactActive(copyId, source)`) and to contain the accepted-damage
    call after their beat gate;
  - the entry-family test reads the real call site;
  - the cap/ledger/lock test asserts the frozen `lock.aim()` geometry.
- `tests/test_end_rift_echo_replica_presentation.py` — the probe now compiles the
  real dependency closure (`BossContactStrikePolicy`, `EndRiftAiPolicy`, protocol
  `BossAnimationPlayback`). The `VOID_BLAST` case was **strengthened**: the
  guardian branch deliberately refuses a non-contact origin rather than relabelling
  it as a chest strike ("a copy that repeats the wrong attack is a lie the player
  cannot read"), and the test now asserts that refusal plus copyability of every
  family the live call site can pass.
- `tests/test_end_rift_boss_phase_mutation_and_echoes.py` — the
  `EchoAttackReplicaPolicyTest` probe compiles the same dependency closure.

## 4. Not claimed

- LIVE-PAPER: whether the copy's actor, beats and explosion read correctly on a real
  server is NOT VERIFIED (0 players online).
- VISUAL: the rendered warning, keyframe strike and explosion are BLOCKED pending a
  real client capture.
