# End Rift — V5.1 combat acceptance matrix (Archmage / Warden / Guardian / Crushing Gravity)

Date: 2026-11-23
Branch: `codex/end-rift-live-playtest-repair`
Authority: `CopiMine_End_Rift_COMPLETE_MASTER_V5_1_2026-11-23.md`
(`sha256 2a53217ac8bbd0354a443749f9be7c53af15fb2c04d32614bd28ffc608608fd7`),
last section **OWNER-RATIFIED V5.1 INTEGRATION LOCK** (§§ V5.1-A … V5.1-H).

This document is the implementation ledger the V5 policy classes point at
(`ArchmageV5CombatPolicy`, `WardenV5CombatPolicy`, `GuardianV5MeleePolicy`,
`CrushingGravityPolicy`). It records, per V5.1 decision, **where it lives**, the
**exact test source and command** and the **evidence tier**. It never upgrades an
automated result to a native/visual one.

Evidence tiers (never conflated):

- `SOURCE` — the behaviour exists in source, traceable to a class/method.
- `AUTO` — covered by an automated test that passed in this session.
- `PAPER-LIVE` — verified against a running Paper server (log/rcon). Not available
  in this environment.
- `NATIVE_VISUAL` — verified by the owner in a real **signed** client. Cannot be
  produced here (`NATIVE_BLOCKED`).
- `PROD` — verified on the live production host. Not performed here.
- `BLOCKED` — the check could not be performed. Never a pass.

---

## A. Scope index and conflict resolution (V5.1-A)

| Subject | Final authority (implemented) | Explicitly rejected (must not run) |
|---|---|---|
| Wave 7 Archmage | Vex support + Circle + 3-block Fang Line + 5-arm rotating Crown + Staff Repulse + existing ranged/step | Large unannounced all-arena Fang Line; unbounded Vexes; permanent invulnerability/teleport; static five-line particle drawing |
| Wave 7 Warden | Real vanilla Warden + 5×4 Sweep (20-tick stun / 60-tick Slow) + wall-piercing radius-6 Echo Space Explosion + counterable Sculk Resonance | Synthetic AI replacing native combat; Personal-Echo behaviour reuse; double native Sonic Boom on the custom burst |
| Rift Guardian | Grounded melee-first in phases 1–5 and after cocoon fall; four melee moves; ≤2 spell follow-ups after Separation; rare arena-wide Crushing Gravity | Magic-only idle loops; 9-second punches; 360° Fan; uninterrupted CC; Gravity during intact cocoon |
| Last Seal | Hovering cocoon casts only; fall returns to grounded melee; original square restored | Generic 3–6 s override; permanent geometry damage |
| Art/verification | Original authored 3D VFX, authoritative Paper hitboxes, staged audio | Recolor/flat-ring substituted for a finished effect; fabricated native proof |

Every older requirement that collides with this table is marked **SUPERSEDED** in
section B (ID reconciliation) rather than silently running both mechanics.

---

## B. Old-ID → final-action reconciliation (no duplicated handlers)

### B.1 Archmage (`ArchmageV5CombatPolicy.action`)

| Legacy id | Final action | Disposition |
|---|---|---|
| `ARCHMAGE_VEX` / `ARCHMAGE_VEX_SUPPORT` | `VEX_SUPPORT` | Retained; owner is `Wave7ArchmageVexPolicy` |
| `ARCHMAGE_FANGS` / `ARCHMAGE_FANGS_GROUP` | `FANG_LINE` | **SUPERSEDED** as a wide all-party line; specialised to the short local 3-block lane |
| `ARCHMAGE_CIRCLE` | `CIRCLE_OF_FANGS` | New |
| `ARCHMAGE_CROWN` | `CROWN_OF_FANGS` | New |
| `ARCHMAGE_REPULSE` | `STAFF_REPULSE` | New |
| `ARCHMAGE_STEP` | `REPOSITION` | Retained (validated floor destination) |
| `ARCHMAGE_BEAM` / `ARCHMAGE_SPEAR` | `RANGED_BEAT` | Retained as legal ranged beats |

### B.2 Warden (`WardenV5CombatPolicy.action`)

| Legacy id | Final action | Disposition |
|---|---|---|
| `WARDEN_WIDE_STRIKE` (`WARDEN_SWEEP_PATTERN = 1`) | `FRONTAL_SWEEP` | Retargeted in place to the 5×4 committed sweep |
| `WARDEN_SLAM` (`WARDEN_SLAM_PATTERN = 3`) | `ECHO_SPACE_EXPLOSION` | Retargeted in place to the radius-6 wall-piercing burst |
| `WARDEN_SCULK` (`WARDEN_TRACE_PATTERN = 4`) | `SCULK_RESONANCE` | New pattern id |
| `WARDEN_NARROW_STRIKE` / `WARDEN_SONIC_LUNGE` | `NATIVE` | Preserved native behaviour and line strikes |

### B.3 Guardian (`GuardianV5MeleePolicy`)

`BASIC_LEFT`, `BASIC_RIGHT`, `FAN`, `HEAVY`, `SEPARATION` are the physical moves;
`Branch.ONE_RANGED_BEAT`, `TWO_SPELLS`, `IMMEDIATE_RUSH` are the post-Separation
follow-ups. No V4 major spell is removed; the physical scheduler is gated by
`GuardianV5MeleePolicy.meleeAllowed(phase, cocoonIntact)`.

---

## C. Runtime ownership map (action → policy → runtime method)

| Ability | Policy constants | Runtime owner (`CopiMineEndEvent`) |
|---|---|---|
| Archmage Vex | `Wave7ArchmageVexPolicy` (unchanged) | existing Vex summon path (2/4/6 caps, ~200 ticks) |
| Archmage Fang Line | `ArchmageV5CombatPolicy.fangLineCells/fangLineOffsets` | `spawnFangLine` |
| Archmage Circle | `circleJaws/hasTraversableGap` | `spawnNamedTrialFangCircle` |
| Archmage Crown | `crownSpokeAnglesDegrees/crownArmPoints/crownMustFinish` | `beginNamedTrialFangCrown` |
| Archmage Repulse | `repulseRadius/repulsePush/repulseWarranted` | `resolveNamedTrialRepulse` |
| Warden Sweep | `sweepHits/SWEEP_PREP_TICKS` | `resolveWardenFrontalSweep` → `applyWardenSweepStunAndSlow` |
| Warden Echo burst | `echoHits/ECHO_RADIUS_BLOCKS` | `finishWardenSlam` |
| Warden Sculk trace | `maySelect/tracePolyline/traceHits` | `resolveWardenSculkTrace` |
| Guardian melee | `basicMove/fanHits/heavyHits/separationHits/punishWindowTicks` | `resolveGuardianCommittedMove`, `renderGuardianMoveTelegraph` |
| Crushing Gravity | `phaseEligible/mayCast/beat/nextVerticalVelocity` | `startCrushingGravity`, `tickCrushingGravity`, `endCrushingGravity`, `applyCrushingGravityPull`, `releaseCrushingGravityPull` |

`resetGuardianV5State()` and `endCrushingGravityStateOnly()` are the cancel/restore
entry points used on death, logout, phase change and restart.

---

## D. V5.1-G evidence matrix (test id → repo source → tier)

All pure-policy tests are registered in `tests\RunEndRiftEventChecks.ps1`
(`$pureTests`, `tests/ArchmageV5CombatPolicyTest.java`,
`WardenV5CombatPolicyTest.java`, `GuardianV5MeleePolicyTest.java`,
`CrushingGravityPolicyTest.java`). The runtime probes run in the gate step
**“V5.1 Wave 7 Archmage, Warden, Guardian and Crushing Gravity runtime probes”**.

Runtime probe command:

```
python -m pytest -q \
  tests/test_wave7_named_trial_warning_audio_runtime.py \
  tests/test_wave7_archmage_obstruction_runtime.py \
  tests/test_wave7_archmage_occluded_target_runtime.py \
  tests/test_guardian_melee_runtime.py \
  tests/test_warden_v5_runtime.py \
  tests/test_crushing_gravity_runtime.py
```

| V5.1-G test id | Behaviour asserted | Repo test source | Tier |
|---|---|---|---|
| `V51_ARCHMAGE_VEX_CAP_AND_LIFETIME` | one Vex per ~200-tick window under caps 2/4/6; no phantom on refusal | `Wave7ArchmageVexPolicy` + `tests/Wave7ArchmageVexPolicyTest.java`, `tests/Wave7ArchmageSwarmPolicyTest.java` (both in `$pureTests`) | `SOURCE` + `AUTO` |
| `V51_ARCHMAGE_FANG_LINE_TARGETED_3` | 3-block local lane from real offsets; sidestep survives | `tests/test_wave7_archmage_obstruction_runtime.py`, `tests/test_wave7_archmage_occluded_target_runtime.py`, `ArchmageV5CombatPolicyTest` | `SOURCE` + `AUTO` |
| `V51_ARCHMAGE_CIRCLE_OPEN_GAP` | 7–9 jaws with a real ≥2-block traversable gap | `ArchmageV5CombatPolicyTest` (`hasTraversableGap`, gap scan) | `SOURCE` + `AUTO` |
| `V51_ARCHMAGE_FIVE_ROTATING_SPOKES` | exactly 5 curved arms, ≤30 s hard finish, deterministic rotation | `ArchmageV5CombatPolicyTest` | `SOURCE` + `AUTO` |
| `V51_ARCHMAGE_MELEE_DEFENCE` | warned 3.5–4.5 radius repulse on cooldown, crowd-gated | `ArchmageV5CombatPolicyTest` (`repulseWarranted/repulseHits`) | `SOURCE` + `AUTO` |
| `V51_WARDEN_VANILLA_UNBROKEN` | native Warden entity/AI/sonic kept; custom layer subordinate | `WardenV5CombatPolicyTest` (non-replacement) | `SOURCE` (+ native `NATIVE_BLOCKED`) |
| `V51_WARDEN_SWEEP_CONTACT_5X4` | one hit / 20-tick stun / 60-tick Slow only on accepted damage; rear/outside/cover unaffected | `tests/test_warden_v5_runtime.py`, `WardenV5CombatPolicyTest` | `SOURCE` + `AUTO` |
| `V51_WARDEN_ECHO_R6_THROUGH_WALL` | radius-6 sphere, wall-piercing, single custom hit (no native double) | `tests/test_warden_v5_runtime.py` (`finishWardenSlam`), `WardenV5CombatPolicyTest` | `SOURCE` + `AUTO` |
| `V51_WARDEN_SCULK_TRACE_COUNTERPLAY` | sneak suppresses pre-lock; perpendicular dodge after lock survives | `tests/test_warden_v5_runtime.py`, `WardenV5CombatPolicyTest` | `SOURCE` + `AUTO` |
| `V51_WARDEN_CC_FAIRNESS` | stun cannot feed straight into the burst (`SWEEP_TO_ECHO_GAP_TICKS`) | `WardenV5CombatPolicyTest` (`mayFollowSweepWithEcho`) | `SOURCE` + `AUTO` |
| `V51_GUARDIAN_BASIC_FAN_HEAVY` | alternating basic, frozen 120–130° Fan, commit/miss Heavy | `tests/test_guardian_melee_runtime.py`, `GuardianV5MeleePolicyTest` | `SOURCE` + `AUTO` |
| `V51_GUARDIAN_SEPARATE_THEN_REENGAGE` | Separation + branches 1/2/3 with ≤2 beats; refusal never silences melee | `tests/test_guardian_melee_runtime.py`, `GuardianV5MeleePolicyTest` | `SOURCE` + `AUTO` |
| `V51_GRAVITY_REAL_PHYSICS_AND_SCOPE` | real player pull and real arrow velocity change inside arena bounds only | `tests/test_crushing_gravity_runtime.py`, `CrushingGravityPolicyTest` | `SOURCE` + `AUTO` |
| `V51_GRAVITY_HAZARD_COMPATIBILITY` | coordinated hazard veto; never during cocoon | `CrushingGravityPolicyTest` (`mayCast/escapesRemain/phaseEligible`) | `SOURCE` + `AUTO` |
| `V51_GRAVITY_RESTORE_AND_ACCESSIBILITY` | full state restore on death/quit/restart/phase/leave-bounds | `tests/test_crushing_gravity_runtime.py`, `CrushingGravityPolicyTest` | `SOURCE` + `AUTO` (reduced-motion UI = native) |
| `V51_V4_NO_REGRESSION` | entire B-01…B-14, E/R/G/A/V, wave 1→7, Ram 4+1, memorial, portal/WorldCore | full `tests/` suite + `tests\RunEndRiftEventChecks.ps1` | `SOURCE` + `AUTO` |

Behavioral results are `AUTO`; every **visual** column of V5.1-G (front/back/
left/right/overhead/first-person/close-up strips, real arrows falling, camera
shake, FPS/TPS) is `NATIVE_BLOCKED` here — see `NATIVE_F2_VISUAL_CHECKLIST.md`.

---

## E. Honest limits

- `NATIVE_VISUAL` and `PROD` are **BLOCKED** in this environment: no GUI, no
  running Paper/Postgres, no signed V4 client, no live players. The owner-facing
  procedure remains `docs/end-rift/2026-11-23/NATIVE_F2_VISUAL_CHECKLIST.md`.
- Automated green does **not** prove native visuals (objective §2/§8 of
  `APPROVED_WAVE_CONTRACT.md`). No item above is upgraded past its tier.
- Timing/damage constants are the V5.1 initial design targets and are expected to
  be tuned by real playtesting without changing the required identity.
