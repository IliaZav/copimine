# CopiMine Bridge V4 Per-Sender Heartbeat Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task. The user approved the written specification and plan on 2026-10-16; implementation is authorized and in progress.

**Goal:** Add authenticated per-sender liveness to Bridge V4 so the client detects a Paper sender stream that silently stopped after readiness and recovers that sender through the existing targeted reopen protocol.

**Architecture:** Add signed control type 253 (`sender_heartbeat`) on each existing sender stream at a five-second cadence. Negotiate support with a signed optional HelloAck feature byte gated by the exact heartbeat-capable client version. Track local authenticated activity per client sender and call the existing targeted reopen path after 20 seconds. Reuse current stream counters and receipt type 254; add no client heartbeat request or acknowledgement.

**Tech Stack:** Java 25, Fabric client, Paper/Bukkit plugins, shared pure-Java bridge protocol, JUnit 5, Ed25519 `BridgeSigningPolicy`.

**Spec:** [2026-10-16 CopiMine Bridge V4 Per-Sender Heartbeat Design](../specs/2026-10-16-copimine-bridge-v4-per-sender-heartbeat-design.md)

## Global Constraints

- Preserve the existing pinned Ed25519 key ID, X.509 public key, canonical signing domain, V4 envelope format, nonce/session binding, receipt type 254, and exact body validation.
- Never read, print, copy, test-fixture, package, or commit the external private key. Use generated ephemeral key pairs in tests.
- Keep heartbeat state, readiness, cadence, and counter independent for End Event and Narcotics.
- Send type 253 only to exact heartbeat-capable `clientVersion` sessions and only after receipt type 254 makes the sender ready.
- A missing capability means legacy mode: no client timeout and no type 253 traffic.
- Do not add a heartbeat request, heartbeat acknowledgement, new request counter, retry race, or V3 fallback.
- Keep the 5-second server cadence and 20-second client timeout; use local monotonic elapsed time for the watchdog and epoch time for signed envelope validation.
- Do not touch Wave 7 implementation or tests in this feature.
- After each verified task, create a small checkpoint commit and push it to the already-authorized integration branch. Stage only this feature's source, tests, documentation, and regenerated lock metadata; exclude pre-existing dirty artifacts, Wave 7 files, runtime data, and secrets. Never force-push or merge to `main`.

## Review Focus

- The feature byte and heartbeat are covered by the exact existing V4 signature and binding checks.
- Old exact-version clients receive the legacy 36-byte HelloAck and never receive type 253; new clients accept old-server legacy HelloAck without starting a watchdog.
- Fresh/default configuration requires `0.1.3+26.3`; every existing saved exact version is preserved, including `0.1.2+26.3`, because its provenance is ambiguous.
- The first post-receipt server packet remains counter 4; heartbeats consume only the existing server stream counter.
- Replay, counter gaps, expiry, malformed bodies, stream changes, reconnects, and server switching cannot refresh or inherit liveness.
- A timeout or send failure clears/reopens only its named sender and continues to obey existing fail-closed signer behavior.

## Execution Status

- [x] Task 1: strict shared control codec (commits `6371b988`, `3a360897`; focused codec tests passed).
- [x] Task 2: client per-sender watchdog (commit `72f73d31`; focused and full client tests passed).
- [x] Task 3: Paper heartbeat production (commit `e13b22e2`; 62 focused and 86 full bridge-server tests passed; independent review approved; Codex Security reported zero findings).
- [ ] Task 4: server tickers and version/config defaults (focused tests and Paper builds passed; requested CodeRabbit and Codex Security reviews pending).
- [ ] Task 5: integrated builds, artifact lock, and runtime verification.

---

### Task 1: Extend the strict shared control codec

**Files:**
- Modify `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeControlBodyCodec.java`
- Modify `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeBodyCodecTest.java`
- Modify `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeControlBodyCodecNegativeTest.java`

1. Add a failing exact-fixture test for `SenderHeartbeat(sender, sessionUuid)` with the 19-byte schema/type/sender/session body.
2. Add failing HelloAck tests for the current 36-byte legacy fixture and the 37-byte extension with heartbeat bit. Assert legacy decode exposes no features; reject unknown flags and any non-exact length.
3. Run only the protocol codec tests and confirm the new cases fail because the type/extension is not implemented.
4. Add `SENDER_HEARTBEAT_TYPE=253`, a known heartbeat capability bit/mask, strict encode/decode, and an optional capability representation on HelloAck. Keep a legacy encoder/constructor that produces the original exact 36-byte form for old clients.
5. Add strict heartbeat and capability negative cases; rerun the codec tests and existing bridge-body fixture tests.
6. Commit and push this codec checkpoint after its focused tests pass.

### Task 2: Add per-sender client liveness and timeout recovery

**Files:**
- Modify `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocolV4.java`
- Modify `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeConnectionState.java` only if a connection-scoped reset hook is needed; prefer keeping sender liveness in `SenderFlow`.
- Modify `tools/minecraft-26.3/client/src/test/java/me/copimine/client/ClientBridgeProtocolV4Test.java`

1. Add failing tests for capability-enabled baseline at type-254 counter 3, no timeout before 20 seconds, targeted timeout at 20 seconds, and unaffected sibling sender state.
2. Add failing heartbeat tests for the expected next counter, same sender/session/stream binding, deadline reset, replay/duplicate not refreshing, counter gap reopening, and modified/expired/bad-signature/wrong-binding packets not refreshing.
3. Add a failing test proving valid application traffic refreshes liveness, and a legacy-HelloAck test that remains ready beyond 20 seconds without watchdog recovery.
4. Add a deterministic monotonic-time seam to `ClientBridgeProtocolV4.Transport` (with a production default based on `System.nanoTime`) and use it only for elapsed liveness. Continue using epoch milliseconds for `BridgeSigningPolicy` verification and signed expiry.
5. Record the negotiated HelloAck capability per `SenderFlow`; initialize that flow's activity baseline when the signed type-254 receipt is accepted. Update activity only after all envelope, session, sender, counter, expiry, and exact-body checks pass. Dispatch type 253 through strict control decoding. For a timed-out capable ready flow, call existing `expireAndReopen(sender, streamId)` unchanged.
6. Run the focused `ClientBridgeProtocolV4Test` and all codec tests. Confirm no change to client request counter sequencing and no cross-sender clearing.
7. Commit and push this client watchdog checkpoint after its focused tests pass.

### Task 3: Add heartbeat production to both Paper senders

**Files:**
- Modify `copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java`
- Modify `copimine-narcotics/src/me/copimine/clientbridge/NarcoticsBridgeV4Sender.java`
- Modify `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/endevent/EndEventBridgeV4SenderTest.java`
- Modify `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/clientbridge/NarcoticsBridgeV4SenderTest.java`

1. Add failing fake-clock tests that neither sender emits before READY, a negotiated READY stream emits at five seconds, heartbeat body names the correct sender/session, and counter 4 is the first post-receipt packet.
2. Add tests that legacy-version sessions never advertise or emit heartbeat; heartbeat tick does nothing for offline, invalidated, or non-ready players; each sender has independent cadence/counters.
3. Add tests for signer failure, ambiguous transport failure, and counter exhaustion retiring only that sender. Verify the resulting stream still accepts the existing targeted reopen fence.
4. Add a `tick` method per sender. Gate on current verified session, exact heartbeat-capable version, online player, and confirmed READY. Build the exact `SenderHeartbeat`, sign using existing envelope metadata and key ID, use the next existing stream counter, and advance only after successful dispatch. Reuse current retire/fail-closed behavior for failures.
5. Gate HelloAck feature-bit creation on the exact verified client version. Emit the legacy HelloAck body for every non-capable version; do not send type 253 to those sessions.
6. Run both sender test classes and the existing session-registry test. Verify counter 3 receipt and contiguous counter-4 onward behavior.
7. Commit and push this Paper sender checkpoint after its focused tests pass.

### Task 4: Wire server tickers and version/config defaults

**Files:**
- Modify `copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Access.java`
- Modify `copimine-end-event/src/me/copimine/endevent/EndEventBridgeV4Bootstrap.java` only if needed to expose the tick method through the existing boundary.
- Modify `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java`
- Modify `copimine-narcotics/src/me/copimine/narcotics/config/NarcoticsConfigService.java`
- Modify `copimine-narcotics/config.yml`
- Modify `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/ClientModHandshakePolicy.java`
- Modify `tools/minecraft-26.3/client/build.gradle`
- Modify `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/ClientModHandshakePolicyTest.java`
- Update `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/clientbridge/ClientModSessionRegistryServiceTest.java` to use the new exact supported version where appropriate.
- Update active current-version fixtures in `tests/test_minecraft_26_3_paper.py`, `tests/test_copimine_client_mob_artifact.py`, `tests/test_minecraft_migration_profile.py`, and `tests/test_minecraft_native_acceptance.py`; retain old-version fixtures that intentionally verify stale artifacts are rejected.
- Update current-version references in `docs/minecraft-26.3/README.md`, `docs/minecraft-26.3/MIGRATION_RUNBOOK.md`, `docs/minecraft-26.3/AUTH_AND_CLIENT_MOD_ENFORCEMENT.md`, and `CopiMineClient/README_INSTALL_RU.md`; leave dated migration history/evidence untouched.
- Update `tools/minecraft-26.3/profile.lock.json` from the reproducibly built JAR (actual filename, version, size, SHA-512) and then update current-version installer assertions.

1. Add a failing test for the fresh default `0.1.3+26.3`, and for preserving an existing saved `0.1.2+26.3` value and other exact versions across config reloads. Do not infer whether the old default value was operator-pinned.
2. Bump `ClientModHandshakePolicy.DEFAULT_REQUIRED_VERSION`, declare the heartbeat-capable exact version, and change the Fabric client Gradle project version to `0.1.3+26.3`.
3. Update the shipped `config.yml` default and the config service's missing-key fallback. Preserve every already-saved `client_bridge.required_client_mod_version` exactly, with no schema marker or automatic rewrite. A legacy `0.1.2+26.3` pin stays on the 36-byte HelloAck and receives no heartbeat; an operator explicitly sets `0.1.3+26.3` to enable the new feature. Continue the existing required-version challenge invalidation behavior when the loaded value changes.
4. Add a default no-op `tick()` to `EndEventBridgeV4Access`; implement it in the real sender boundary and call it from the already-running synchronous `CopiMineEndEvent.tick()` (every 5 server ticks). Keep the sender's own five-second deadline so the event tick does not increase packet rate.
5. Schedule `NarcoticsBridgeV4Sender.tick()` synchronously from `CopiMineClientBridge.register()` and cancel its `BukkitTask` in `shutdown()` before closing senders. Ensure reload/session invalidation is reflected before a heartbeat can be emitted.
6. Run handshake/config tests, session-registry tests, and both sender test classes. Build the client and migration Paper plugins with the repository's pinned Java 25.0.4.1 toolchain.
7. Commit and push this version/defaults/ticker checkpoint after its focused tests and builds pass.

### Task 5: Integration and final verification

**Files:**
- Update only Bridge V4 tests and implementation files from Tasks 1–4, the current-version build lock/tests/docs listed in Task 4, and no Wave 7 files.

1. Build the client with the pinned Java 25.0.4.1 toolchain and update `tools/minecraft-26.3/profile.lock.json` from that JAR's exact filename, version, size, and SHA-512. Run current-version Python profile/native-acceptance assertions, the full focused protocol/client suite, `bridgeServerTest`, and the existing Paper plugin build tasks used by the Minecraft 26.3 migration gate.
2. Review the final diff for exact legacy/extended HelloAck lengths, reserved-bit rejection, no key/secret changes, exact body signing, envelope TTL, monotonic client timeout, per-sender isolation, and no V3 mutation path.
3. Explicitly configure the existing Paper `required_client_mod_version` to `0.1.3+26.3`, provision the server with the external protected key file without displaying or copying its content, and connect a `0.1.3+26.3` client. Verify both signed feature bits and per-sender heartbeats, and keep both streams READY for more than 20 seconds.
4. Use deterministic fake-clock and sender transport tests to suppress one sender's heartbeat/application traffic while leaving the other sender live. Verify the client clears and reopens only the silent sender, the reopen names its retired stream, readiness is restored with a fresh stream, and the healthy sender's state remains intact.
5. Verify compatibility using an old server/new client case with exact required version configured to `0.1.3+26.3` (legacy HelloAck, no timeout), and a new server/older exact client case (legacy HelloAck, no emitted heartbeat). Record automated results separately from native Minecraft evidence.
6. Commit and push the final verified integration checkpoint; report the commit and integration branch URL to the parent.

## Completion gate

Implementation is complete only when focused tests, full client/server bridge checks, builds, and the specified runtime verification pass. Keep private key material external and report runtime verification separately from unit-test evidence. Push each verified checkpoint under the user's existing authorization. Never force-push, overwrite either development branch, merge to `main`, or include unrelated dirty files.
