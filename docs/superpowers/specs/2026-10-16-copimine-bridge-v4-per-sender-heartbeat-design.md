# CopiMine Bridge V4 Per-Sender Heartbeat Design

**Status:** Approved for implementation; native runtime verification pending

**Date:** 2026-10-16

**Base protocol:** [CopiMine Client Bridge v4 design](2026-10-15-copimine-client-bridge-v4-design.md)

## Goal

Detect and recover a sender stream that the Paper side has retired after an ambiguous post-ready send while the client still considers that sender `READY`. Add authenticated, per-sender liveness without changing gameplay packets, readiness semantics, or the V4 client-request counter.

## Approved decisions

- Each Paper sender emits a signed, one-way `sender_heartbeat` on its existing V4 stream every 5 seconds while that stream is `READY` and bound to the current verified client session.
- The client retires only the silent sender after 20 seconds without a newly accepted authenticated packet for that sender, clears that sender's local state, and sends the existing targeted `stream_reopen_request` naming the retired stream.
- Heartbeats use the existing per-sender envelope counter. There is no heartbeat request, heartbeat acknowledgement, or additional request counter.
- The existing signed type-254 `sender_ready_confirm_ack` remains server counter 3. The first post-ready application packet or heartbeat is server counter 4.
- Capability is negotiated by a signed HelloAck feature bit. The feature bit is sent only to a verified session whose exact client version is the heartbeat-capable release.
- Client version advances from `0.1.2+26.3` to `0.1.3+26.3`, and the default required client version advances for new configurations.
- Existing saved `required_client_mod_version` values are never auto-migrated. An existing `0.1.2+26.3` may be an intentional exact-version pin and cannot be distinguished from a value that came from the previous default. It remains on legacy HelloAck behavior until an operator explicitly changes it to `0.1.3+26.3`.
- The Ed25519 trust model is unchanged: the client verifies the pinned CopiMine key ID/key, exact signed body, recipient UUID, connection nonce, sender stream, counter, and timestamp window before accepting heartbeat activity. No V3 mutation fallback is introduced.

## Wire format

### HelloAck heartbeat capability

The current HelloAck body is exactly 36 bytes:

```text
u8 schema=1
u8 type=0
u8 sender                 // 1=end_event, 2=narcotics
uuid session_uuid[16]
u8 request_kind           // 0=initial hello, 1=targeted reopen
byte request_id[16]
```

The extended form appends one byte:

```text
u8 feature_flags           // bit 0=sender heartbeat; all other bits reserved and rejected
```

A strict decoder accepts exactly 36 bytes as the legacy form with no negotiated features, or exactly 37 bytes as the extended form. It rejects every other size and unknown feature bit. A four-argument legacy HelloAck encoder remains available so a new server can send the old 36-byte form to an older exact-version client. New heartbeat-capable sessions receive the extended form with bit 0 set. The extension is covered by the existing Ed25519 signature as part of the exact body.

### Sender heartbeat

The heartbeat body is exactly 19 bytes:

```text
u8 schema=1
u8 type=253
u8 sender                 // 1=end_event, 2=narcotics
uuid session_uuid[16]
```

The existing signed envelope carries the pinned key ID, client nonce, recipient/player UUID, the same sender stream ID, the next sender counter, `issued_at_ms`, `expires_at_ms`, and the Ed25519 signature over the exact heartbeat body. Use the sender's existing 30-second envelope TTL; the global V4 signing policy still caps validity at 60 seconds.

The heartbeat body has no event ID, generation, payload, or client request ID. It proves only that this trusted sender can still produce fresh authenticated traffic for this player/session/stream. It never applies, clears, or refreshes gameplay state by itself.

## Negotiation and compatibility

- A new server advertises the heartbeat bit and emits type 253 only when `VerifiedClientSession.clientVersion()` exactly equals `0.1.3+26.3`. An operator-configured exact version for an older release remains on the legacy HelloAck and receives no heartbeat. The server must never send type 253 to a client version that does not advertise support.
- A new client accepts both the legacy 36-byte and extended 37-byte signed HelloAck. It enables the watchdog only if the signed extended form has the heartbeat bit. Missing bit means legacy behavior with no heartbeat timeout or reopen loop.
- An older server sends its existing 36-byte HelloAck. A new client therefore stays in legacy mode. To accept the new `0.1.3+26.3` client, an older server's exact `required_client_mod_version` must be configured to that exact value; the old server still sends no heartbeat and the client does not expect one.
- New configurations default to `0.1.3+26.3`. An existing saved exact version is preserved byte-for-byte, including `0.1.2+26.3`; do not infer operator intent from a value equal to the old default. To enable the heartbeat-capable client on an existing server, an operator explicitly updates the configured exact version to `0.1.3+26.3`. The protocol remains V4; the client version change does not alter the pinned key or signing domain.

## Liveness state and transitions

Liveness is tracked independently in each existing `SenderFlow`:

1. On a verified HelloAck, record the negotiated heartbeat capability for that stream. A capability-free stream has no liveness deadline.
2. On a verified type-254 receipt at counter 3, mark the sender ready as today and set the enabled stream's local authenticated-activity baseline. The baseline uses local elapsed time, never the signed remote `issued_at_ms` value.
3. On each subsequent, newly accepted server envelope, advance the existing counter and refresh liveness only after envelope signature/freshness, key ID, connection nonce, recipient/player UUID, active session, sender/stream binding, exact body parsing, and session/body checks all pass. A well-formed application envelope for the active stream can refresh transport liveness even if its event generation is already stale and its gameplay mutation is consequently ignored.
4. A duplicate or replayed counter is discarded and never refreshes activity. A gap, malformed body, wrong sender/session/stream, expired or bad-signature envelope follows the existing retire-and-targeted-reopen path and never refreshes activity.
5. On each client tick, if a negotiated ready sender has no accepted activity for 20,000 ms, call the existing `expireAndReopen(sender, streamId)`. This retires and clears only that sender, preserves the End Event context only for its normal reassertion path, and sends the existing counted reopen naming the retired 22-character stream ID.
6. A successful reopen installs a fresh stream and renegotiates capability through its signed HelloAck. A connection close, server switch, player change, or verified-session change clears all per-sender liveness state with the existing connection/stream reset.

Use a local monotonic elapsed-time source for the 20-second watchdog. Keep envelope freshness on epoch milliseconds. This prevents wall-clock adjustments from extending a ready state indefinitely or causing a spurious liveness timeout. If the current `Transport` seam is used, expose an injectable monotonic source so tests can advance it deterministically.

## Paper sender behavior

Both `EndEventBridgeV4Sender` and `NarcoticsBridgeV4Sender` add a main-thread `tick` entry point. For each online player with a current verified session, heartbeat-capable exact client version, and confirmed `READY` stream, sign and dispatch type 253 at the next 5-second deadline. Store the heartbeat deadline per stream; do not share cadence, readiness, stream ID, or counters between senders.

Allocate each heartbeat as `serverCounter + 1` (End Event) or the current `nextEnvelopeCounter` (Narcotics), then advance the counter only after signing and dispatch succeed. Any signer failure, counter exhaustion, or ambiguous dispatch failure retires that sender's stream using existing fail-closed behavior. The client then recovers with its existing targeted reopen. Do not resume or reuse the failed stream counter.

The End Event plugin's existing synchronous `CopiMineEndEvent.tick()` task runs every 5 ticks. Call the optional `EndEventBridgeV4Access.tick()` there; the sender's deadline gate sends only every 5 seconds. The Narcotics client bridge owns its lifecycle, so schedule a synchronous sender tick from `CopiMineClientBridge.register()` and cancel it from `shutdown()` before closing the sender. Both tickers must stop on plugin shutdown and must not send for offline, invalidated, unverified, non-ready, or legacy-capability sessions.

## Security and failure behavior

- Type 253 is a normal V4 signed envelope under the pinned key and existing `copimine:client-bridge:v4` domain; key ID, signature, exact body, nonce, recipient, stream, counter, and timestamp policy remain mandatory.
- Heartbeat capability is inside the signed HelloAck, so an unauthenticated server cannot enable watchdog behavior or suppress it for a trusted stream.
- Do not update liveness on receipt before full authentication or based on envelope timestamps supplied by the server.
- A heartbeat does not make a retired server stream current. Any post-retirement type 253 from its old stream is rejected by existing stream binding.
- Missing signer material remains fail-closed. A sender that cannot sign a due heartbeat retires the stream; there is no unsigned or V3 fallback.
- Keep the timeout per sender. A healthy Narcotics stream must not keep a silent End Event stream ready, or vice versa.

## Test requirements

### Shared protocol and compatibility

- Exact fixtures for legacy and extended HelloAck, including capability bit round trip.
- Reject unsupported feature bits, truncated capability, extra trailing bytes, bad schema/type/sender, malformed/truncated heartbeat, and trailing heartbeat bytes.
- Decode a legacy HelloAck as feature-free; preserve the current exact legacy fixture.

### Client

- Negotiated sender stays ready before 20 seconds and reopens at exactly 20 seconds without authenticated activity.
- A fresh valid heartbeat resets only its sender's deadline; valid application traffic also refreshes it.
- Heartbeat is accepted only at the next stream counter with exact sender/session and valid envelope binding; duplicate/replay does not refresh; counter gap retires only that stream.
- Wrong signature/key ID, nonce, player UUID, session, stream, sender, expiry, modified body, or trailing body data never refreshes liveness.
- Timeout uses one existing counted `stream_reopen_request` naming the retired stream and does not add a request or acknowledgement type.
- Legacy HelloAck remains ready beyond 20 seconds without watchdog reopen.
- Reconnect/server switch/session replacement clears heartbeat state and cannot inherit prior activity.

### Paper senders and integration

- Heartbeat is absent until type-254 readiness and is absent for unadvertised/legacy exact client versions.
- For a heartbeat-capable ready stream, type 254 remains server counter 3 and the first subsequent application or heartbeat uses counter 4; each later signed message is contiguous.
- Each sender emits on its own 5-second cadence, to its own stream, and includes its own sender ID and the current verified session UUID.
- Offline/non-ready/invalidated/legacy sessions produce no heartbeat. Ambiguous send or signing failure retires only the affected sender and supports its existing targeted reopen fence.
- Verify fresh/default configuration uses `0.1.3+26.3`, an existing saved value of `0.1.2+26.3` remains unchanged, and heartbeat is advertised only for exact version `0.1.3+26.3`.
- Verify the End Event tick hook and Narcotics task are synchronous and cancelled/closed on shutdown.
- Run the focused codec, client V4, End Event sender, Narcotics sender, handshake/config tests and the existing Minecraft 26.3 plugin/client builds. Then exercise one current client against the configured Paper server for more than 20 seconds, verify both negotiated READY streams remain stable, and fault-inject/observe one stream timeout to confirm sender-local clear and targeted reopen. Do not inspect or log the private signing key during runtime verification.

## Client-version release metadata

The `0.1.3+26.3` version bump changes the client artifact identity. Update the active `tools/minecraft-26.3/profile.lock.json` entry from the reproducibly built JAR's actual filename, version, size, and SHA-512, then update active installer/profile/native-acceptance tests and current installation docs. Preserve historical migration evidence and fixtures that intentionally represent rejected stale artifacts. Do not claim the lock update until the new artifact has been built and its bytes measured.
