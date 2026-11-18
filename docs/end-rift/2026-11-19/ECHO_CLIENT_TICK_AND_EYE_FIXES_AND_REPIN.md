# Echo client: per-tick animation clocks, serial baseline and body drain

Date: 2026-11-19
Scope: the 26.3 Echo presentation client (`tools/minecraft-26.3/client`):
frame-dependent vanilla animation, a spurious swing/hurt on a freshly bound
copy, and a body-drain pass that was skipped while the eyes were still dark.
Spec: `COPIMINE_RIFT_GUARDIAN_KAGUNE_CANONICAL.md` (Wave 7 personal Echo) and
`docs/end-rift/2026-11-11/MASTER_REBUILD_CONTRACT.md`.

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a test drives the real decision code.
* **LIVE-PAPER-VERIFIED** - exercised on a live Paper server.
* **VISUAL-VERIFIED** - confirmed on a real client.
* **NOT VERIFIED** - neither of the above.

## 1. The defects and their repairs

### 1.1 The vanilla gait and hurt clocks advanced once per frame

`EchoVanillaRenderer.driveVanillaAnimation` was called from both the client
tick and the render pass, and it called `walkAnimation.update` plus
`hurtTime--` unconditionally. A second frame in the same game tick therefore
advanced the copy's walk cycle and hurt flash again, so the gait cadence and
the flash length depended on FPS (60 vs 144 Hz).

The new `EchoAnimationTickGuard` records the last advanced tick and collapses
all calls for one tick into a single advance. The renderer reads it in
`beginTick`, passes the result to `EchoPlayerView.project` and
`driveVanillaAnimation`, and gait is owned solely by the view's vanilla
`calculateEntityAnimation` under that per-tick flag. The raw per-frame
`walkAnimation.update` call is gone.

### 1.2 A newly bound copy swung and flashed hurt on its first frame

`CachedView` initialised `lastSwingSerial`/`lastHurtSerial` to
`Long.MIN_VALUE`, while the server's `EchoPresentationProbeState` starts both
serials at `0`. The first processed frame therefore looked like a new swing
and a new hurt receipt, producing a spurious arm swing and red flash on
appearance.

The guard now adopts the bind frame's serials as the baseline (`prime`) and
only fires on a strictly larger serial. `prime` is idempotent, so a real
receipt delivered after bind is still shown exactly once.

### 1.3 The skin was not darkened until the eyes lit up

`EchoEyesFeature.submit` returned early while `glow <= 0.02F`, before the
body-darkening `submitModel`. A fresh copy therefore appeared in its
undarkened skin for roughly the first 450 ms, even though the drain is meant
to be present from the first frame.

The eye/body decision now comes from `EchoEyePassPlan`: `BODY` is
unconditional and `EYES` joins it above the threshold. The layer submits the
body pass whenever the plan includes it, independent of the glow ramp.

## 2. Evidence

| Claim | Status |
|---|---|
| Gait and the hurt countdown advance once per game tick, not per frame | AUTOMATED-TESTED (`tests/test_echo_animation_tick_guard.py` drives the real guard: repeated same-tick calls collapse, the next tick advances) |
| A fresh copy does not replay serial zero as a swing or hurt | AUTOMATED-TESTED (`tests/test_echo_animation_tick_guard.py`: zero/non-zero bind baselines fire nothing; later receipts fire once) |
| The darkened body pass survives the whole eye-glow ramp | AUTOMATED-TESTED (`tests/test_echo_eye_pass_plan.py` drives `EchoEyePassPlan` from `glow = 0` to `1`) |
| The renderer keeps one gait owner and gates the hurt decay | AUTOMATED-TESTED (`tests/test_end_rift_echo_player_animation_contract.py`) |
| The eye layer keeps the tested pass order and no early body skip | AUTOMATED-TESTED (`tests/test_end_rift_echo_player_animation_contract.py`, `tests/test_end_rift_echo_eye_presentation.py`) |
| Client builds with the pinned JDK 25.0.4.1 and Gradle 9.8.1 | AUTOMATED-TESTED: `gradle test jar`, `BUILD SUCCESSFUL` |
| Client JUnit suite | AUTOMATED-TESTED: passed as part of `test jar` |
| Gait at 60 and 144 FPS looks identical on screen | NOT VERIFIED - needs a real client and an FPS measurement |
| Swing/hurt timing and the darkened body read correctly on screen | NOT VERIFIED - needs a real client |
| 2/5/10/11/20-player behaviour | NOT VERIFIED - no players online |

## 3. Re-pin

Only client sources moved, so the client artifact was rebuilt with the pinned
JDK and re-pinned:

* previous: `30249251` bytes,
  `f7e6c42b2259d21803cb39007a702f03869f2f0b3dcacf13435fbdc1ad2d67d48c03429e3443bd04becd9accf5f439f80f5923643014f6484ad0646bc0373b6f`;
* current: `30250423` bytes,
  `96947fa52c0faaa6b41a894e2fc04c3ccf6442c0e69be624589f07e4d501004638c5af337c8617f4309f648046f5aaecc5d10277b26a535caae56db51344c3ad`.

`tools/minecraft-26.3/profile.lock.json`, the literal in
`tests/test_minecraft_migration_profile.py` and the value in
`docs/minecraft-26.3/README.md` carry the same new size and digest.

No `copimine-end-event` source changed in this batch, so the plugin candidate
stays `2582610` /
`987f31a77bc33764eab5bb5d9d3750d38ac1ba98a8207c4d55f970cfcf7770dc`.
`copimine-end-event/CopiMineEndEvent.jar` is a separate, git-ignored local
runtime copy and is not a pin target.

## 4. Scope note

`CopiMineClient/` is the older tracked client tree. This batch changes only the
shipped 26.3 client under `tools/minecraft-26.3/client`; the legacy tree was
left untouched and is not part of the 26.3 artifact.
