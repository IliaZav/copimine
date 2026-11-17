# Wave 7 Echo AI: the habits that were encoded but never read

**Date:** 2026-11-18
**Scope:** `copimine-end-event` Wave 7 (Reality Split) Personal Echo,
`runtime/wave7/EchoDuelCombatRuntimeAdapter.java`, and the End Rift gate
(`tests/RunEndRiftEventChecks.ps1`).
**Status:** IMPLEMENTED; AUTOMATED-TESTED through the End Rift gate.
LIVE-PAPER and VISUAL verification remain BLOCKED (no live roster, no client
capture).

## 1. The gap that was found

`EchoBehaviorStrategy` carries thirteen bounded weights. Three of them were
written, validated, encoded, decoded and persisted, and then never read by the
only code that decides what a copy does:

| Field | Written by | Read by the adapter before this change |
|---|---|---|
| `jumpAttackPermille` | `EchoBehaviorStrategy.from(...)` from `ProfileCounts.jumpAttacks` | nothing |
| `weaponSwitchPermille` | `EchoBehaviorStrategy.from(...)` from `ProfileCounts.itemSwitches` | nothing |
| `holdDistancePermille` | measured preferred distance | read, but clamped nowhere near melee reach |

`EchoDuelCombatRuntimeAdapter` is the single decision point for the official
named Echo tick (`CopiMineEndEvent.tickNamedEchoDuel` →
`EchoDuelCombatRuntimeAdapter.tick`), so a field the adapter never reads has no
effect anywhere in the product. No test referenced either unused field, which is
why the gap survived: the profile round-tripped perfectly and every assertion
about it still passed.

## 2. What was fixed

### 2.1 A brawler copy now actually reaches melee reach

The hold band was `1.5 + 10.5 * holdDistancePermille / 1000` blocks with a
1.5-block tolerance. For the neutral 500 per mille, a melee copy stopped
repositioning at 6.75 blocks and only started approaching beyond 8.25 blocks,
while melee reach is 4 blocks. A melee copy therefore never closed on its own;
it fought only owners that walked into it.

For melee-dominant profiles (`rangedPermille <= meleePermille`) the upper edge of
the band is now clamped to the melee envelope
(`MELEE_RANGE_BLOCKS - HOLD_TOLERANCE_BLOCKS`), so the copy approaches until it
is inside reach and then holds. Ranged profiles keep their measured band
unchanged — a kiter still kites, checked directly in the same test.

### 2.2 `jumpAttackPermille` drives the closing hop

An owner who jumped into their attacks gets a copy that closes with a hop, so
the arrival itself sets up the falling critical that `Action.CRITICAL` already
requires (`!onGround && criticalWindow`). The branch:

- runs only after every weapon branch has declined this tick, so it can never
  replace a legal attack with a jump;
- requires the copy to be outside melee reach, so a swing is never traded for a
  hop;
- requires `intent == APPROACH`, so the hop is part of closing;
- requires a rate strictly above `JUMP_ATTACK_BASELINE_PERMILLE = 250`, the
  documented default of the legacy five-feature profile. A baseline profile has
  no jump evidence, and a copy must not invent jump attacks its owner never
  played.

### 2.3 `weaponSwitchPermille` drives the mid-fight swap

A copy of a player who swapped tools constantly no longer locks itself to one
weapon: inside its own recorded switch window (`inProfileWindow`, the same
deterministic window helper the mobility and defence habits use) it puts a shot
in from inside melee reach instead of swinging. The swap is only taken when the
shot is legal this tick — weapon and ammo carried, line of sight, inside the
ranged envelope, and past the ranged, shared-action and pending-use gates — so
it can never burn a swing on a bow it cannot draw. Owners below
`WEAPON_SWITCH_FLOOR_PERMILLE = 500` are unchanged, which is why no existing
fixture moved: the legacy constructor defaults the field to 250.

All three behaviours are pure functions of the tick and the persisted profile.
No random source was added, so a replay produces the same decision.

### 2.4 The mobility hop no longer outranks every attack

`mobilityPermille` is not a jump weight: `from(...)` sets it to
`share(counts.sprintSamples(), counts.movementSamples())`, the owner's **sprint**
share. The adapter nevertheless let it trigger a `JUMP` before the potion, shield,
critical, melee, ranged, wind-charge and splash branches, and the window helper
opens for roughly `permille / 10` ticks out of every hundred. A player who
sprinted through half their movement samples therefore produced a copy that was
airborne on about half its ground ticks and spent most of the fight not hitting
anything — and because the branch sat above the attack branches, it pre-empted
every attack that was legal at the time.

The branch now sits below all of them, immediately before the `NONE` fallback:
the copy still visibly jumps whenever nothing is there to be hit, which is what
`profileDrivenMobilitySchedulesBoundedJumpActions` asserts, but a legal swing is
chosen first. This is the same rule the jump-attack hop already followed, so the
two jump branches no longer disagree about priority. No cooldown, cost or
admission rule changed: the jump still goes through
`EchoDuelCombatPolicy.tryStartJump`, which enforces the shared action and jump
cooldowns.

### 2.5 A shielded copy no longer freezes on the spot

The adapter short-circuits while its own shield window is live and returns
`NONE`. That return used the nine-argument `Decision` constructor, whose missing
intent defaults to `MovementIntent.HOLD` — so for the whole parry (5-20 ticks,
`5 + 15 * defensePermille / 1000`) the copy stood perfectly still while its owner
was free to walk out of the duel cell and re-line a shot. The navigation comment
two hundred lines below already claimed the opposite ("keeps a mid-jump or
shielded copy visibly repositioning instead of freezing on the spot"), so the
code contradicted its own contract.

Health and navigation are now resolved *before* the shield short-circuit and the
short-circuit reports the intent the copy would have chosen anyway. The window
still grants no action — the decision remains `NONE` and the shield stays the
only thing happening — but the copy keeps walking. `wounded` is computed once and
reused by the heal branch, so no behaviour was added, only moved.

The consumable branch deliberately still reports `HOLD`: unlike a raised shield,
starting to eat is a commitment the copy makes on purpose, and the health it is
about to regain is granted at `pendingUseUntilTick`, not on the spot. Do not
"fix" that the same way.

The freeze was not cosmetic. `CopiMineEndEvent.driveNamedEchoMovement(...)` is
called with `decision.intent()` for every action kind, and its first branch is
`if (intent == HOLD) carrier.getPathfinder().stopPathfinding()`. A `HOLD` from
the shield short-circuit therefore actively cancelled the copy's path for the
whole parry, not just skipped one steering update.

### 2.6 A wounded copy only retreats when it can actually heal

`movementIntent(...)` returned `KITE` for every melee-dominant copy that was
below `HEAL_THRESHOLD_PERMILLE`, with no regard for whether the copy had anything
to heal with. The heal branch above it is gated on two more things — a carried
golden apple or food, and `sustainPermille() >= SUSTAIN_FLOOR_PERMILLE` — so a
wounded brawler with an empty inventory took the retreat and never came back:
it left melee reach, was still wounded on every later tick, and therefore kept
kiting for the rest of the duel. That is the "endless retreat without firing"
failure the navigation rule in this very file forbids, and it is not what a
player without healing does.

The intent is now computed from a `canHeal` flag that mirrors the heal branch's
own gate (`consumableSlot >= 0 && sustainPermille() >= SUSTAIN_FLOOR_PERMILLE`).
In practice the heal branch runs first, so a wounded copy that can start eating
this tick eats (`CONSUMABLE`, reported with `HOLD` on purpose) and the retreat
covers the ticks where the heal is not startable yet — a shared-action cooldown,
a pending use. A copy with nothing to heal now keeps fighting instead of kiting
for the rest of the duel. The three-argument
`movementIntent(strategy, observation, wounded)` overload is unchanged in
meaning — it is the "wounded and able to heal" case the retreat was written for,
and the harness test keeps asserting exactly that.

## 3. Dead coverage that was revived

`tests/harness/me/copimine/endevent/runtime/wave7/EchoWoundedRetreatTest.java`
was committed on 2026-11-15 and then never compiled and never run: it lives in a
packaged harness directory that no runner walked, and it was not listed in
`$pureTests`. It asserts the wounded-melee retreat through the package-private
`movementIntent`, which is exactly why it cannot live in the default package.

The gate now resolves test sources by simple name across `tests\` **and**
`tests\harness\`, compiles the harness sources into the same bundle, and runs
`EchoWoundedRetreatTest` by its qualified name. `tests/RunEndRiftEventChecks.ps1`
therefore executes one more real behavioural suite than before.

`tests/test_end_rift_java_tests_are_all_executed.py` had the matching hole: its
registered-name scan matched only `[A-Za-z0-9_]+`, so a qualified entry was
invisible, and its file scan only looked at `tests\*.java`. Both now cover
qualified entries and the harness tree. Making the scan honest immediately
exposed seven exemptions in `NOT_IN_GATE_BUNDLE` that claimed a test could not
be compiled into the bundle while the gate was already running that very test:
`EchoLimboThresholdTest`, `MinibossGroupSpellPolicyTest`,
`SpiderEffectLeasePolicyTest`, `WardenSlamTriggerPolicyTest`,
`WaveSixDurableStateTest`, `WaveTwoStagePolicyTest`,
`WaveTwoOwnershipPolicyTest`. Those stale exemptions were deleted rather than
kept alive by a weak regex.

## 4. Verification

| Claim | State | Evidence |
|---|---|---|
| Three habits wired | IMPLEMENTED | `EchoDuelCombatRuntimeAdapter.java`; `jumpAttackPermille` / `weaponSwitchPermille` now have readers |
| Behaviour asserted | AUTOMATED-TESTED | `tests/EchoDuelCombatRuntimeAdapterTest.java`: `meleeCopiesCloseIntoMeleeReach`, `jumpAttackOwnersHopWhileClosingIntoTheFallingCrit`, `frequentWeaponSwitchersTradeASwingForAShot`, `sprintHeavyCopiesAttackBeforeTheyHop`, and the shielded-copy intent check inside `profileDrivenDefenseSchedulesOwnerBoundShieldActions` |
| Wounded retreat truly executed | AUTOMATED-TESTED | `EchoWoundedRetreatTest` now in the gate's `$pureTests`, resolved from `tests\harness\`; its `woundedWithoutHealingKeepsFighting` case separates "retreat to heal" from "flee forever" |
| Wounded copy with no heal keeps fighting | AUTOMATED-TESTED | `EchoWoundedRetreatTest.woundedWithoutHealingKeepsFighting` (intent: `canHeal=false` does not KITE, `canHeal=true` does) and `EchoDuelCombatRuntimeAdapterTest.woundedCopyWithoutFoodKeepsFighting` (fed copy eats, empty copy does not flee) |
| Gate green | AUTOMATED-TESTED | `tests\RunEndRiftEventChecks.ps1 -JavaHome <pinned Temurin 25.0.4.1>` exit 0 |
| Copies look right in a real client | NOT VERIFIED / BLOCKED | no client capture; hop landing, swap animation and the crit window need F2 frames |
| Live roster behaviour | NOT VERIFIED / BLOCKED | no players online; `/cmend test wave 7` refuses an empty sandbox roster |

The behaviours are bounded and additive: no cooldown, cost, admission or
persistence rule was relaxed, and every pre-existing assertion in
`EchoDuelCombatRuntimeAdapterTest`, `EchoBehaviorPersonalisationTest` and
`EchoWoundedRetreatTest` is unchanged.

## 5. The two remaining unread weights, and why that is deliberate

A source-wide read audit (every `.field()` access under
`copimine-end-event/src`) still finds two `EchoBehaviorStrategy` weights with no
reader. This is recorded here so the next person does not re-open it as a bug:

- `shieldPermille` — `EchoBehaviorStrategy.from(...)` computes it with the exact
  expression it uses for `defensePermille`
  (`share(counts.shieldSamples(), counts.movementSamples())`), and the legacy
  8-argument constructor sets `shieldPermille = defensePermille`. The adapter
  already reads `defensePermille` to drive the shield branches, so
  `shieldPermille` is a byte-compatible duplicate kept because it is already
  part of the persisted 14-field encoding. Giving it a second, different
  behaviour would make two fields that are always equal drive two unrelated
  things. Removing it would change `ENCODED_FIELDS` and invalidate every
  persisted duel snapshot, which is not worth it for a field that duplicates
  another.
- `confidencePermille` — the profile's evidence-quality scalar. It is encoded,
  decoded, validated and asserted, but it gates nothing. The "do not invent a
  habit the owner never played" protection is already structural: every style
  weight falls back to a documented neutral value when its evidence is missing
  (`share()` returns 500 for a zero denominator, `jumpAttackPermille` falls back
  to 250, `holdDistancePermille` to 500), so a sparse profile already produces a
  generic copy without consulting confidence. Gating the new habits on
  confidence as well would be a second, arbitrary threshold on top of the ones
  the habit weight already encodes.

Both are reported as NOT VERIFIED-BEHAVIOURAL rather than claimed as wired: no
test asserts that they have an effect, and the audit above is the reason.