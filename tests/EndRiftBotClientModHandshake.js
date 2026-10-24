'use strict'

const CHANNEL = 'copimine:client_bridge'
const PROTOCOL_VERSION = 3
const REQUIRED_VERSION = '0.1.3+26.3'
const MOD_ID = 'copimineclient'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const { CONTROL_CHANNEL, createBridgeV4TestClient } = require('./EndRiftBotBridgeV4')

function decodeChallenge(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0 || buffer.length > 4_096) {
    throw new Error('invalid challenge payload size')
  }
  let offset = 0
  const readUtf = () => {
    if (offset + 2 > buffer.length) throw new Error('truncated UTF length')
    const length = buffer.readUInt16BE(offset)
    offset += 2
    if (offset + length > buffer.length) throw new Error('truncated UTF value')
    const bytes = buffer.subarray(offset, offset + length)
    if (bytes.some(byte => byte > 0x7f || byte === 0)) throw new Error('non-ASCII challenge field')
    offset += length
    return bytes.toString('ascii')
  }
  const readInt = () => {
    if (offset + 4 > buffer.length) throw new Error('truncated integer')
    const value = buffer.readInt32BE(offset)
    offset += 4
    return value
  }
  const readLong = () => {
    if (offset + 8 > buffer.length) throw new Error('truncated long')
    const value = buffer.readBigInt64BE(offset)
    offset += 8
    return value
  }
  const messageType = readUtf()
  const protocol = readInt()
  const sequence = readLong()
  const timestamp = readLong()
  const sessionId = readUtf()
  const clientVersion = readUtf()
  offset += 4 // booleans
  if (offset > buffer.length) throw new Error('truncated capability flags')
  const effectCount = readInt()
  if (effectCount < 0 || effectCount > 64) throw new Error('invalid supported effect count')
  for (let index = 0; index < effectCount; index++) readUtf()
  readUtf() // effect id
  readUtf() // shaderpack
  readInt() // duration
  if (offset + 4 > buffer.length) throw new Error('truncated intensity')
  offset += 4
  readInt() // fade in
  readInt() // fade out
  const mode = readUtf()
  readUtf() // clear policy
  readUtf() // source
  readUtf() // reason
  readUtf() // status
  if (offset !== buffer.length) throw new Error('trailing challenge bytes')
  return { messageType, protocol, sequence, timestamp, sessionId, clientVersion, mode }
}

function writeUtf(value) {
  const bytes = Buffer.from(value, 'ascii')
  if (bytes.length > 0xffff || bytes.some(byte => byte > 0x7f || byte === 0)) {
    throw new Error('invalid test handshake text')
  }
  const length = Buffer.alloc(2)
  length.writeUInt16BE(bytes.length)
  return Buffer.concat([length, bytes])
}

function buildHelloResponse(challengeId, version, nowMillis = Date.now()) {
  const protocol = Buffer.alloc(4)
  protocol.writeInt32BE(PROTOCOL_VERSION)
  const sequence = Buffer.alloc(8)
  sequence.writeBigInt64BE(0n)
  const timestamp = Buffer.alloc(8)
  timestamp.writeBigInt64BE(BigInt(nowMillis))
  const flags = Buffer.from([0, 0, 0, 0])
  const effectCount = Buffer.alloc(4)
  effectCount.writeInt32BE(0)
  const zeroInt = Buffer.alloc(4)
  const zeroFloat = Buffer.alloc(4)
  zeroFloat.writeFloatBE(0)
  return Buffer.concat([
    writeUtf('hello'), protocol, sequence, timestamp,
    writeUtf(challengeId), writeUtf(version), flags, effectCount,
    writeUtf(''), writeUtf(''), zeroInt, zeroFloat, zeroInt, zeroInt,
    writeUtf(MOD_ID), writeUtf(''), writeUtf('SYSTEM'), writeUtf(''), writeUtf(''),
  ])
}

function acceptLocalTestResourcePack(bot, packet) {
  const client = bot._client
  if (typeof bot.acceptResourcePack === 'function') {
    // Mineflayer accepts automatically during configuration. In play, answer
    // the request explicitly so the required-pack gate does not block login.
    if (client.state !== 'configuration') bot.acceptResourcePack()
    return
  }
  const identity = packet && packet.uuid !== undefined
    ? { uuid: packet.uuid }
    : packet && packet.hash !== undefined ? { hash: packet.hash } : {}
  client.write('resource_pack_receive', { ...identity, result: 3 })
  client.write('resource_pack_receive', { ...identity, result: 0 })
}

function attachLocalClientModHandshake(bot, endpoint) {
  if (!endpoint || endpoint.host !== '127.0.0.1') {
    throw new Error(`test client handshake is restricted to loopback, got ${endpoint && endpoint.host}`)
  }
  if (endpoint.version !== REQUIRED_VERSION) {
    throw new Error(`test client handshake only emulates ${REQUIRED_VERSION}`)
  }
  const client = bot && bot._client
  if (!bot || typeof bot.on !== 'function' || typeof bot.once !== 'function' || !client
      || typeof client.registerChannel !== 'function' || typeof client.write !== 'function') {
    throw new Error('Mineflayer protocol client is unavailable')
  }
  client.on('add_resource_pack', packet => acceptLocalTestResourcePack(bot, packet))
  client.on('resource_pack_send', packet => acceptLocalTestResourcePack(bot, packet))
  client.on('custom_payload', packet => {
    if (!packet || typeof packet.channel !== 'string' || !packet.channel.startsWith('copimine:')) return
    const bytes = Buffer.isBuffer(packet.data) ? packet.data.length : 0
    console.log(`TEST_CLIENT_SERVER_PLUGIN_PACKET ${bot.username} channel=${packet.channel} bytes=${bytes}`)
  })
  let pendingSessionId = ''
  let bridgeV4 = null
  let readySessionId = ''
  const retryBridgeRequests = () => {
    if (!bridgeV4) return
    for (const retry of bridgeV4.tick(Date.now())) {
      console.log(`TEST_CLIENT_BRIDGE_V4_RETRY ${bot.username} sender=${retry.sender} bytes=${retry.response.length}`)
      client.write('custom_payload', { channel: CONTROL_CHANNEL, data: retry.response })
    }
  }
  const bridgeRetryTimer = setInterval(retryBridgeRequests, 100)
  if (typeof bridgeRetryTimer.unref === 'function') bridgeRetryTimer.unref()
  const startBridgeV4 = sessionId => {
    if (!sessionId) return false
    const playerUuid = bot.uuid || bot.player?.uuid || bot.entity?.uuid
    if (!playerUuid) return false
    if (!bridgeV4) bridgeV4 = createBridgeV4TestClient({ playerUuid })
    const hello = bridgeV4.beginVerifiedSession(sessionId)
    client.write('custom_payload', { channel: CONTROL_CHANNEL, data: hello })
    console.log(`TEST_CLIENT_BRIDGE_V4_HELLO ${bot.username} session=${sessionId}`)
    return true
  }
  client.on(CHANNEL, payload => {
    let challenge
    try {
      challenge = decodeChallenge(payload)
    } catch (_) {
      return
    }
    if (challenge.protocol !== PROTOCOL_VERSION || challenge.sequence !== 0n
        || challenge.timestamp <= 0n || !UUID_PATTERN.test(challenge.sessionId)) {
      return
    }
    if (challenge.messageType === 'client_mod_challenge'
        && challenge.clientVersion === REQUIRED_VERSION && challenge.mode === MOD_ID) {
      pendingSessionId = challenge.sessionId
      client.write('custom_payload', {
        channel: CHANNEL,
        data: buildHelloResponse(challenge.sessionId, endpoint.version),
      })
      return
    }
    if (challenge.messageType !== 'ping' || !pendingSessionId
        || challenge.sessionId !== pendingSessionId) {
      return
    }
    startBridgeV4(challenge.sessionId)
  })
  client.on(CONTROL_CHANNEL, payload => {
    console.log(`TEST_CLIENT_BRIDGE_V4_PACKET ${bot.username} bytes=${Buffer.isBuffer(payload) ? payload.length : 0} active=${Boolean(bridgeV4)}`)
    if (!bridgeV4 || !Buffer.isBuffer(payload)) return
    const result = bridgeV4.handleEnvelope(payload, Date.now())
    if (!result.accepted) {
      console.warn(`TEST_CLIENT_BRIDGE_V4_REJECTED ${bot.username} reason=${result.reason}`)
      return
    }
    console.log(`TEST_CLIENT_BRIDGE_V4_ACCEPTED ${bot.username} stream=${bridgeV4.stream || 'narcotics'} ready=${result.ready}`)
    if (result.response) {
      console.log(`TEST_CLIENT_BRIDGE_V4_SEND ${bot.username} bytes=${result.response.length}`)
      client.write('custom_payload', { channel: CONTROL_CHANNEL, data: result.response })
    }
    if (result.ready && readySessionId !== bridgeV4.session) {
      readySessionId = bridgeV4.session
      console.log(`TEST_CLIENT_BRIDGE_V4_READY ${bot.username} session=${readySessionId}`)
    }
  })
  bot.once('login', () => {
    if (typeof client.writeChannel !== 'function') {
      throw new Error('Mineflayer protocol client cannot register the test channels')
    }
    client.registerChannel(CHANNEL, null, false)
    client.registerChannel(CONTROL_CHANNEL, null, false)
    // Advertise both receive channels in one REGISTER payload. V4 starts
    // only after the later V3 PING, when Mineflayer has installed the player UUID.
    client.writeChannel('minecraft:register', [CHANNEL, CONTROL_CHANNEL])
    console.log(`TEST_CLIENT_BRIDGE_CHANNELS_REGISTERED ${bot.username}`)
  })
  bot.once('spawn', () => {
    if (pendingSessionId) startBridgeV4(pendingSessionId)
  })
  bot.once('end', () => {
    clearInterval(bridgeRetryTimer)
    if (bridgeV4) bridgeV4.close()
    bridgeV4 = null
    pendingSessionId = ''
    readySessionId = ''
  })
}

module.exports = {
  CHANNEL,
  CONTROL_CHANNEL,
  attachLocalClientModHandshake,
  buildHelloResponse,
  createBridgeV4TestClient,
  decodeChallenge,
}
