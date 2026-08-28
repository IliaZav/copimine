/*
 * Local-only bed respawn behavior probe for the isolated Paper server.
 * It logs in, sends the real player block-interaction packet for a bed, then
 * acknowledges a server-side death so the caller can inspect the respawn.
 */
const path = require('path')
const mc = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'minecraft-protocol'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'EndRiftBedProbe'
const durationMs = Number(process.argv[3] || 30000)
const bed = {
  x: Number(process.env.END_RIFT_BED_X || 100),
  y: Number(process.env.END_RIFT_BED_Y || 70),
  z: Number(process.env.END_RIFT_BED_Z || 100)
}

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
let sessionStarted = false
let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }
let positionTimer = null
let interactionSent = false
let respawnRequestSent = false

function sendBedInteraction () {
  if (interactionSent || !client.state || client.state !== 'play') return
  interactionSent = true
  console.log(`BED_INTERACTION_SENT ${username} ${bed.x},${bed.y},${bed.z}`)
  for (const [index, target] of [bed, { x: bed.x, y: bed.y, z: bed.z - 1 }].entries()) {
    client.write('block_place', {
      hand: 0,
      location: target,
      direction: 1,
      cursorX: 0.5,
      cursorY: 0.5,
      cursorZ: 0.5,
      insideBlock: false,
      sequence: index + 1
    })
  }
}

function startSession () {
  if (sessionStarted) return
  sessionStarted = true
  joined = true
  console.log(`PLAYER_JOIN ${username}`)
  client.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => client.chat('/login endrift-local'), delay)
  }
  setTimeout(sendBedInteraction, 10000)
}

client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
  if (meta?.name === 'death_combat_event') {
    console.log(`DEATH_PACKET ${username}`)
    setTimeout(() => {
      if (respawnRequestSent || !client.state || client.state !== 'play') return
      respawnRequestSent = true
      client.write('client_command', { actionId: 0 })
      console.log(`RESPAWN_REQUEST_SENT ${username}`)
    }, 200)
  }
  if (meta?.name !== 'position') return
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
      if (client.state === 'play') client.write('position_look', { ...position, onGround: true })
    }, 100)
  }
  console.log(`SERVER_POSITION ${username} ${position.x},${position.y},${position.z}`)
})

// Start only after configuration/resource-pack negotiation has entered the
// play state; `playerJoin` is not a reliable self-player signal here.
client.once('login', startSession)
client.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
client.on('end', () => {
  if (positionTimer !== null) clearInterval(positionTimer)
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username}`)
  process.exit()
})

setTimeout(() => client.end(), durationMs)
