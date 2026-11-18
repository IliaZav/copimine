# Unwired Wave 7 pair methods: superseded, not missing

Date: 2026-11-18
Scope: Wave 7 Trial C (paired Skeletons), pure policies vs. the running runtime.
Companion: `docs/end-rift/2026-11-17/UNWIRED_POLICY_TRIAGE.md` classified whole
classes. This note covers methods that live inside *wired* classes and are
therefore easy to mistake for active code.

Status vocabulary:

* **IMPLEMENTED** - the code path exists and is reachable from the plugin.
* **AUTOMATED-TESTED** - a gate test drives the real code.
* **AUDITED** - verified by exhaustive source search during this review; the
  finding is a fact about the tree at the recorded commit, not a runtime test.
* **SUPERSEDED** - a newer wired owner performs the contract; the listed method
  is kept for its unit test and must not be wired back.
* **NOT VERIFIED** - neither of the above.

## 1. Method

Every `public static` method of `Wave7SkeletonElusivenessPolicy` and
`Wave7SkeletonPairPolicy` was searched across `copimine-end-event/src`,
`tools/minecraft-26.3/client/src` and `tests`. A method whose only callers are
its own unit test has no runtime caller.

## 2. Evidence

| Method | Runtime callers | Only-test callers | Status |
|---|---|---|---|
| `Wave7SkeletonElusivenessPolicy.speed` | `CopiMineEndEvent` 38674 | - | IMPLEMENTED |
| `Wave7SkeletonElusivenessPolicy.mayProvoke` | `CopiMineEndEvent` 38511 | - | IMPLEMENTED |
| `Wave7SkeletonElusivenessPolicy.mayVanish` | none | yes | SUPERSEDED |
| `Wave7SkeletonElusivenessPolicy.hidden` | none | yes | SUPERSEDED |
| `Wave7SkeletonElusivenessPolicy.recovering` | none | yes | SUPERSEDED |
| `Wave7SkeletonElusivenessPolicy.mayAttack` | none | yes | SUPERSEDED |
| `Wave7SkeletonElusivenessPolicy.hideDriftBlocks` | none | yes | SUPERSEDED |
| `Wave7SkeletonElusivenessPolicy.refusal` | none | yes | SUPERSEDED |
| `Wave7SkeletonPairPolicy.flankOf` | `CopiMineEndEvent` 38444, 38480, 38485, 38599 | - | IMPLEMENTED |
| `Wave7SkeletonPairPolicy.laneOffsetBlocks` | `CopiMineEndEvent` 38445 | - | IMPLEMENTED |
| `Wave7SkeletonPairPolicy.jointReadiness` | `CopiMineEndEvent` 38491 | - | IMPLEMENTED |
| `Wave7SkeletonPairPolicy.crossfireOffsetDegrees` | `CopiMineEndEvent` 39038 | - | IMPLEMENTED |
| `Wave7SkeletonPairPolicy.orderFor` | none | yes | SUPERSEDED |
| `Wave7SkeletonPairPolicy.refusalReason` | none | yes | SUPERSEDED |

## 3. Why each superseded method is not a gap

### 3.1 The hide window has one owner

`Wave7SkeletonElusivenessPolicy` encodes a tick-window hide model:
`VANISH_TICKS = 34`, `VANISH_RECOVERY_TICKS = 10`, a `lastVanishTick` in the
actor's memory. The shipped hide is owned by
`Wave7MarksmanInvisibilityPolicy` plus the durable
`Wave7NamedTrialActorPolicy.Checkpoint`:

* `INVISIBLE_TICKS = 100` (five seconds, as MARK-005/006 requires),
* `REVEAL_TICKS = 10`, `COOLDOWN_TICKS = 110`,
* readiness via `Checkpoint.mayVanishNow(now)` and the derived
  `nextVanishAtMillis(...)`,
* firing via `Checkpoint.mayAttackNow(now)`, which also honours the reveal.

Wiring `mayVanish`/`hidden`/`recovering`/`mayAttack` back would create a second,
contradictory owner: the elusiveness model hides for 1.7 s and reads its window
from process memory, so a restart would forget it, while the receipt-based model
survives restarts. The 2026-11-18 repair
(`MARKSMAN_HIDE_COOLDOWN_AND_REVEAL.md`) made the receipt model correct; the
older model must not be reintroduced.

`refusal` is a diagnostic for that same old model and describes only its
constants; the live diagnostics are the `WAVE7_MARKS_*` log lines emitted by
`enforceNamedTrialVanish` and `beginMarksmanVanish`.

### 3.2 A hide still relocates

`hideDriftBlocks` returns a per-flank lateral offset for a hide. The shipped
`moveNamedTrialMarksman` already relocates the actor on every tick, and it asks
`speed(true, vanished)` from the same class, so a vanished Marksman moves at
`SPEED_SPRINT` while hidden. Lane separation is owned by
`Wave7SkeletonPairPolicy.laneOffsetBlocks` inside `maintainNamedMarksmanPair`.
Adding `hideDriftBlocks` on top would give two lateral owners fighting over the
same position, which is exactly the pair pile-up the lane system was added to
prevent.

### 3.3 Pair ordering is owned by readiness plus a shared cooldown

`orderFor`/`refusalReason` describe which of the two skeletons should cast
first. The shipped runtime asks `Wave7SkeletonPairPolicy.jointReadiness` for the
joint attack and gates the two members on one shared cooldown key
(`pairedCrossfireKey`, `wave7PairedCrossfireTicks`), so only one wing is ever in
an active cast. The ordering helpers would duplicate that decision.

## 4. Honest status

| Claim | Status |
|---|---|
| The listed methods have no runtime caller | AUDITED (exhaustive source search over the plugin, client and tests; not a gate test) |
| The live hide window is `Wave7MarksmanInvisibilityPolicy` + `Checkpoint` | IMPLEMENTED |
| A vanished Marksman still relocates | IMPLEMENTED |
| Lane separation uses `laneOffsetBlocks` | IMPLEMENTED |
| Pair alternation uses `jointReadiness` + one shared cooldown | IMPLEMENTED |
| The old tick-window model is not reachable | IMPLEMENTED |
| Hide and lane behaviour on screen at 2/5/10/20 players | NOT VERIFIED (needs a live server and a real client) |

This note does not claim a visual or live pass. It records that the unwired
methods are intentional supersessions rather than missing mechanics, so a later
audit does not "restore" a second hide owner.
