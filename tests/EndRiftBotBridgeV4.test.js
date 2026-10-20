'use strict'

const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const { EventEmitter } = require('node:events')
const test = require('node:test')
const {
  CONTROL_CHANNEL,
  attachLocalClientModHandshake,
  createBridgeV4TestClient,
} = require('./EndRiftBotClientModHandshake')

const KEY_ID = 'test-copimine-bridge-v4'
const PLAYER = '2a000000-0000-4000-8000-000000000001'
const SESSION = '2a000000-0000-4000-8000-000000000002'
const STREAM = crypto.randomBytes(16).toString('base64url')

function uuidBytes(value) {
  return Buffer.from(value.replaceAll('-', ''), 'hex')
}

function int32(value) {
  const bytes = Buffer.alloc(4)
  bytes.writeInt32BE(value)
  return bytes
}

function int64(value) {
  const bytes = Buffer.alloc(8)
  bytes.writeBigInt64BE(BigInt(value))
  return bytes
}

function lengthPrefixed(bytes) {
  return Buffer.concat([int32(bytes.length), bytes])
}

function signingInput({ keyId, clientNonce, playerUuid, streamId, counter, issuedAt, expiresAt, body }) {
  return Buffer.concat([
    lengthPrefixed(Buffer.from('copimine:client-bridge:v4', 'ascii')),
    int32(1), Buffer.from([4]),
    lengthPrefixed(Buffer.from(keyId, 'ascii')),
    lengthPrefixed(Buffer.from(clientNonce, 'ascii')),
    uuidBytes(playerUuid),
    lengthPrefixed(Buffer.from(streamId, 'ascii')),
    int64(counter), int64(issuedAt), int64(expiresAt),
    lengthPrefixed(body),
  ])
}

function signEnvelope({ privateKey, keyId = KEY_ID, clientNonce, recipient = PLAYER,
  streamId = STREAM, counter, body, now = Date.now(), issuedAt = now, expiresAt = issuedAt + 30_000 }) {
  const key = Buffer.from(keyId, 'ascii')
  const nonce = Buffer.from(clientNonce, 'ascii')
  const recipientBytes = uuidBytes(recipient)
  const stream = Buffer.from(streamId, 'ascii')
  const signature = crypto.sign(null, signingInput({
    keyId, clientNonce, playerUuid: recipient, streamId, counter, issuedAt, expiresAt, body,
  }), privateKey)
  return Buffer.concat([
    Buffer.from([4, key.length]), key,
    Buffer.from([nonce.length]), nonce,
    recipientBytes,
    Buffer.from([stream.length]), stream,
    int64(counter), int64(issuedAt), int64(expiresAt), int32(body.length), body, signature,
  ])
}

function decodeRequest(encoded) {
  assert.ok(Buffer.isBuffer(encoded))
  assert.equal(encoded[0], 4)
  const type = encoded[1]
  const nonceLength = encoded[2]
  const nonceStart = 3
  const playerStart = nonceStart + nonceLength
  const sessionStart = playerStart + 16
  return {
    type,
    nonce: encoded.toString('ascii', nonceStart, playerStart),
    player: encoded.subarray(playerStart, sessionStart).toString('hex'),
    session: encoded.subarray(sessionStart, sessionStart + 16).toString('hex'),
    bodyOffset: sessionStart + 16,
    bytes: encoded,
  }
}

function helloAck(sessionUuid, helloId, sender = 1, featureFlags = 1) {
  return Buffer.concat([
    Buffer.from([1, 0, sender]), uuidBytes(sessionUuid), Buffer.from([0]), helloId,
    ...(featureFlags === 0 ? [] : [Buffer.from([featureFlags])]),
  ])
}

function readyAck(sessionUuid, requestId, counter, sender = 1) {
  return Buffer.concat([
    Buffer.from([1, 255, sender]), uuidBytes(sessionUuid), requestId, int64(counter),
  ])
}

function readyConfirmAck(sessionUuid, requestId, counter, requestDigest, sender = 1) {
  return Buffer.concat([
    Buffer.from([1, 254, sender]), uuidBytes(sessionUuid), requestId, int64(counter), requestDigest,
  ])
}

function heartbeat(sessionUuid, sender = 1) {
  return Buffer.concat([Buffer.from([1, 253, sender]), uuidBytes(sessionUuid)])
}

function setup(clientNonce = crypto.randomBytes(32).toString('base64url')) {
  const keys = crypto.generateKeyPairSync('ed25519')
  const publicKeyDer = keys.publicKey.export({ format: 'der', type: 'spki' })
  const client = createBridgeV4TestClient({
    playerUuid: PLAYER,
    clientNonce,
    trust: { keyId: KEY_ID, publicKeyDer },
  })
  const hello = client.beginVerifiedSession(SESSION)
  const helloRequest = decodeRequest(hello)
  const helloId = hello.subarray(helloRequest.bodyOffset)
  return { ...keys, publicKeyDer, client, clientNonce, hello, helloId }
}

test('local test client registers the legacy and V4 channels together', () => {
  const bot = new EventEmitter()
  bot.username = 'BridgeProbe'
  bot.uuid = PLAYER
  bot._client = new EventEmitter()
  const registrations = []
  bot._client.registerChannel = (name, _parser, custom) => {
    if (custom) bot._client.writeChannel('minecraft:register', [name])
  }
  bot._client.writeChannel = (channel, names) => registrations.push({ channel, names })
  bot._client.write = () => {}

  attachLocalClientModHandshake(bot, {
    host: '127.0.0.1',
    port: 25566,
    version: '0.1.3+26.3',
  })
  bot.emit('login')

  assert.deepEqual(registrations, [{
    channel: 'minecraft:register',
    names: ['copimine:client_bridge', CONTROL_CHANNEL],
  }])
})

test('V4 test client reaches ready only through the three signed sender receipts', () => {
  const state = setup()
  const now = Date.now()
  const ack = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    counter: 1,
    body: helloAck(SESSION, state.helloId),
    now,
  })
  const readyResult = state.client.handleEnvelope(ack, now)
  assert.equal(readyResult.accepted, true)
  assert.equal(readyResult.ready, false)
  const ready = decodeRequest(readyResult.response)
  assert.equal(ready.type, 3)
  assert.equal(ready.bytes.readBigInt64BE(ready.bodyOffset), 1n)
  const streamLengthOffset = ready.bodyOffset + 8 + 1
  const streamLength = ready.bytes[streamLengthOffset]
  const streamId = ready.bytes.toString('ascii', streamLengthOffset + 1,
    streamLengthOffset + 1 + streamLength)
  const readyRequestIdOffset = streamLengthOffset + 1 + streamLength
  const readyRequestId = ready.bytes.subarray(readyRequestIdOffset, readyRequestIdOffset + 16)
  const readyAckEnvelope = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    streamId,
    counter: 2,
    body: readyAck(SESSION, readyRequestId, 1),
    now: now + 1,
  })
  const confirmResult = state.client.handleEnvelope(readyAckEnvelope, now + 1)
  assert.equal(confirmResult.accepted, true)
  assert.equal(confirmResult.ready, false)
  const confirm = decodeRequest(confirmResult.response)
  assert.equal(confirm.type, 4)
  assert.equal(confirm.bytes.readBigInt64BE(confirm.bodyOffset + 1 + 1 + streamLength + 16), 1n)
  const confirmAckEnvelope = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    streamId,
    counter: 3,
    body: readyConfirmAck(SESSION, readyRequestId, 1, crypto.createHash('sha256').update(confirm.bytes).digest()),
    now: now + 2,
  })
  const confirmed = state.client.handleEnvelope(confirmAckEnvelope, now + 2)
  assert.equal(confirmed.accepted, true)
  assert.equal(confirmed.ready, true)
  const next = state.client.handleEnvelope(signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    streamId,
    counter: 4,
    body: heartbeat(SESSION),
    now: now + 3,
  }), now + 3)
  assert.equal(next.accepted, true)
  assert.equal(next.ready, true)
})

test('V4 test client retries the exact ready and confirmation requests after rate limiting', () => {
  const state = setup()
  const now = Date.now()
  const helloAckEnvelope = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    counter: 1,
    body: helloAck(SESSION, state.helloId),
    now,
  })
  const readyResult = state.client.handleEnvelope(helloAckEnvelope, now)
  const readyRequest = readyResult.response
  assert.deepEqual(state.client.tick(now + 999), [], 'first retry waits one second')
  const readyRetry = state.client.tick(now + 1_000)
  assert.equal(readyRetry.length, 1)
  assert.deepEqual(readyRetry[0].response, readyRequest, 'retry bytes and counter are unchanged')

  const requestIdOffset = 3 + 43 + 16 + 16 + 8 + 1 + 1 + 22
  const requestId = readyRequest.subarray(requestIdOffset, requestIdOffset + 16)
  const streamOffset = 3 + 43 + 16 + 16 + 8 + 1 + 1
  const streamId = readyRequest.toString('ascii', streamOffset, streamOffset + 22)
  const readyAckEnvelope = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    streamId,
    counter: 2,
    body: readyAck(SESSION, requestId, 1),
    now: now + 1_001,
  })
  const confirmResult = state.client.handleEnvelope(readyAckEnvelope, now + 1_001)
  const confirmRequest = confirmResult.response
  assert.deepEqual(state.client.tick(now + 2_000), [], 'confirmation retry also waits one second')
  const confirmRetry = state.client.tick(now + 2_001)
  assert.equal(confirmRetry.length, 1)
  assert.deepEqual(confirmRetry[0].response, confirmRequest, 'confirmation retry uses the exact same bytes')

  const duplicateReadyAck = state.client.handleEnvelope(readyAckEnvelope, now + 2_002)
  assert.equal(duplicateReadyAck.accepted, true)
  assert.deepEqual(duplicateReadyAck.response, confirmRequest,
    'a replayed ready acknowledgment resends the original confirm')

  const confirmAckEnvelope = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    streamId,
    counter: 3,
    body: readyConfirmAck(SESSION, requestId, 1,
      crypto.createHash('sha256').update(confirmRequest).digest()),
    now: now + 2_003,
  })
  assert.equal(state.client.handleEnvelope(confirmAckEnvelope, now + 2_003).ready, true)
  assert.deepEqual(state.client.tick(now + 12_003), [], 'completed streams stop retrying')
})

test('V4 test client tracks both signed sender streams and negotiated heartbeats independently', () => {
  const state = setup()
  const now = Date.now()
  const senderStreams = new Map()

  for (const sender of [2, 1]) {
    const streamId = crypto.randomBytes(16).toString('base64url')
    const ackEnvelope = signEnvelope({
      privateKey: state.privateKey,
      clientNonce: state.clientNonce,
      streamId,
      counter: 1,
      body: helloAck(SESSION, state.helloId, sender, 1),
      now,
    })
    const readyResult = state.client.handleEnvelope(ackEnvelope, now)
    assert.equal(readyResult.accepted, true, `sender ${sender} hello ack is accepted`)
    const ready = decodeRequest(readyResult.response)
    assert.equal(ready.type, 3)
    assert.equal(ready.bytes[ready.bodyOffset + 8], sender)
    const requestIdOffset = ready.bodyOffset + 8 + 1 + 1 + 22
    const requestId = ready.bytes.subarray(requestIdOffset, requestIdOffset + 16)

    const readyAckEnvelope = signEnvelope({
      privateKey: state.privateKey,
      clientNonce: state.clientNonce,
      streamId,
      counter: 2,
      body: readyAck(SESSION, requestId, 1, sender),
      now: now + 1,
    })
    const confirmResult = state.client.handleEnvelope(readyAckEnvelope, now + 1)
    assert.equal(confirmResult.accepted, true, `sender ${sender} ready ack is accepted`)
    const confirm = decodeRequest(confirmResult.response)
    assert.equal(confirm.type, 4)
    assert.equal(confirm.bytes[confirm.bodyOffset], sender)
    senderStreams.set(sender, { streamId, requestId, confirm: confirm.bytes })

    const confirmAckEnvelope = signEnvelope({
      privateKey: state.privateKey,
      clientNonce: state.clientNonce,
      streamId,
      counter: 3,
      body: readyConfirmAck(SESSION, requestId, 1,
        crypto.createHash('sha256').update(confirm.bytes).digest(), sender),
      now: now + 2,
    })
    const confirmed = state.client.handleEnvelope(confirmAckEnvelope, now + 2)
    assert.equal(confirmed.accepted, true, `sender ${sender} confirmation is accepted`)
    assert.equal(confirmed.ready, sender === 1,
      'End Event readiness is exposed independently from Narcotics readiness')
    assert.equal(state.client.isReadyFor(sender), true)
  }

  for (const sender of [2, 1]) {
    const senderState = senderStreams.get(sender)
    const heartbeatEnvelope = signEnvelope({
      privateKey: state.privateKey,
      clientNonce: state.clientNonce,
      streamId: senderState.streamId,
      counter: 4,
      body: heartbeat(SESSION, sender),
      now: now + 3,
    })
    assert.equal(state.client.handleEnvelope(heartbeatEnvelope, now + 3).accepted, true)
  }
})

test('V4 test client rejects forged signatures and modified signed bodies', () => {
  const state = setup()
  const attacker = crypto.generateKeyPairSync('ed25519')
  const now = Date.now()
  const forged = signEnvelope({
    privateKey: attacker.privateKey,
    clientNonce: state.clientNonce,
    counter: 1,
    body: helloAck(SESSION, state.helloId),
    now,
  })
  assert.equal(state.client.handleEnvelope(forged, now).accepted, false)

  const valid = signEnvelope({
    privateKey: state.privateKey,
    clientNonce: state.clientNonce,
    counter: 1,
    body: helloAck(SESSION, state.helloId),
    now,
  })
  valid[valid.length - 65] ^= 1
  assert.equal(state.client.handleEnvelope(valid, now).accepted, false)
  assert.equal(state.client.ready, false)
})

test('V4 test client rejects wrong recipients, expired receipts, and prior-connection replay', () => {
  const previous = setup()
  const now = Date.now()
  const oldReceipt = signEnvelope({
    privateKey: previous.privateKey,
    clientNonce: previous.clientNonce,
    counter: 1,
    body: helloAck(SESSION, previous.helloId),
    now,
  })
  const reconnected = setup()
  assert.notEqual(reconnected.clientNonce, previous.clientNonce)
  assert.equal(reconnected.client.handleEnvelope(oldReceipt, now).accepted, false)

  const wrongRecipient = signEnvelope({
    privateKey: reconnected.privateKey,
    clientNonce: reconnected.clientNonce,
    recipient: '2a000000-0000-4000-8000-000000000003',
    counter: 1,
    body: helloAck(SESSION, reconnected.helloId),
    now,
  })
  assert.equal(reconnected.client.handleEnvelope(wrongRecipient, now).accepted, false)

  const expired = signEnvelope({
    privateKey: reconnected.privateKey,
    clientNonce: reconnected.clientNonce,
    counter: 1,
    body: helloAck(SESSION, reconnected.helloId),
    issuedAt: now - 40_000,
    expiresAt: now - 1,
    now,
  })
  assert.equal(reconnected.client.handleEnvelope(expired, now).accepted, false)
})
