# Marksman hide: cooldown and reveal repair

Date: 2026-11-18
Scope: Wave 7 Trial C (Mobile Marksman), server runtime and pure policies.
Spec: `COPIMINE_END_RIFT_WAVES_CANONICAL.md` §9.4 (tactical repositioning,
bounded relocation, real telegraph before real damage).

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a gate test drives the real code path.
* **VISUAL-VERIFIED** - confirmed on a real client.
* **NOT VERIFIED** - neither of the above.

## 1. What the contract asks for

§9.4 requires a Marksman that relocates tactically rather than retreating
permanently, that shows a readable warning before a real projectile, and that
never lands an uncontested hit from concealment. MARK-005/006 in the Wave 7
register describe the concrete mechanism: a five-second hide used to change
position, with a real cooldown, a real reveal, and no immunity.

## 2. Two defects found during the audit

Both live in the same short path, and both were reachable in play.

### 2.1 The hide cooldown could never elapse

The durable receipt stores one invisibility field: `vanishUntilMillis`, the end
of the hidden window. The tick path gated a new hide on

```java
checkpoint.vanishUntilMillis() <= 0L
```

That reads as "have I never hidden?", not "is my cooldown over?" - because after
the first hide the field holds a positive absolute timestamp forever. A Marksman
therefore hid exactly once per trial and never again, which contradicts the
policy's own documented intent ("two hides in a short fight are what makes the
pair feel evasive") and the `COOLDOWN_TICKS` constant that existed only to bound
that repetition.

Evidence: `beginMarksmanVanish` also called
`Wave7MarksmanInvisibilityPolicy.vanish(now, 0L)`, passing a zero cooldown, so
the policy's `Window.cooldownUntil` was computed and then discarded.

### 2.2 The reveal window did not exist

`beginMarksmanVanish` deliberately records the *end of the hide* rather than the
end of the reveal, so the actor becomes visible for `REVEAL_TICKS` before it can
fire again. The comment says exactly that. But `Checkpoint.mayAttackNow` was

```java
return !hiddenNow(nowMillis);
```

which resumes attacks on the exact millisecond the hide ends - the first reveal
tick. The intended readable reappearance was therefore a point-blank volley
from a mob that had been invisible one tick earlier, and the spec's
warning-before-damage requirement was not met for this attack.

`Wave7MarksmanInvisibilityPolicy.resume` already treats the persisted deadline
as the hide end and adds `REVEAL_TICKS` before attacks resume, so the pure
policy and the persisted receipt disagreed about the same window. That
disagreement is the strongest evidence that this was a defect and not a design
choice.

## 3. The repair

One owner computes the window; the receipt just records the hide deadline.

* `Wave7MarksmanInvisibilityPolicy` gained `REVEAL_MILLIS`, `COOLDOWN_MILLIS`,
  `nextVanishAtMillis(long hideDeadline)` and
  `mayVanishAt(long now, long hideDeadline)`. The derived "ready" instant is
  `hideDeadline + REVEAL_MILLIS + COOLDOWN_MILLIS`, which is the same number
  `Window.cooldownUntil` already produced in ticks. Both timelines are asserted
  equal so they cannot drift apart again.
* `Checkpoint.mayAttackNow` now honours
  `Wave7MarksmanInvisibilityPolicy.REVEAL_MILLIS` after the hide ends, and a
  receipt with no recorded hide still attacks freely (`vanishUntilMillis <= 0`).
* `Checkpoint.mayVanishNow` is the single readiness predicate; the tick path
  uses it instead of testing the raw deadline.
* `beginMarksmanVanish` passes the derived cooldown into the policy, so the
  window it builds is consistent with the receipt it writes.

No schema change was needed: the cooldown is derived from data already
persisted, so a restart mid-cooldown resumes the same cooldown instead of
granting a fresh hide.

## 4. Tests

`tests/Wave7MarksmanInvisibilityPolicyTest.java` (gate-registered) gained two
behavioural checks:

* `revealIsHonouredByThePersistedDeadline` - the derived ready instant equals
  hide end + reveal + cooldown, and the millisecond and tick timelines agree.
* `vanishIsRepeatableOnceTheDerivedCooldownElapses` - a hide still running or
  still in its reveal refuses a second hide, a cooldown that has not elapsed
  refuses it, the exact boundary re-arms it, and a malformed clock stays
  fail-closed.

The four Python harnesses that compile `Wave7NamedTrialActorPolicy` in
isolation (`test_wave7_named_runtime.py` x4, `test_wave7_roster_routes.py`,
`test_wave7_scripted_projectile_admission.py`,
`test_wave7_archmage_occluded_target_runtime.py`) now also compile
`Wave7MarksmanInvisibilityPolicy.java`, because the checkpoint genuinely depends
on it. That is a real dependency, not a test workaround.

## 5. Honest status

| Claim | Status |
|---|---|
| A Marksman can hide more than once per trial | IMPLEMENTED |
| The derived cooldown matches the policy's tick timeline | AUTOMATED-TESTED |
| A second hide inside the cooldown is refused | AUTOMATED-TESTED |
| Attacks wait for the reveal after a hide | IMPLEMENTED |
| The checkpoint's attack gate honours the reveal | AUTOMATED-TESTED |
| Hide/reveal in a live encounter at 2/5/10/20 players | NOT VERIFIED |
| The smoke tell, ash column and reappearance read correctly on screen | NOT VERIFIED (needs a real client) |
| Windows client rendering of the vanish | NOT VERIFIED (needs a real client) |

Live and visual confirmation remain outstanding because no players are online
and `/cmend test wave 7` refuses an empty sandbox roster. Nothing above should be
read as a live pass.

## 6. Re-pin

The End Event sources changed, so the compiled jar moved and the candidate was
re-pinned to `2578629` / `0023966595152218288af1f02db1e4bb122f8a2178b043077930b185addaa6bb`.
The replaced `4fa53ab0...` digest was prepended to `replaceSha256`, keeping
already-staged older candidates acceptable.