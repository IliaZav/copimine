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

### 2.7 A restored copy is no longer frozen until the tick counter catches up

`RealitySplitEchoDuelState.ActorState` persists seven **absolute** event ticks:
`nextActionTick`, `meleeCooldownUntilTick`, `rangedCooldownUntilTick`,
`pendingUseUntilTick`, `shieldActiveUntilTick`, `shieldCooldownUntilTick`,
`jumpCooldownUntilTick`. `CopiMineEndEvent.eventTickCounter` is the opposite kind
of value: it is declared with no initialiser, its only mutation in the whole
plugin is `eventTickCounter += 5L` in `tick()` (every five server ticks, so it
advances 20 per second), it is never written to `EventSnapshot` or to
`objectiveProgressSnapshot()`, and it therefore **starts at zero on every
boot**.

The recovery path copied the saved deadlines into the re-registered checkpoint
verbatim (`ensureNamedEchoDuelActor`, the branch that rebuilds an existing duel
rather than planning a new one). After a restart the Pillager is gone, so
`found == null` and a brand-new carrier is spawned — and immediately told, with
absolute numbers, that it may not act yet. `EchoDuelCombatPolicy.ready(...)`
requires `tick >= nextActionTick` and `tick >= pendingUseUntilTick`, so a duel
persisted about forty minutes into the event (deadlines around 50 000) left the
copy unable to take **any** action for about forty minutes of the new boot. The
pending-use deadline had the same effect on a heal the copy had already paid
for, and `synchronizeNamedEchoShield` reads `shieldActiveUntilTick >
eventTickCounter`, so a saved shield window also stayed "live" for the whole
stale span. Movement is not gated, which is what the failure looked like in
play: a copy that walks towards its owner and never attacks.

This is not a hypothetical path. `namedWave7RuntimeRestorePending` is set when a
persisted snapshot is loaded (`restoreWave7Checkpoint`) and stays true until the
first `ensureNamedRealitySplitTrialRoom` pass has rebuilt the actors, so every
restart with a live Wave 7 record reaches it — and a plugin reload reaches it
with the carrier entity still present, which is why the rebase is applied in the
actor-rebuild branch and not only when the entity is missing.

The codebase already has exactly one answer to this problem, and the Echo duel
was the only persisted tick-based state that did not use it:
`Wave4ObeliskRecoveryPolicy.restoreTiming(currentEventTick, receipt)` exists to
"re-base saved relative timers onto the fresh event tick counter after restart",
and the Wave 7 named-trial checkpoints store millis rather than ticks for the
same reason.

`EchoDuelCombatRuntimeAdapter.rebaseRestoredDeadlines(actor, counter)` applies
that rule. Every deadline is written as `actionTick + duration` and every
duration comes from the bounded `Cooldowns` record, so a saved value can never
exceed `saveTick + duration`; clamping each deadline to `counter + duration` is
therefore an upper bound on the real remaining time. It keeps a cooldown that was
still running, forbids inventing a long one, and is **provably a no-op for a
deadline saved earlier on the same boot** (`saved <= now + duration` always
holds there), so the same-boot re-register path is unchanged. The shield window
is clamped after its own cooldown, which keeps the
`shieldActiveUntilTick <= shieldCooldownUntilTick` invariant of the record.

Nothing about the persisted encoding changed: the duel record keeps its thirteen
colon-separated actor fields, so no migration and no schema bump is involved, and
no damage, heal, cooldown, admission or durability rule was touched.

### 2.8 A wounded copy that eats finally heals

`EchoDuelCombatPolicy` heals a copy only when a consumable use **finishes**
(`completeConsumable`, introduced on 2026-11-01 in `570cf021`, "heal only on
completion"). Ending the heal at the start was deliberate: `tryStartConsumable`
spends exactly one supply and stages `pendingUseUntilTick` with **no** health
change, so a player who disconnects inside the eating window cannot be healed
twice.

The runtime never learned either half of that change. `570cf021` touched
`EchoDuelCombatPolicy`, `EchoBehaviorStrategy`, `EchoLoadoutState` and two test
files — `CopiMineEndEvent.java` is not in that commit, and
`git log -S "completeConsumable" -- copimine-end-event/.../CopiMineEndEvent.java`
returns nothing, so the plugin had no completion handler at all. Both ends of
the contract were therefore broken at once:

1. **The start was refused.** `checkpointNamedEchoHealthChanges` runs before the
   duel tick on every pass and copies the actor's health into the carrier body,
   so when the eat begins `nextDuel().actor().currentHealth()` equals
   `carrier.getHealth()` exactly. The `CONSUMABLE` branch of
   `executeNamedEchoCombatAction` required a *strict* increase
   (`expectedHealth <= carrier.getHealth()` → `return false`) and rejected every
   legitimate eat start. The rejection was then refunded by
   `compensateRejectedNamedEchoCombatAction`, so the supply came back and the
   copy chose the same eat again on the next tick: a permanent loop that also
   wrote two durable saves per five-tick pass (one for the accepted checkpoint,
   one for the refund).
2. **The completion was dropped.** The finishing tick reports
   `Action.NONE` — the supply was already spent when the use started, so there
   is no second item action to execute — and `commitNamedEchoCombatAction`
   short-circuited `Action.NONE` before touching any checkpoint. The healed
   actor never reached `registerPersonalEchoDuel`, so even a start that had been
   accepted would have healed nothing.

Together those two facts meant a wounded copy carrying food could never recover:
the eat was refused forever, and the one tick that carried the heal was
discarded. The fix keeps the policy untouched and repairs the runtime:

- `commitNamedEchoCombatAction` no longer swallows `Action.NONE`; it routes the
  tick to `commitNamedEchoStateChange`.
- `commitNamedEchoStateChange` is the completion checkpoint. An idle tick
  (`next.equals(prior)`) stays a no-op, so the ordinary path costs nothing. A
  real state change is validated as an exact successor of the stored record
  (same event, attempt and runtime generation, `revision() + 1`, actor uuid
  equal to the carrier) and is then registered, persisted and finally projected
  onto the body. The heal only ever raises the carrier and only up to its
  maximum; a body that refuses it is logged and **never** rolls the completed use
  back, because the checkpoint is the authority and the supply was already paid
  for.
- The `CONSUMABLE` start guard now rejects only what is genuinely wrong — a
  non-finite or above-maximum value — and applies the health change when there is
  one. The staged `Double.compare` check stays, because an accepted start must
  still prove the body agrees with the checkpoint it is about to commit.

The potion branch was audited in the same pass and is **not** a defect: a drunk
buff potion is deliberately an animation with no health transition ("drinking is
an animation, not a heal"), and it was equally affected by the dropped
completion, so nothing about it needed a separate change.

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
| Sprint hop no longer pre-empts attacks | AUTOMATED-TESTED | `EchoDuelCombatRuntimeAdapterTest.sprintHeavyCopiesAttackBeforeTheyHop` (legal swing wins, hop survives when nothing is reachable) |
| Shielded copy keeps moving | AUTOMATED-TESTED | `EchoDuelCombatRuntimeAdapterTest.profileDrivenDefenseSchedulesOwnerBoundShieldActions` asserts `hold.intent() == APPROACH` while the parry window is live |
| Gate green | AUTOMATED-TESTED | `tests\RunEndRiftEventChecks.ps1 -JavaHome <pinned Temurin 25.0.4.1>` exit 0; `EchoDuelCombatRuntimeAdapterTest OK assertions=88`, `EchoWoundedRetreatTest OK` |
| Candidate pin matches the build | AUTOMATED-TESTED | `tools/minecraft-26.3/server-plugins.lock.json` candidate `ab239007…` / 2574011 B equals a fresh pinned-JDK build and the `expected_candidates` literals; tests/test_minecraft_migration_server_plugins.py 135 passed, 6 skipped |
| The completed eat reaches the body | AUTOMATED-TESTED | `EchoDuelCombatRuntimeAdapterTest`: the food decision stages `8.0` health with `pendingUseUntilTick=332`, the resolution tick is `Action.NONE`, and the deadline tick carries `CONSUMABLE_HEAL_FRACTION` of maximum health once; `EchoConsumableTimingTest` asserts the same pair on the policy |
| The runtime wires that completion | AUTOMATED-TESTED | `tests/test_wave7_echo_consumable_completion.py`: `Action.NONE` routes to `commitNamedEchoStateChange`, the handler orders `registerPersonalEchoDuel(next)` < `saveStateSync()` < `carrier.setHealth(restored)`, an idle tick stays a no-op, and the eat start no longer demands a health increase while still rejecting non-finite and above-maximum values |
| Eat start is no longer refused-and-refunded | IMPLEMENTED | `executeNamedEchoCombatAction` `CONSUMABLE` branch: guard is `!Double.isFinite(expectedHealth) || expectedHealth > carrier.getMaxHealth()`, health applied only when it actually rises |
| Restored copy re-bases its persisted deadlines | AUTOMATED-TESTED | `EchoDuelCombatRuntimeAdapterTest.restoredDuelDeadlinesRebaseOntoFreshCounter`: a record saved with deadline 50 000 is frozen (`NONE`) at tick 0 **and** at tick 1 000, every re-based deadline equals its own `Cooldowns` bound (10/20/40/0/20/100/60), a re-based copy fires `RANGED` at tick 100, and a same-boot deadline set is returned unchanged (`equals` on the whole `ActorState`, so no running cooldown is shortened) |
| The re-base is actually reached on restart | AUTOMATED-TESTED | `tests/test_wave7_echo_restart_rebase.py`: `CopiMineEndEvent.ensureNamedEchoDuelActor` passes `duel.actor()` through `rebaseRestoredDeadlines(..., eventTickCounter)`, the rebuilt checkpoint is built from `restoredActor.*`, no `duel.actor().<deadline>` survives in that method, and the agent clamp uses `Math.min(savedDeadline, latest)` |
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