/*
 * Local-only low-level combat probe.  It deliberately sends the same
 * serverbound use_entity attack packet as a vanilla client; no command-based
 * boss damage is used as a substitute for a player hit.
 */
const path = require('path')
const mc = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'minecraft-protocol'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'BossHitProbe'
const durationMs = Number(process.argv[3] || 45000)
const bossUuid = (process.env.END_RIFT_BOSS_UUID || '').toLowerCase()
const targetEntityType = Number(process.env.END_RIFT_TARGET_ENTITY_TYPE || '')
const attackDelayMs = Number(process.env.END_RIFT_BOSS_ATTACK_DELAY_MS || 9000)
const attackEveryMs = Number(process.env.END_RIFT_BOSS_ATTACK_EVERY_MS || 900)

const client = mc.createClient({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline',
  disableChatSigning: true,
  clientSettings: {
    locale: 'en_us',
    viewDistance: 8,
    chatFlags: 0,
    chatColors: true,
    skinParts: 127,
    mainHand: 1,
    enableTextFiltering: false,
    enableServerListing: true
  }
})

let joined = false
let started = false
let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }
let positionTimer = null
let bossEntityId = null
let attackTimer = null
let attacks = 0
let bossSeen = false

const writePacket = client.write.bind(client)
client.write = (name, params) => {
  if (name === 'use_entity') {
    let encoded = 'unavailable'
    try {
      encoded = client.serializer.createPacketBuffer({ name, params }).toString('hex')
    } catch (error) {
      encoded = `encode-error:${error.message}`
    }
    console.log(`USE_ENTITY_SENT ${username} ${JSON.stringify(params)} wire=${encoded}`)
  }
  return writePacket(name, params)
}

function begin () {
  if (started) return
  started = true
  joined = true
  console.log(`PLAYER_JOIN ${username}`)
  client.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => client.chat('/login endrift-local'), delay)
  }
  setTimeout(() => {
    if (bossEntityId === null) {
      console.log(`BOSS_ENTITY_MISSING ${username} expectedUuid=${bossUuid}`)
      return
    }
    attackTimer = setInterval(() => {
      client.write('use_entity', {
        target: bossEntityId,
        mouse: 1,
        sneaking: false
      })
      client.write('arm_animation', { hand: 0 })
      attacks += 1
      console.log(`PLAYER_ATTACK ${username} count=${attacks} bossId=${bossEntityId}`)
    }, attackEveryMs)
  }, attackDelayMs)
}

client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
    return
  }
  if (meta?.name === 'disconnect') {
    console.log(`CLIENT_DISCONNECT ${username} ${JSON.stringify(data)}`)
    return
  }
  const matchesUuid = data && bossUuid
      && String(data.objectUUID).toLowerCase() === bossUuid
  const matchesType = data && Number.isFinite(targetEntityType)
      && targetEntityType > 0 && Number(data.type) === targetEntityType
  if (meta?.name === 'spawn_entity' && (matchesUuid || matchesType)) {
    bossEntityId = data.entityId
    bossSeen = true
    console.log(`BOSS_ENTITY ${username} id=${data.entityId} uuid=${data.objectUUID} pos=${data.x},${data.y},${data.z}`)
  }
  if (meta?.name !== 'position') return
  console.log(`SERVER_POSITION_RAW ${username} ${JSON.stringify(data)}`)
  const relative = flag => Boolean(data.flags && data.flags[flag])
  position = {
    x: relative('x') ? position.x + data.x : data.x,
    y: relative('y') ? position.y + data.y : data.y,
    z: relative('z') ? position.z + data.z : data.z,
    yaw: relative('yaw') ? position.yaw + data.yaw : data.yaw,
    pitch: relative('pitch') ? position.pitch + data.pitch : data.pitch
  }
  client.write('teleport_confirm', { teleportId: data.teleportId })
  if (positionTimer === null) {
    positionTimer = setInterval(() => {
      client.write('position_look', { ...position, onGround: true })
    }, 100)
  }
  console.log(`SERVER_POSITION ${username} ${position.x},${position.y},${position.z}`)
})

client.once('login', begin)
client.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
client.on('end', () => {
  if (positionTimer !== null) clearInterval(positionTimer)
  if (attackTimer !== null) clearInterval(attackTimer)
  if (!joined || !bossSeen || attacks === 0) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attacks} bossSeen=${bossSeen}`)
  process.exit()
})

setTimeout(() => client.end(), durationMs)
