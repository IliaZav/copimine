'use strict'

const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const test = require('node:test')

const {
  CHANNEL,
  CONTROL_CHANNEL,
  attachLocalClientModHandshake,
} = require('./EndRiftBotClientModHandshake')

const CLIENT_VERSION = '0.1.3+26.3'
const CHALLENGE_ID = '123e4567-e89b-12d3-a456-426614174000'

function dataUtf(value) {
  const bytes = Buffer.from(value, 'utf8')
  const length = Buffer.alloc(2)
  length.writeUInt16BE(bytes.length)
  return Buffer.concat([length, bytes])
}

function challengeFixture(overrides = {}) {
  const fields = {
    messageType: 'client_mod_challenge',
    protocol: 3,
    sequence: 0n,
    timestamp: 1_800_000_000_000n,
    sessionId: CHALLENGE_ID,
    clientVersion: CLIENT_VERSION,
    clientVisuals: false,
    clientOverlay: false,
    clientShaderLike: false,
    trueIrisShader: false,
    effects: [],
    effectId: '',
    shaderpack: '',
    duration: 0,
    intensity: 0,
    fadeIn: 0,
    fadeOut: 0,
    mode: 'copimineclient',
    clearPolicy: '',
    source: 'SYSTEM',
    reason: '',
    status: '',
    ...overrides,
  }
  const chunks = [
    dataUtf(fields.messageType),
    Buffer.from([0, 0, 0, fields.protocol]),
    Buffer.alloc(8),
    Buffer.from('000001A3185C5000', 'hex'),
    dataUtf(fields.sessionId),
    dataUtf(fields.clientVersion),
    Buffer.from([
      fields.clientVisuals ? 1 : 0,
      fields.clientOverlay ? 1 : 0,
      fields.clientShaderLike ? 1 : 0,
      fields.trueIrisShader ? 1 : 0,
    ]),
  ]
  const effectCount = Buffer.alloc(4)
  effectCount.writeInt32BE(fields.effects.length)
  chunks.push(effectCount, ...fields.effects.map(dataUtf))
  chunks.push(
    dataUtf(fields.effectId),
    dataUtf(fields.shaderpack),
  )
  const duration = Buffer.alloc(4)
  duration.writeInt32BE(fields.duration)
  const intensity = Buffer.alloc(4)
  intensity.writeFloatBE(fields.intensity)
  const fadeIn = Buffer.alloc(4)
  fadeIn.writeInt32BE(fields.fadeIn)
  const fadeOut = Buffer.alloc(4)
  fadeOut.writeInt32BE(fields.fadeOut)
  chunks.push(duration, intensity, fadeIn, fadeOut)
  chunks.push(
    dataUtf(fields.mode),
    dataUtf(fields.clearPolicy),
    dataUtf(fields.source),
    dataUtf(fields.reason),
    dataUtf(fields.status),
  )
  return Buffer.concat(chunks)
}

function parseHelloFixture(buffer) {
  let offset = 0
  const readUtf = () => {
    const length = buffer.readUInt16BE(offset)
    offset += 2
    const value = buffer.toString('utf8', offset, offset + length)
    offset += length
    return value
  }
  const messageType = readUtf()
  const protocol = buffer.readInt32BE(offset); offset += 4
  const sequence = buffer.readBigInt64BE(offset); offset += 8
  const timestamp = buffer.readBigInt64BE(offset); offset += 8
  const sessionId = readUtf()
  const clientVersion = readUtf()
  const capabilities = [buffer[offset++] === 1, buffer[offset++] === 1,
    buffer[offset++] === 1, buffer[offset++] === 1]
  const effectCount = buffer.readInt32BE(offset); offset += 4
  const effects = []
  for (let index = 0; index < effectCount; index++) effects.push(readUtf())
  const effectId = readUtf()
  const shaderpack = readUtf()
  const duration = buffer.readInt32BE(offset); offset += 4
  const intensity = buffer.readFloatBE(offset); offset += 4
  const fadeIn = buffer.readInt32BE(offset); offset += 4
  const fadeOut = buffer.readInt32BE(offset); offset += 4
  const mode = readUtf()
  const clearPolicy = readUtf()
  const source = readUtf()
  const reason = readUtf()
  const status = readUtf()
  assert.equal(offset, buffer.length, 'hello response has no trailing bytes')
  return {
    messageType, protocol, sequence, timestamp, sessionId, clientVersion,
    capabilities, effects, effectId, shaderpack, duration, intensity,
    fadeIn, fadeOut, mode, clearPolicy, source, reason, status,
  }
}

test('local bot answers only a valid current-version challenge with the exact challenge id', () => {
  const client = new EventEmitter()
  const registrations = []
  const writes = []
  client.registerChannel = (...args) => registrations.push(args)
  client.writeChannel = (...args) => registrations.push({ write: args })
  client.write = (...args) => writes.push(args)
  const bot = new EventEmitter()
  bot._client = client

  attachLocalClientModHandshake(bot, { host: '127.0.0.1', port: 25566, version: CLIENT_VERSION })
  assert.deepEqual(registrations, [], 'channel registration waits until the play login is established')
  bot.emit('login')
  assert.deepEqual(registrations, [
    [CHANNEL, null, false],
    [CONTROL_CHANNEL, null, false],
    { write: ['minecraft:register', [CHANNEL, CONTROL_CHANNEL]] },
  ])
  client.emit(CHANNEL, challengeFixture())

  assert.equal(writes.length, 1)
  assert.equal(writes[0][0], 'custom_payload')
  assert.equal(writes[0][1].channel, CHANNEL)
  const hello = parseHelloFixture(writes[0][1].data)
  assert.equal(hello.messageType, 'hello')
  assert.equal(hello.protocol, 3)
  assert.equal(hello.sequence, 0n)
  assert.ok(Number(hello.timestamp) >= Date.now() - 1_000)
  assert.equal(hello.sessionId, CHALLENGE_ID)
  assert.equal(hello.clientVersion, CLIENT_VERSION)
  assert.deepEqual(hello.capabilities, [false, false, false, false])
  assert.deepEqual(hello.effects, [])
  assert.equal(hello.effectId, '')
  assert.equal(hello.shaderpack, '')
  assert.equal(hello.duration, 0)
  assert.equal(hello.intensity, 0)
  assert.equal(hello.fadeIn, 0)
  assert.equal(hello.fadeOut, 0)
  assert.equal(hello.mode, 'copimineclient')
  assert.equal(hello.clearPolicy, '')
  assert.equal(hello.source, 'SYSTEM')
  assert.equal(hello.reason, '')
  assert.equal(hello.status, '')
})

test('local headless client accepts required UUID resource packs without downloading them', () => {
  const client = new EventEmitter()
  const writes = []
  client.registerChannel = () => {}
  client.write = (...args) => writes.push(args)
  const bot = new EventEmitter()
  bot._client = client
  attachLocalClientModHandshake(bot, { host: '127.0.0.1', port: 25566, version: CLIENT_VERSION })

  client.emit('add_resource_pack', {
    uuid: CHALLENGE_ID,
    url: 'http://127.0.0.1:8092/CopiMineResourcePack.zip',
    hash: 'bed02f57fb0606ce69211b3fb3488cc1bae5e6c5',
  })

  assert.deepEqual(writes, [
    ['resource_pack_receive', { uuid: CHALLENGE_ID, result: 3 }],
    ['resource_pack_receive', { uuid: CHALLENGE_ID, result: 0 }],
  ])
})

test('local bot refuses to emulate the client on any non-test endpoint', () => {
  const client = new EventEmitter()
  client.registerChannel = () => {}
  const bot = new EventEmitter()
  bot._client = client

  assert.throws(() => attachLocalClientModHandshake(bot, {
    host: 'example.com', port: 25566, version: CLIENT_VERSION,
  }), /127\.0\.0\.1:25566/)
  assert.throws(() => attachLocalClientModHandshake(bot, {
    host: '127.0.0.1', port: 25567, version: CLIENT_VERSION,
  }), /127\.0\.0\.1:25566/)
  assert.throws(() => attachLocalClientModHandshake(bot, {
    host: '127.0.0.1', port: 25566, version: '0.1.2',
  }), /0\.1\.3\+26\.3/)
})

test('local bot ignores malformed and mismatched server challenges', () => {
  const client = new EventEmitter()
  const writes = []
  client.registerChannel = () => {}
  client.writeChannel = () => {}
  client.write = (...args) => writes.push(args)
  const bot = new EventEmitter()
  bot._client = client
  attachLocalClientModHandshake(bot, { host: '127.0.0.1', port: 25566, version: CLIENT_VERSION })
  bot.emit('login')

  client.emit(CHANNEL, challengeFixture({ messageType: 'hello' }))
  client.emit(CHANNEL, challengeFixture({ protocol: 2 }))
  client.emit(CHANNEL, challengeFixture({ mode: 'othermod' }))
  client.emit(CHANNEL, Buffer.from([1, 2, 3]))

  assert.equal(writes.length, 0)
})

test('V4 registration does not depend on a player UUID being ready at login', () => {
  const client = new EventEmitter()
  const registrations = []
  const writes = []
  client.registerChannel = (...args) => registrations.push(args)
  client.writeChannel = (...args) => registrations.push({ write: args })
  client.write = (...args) => writes.push(args)
  const bot = new EventEmitter()
  bot.username = 'HandshakeProbe'
  bot._client = client

  attachLocalClientModHandshake(bot, { host: '127.0.0.1', port: 25566, version: CLIENT_VERSION })
  assert.doesNotThrow(() => bot.emit('login'))
  assert.equal(registrations.at(-1).write[0], 'minecraft:register')
  assert.deepEqual(registrations.at(-1).write[1], [CHANNEL, CONTROL_CHANNEL])

  client.emit(CHANNEL, challengeFixture())
  const ping = challengeFixture({ messageType: 'ping' })
  client.emit(CHANNEL, ping)
  assert.equal(writes.filter(([, payload]) => payload.channel === CONTROL_CHANNEL).length, 0,
    'the V4 hello waits until the UUID exists')

  bot.uuid = '123e4567-e89b-12d3-a456-426614174000'
  bot.emit('spawn')
  const v4Hellos = writes.filter(([, payload]) => payload.channel === CONTROL_CHANNEL)
  assert.equal(v4Hellos.length, 1, 'the spawn retry starts the signed V4 handshake')
  assert.ok(v4Hellos[0][1].data.length > 32)
})
