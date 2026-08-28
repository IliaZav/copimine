/*
 * Local-only survival combat probe.  It uses Mineflayer's real use_entity
 * attack path; it is never used by the launcher or by a production server.
 * The test runner prepares the bot with RCON, while this process supplies the
 * player-side packets and records the observed boss entity.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'BossHitProbe'
const durationMs = Number(process.argv[3] || 45000)
const attackEveryMs = Number(process.env.END_RIFT_BOSS_ATTACK_EVERY_MS || 1100)
const attackDelayMs = Number(process.env.END_RIFT_BOSS_ATTACK_DELAY_MS || 9000)
const targetUuid = process.env.END_RIFT_BOSS_UUID || ''

const bot = mineflayer.createBot({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline'
})

let spawned = false
let attackTimer = null
let attackCount = 0
let bossSeen = false

bot._client.on('packet', (data, meta) => {
  if (meta?.name !== 'add_resource_pack') return
  console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
  bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
})

function bossEntity () {
  const entities = Object.values(bot.entities)
  return entities
    .filter(entity => entity && entity.name === 'enderman')
    .filter(entity => !targetUuid || entity.uuid === targetUuid)
    .filter(entity => bot.entity && entity.position.distanceTo(bot.entity.position) <= 40)
    .sort((first, second) => first.position.distanceTo(bot.entity.position)
      - second.position.distanceTo(bot.entity.position))[0]
}

function tryAttack () {
  if (!bot.entity) return
  const boss = bossEntity()
  if (!boss) return
  if (!bossSeen) {
    bossSeen = true
    console.log(`BOSS_ENTITY ${username} id=${boss.id} pos=${boss.position.x},${boss.position.y},${boss.position.z}`)
  }
  bot.lookAt(boss.position.offset(0, 1.2, 0), true).then(() => {
    // The boss can move during lookAt's asynchronous turn. Re-resolve the
    // entity and measure reach immediately before the use_entity packet so a
    // stale client-side position cannot turn a real player hit into a miss.
    const refreshedBoss = bossEntity()
    if (!refreshedBoss || refreshedBoss.id !== boss.id) return
    const refreshedDistance = bot.entity.position.distanceTo(refreshedBoss.position)
    if (refreshedDistance > 4.5) {
      console.log(`BOSS_OUT_OF_REACH ${username} distance=${refreshedDistance.toFixed(2)}`)
      return
    }
    const selectSlot = typeof bot.setQuickBarSlot === 'function'
      ? bot.setQuickBarSlot(0)
      : undefined
    return Promise.resolve(selectSlot).catch(() => {}).then(() => {
      const attackTarget = bossEntity()
      if (!attackTarget || attackTarget.id !== refreshedBoss.id) return
      const finalDistance = bot.entity.position.distanceTo(attackTarget.position)
      if (finalDistance > 4.5) {
        console.log(`BOSS_OUT_OF_REACH ${username} distance=${finalDistance.toFixed(2)}`)
        return
      }
      bot.attack(attackTarget)
      attackCount += 1
      console.log(`PLAYER_ATTACK ${username} count=${attackCount} bossId=${attackTarget.id} distance=${finalDistance.toFixed(2)}`)
    })
  }).catch(error => {
    console.error(`ATTACK_ERROR ${username} ${error.stack || error}`)
  })
}

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  setTimeout(() => {
    attackTimer = setInterval(tryAttack, attackEveryMs)
    tryAttack()
  }, attackDelayMs)
})

bot.on('entitySpawn', entity => {
  if (entity?.name === 'enderman') {
    console.log(`ENTITY_SPAWN ${username} id=${entity.id} pos=${entity.position.x},${entity.position.y},${entity.position.z}`)
  }
})

bot.on('death', () => console.log(`DEATH_EVENT ${username}`))
bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
bot.on('end', () => {
  if (attackTimer !== null) clearInterval(attackTimer)
  if (!spawned || attackCount === 0) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} bossSeen=${bossSeen}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
