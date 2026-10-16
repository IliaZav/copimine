# CopiMine Client Bridge v4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Replace unsigned server-to-client End Rift and Narcotics mutations with fail-closed Ed25519-signed v4 streams bound to the current trusted CopiMine client connection.

**Architecture:** Keep protocol-v3 only for the existing client-mod challenge/hello, capabilities, heartbeat, PING, and client-originated visual delivery reports. Put canonical v4 signing and binary records in the pure-Java protocol source. `CopiMineNarcotics` owns the verified-session registry, external private-key loader, and signing service; `CopiMineEndEvent` consumes these Bukkit services without trusting its legacy client hello. The Fabric client maintains independent End Event and Narcotics streams and drops all v3 server-to-client mutations.

**Tech Stack:** Java 25, Minecraft 26.3 Fabric client, Paper API, Java Ed25519 (`Signature`), Gradle, PowerShell local verification scripts.

**Spec:** `docs/superpowers/specs/2026-10-15-copimine-client-bridge-v4-design.md`

## Global Constraints

- Pin key ID `copimine-bridge-20261015-v1`, public X.509 key `MCowBQYDK2VwAyEAlud9gEV0EMTMJPDo5bQSDpd+giw+SD6/mpli1wr3l54=`, and SHA-256 fingerprint `2dadc8a92218471ad51721299974968c326e9ffd3b08ae8dd1e50ec7079dff62`.
- Read the server private key only from `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE`; never put private key material, encoded contents, or the path in source, logs, build output, resource packs, tests, or Git.
- Use only the v4 domain `copimine:client-bridge:v4` and the exact canonical signing bytes in the spec. Do not verify or send a v1 signature fallback.
- Accept protected server-to-client changes only on `copimine:client_bridge_v4` after signature, connection, sender stream, counter, expiry, and body validation.
- Preserve protocol-v3 version 3 for its challenge/hello/capabilities/heartbeat/PING flow and client-to-server visual reports; ignore v3 End Event mutations and v3 visual start/stop/clear unconditionally.
- End Event and Narcotics maintain independent streams and readiness. No protected feature uses the other sender's stream or readiness.
- Keep End Event visual state, Narcotics remote visuals, and local test visuals in separately owned stores; a Narcotics clear never affects either other store.
- Use generated ephemeral keys for tests. Actual Minecraft proof is recorded separately from protocol tests, plugin compilation, and the event gate.
- Do not read, print, copy, or commit the user's private signing-key file. Preserve unrelated dirty files and stage only reviewed bridge paths.
- The user approved the v4 architecture and directly authorized implementation on 2026-10-15. Implementation is isolated on `codex/client-bridge-v4`, based at verified commit `a703c921610b9291a9c47cfb2fb0a7a536a1cb2c`. The drafting worktree has extensive pre-existing dirty Wave/migration work; do not copy, stage, reset, or otherwise alter those unrelated files. Push only reviewed bridge commits to the dedicated branch; do not merge or deploy.

## Review Focus

- A stale signed packet arrives after reconnect or server switch: prove the new connection nonce and epoch reject it and queued work cannot apply.
- An unauthenticated or wrong-key packet claims a current stream: prove it cannot retire that stream or change state.
- A correctly signed packet has a skipped counter, expired queued timestamp, or malformed body: prove it is not dispatched and only its authenticated sender stream is retired where specified.
- A refreshed Narcotics visual uses `REPLACE_ALL_FULLSCREEN`: prove the fence does not clear that same visual instance, End Event visuals, or local test visuals.
- A v3 server sends any legacy visual or End Event mutation after upgrade: prove no protected client-state mutator is called.

## File Map

- `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeSigningPolicy.java` — v4-only Ed25519 signing input, signature verification, time bounds, and pinned key constants.
- `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeEnvelope.java` and `BridgeEnvelopeCodec.java` — immutable v4 envelope metadata and exact binary encode/decode.
- `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeRequestCodec.java`, `EndEventBridgeBodyCodec.java`, and `NarcoticsBridgeBodyCodec.java` — strict client request and typed signed body schemas.
- `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/ClientModSessionRegistry.java` and `BridgeSigner.java` — pure-Java Bukkit service contracts compiled into the Narcotics plugin and shared with End Event through its plugin dependency classloader.
- `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocol.java` and `BridgePayload.java` — retain the v3 handshake and client-originated reports; remove every v3 server mutation dispatch.
- `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocolV4.java` and `ClientBridgeConnectionState.java` — v4 channel registration, connection nonce/epoch, per-sender streams, strict validation, request outbox, and ordered main-thread queue.
- `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientVisualManager.java` — stream-owned visual-instance map and exact start/stop/clear semantics.
- `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventClientState.java`, `EndEventWorldVfxManager.java`, `EndEventPlayerVisualManager.java`, `EndEventBlackFogManager.java`, and `PrisonerHudController.java` — apply only validated typed End Event records and maintain context/generation ownership.
- `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java` and `ClientBridgePayloads.java` — preserve/validate v3 session handshake and responses; register v4 requests and publish verified session state.
- `copimine-narcotics/src/me/copimine/clientbridge/BridgePrivateKeyLoader.java`, `ClientModSessionRegistryService.java`, `NarcoticsBridgeV4Sender.java`, and `ClientVisualEffectService.java` — bounded external key loading, services, signed Narcotics streams, exact visual command correlation.
- `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java` and `copimine-narcotics/plugin.yml` — service and v4 channel lifecycle.
- `copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java`, `CopiMineEndEvent.java`, and `plugin.yml` — consume shared services; encode typed End Event records; validate v4 ability requests and readiness; remove trust in the legacy End Event hello.
- `copimine-end-event/build-plugin.ps1` and `tools/minecraft-26.3/build.gradle` — compile End Event against the Narcotics service API without bundling duplicate protocol classes.
- `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/` and `tools/minecraft-26.3/client/src/test/java/me/copimine/client/` — protocol, connection, replay, routing, and visual ownership tests.
- `tools/minecraft-26.3/server-bridge-tests/src/test/java/` and the `bridgeServerTest` task in `tools/minecraft-26.3/build.gradle` — focused Paper-side session, key-loader, and sender-state tests using temporary generated key files.
- `tests/ValidateClientBridgeV4FailClosed.ps1` and `tests/RunClientBridgeV4Integration.ps1` — source-level fallback guard and isolated Paper/client integration scenarios.

---

### Task 1: Canonical v4 protocol policy and binary codecs

**Files:**

- Modify: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeSigningPolicy.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeEnvelope.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeEnvelopeCodec.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeRequestCodec.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EndEventBridgeBodyCodec.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/NarcoticsBridgeBodyCodec.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeRequestCodecTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeSigningPolicyTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeEnvelopeCodecTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/protocol/BridgeBodyCodecTest.java`

**Interfaces:**

- `BridgeSigningPolicy.signV4(PrivateKey, BridgeEnvelope.Metadata, byte[]) -> byte[]` returns exactly 64 signature bytes.
- `BridgeSigningPolicy.verifyV4(PublicKey, BridgeEnvelope, long nowMillis) -> VerificationResult` validates the v4 domain, exact raw body, metadata, and freshness.
- `BridgeEnvelopeCodec.encode(BridgeEnvelope) -> byte[]` and `BridgeEnvelopeCodec.decode(byte[]) -> BridgeEnvelope` enforce exact lengths, no trailing bytes, and the 66,000-byte total envelope cap before allocation.
- Body codecs accept typed immutable records and reject any byte stream that does not consume exactly one record or violates a reserved/default field. Maintain one exact encoded fixture and reserved/default-field assertion for every supported End Event type in the spec, including Echo.

- [ ] **Step 1: Add failing v4 signature tests** for the pinned key ID, exact canonical input bytes, raw 64-byte signatures, v1-domain rejection, altered metadata/body, wrong key ID, bad time window, and exact expiry boundary.
- [ ] **Step 2: Run the focused client protocol tests** with the repository's Minecraft 26.3 client Gradle project and confirm the new tests fail for missing v4 behavior.
- [ ] **Step 3: Implement `BridgeSigningPolicy.signV4` and `verifyV4`** with the exact `copimine:client-bridge:v4` input; retain no v1 verification branch.
- [ ] **Step 4: Add failing envelope tests** for canonical nonce/stream Base64URL, all length boundaries, malformed/trailing bytes, body-size bounds, and exact envelope round-trip bytes.
- [ ] **Step 5: Implement `BridgeEnvelope` and `BridgeEnvelopeCodec`** with bounded parsing before allocation and raw signature storage.
- [ ] **Step 6: Add byte-exact request fixtures** for control_hello, prisoner_ability_request, stream_reopen_request, sender_ready, and sender_ready_confirm; add body fixtures for hello_ack, sender_ready_ack, every supported End Event type, each Narcotics type, unused-field defaults, UUID order, strings, bounds, allowlists, per-type Narcotics duration and zero-field rules, logical retransmission matching, preserved clear-fence retries, command-sequence exhaustion, Echo monotonicity/overflow, and body timestamp equality.
- [ ] **Step 7: Implement the request and typed-body codecs** using the v4 schemas in the spec; reject unknown identifiers rather than trimming, truncating, or substituting fallback values.
- [ ] **Step 8: Run focused protocol tests** and the entire client unit suite; record exact commands and results.
- [ ] **Step 9: Request an independent review of the protocol diff, fix confirmed findings, then commit and push only these tested protocol paths** to the dedicated branch.

### Task 2: Shared Paper session registry and signer services

**Files:**

- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/ClientModSessionRegistry.java`
- Create: `tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeSigner.java`
- Create: `copimine-narcotics/src/me/copimine/clientbridge/BridgePrivateKeyLoader.java`
- Create: `copimine-narcotics/src/me/copimine/clientbridge/ClientModSessionRegistryService.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java`
- Modify: `copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java`
- Modify: `copimine-narcotics/plugin.yml`
- Modify: `tools/minecraft-26.3/build.gradle` to register the focused `bridgeServerTest` source set/task with JUnit 5 and Narcotics/End Event compile outputs.
- Create: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/clientbridge/BridgePrivateKeyLoaderTest.java`
- Create: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/clientbridge/ClientModSessionRegistryServiceTest.java`

**Interfaces:**

- `ClientModSessionRegistry.current(UUID playerUuid) -> Optional<VerifiedClientSession>` returns the current challenge session, client version, and opaque per-connection epoch; `requestFreshChallenge(UUID playerUuid) -> void` invalidates the entry, notifies both sender services, and begins a fresh v3 challenge.
- `BridgeSigner.sign(String nonce, UUID recipient, String streamId, long counter, long issuedAtMillis, long expiresAtMillis, byte[] exactBody) -> Optional<byte[]>` signs with the one loaded key and exposes no `PrivateKey`.
- Narcotics is the sole owner and publisher of both Bukkit services. End Event uses `softdepend: [CopiMineNarcotics]` and compiles against its API without copying protocol classes into its JAR.

- [ ] **Step 1: Add failing registry-policy tests** for challenge accepted/rejected, epoch changes on reconnect, invalidation on quit/version change/rejection/shutdown, fresh-challenge requests after either bridge restarts, cross-sender invalidation notification before challenge dispatch, and registry publication only after the replacement v3 challenge response is verified.
- [ ] **Step 2: Implement the pure-Java service contracts and registry service**; publish a session only after the pending challenge response passes the existing handshake policy.
- [ ] **Step 3: Add failing key-loader tests** for 4,096-byte encoded-file limit, 512-byte decoded limit, ASCII Base64 with one optional terminal newline, empty/internal-whitespace/invalid Base64, missing/unreadable/malformed keys, mismatch with the pinned public key/fingerprint, and byte-array zeroing.
- [ ] **Step 4: Implement `BridgePrivateKeyLoader`** using a bounded read, sanitized generic errors, no fallback, and a signing challenge verified by the pinned public key before registering `BridgeSigner`.
- [ ] **Step 5: Register and unregister the services and v4 channel** in Narcotics startup/shutdown; on a bridge restart request a fresh v3 challenge for online players before v4 traffic resumes. Test that both sender services stop signing and processing requests immediately while `registry.current(player)` is absent or mismatched.
- [ ] **Step 6: Add `bridgeServerTest` wiring** in `tools/minecraft-26.3/build.gradle` so Paper-side tests compile against Narcotics output and its pinned Paper API without adding test classes to production jars.
- [ ] **Step 7: Run `gradle -p tools/minecraft-26.3 bridgeServerTest` and compile Narcotics**; confirm the verified-session registry remains available, signer calls return unavailable with a missing key, and no protected send occurs.
- [ ] **Step 8: Request a fresh independent agent review of this task's diff, fix accepted findings, then commit and push only its verified paths** to the already authorized dedicated branch.

### Task 3: Client connection lifecycle, strict v4 handshake, and v3 fallback removal

**Files:**

- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocolV4.java`
- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeConnectionState.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocol.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/BridgePayload.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/CopiMineClient.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/ClientBridgeConnectionStateTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/ClientBridgeProtocolV4Test.java`

**Interfaces:**

- `ClientBridgeConnectionState.open(UUID playerUuid) -> Connection` creates a never-reused local epoch and fresh nonce; `verifiedSessionChanged(UUID sessionId)` retires both prior-session streams, readiness, outstanding request IDs, counters, and queued mutations while retaining the connection nonce, local connection epoch, seen-stream-ID history, and event generation high-water marks; `close()` clears nonce, both senders, queues, request counters, stream history, and event generation fences.
- `ClientBridgeProtocolV4.onEnvelope(byte[] payload, UUID currentPlayer, long nowMillis)` validates signature and envelope before queuing a typed mutation.
- `ClientBridgeProtocolV4.sendControlHello()`, `sendSenderReady(Sender, byte[] exactAckEnvelope)`, `requestReopen(Sender)`, and `sendPrisonerAbility(...)` use one serialized per-sender outbox for counted requests. `sendReadyConfirm(Sender, byte[] exactReadyAckEnvelope)` queues the uncounted, idempotent receipt in that same outbox without allocating or advancing the request counter.

- [ ] **Step 1: Add failing lifecycle tests** for connection nonce generation/canonical format, join/disconnect/switch/player change cleanup, same-connection verified v3 session replacement and queue invalidation, queue epoch invalidation, and server switch replay rejection.
- [ ] **Step 2: Implement connection state and second-channel registration** while leaving the existing channel version at 3.
- [ ] **Step 3: Add failing handshake/recovery tests** for ACK request ID correlation, sender_ready counter/digest and ready-confirm digest/uncounted receipt semantics, lost or expired sender_ready_ack leaving server features disabled, duplicate hello/reopen/ready-confirm idempotence, dropped ready-confirm, a structurally valid ability request rejected while pending but consuming its counter, then ACK expiry and successful reopen at the next unused counter after all earlier counted requests, zero-length missing-ACK recovery, sender independence, and 256-stream exhaustion. Assert that a signed application message is sent only after the sender accepts ready_confirm.
- [ ] **Step 4: Implement client v4 handshake and per-sender state** with strict exact counters and the single serialized request outbox. Verify ready_ack, enqueue the exact ready_confirm before later requests, retry it byte-for-byte without consuming a request counter, and on expiry recover with a sender-specific reopen using the next unused request counter after all earlier counted requests; only then allow local sender mutations.
- [ ] **Step 5: Add failing v3 fallback and active-stream isolation tests** that send v3 End Event/visual mutations and wrong-key, wrong-nonce, or bad-signature traffic, then assert no protected state mutator is invoked and an established stream remains intact.
- [ ] **Step 6: Remove v3 server-to-client mutation dispatch** from `ClientBridgeProtocol`; retain the v3 challenge/hello/capabilities/heartbeat/PING and client-originated visual reports.
- [ ] **Step 7: Run focused protocol/session tests** and the Minecraft 26.3 client test suite.
- [ ] **Step 8: Request a fresh independent agent review of this task's diff, fix accepted findings, then commit and push only its verified paths** to the dedicated branch.

### Task 4: Client mutation isolation and visual instance handling

**Files:**

- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientVisualManager.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventClientState.java`
- Create: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientVisualCommandLedger.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventWorldVfxManager.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventPlayerVisualManager.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndEventBlackFogManager.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/PrisonerHudController.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EchoPresentationPacket.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EchoPresentationState.java`
- Modify: `tools/minecraft-26.3/client/src/main/java/me/copimine/client/EchoVanillaRenderer.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/ClientBridgeVisualOwnershipTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/EndEventBridgeContextTest.java`
- Test: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/EchoPresentationPacketTest.java`
- Modify: `tools/minecraft-26.3/client/src/test/java/me/copimine/client/EchoPresentationStateTest.java`

**Interfaces:**

- The v4 dispatcher calls one typed `apply(VerifiedBridgeMessage)` entry point after signature, sender readiness, ordering, expiry, and body validation; no manager accepts a raw plugin message.
- Narcotics remote visual entries are keyed by `(streamId, visualInstanceId)` and hold `effectId`, `commandSeq`, latest start counter, and runtime. End Event and local test visual managers remain separate.
- `ClientVisualCommandLedger` is keyed by recipient, local connection epoch, and verified client-mod session; it survives sender stream rotation, retains 256 accepted sequence receipts and the high-water mark, and is separate from server-side `ClientVisualCommandCorrelation`. Exact retained duplicates replay the original report tuple without side effects; modified retained duplicates retire only Narcotics; entries older than the retained window are rejected as stale without applying effects.
- `ClientVisualCommandCorrelation` is a separate server-side store keyed by recipient, verified session, active Narcotics stream, and `command_seq`, capped at 512 records per recipient/session. Exact retries stop on ACK, after 30 seconds, after 256 newer command sequences are issued, or when the sender stream retires. After retry expiry, reconcile with a newly constructed command and fresh sequence; never retry an old sequence that might have been evicted from the client ledger. Start lifecycle reports have a finite expiry; retired-stream/session records are cleared.
- The End Event context stores event UUID, generation, signed dimension/AABB, and per-connection generation high-water marks. Only the exact active context and bounds survive stream replacement; unbind clears both, a new event replaces both atomically, and only generation high-water marks survive unbind and event replacement.
- Echo remains the disposable presentation probe. Frame validation preserves long `swingSerial`/`hurtSerial` values, int-bounded equipment version, owner/observer routing, a global 32-active-actor cap, a 128-entry actor cache, and a separate 128 removed-before-bind ticket cap.

- [ ] **Step 1: Add failing context, ownership, and Echo tests** for context bind/unbind, AABB clearing and replacement, same-generation reassertion only after stream rotation, generation high-water retention, 64-event bound, checked inclusive-volume calculation, an elongated `32,768×1×8`-block arena accepted at exactly 262,144 blocks, an over-volume arena rejected with dependent mechanics disabled, both-endpoint arena checks using floor-to-inclusive-block bounds (including `max+0.5` accepted and `max+1.0` rejected), and disconnect reset. Echo coverage includes owner/observer delivery, invalid frames, decreasing and equal frame-sequence rejection (including `END_ECHO_REMOVE`), `Long.MAX_VALUE` frame/swing/hurt exhaustion, epoch allocator exhaustion at `Long.MAX_VALUE`, equipmentVersion overflow at `Integer.MAX_VALUE`, deathTicks above 20, and actor retirement before any counter wraps; retain the 32-active-actor, 128-entry, and 128 removed-before-bind caps.
- [ ] **Step 2: Implement typed context ownership and arena-bound world beams** including both endpoints, finite width 0.02..0.45, duration 1..2,000 ms, 64 active-beam and 256 instance-version caps, and the existing canonical dimension/key/coordinate grammar. Check inclusive spans and volume with overflow-safe widened arithmetic; reject context bind and keep dependent mechanics disabled on malformed, overflowing, or >262,144-block layouts. Change instance-version overflow to reject the new operation without clearing or evicting existing beams; test that the 65th active beam and 257th new instance/clear fence leave prior instances intact.
- [ ] **Step 3: Add failing visual-store and command-ledger tests** for exact instance stop, exact effect correlation, idempotent retransmit with a changed body timestamp but unchanged logical fields, rejection and Narcotics-stream retirement for the same retained command sequence with a changed type/body, exact duplicate `visual_stop` and `visual_clear_all` after their visual state was removed, stale duplicate rejection after receipt eviction, the 256-entry bound with high-water retention, refresh with new command sequence, stream replacement cleanup, clear-through fence preservation on retry, start duration minimum/maximum, zero stop/clear numeric fields, and clear-through fencing. Also test server correlation expiry/capacity, stopping retries after ACK or the 30-second/256-newer-command limit, no old-sequence retry after stream rotation, and reconciliation using a fresh sequence.
- [ ] **Step 4: Implement the Narcotics visual-instance store and `ClientVisualCommandLedger`** so replacement clears exclude the current refreshed ID and cannot clear End Event or local visuals; keep duplicate receipts independent of active visual state and replay the stored response without reapplying side effects.
- [ ] **Step 5: Add queue expiry and malformed-body tests** proving authenticated stream retirement and no later mutation dispatch; wrong-key, wrong-nonce, and bad-signature packets leave an active stream and its state untouched, while an authenticated gap or malformed body retires only its own sender stream.
- [ ] **Step 6: Route all existing End Event/prisoner/visual managers through typed verified messages only** and run the focused client tests plus the whole client suite.
- [ ] **Step 7: Request a fresh independent agent review of this task's diff, fix accepted findings, then commit and push only its verified paths** to the dedicated branch.

### Task 5: Narcotics v4 sender and response correlation

**Files:**

- Create: `copimine-narcotics/src/me/copimine/clientbridge/NarcoticsBridgeV4Sender.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/CopiMineClientBridge.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientBridgePayloads.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientVisualCommand.java`
- Create: `copimine-narcotics/src/me/copimine/clientbridge/ClientVisualCommandCorrelation.java`
- Modify: `copimine-narcotics/src/me/copimine/clientbridge/ClientVisualEffectService.java`
- Modify: `copimine-narcotics/src/me/copimine/visualruntime/VisualRuntimeService.java`
- Test: `tests/ValidateCopiMineClientBridgeV4Narcotics.ps1`
- Test: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/clientbridge/NarcoticsBridgeV4SenderTest.java`

**Interfaces:**

- `NarcoticsBridgeV4Sender.startStream(Player, VerifiedClientSession, String nonce, UUID helloId)` returns an independent stream and sends correlated signed counter-1 ACK. Before every send or accepted request it requires `registry.current(player)` to match the cached player UUID, session, and connection epoch; absence or mismatch retires the stream and blocks visuals.
- `NarcoticsBridgeV4Sender.sendVisual(Player, ClientVisualCommand, UUID visualInstanceId)` and `stopVisual(...)` allocate stream counters in their serialized player queue and send only v4 signed bodies.
- `ClientVisualEffectService` allocates one positive command sequence per player and verified session across start, refresh, stop, and clear-all. `ClientVisualCommandCorrelation` records player, session, active stream, sequence, command type, expected effect/instance, and accepted report kinds for every outstanding visual command; reports are matched against those fields plus bounded status/reason. The store is capped at 512 records per recipient/session, expires unacknowledged commands after 30 seconds and start lifecycle reports after body timestamp plus duration plus 30 seconds, and is cleared on stream/session retirement. Start ACKs accept `STARTED` and `STARTED_<route-token>`; stop and clear ACKs accept `STOPPED` and `CLEARED`, with an empty effect ID for clear. Reports never authorize game progression or damage.

- [ ] **Step 1: Add failing service tests** for trusted stream creation, missing signer, exact counters, hello/reopen/sender_ready/ready-confirm flow, uncounted confirm retry after later requests, dropped confirm followed by ACK expiry and successful reopen without counter desynchronization, late ready-confirm rejected at or after ACK expiry, command-sequence exhaustion at `Long.MAX_VALUE`, no visual send before exact confirmation, no send while registry session is absent/mismatched, duplicate request caching, stream rotation snapshot, and independent End Event state.
- [ ] **Step 2: Implement Narcotics per-player v4 stream state** and its signed hello/readiness/reopen messages using the shared signer.
- [ ] **Step 3: Add failing command-correlation checks** for positive and negative ACK/FINISHED/ERROR cases across start, refresh, stop, and clear-all: wrong player, session, sequence, command type, effect, status, duplicate, malformed route token, stale response, expiry, and bounded-store exhaustion. Assert valid client wire statuses correlate, including the empty clear effect ID; retries stop after ACK, the 30-second timeout, 256 newer command issues, or stream retirement, and desired state is reconciled with a fresh command sequence.
- [ ] **Step 4: Implement typed visual start/stop/clear sends** with a per-player/session sequence allocator shared by every command with fail-closed exhaustion at `Long.MAX_VALUE`, logical instance IDs, retries only within the active stream and bounded retry window, fresh command IDs for reconciliation, new refresh command IDs, 1,000..600,000 ms start duration, zero stop/clear numeric fields, 10,000 ms fade caps, preserved retry clear fences, bounded allowlists, and snapshots after stream replacement.
- [ ] **Step 5: Remove all server-side v3 visual start/stop/clear sends and reject invalid legacy response fields**; retain only the v3 client reports allowed by the spec.
- [ ] **Step 6: Run focused Narcotics guards and compile plugin outputs** with the client test suite.
- [ ] **Step 7: Request a fresh independent agent review of this task's diff, fix accepted findings, then commit and push only its verified paths** to the dedicated branch.

### Task 6: End Event v4 sender and protected mechanic gating

**Files:**

- Create: `copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/runtime/wave7/EchoPresentationProbe.java`
- Modify: `copimine-end-event/src/me/copimine/endevent/domain/wave7/EchoPresentationProbeState.java`
- Create: `copimine-end-event/src/me/copimine/endevent/domain/wave7/EchoPresentationEpochPolicy.java`
- Modify: `copimine-end-event/plugin.yml`
- Modify: `copimine-end-event/build-plugin.ps1`
- Modify: `tools/minecraft-26.3/build.gradle`
- Test: `tests/ValidateCopiMineEndEventBridgeV4.ps1`
- Test: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/endevent/EndEventBridgeV4SenderTest.java`
- Test: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/endevent/EchoPresentationProbeStateTest.java`
- Test: `tools/minecraft-26.3/server-bridge-tests/src/test/java/me/copimine/endevent/EchoPresentationEpochPolicyTest.java`

**Interfaces:**

- `EndEventBridgeV4Sender` owns End Event's hello, reopen, sender_ready, and ready-confirm receiver/state machine, request counter, per-player ordered signer outbox, and readiness gate. Before every send or accepted request it requires `registry.current(player)` to match the cached player UUID, session, and connection epoch; absence or mismatch retires the stream and disables dependent mechanics. On its own bridge restart it calls `requestFreshChallenge` for online players and waits for the verified replacement v3 session before starting new streams.
- `EndEventBridgeV4Sender.bindContext(Player, EventContext)` sends signed `CONTEXT_BIND` before event mutations; `unbindContext(...)` sends signed `CONTEXT_UNBIND` before clearing server event identity.
- `EndEventBridgeV4Sender.send(Player, EndEventMessage)` validates readiness and serializes exactly one typed End Event body; absent signer/registry/readiness means no protected send.
- Incoming prisoner requests use `BridgeRequestCodec`, current registry epoch/session, active sender stream readiness, event UUID/generation, and the existing permission/cooldown/target policies.

- [ ] **Step 1: Add failing sender and Echo state tests** for control_hello, correlated hello_ack, reopen and cached duplicate handling, sender_ready counter/digest and uncounted ready-confirm digest, readiness only after the exact unexpired confirm, dropped-confirm recovery after ACK expiry, rejection of a late confirmation at the exact expiry boundary, no send while registry session is absent/mismatched, and End Event restart requesting a fresh v3 challenge. In server-side Echo tests, initialize each frame counter at its maximum, prove the next sequence/swing/hurt/equipment increment retires the actor without wrap, and prove epoch allocation fails closed at `Long.MAX_VALUE`; client tests cover equal-sequence `END_ECHO_REMOVE` rejection.
- [ ] **Step 2: Implement the End Event listener and sender state machine** using shared signer/session services, exact v4 body codec, ordered per-player outbox, request counters, and readiness gate; bind every stream to the registry session epoch. Add checked Echo frame-counter increments and a monotonic overflow-safe epoch policy so an actor is retired before any counter wraps and no epoch is allocated beyond `Long.MAX_VALUE`.
- [ ] **Step 3: Add failing packet-order tests** for context bind before mutations, unbind before event reset, stream reassertion with only active-context bounds, and no protected feature start without End Event readiness.
- [ ] **Step 4: Implement typed context bind/unbind and protected sends**; preserve context only across stream replacement, clear bounds on unbind, and atomically replace them on a new event.
- [ ] **Step 5: Add failing inbound request tests** for stale nonce/session/epoch, sender mismatch, wrong event/generation, target absence/mismatch, duplicate/gap counters, and assertions that malformed records, wrong live bindings, requests routed to the other sender, and wrong or gapped counters do not advance the expected counter. Also cover uncounted ready-confirm gating, a valid ability request rejected while `READY_PENDING_CONFIRM` but still consuming its counter, a syntactically valid stale-stream request at the expected counter rejected after consumption followed by a successful current-stream reopen using the next counter, and rejected gameplay checks consuming their valid counter.
- [ ] **Step 6: Migrate all End Event mutations and prisoner ability requests** off v3. Remove `clientBridgeSessions` trust in the client-supplied hello and call the canonical registry instead.
- [ ] **Step 7: Add the soft dependency and build classpath wiring** so End Event uses the Narcotics service API without bundling duplicate protocol types.
- [ ] **Step 8: Run the End Event bridge source guards and compile the Paper plugins**.
- [ ] **Step 9: Request a fresh independent agent review of this task's diff, fix accepted findings, then commit and push only its verified paths** to the dedicated branch.

### Task 7: Full automated regression, security review, and integration harness

**Files:**

- Create: `tests/ValidateClientBridgeV4FailClosed.ps1`
- Create: `tests/RunClientBridgeV4Integration.ps1`
- Modify as needed: `tests/RunEndRiftEventChecks.ps1`
- Modify: protocol/client/Paper test files created by Tasks 1–6.

- [ ] **Step 1: Add end-to-end test cases** for trusted signer, missing signer, wrong key/key ID, modified body, expiry, reconnect, same-connection v3 session replacement, server switch, ACK replay, body replay, counter gap, ready-confirm retry/loss and late-confirm rejection at expiry, dropped-confirm then a pending-state ability rejection that consumes its counter, expiry, and successful next-counter reopen; stale-stream rejection with counter consumption followed by synchronized reopen, both senders stopping when registry session is absent/mismatched, active-stream survival under wrong-key/wrong-nonce/bad-signature traffic, authenticated malformed-body retirement of only its sender, stream replacement, v3 mutation fallback, wrong-key official client sending no readiness confirmation, forged readiness receipt not bypassing server authorization, visual ACK correlation, visual isolation, Echo bounds, and event generation fencing.
- [ ] **Step 2: Run `gradle -p tools/minecraft-26.3/client test`** and require all existing plus new client/protocol tests to pass.
- [ ] **Step 3: Run `gradle -p tools/minecraft-26.3 migrationPlugins`** and the repository Paper plugin build/CI scripts; require both Narcotics and End Event outputs to compile with one shared protocol API.
- [ ] **Step 4: After both Paper sender test suites exist, run `gradle -p tools/minecraft-26.3 bridgeServerTest` again** so the final run includes Narcotics and End Event sender tests.
- [ ] **Step 5: Run `tests/RunEndRiftEventChecks.ps1` without build-skipping flags** and the new bridge fail-closed/integration harness. Keep the existing End Rift gameplay gate as separate evidence from the bridge-specific scenarios.
- [ ] **Step 6: Run Codex Security deep scan/diff scan and CodeRabbit review against the final implementation diff; use the bundled Code Review diagnostic if its review interface is available.** Fix confirmed findings and repeat review on the final diff. Do not create a PR only to invoke diagnostics.
- [ ] **Step 7: Recheck the entire worktree and explicitly stage only reviewed implementation paths**; do not absorb the pre-existing dirty files outside this plan.
- [ ] **Step 8: Push the verified commit and confirm GitHub CI is green on that exact SHA**; do not merge or deploy.

### Task 8: Live Paper and Minecraft verification

**Files:**

- Create evidence under: `artifacts/client-bridge-v4/`
- Update: `docs/superpowers/reports/2026-10-15-copimine-client-bridge-v4-verification.md`

- [ ] **Step 1: Provision the Paper process with the external private-key path** through `COPIMINE_CLIENT_BRIDGE_PRIVATE_KEY_FILE`; verify effective ACLs on the key file and all parent directories without opening the file contents: the Paper service identity can read/traverse but cannot write, modify, delete, replace, or change ACLs; only SYSTEM and local Administrators may manage the secret; no untrusted principal has write/modify/delete/replace/ACL-change rights. Confirm the server reports only generic signer-ready state and never logs the path or key.
- [ ] **Step 2: Run an isolated Paper/client session with the matching pinned key** and capture evidence that both senders establish separate ready streams, End Event controls/visuals apply, and Narcotics visuals correlate to exact instances.
- [ ] **Step 3: Run isolated no-key and wrong-key sessions with the official client**. For a no-key server, verify the sender emits no protected signed controls/visuals and dependent mechanics stay disabled. For a wrong-key server, verify it may emit signatures but the official client rejects them without changing client state or sending ready-confirm, leaving dependent server mechanics gated. Record separately that a modified client can forge the unsigned receipt, so the guarantee is cooperative-client-only.
- [ ] **Step 4: Exercise actual reconnect and server switch** in Minecraft; verify old nonce/stream work is rejected and queued state is cleared.
- [ ] **Step 5: Capture actual in-game behavior and write the evidence report** separating protocol tests, plugin builds, server/client integration, and native Minecraft observation. If native access is blocked, report `nativeVerified=false` with the reproducible manual scenario.

## Delivery Gate

- The user explicitly authorized implementation after approving the architecture. The written spec and plan capture that approved design and direct the implementation on the isolated branch.
- After implementation, report in Russian with branch/HEAD, scoped files, tests/builds, CodeRabbit, Codex Security, Code Review availability, signer provisioning state without key details, live integration outcomes, native verification status, the cooperative-client readiness boundary, remaining blockers, and exact commit/push SHAs if authorized delivery is completed.
