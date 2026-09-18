/*
 * Local-only behavior probe using Mineflayer's normal block interaction
 * path. It is deliberately isolated from the launcher and production data.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))
const Vec3 = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'vec3')).Vec3

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'BedMineProbe'
const durationMs = Number(process.argv[3] || 30000)
const bedPosition = new Vec3(
  Number(process.env.END_RIFT_BED_X || 100),
  Number(process.env.END_RIFT_BED_Y || 63),
  Number(process.env.END_RIFT_BED_Z || 100)
)

const bot = mineflayer.createBot({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline'
})

let spawned = false
let interactionAttempts = 0
let respawnCount = 0

bot._client.on('packet', (data, meta) => {
  if (meta?.name !== 'add_resource_pack') return
  console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
  bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
})

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  const tryActivateBed = async () => {
    if (!bot.entity || interactionAttempts >= 4) return
    interactionAttempts += 1
    const block = bot.blockAt(bedPosition)
    console.log(`BED_BLOCK ${username} attempt=${interactionAttempts} ${block ? block.name : 'missing'}`)
    if (!block) {
      setTimeout(tryActivateBed, 1500)
      return
    }
    try {
      await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
      await bot.sleep(block)
      console.log(`BED_SLEEP_OK ${username} ${bedPosition.x},${bedPosition.y},${bedPosition.z}`)
    } catch (error) {
      console.error(`BED_INTERACTION_ERROR ${username} ${error.stack || error}`)
      if (interactionAttempts < 4) {
        setTimeout(tryActivateBed, 1500)
      } else {
        process.exitCode = 1
      }
    }
  }
  setTimeout(tryActivateBed, 10000)
})

bot.on('death', () => {
  console.log(`DEATH_EVENT ${username}`)
  setTimeout(() => {
    respawnCount += 1
    bot.respawn()
    console.log(`RESPAWN_REQUEST_SENT ${username} count=${respawnCount}`)
  }, 200)
})

bot.on('respawn', () => {
  if (bot.entity) {
    console.log(`RESPAWN_POSITION ${username} ${bot.entity.position.x},${bot.entity.position.y},${bot.entity.position.z}`)
  }
})

bot.on('spawn', () => {
  if (spawned && respawnCount > 0 && bot.entity) {
    console.log(`RESPAWN_POSITION ${username} ${bot.entity.position.x},${bot.entity.position.y},${bot.entity.position.z}`)
  }
})

bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})

bot.on('end', () => {
  if (!spawned) process.exitCode = 1
  console.log(`PLAYER_END ${username}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
