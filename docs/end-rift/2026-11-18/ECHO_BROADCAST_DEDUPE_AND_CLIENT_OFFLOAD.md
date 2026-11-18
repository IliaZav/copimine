# Echo broadcast dedupe and client-side extrapolation

Date: 2026-11-18
Scope: Wave 7 personal Echo presentation transport (server send path + client state).
Requirement (user): "network/broadcast improved with more client-side processing" -
do not pay a signed packet for information the client can derive itself.

Status vocabulary:

* **IMPLEMENTED** - the code path exists in the shipped sources.
* **AUTOMATED-TESTED** - a gate test drives the real code.
* **AUDITED** - verified by reading the runtime path during this review.
* **NOT VERIFIED** - neither of the above.

## 1. What the transport used to cost

`tick()` is scheduled with `runTaskTimer(this, this::tick, 1L, 5L)` and advances
`eventTickCounter += 5L`, so the event tick runs four times per second.
`tickEchoPresentationProbe()` calls `tickEchoClones()`, which calls
`sendEchoCloneView(...)` once per clone per run, and
`EchoPresentationProbeState.nextFrame(...)` returns a frame on every call.

Before the dedupe, that meant **one signed packet per viewer per copy per run =
4 packets/s per viewer per copy**, whether or not anything a viewer can see had
changed. Each packet is an Ed25519 signature plus a plugin-message copy, so an
idle, crouching copy cost the same CPU as a fighting one, multiplied by the
number of nearby viewers.

## 2. What the client already derives

Two frame fields carry information the client can reconstruct locally:

* **Item-use elapsed.** `EchoPresentationState.view(...)` computes
  `useElapsed + (now - receivedAt) / 50` and clamps it to `useDuration`, so the
  client advances the eating/shield/bow clock between frames. A frame therefore
  does not need to be resent merely because the elapsed counter moved.
* **Position, gait and pose animation.** The copy is rendered through the
  vanilla player renderer: `EchoVanillaRenderer` extracts a render state from the
  bound carrier entity with the camera `partialTick`, and
  `driveVanillaAnimation` advances the vanilla walk/swing/hurt clocks per tick.
  Position interpolation is the carrier entity's own client interpolation, not
  something the echo frame must stream.

## 3. What the dedupe does

`EchoFrameBroadcastPolicy` (`domain/wave7`) decides one delivery per viewer:

* The **signature** is the frame's field list with two fields removed: the
  monotonic `sequence` (it changes every frame by definition) and the
  client-extrapolated `useElapsed`. Everything a viewer can see is kept: pose,
  sprint, hand, use duration, swing, hurt, death, equipment, owner, epoch.
* The **key is viewer AND actor**, so one copy can never suppress another copy a
  player is also watching.
* A repeated frame is re-sent at least every `KEEPALIVE_TICKS = 16` event ticks
  (0.8 s), so the client's `LEASE_MILLIS = 2_000` lease cannot lapse and the
  copy cannot silently disappear.
* A changed signature is never delayed: it is delivered on the run it happens.
* `record(...)` runs only after the transport accepted the frame, so a dropped
  packet is retried on the next run instead of being suppressed.
* An unknown field shape and a backwards clock both fail open (send), so a wire
  change degrades to "always send" rather than to silence.

## 4. Effect on the wire

| State of the copy | Before | After |
|---|---|---|
| Idle, nothing visible changes | 4 packets/s per viewer | at most 1.25 packets/s per viewer (keepalive) |
| Visible transition (pose, swing, hurt, death, equipment) | 4 packets/s per viewer | delivered on the transition run, then deduped |
| Eating / holding a shield | 4 packets/s per viewer | deduped; the client advances the clock |

The 1.25 packets/s figure is `20 / 16`: sixteen event ticks at twenty per second.
It is a ceiling for a truly idle copy, not an average, and it is only reached
while a viewer stays within the 64-block broadcast radius.

## 5. Tests

`tests/EchoFrameBroadcastPolicyTest.java` (gate-registered) drives the policy
directly: unchanged signatures are suppressed, a changed signature is sent
immediately, the keepalive boundary re-sends, the viewer+actor key keeps two
copies independent, `forget`/`clear` release memory, and an unexpected field
shape fails open.

## 6. Honest status

| Claim | Status |
|---|---|
| An unchanged frame is not re-sent before the keepalive | AUTOMATED-TESTED |
| A changed frame is sent on the transition run | AUTOMATED-TESTED |
| Two copies watched by one viewer stay independent | AUTOMATED-TESTED |
| A dropped packet is retried rather than suppressed | AUTOMATED-TESTED |
| The client extrapolates the item-use clock between frames | IMPLEMENTED (`EchoPresentationState.view`) |
| The copy's position/gait come from the vanilla renderer | IMPLEMENTED (`EchoVanillaRenderer`) |
| The measured packet-rate drop on a live server | NOT VERIFIED (needs a live encounter and a packet capture) |
| The copy still looks continuous at 2/5/10/20 players | NOT VERIFIED (needs a real client) |

Nothing above is a live or visual pass. The reduction is stated as a ceiling
derived from the scheduler cadence and the keepalive constant, which are both in
the shipped sources.