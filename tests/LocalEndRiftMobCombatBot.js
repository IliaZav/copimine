/*
 * Local-only live probe for the event's real wave-mob path and attack path.
 * This is deliberately a player-side client: it samples server entity
 * positions, walks into the arena and sends actual use_entity attacks.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'MobCombatProbe'
const durationMs = Number(process.argv[3] || 30000)
const arenaX = Number(process.argv[4])
const arenaY = Number(process.argv[5])
const arenaZ = Number(process.argv[6])
const arenaRadius = Number(process.argv[7])
const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })

let joined = false
let sampleTimer = null
let attackTimer = null
let healthTimer = null
let previousHealth = null
let sampleCount = 0
let movedEntities = new Map()
let attackCount = 0

function distance(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function isConfiguredArenaMob(entity) {
  if (![arenaX, arenaY, arenaZ, arenaRadius].every(Number.isFinite)) return true
  if (!entity?.position) return false
  const dx = entity.position.x - (arenaX + 0.5)
  const dz = entity.position.z - (arenaZ + 0.5)
  return dx * dx + dz * dz <= (arenaRadius + 1) * (arenaRadius + 1)
    && Math.abs(entity.position.y - arenaY) <= 5
}

function eventMobs() {
  return Object.values(bot.entities)
    .filter(entity => entity && ['spider', 'enderman', 'skeleton'].includes(entity.name))
    .filter(isConfiguredArenaMob)
    .filter(entity => bot.entity && distance(entity.position, bot.entity.position) <= 32)
}

function sampleMobs() {
  if (!bot.entity) return
  const mobs = eventMobs()
  sampleCount += 1
  for (const mob of mobs) {
    const previous = movedEntities.get(mob.id)
    const now = { x: mob.position.x, y: mob.position.y, z: mob.position.z }
    if (previous) {
      const moved = distance(previous, now)
      if (moved >= 0.25) {
        console.log(`MOB_MOVED ${username} id=${mob.id} type=${mob.name} delta=${moved.toFixed(2)} pos=${now.x.toFixed(2)},${now.y.toFixed(2)},${now.z.toFixed(2)}`)
      }
    } else {
      console.log(`MOB_SEEN ${username} id=${mob.id} type=${mob.name} pos=${now.x.toFixed(2)},${now.y.toFixed(2)},${now.z.toFixed(2)}`)
    }
    movedEntities.set(mob.id, now)
  }
  if (sampleCount % 8 === 0) {
    console.log(`MOB_SAMPLE ${username} count=${mobs.length}`)
  }
}

function attackNearest() {
  if (!bot.entity) return
  const target = eventMobs()
    .filter(entity => distance(entity.position, bot.entity.position) <= 4.5)
    .sort((a, b) => distance(a.position, bot.entity.position) - distance(b.position, bot.entity.position))[0]
  if (!target) return
  bot.lookAt(target.position.offset(0, 0.8, 0), true).then(() => {
    const refreshed = bot.entities[target.id]
    if (!refreshed || distance(refreshed.position, bot.entity.position) > 4.5) return
    const selectSlot = typeof bot.setQuickBarSlot === 'function'
      ? bot.setQuickBarSlot(0)
      : undefined
    return Promise.resolve(selectSlot).catch(() => {}).then(() => {
      const finalTarget = bot.entities[target.id]
      if (!finalTarget || distance(finalTarget.position, bot.entity.position) > 4.5) return
      bot.attack(finalTarget)
      attackCount += 1
      console.log(`PLAYER_ATTACK ${username} count=${attackCount} target=${finalTarget.id} type=${finalTarget.name} distance=${distance(finalTarget.position, bot.entity.position).toFixed(2)}`)
    })
  }).catch(error => console.error(`ATTACK_ERROR ${username} ${error.stack || error}`))
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
})

bot.once('spawn', () => {
  joined = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) setTimeout(() => bot.chat('/login endrift-local'), delay)
  // Mineflayer's physics plugin already acknowledges server teleports and
  // sends the matching position packet.  A second 100 ms position loop fights
  // that controller, produces invalid-packet spam after RCON sweeps, and can
  // make GrimAC disconnect the local probe before it reaches outer spawns.
  sampleTimer = setInterval(sampleMobs, 250)
  // Keep a survival-like attack cadence while still reacting quickly enough
  // to the tower-defense wave's moving attackers.
  attackTimer = setInterval(attackNearest, 400)
  healthTimer = setInterval(() => {
    if (previousHealth !== null && bot.health < previousHealth - 0.01) {
      console.log(`PLAYER_HURT ${username} before=${previousHealth.toFixed(2)} after=${bot.health.toFixed(2)}`)
    }
    previousHealth = bot.health
  }, 100)
})

bot.on('entitySpawn', entity => {
  if (entity && ['spider', 'enderman', 'skeleton'].includes(entity.name)) {
    console.log(`ENTITY_SPAWN ${username} id=${entity.id} type=${entity.name}`)
  }
})
bot.on('health', () => {
  if (previousHealth !== null && bot.health < previousHealth - 0.01) {
    console.log(`PLAYER_HURT ${username} before=${previousHealth.toFixed(2)} after=${bot.health.toFixed(2)}`)
  }
  previousHealth = bot.health
})
bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
bot.on('end', () => {
  for (const timer of [sampleTimer, attackTimer, healthTimer]) if (timer !== null) clearInterval(timer)
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} samples=${sampleCount}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
