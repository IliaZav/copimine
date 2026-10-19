'use strict'

const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const PROTOCOL_VERSION = 4
const CONTROL_CHANNEL = 'copimine:client_bridge_v4'
const DOMAIN = Buffer.from('copimine:client-bridge:v4', 'ascii')
const MAX_ENVELOPE_BYTES = 66_000
const MAX_BODY_BYTES = 65_536
const MAX_VALIDITY_MILLIS = 60_000
const MAX_FUTURE_SKEW_MILLIS = 30_000
const FIRST_RETRY_DELAY_MILLIS = 1_000
const MAX_RETRY_DELAY_MILLIS = 30_000

function readPinnedTrust() {
  const policyPath = path.resolve(__dirname,
    '../tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BridgeSigningPolicy.java')
  const source = fs.readFileSync(policyPath, 'utf8')
  const keyId = source.match(/PINNED_KEY_ID\s*=\s*"([^"]+)"/)
  const publicKey = source.match(/PINNED_PUBLIC_KEY_X509_BASE64\s*=\s*"([^"]+)"/)
  const fingerprint = source.match(/PINNED_PUBLIC_KEY_SHA256\s*=\s*"([0-9a-f]{64})"/i)
  if (!keyId || !publicKey || !fingerprint) {
    throw new Error('V4 bridge pinned trust configuration is incomplete')
  }
  const der = Buffer.from(publicKey[1], 'base64')
  if (der.length === 0 || der.toString('base64') !== publicKey[1]
      || crypto.createHash('sha256').update(der).digest('hex') !== fingerprint[1].toLowerCase()) {
    throw new Error('V4 bridge pinned public key fingerprint mismatch')
  }
  return { keyId: keyId[1], publicKey: crypto.createPublicKey({ key: der, format: 'der', type: 'spki' }) }
}

function isCanonicalBase64Url(value, decodedBytes, encodedChars) {
  if (typeof value !== 'string' || value.length !== encodedChars
      || !/^[A-Za-z0-9_-]+$/.test(value)) return false
  try {
    const decoded = Buffer.from(value, 'base64url')
    return decoded.length === decodedBytes && decoded.toString('base64url') === value
  } catch (_) {
    return false
  }
}

function uuidBytes(value) {
  if (typeof value !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
    throw new Error('invalid UUID')
  }
  return Buffer.from(value.replaceAll('-', ''), 'hex')
}

function uuidFromBytes(value) {
  if (value.length !== 16) throw new Error('invalid UUID bytes')
  const hex = value.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function uint64(value) {
  const bytes = Buffer.alloc(8)
  bytes.writeBigInt64BE(BigInt(value))
  return bytes
}

function int32(value) {
  const bytes = Buffer.alloc(4)
  bytes.writeInt32BE(value)
  return bytes
}

function lengthPrefixed(value) {
  return Buffer.concat([int32(value.length), value])
}

function readPrintableAscii(buffer, offset, length) {
  if (length < 1 || offset + length > buffer.length) throw new Error('truncated v4 field')
  const value = buffer.subarray(offset, offset + length)
  if (value.some(byte => byte < 0x21 || byte > 0x7e)) throw new Error('invalid v4 ASCII field')
  return value.toString('ascii')
}

function parseEnvelope(encoded) {
  if (!Buffer.isBuffer(encoded) || encoded.length === 0 || encoded.length > MAX_ENVELOPE_BYTES) {
    throw new Error('invalid v4 envelope size')
  }
  let offset = 0
  const readString = (minimum, maximum, label) => {
    if (offset >= encoded.length) throw new Error(`truncated ${label} length`)
    const length = encoded[offset++]
    if (length < minimum || length > maximum) throw new Error(`invalid ${label} length`)
    const value = readPrintableAscii(encoded, offset, length)
    offset += length
    return value
  }
  if (encoded[offset++] !== PROTOCOL_VERSION) throw new Error('wrong v4 protocol version')
  const keyId = readString(1, 64, 'key id')
  const clientNonce = readString(43, 43, 'client nonce')
  if (!isCanonicalBase64Url(clientNonce, 32, 43)) throw new Error('noncanonical client nonce')
  if (offset + 16 > encoded.length) throw new Error('truncated recipient')
  const recipientUuid = uuidFromBytes(encoded.subarray(offset, offset + 16))
  offset += 16
  const streamId = readString(22, 22, 'stream id')
  if (!isCanonicalBase64Url(streamId, 16, 22)) throw new Error('noncanonical stream id')
  if (offset + 8 * 3 + 4 + 64 > encoded.length) throw new Error('truncated v4 envelope metadata')
  const counter = encoded.readBigInt64BE(offset); offset += 8
  const issuedAtMillis = encoded.readBigInt64BE(offset); offset += 8
  const expiresAtMillis = encoded.readBigInt64BE(offset); offset += 8
  const bodyLength = encoded.readUInt32BE(offset); offset += 4
  if (counter <= 0n || issuedAtMillis <= 0n || expiresAtMillis <= issuedAtMillis
      || expiresAtMillis - issuedAtMillis > BigInt(MAX_VALIDITY_MILLIS)
      || bodyLength < 1 || bodyLength > MAX_BODY_BYTES
      || encoded.length - offset !== bodyLength + 64) {
    throw new Error('invalid v4 envelope metadata')
  }
  const body = encoded.subarray(offset, offset + bodyLength)
  const signature = encoded.subarray(offset + bodyLength)
  return {
    keyId, clientNonce, recipientUuid, streamId, counter,
    issuedAtMillis, expiresAtMillis, body, signature,
  }
}

function canonicalSigningInput(metadata, exactBody) {
  return Buffer.concat([
    lengthPrefixed(DOMAIN),
    int32(1), Buffer.from([PROTOCOL_VERSION]),
    lengthPrefixed(Buffer.from(metadata.keyId, 'ascii')),
    lengthPrefixed(Buffer.from(metadata.clientNonce, 'ascii')),
    uuidBytes(metadata.recipientUuid),
    lengthPrefixed(Buffer.from(metadata.streamId, 'ascii')),
    uint64(metadata.counter), uint64(metadata.issuedAtMillis), uint64(metadata.expiresAtMillis),
    lengthPrefixed(exactBody),
  ])
}

function verifyEnvelope(encoded, nowMillis, trust) {
  const metadata = parseEnvelope(encoded)
  const now = BigInt(nowMillis)
  if (metadata.keyId !== trust.keyId
      || metadata.expiresAtMillis <= now
      || metadata.issuedAtMillis > now + BigInt(MAX_FUTURE_SKEW_MILLIS)) {
    throw new Error('untrusted or expired v4 envelope')
  }
  const signed = canonicalSigningInput(metadata, metadata.body)
  if (!crypto.verify(null, signed, trust.publicKey, metadata.signature)) {
    throw new Error('invalid v4 signature')
  }
  return metadata
}

function commonHeader(type, nonce, playerUuid, sessionUuid) {
  if (!isCanonicalBase64Url(nonce, 32, 43)) throw new Error('invalid test client nonce')
  return Buffer.concat([
    Buffer.from([PROTOCOL_VERSION, type, 43]),
    Buffer.from(nonce, 'ascii'), uuidBytes(playerUuid), uuidBytes(sessionUuid),
  ])
}

function writeStream(streamId) {
  if (!isCanonicalBase64Url(streamId, 16, 22)) throw new Error('invalid bridge stream id')
  return Buffer.concat([Buffer.from([22]), Buffer.from(streamId, 'ascii')])
}

function requestResult(accepted, response, ready, reason = '') {
  return { accepted, response: response || null, ready, reason }
}

function scheduleNextRetry(senderState, nowMillis) {
  const delay = Math.max(FIRST_RETRY_DELAY_MILLIS, senderState.retryDelayMillis)
  senderState.nextRetryAtMillis = nowMillis + delay
  senderState.retryDelayMillis = Math.min(MAX_RETRY_DELAY_MILLIS, delay * 2)
}

function createBridgeV4TestClient({ playerUuid, clientNonce, trust } = {}) {
  uuidBytes(playerUuid)
  const nonce = clientNonce || crypto.randomBytes(32).toString('base64url')
  if (!isCanonicalBase64Url(nonce, 32, 43)) throw new Error('invalid test client nonce')
  const pinned = trust
    ? { keyId: trust.keyId, publicKey: crypto.createPublicKey({
      key: trust.publicKeyDer, format: 'der', type: 'spki',
    }) }
    : readPinnedTrust()
  if (!pinned.keyId || !pinned.publicKey) throw new Error('missing V4 bridge trust anchor')

  let sessionUuid = null
  let helloId = null
  let controlHelloBytes = null
  const senderStreams = new Map()
  let isClosed = false

  const isReadyFor = sender => senderStreams.get(sender)?.ready === true
  const isEndEventReady = () => isReadyFor(1)

  return {
    nonce,
    get ready() { return isEndEventReady() },
    get session() { return sessionUuid },
    get stream() { return senderStreams.get(1)?.streamId || null },
    isReadyFor,
    beginVerifiedSession(verifiedSessionUuid) {
      if (isClosed) throw new Error('V4 test client is closed')
      uuidBytes(verifiedSessionUuid)
      if (sessionUuid === verifiedSessionUuid && controlHelloBytes) return Buffer.from(controlHelloBytes)
      sessionUuid = verifiedSessionUuid
      helloId = crypto.randomBytes(16)
      controlHelloBytes = Buffer.concat([
        commonHeader(0, nonce, playerUuid, sessionUuid), helloId,
      ])
      senderStreams.clear()
      return Buffer.from(controlHelloBytes)
    },
    handleEnvelope(exactEnvelopeBytes, nowMillis = Date.now()) {
      if (isClosed || !sessionUuid) return requestResult(false, null, isEndEventReady(), 'no active session')
      let metadata
      try {
        metadata = verifyEnvelope(exactEnvelopeBytes, nowMillis, pinned)
      } catch (error) {
        return requestResult(false, null, isEndEventReady(), error.message)
      }
      if (metadata.clientNonce !== nonce || metadata.recipientUuid !== playerUuid) {
        return requestResult(false, null, isEndEventReady(), 'wrong connection or recipient')
      }
      const body = metadata.body
      if (body.length < 3 || body[0] !== 1) {
        return requestResult(false, null, isEndEventReady(), 'invalid bridge control schema')
      }
      const type = body[1]
      const sender = body[2]
      if (sender !== 1 && sender !== 2) {
        return requestResult(false, null, isEndEventReady(), 'invalid bridge sender')
      }

      if (type === 0) {
        if ((body.length !== 36 && body.length !== 37) || metadata.counter !== 1n
            || senderStreams.has(sender)) {
          return requestResult(false, null, isEndEventReady(), 'unexpected initial bridge receipt')
        }
        const bodySession = uuidFromBytes(body.subarray(3, 19))
        const featureFlags = body.length === 37 ? body[36] : 0
        if (bodySession !== sessionUuid || body[19] !== 0 || (featureFlags & ~1) !== 0
            || !crypto.timingSafeEqual(body.subarray(20, 36), helloId)) {
          return requestResult(false, null, isEndEventReady(), 'initial receipt does not match the verified session')
        }
        const senderState = {
          sender,
          streamId: metadata.streamId,
          lastServerCounter: 1n,
          featureFlags,
          readyRequestId: crypto.randomBytes(16),
          readyRequestCounter: 1n,
          readyConfirmBytes: null,
          pendingRequestBytes: null,
          nextRetryAtMillis: 0,
          retryDelayMillis: FIRST_RETRY_DELAY_MILLIS,
          retryExpiresAtMillis: metadata.expiresAtMillis,
          ready: false,
        }
        senderStreams.set(sender, senderState)
        const response = Buffer.concat([
          commonHeader(3, nonce, playerUuid, sessionUuid),
          uint64(senderState.readyRequestCounter), Buffer.from([sender]), writeStream(senderState.streamId),
          senderState.readyRequestId,
          crypto.createHash('sha256').update(exactEnvelopeBytes).digest(),
        ])
        senderState.pendingRequestBytes = response
        scheduleNextRetry(senderState, Number(nowMillis))
        return requestResult(true, response, isEndEventReady())
      }

      const senderState = senderStreams.get(sender)
      if (!senderState || metadata.streamId !== senderState.streamId) {
        return requestResult(false, null, isEndEventReady(), 'wrong stream or replayed/out-of-order server counter')
      }

      if (type === 255 && senderState.readyConfirmBytes && metadata.counter === 2n
          && senderState.lastServerCounter === 2n
          && metadata.body.length === 43
          && uuidFromBytes(metadata.body.subarray(3, 19)) === sessionUuid
          && crypto.timingSafeEqual(metadata.body.subarray(19, 35), senderState.readyRequestId)
          && metadata.body.readBigInt64BE(35) === senderState.readyRequestCounter) {
        return requestResult(true, Buffer.from(senderState.readyConfirmBytes), isEndEventReady())
      }
      if (metadata.counter !== senderState.lastServerCounter + 1n) {
        return requestResult(false, null, isEndEventReady(), 'replayed or out-of-order server counter')
      }

      if (!senderState.ready) {
        if (type === 255 && body.length === 43 && metadata.counter === 2n
            && senderState.readyConfirmBytes === null) {
          const bodySession = uuidFromBytes(body.subarray(3, 19))
          const bodyCounter = body.readBigInt64BE(35)
          if (bodySession !== sessionUuid
              || !crypto.timingSafeEqual(body.subarray(19, 35), senderState.readyRequestId)
              || bodyCounter !== senderState.readyRequestCounter) {
            return requestResult(false, null, isEndEventReady(), 'sender ready acknowledgement mismatch')
          }
          const confirm = Buffer.concat([
            commonHeader(4, nonce, playerUuid, sessionUuid), Buffer.from([sender]),
            writeStream(senderState.streamId), senderState.readyRequestId,
            uint64(senderState.readyRequestCounter),
            crypto.createHash('sha256').update(exactEnvelopeBytes).digest(),
          ])
          senderState.lastServerCounter = 2n
          senderState.readyConfirmBytes = confirm
          senderState.pendingRequestBytes = confirm
          senderState.retryExpiresAtMillis = metadata.expiresAtMillis
          senderState.retryDelayMillis = FIRST_RETRY_DELAY_MILLIS
          scheduleNextRetry(senderState, Number(nowMillis))
          return requestResult(true, confirm, isEndEventReady())
        }
        if (type === 254 && body.length === 75 && metadata.counter === 3n
            && senderState.readyConfirmBytes !== null) {
          const bodySession = uuidFromBytes(body.subarray(3, 19))
          const bodyCounter = body.readBigInt64BE(35)
          const confirmDigest = crypto.createHash('sha256').update(senderState.readyConfirmBytes).digest()
          if (bodySession !== sessionUuid
              || !crypto.timingSafeEqual(body.subarray(19, 35), senderState.readyRequestId)
              || bodyCounter !== senderState.readyRequestCounter
              || !crypto.timingSafeEqual(body.subarray(43, 75), confirmDigest)) {
            return requestResult(false, null, isEndEventReady(), 'sender ready confirmation mismatch')
          }
          senderState.lastServerCounter = 3n
          senderState.ready = true
          senderState.pendingRequestBytes = null
          return requestResult(true, null, isEndEventReady())
        }
        return requestResult(false, null, isEndEventReady(), 'unexpected sender readiness receipt')
      }

      if (type === 253) {
        if ((senderState.featureFlags & 1) === 0 || body.length !== 19
            || uuidFromBytes(body.subarray(3, 19)) !== sessionUuid) {
          return requestResult(false, null, isEndEventReady(), 'invalid or unnegotiated sender heartbeat')
        }
      } else {
        return requestResult(false, null, isEndEventReady(), 'unexpected bridge control receipt')
      }
      senderState.lastServerCounter = metadata.counter
      return requestResult(true, null, isEndEventReady())
    },
    tick(nowMillis = Date.now()) {
      if (isClosed || !sessionUuid) return []
      const now = Number(nowMillis)
      if (!Number.isSafeInteger(now) || now < 0) return []
      const retries = []
      for (const senderState of senderStreams.values()) {
        if (senderState.ready || !senderState.pendingRequestBytes
            || now < senderState.nextRetryAtMillis || now >= Number(senderState.retryExpiresAtMillis)) {
          continue
        }
        retries.push({ sender: senderState.sender, response: Buffer.from(senderState.pendingRequestBytes) })
        scheduleNextRetry(senderState, now)
      }
      return retries
    },
    close() {
      isClosed = true
      sessionUuid = null
      helloId = null
      controlHelloBytes = null
      senderStreams.clear()
    },
  }
}

module.exports = {
  CONTROL_CHANNEL,
  createBridgeV4TestClient,
  parseEnvelope,
  verifyEnvelope,
}
